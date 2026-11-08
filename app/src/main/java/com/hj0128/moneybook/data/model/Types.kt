package com.hj0128.moneybook.data.model

/**
 * 거래 종류.
 *
 * TRANSFER(계좌 이체)는 처음부터 넣어둔다. 나중에 추가하면 통계/합계 로직을 전부 손봐야 한다.
 * 이체는 수입도 지출도 아니므로 월 합계 계산에서 제외된다.
 */
enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER;

    val label: String
        get() = when (this) {
            INCOME -> "수입"
            EXPENSE -> "지출"
            TRANSFER -> "이체"
        }

    /**
     * 자산 칸을 부르는 이름. 같은 칸이라도 종류에 따라 다르다.
     * 수입에 "결제수단"이라고 써 두면 무엇을 고르라는 건지 알 수 없다.
     */
    val accountLabel: String
        get() = if (this == INCOME) "입금 자산" else "결제수단"
}

/** 자산 종류. */
enum class AccountType {
    CASH,
    BANK,
    CARD;

    val label: String
        get() = when (this) {
            CASH -> "현금"
            BANK -> "은행"
            CARD -> "카드"
        }
}
