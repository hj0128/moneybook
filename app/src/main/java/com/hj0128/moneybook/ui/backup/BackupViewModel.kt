package com.hj0128.moneybook.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.backup.BackupInfo
import com.hj0128.moneybook.data.backup.BackupManager
import com.hj0128.moneybook.data.prefs.SettingsRepository
import com.hj0128.moneybook.util.formatAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    /** 파일을 쓰거나 읽는 중. 그동안 버튼을 막아 같은 작업이 두 번 돌지 않게 한다. */
    val busy: Boolean = false,
    /** 스낵바로 한 번 보여줄 결과. 보여주고 나면 비운다. */
    val message: String? = null,
    /** 고른 백업을 열어 본 결과. 있으면 "정말 복원할까요?"를 묻는 중이다. */
    val pendingRestore: BackupInfo? = null,
)

class BackupViewModel(
    private val backupManager: BackupManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    val lastBackupAt: StateFlow<Long?> = settings.lastBackupAt.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun exportCsv(uri: Uri) = runTask {
        backupManager.exportCsv(uri).fold(
            onSuccess = { count -> "내역 ${count.toLong().formatAmount()}건을 CSV 파일로 저장했습니다." },
            onFailure = { "내보내지 못했습니다. ${it.message.orEmpty()}" },
        )
    }

    fun createBackup(uri: Uri) = runTask {
        backupManager.createBackup(uri).fold(
            onSuccess = {
                settings.setLastBackupAt(System.currentTimeMillis())
                "백업 파일을 저장했습니다."
            },
            onFailure = { "백업하지 못했습니다. ${it.message.orEmpty()}" },
        )
    }

    /** 고른 파일을 먼저 열어 본다. 바로 덮어쓰지 않고 무엇이 들어 있는지 보여준 뒤 묻는다. */
    fun inspect(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            backupManager.inspect(uri)
                .onSuccess { info -> _state.update { it.copy(busy = false, pendingRestore = info) } }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, message = error.message ?: "백업 파일을 읽지 못했습니다.") }
                }
        }
    }

    fun cancelRestore() {
        _state.update { it.copy(pendingRestore = null) }
    }

    /** 복원이 끝나면 앱이 다시 켜지므로 성공 메시지를 띄울 자리가 없다. 실패만 알린다. */
    fun confirmRestore() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, pendingRestore = null) }
            backupManager.restoreStaged()
                .onSuccess { backupManager.restartApp() }
                .onFailure { error ->
                    _state.update { it.copy(busy = false, message = "복원하지 못했습니다. ${error.message.orEmpty()}") }
                }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun runTask(block: suspend () -> String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val message = block()
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                BackupViewModel(
                    backupManager = container.backupManager,
                    settings = container.settingsRepository,
                )
            }
        }
    }
}
