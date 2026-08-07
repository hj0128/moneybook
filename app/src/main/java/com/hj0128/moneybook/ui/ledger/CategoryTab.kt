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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.UncategorizedTotal
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatWon
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

private val breakdownTypes = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

/** 카테고리별 탭: 선택한 달의 지출(또는 수입)이 어디로 갔는지 비중 순으로 보여준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTab(
    viewModel: LedgerViewModel,
    onOpenCategory: (categoryId: Long) -> Unit,
    onOpenUncategorized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.categoryState.collectAsStateWithLifecycle()
    val ledgerColors = LocalLedgerColors.current

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
        Spacer(Modifier.height(8.dp))

        if (state.isEmpty) {
            EmptyPlaceholder("이 달에는 ${state.type.label} 내역이 없습니다.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.totals, key = { it.categoryId }) { item ->
                    CategoryRow(
                        item = item,
                        ratio = state.ratioOf(item),
                        onClick = { onOpenCategory(item.categoryId) },
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

@Composable
private fun CategoryRow(item: CategoryTotal, ratio: Float, onClick: () -> Unit) {
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
