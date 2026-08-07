package com.hj0128.moneybook.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.flatten
import com.hj0128.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val MAX_AMOUNT_DIGITS = 12

data class EntryUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    /** 사용자가 누른 숫자만 담는다. 포맷팅은 화면에서 한다. */
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val memo: String = "",
    /** 대분류 > 소분류로 묶은 카테고리. 화면은 묶음 그대로 그린다. */
    val categoryNodes: List<CategoryNode> = emptyList(),
    val selectedCategoryId: Long? = null,
    val accounts: List<AccountBalance> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
) {
    val amount: Long get() = amountText.toLongOrNull() ?: 0L

    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    val canSave: Boolean
        get() = amount > 0 &&
            selectedAccountId != null &&
            if (isTransfer) {
                selectedToAccountId != null && selectedToAccountId != selectedAccountId
            } else {
                selectedCategoryId != null
            }
}

class EntryViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    init {
        // 지출 카테고리를 기본으로 먼저 띄운다(가장 많이 쓰는 입력이라).
        observeCategories(TransactionType.EXPENSE)
        viewModelScope.launch {
            repository.observeAccounts().collect { accounts ->
                _uiState.update { state ->
                    state.copy(
                        accounts = accounts,
                        selectedAccountId = state.selectedAccountId
                            ?: accounts.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    private var categoryJob: kotlinx.coroutines.Job? = null

    private fun observeCategories(type: TransactionType) {
        categoryJob?.cancel()
        if (type == TransactionType.TRANSFER) {
            _uiState.update { it.copy(categoryNodes = emptyList(), selectedCategoryId = null) }
            return
        }
        categoryJob = viewModelScope.launch {
            repository.observeCategoryTree(type).collect { nodes ->
                _uiState.update { state ->
                    // 종류를 바꾸거나 고른 카테고리가 지워지면 선택은 무효다.
                    val stillValid = nodes.flatten().any { it.id == state.selectedCategoryId }
                    state.copy(
                        categoryNodes = nodes,
                        selectedCategoryId = if (stillValid) state.selectedCategoryId else null,
                    )
                }
            }
        }
    }

    fun selectType(type: TransactionType) {
        if (_uiState.value.type == type) return
        _uiState.update { it.copy(type = type, selectedCategoryId = null, errorMessage = null) }
        observeCategories(type)
    }

    fun appendDigit(digit: Char) {
        _uiState.update { state ->
            val next = (state.amountText + digit).trimStart('0')
            if (next.length > MAX_AMOUNT_DIGITS) state
            else state.copy(amountText = next, errorMessage = null)
        }
    }

    /** 000, 만 같은 빠른 입력 버튼용. */
    fun appendZeros(count: Int) {
        if (_uiState.value.amountText.isEmpty()) return
        repeat(count) { appendDigit('0') }
    }

    fun deleteLastDigit() {
        _uiState.update { it.copy(amountText = it.amountText.dropLast(1), errorMessage = null) }
    }

    fun clearAmount() {
        _uiState.update { it.copy(amountText = "", errorMessage = null) }
    }

    fun selectCategory(id: Long) {
        _uiState.update { it.copy(selectedCategoryId = id, errorMessage = null) }
    }

    fun selectAccount(id: Long) {
        _uiState.update { state ->
            state.copy(
                selectedAccountId = id,
                // 이체에서 출금/입금이 같아지면 입금 선택을 비운다.
                selectedToAccountId = state.selectedToAccountId.takeIf { it != id },
                errorMessage = null,
            )
        }
    }

    fun selectToAccount(id: Long) {
        _uiState.update { it.copy(selectedToAccountId = id, errorMessage = null) }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateMemo(memo: String) {
        _uiState.update { it.copy(memo = memo) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update { it.copy(errorMessage = validationMessage(state)) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.addTransaction(
                    amount = state.amount,
                    type = state.type,
                    categoryId = state.selectedCategoryId,
                    accountId = requireNotNull(state.selectedAccountId),
                    toAccountId = state.selectedToAccountId,
                    date = state.date,
                    memo = state.memo,
                    nowMillis = System.currentTimeMillis(),
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaved = true) }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "저장에 실패했습니다.") }
            }
        }
    }

    private fun validationMessage(state: EntryUiState): String = when {
        state.amount <= 0 -> "금액을 입력해 주세요."
        state.selectedAccountId == null -> "자산을 선택해 주세요."
        state.isTransfer && state.selectedToAccountId == null -> "입금 자산을 선택해 주세요."
        state.isTransfer && state.selectedToAccountId == state.selectedAccountId ->
            "출금 자산과 입금 자산이 같습니다."

        else -> "카테고리를 선택해 주세요."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { EntryViewModel(appContainer().ledgerRepository) }
        }
    }
}
