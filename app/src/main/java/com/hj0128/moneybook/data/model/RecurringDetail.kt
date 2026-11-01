package com.hj0128.moneybook.data.model

import androidx.room.ColumnInfo
import java.time.LocalDate

/** 고정 지출 관리 화면의 한 줄(규칙 + 카테고리/자산 이름). */
data class RecurringDetail(
    val id: Long,
    val amount: Long,
    val type: TransactionType,
    val merchant: String,
    val memo: String,
    @ColumnInfo(name = "repeat_unit")
    val repeatUnit: RepeatUnit,
    @ColumnInfo(name = "anchor_epoch_day")
    val anchorEpochDay: Long,
    @ColumnInfo(name = "next_epoch_day")
    val nextEpochDay: Long,
    @ColumnInfo(name = "category_name")
    val categoryName: String?,
    @ColumnInfo(name = "parent_category_name")
    val parentCategoryName: String?,
    @ColumnInfo(name = "category_color")
    val categoryColor: Int?,
    @ColumnInfo(name = "account_name")
    val accountName: String?,
    @ColumnInfo(name = "to_account_name")
    val toAccountName: String?,
) {
    val nextDate: LocalDate get() = LocalDate.ofEpochDay(nextEpochDay)

    val anchor: LocalDate get() = LocalDate.ofEpochDay(anchorEpochDay)

    /**
     * 같은 주기 안에서의 순서. 매주는 요일, 매월은 일, 매년은 월·일 순.
     * 다음 날짜로 줄 세우면 이번 달 것이 들어가고 나서 순서가 뒤바뀌어 찾던 줄이 움직인다.
     */
    val sortKey: Int
        get() = when (repeatUnit) {
            RepeatUnit.WEEK -> anchor.dayOfWeek.value
            RepeatUnit.MONTH -> anchor.dayOfMonth
            RepeatUnit.YEAR -> anchor.monthValue * 100 + anchor.dayOfMonth
        }

    /** 내역 목록과 같은 규칙. 거래처가 있으면 그것, 없으면 카테고리. */
    val title: String
        get() = when {
            type == TransactionType.TRANSFER -> "${accountName ?: "?"} → ${toAccountName ?: "?"}"
            merchant.isNotBlank() -> merchant
            else -> categoryName ?: "미분류"
        }

    val categoryLabel: String
        get() = parentCategoryName?.let { "$it › ${categoryName.orEmpty()}" } ?: categoryName ?: "미분류"

    val subtitle: String
        get() = if (type == TransactionType.TRANSFER) {
            "이체"
        } else {
            listOfNotNull(categoryLabel, accountName).joinToString(" | ")
        }
}
