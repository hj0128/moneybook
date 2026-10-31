package com.hj0128.moneybook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.model.PeriodUnit

/** 자산(현금, 국민은행, 신한카드 ...). */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: AccountType,
    /** 앱 사용 시작 시점의 잔액(원). 현재 잔액은 거래를 누적해 계산한다. */
    @ColumnInfo(name = "initial_balance")
    val initialBalance: Long = 0L,
    /**
     * 선택 화면에서 쓰는 색상 (ARGB).
     *
     * 종류(현금/은행/카드)가 아니라 자산 하나하나에 붙는다. 종류로 색을 정하면
     * 은행 계좌를 둘 만들었을 때 둘이 같은 색이 되어 구분이 안 된다.
     */
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    /**
     * 사용 한도(원). 복지포인트처럼 주기마다 쓸 수 있는 금액이 정해진 결제수단에만 둔다.
     *
     * 잔액으로 대신하지 않는 이유: 잔액은 해가 바뀌어도 저절로 채워지지 않아 매년 손으로
     * 맞춰야 한다. 한도는 주기만 정해 두면 사용액이 달력 기준으로 알아서 0부터 다시 센다.
     */
    @ColumnInfo(name = "limit_amount")
    val limitAmount: Long? = null,
    /** 한도가 다시 차는 주기. 달력 기준(매월 1일 / 매년 1월 1일)이다. [limitAmount] 와 함께 있거나 함께 없다. */
    @ColumnInfo(name = "limit_period")
    val limitPeriod: PeriodUnit? = null,
)
