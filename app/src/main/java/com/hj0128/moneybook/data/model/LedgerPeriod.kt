package com.hj0128.moneybook.data.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 집계 단위. 카테고리별 탭에서 한 달을 볼지 한 해를 통째로 볼지. */
enum class PeriodUnit(val label: String) {
    MONTH("월"),
    YEAR("년"),
}

/**
 * 카테고리별 탭에서 기간을 고르는 방식. 한 달 / 한 해 / 직접 고른 날짜 범위.
 *
 * [PeriodUnit] 에 "기간"을 더하지 않은 이유: 그쪽은 한도·실적 주기로 DB 에 저장되는 값이다.
 * 한도를 "3월 15일~4월 14일"마다 다시 셀 수는 없으니 섞으면 고를 수 없는 값이 생긴다.
 */
enum class PeriodMode(val label: String) {
    MONTH("월"),
    YEAR("년"),
    RANGE("기간"),
}

/**
 * 집계 기간. 카테고리별 탭과 카테고리 상세가 같은 값을 주고받는다.
 *
 * DAO 는 원래 시작일~종료일 범위만 받으므로 기간 종류가 늘어도 쿼리는 그대로다.
 */
sealed interface LedgerPeriod {
    val start: LocalDate
    val end: LocalDate
    val mode: PeriodMode

    /** 상단 제목용. "2026년 10월" / "2026년" / "3.15 ~ 4.14" */
    val label: String

    /** 상세 화면 제목처럼 좁은 자리용. "10월" / "2026년" / "3.15 ~ 4.14" */
    val shortLabel: String

    /** 빈 화면 문구용. "이 달" / "이 해" / "이 기간" */
    val thisLabel: String

    /** 같은 단위로 [by] 칸 옮긴 기간. 한 달이면 달을, 한 해면 해를, 직접 고른 기간이면 그 길이만큼 옮긴다. */
    fun shifted(by: Int): LedgerPeriod

    data class Month(val yearMonth: YearMonth) : LedgerPeriod {
        override val start: LocalDate get() = yearMonth.atDay(1)
        override val end: LocalDate get() = yearMonth.atEndOfMonth()
        override val mode: PeriodMode get() = PeriodMode.MONTH
        override val label: String get() = "${yearMonth.year}년 ${yearMonth.monthValue}월"
        override val shortLabel: String get() = "${yearMonth.monthValue}월"
        override val thisLabel: String get() = "이 달"
        override fun shifted(by: Int): LedgerPeriod = Month(yearMonth.plusMonths(by.toLong()))
    }

    data class Year(val year: Int) : LedgerPeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = LocalDate.of(year, 12, 31)
        override val mode: PeriodMode get() = PeriodMode.YEAR
        override val label: String get() = "${year}년"
        override val shortLabel: String get() = label
        override val thisLabel: String get() = "이 해"
        override fun shifted(by: Int): LedgerPeriod = Year(year + by)
    }

    /**
     * 직접 고른 날짜 범위. 양 끝 날을 모두 넣는다.
     *
     * 카드 명세서처럼 "15일 ~ 다음 달 14일"을 보려고 쓰는 일이 많다. 그런 모양이면 화살표가
     * 날짜 수가 아니라 달 단위로 옮긴다. 31일인 달과 30일인 달이 섞여도 늘 15일에서 시작한다.
     */
    data class Range(override val start: LocalDate, override val end: LocalDate) : LedgerPeriod {
        init {
            require(!end.isBefore(start)) { "끝나는 날이 시작하는 날보다 빠릅니다." }
        }

        override val mode: PeriodMode get() = PeriodMode.RANGE

        /** 양 끝을 넣은 날 수. 3월 15일~3월 15일이면 1일이다. */
        val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

        /** "15일 ~ 다음 달 14일"처럼 달로 딱 떨어지면 그 달 수, 아니면 null. */
        val wholeMonths: Long?
            get() {
                val months = ChronoUnit.MONTHS.between(start, end.plusDays(1))
                return months.takeIf { it >= 1 && start.plusMonths(it) == end.plusDays(1) }
            }

        override val label: String get() = rangeLabel(start, end, LocalDate.now().year)
        override val shortLabel: String get() = label
        override val thisLabel: String get() = "이 기간"

        override fun shifted(by: Int): LedgerPeriod {
            val months = wholeMonths
            return if (months != null) {
                val newStart = start.plusMonths(months * by)
                Range(newStart, newStart.plusMonths(months).minusDays(1))
            } else {
                Range(start.plusDays(days * by), end.plusDays(days * by))
            }
        }
    }

    companion object {
        /** 보고 있던 달을 기준으로 단위만 바꾼다. 년으로 갔다가 월로 돌아와도 그 달이 그대로다. */
        fun of(unit: PeriodUnit, yearMonth: YearMonth): LedgerPeriod = when (unit) {
            PeriodUnit.MONTH -> Month(yearMonth)
            PeriodUnit.YEAR -> Year(yearMonth.year)
        }
    }
}

/**
 * "3.15 ~ 4.14". 올해가 아닌 날이 끼면 연도를 붙인다: "2025.12.15 ~ 2026.1.14".
 *
 * 상단 제목 자리가 좁아 "3월 15일 ~ 4월 14일"처럼 풀어 쓰면 검색·메뉴 버튼과 부딪친다.
 * 끝 날짜는 시작과 같은 해면 연도를 다시 쓰지 않는다.
 */
internal fun rangeLabel(start: LocalDate, end: LocalDate, thisYear: Int): String {
    fun md(date: LocalDate) = "${date.monthValue}.${date.dayOfMonth}"
    val startText = if (start.year == thisYear && end.year == thisYear) md(start) else "${start.year}.${md(start)}"
    val endText = if (end.year == start.year) md(end) else "${end.year}.${md(end)}"
    return if (start == end) startText else "$startText ~ $endText"
}
