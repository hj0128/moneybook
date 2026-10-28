package com.hj0128.moneybook.data.model

import java.time.LocalDate
import java.time.YearMonth

/** 집계 단위. 카테고리별 탭에서 한 달을 볼지 한 해를 통째로 볼지. */
enum class PeriodUnit(val label: String) {
    MONTH("월"),
    YEAR("년"),
}

/**
 * 집계 기간. 카테고리별 탭과 카테고리 상세가 같은 값을 주고받는다.
 *
 * DAO 는 원래 시작일~종료일 범위만 받으므로 기간 종류가 늘어도 쿼리는 그대로다.
 * 나중에 "기간 지정"을 넣으면 여기에 한 종류만 더하면 된다.
 */
sealed interface LedgerPeriod {
    val start: LocalDate
    val end: LocalDate
    val unit: PeriodUnit

    /** 상단 제목용. "2026년 10월" / "2026년" */
    val label: String

    /** 상세 화면 제목처럼 좁은 자리용. "10월" / "2026년" */
    val shortLabel: String

    /** 빈 화면 문구용. "이 달" / "이 해" */
    val thisLabel: String

    data class Month(val yearMonth: YearMonth) : LedgerPeriod {
        override val start: LocalDate get() = yearMonth.atDay(1)
        override val end: LocalDate get() = yearMonth.atEndOfMonth()
        override val unit: PeriodUnit get() = PeriodUnit.MONTH
        override val label: String get() = "${yearMonth.year}년 ${yearMonth.monthValue}월"
        override val shortLabel: String get() = "${yearMonth.monthValue}월"
        override val thisLabel: String get() = "이 달"
    }

    data class Year(val year: Int) : LedgerPeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = LocalDate.of(year, 12, 31)
        override val unit: PeriodUnit get() = PeriodUnit.YEAR
        override val label: String get() = "${year}년"
        override val shortLabel: String get() = label
        override val thisLabel: String get() = "이 해"
    }

    companion object {
        /** 보고 있던 달을 기준으로 단위만 바꾼다. 년으로 갔다가 월로 돌아와도 그 달이 그대로다. */
        fun of(unit: PeriodUnit, yearMonth: YearMonth): LedgerPeriod = when (unit) {
            PeriodUnit.MONTH -> Month(yearMonth)
            PeriodUnit.YEAR -> Year(yearMonth.year)
        }
    }
}
