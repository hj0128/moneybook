package com.hj0128.moneybook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hj0128.moneybook.data.model.TransactionType

/**
 * 거래 내역. 이 앱의 심장.
 *
 * - [amount] 는 항상 양수이고 단위는 '원'. 부호는 [type] 이 결정한다.
 *   금액을 Double 로 저장하면 부동소수점 오차 때문에 합계가 틀어지므로 Long 을 쓴다.
 * - [dateEpochDay] 는 LocalDate.toEpochDay(). 정수라 정렬/범위 조회가 빠르고 타임존 영향이 없다.
 * - TRANSFER 인 경우에만 [toAccountId] 가 채워진다(출금 계좌 -> 입금 계좌).
 * - 한 번의 결제를 카테고리나 결제수단별로 나눴으면 줄마다 따로 저장하고 [splitGroupId] 로 묶는다.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("date_epoch_day"),
        Index("category_id"),
        Index("account_id"),
        Index("to_account_id"),
        Index("split_group_id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 항상 양수(원). */
    val amount: Long,
    val type: TransactionType,
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    /** 이체일 때의 입금 계좌. */
    @ColumnInfo(name = "to_account_id")
    val toAccountId: Long? = null,
    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,
    /** 어디서 썼는지(스타벅스, 이마트 ...). 목록에서 이 건이 무엇이었는지 알아보게 하는 값. */
    val merchant: String = "",
    val memo: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /**
     * 같은 결제에서 나온 줄들의 묶음 번호. 나누지 않은 내역은 null.
     *
     * 다이소에서 한 번 긁은 7,500원이 생필품 1,500원 + 식비 6,000원이면 두 줄로 저장하고 같은 번호를 준다.
     * 줄마다 평범한 내역이라 카테고리별·결제수단별 집계와 한도는 손대지 않아도 맞는다.
     * 번호는 묶음의 첫 줄 id 를 쓴다. 따로 순번 테이블을 둘 필요가 없다.
     *
     * 날짜·거래처·결제수단이 같다고 같은 결제로 볼 수는 없다. 같은 날 같은 가게에서 두 번
     * 따로 긁었을 수도 있다. 그래서 입력할 때 "나눠서 입력"으로 넣은 것만 이 번호로 묶는다.
     */
    @ColumnInfo(name = "split_group_id")
    val splitGroupId: Long? = null,
)
