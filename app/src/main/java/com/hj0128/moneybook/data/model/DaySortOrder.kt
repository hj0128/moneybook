package com.hj0128.moneybook.data.model

/** 일별 탭에서 날짜를 늘어놓는 방향. */
enum class DaySortOrder {
    /** 31일 → 1일. 방금 쓴 내역을 바로 확인하는 용도. */
    NEWEST_FIRST,

    /** 1일 → 31일. 한 달을 처음부터 훑어보는 용도. */
    OLDEST_FIRST;

    val label: String
        get() = when (this) {
            NEWEST_FIRST -> "최신순"
            OLDEST_FIRST -> "오래된순"
        }

    fun toggled(): DaySortOrder = when (this) {
        NEWEST_FIRST -> OLDEST_FIRST
        OLDEST_FIRST -> NEWEST_FIRST
    }
}
