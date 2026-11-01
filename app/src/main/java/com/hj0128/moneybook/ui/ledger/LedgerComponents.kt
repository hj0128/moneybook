package com.hj0128.moneybook.ui.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hj0128.moneybook.data.model.MonthlySummary
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatListHeader
import com.hj0128.moneybook.util.formatSignedWon
import com.hj0128.moneybook.util.formatWon

/** 일별/월별 탭 위에 공통으로 올라가는 수입·지출·합계 카드. */
@Composable
fun SummaryCard(
    summary: MonthlySummary,
    modifier: Modifier = Modifier,
) {
    val ledgerColors = LocalLedgerColors.current
    Card(
        modifier = modifier
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
            SummaryItem("수입", summary.income.formatWon(), ledgerColors.income)
            SummaryItem("지출", summary.expense.formatWon(), ledgerColors.expense)
            SummaryItem(
                label = "합계",
                value = summary.net.formatSignedWon(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
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

/** 하루치 묶음의 머리글. 일별 탭과 카테고리 상세가 같은 모양을 쓴다. */
@Composable
fun DayHeader(group: DayGroup) {
    val ledgerColors = LocalLedgerColors.current
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.date.formatListHeader(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (group.dayTotal.income > 0) {
                Text(
                    text = group.dayTotal.income.formatWon(),
                    style = MaterialTheme.typography.labelMedium,
                    color = ledgerColors.income,
                )
                Spacer(Modifier.width(8.dp))
            }
            if (group.dayTotal.expense > 0) {
                Text(
                    text = group.dayTotal.expense.formatWon(),
                    style = MaterialTheme.typography.labelMedium,
                    color = ledgerColors.expense,
                )
            }
        }
    }
}

/**
 * 내역 한 줄. 길게 누르면 삭제를 요청한다.
 *
 * [selectionMode] 가 켜지면 색 점 자리에 체크박스가 들어서고 탭이 선택 토글이 된다.
 * 기본값이 꺼짐이라 일별 탭은 예전과 똑같이 동작한다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    item: TransactionDetail,
    onLongClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    parts: List<TransactionDetail> = emptyList(),
) {
    if (parts.size > 1) {
        SplitTransactionRow(item, parts, onLongClick, onClick)
        return
    }
    val ledgerColors = LocalLedgerColors.current
    val amountColor = when (item.type) {
        TransactionType.INCOME -> ledgerColors.income
        TransactionType.EXPENSE -> ledgerColors.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val amountText = when (item.type) {
        TransactionType.INCOME -> "+${item.amount.formatWon()}"
        TransactionType.EXPENSE -> "-${item.amount.formatWon()}"
        TransactionType.TRANSFER -> item.amount.formatWon()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    Modifier
                }
            )
            // 평소에는 탭이 '열어서 고치기', 선택 모드에서는 '고르기'다. 어느 쪽인지는
            // 부르는 쪽이 onClick 에 담아 넘긴다.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(20.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        item.categoryColor?.let { Color(it) }
                            ?: MaterialTheme.colorScheme.outline
                    ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 메모(무엇을 샀는지)를 카테고리·결제수단보다 위에 둔다. 나눈 결제의 줄과 같은 순서라
            // 일별 탭에서 두 모양이 섞여도 같은 자리에 같은 값이 온다.
            // 카테고리·결제수단과 한 줄에 잇지 않는 건 메모 길이를 가늠할 수 없어서다.
            if (item.memo.isNotBlank()) {
                Text(
                    text = item.memo,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = amountText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
        )
    }
}

/**
 * 나눈 결제 한 줄. 위에는 결제 전체(거래처 · 총액), 아래에는 나눈 줄을 하나씩 펼친다.
 *
 * 줄마다 무엇을 샀는지(메모), 어디로 분류했는지, 얼마인지를 따로 보여준다. 메모를 쉼표로 이어 붙이면
 * 어느 메모가 어느 카테고리 것인지 알 수 없다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SplitTransactionRow(
    item: TransactionDetail,
    parts: List<TransactionDetail>,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val ledgerColors = LocalLedgerColors.current
    val amountColor = if (item.type == TransactionType.INCOME) ledgerColors.income else ledgerColors.expense
    val sign = if (item.type == TransactionType.INCOME) "+" else "-"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 나눈 결제는 카테고리가 여럿이라 색 점 하나로 대표할 수 없다. 자리만 비워 제목 줄을 맞춘다.
            Spacer(Modifier.width(22.dp))
            Text(
                text = item.merchant.ifBlank { "나눈 결제" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$sign${item.amount.formatWon()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = amountColor,
            )
        }
        parts.forEach { part ->
            Spacer(Modifier.height(4.dp))
            SplitPartLine(part)
        }
    }
}

/** 나눈 줄 하나: ● 라면  편의점 | 체크/신용카드 ······ 6,000원 */
@Composable
private fun SplitPartLine(part: TransactionDetail) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(part.categoryColor?.let { Color(it) } ?: MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 메모가 있으면 그게 이 줄의 이름이다("라면"). 없으면 카테고리로 대신한다.
            Text(
                text = part.memo.ifBlank { part.categoryName ?: "미분류" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = part.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = part.amount.formatWon(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DeleteTransactionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 내역을 삭제할까요?") },
        text = { Text("삭제하면 되돌릴 수 없습니다.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
