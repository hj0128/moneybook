package com.hj0128.moneybook.ui.ledger

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.CategorySortOrder
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.PeriodUnit
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.UncategorizedTotal
import com.hj0128.moneybook.ui.common.UsageGauge
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatWon
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

private val breakdownTypes = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

private val breakdownBases = BreakdownBasis.entries

/**
 * 카테고리별 탭: 선택한 기간의 지출(또는 수입)이 어디로 갔는지 비중 순으로 보여준다.
 * 기준을 결제수단으로 바꾸면 같은 화면에서 어떤 수단으로 얼마 썼는지를 본다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTab(
    viewModel: LedgerViewModel,
    onOpenCategory: (categoryId: Long) -> Unit,
    onOpenUncategorized: () -> Unit,
    onOpenAccount: (accountId: Long) -> Unit,
    onOpenBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.categoryState.collectAsStateWithLifecycle()
    val ledgerColors = LocalLedgerColors.current
    val listState = rememberLazyListState()

    // 줄 순서가 통째로 바뀌면 보고 있던 자리는 의미가 없어진다. 그대로 두면 정렬을 바꾼 직후
    // 목록 한가운데를 보게 되고, 1위가 화면 위로 밀려 올라가 안 보인다.
    LaunchedEffect(state.sortOrder, state.type, state.period, state.basis) {
        listState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 무엇으로 묶을지가 가장 큰 갈래라 맨 위에 둔다. 그 아래가 수입/지출, 그 아래가 기간이다.
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        ) {
            breakdownBases.forEachIndexed { index, basis ->
                SegmentedButton(
                    selected = basis == state.basis,
                    onClick = { viewModel.selectCategoryBasis(basis) },
                    shape = SegmentedButtonDefaults.itemShape(index, breakdownBases.size),
                ) {
                    Text(basis.label)
                }
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        ) {
            breakdownTypes.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == state.type,
                    onClick = { viewModel.selectCategoryType(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, breakdownTypes.size),
                ) {
                    Text(type.label)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.type.label} 합계",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.sum.formatWon(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (state.type) {
                    TransactionType.INCOME -> ledgerColors.income
                    else -> ledgerColors.expense
                },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnitSelector(
                selected = state.period.unit,
                onSelect = viewModel::selectCategoryUnit,
            )
            Spacer(Modifier.weight(1f))
            SortSelector(
                selected = state.sortOrder,
                onSelect = viewModel::selectCategorySortOrder,
            )
        }

        if (state.isEmpty) {
            EmptyPlaceholder("${state.period.thisLabel}에는 ${state.type.label} 내역이 없습니다.")
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                if (state.showsBudget) {
                    item(key = "budget") {
                        BudgetCard(state = state, onClick = onOpenBudget)
                    }
                }
                items(state.totals, key = { "${state.basis}-${it.categoryId}" }) { item ->
                    CategoryRow(
                        item = item,
                        ratio = state.ratioOf(item),
                        limit = state.limitOf(item),
                        budget = state.budgetOf(item),
                        onClick = {
                            when (state.basis) {
                                BreakdownBasis.CATEGORY -> onOpenCategory(item.categoryId)
                                BreakdownBasis.ACCOUNT -> onOpenAccount(item.categoryId)
                            }
                        },
                    )
                }
                // 미분류는 항상 맨 아래. 정리해야 할 것이지 카테고리가 아니다.
                if (!state.uncategorized.isEmpty) {
                    item(key = "uncategorized") {
                        UncategorizedRow(
                            total = state.uncategorized,
                            ratio = state.ratioOf(state.uncategorized.total),
                            onClick = onOpenUncategorized,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 한 달씩 볼지 한 해를 통째로 볼지. 일별 탭의 전체/수입/지출 칩과 같은 자리, 같은 모양이다.
 *
 * 년을 고르면 상단 제목과 화살표도 한 해 단위로 바뀐다.
 */
@Composable
private fun UnitSelector(
    selected: PeriodUnit,
    onSelect: (PeriodUnit) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PeriodUnit.entries.forEach { unit ->
            FilterChip(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                label = { Text(unit.label) },
            )
        }
    }
}

/**
 * 정렬 기준 선택.
 *
 * 일별 탭은 방향이 둘뿐이라 눌러 뒤집는 버튼이지만, 여기는 셋이라 눌러 돌리면
 * 원하는 것을 지나칠 수 있다. 무엇을 고를 수 있는지 펼쳐 보여준다.
 */
@Composable
private fun SortSelector(
    selected: CategorySortOrder,
    onSelect: (CategorySortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // 메뉴는 누른 버튼 자리에서 펼쳐져야 한다. Box 로 묶어야 그 위치를 기준으로 잡는다.
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics {
                contentDescription = "정렬 ${selected.label}. 눌러서 변경"
            },
        ) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text = selected.label, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CategorySortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = order.label,
                            fontWeight = if (order == selected) FontWeight.SemiBold
                            else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(order)
                    },
                )
            }
        }
    }
}

/**
 * 미분류 줄.
 *
 * 카테고리 색 대신 테두리만 있는 회색 점을 쓴다. 색을 주면 정상 카테고리처럼 보이는데,
 * 이건 그냥 두면 안 되는 줄이라 다르게 보여야 한다.
 */
@Composable
private fun UncategorizedRow(
    total: UncategorizedTotal,
    ratio: Float,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "미분류 내역 정리", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "미분류",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${total.entryCount}건",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(ratio * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = total.total.formatWon(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (ratio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

/**
 * 한도 자산 줄 아래의 한 줄. "올해 남은 380,000원 / 1,000,000원".
 *
 * 오른쪽 금액은 다른 줄처럼 고른 기간에 쓴 돈이고, 이 줄은 기간과 상관없이 늘 지금
 * 주기(올해/이번 달) 기준이다. 지난 달을 보고 있어도 "지금 얼마 남았나"가 궁금하기 때문이다.
 * 그래서 "올해"를 앞에 붙여 둔다.
 */
@Composable
private fun LimitNote(account: AccountBalance) {
    val limit = account.limitAmount ?: return
    val over = account.limitRemaining < 0
    val text = if (over) {
        "${account.limitPeriodLabel} 초과 ${(-account.limitRemaining).formatWon()} / ${limit.formatWon()}"
    } else {
        "${account.limitPeriodLabel} 남은 ${account.limitRemaining.formatWon()} / ${limit.formatWon()}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        // 넘겼을 때만 지출 색. 평소에는 보조 글씨라 눈에 띄지 않아야 한다.
        color = if (over) LocalLedgerColors.current.expense else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 2.dp),
    )
}

/**
 * 이 달 예산 전체. 누르면 예산 화면으로 간다(예산을 고치는 곳).
 *
 * 예산을 정한 카테고리만 더한다. 줄마다 적힌 남은 금액을 다 더해 보지 않아도 되게.
 */
@Composable
private fun BudgetCard(state: CategoryUiState, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(CardDefaults.shape)
            .clickable(onClickLabel = "예산 보기", onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        UsageGauge(
            periodLabel = "${state.period.shortLabel} 예산",
            used = state.budgetSpent,
            limit = state.budgetTotal,
            color = MaterialTheme.colorScheme.primary,
            accessibilityName = "${state.period.shortLabel} 예산",
            // 카드 바탕과 막대 바탕이 같은 색이면 막대 길이를 가늠할 수 없다.
            trackColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * 예산 있는 카테고리 줄 아래의 한 줄. "예산 남은 80,000원 / 500,000원".
 * 결제수단 한도 줄과 같은 모양이다. 넘겼을 때만 지출 색으로 눈에 띄게 한다.
 */
@Composable
private fun BudgetNote(spent: Long, budget: Long) {
    val remaining = budget - spent
    val over = remaining < 0
    val text = if (over) {
        "예산 초과 ${(-remaining).formatWon()} / ${budget.formatWon()}"
    } else {
        "예산 남은 ${remaining.formatWon()} / ${budget.formatWon()}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (over) LocalLedgerColors.current.expense else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 2.dp),
    )
}

@Composable
private fun CategoryRow(
    item: CategoryTotal,
    ratio: Float,
    onClick: () -> Unit,
    limit: AccountBalance? = null,
    budget: Long? = null,
) {
    val color = Color(item.colorArgb)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "${item.name} 내역 보기", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${item.entryCount}건",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(ratio * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = item.total.formatWon(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            // 눌러서 들어갈 수 있다는 표시. 없으면 합계만 보여주는 목록으로 읽힌다.
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        limit?.let { LimitNote(it) }
        budget?.let { BudgetNote(spent = item.total, budget = it) }
        Spacer(Modifier.height(6.dp))
        // 비중 막대는 카테고리 색을 그대로 쓴다. 목록의 색 점과 같은 색이라 눈으로 잇기 쉽다.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (ratio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
        }
    }
}
