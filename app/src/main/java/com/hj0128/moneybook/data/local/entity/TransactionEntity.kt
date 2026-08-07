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
    val memo: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
