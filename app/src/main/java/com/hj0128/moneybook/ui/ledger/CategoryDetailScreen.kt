package com.hj0128.moneybook.ui.ledger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatWon
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * 카테고리 상세: 카테고리별 탭에서 한 줄을 눌렀을 때 그 카테고리의 그 달 내역만 보여준다.
 *
 * 기간은 넘겨받은 달로 고정한다. 여기서 달을 옮길 수 있게 하면 뒤로 갔을 때
 * 목록의 달과 어긋나 어디를 보고 있었는지 잃어버린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    source: DetailSource,
    yearMonth: YearMonth,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryDetailViewModel = viewModel(
        factory = CategoryDetailViewModel.factory(source, yearMonth)
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inSelectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val assignable by viewModel.assignableCategories.collectAsStateWithLifecycle()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showAssignSheet by remember { mutableStateOf(false) }

    // 선택 모드에서 뒤로가기는 화면을 나가는 대신 선택을 푼다.
    BackHandler(enabled = inSelectionMode) { viewModel.exitSelection() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { if (inSelectionMode) viewModel.exitSelection() else onBack() },
                    ) {
                        if (inSelectionMode) {
                            Icon(Icons.Default.Close, contentDescription = "선택 해제")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                },
                title = {
                    if (inSelectionMode) {
                        Text("${selectedIds.size}건 선택")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            state.categoryColor?.let { argb ->
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb)),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = "${state.yearMonth.monthValue}월 · ${state.categoryName}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (inSelectionMode) {
                        TextButton(onClick = viewModel::selectAll) { Text("전체") }
                        TextButton(
                            onClick = { showAssignSheet = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) { Text("옮기기") }
                    } else if (!state.isEmpty) {
                        IconButton(onClick = viewModel::startSelection) {
                            Icon(Icons.Default.Checklist, contentDescription = "선택해서 옮기기")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            CategoryDetailHeader(state)

            if (state.hasBreakdown) {
                ChildBreakdown(state)
            }

            if (state.isEmpty) {
                EmptyPlaceholder("${state.yearMonth.year}년 ${state.yearMonth.monthValue}월에는\n${state.categoryName} 내역이 없습니다.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    state.groups.forEach { group ->
                        item(key = "header-${group.date}") {
                            DayHeader(group)
                        }
                        items(group.items, key = { it.id }) { item ->
                            TransactionRow(
                                item = item,
                                selectionMode = inSelectionMode,
                                selected = item.id in selectedIds,
                                onClick = { viewModel.toggleSelection(item.id) },
                                // 선택 모드에서는 길게 눌러도 삭제되지 않는다. 실수로 지우기 쉽다.
                                onLongClick = {
                                    if (inSelectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        pendingDeleteId = item.id
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAssignSheet) {
        AssignCategoryDialog(
            categories = assignable,
            count = selectedIds.size,
            onDismiss = { showAssignSheet = false },
            onSelect = { categoryId ->
                viewModel.assignSelected(categoryId)
                showAssignSheet = false
            },
        )
    }

    pendingDeleteId?.let { id ->
        DeleteTransactionDialog(
            onConfirm = {
                viewModel.delete(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

/** 합계 · 건수 · 하루 평균. 일별 탭의 SummaryCard 와 같은 자리에 같은 모양으로 둔다. */
@Composable
private fun CategoryDetailHeader(state: CategoryDetailUiState) {
    val ledgerColors = LocalLedgerColors.current
    val totalColor = when (state.type) {
        TransactionType.INCOME -> ledgerColors.income
        else -> ledgerColors.expense
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HeaderItem("합계", state.total.formatWon(), totalColor)
            HeaderItem("건수", "${state.entryCount}건", MaterialTheme.colorScheme.onSurface)
            HeaderItem(
                label = "하루 평균",
                value = state.dailyAverage.formatWon(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 소분류별 소계. "식비 75,500원" 다음에 "외식 55,000 / 마트 20,500" 을 보여주는 자리다.
 *
 * 이 화면의 존재 이유라서 목록보다 위에 둔다. 아래 내역은 개별 건을 확인하는 용도고,
 * 어디에 쏠렸는지는 여기서 한눈에 끝나야 한다.
 */
@Composable
private fun ChildBreakdown(state: CategoryDetailUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = "소분류",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        state.childTotals.forEach { child ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(child.colorArgb)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = child.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${child.entryCount}건",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(state.childRatioOf(child) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = child.total.formatWon(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 고른 내역을 어느 카테고리로 옮길지 고른다.
 *
 * 소분류까지 전부 평평하게 낸다. 여기서 트리를 다시 그리면 다이얼로그가 길어지고,
 * 어차피 옮길 곳은 대개 소분류다.
 */
@Composable
private fun AssignCategoryDialog(
    categories: List<CategoryEntity>,
    count: Int,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${count}건을 옮길 카테고리") },
        text = {
            if (categories.isEmpty()) {
                Text("옮길 카테고리가 없습니다.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(category.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    // 소분류는 들여써서 어디 밑인지 보이게 한다.
                                    .padding(start = if (category.isTopLevel) 0.dp else 20.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(category.colorArgb)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun HeaderItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
