package com.hj0128.moneybook.data.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 고정 지출의 주기. 무엇을 기준으로 다음 날을 정할지는 기준일(anchor)에서 읽는다.
 *
 * 격주나 2개월마다 같은 간격은 두지 않는다. 쓰는 사람이 드문데 고르는 칸만 복잡해진다.
 */
enum class RepeatUnit {
    WEEK,
    MONTH,
    YEAR;

    /** 고르는 칸의 이름. "매주" / "매월" / "매년" */
    val label: String
        get() = when (this) {
            WEEK -> "매주"
            MONTH -> "매월"
            YEAR -> "매년"
        }

    /** 합계 줄 이름. "매주 나가는 돈"처럼 앞에 붙인다. 매월만 말로 할 때는 "매달"이 자연스럽다. */
    val spokenLabel: String
        get() = if (this == MONTH) "매달" else label

    /** [date] 에 넣고 난 다음 차례. 없는 날(31일, 2월 29일)은 그 달 말일로 당긴다. */
    fun occurrenceAfter(date: LocalDate, anchor: LocalDate): LocalDate = when (this) {
        WEEK -> date.plusWeeks(1)
        MONTH -> YearMonth.from(date).plusMonths(1).occurrence(anchor.dayOfMonth)
        YEAR -> YearMonth.of(date.year + 1, anchor.month).occurrence(anchor.dayOfMonth)
    }

    /** "매주 일요일" / "매월 25일" / "매년 3월 15일". 없는 날이 생기는 경우엔 어떻게 되는지 같이 적는다. */
    fun describe(anchor: LocalDate): String = when (this) {
        WEEK -> "매주 ${anchor.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}"
        MONTH -> if (anchor.dayOfMonth >= 29) "매월 ${anchor.dayOfMonth}일 (없는 달은 말일)" else "매월 ${anchor.dayOfMonth}일"
        YEAR -> if (anchor.monthValue == 2 && anchor.dayOfMonth == 29) {
            "매년 2월 29일 (평년은 28일)"
        } else {
            "매년 ${anchor.monthValue}월 ${anchor.dayOfMonth}일"
        }
    }

    /** 목록 맨 앞 동그라미에 들어갈 짧은 값. "일" / "25일" / "3/15" */
    fun badge(anchor: LocalDate): String = when (this) {
        WEEK -> anchor.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        MONTH -> "${anchor.dayOfMonth}일"
        YEAR -> "${anchor.monthValue}/${anchor.dayOfMonth}"
    }

    /**
     * 이 주기에서 [date] 가 속한 칸의 시작. 이미 넣은 칸으로 다음 날짜를 되돌리지 못하게 할 때 쓴다.
     *
     * 매주는 칸을 따로 두지 않고 날짜 그대로 본다. 요일을 바꾸는 경우(일요일 → 수요일)
     * 같은 주 안의 뒷날로 옮기는 것은 막을 이유가 없다.
     */
    fun periodStart(date: LocalDate): LocalDate = when (this) {
        WEEK -> date
        MONTH -> date.withDayOfMonth(1)
        YEAR -> date.withDayOfYear(1)
    }

    /** [periodStart] 다음 칸의 시작. "이 날부터 고를 수 있다"고 알려줄 때 쓴다. */
    fun nextPeriodStart(date: LocalDate): LocalDate = when (this) {
        WEEK -> date.plusDays(1)
        MONTH -> date.withDayOfMonth(1).plusMonths(1)
        YEAR -> date.withDayOfYear(1).plusYears(1)
    }
}

/** 그 달의 [dayOfMonth] 일. 없는 날이면 말일. */
fun YearMonth.occurrence(dayOfMonth: Int): LocalDate = atDay(dayOfMonth.coerceAtMost(lengthOfMonth()))
