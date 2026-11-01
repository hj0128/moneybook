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
    UsageGauge(
        periodLabel = account.limitPeriodLabel,
        used = account.limitUsed,
        limit = limit,
        color = Color(account.colorArgb),
        name = account.name.takeIf { showName },
        accessibilityName = account.name,
        limitWord = "한도",
        modifier = modifier,
    )
}

/**
 * 정해 둔 금액 대비 사용 막대. 결제수단 한도와 카테고리 예산이 같은 모양을 쓴다.
 * 둘 다 "얼마 중 얼마 썼고 얼마 남았나"라서 다르게 생기면 같은 것을 두 번 읽는 법을 익혀야 한다.
 */
@Composable
fun UsageGauge(
    periodLabel: String,
    used: Long,
    limit: Long,
    color: Color,
    modifier: Modifier = Modifier,
    name: String? = null,
    accessibilityName: String = name.orEmpty(),
    limitWord: String = "예산",
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val remaining = limit - used
    val over = remaining < 0
    val ledgerColors = LocalLedgerColors.current
    val barColor = if (over) ledgerColors.expense else color
    // "남은 X원"과 같은 자리, 같은 순서로 읽히게 "초과 X원"으로 쓴다.
    val remainingText = if (over) "초과 ${(-remaining).formatWon()}" else "남은 ${remaining.formatWon()}"

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$accessibilityName $periodLabel " +
                "${used.formatWon()} 사용, $limitWord ${limit.formatWon()}, $remainingText"
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (name != null) {
                Text(
                    text = "$name ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            // 기간이 화면 위에 이미 적혀 있는 곳은 빈 값을 넘긴다.
            if (periodLabel.isNotEmpty()) {
                Text(
                    text = "$periodLabel ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = used.formatWon(),
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
                .background(trackColor),
        ) {
            // 예산 0원에 쓴 돈이 있으면 다 넘긴 것이다. 나눌 수 없다고 빈 막대로 두면 안 넘긴 것처럼 보인다.
            val fraction = when {
                limit > 0L -> (used.toDouble() / limit).toFloat().coerceIn(0f, 1f)
                used > 0L -> 1f
                else -> 0f
            }
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
