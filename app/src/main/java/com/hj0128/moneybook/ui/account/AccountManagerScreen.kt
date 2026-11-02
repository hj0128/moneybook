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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountNode
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.model.PeriodUnit
import com.hj0128.moneybook.data.repository.AccountLimit
import com.hj0128.moneybook.ui.common.LimitGauge
import com.hj0128.moneybook.ui.common.ReorderableList
import com.hj0128.moneybook.ui.theme.CategoryPalette
import com.hj0128.moneybook.ui.theme.paletteColorOrDefault
import com.hj0128.moneybook.util.formatWon
import com.hj0128.moneybook.util.withSubjectJosa

private val accountTypes = listOf(AccountType.CASH, AccountType.BANK, AccountType.CARD)

/**
 * 자산 관리: 상위 > 하위 자산을 보고 추가/수정/삭제하고, 순서를 바꾼다.
 *
 * 카테고리 관리와 같은 모양이다. 상위 줄을 누르면 하위로 들어가고, 수정은 연필로 한다.
 * 자산도 카테고리처럼 두 단계라서, 같은 구조를 다른 손놀림으로 다루게 하지 않는다.
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
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val openParent by viewModel.openParent.collectAsStateWithLifecycle()
    val editTarget by viewModel.editTarget.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val reorderDraft by viewModel.reorderDraft.collectAsStateWithLifecycle()

    // 뒤로가기는 한 겹씩만 벗긴다. 순서 변경 중이면 모드만 끝내고, 하위를 보는 중이면 상위 목록으로.
    BackHandler(enabled = reorderDraft != null) { viewModel.stopReorder() }
    BackHandler(enabled = reorderDraft == null && openParent != null) { viewModel.closeParent() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                reorderDraft != null -> viewModel.stopReorder()
                                openParent != null -> viewModel.closeParent()
                                else -> onBack()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = {
                    val parent = openParent
                    when {
                        reorderDraft != null -> Text("순서 변경")
                        parent == null -> Text("자산 관리")
                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(parent.account.colorArgb)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(parent.account.name)
                        }
                    }
                },
                actions = {
                    if (reorderDraft != null) {
                        TextButton(onClick = viewModel::stopReorder) { Text("완료") }
                    } else if (canReorder(openParent, nodes)) {
                        IconButton(onClick = viewModel::startReorder) {
                            Icon(Icons.Default.SwapVert, contentDescription = "순서 변경")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val parent = openParent
            // 순서를 바꾸는 중에는 추가 버튼을 감춘다. 목록이 바뀌면 끌던 자리가 어긋난다.
            if (reorderDraft == null) {
                FloatingActionButton(
                    onClick = {
                        if (parent == null) {
                            viewModel.startAddTopLevel()
                        } else {
                            viewModel.startAddChild(parent.account)
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (parent == null) "자산 추가" else "하위 자산 추가",
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            val parent = openParent
            val draft = reorderDraft
            if (draft != null) {
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
            } else if (parent == null) {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(nodes, key = { it.account.id }) { node ->
                        ParentRow(
                            node = node,
                            onOpen = { viewModel.openParent(node.account.id) },
                            onEdit = { viewModel.startEdit(node.account) },
                            onDelete = { viewModel.startDelete(node.account) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else if (parent.children.isEmpty()) {
                EmptyChildren(parentName = parent.account.name)
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(parent.children, key = { it.id }) { child ->
                        ChildRow(
                            account = child,
                            onEdit = { viewModel.startEdit(child) },
                            onDelete = { viewModel.startDelete(child) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
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

/** 옮길 것이 하나뿐이면 순서라는 게 없다. 눌러도 아무 일 없는 버튼은 두지 않는다. */
private fun canReorder(openParent: AccountNode?, nodes: List<AccountNode>): Boolean =
    if (openParent == null) nodes.size >= 2 else openParent.children.size >= 2

/**
 * 상위 자산 한 줄. 카테고리 관리의 대분류 줄과 같은 모양이다.
 *
 * 줄을 누르면 하위로 들어간다. 수정은 연필로만 한다. 줄 탭이 수정이면 하위를 보러 들어갈 길이 없어진다.
 * 하위가 있으면 잔액은 하위까지 합친 값이다. 이걸 보려고 묶는 것이다.
 */
@Composable
private fun ParentRow(
    node: AccountNode,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val account = node.account
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "${account.name} 하위 자산 보기", onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    text = if (node.hasChildren) {
                        "하위 ${node.children.size}개 · ${node.totalBalance.formatWon()}"
                    } else {
                        "${account.type.label} · ${account.balance.formatWon()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowActions(name = account.name, onEdit = onEdit, onDelete = onDelete)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(8.dp))
        }
        LimitBar(account)
    }
}

/** 하위 자산 한 줄. 더 들어갈 곳이 없으므로 줄 탭이 수정이다(카테고리 소분류 줄과 같다). */
@Composable
private fun ChildRow(
    account: AccountBalance,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "${account.name} 수정", onClick = onEdit),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${account.type.label} · ${account.balance.formatWon()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowActions(name = account.name, onEdit = onEdit, onDelete = onDelete)
        }
        LimitBar(account)
    }
}

/**
 * 한도 막대. 버튼 아래까지 넓게 쓴다. 버튼 옆 좁은 칸에 넣으면 "남은 X원"이 잘린다.
 * 이름 글자가 시작하는 자리(여백 16 + 점 12 + 간격 12)에 맞춘다.
 */
@Composable
private fun LimitBar(account: AccountBalance) {
    if (account.hasLimit) {
        LimitGauge(
            account = account,
            modifier = Modifier.padding(start = 40.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun RowActions(name: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    IconButton(onClick = onEdit) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "$name 수정",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onClick = onDelete) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "$name 삭제",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun EmptyChildren(parentName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$parentName 안에 하위 자산이 없습니다.\n오른쪽 아래 + 버튼으로 추가해 보세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AccountEditDialog(
    target: AccountEditTarget,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        type: AccountType,
        colorArgb: Int,
        initialBalance: Long,
        limit: AccountLimit?,
        parentId: Long?,
    ) -> Unit,
    onErrorShown: () -> Unit,
) {
    val existing = (target as? AccountEditTarget.Edit)?.account
    var name by remember(target) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(target) { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var colorArgb by remember(target) {
        mutableIntStateOf(paletteColorOrDefault(existing?.colorArgb))
    }
    // 초기 잔액은 현재 잔액이 아니다. 현재 잔액은 거래를 누적해 나오는 값이라 여기서 못 고친다.
    // 수정할 때는 저장된 값을 채워 둔다. 비워 두면 색만 바꿔도 시작 잔액이 0으로 덮인다.
    var initialBalanceText by remember(target) {
        mutableStateOf(existing?.initialBalance?.takeIf { it != 0L }?.toString().orEmpty())
    }
    // null 이면 한도 없음. 대부분의 자산은 한도가 없어서 기본값도 없음이다.
    var limitPeriod by remember(target) { mutableStateOf(existing?.limitPeriod) }
    var limitText by remember(target) { mutableStateOf(existing?.limitAmount?.toString().orEmpty()) }
    // 상위는 수정 창에서 바꾸지 않는다. 카테고리 소분류처럼 들어온 자리가 곧 자리다.
    val parentId = existing?.parentId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (target) {
                    AccountEditTarget.NewTopLevel -> "자산 추가"
                    is AccountEditTarget.NewChild -> "${target.parent.name} 안에 하위 자산 추가"
                    is AccountEditTarget.Edit -> "자산 수정"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    // 한도 칸까지 처음부터 보이게 넉넉히 둔다. 잘려서 스크롤해야 보이면 있는 줄 모른다.
                    .heightIn(max = 600.dp)
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

                // 하위 자산은 상위의 종류를 따른다. 체크카드 아래에서 현금/은행을 고르라고 하면 이상하다.
                val underParent = target is AccountEditTarget.NewChild || parentId != null
                if (!underParent) {
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

                Spacer(Modifier.height(16.dp))
                DialogLabel("사용 한도")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    limitOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == limitPeriod,
                            onClick = { limitPeriod = option },
                            shape = SegmentedButtonDefaults.itemShape(index, limitOptions.size),
                        ) {
                            Text(option.limitLabel())
                        }
                    }
                }
                if (limitPeriod != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { input -> limitText = input.filter { it.isDigit() } },
                        label = { Text("한도 금액") },
                        supportingText = {
                            Text(
                                when (limitPeriod) {
                                    PeriodUnit.YEAR -> "매년 1월 1일에 사용액이 0부터 다시 셉니다."
                                    else -> "매월 1일에 사용액이 0부터 다시 셉니다."
                                }
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSubmit(
                        name,
                        type,
                        colorArgb,
                        initialBalanceText.toLongOrNull() ?: 0L,
                        limitPeriod?.let { AccountLimit(limitText.toLongOrNull() ?: 0L, it) },
                        parentId,
                    )
                },
                // 한도를 고르고 금액을 비워 두면 무엇을 셀지 정할 수 없다. 저장 전에 막는다.
                enabled = name.isNotBlank() && (limitPeriod == null || (limitText.toLongOrNull() ?: 0L) > 0L),
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

/** 한도 선택지. null 은 "없음"이다. */
private val limitOptions: List<PeriodUnit?> = listOf(null, PeriodUnit.MONTH, PeriodUnit.YEAR)

private fun PeriodUnit?.limitLabel(): String = when (this) {
    null -> "없음"
    PeriodUnit.MONTH -> "매월"
    PeriodUnit.YEAR -> "매년"
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
                if (target.isLast) {
                    Text(
                        text = "자산이 하나뿐입니다. 거래를 옮길 곳이 없어 지울 수 없습니다.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (!target.needsReassign) {
                    Text("이 자산을 쓴 내역이 없습니다. 바로 삭제합니다.")
                } else if (target.candidates.isEmpty()) {
                    Text(buildDeleteSummary(target))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "옮길 상위 자산이 없습니다. 먼저 다른 자산을 만들어 주세요.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(buildDeleteSummary(target))
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

/** 카테고리 삭제 문구와 같은 모양. 하위는 내역째로 옮겨진다는 걸 함께 적는다. */
private fun buildDeleteSummary(target: AccountDeleteTarget): String {
    val parts = buildList {
        if (target.usage > 0) add("내역 ${target.usage}건")
        if (target.children > 0) add("하위 자산 ${target.children}개")
    }
    val summary = parts.joinToString(", ")
    return "이 자산에 ${summary.withSubjectJosa()} 달려 있습니다."
}
