package com.hj0128.moneybook.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.util.formatAmount
import com.hj0128.moneybook.util.formatFull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 백업 / 내보내기.
 *
 * 모든 기록이 이 폰 안의 DB 파일 하나에만 있다. 폰을 바꾸거나 앱을 지우면 함께 사라지므로
 * 사용자가 직접 파일로 꺼내 둘 길을 둔다. 세 가지는 쓰임이 달라 칸을 나눈다.
 * 엑셀로 보기(CSV) / 통째로 보관(백업) / 되돌리기(복원).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 사용자가 고른 곳(다운로드, 구글 드라이브 …)에 쓴다. 저장소 권한이 필요 없다.
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let(viewModel::createBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::inspect)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = { Text("백업 / 내보내기") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // 큰 DB 는 복사에 몇 초 걸릴 수 있다. 눌렀는데 아무 반응이 없으면 다시 누른다.
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BackupSection(
                    title = "백업",
                    description = "카테고리·자산·한도·나눈 결제까지 앱 데이터 전체를 파일 하나로 저장합니다. " +
                        "구글 드라이브나 PC처럼 이 폰 밖에 옮겨 두세요.",
                    status = lastBackupAt?.let { "마지막 백업: ${it.toLocalDate().formatFull()}" }
                        ?: "아직 백업한 적이 없습니다.",
                    statusIsWarning = lastBackupAt == null,
                ) {
                    Button(
                        onClick = { backupLauncher.launch("가계부-백업-${LocalDate.now()}.db") },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("백업 파일 만들기") }
                }

                BackupSection(
                    title = "복원",
                    description = "백업 파일로 되돌립니다. 지금 앱에 있는 데이터는 모두 백업 파일 내용으로 바뀌고, " +
                        "복원이 끝나면 앱이 다시 켜집니다.",
                ) {
                    OutlinedButton(
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("백업 파일에서 복원") }
                }

                BackupSection(
                    title = "엑셀로 내보내기",
                    description = "모든 내역을 CSV 파일로 저장합니다. 엑셀이나 구글 시트에서 열어 볼 수 있습니다. " +
                        "보기용이라 이 파일로는 복원할 수 없습니다.",
                ) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch("가계부-${LocalDate.now()}.csv") },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("CSV 파일 저장") }
                }
            }
        }
    }

    state.pendingRestore?.let { info ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("이 백업으로 복원할까요?") },
            text = {
                val last = info.lastDateEpochDay?.let { "마지막 내역 ${LocalDate.ofEpochDay(it).formatFull()}" }
                Text(
                    listOfNotNull("내역 ${info.transactionCount.toLong().formatAmount()}건", last)
                        .joinToString(", ") +
                        "\n\n지금 앱에 있는 데이터는 모두 이 백업 내용으로 바뀝니다. 복원이 끝나면 앱이 다시 켜집니다.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("복원") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("취소") } },
        )
    }
}

@Composable
private fun BackupSection(
    title: String,
    description: String,
    status: String? = null,
    statusIsWarning: Boolean = false,
    action: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    // 한 번도 안 했으면 눈에 띄게. 이 화면에 들어온 이유를 바로 알려준다.
                    color = if (statusIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
