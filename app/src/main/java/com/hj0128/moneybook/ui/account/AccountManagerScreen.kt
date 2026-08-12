package com.hj0128.moneybook.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.ui.common.ReorderableList
import com.hj0128.moneybook.ui.theme.CategoryPalette
import com.hj0128.moneybook.ui.theme.paletteColorOrDefault
import com.hj0128.moneybook.util.formatWon

private val accountTypes = listOf(AccountType.CASH, AccountType.BANK, AccountType.CARD)

/**
 * 자산 관리: 현금·계좌·카드를 추가/수정/삭제하고, 순서를 바꾼다.
 *
 * 여기서 정한 순서가 내역을 적을 때 결제수단이 놓이는 순서다. 자주 쓰는 것을 앞에 두면
 * 매번 찾아 누르는 손이 줄어든다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountManagerViewModel = viewModel(factory = AccountManagerViewModel.factory()),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val editTarget by viewModel.editTarget.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val reorderDraft by viewModel.reorderDraft.collectAsStateWithLifecycle()

    BackHandler(enabled = reorderDraft != null) { viewModel.stopReorder() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { if (reorderDraft != null) viewModel.stopReorder() else onBack() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = { Text(if (reorderDraft != null) "순서 변경" else "자산 관리") },
                actions = {
                    if (reorderDraft != null) {
                        TextButton(onClick = viewModel::stopReorder) { Text("완료") }
                    } else if (accounts.size >= 2) {
                        // 하나뿐이면 순서라는 게 없다.
                        IconButton(onClick = viewModel::startReorder) {
                            Icon(Icons.Default.SwapVert, contentDescription = "순서 변경")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // 순서를 바꾸는 중에는 추가 버튼을 감춘다. 목록이 바뀌면 끌던 자리가 어긋난다.
            if (reorderDraft == null) {
                FloatingActionButton(onClick = viewModel::startAdd) {
                    Icon(Icons.Default.Add, contentDescription = "자산 추가")
                }
            }
        },
    ) { innerPadding ->
        val draft = reorderDraft
        if (draft != null) {
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                Text(
                    text = "오른쪽 손잡이를 끌어 순서를 바꾸세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                ReorderableList(
                    items = draft,
                    keyOf = { it.id },
                    labelOf = { it.name },
                    onMove = viewModel::moveInDraft,
                    onDrop = viewModel::commitOrder,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) { account ->
                    ReorderRow(account = account)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(accounts, key = { it.id }) { account ->
                    AccountRow(
                        account = account,
                        onEdit = { viewModel.startEdit(account) },
                        onDelete = { viewModel.startDelete(account) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    editTarget?.let { target ->
        AccountEditDialog(
            target = target,
            errorMessage = errorMessage,
            onDismiss = viewModel::dismissEdit,
            onSubmit = viewModel::submitEdit,
            onErrorShown = viewModel::clearError,
        )
    }

    deleteTarget?.let { target ->
        AccountDeleteDialog(
            target = target,
            onSelectTarget = viewModel::selectDeleteTarget,
            onDismiss = viewModel::dismissDelete,
            onConfirm = viewModel::confirmDelete,
        )
    }
}

/** 순서 변경 모드의 줄. 이 모드에서는 눌러도 수정되지 않으므로 버튼을 빼고 이름만 남긴다. */
@Composable
private fun ReorderRow(account: AccountBalance) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(account.colorArgb)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = account.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = account.type.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountRow(
    account: AccountBalance,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "${account.name} 수정", onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(account.colorArgb)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${account.type.label} · ${account.balance.formatWon()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "${account.name} 수정",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "${account.name} 삭제",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountEditDialog(
    target: AccountEditTarget,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, type: AccountType, colorArgb: Int, initialBalance: Long) -> Unit,
    onErrorShown: () -> Unit,
) {
    val existing = (target as? AccountEditTarget.Edit)?.account
    var name by remember(target) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(target) { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var colorArgb by remember(target) {
        mutableIntStateOf(paletteColorOrDefault(existing?.colorArgb))
    }
    // 초기 잔액은 현재 잔액이 아니다. 현재 잔액은 거래를 누적해 나오는 값이라 여기서 못 고친다.
    var initialBalanceText by remember(target) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "자산 추가" else "자산 수정") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onErrorShown()
                    },
                    label = { Text("이름") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                DialogLabel("종류")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    accountTypes.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == type,
                            onClick = { type = option },
                            shape = SegmentedButtonDefaults.itemShape(index, accountTypes.size),
                        ) {
                            Text(option.label)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                DialogLabel("색상")
                PaletteGrid(selected = colorArgb, onSelect = { colorArgb = it })

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { input -> initialBalanceText = input.filter { it.isDigit() } },
                    label = { Text("시작 잔액") },
                    supportingText = {
                        Text(
                            if (existing == null) {
                                "비워두면 0원으로 시작합니다."
                            } else {
                                "지금 잔액 ${existing.balance.formatWon()} 은 거래에서 계산된 값입니다."
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(name, type, colorArgb, initialBalanceText.toLongOrNull() ?: 0L)
                },
                enabled = name.isNotBlank(),
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun DialogLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** 팔레트 12색을 6개씩 두 줄로. 카테고리 관리와 같은 팔레트를 쓴다. */
@Composable
private fun PaletteGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        CategoryPalette.chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .then(
                                if (argb == selected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(onClickLabel = "색상 선택") { onSelect(argb) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 삭제 확인.
 *
 * 자산은 카테고리와 달리 비워둘 수 없다. transactions.account_id 가 NOT NULL 이고
 * 외래키가 CASCADE 라 그냥 지우면 그 자산의 거래가 함께 사라진다.
 * 그래서 옮길 곳을 반드시 고르게 하고, 마지막 자산은 지울 수 없다.
 */
@Composable
private fun AccountDeleteDialog(
    target: AccountDeleteTarget,
    onSelectTarget: (Long) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("'${target.account.name}' 삭제") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (target.candidates.isEmpty()) {
                    Text(
                        text = "자산이 하나뿐입니다. 거래를 옮길 곳이 없어 지울 수 없습니다.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (!target.needsReassign) {
                    Text("이 자산을 쓴 내역이 없습니다. 바로 삭제합니다.")
                } else {
                    Text("내역 ${target.usage}건이 이 자산에 달려 있습니다.")
                    Spacer(Modifier.height(12.dp))
                    DialogLabel("어디로 옮길까요?")
                    Column(modifier = Modifier.selectableGroup()) {
                        target.candidates.forEach { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTarget(candidate.id) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = candidate.id == target.selectedTargetId,
                                    onClick = { onSelectTarget(candidate.id) },
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(candidate.colorArgb)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = candidate.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    if (target.selfTransfers > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "이체 ${target.selfTransfers}건은 출금과 입금이 같아져 " +
                                "함께 삭제됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = target.canDelete) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
