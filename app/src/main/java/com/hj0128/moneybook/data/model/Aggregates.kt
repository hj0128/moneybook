package com.hj0128.moneybook.data.model

import androidx.room.ColumnInfo

/** 월별 탭에서 쓰는 한 달치 합계. */
data class MonthTotal(
    @ColumnInfo(name = "month_value")
    val monthValue: Int,
    val income: Long,
    val expense: Long,
) {
    val net: Long get() = income - expense
}

/** 카테고리가 빠진 거래의 합계. 0건이면 화면에 내지 않는다. */
data class UncategorizedTotal(
    @ColumnInfo(name = "entry_count")
    val entryCount: Int,
    val total: Long,
) {
    val isEmpty: Boolean get() = entryCount == 0
}

/** 카테고리별 탭에서 쓰는 카테고리 한 줄. */
data class CategoryTotal(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    val name: String,
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    val total: Long,
    @ColumnInfo(name = "entry_count")
    val entryCount: Int,
    /** 카테고리 관리 화면에서의 자리. 금액과 무관하게 늘 같은 순서를 보고 싶을 때 쓴다. */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)
