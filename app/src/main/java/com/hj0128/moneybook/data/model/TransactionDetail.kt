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
    val merchant: String,
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

    /**
     * 화면에 보여줄 제목.
     *
     * 사용처가 있으면 그것이 제목이다. "식비 8,000원"보다 "김밥천국 8,000원"이
     * 무슨 지출이었는지 훨씬 빨리 알아보게 한다.
     * 사용처를 안 적었거나 v3 이전에 쌓인 내역은 예전처럼 카테고리 이름을 쓴다.
     * 이체는 카테고리가 없으므로 계좌 흐름을 제목으로 쓴다.
     */
    val title: String
        get() = when {
            type == TransactionType.TRANSFER -> "${accountName ?: "?"} → ${toAccountName ?: "?"}"
            merchant.isNotBlank() -> merchant
            else -> categoryName ?: "미분류"
        }

    /**
     * 제목 아래 첫 줄: 카테고리 | 결제수단.
     *
     * 거래처를 안 적어 제목이 카테고리가 된 경우에도 카테고리를 그대로 다시 쓴다.
     * 되풀이처럼 보여도 줄마다 같은 자리에 같은 값이 오는 편이 훑기 쉽다.
     * 조건에 따라 첫 줄이 "카테고리 | 결제수단"이 되었다가 "결제수단"만 되면
     * 두 번째 값이 무엇인지 매번 다시 읽어야 한다.
     *
     * 이체만 예외다. 카테고리가 없고 두 자산은 이미 제목("현금 → 은행 계좌")에 있다.
     */
    val subtitle: String
        get() = if (type == TransactionType.TRANSFER) {
            ""
        } else {
            listOfNotNull(categoryName ?: "미분류", accountName).joinToString(" | ")
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
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    val balance: Long,
)
