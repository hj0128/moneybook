package com.hj0128.moneybook.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatWon

/**
 * 한도 사용 막대. "올해 620,000 / 1,000,000원 · 남은 380,000원".
 *
 * 자산 관리, 결제수단별 목록, 결제수단 상세가 같은 모양을 쓴다. 어디서 보든 같은 숫자가
 * 같은 자리에 있어야 "아까 본 그 막대"로 알아본다.
 *
 * 막대 색은 자산 색이고, 넘기면 지출 색(빨강)으로 바뀐다. 넘긴 걸 색으로 먼저 알아채야 한다.
 */
@Composable
fun LimitGauge(
    account: AccountBalance,
    modifier: Modifier = Modifier,
    showName: Boolean = false,
) {
    val limit = account.limitAmount ?: return
    val over = account.limitRemaining < 0
    val ledgerColors = LocalLedgerColors.current
    val barColor = if (over) ledgerColors.expense else Color(account.colorArgb)
    val remainingText = if (over) {
        "${(-account.limitRemaining).formatWon()} 초과"
    } else {
        "남은 ${account.limitRemaining.formatWon()}"
    }

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${account.name} ${account.limitPeriodLabel} " +
                "${account.limitUsed.formatWon()} 사용, 한도 ${limit.formatWon()}, $remainingText"
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (showName) {
                Text(
                    text = "${account.name} ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = "${account.limitPeriodLabel} ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = account.limitUsed.formatWon(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = " / ${limit.formatWon()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = remainingText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (over) ledgerColors.expense else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val fraction = account.limitRatio.coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor),
                )
            }
        }
    }
}
