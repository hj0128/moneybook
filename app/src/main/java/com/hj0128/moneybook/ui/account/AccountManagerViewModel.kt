package com.hj0128.moneybook.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountNode
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.repository.AccountLimit
import com.hj0128.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 추가/수정 다이얼로그가 열려 있을 때의 대상. 카테고리 관리와 같은 갈래다. */
sealed interface AccountEditTarget {
    /** 새 상위 자산. */
    data object NewTopLevel : AccountEditTarget

    /** [parent] 밑에 새 하위 자산. */
    data class NewChild(val parent: AccountBalance) : AccountEditTarget

    /** 기존 자산 수정. */
    data class Edit(val account: AccountBalance) : AccountEditTarget
}

/**
 * 삭제 다이얼로그 상태.
 *
 * [candidates] 가 비어 있으면 옮길 곳이 없어 지울 수 없다.
 * [selfTransfers] 는 고른 대상으로 옮겼을 때 출금과 입금이 같아져 사라지는 이체 수라
 * 대상을 바꾸면 다시 세야 한다.
 */
data class AccountDeleteTarget(
    val account: AccountBalance,
    val usage: Int,
    /** 하위 자산 수. 지우면 하위는 고른 상위 밑으로 옮겨진다(카테고리 소분류와 같다). */
    val children: Int,
    val candidates: List<AccountBalance>,
    val selectedTargetId: Long?,
    val selfTransfers: Int,
    /** 자산이 이것 하나뿐이다. 거래가 갈 곳이 없어 지울 수 없다. */
    val isLast: Boolean,
) {
    /** 내역도 하위도 없으면 옮길 것이 없으므로 어디로 옮길지 묻지 않는다. */
    val needsReassign: Boolean get() = usage > 0 || children > 0

    val canDelete: Boolean
        get() = !isLast && (!needsReassign || selectedTargetId != null)
}

class AccountManagerViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    val nodes: StateFlow<List<AccountNode>> =
        repository.observeAccountTree().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 열어 둔 상위 자산. null 이면 상위 목록을 보는 중이다. */
    private val _openParentId = MutableStateFlow<Long?>(null)

    /**
     * 열어 둔 상위 자산.
     *
     * 목록에서 찾아 쓰므로, 그 자산이 지워지면 자연히 null 이 되어 상위 목록으로 돌아온다.
     */
    val openParent: StateFlow<AccountNode?> =
        combine(nodes, _openParentId) { list, id ->
            list.firstOrNull { it.account.id == id }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _editTarget = MutableStateFlow<AccountEditTarget?>(null)
    val editTarget: StateFlow<AccountEditTarget?> = _editTarget.asStateFlow()

    private val _deleteTarget = MutableStateFlow<AccountDeleteTarget?>(null)
    val deleteTarget: StateFlow<AccountDeleteTarget?> = _deleteTarget.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 순서 변경 중인 목록. null 이면 보통 상태다.
     *
     * DB 대신 이 사본을 화면에 보여준다. 한 칸 옮길 때마다 저장하면 저장 결과가 Flow 로
     * 되돌아오는 사이에 화면이 튄다. 손을 뗄 때 한 번만 저장한다.
     */
    private val _reorderDraft = MutableStateFlow<List<AccountBalance>?>(null)
    val reorderDraft: StateFlow<List<AccountBalance>?> = _reorderDraft.asStateFlow()

    fun openParent(accountId: Long) {
        _openParentId.value = accountId
        stopReorder()
    }

    fun closeParent() {
        _openParentId.value = null
        stopReorder()
    }

    /** 지금 보고 있는 목록(상위 목록이거나 열어 둔 상위의 하위)의 순서를 바꾸기 시작한다. */
    fun startReorder() {
        _reorderDraft.value = openParent.value?.children ?: nodes.value.map { it.account }
    }

    fun moveInDraft(from: Int, to: Int) {
        val draft = _reorderDraft.value ?: return
        if (from !in draft.indices || to !in draft.indices) return
        _reorderDraft.value = draft.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** 손을 뗐을 때 저장. 모드는 그대로 두어 이어서 다른 줄도 옮길 수 있다. */
    fun commitOrder() {
        val draft = _reorderDraft.value ?: return
        viewModelScope.launch { repository.reorderAccounts(draft.map { it.id }) }
    }

    fun stopReorder() {
        _reorderDraft.value = null
    }

    fun startAddTopLevel() {
        _editTarget.value = AccountEditTarget.NewTopLevel
    }

    fun startAddChild(parent: AccountBalance) {
        _editTarget.value = AccountEditTarget.NewChild(parent)
    }

    fun startEdit(account: AccountBalance) {
        _editTarget.value = AccountEditTarget.Edit(account)
    }

    fun dismissEdit() {
        _editTarget.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun submitEdit(
        name: String,
        type: AccountType,
        colorArgb: Int,
        initialBalance: Long,
        limit: AccountLimit?,
        parentId: Long?,
    ) {
        val target = _editTarget.value ?: return
        viewModelScope.launch {
            val result = when (target) {
                AccountEditTarget.NewTopLevel ->
                    repository.addAccount(name, type, colorArgb, initialBalance, limit, parentId = null).map { }

                is AccountEditTarget.NewChild ->
                    repository.addAccount(name, type, colorArgb, initialBalance, limit, target.parent.id).map { }

                is AccountEditTarget.Edit -> repository.updateAccount(
                    target.account.id, name, type, colorArgb, initialBalance, limit, parentId,
                )
            }
            result
                .onSuccess { dismissEdit() }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun startDelete(account: AccountBalance) {
        viewModelScope.launch {
            val tree = nodes.value
            val all = tree.flatMap { listOf(it.account) + it.children }
            val usage = repository.accountUsage(account.id)
            val children = repository.accountChildCount(account.id)
            // 상위 자산은 하위째로 옮겨야 하므로 다른 상위로만 보낼 수 있다(3단계가 되지 않게).
            // 카테고리 관리의 삭제와 같은 규칙이다.
            val candidates = if (usage == 0 && children == 0) {
                emptyList()
            } else if (account.isTopLevel) {
                tree.map { it.account }.filter { it.id != account.id }
            } else {
                all.filter { it.id != account.id }
            }
            val first = candidates.firstOrNull()
            _deleteTarget.value = AccountDeleteTarget(
                account = account,
                usage = usage,
                children = children,
                candidates = candidates,
                selectedTargetId = first?.id,
                selfTransfers = first?.let {
                    repository.selfTransfersAfterMove(account.id, it.id)
                } ?: 0,
                isLast = all.size <= 1,
            )
        }
    }

    /** 이전 대상을 바꾸면 사라질 이체 수도 달라진다. */
    fun selectDeleteTarget(toId: Long) {
        val current = _deleteTarget.value ?: return
        viewModelScope.launch {
            _deleteTarget.value = current.copy(
                selectedTargetId = toId,
                selfTransfers = repository.selfTransfersAfterMove(current.account.id, toId),
            )
        }
    }

    fun dismissDelete() {
        _deleteTarget.value = null
    }

    fun confirmDelete() {
        val target = _deleteTarget.value ?: return
        val toId = if (target.needsReassign) target.selectedTargetId ?: return else null
        viewModelScope.launch {
            repository.deleteAccount(target.account.id, toId)
                .onSuccess { _deleteTarget.value = null }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AccountManagerViewModel(repository = appContainer().ledgerRepository)
            }
        }
    }
}
