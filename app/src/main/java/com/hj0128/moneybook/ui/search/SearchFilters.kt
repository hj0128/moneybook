package com.hj0128.moneybook.ui.search

import com.hj0128.moneybook.data.model.SearchCandidate
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.util.formatShortWon
import java.time.YearMonth

/**
 * 검색 필터. 모두 비어 있으면 거르지 않는다.
 *
 * 검색어 없이 필터만으로도 찾는다. "10만원 넘게 쓴 것"이나 "2023년 7~9월 식비"에는 칠 단어가 없다.
 *
 * 기간과 카테고리는 여럿 고를 수 있다("2023년 3월과 7월", "외식과 마트"). 같은 필터 안에서는 하나라도
 * 맞으면 되고, 서로 다른 필터끼리는 모두 맞아야 한다.
 */
data class SearchFilters(
    /** 지출만 / 수입만. null 이면 이체까지 전부. */
    val type: TransactionType? = null,
    val categories: List<CategoryFilter> = emptyList(),
    val amount: AmountRange? = null,
    /** 고른 달들. 이어지지 않아도 된다. */
    val months: Set<YearMonth> = emptySet(),
) {
    val isActive: Boolean
        get() = type != null || categories.isNotEmpty() || amount != null || months.isNotEmpty()

    fun matches(candidate: SearchCandidate): Boolean {
        val detail = candidate.detail
        if (type != null && detail.type != type) return false
        if (categories.isNotEmpty() && categories.none { it.matches(candidate) }) return false
        if (amount != null && detail.amount !in amount) return false
        if (months.isNotEmpty() && YearMonth.from(detail.date) !in months) return false
        return true
    }
}

/** 고른 카테고리. 대분류면 그 밑 소분류에 붙은 내역까지 포함한다. */
data class CategoryFilter(
    val id: Long,
    val name: String,
    val type: TransactionType,
) {
    fun matches(candidate: SearchCandidate): Boolean =
        candidate.detail.categoryId == id || candidate.parentCategoryId == id
}

/**
 * 금액 범위(원, 양 끝 포함). 한쪽만 있어도 된다.
 *
 * 나눈 결제는 줄마다 따진다. 카테고리가 줄마다 다르니 "식비 5천원 이상"은 식비 줄의 금액으로 보는 게 맞다.
 */
data class AmountRange(val min: Long?, val max: Long?) {
    operator fun contains(amount: Long): Boolean =
        (min == null || amount >= min) && (max == null || amount <= max)

    val label: String
        get() = when {
            min != null && max != null -> "${min.formatShortWon()} ~ ${max.formatShortWon()}"
            min != null -> "${min.formatShortWon()} 이상"
            max != null -> "${max.formatShortWon()} 이하"
            else -> "금액"
        }

    companion object {
        /** 둘 다 비면 필터가 없는 것이다. 거꾸로 넣었으면(10만 ~ 3만) 바로잡아 준다. */
        fun of(min: Long?, max: Long?): AmountRange? = when {
            min == null && max == null -> null
            min != null && max != null && min > max -> AmountRange(max, min)
            else -> AmountRange(min, max)
        }
    }
}

/** 이어진 달 묶음(양 끝 포함). 고른 달을 칩으로 보여줄 때 7·8·9월을 "7월 ~ 9월" 한 칩으로 묶는다. */
data class MonthRange(val start: YearMonth, val end: YearMonth) {
    val months: List<YearMonth>
        get() = generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= end }.toList()

    val label: String
        get() = when {
            start == end -> "${start.year}년 ${start.monthValue}월"
            start.year == end.year -> "${start.year}년 ${start.monthValue}월 ~ ${end.monthValue}월"
            else -> "${start.year}년 ${start.monthValue}월 ~ ${end.year}년 ${end.monthValue}월"
        }
}

/** 고른 달들을 이어진 것끼리 묶는다. 3·7·8·9월 → [3월], [7월 ~ 9월]. 오래된 것부터. */
fun Set<YearMonth>.toRanges(): List<MonthRange> {
    val ranges = mutableListOf<MonthRange>()
    for (month in sorted()) {
        val last = ranges.lastOrNull()
        if (last != null && last.end.plusMonths(1) == month) {
            ranges[ranges.lastIndex] = last.copy(end = month)
        } else {
            ranges += MonthRange(month, month)
        }
    }
    return ranges
}

/** 기간 빠른 선택. 고르면 그 달들이 한꺼번에 더해진다. */
enum class PeriodPreset(val label: String) {
    THIS_MONTH("이번 달"),
    LAST_MONTH("지난 달"),
    LAST_3_MONTHS("최근 3개월"),
    THIS_YEAR("올해"),
    LAST_YEAR("작년");

    fun range(today: YearMonth = YearMonth.now()): MonthRange = when (this) {
        THIS_MONTH -> MonthRange(today, today)
        LAST_MONTH -> today.minusMonths(1).let { MonthRange(it, it) }
        LAST_3_MONTHS -> MonthRange(today.minusMonths(2), today)
        THIS_YEAR -> MonthRange(YearMonth.of(today.year, 1), today)
        LAST_YEAR -> MonthRange(YearMonth.of(today.year - 1, 1), YearMonth.of(today.year - 1, 12))
    }
}
