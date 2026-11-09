package com.hj0128.moneybook.data.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * 실적을 세는 한 주기. 양 끝 날을 모두 넣는다.
 *
 * 매월 실적은 카드마다 세기 시작하는 날이 다를 수 있다("매월 15일 ~ 다음 달 14일").
 * 그 달에 그 날이 없으면(31일 시작인데 30일까지인 달) 그 달 마지막 날에 시작한다.
 * 매년 실적은 늘 1월 1일부터다. 해마다 시작일이 다른 카드는 거의 없다.
 */
data class PerformanceCycle(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        /** [today] 가 들어 있는 주기. [startDay] 가 null 이거나 1이면 달력 기준이다. */
        fun current(period: PeriodUnit, startDay: Int?, today: LocalDate): PerformanceCycle = when (period) {
            PeriodUnit.YEAR -> PerformanceCycle(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 12, 31))
            PeriodUnit.MONTH -> {
                val day = startDay ?: 1
                val thisMonth = startIn(YearMonth.from(today), day)
                val start = if (today.isBefore(thisMonth)) startIn(YearMonth.from(today).minusMonths(1), day) else thisMonth
                PerformanceCycle(start, startIn(YearMonth.from(start).plusMonths(1), day).minusDays(1))
            }
        }

        /** 바로 전 주기. 혜택은 보통 이 주기의 실적으로 정해진다. */
        fun previous(period: PeriodUnit, startDay: Int?, today: LocalDate): PerformanceCycle =
            current(period, startDay, current(period, startDay, today).start.minusDays(1))

        private fun startIn(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))
    }
}
