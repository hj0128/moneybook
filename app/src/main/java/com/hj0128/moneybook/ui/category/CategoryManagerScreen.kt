package com.hj0128.moneybook.ui.category

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.theme.CategoryPalette
import com.hj0128.moneybook.ui.theme.paletteColorOrDefault
import com.hj0128.moneybook.util.withSubjectJosa

private val manageableTypes = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

/**
 * 카테고리 관리: 대분류 > 소분류 트리를 보고 추가/수정/삭제한다.
 *
 * 순서 변경은 아직 없다. sort_order 는 만들어진 순서를 따른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryManagerViewModel = viewModel(factory = CategoryManagerViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openParent by viewModel.openParent.collectAsStateWithLifecycle()
    val editTarget by viewModel.editTarget.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // 소분류를 보는 중이면 뒤로가기는 화면을 나가는 대신 대분류 목록으로 돌아간다.
    BackHandler(enabled = openParent != null) { viewModel.closeParent() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { if (openParent != null) viewModel.closeParent() else onBack() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = {
                    val parent = openParent
                    if (parent == null) {
                        Text("카테고리 관리")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(parent.category.colorArgb)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(parent.category.name)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val parent = openParent
            FloatingActionButton(
                onClick = {
                    if (parent == null) {
                        viewModel.startAddTopLevel()
                    } else {
                        viewModel.startAddChild(parent.category)
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (parent == null) "대분류 추가" else "소분류 추가",
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            val parent = openParent
            if (parent == null) {
                // 종류는 대분류 목록에서만 고른다. 소분류를 보는 중에는 부모가 이미 정해져 있다.
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    manageableTypes.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = type == state.type,
                            onClick = { viewModel.selectType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index, manageableTypes.size),
                        ) {
                            Text(type.label)
                        }
                    }
                }

                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.nodes, key = { it.category.id }) { node ->
                        ParentRow(
                            node = node,
                            onOpen = { viewModel.openParent(node.category.id) },
                            onEdit = { viewModel.startEdit(node.category) },
                            onDelete = { viewModel.startDelete(node.category) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else {
                if (parent.children.isEmpty()) {
                    EmptyChildren(parentName = parent.category.name)
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(parent.children, key = { it.id }) { child ->
                            ChildRow(
                                category = child,
                                onEdit = { viewModel.startEdit(child) },
                                onDelete = { viewModel.startDelete(child) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        CategoryEditDialog(
            target = target,
            errorMessage = errorMessage,
            onDismiss = viewModel::dismissEdit,
            onSubmit = viewModel::submitEdit,
            onErrorShown = viewModel::clearError,
        )
    }

    deleteTarget?.let { target ->
        CategoryDeleteDialog(
            target = target,
            onDismiss = viewModel::dismissDelete,
            onConfirm = viewModel::confirmDelete,
        )
    }
}

/**
 * 대분류 한 줄.
 *
 * 줄을 누르면 소분류로 들어간다. 수정은 연필로만 한다. 줄 탭이 수정이면
 * 소분류를 보러 들어갈 길이 없어진다.
 */
@Composable
private fun ParentRow(
    node: CategoryNode,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "${node.category.name} 소분류 보기", onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(node.category.colorArgb)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (node.hasChildren) "소분류 ${node.children.size}개" else "소분류 없음",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowActions(name = node.category.name, onEdit = onEdit, onDelete = onDelete)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(8.dp))
    }
}

/** 소분류 한 줄. 더 들어갈 곳이 없으므로 줄 탭이 수정이다. */
@Composable
private fun ChildRow(
    category: CategoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "${category.name} 수정", onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(category.colorArgb)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RowActions(name = category.name, onEdit = onEdit, onDelete = onDelete)
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

@Composable
private fun EmptyChildren(parentName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$parentName 안에 소분류가 없습니다.\n오른쪽 아래 + 버튼으로 추가해 보세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CategoryEditDialog(
    target: CategoryEditTarget,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, colorArgb: Int) -> Unit,
    onErrorShown: () -> Unit,
) {
    val existing = (target as? CategoryEditTarget.Edit)?.category
    var name by remember(target) { mutableStateOf(existing?.name.orEmpty()) }
    var colorArgb by remember(target) {
        mutableIntStateOf(paletteColorOrDefault(existing?.colorArgb))
    }

    val title = when (target) {
        CategoryEditTarget.NewTopLevel -> "대분류 추가"
        is CategoryEditTarget.NewChild -> "${target.parent.name} 안에 소분류 추가"
        is CategoryEditTarget.Edit -> "카테고리 수정"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
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
                Text(
                    text = "색상",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                PaletteGrid(
                    selected = colorArgb,
                    onSelect = { colorArgb = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name, colorArgb) },
                enabled = name.isNotBlank(),
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

/** 팔레트 12색을 6개씩 두 줄로. LazyGrid 를 다이얼로그 안에 넣으면 높이 계산이 꼬인다. */
@Composable
private fun PaletteGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        CategoryPalette.chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { argb ->
                    val isSelected = argb == selected
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .then(
                                if (isSelected) {
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
 * 옮길 것이 없으면 그냥 지울지만 묻고, 있으면 어디로 옮길지 반드시 고르게 한다.
 * 고르지 않고 지우면 과거 내역이 미분류가 되는데 되돌릴 방법이 없다.
 */
@Composable
private fun CategoryDeleteDialog(
    target: DeleteTarget,
    onDismiss: () -> Unit,
    onConfirm: (reassignToId: Long?) -> Unit,
) {
    var selectedId by remember(target) {
        mutableLongStateOf(target.candidates.firstOrNull()?.id ?: 0L)
    }
    // 지금 결정하기 어려우면 미분류로 미뤄둘 수 있다. 카테고리별 탭에 미분류 줄이
    // 남으니 잊히지 않고, 거기서 여러 건을 골라 제자리로 나눠 보낼 수 있다.
    var sendToUncategorized by remember(target) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("'${target.category.name}' 삭제") },
        text = {
            Column {
                if (!target.usage.needsReassign) {
                    Text("이 카테고리를 쓴 내역이 없습니다. 바로 삭제합니다.")
                } else {
                    Text(buildDeleteSummary(target))
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sendToUncategorized = !sendToUncategorized }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = sendToUncategorized,
                            onCheckedChange = { sendToUncategorized = it },
                        )
                        Text(
                            text = "미분류로 보내고 나중에 정리",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (sendToUncategorized) {
                        Text(
                            text = "카테고리별 탭에 '미분류'로 남습니다. 거기서 여러 건을 골라 나눠 보낼 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (target.candidates.isEmpty()) {
                        Text(
                            text = "옮길 카테고리가 없습니다. 미분류로 보내거나, 먼저 다른 카테고리를 만들어 주세요.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            text = "어디로 옮길까요?",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        // 카테고리가 많으면 다이얼로그를 넘쳐 마지막 후보가 잘린다. 목록만 스크롤시킨다.
                        Column(
                            modifier = Modifier
                                .selectableGroup()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            target.candidates.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedId = candidate.id }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = candidate.id == selectedId,
                                        onClick = { selectedId = candidate.id },
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
                    }
                }
            }
        },
        confirmButton = {
            val movesToCategory = target.usage.needsReassign && !sendToUncategorized
            TextButton(
                onClick = { onConfirm(if (movesToCategory) selectedId else null) },
                enabled = !movesToCategory || target.candidates.isNotEmpty(),
            ) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

private fun buildDeleteSummary(target: DeleteTarget): String {
    val parts = buildList {
        if (target.usage.directTransactions > 0) {
            add("내역 ${target.usage.directTransactions}건")
        }
        if (target.usage.children > 0) {
            add("소분류 ${target.usage.children}개")
        }
    }
    val summary = parts.joinToString(", ")
    return "이 카테고리에 ${summary.withSubjectJosa()} 달려 있습니다."
}
