package com.hj0128.moneybook.ui.ledger

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
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

        if (state.totals.isEmpty()) {
            EmptyPlaceholder("이 달에는 ${state.type.label} 내역이 없습니다.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.totals, key = { it.categoryId }) { item ->
                    CategoryRow(item = item, ratio = state.ratioOf(item))
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(item: CategoryTotal, ratio: Float) {
    val color = Color(item.colorArgb)
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
