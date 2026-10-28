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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatWon
import com.hj0128.moneybook.data.model.LedgerPeriod
import kotlin.math.roundToInt

/**
 * 카테고리 상세: 카테고리별 탭에서 한 줄을 눌렀을 때 그 카테고리의 그 기간 내역만 보여준다.
 *
 * 기간(한 달 또는 한 해)은 넘겨받은 값으로 고정한다. 여기서 기간을 옮길 수 있게 하면
 * 뒤로 갔을 때 목록의 기간과 어긋나 어디를 보고 있었는지 잃어버린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    source: DetailSource,
    period: LedgerPeriod,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryDetailViewModel = viewModel(
        factory = CategoryDetailViewModel.factory(source, period)
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inSelectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val assignable by viewModel.assignableCategories.collectAsStateWithLifecycle()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    /**
     * 추이를 볼 소분류. null 이면 카테고리 전체.
     * 재분류로 그 소분류 내역이 다 빠져 줄이 사라지면 전체로 돌아간다. 빈 막대만 남으면 고장 난 줄 안다.
     */
    var pickedChildId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedChild = state.childTotals.firstOrNull { it.categoryId == pickedChildId }
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
                                text = "${state.period.shortLabel} · ${state.categoryName}",
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
            if (state.isEmpty) {
                CategoryDetailHeader(state)
                EmptyPlaceholder("${state.period.label}에는\n${state.categoryName} 내역이 없습니다.")
            } else {
                // 요약·소분류·추이도 목록과 함께 스크롤한다. 한 해를 보면 위에 쌓이는 게 많아서
                // 고정해 두면 정작 내역 칸이 몇 줄 안 남는다.
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "summary") {
                        CategoryDetailHeader(state)
                    }
                    if (state.showsTrend) {
                        item(key = "trend") {
                            MonthlyTrend(state, selectedChild)
                        }
                    }
                    if (state.hasBreakdown) {
                        item(key = "breakdown") {
                            ChildBreakdown(
                                state = state,
                                selectedId = selectedChild?.categoryId,
                                // 같은 줄을 다시 누르면 전체로 돌아간다. 따로 "전체" 버튼을 두지 않아도 된다.
                                onSelect = { id -> pickedChildId = id.takeIf { it != pickedChildId } },
                            )
                        }
                    }
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

/** 합계 · 건수 · 평균(한 달이면 하루, 한 해면 월). 일별 탭의 SummaryCard 와 같은 자리에 같은 모양으로 둔다. */
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
                label = state.averageLabel,
                value = state.average.formatWon(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 월별 추이. 한 해를 볼 때 "어느 달에 많이 나갔나"를 보여주는 1~12월 세로 막대.
 *
 * 막대마다 금액을 적으면 12개 숫자가 겹쳐 읽을 수 없다. 대신 누른 달 하나만 위에 크게
 * 적는다. 처음에는 가장 많이 쓴 달을 골라 둔다. 그게 이 그래프에서 제일 먼저 궁금한 답이다.
 */
@Composable
private fun MonthlyTrend(state: CategoryDetailUiState, child: CategoryTotal?) {
    val trend = state.monthlyTrend(child?.categoryId)
    val maxValue = trend.maxOrNull() ?: 0L
    val peakMonth = trend.indexOf(maxValue) + 1
    // 다른 해·카테고리로 바뀌면 고른 달도 그 데이터의 최대 달로 다시 잡는다.
    var selectedMonth by remember(trend) { mutableStateOf(peakMonth) }
    val barColor = (child?.colorArgb ?: state.categoryColor)?.let { Color(it) }
        ?: MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                // 소분류를 골랐으면 제목에 적는다. 막대 색만으로는 무엇의 추이인지 확신하기 어렵다.
                text = child?.let { "월별 추이 · ${it.name}" } ?: "월별 추이",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${selectedMonth}월 ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (state.isFutureMonth(selectedMonth)) "-" else trend[selectedMonth - 1].formatWon(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrendBarMaxHeight),
            verticalAlignment = Alignment.Bottom,
        ) {
            trend.forEachIndexed { index, value ->
                val month = index + 1
                val selected = month == selectedMonth
                val fraction = if (maxValue <= 0L) 0f else (value.toDouble() / maxValue).toFloat()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClickLabel = "${month}월 금액 보기") { selectedMonth = month }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${month}월 ${value.formatWon()}"
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .fillMaxWidth()
                            // 0원인 달도 바닥에 얇게 남긴다. 아예 비면 그 달이 빠진 줄 안다.
                            .height(TrendBarMaxHeight * fraction.coerceIn(0f, 1f) + 2.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                when {
                                    state.isFutureMonth(month) -> MaterialTheme.colorScheme.surfaceVariant
                                    selected -> barColor
                                    // 고르지 않은 달은 옅게. 고른 달이 어느 것인지 색으로 바로 보인다.
                                    else -> barColor.copy(alpha = 0.35f)
                                }
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            (1..12).forEach { month ->
                Text(
                    text = "$month",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (month == selectedMonth) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (month == selectedMonth) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private val TrendBarMaxHeight = 96.dp

/**
 * 소분류별 소계. "식비 75,500원" 다음에 "외식 55,000 / 마트 20,500" 을 보여주는 자리다.
 *
 * 이 화면의 존재 이유라서 목록보다 위에 둔다. 아래 내역은 개별 건을 확인하는 용도고,
 * 어디에 쏠렸는지는 여기서 한눈에 끝나야 한다.
 */
@Composable
private fun ChildBreakdown(
    state: CategoryDetailUiState,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    // 추이가 있을 때만 고를 수 있다. 한 달짜리 화면에서 눌러도 바뀌는 게 없으면 고장처럼 보인다.
    val selectable = state.showsTrend
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (selectable) "소분류 · 눌러서 추이 보기" else "소분류",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(6.dp))
        state.childTotals.forEach { child ->
            val selected = child.categoryId == selectedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        enabled = selectable,
                        onClickLabel = "${child.name} 추이 보기",
                    ) { onSelect(child.categoryId) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
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
 * 트리를 다시 그리는 대신 "식비 > 외식" 처럼 한 줄로 낸다. 다이얼로그에서 계층을
 * 그리면 세로로만 길어지고, 어차피 고를 수 있는 건 리프뿐이라 계층이 필요 없다.
 */
@Composable
private fun AssignCategoryDialog(
    categories: List<AssignableCategory>,
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
                    categories.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item.category.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(item.category.colorArgb)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = item.label,
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
