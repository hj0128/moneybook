package com.hj0128.moneybook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hj0128.moneybook.data.model.RepeatUnit
import com.hj0128.moneybook.data.model.TransactionType
import java.time.LocalDate

/**
 * 고정 지출(월세, 통신비, 자동차보험, 주간 용돈, 월급, 적금 자동이체 ...). 정한 주기마다 내역 한 줄을 넣는다.
 *
 * 내역을 미리 몇 번치 만들어 두지 않고 "다음에 넣을 날"만 들고 있다가, 그날이 지나면 그때 넣는다.
 * 미리 만들어 두면 금액이나 결제수단을 바꿀 때 아직 오지 않은 내역을 찾아 고쳐야 하고,
 * 목록과 합계에 오지도 않은 돈이 섞인다.
 *
 * 앱을 며칠 안 열었어도 빠지지 않는다. 열었을 때 [nextEpochDay] 부터 오늘까지 밀린 것을 한꺼번에 넣는다.
 */
@Entity(
    tableName = "recurring",
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
    indices = [Index("category_id"), Index("account_id"), Index("to_account_id")],
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amount: Long,
    val type: TransactionType,
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "to_account_id")
    val toAccountId: Long? = null,
    val merchant: String = "",
    val memo: String = "",
    @ColumnInfo(name = "repeat_unit")
    val repeatUnit: RepeatUnit,
    /**
     * 주기를 정한 날. 매주면 이 날의 요일, 매월이면 이 날의 "일", 매년이면 이 날의 월·일마다 넣는다.
     *
     * [nextEpochDay] 에서 다시 읽지 않고 따로 두는 이유: 31일짜리가 4월에 30일로 들어가고 나면
     * 다음 달부터 30일로 굳어 버린다. 2월 29일짜리 매년도 마찬가지다.
     */
    @ColumnInfo(name = "anchor_epoch_day")
    val anchorEpochDay: Long,
    /** 다음에 넣을 날(LocalDate.toEpochDay()). 이날이 오늘이거나 지났으면 넣을 차례다. */
    @ColumnInfo(name = "next_epoch_day")
    val nextEpochDay: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /**
     * 넣는 내역마다 "실적 제외"를 붙인다. 카드로 자동이체하는 통신비·관리비는 카드사가 실적으로 쳐 주지 않는 경우가 많다.
     * 고쳐도 이미 넣은 내역은 그대로다(다른 칸과 같다).
     */
    @ColumnInfo(name = "excluded_from_performance", defaultValue = "0")
    val excludedFromPerformance: Boolean = false,
) {
    val nextDate: LocalDate get() = LocalDate.ofEpochDay(nextEpochDay)

    val anchor: LocalDate get() = LocalDate.ofEpochDay(anchorEpochDay)

    /** [date] 에 넣고 난 다음 차례. */
    fun occurrenceAfter(date: LocalDate): LocalDate = repeatUnit.occurrenceAfter(date, anchor)

    fun toTransaction(date: LocalDate, nowMillis: Long): TransactionEntity = TransactionEntity(
        amount = amount,
        type = type,
        categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
        accountId = accountId,
        toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
        dateEpochDay = date.toEpochDay(),
        merchant = merchant,
        memo = memo,
        createdAt = nowMillis,
        recurringId = id,
        excludedFromPerformance = excludedFromPerformance,
    )
}
