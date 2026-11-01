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
import com.hj0128.moneybook.data.model.RepeatUnit
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.flatten
import com.hj0128.moneybook.data.repository.LedgerRepository
import com.hj0128.moneybook.data.repository.PaymentPart
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
    /**
     * 나눠서 입력하는 줄들. 비어 있으면 나누지 않은 평범한 입력이다.
     *
     * 마지막 줄은 금액을 받지 않고 "총액 - 앞 줄들"로 채운다. 나눈 합이 총액과 어긋날 수가 없고,
     * 둘로 나누는 가장 흔한 경우에는 한 줄만 적으면 된다.
     */
    val parts: List<SplitPart> = emptyList(),
    /** 키패드가 무엇을 적는 중인지. null 이면 총액, 숫자면 그 번째 줄의 금액. */
    val keypadTarget: Int? = null,
    /** 고치는 내역이 이미 나눈 결제였는지. 한 줄로 되돌려도 묶음을 지우고 다시 저장해야 한다. */
    val editingSplit: Boolean = false,
    /** 반복 주기. null 이면 반복하지 않는다. 새로 넣을 때와 고정 지출을 고칠 때만 고를 수 있다. */
    val repeatUnit: RepeatUnit? = null,
    /** 고정 지출 자체를 고치는 중이면 그 id. 이때 날짜는 "다음에 넣을 날"이다. */
    val editingRecurringId: Long? = null,
    /** 고치는 내역이 고정 지출이 넣은 것이면 그 고정 지출. 이 내역만 바뀐다는 것을 알려준다. */
    val sourceRecurring: SourceRecurring? = null,
    /**
     * 불러온 고정 지출의 기준일. 날짜나 주기를 새로 고르기 전까지 지킨다.
     *
     * 31일짜리의 다음 날짜는 11월 30일일 수 있다. 날짜에서 다시 읽으면 손대지 않고 저장만 해도 30일로 바뀐다.
     */
    val keptAnchor: LocalDate? = null,
) {
    /** 주기를 정하는 날. 매주면 이 날의 요일, 매월이면 일, 매년이면 월·일. */
    val repeatAnchor: LocalDate get() = keptAnchor ?: date

    val isEditing: Boolean get() = editingId != null

    val isEditingRecurring: Boolean get() = editingRecurringId != null

    /** 저장하면 내역이 아니라 고정 지출이 만들어지거나 바뀐다. */
    val savesRecurring: Boolean get() = isEditingRecurring || (repeatUnit != null && !isEditing)

    val isSplit: Boolean get() = parts.isNotEmpty()

    /** [index] 번째 줄의 금액. 마지막 줄은 남은 금액이다. */
    fun partAmount(index: Int): Long =
        if (index == parts.lastIndex) {
            amount - parts.dropLast(1).sumOf { it.amount }
        } else {
            parts[index].amount
        }

    val amount: Long get() = amountText.toLongOrNull() ?: 0L

    val isTransfer: Boolean get() = type == TransactionType.TRANSFER

    /** 카테고리 줄에 보여줄 값. 소분류면 "식비 › 외식". */
    val selectedCategory: SelectedCategory? get() = categoryNodes.findSelected(selectedCategoryId)

    val selectedAccountName: String? get() = accountNameOf(selectedAccountId)

    val selectedToAccountName: String? get() = accountNameOf(selectedToAccountId)

    private fun accountNameOf(id: Long?): String? =
        id?.let { accounts.firstOrNull { account -> account.id == it }?.name }

    val canSave: Boolean
        get() = if (isSplit) canSaveSplit else canSaveSingle

    private val canSaveSplit: Boolean
        get() = amount > 0 &&
            parts.indices.all { partAmount(it) > 0 } &&
            parts.all { it.categoryId != null && it.accountId != null }

    private val canSaveSingle: Boolean
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
    private val recurringId: Long? = null,
    startRepeating: Boolean = false,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EntryUiState(
            editingId = editingId,
            editingRecurringId = recurringId,
            // 고정 지출 화면에서 추가하면 가장 흔한 매월로 시작한다. 고칠 때는 불러온 값으로 바뀐다.
            repeatUnit = if (startRepeating || recurringId != null) RepeatUnit.MONTH else null,
        )
    )
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    init {
        // 지출 카테고리를 기본으로 먼저 띄운다(가장 많이 쓰는 입력이라).
        observeCategories(TransactionType.EXPENSE)
        if (editingId != null) loadForEdit(editingId)
        if (recurringId != null) loadRecurring(recurringId)
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
            val all = repository.findPaymentParts(id)
            val entity = all.firstOrNull() ?: return@launch
            val split = all.size > 1
            _uiState.update { state ->
                state.copy(
                    type = entity.type,
                    amountText = all.sumOf { it.amount }.toString(),
                    parts = if (split) {
                        all.map { SplitPart(it.categoryId, it.accountId, it.amount.toString(), it.memo) }
                    } else {
                        emptyList()
                    },
                    editingSplit = split,
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
            val source = entity.recurringId?.let { repository.findRecurring(it) }
            if (source != null) {
                val label = source.repeatUnit.describe(source.anchor)
                _uiState.update { it.copy(sourceRecurring = SourceRecurring(source.id, label)) }
            }
        }
    }

    /** 고정 지출을 고치는 화면. 내역 화면과 같은 칸을 쓰고, 날짜 칸에 다음에 넣을 날을 채운다. */
    private fun loadRecurring(id: Long) {
        viewModelScope.launch {
            val rule = repository.findRecurring(id) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    type = rule.type,
                    amountText = rule.amount.toString(),
                    date = rule.nextDate,
                    merchant = rule.merchant,
                    memo = rule.memo,
                    selectedCategoryId = rule.categoryId,
                    selectedAccountId = rule.accountId,
                    selectedToAccountId = rule.toAccountId,
                    repeatUnit = rule.repeatUnit,
                    keptAnchor = rule.anchor,
                )
            }
            if (rule.type != TransactionType.EXPENSE) observeCategories(rule.type)
        }
    }

    /**
     * 주기를 바꾸면 기준일도 지금 날짜로 다시 잡는다. 31일짜리 매월을 매주로 바꿨는데 기준일이 8월 31일로
     * 남아 있으면, 화면의 날짜와 다른 요일이 적혀 무엇이 들어갈지 알 수 없다.
     */
    fun setRepeatUnit(unit: RepeatUnit?) {
        _uiState.update { state ->
            if (state.repeatUnit == unit) state
            else state.copy(repeatUnit = unit, keptAnchor = null, errorMessage = null)
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
                    val validIds = nodes.flatten().map { it.id }.toSet()
                    state.copy(
                        categoryNodes = nodes,
                        selectedCategoryId = state.selectedCategoryId?.takeIf { it in validIds },
                        parts = state.parts.map { part ->
                            part.copy(categoryId = part.categoryId?.takeIf { it in validIds })
                        },
                    )
                }
            }
        }
    }

    fun selectType(type: TransactionType) {
        if (_uiState.value.type == type) return
        _uiState.update { state ->
            state.copy(
                type = type,
                selectedCategoryId = null,
                // 이체는 나눌 수 없다. 수입/지출을 오가면 카테고리만 다시 고르면 된다.
                parts = if (type == TransactionType.TRANSFER) {
                    emptyList()
                } else {
                    state.parts.map { it.copy(categoryId = null) }
                },
                keypadTarget = null,
                errorMessage = null,
            )
        }
        observeCategories(type)
    }

    fun appendDigit(digit: Char) {
        editTargetText { text ->
            val next = (text + digit).trimStart('0')
            if (next.length > MAX_AMOUNT_DIGITS) text else next
        }
    }

    /** 000, 만 같은 빠른 입력 버튼용. */
    fun appendZeros(count: Int) {
        if (_uiState.value.targetText.isEmpty()) return
        repeat(count) { appendDigit('0') }
    }

    fun deleteLastDigit() {
        editTargetText { it.dropLast(1) }
    }

    fun clearAmount() {
        editTargetText { "" }
    }

    /** 키패드가 가리키는 칸(총액 또는 나눈 줄 하나)의 글자를 바꾼다. */
    private fun editTargetText(transform: (String) -> String) {
        _uiState.update { state ->
            val target = state.keypadTarget
            if (target == null || target !in state.parts.indices) {
                state.copy(amountText = transform(state.amountText), errorMessage = null)
            } else {
                state.copy(
                    parts = state.parts.updated(target) { it.copy(amountText = transform(it.amountText)) },
                    errorMessage = null,
                )
            }
        }
    }

    private val EntryUiState.targetText: String
        get() = keypadTarget?.let { parts.getOrNull(it)?.amountText } ?: amountText

    // ---- 나눠서 입력 ----

    /**
     * 나누기 시작. 지금 고른 카테고리·결제수단을 첫 줄로 옮기고, 둘째 줄이 나머지를 받는다.
     * 첫 줄 금액부터 적으면 되도록 키패드를 그 줄에 맞춰 올린다.
     */
    fun startSplit() {
        _uiState.update { state ->
            state.copy(
                // 적어 둔 메모는 첫 줄로 옮긴다. 나누는 동안 위쪽 메모 칸은 감춰진다.
                parts = listOf(
                    SplitPart(state.selectedCategoryId, state.selectedAccountId, memo = state.memo),
                    SplitPart(null, state.selectedAccountId),
                ),
                keypadTarget = 0,
                keypadVisible = true,
                errorMessage = null,
            )
        }
    }

    /** 줄 추가. 나머지를 받는 마지막 줄은 그대로 마지막에 두고 그 위에 새 줄을 끼운다. */
    fun addPart() {
        _uiState.update { state ->
            val insertAt = state.parts.lastIndex.coerceAtLeast(0)
            val account = state.parts.lastOrNull()?.accountId ?: state.selectedAccountId
            state.copy(
                parts = state.parts.toMutableList().apply { add(insertAt, SplitPart(null, account)) },
                keypadTarget = insertAt,
                keypadVisible = true,
            )
        }
    }

    /** 줄 삭제. 한 줄만 남으면 나누기를 끝내고 그 줄의 값으로 평범한 입력이 된다. */
    fun removePart(index: Int) {
        _uiState.update { state ->
            val remaining = state.parts.filterIndexed { i, _ -> i != index }
            if (remaining.size >= 2) {
                state.copy(parts = remaining, keypadTarget = null)
            } else {
                val last = remaining.singleOrNull()
                state.copy(
                    parts = emptyList(),
                    keypadTarget = null,
                    selectedCategoryId = last?.categoryId ?: state.selectedCategoryId,
                    selectedAccountId = last?.accountId ?: state.selectedAccountId,
                    memo = last?.memo ?: state.memo,
                )
            }
        }
    }

    fun selectPartCategory(index: Int, id: Long) {
        _uiState.update { state ->
            state.copy(parts = state.parts.updated(index) { it.copy(categoryId = id) }, errorMessage = null)
        }
    }

    fun selectPartAccount(index: Int, id: Long) {
        _uiState.update { state ->
            state.copy(parts = state.parts.updated(index) { it.copy(accountId = id) }, errorMessage = null)
        }
    }

    fun updatePartMemo(index: Int, memo: String) {
        _uiState.update { state -> state.copy(parts = state.parts.updated(index) { it.copy(memo = memo) }) }
    }

    /** 줄 금액을 누르면 키패드가 그 줄을 적는다. 마지막 줄은 남은 금액이라 적을 수 없다. */
    fun editPartAmount(index: Int) {
        _uiState.update { state ->
            if (index == state.parts.lastIndex) state
            else state.copy(keypadTarget = index, keypadVisible = true)
        }
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
        // 날짜를 새로 고르면 그 날의 "일"이 매달 넣을 날이 된다.
        _uiState.update { it.copy(date = date, keptAnchor = null) }
    }

    fun updateMerchant(merchant: String) {
        _uiState.update { it.copy(merchant = merchant, errorMessage = null) }
    }

    fun updateMemo(memo: String) {
        _uiState.update { it.copy(memo = memo) }
    }

    /** 총액을 눌렀을 때. */
    fun showKeypad() {
        _uiState.update { it.copy(keypadVisible = true, keypadTarget = null) }
    }

    /** 금액 말고 다른 것을 만지기 시작하면 키패드는 자리를 비켜준다. */
    fun hideKeypad() {
        _uiState.update { it.copy(keypadVisible = false) }
    }

    /** 수정 중인 내역 삭제. 목록에서 길게 누르는 것과 같은 동작을 이 화면에도 둔다. */
    fun delete() {
        _uiState.value.editingRecurringId?.let { recurring ->
            viewModelScope.launch {
                // 그만두기만 한다. 이미 들어간 내역은 실제로 나간 돈이라 남는다.
                repository.deleteRecurring(recurring)
                _uiState.update { it.copy(isDeleted = true) }
            }
            return
        }
        val id = _uiState.value.editingId ?: return
        viewModelScope.launch {
            // 이 화면은 결제 하나를 통째로 다룬다. 나눈 결제면 묶음 전체가 지워진다.
            repository.deletePayment(id)
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
        if (state.savesRecurring) {
            saveRecurring(state)
            return
        }
        if (state.isSplit || state.editingSplit) {
            savePayment(state)
            return
        }
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

    private fun saveRecurring(state: EntryUiState) {
        viewModelScope.launch {
            repository.saveRecurring(
                id = state.editingRecurringId,
                amount = state.amount,
                type = state.type,
                categoryId = state.selectedCategoryId,
                accountId = requireNotNull(state.selectedAccountId),
                toAccountId = state.selectedToAccountId,
                repeatUnit = state.repeatUnit ?: RepeatUnit.MONTH,
                firstDate = state.date,
                anchor = state.repeatAnchor,
                merchant = state.merchant,
                memo = state.memo,
            )
                .onSuccess { _uiState.update { it.copy(isSaved = true) } }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "저장에 실패했습니다.") }
                }
        }
    }

    /** 나눈 결제 저장. 이미 나눈 결제를 한 줄로 되돌린 경우도 여기로 와서 묶음째 바꾼다. */
    private fun savePayment(state: EntryUiState) {
        val parts = if (state.isSplit) {
            state.parts.mapIndexed { index, part ->
                PaymentPart(
                    amount = state.partAmount(index),
                    categoryId = requireNotNull(part.categoryId),
                    accountId = requireNotNull(part.accountId),
                    memo = part.memo,
                )
            }
        } else {
            listOf(
                PaymentPart(
                    amount = state.amount,
                    categoryId = requireNotNull(state.selectedCategoryId),
                    accountId = requireNotNull(state.selectedAccountId),
                    memo = state.memo,
                )
            )
        }
        viewModelScope.launch {
            repository.savePayment(
                replacingId = state.editingId,
                parts = parts,
                type = state.type,
                date = state.date,
                merchant = state.merchant,
                nowMillis = System.currentTimeMillis(),
            )
                .onSuccess { _uiState.update { it.copy(isSaved = true) } }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "저장에 실패했습니다.") }
                }
        }
    }

    private fun validationMessage(state: EntryUiState): String = when {
        state.isSplit && state.amount <= 0 -> "총액을 먼저 입력해 주세요."
        state.isSplit && state.parts.indices.any { state.partAmount(it) <= 0 } ->
            "나눈 금액을 확인해 주세요. 남은 금액이 0원보다 커야 합니다."

        state.isSplit -> "나눈 줄마다 카테고리와 결제수단을 골라 주세요."
        state.amount <= 0 -> "금액을 입력해 주세요."
        state.selectedAccountId == null -> "자산을 선택해 주세요."
        state.isTransfer && state.selectedToAccountId == null -> "입금 자산을 선택해 주세요."
        state.isTransfer && state.selectedToAccountId == state.selectedAccountId ->
            "출금 자산과 입금 자산이 같습니다."

        else -> "카테고리를 선택해 주세요."
    }

    companion object {
        /**
         * [editingId] 가 있으면 그 내역을, [recurringId] 가 있으면 그 고정 지출을 고치는 화면이 된다.
         * [startRepeating] 은 고정 지출 화면에서 "추가"로 들어왔을 때 반복을 켠 채 시작한다.
         */
        fun factory(
            editingId: Long? = null,
            recurringId: Long? = null,
            startRepeating: Boolean = false,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EntryViewModel(
                    repository = appContainer().ledgerRepository,
                    editingId = editingId,
                    recurringId = recurringId,
                    startRepeating = startRepeating,
                )
            }
        }
    }
}

/** 고치는 내역을 넣은 고정 지출. */
data class SourceRecurring(val id: Long, val label: String)

/** 나눠서 입력하는 한 줄. 금액은 키패드로 적은 글자 그대로 둔다(마지막 줄은 쓰지 않는다). */
data class SplitPart(
    val categoryId: Long?,
    val accountId: Long?,
    val amountText: String = "",
    val memo: String = "",
) {
    val amount: Long get() = amountText.toLongOrNull() ?: 0L
}

private fun <T> List<T>.updated(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) transform(item) else item }

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
