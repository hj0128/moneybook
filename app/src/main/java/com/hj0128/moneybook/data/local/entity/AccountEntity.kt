package com.hj0128.moneybook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hj0128.moneybook.data.model.AccountType

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
)
