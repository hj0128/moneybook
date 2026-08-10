package com.hj0128.moneybook.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 추가/수정 다이얼로그가 열려 있을 때의 대상. */
sealed interface AccountEditTarget {
    data object New : AccountEditTarget
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
    val candidates: List<AccountBalance>,
    val selectedTargetId: Long?,
    val selfTransfers: Int,
) {
    /** 쓰인 적이 없으면 옮길 것이 없으므로 어디로 옮길지 묻지 않는다. */
    val needsReassign: Boolean get() = usage > 0

    /** 마지막 자산이면 지울 수 없다. */
    val canDelete: Boolean
        get() = candidates.isNotEmpty() && (!needsReassign || selectedTargetId != null)
}

class AccountManagerViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    val accounts: StateFlow<List<AccountBalance>> =
        repository.observeAccounts().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _editTarget = MutableStateFlow<AccountEditTarget?>(null)
    val editTarget: StateFlow<AccountEditTarget?> = _editTarget.asStateFlow()

    private val _deleteTarget = MutableStateFlow<AccountDeleteTarget?>(null)
    val deleteTarget: StateFlow<AccountDeleteTarget?> = _deleteTarget.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun startAdd() {
        _editTarget.value = AccountEditTarget.New
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

    fun submitEdit(name: String, type: AccountType, colorArgb: Int, initialBalance: Long) {
        val target = _editTarget.value ?: return
        viewModelScope.launch {
            val result = when (target) {
                AccountEditTarget.New ->
                    repository.addAccount(name, type, colorArgb, initialBalance).map { }

                is AccountEditTarget.Edit ->
                    repository.updateAccount(target.account.id, name, type, colorArgb, initialBalance)
            }
            result
                .onSuccess { dismissEdit() }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun startDelete(account: AccountBalance) {
        viewModelScope.launch {
            val candidates = accounts.value.filter { it.id != account.id }
            val first = candidates.firstOrNull()
            _deleteTarget.value = AccountDeleteTarget(
                account = account,
                usage = repository.accountUsage(account.id),
                candidates = candidates,
                selectedTargetId = first?.id,
                selfTransfers = first?.let {
                    repository.selfTransfersAfterMove(account.id, it.id)
                } ?: 0,
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
