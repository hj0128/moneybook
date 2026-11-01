package com.hj0128.moneybook.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val koreanLocale = Locale.KOREA
private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(koreanLocale)

/** 1234567 -> "1,234,567" */
fun Long.formatAmount(): String = numberFormat.format(this)

/** 1234567 -> "1,234,567원" */
fun Long.formatWon(): String = "${formatAmount()}원"

/** 부호를 붙인 표기. 합계처럼 +/- 가 의미 있는 자리에서 쓴다. */
fun Long.formatSignedWon(): String = when {
    this > 0 -> "+${formatAmount()}원"
    this < 0 -> "-${(-this).formatAmount()}원"
    else -> "0원"
}

private val monthDayFormatter = DateTimeFormatter.ofPattern("M월 d일", koreanLocale)
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일", koreanLocale)

/** "8월 7일 (금)" */
fun LocalDate.formatListHeader(): String {
    val dayOfWeek = dayOfWeek.getDisplayName(TextStyle.SHORT, koreanLocale)
    return "${format(monthDayFormatter)} ($dayOfWeek)"
}

fun LocalDate.formatFull(): String = format(fullDateFormatter)

/** "10만원", "3만 5,000원", "8,000원". 금액 범위처럼 좁은 자리에 짧게 적을 때 쓴다. */
fun Long.formatShortWon(): String {
    val man = this / 10_000
    val rest = this % 10_000
    return when {
        man == 0L -> formatWon()
        rest == 0L -> "${man.formatAmount()}만원"
        else -> "${man.formatAmount()}만 ${rest.formatAmount()}원"
    }
}
