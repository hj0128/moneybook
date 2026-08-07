package com.hj0128.moneybook.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hj0128.moneybook.data.model.MonthTotal
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatSignedWon
import com.hj0128.moneybook.util.formatWon

/** 월별 탭: 한 해 12개월을 한 화면에서 비교한다. 한 달을 누르면 그 달의 일별 목록으로 간다. */
@Composable
fun MonthlyTab(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.monthlyState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        SummaryCard(summary = state.yearTotal)

        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            items(state.months, key = { it.monthValue }) { month ->
                MonthRow(
                    month = month,
                    maxAmount = state.maxAmount,
                    onClick = { viewModel.openMonth(month.monthValue) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MonthRow(
    month: MonthTotal,
    maxAmount: Long,
    onClick: () -> Unit,
) {
    val ledgerColors = LocalLedgerColors.current
    val hasData = month.income > 0 || month.expense > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${month.monthValue}월",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(44.dp),
                color = if (hasData) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (hasData) month.net.formatSignedWon() else "-",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (hasData) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }

        if (hasData) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "수입 ${month.income.formatWon()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ledgerColors.income,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "지출 ${month.expense.formatWon()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ledgerColors.expense,
                )
            }
            Spacer(Modifier.height(6.dp))
            AmountBar(value = month.income, maxValue = maxAmount, color = ledgerColors.income)
            Spacer(Modifier.height(3.dp))
            AmountBar(value = month.expense, maxValue = maxAmount, color = ledgerColors.expense)
        }
    }
}

/** 가장 큰 달을 100% 로 잡은 가로 막대. 축 눈금 없이 달끼리의 비교만 보여준다. */
@Composable
private fun AmountBar(value: Long, maxValue: Long, color: Color) {
    val fraction = if (maxValue <= 0L) 0f else (value.toDouble() / maxValue).toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}
