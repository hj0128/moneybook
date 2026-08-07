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
)
