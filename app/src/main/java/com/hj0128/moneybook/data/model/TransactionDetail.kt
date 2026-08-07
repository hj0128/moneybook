package com.hj0128.moneybook.data.model

import androidx.room.ColumnInfo
import java.time.LocalDate

/** 리스트 화면에서 쓰는 조회 전용 모델(거래 + 카테고리/자산 이름을 조인한 결과). */
data class TransactionDetail(
    val id: Long,
    val amount: Long,
    val type: TransactionType,
    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,
    val memo: String,
    @ColumnInfo(name = "category_name")
    val categoryName: String?,
    @ColumnInfo(name = "category_color")
    val categoryColor: Int?,
    @ColumnInfo(name = "account_name")
    val accountName: String?,
    @ColumnInfo(name = "to_account_name")
    val toAccountName: String?,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)

    /** 화면에 보여줄 제목. 이체는 카테고리가 없으므로 계좌 흐름을 제목으로 쓴다. */
    val title: String
        get() = when (type) {
            TransactionType.TRANSFER -> "${accountName ?: "?"} → ${toAccountName ?: "?"}"
            else -> categoryName ?: "미분류"
        }
}

/** 월 단위 합계. */
data class MonthlySummary(
    val income: Long = 0L,
    val expense: Long = 0L,
) {
    /** 이체는 자산 간 이동일 뿐이라 수입/지출 어디에도 포함되지 않는다. */
    val net: Long get() = income - expense
}

/** 자산 목록 + 현재 잔액. */
data class AccountBalance(
    val id: Long,
    val name: String,
    val type: AccountType,
    val balance: Long,
)
