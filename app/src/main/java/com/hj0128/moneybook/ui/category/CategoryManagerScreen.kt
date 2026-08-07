package com.hj0128.moneybook.ui.category

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.local.entity.CategoryEntity
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
    val editTarget by viewModel.editTarget.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = { Text("카테고리 관리") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startAddTopLevel) {
                Icon(Icons.Default.Add, contentDescription = "대분류 추가")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
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
                state.nodes.forEach { node ->
                    item(key = "parent-${node.category.id}") {
                        CategoryRow(
                            category = node.category,
                            isChild = false,
                            onEdit = { viewModel.startEdit(node.category) },
                            onDelete = { viewModel.startDelete(node.category) },
                            onAddChild = { viewModel.startAddChild(node.category) },
                        )
                    }
                    node.children.forEach { child ->
                        item(key = "child-${child.id}") {
                            CategoryRow(
                                category = child,
                                isChild = true,
                                onEdit = { viewModel.startEdit(child) },
                                onDelete = { viewModel.startDelete(child) },
                                onAddChild = null,
                            )
                        }
                    }
                    item(key = "divider-${node.category.id}") { HorizontalDivider() }
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

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    isChild: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddChild: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "${category.name} 수정", onClick = onEdit)
            // 소분류는 들여써서 어디에 속하는지 눈으로 바로 잡히게 한다.
            .padding(start = if (isChild) 40.dp else 16.dp, end = 4.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (isChild) 8.dp else 12.dp)
                .clip(CircleShape)
                .background(Color(category.colorArgb)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            style = if (isChild) MaterialTheme.typography.bodyMedium
            else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isChild) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onAddChild != null) {
            IconButton(onClick = onAddChild) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "${category.name}에 소분류 추가",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "${category.name} 수정",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "${category.name} 삭제",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                    if (target.candidates.isEmpty()) {
                        Text(
                            text = "옮길 카테고리가 없습니다. 먼저 다른 카테고리를 만들어 주세요.",
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
            TextButton(
                onClick = {
                    onConfirm(if (target.usage.needsReassign) selectedId else null)
                },
                enabled = !target.usage.needsReassign || target.candidates.isNotEmpty(),
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
