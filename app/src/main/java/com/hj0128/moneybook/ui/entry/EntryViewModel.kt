package com.hj0128.moneybook.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.local.entity.CategoryEntity
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
    /** 어디서 썼는지. 비워도 저장된다(현금 지출처럼 사용처가 없는 경우도 있다). */
    val merchant: String = "",
    val memo: String = "",
    /** 키패드는 접힌 채로 시작한다. 금액을 눌러야 올라온다. */
    val keypadVisible: Boolean = false,
    /** 대분류 > 소분류로 묶은 카테고리. 화면은 묶음 그대로 그린다. */
    val categoryNodes: List<CategoryNode> = emptyList(),
    val selectedCategoryId: Long? = null,
    val accounts: List<AccountBalance> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    /** 기존 내역을 고치는 중이면 그 id. 새로 넣는 중이면 null. */
    val editingId: Long? = null,
    val isDeleted: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null

    val amount: Long get() = amountText.toLongOrNull() ?: 0L

    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    /** 카테고리 줄에 보여줄 값. 소분류면 "식비 › 외식". */
    val selectedCategory: SelectedCategory? get() = categoryNodes.findSelected(selectedCategoryId)

    val selectedAccountName: String? get() = accountNameOf(selectedAccountId)

    val selectedToAccountName: String? get() = accountNameOf(selectedToAccountId)

    private fun accountNameOf(id: Long?): String? =
        id?.let { accounts.firstOrNull { account -> account.id == it }?.name }

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
    private val editingId: Long? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryUiState(editingId = editingId))
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    init {
        // 지출 카테고리를 기본으로 먼저 띄운다(가장 많이 쓰는 입력이라).
        observeCategories(TransactionType.EXPENSE)
        if (editingId != null) loadForEdit(editingId)
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

    /**
     * 고칠 내역을 불러와 화면을 채운다.
     *
     * 자산 목록을 보는 쪽에서 selectedAccountId 가 비어 있으면 첫 자산으로 채우는데,
     * 여기서 실제 값을 덮어쓰므로 그 기본값과 부딪히지 않는다.
     */
    private fun loadForEdit(id: Long) {
        viewModelScope.launch {
            val entity = repository.findTransaction(id) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    type = entity.type,
                    amountText = entity.amount.toString(),
                    date = LocalDate.ofEpochDay(entity.dateEpochDay),
                    merchant = entity.merchant,
                    memo = entity.memo,
                    selectedCategoryId = entity.categoryId,
                    selectedAccountId = entity.accountId,
                    selectedToAccountId = entity.toAccountId,
                )
            }
            // 종류가 지출이 아니면 그 종류의 카테고리를 다시 구독해야 한다.
            if (entity.type != TransactionType.EXPENSE) observeCategories(entity.type)
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

    fun updateMerchant(merchant: String) {
        _uiState.update { it.copy(merchant = merchant, errorMessage = null) }
    }

    fun updateMemo(memo: String) {
        _uiState.update { it.copy(memo = memo) }
    }

    fun showKeypad() {
        _uiState.update { it.copy(keypadVisible = true) }
    }

    /** 금액 말고 다른 것을 만지기 시작하면 키패드는 자리를 비켜준다. */
    fun hideKeypad() {
        _uiState.update { it.copy(keypadVisible = false) }
    }

    /** 수정 중인 내역 삭제. 목록에서 길게 누르는 것과 같은 동작을 이 화면에도 둔다. */
    fun delete() {
        val id = _uiState.value.editingId ?: return
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update { it.copy(errorMessage = validationMessage(state)) }
            return
        }
        val id = state.editingId
        if (id != null) {
            viewModelScope.launch {
                repository.updateTransaction(
                    id = id,
                    amount = state.amount,
                    type = state.type,
                    categoryId = state.selectedCategoryId,
                    accountId = requireNotNull(state.selectedAccountId),
                    toAccountId = state.selectedToAccountId,
                    date = state.date,
                    merchant = state.merchant,
                    memo = state.memo,
                )
                    .onSuccess { _uiState.update { it.copy(isSaved = true) } }
                    .onFailure { error ->
                        _uiState.update { it.copy(errorMessage = error.message ?: "저장에 실패했습니다.") }
                    }
            }
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
                    merchant = state.merchant,
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
        /** [editingId] 가 있으면 그 내역을 고치는 화면이 된다. */
        fun factory(editingId: Long? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EntryViewModel(
                    repository = appContainer().ledgerRepository,
                    editingId = editingId,
                )
            }
        }
    }
}

/** 고른 카테고리와, 소분류라면 그 대분류 이름. */
data class SelectedCategory(
    val category: CategoryEntity,
    val parentName: String?,
) {
    val label: String get() = parentName?.let { "$it › ${category.name}" } ?: category.name
}

internal fun List<CategoryNode>.findSelected(categoryId: Long?): SelectedCategory? {
    if (categoryId == null) return null
    for (node in this) {
        if (node.category.id == categoryId) return SelectedCategory(node.category, null)
        node.children.firstOrNull { it.id == categoryId }?.let { child ->
            return SelectedCategory(child, node.category.name)
        }
    }
    return null
}
