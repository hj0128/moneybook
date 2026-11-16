package com.hj0128.moneybook.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatWon

/**
 * 한도 사용 막대. "올해 한도 620,000 / 1,000,000원 · 380,000원 남음".
 *
 * 한도·실적·예산은 모두 "[기간] [무엇] 쓴 돈 / 기준 · 남음|초과|달성" 한 가지 틀로 적는다.
 * 셋이 화면마다 "남은 X", "X 더", "X 모자람"처럼 다르게 쓰이면 같은 뜻인지 매번 다시 읽어야 한다.
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
        periodLabel = "${account.limitPeriodLabel} 한도",
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
    val remainingText = remainingWord(remaining)

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$accessibilityName $periodLabel " +
                "${used.formatWon()} 사용, $limitWord ${limit.formatWon()}, $remainingText"
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            // 폭이 모자라면 왼쪽이 줄어든다. 오른쪽 "X원 남음"이 두 줄로 깨지면 숫자가 반으로 갈려 읽을 수 없다.
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                if (name != null) {
                    Text(
                        text = "$name ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                // 기간이 화면 위에 이미 적혀 있는 곳은 빈 값을 넘긴다.
                if (periodLabel.isNotEmpty()) {
                    Text(
                        text = "$periodLabel ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = used.formatWon(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = " / ${limit.formatWon()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = remainingText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (over) ledgerColors.expense else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
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

/**
 * 실적 막대. "이번 달 실적 180,000 / 300,000원 · 120,000원 남음", 그 아래 "지난달 실적 320,000원 · 달성".
 *
 * 한도 막대와 같은 자리에 같은 순서로 숫자를 놓는다. 다만 한도는 남은 금액이 줄어드는 게
 * 좋은 소식이 아니고 실적은 막대가 차는 게 좋은 소식이라, 넘겨도 빨갛게 바꾸지 않고 "달성"으로 적는다.
 *
 * 지난 실적을 함께 적는 이유: 혜택은 보통 지난달 실적으로 정해진다. 이번 달 막대만 보면
 * "지금 혜택을 받고 있나"를 알 수 없다.
 */
@Composable
fun PerformanceGauge(
    account: AccountBalance,
    modifier: Modifier = Modifier,
) {
    if (!account.hasPerformance) return
    val tiers = account.performanceTiers
    val used = account.performanceUsed
    val achieved = account.performanceAchieved
    // 분모는 다음에 채울 단계다. 다 채웠으면 맨 위 단계. 막대 길이는 맨 위 단계에 맞춘다.
    val target = account.performanceNextTarget ?: tiers.last()
    val top = tiers.last()
    val doneColor = MaterialTheme.colorScheme.primary
    val rightText = performanceProgressWord(account)
    val prevText = performancePrevText(account)
    val currentBenefit = currentBenefitText(account)
    val nextBenefit = nextBenefitText(account)

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${account.name} ${account.performancePeriodLabel} 실적 " +
                "${used.formatWon()}, 목표 ${target.formatWon()}, $rightText, $prevText" +
                listOfNotNull(currentBenefit, nextBenefit).joinToString("") { ", $it" }
        },
    ) {
        // 한 줄에 다 들어가지 않으면 오른쪽 말을 아랫줄로 내린다. 목표 금액을 "700,00…"처럼 자르면 안 된다.
        WrapTrailing(gap = 8.dp) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${account.performancePeriodLabel} 실적 ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = used.formatWon(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = " / ${target.formatWon()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = rightText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (achieved) doneColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
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
            val fraction = (used.toDouble() / top).toFloat().coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (achieved) doneColor else Color(account.colorArgb)),
                )
            }
            // 중간 단계 자리에 눈금. 막대가 어디까지 차면 다음 혜택인지 보인다.
            tiers.dropLast(1).forEach { tier ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth((tier.toDouble() / top).toFloat())
                        .height(8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = prevText,
            style = MaterialTheme.typography.bodySmall,
            color = if (account.performancePrevAchieved) doneColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (currentBenefit != null) {
            Text(
                text = currentBenefit,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (account.currentBenefit != null) doneColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (nextBenefit != null) {
            Text(
                text = nextBenefit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 두 칸을 한 줄에 놓되, 둘 다 들어가지 않으면 두 번째 칸을 아랫줄 왼쪽으로 내린다.
 * 한 줄일 때는 첫 칸은 왼쪽, 두 번째 칸은 오른쪽 끝에 붙이고 아래쪽을 맞춘다.
 */
@Composable
private fun WrapTrailing(
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        require(measurables.size == 2)
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lead = measurables[0].measure(loose)
        val trail = measurables[1].measure(loose)
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        if (lead.width + gapPx + trail.width <= width) {
            val height = maxOf(lead.height, trail.height)
            layout(width, height) {
                lead.placeRelative(0, height - lead.height)
                trail.placeRelative(width - trail.width, height - trail.height)
            }
        } else {
            layout(width, lead.height + trail.height) {
                lead.placeRelative(0, 0)
                trail.placeRelative(0, lead.height)
            }
        }
    }
}

/**
 * 지금 받는 혜택. "이번 달 혜택 · 통신비 1만원 할인" / "이번 달 혜택 없음".
 *
 * 지난 실적 줄 바로 아래에 둔다. 혜택은 지난 실적으로 정해져서 "지난달 1단계 달성"의 결과가 이 줄이다.
 * 혜택을 하나도 안 적었거나, 채운 단계의 혜택만 비워 두었으면 null(줄을 내지 않는다).
 */
fun currentBenefitText(account: AccountBalance): String? {
    if (!account.hasBenefits) return null
    val label = "${account.performancePeriodLabel} 혜택"
    return when {
        account.currentBenefit != null -> "$label · ${account.currentBenefit}"
        account.tiersReached(account.performancePrev) == 0 -> "$label 없음"
        else -> null
    }
}

/**
 * 이번 실적으로 다음에 받을 혜택. 아직 채울 단계가 남았으면 그 단계의 혜택을 앞에 둔다.
 * "120,000원 더 쓰면 다음 달 혜택 · 10% 할인" / 다 채웠으면 "다음 달 혜택 · 10% 할인".
 */
fun nextBenefitText(account: AccountBalance): String? {
    if (!account.hasBenefits) return null
    val label = "${account.performanceNextLabel} 혜택"
    account.nextTierBenefit?.let { return "${account.performanceRemaining.formatWon()} 더 쓰면 $label · $it" }
    return account.nextCycleBenefit?.takeIf { account.performanceAchieved }?.let { "$label · $it" }
}

/**
 * "지난달 실적 320,000원 · 달성" / "지난달 실적 210,000원 · 90,000원 미달".
 * 단계가 있으면 "지난달 실적 520,000원 · 1단계 달성". 미달은 1단계까지 모자란 금액이다.
 */
fun performancePrevText(account: AccountBalance): String {
    val prev = account.performancePrev
    // 1일이 아닌 날부터 세면 "지난달"이 달력의 지난달과 다르다. 어느 날 기준인지 함께 적는다.
    val basis = account.performanceCustomStartDay?.let { " · ${it}일 기준" }.orEmpty()
    return "${account.performancePrevLabel} 실적 ${prev.formatWon()} · " +
        performancePrevWord(account, withShortfall = true) + basis
}

/**
 * 지난 실적 한 마디. "달성" / "1단계 달성" / "미달"([withShortfall] 이면 "90,000원 미달").
 * 단계가 있으면 몇 단계인지가 곧 이번 혜택이라 "달성" 대신 단계를 적는다.
 */
fun performancePrevWord(account: AccountBalance, withShortfall: Boolean = false): String {
    val reached = account.tiersReached(account.performancePrev)
    return when {
        reached > 0 && account.isTiered -> "${reached}단계 달성"
        reached > 0 -> "달성"
        withShortfall -> "${((account.performanceTiers.firstOrNull() ?: 0L) - account.performancePrev).formatWon()} 미달"
        else -> "미달"
    }
}

/**
 * 이번 실적 한 마디. "120,000원 남음" / "달성".
 * 단계가 있으면 채운 단계를 앞에 붙인다: "1단계 달성 · 180,000원 남음"(다음 단계까지) / "2단계 달성".
 */
fun performanceProgressWord(account: AccountBalance): String {
    val reached = account.tiersReached(account.performanceUsed)
    return when {
        account.performanceAchieved -> if (account.isTiered) "${reached}단계 달성" else "달성"
        reached > 0 -> "${reached}단계 달성 · ${remainingWord(account.performanceRemaining)}"
        else -> remainingWord(account.performanceRemaining)
    }
}

/** 남은 금액 한 마디. 0보다 작으면 넘긴 것이다. 한도·실적·예산이 모두 이 말을 쓴다. */
fun remainingWord(remaining: Long): String =
    if (remaining < 0) "${(-remaining).formatWon()} 초과" else "${remaining.formatWon()} 남음"

/** 한 줄 요약. "올해 한도 380,000원 남음" / "올해 한도 120,000원 초과". */
fun limitSummary(account: AccountBalance): String =
    "${account.limitPeriodLabel} 한도 ${remainingWord(account.limitRemaining)}"

/** 한 줄 요약. "이번 달 실적 30,000원 남음" / "이번 달 실적 달성" / "이번 달 실적 1단계 달성 · 180,000원 남음". */
fun performanceSummary(account: AccountBalance): String =
    "${account.performancePeriodLabel} 실적 ${performanceProgressWord(account)}"
