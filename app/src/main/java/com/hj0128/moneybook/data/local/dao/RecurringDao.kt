package com.hj0128.moneybook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hj0128.moneybook.data.local.entity.RecurringEntity
import com.hj0128.moneybook.data.local.entity.TransactionEntity
import com.hj0128.moneybook.data.model.RecurringDetail
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringDao {

    /** 관리 화면용. 주기 안에서의 순서는 화면에서 정한다(요일·일·월일이 기준일 안에 섞여 있다). */
    @Query(
        """
        SELECT r.id AS id,
               r.amount AS amount,
               r.type AS type,
               r.merchant AS merchant,
               r.memo AS memo,
               r.repeat_unit AS repeat_unit,
               r.anchor_epoch_day AS anchor_epoch_day,
               r.next_epoch_day AS next_epoch_day,
               c.name AS category_name,
               p.name AS parent_category_name,
               c.color_argb AS category_color,
               a.name AS account_name,
               ta.name AS to_account_name,
               r.excluded_from_performance AS excluded_from_performance
        FROM recurring AS r
        LEFT JOIN categories AS c ON c.id = r.category_id
        LEFT JOIN categories AS p ON p.id = c.parent_id
        LEFT JOIN accounts AS a ON a.id = r.account_id
        LEFT JOIN accounts AS ta ON ta.id = r.to_account_id
        ORDER BY r.created_at ASC, r.id ASC
        """
    )
    fun observeAll(): Flow<List<RecurringDetail>>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun findById(id: Long): RecurringEntity?

    @Insert
    suspend fun insert(recurring: RecurringEntity): Long

    @Update
    suspend fun update(recurring: RecurringEntity)

    /** 그만두기. 이미 넣은 내역은 남는다(recurring_id 만 비워진다). */
    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 이 고정 지출이 마지막으로 넣은 내역의 날짜. 아직 없으면 null. */
    @Query("SELECT MAX(date_epoch_day) FROM transactions WHERE recurring_id = :id")
    suspend fun lastEnteredEpochDay(id: Long): Long?

    @Query("SELECT * FROM recurring WHERE next_epoch_day <= :todayEpochDay")
    suspend fun findDue(todayEpochDay: Long): List<RecurringEntity>

    @Query("UPDATE recurring SET next_epoch_day = :nextEpochDay WHERE id = :id")
    suspend fun setNext(id: Long, nextEpochDay: Long)

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    /**
     * 차례가 온 고정 지출을 내역으로 넣는다. 돌려주는 값은 넣은 줄 수.
     *
     * 넣기와 다음 날 옮기기를 한 트랜잭션으로 묶는다. 둘 사이에 앱이 죽으면 다음에 열 때
     * 같은 달이 또 들어간다. 화면이 두 번 켜져 동시에 불려도 트랜잭션이 차례로 돌아 겹치지 않는다.
     */
    @Transaction
    suspend fun applyDue(today: LocalDate, nowMillis: Long): Int {
        var count = 0
        for (rule in findDue(today.toEpochDay())) {
            var date = rule.nextDate
            while (!date.isAfter(today)) {
                insertTransaction(rule.toTransaction(date, nowMillis))
                count++
                date = rule.occurrenceAfter(date)
            }
            setNext(rule.id, date.toEpochDay())
        }
        return count
    }
}
