package com.hj0128.moneybook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hj0128.moneybook.data.local.entity.TransactionEntity
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.MonthTotal
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.UncategorizedTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /**
     * 기간 내 거래를 최신순으로. Flow 라서 insert/delete 가 일어나면 UI가 자동으로 갱신된다.
     * 같은 날짜 안에서는 입력한 순서의 역순(created_at DESC)으로 보여준다.
     */
    @Query(
        """
        SELECT t.id AS id,
               t.amount AS amount,
               t.type AS type,
               t.date_epoch_day AS date_epoch_day,
               t.merchant AS merchant,
               t.memo AS memo,
               c.name AS category_name,
               c.color_argb AS category_color,
               a.name AS account_name,
               ta.name AS to_account_name
        FROM transactions AS t
        LEFT JOIN categories AS c ON c.id = t.category_id
        LEFT JOIN accounts AS a ON a.id = t.account_id
        LEFT JOIN accounts AS ta ON ta.id = t.to_account_id
        WHERE t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY t.date_epoch_day DESC, t.created_at DESC
        """
    )
    fun observeBetween(startEpochDay: Long, endEpochDay: Long): Flow<List<TransactionDetail>>

    /**
     * 월별 탭용. 12개월치를 한 번의 쿼리로 집계한다.
     *
     * date_epoch_day 는 '일' 단위라 86400 을 곱해 unix 초로 만든 뒤 월을 뽑는다.
     * 저장할 때 LocalDate.toEpochDay() 를 그대로 넣었으므로 UTC 로 해석해도 날짜가 어긋나지 않는다.
     */
    @Query(
        """
        SELECT CAST(strftime('%m', t.date_epoch_day * 86400, 'unixepoch') AS INTEGER) AS month_value,
               SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END) AS income,
               SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense
        FROM transactions AS t
        WHERE t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY month_value
        ORDER BY month_value ASC
        """
    )
    fun observeMonthlyTotals(startEpochDay: Long, endEpochDay: Long): Flow<List<MonthTotal>>

    /**
     * 카테고리별 탭용. 이체는 category_id 가 없어 INNER JOIN 에서 자연히 빠진다.
     *
     * 소분류에 붙은 거래는 부모 대분류로 합친다(식비/마트 + 식비/외식 -> 식비).
     * 대분류에 직접 붙은 거래는 부모가 없으므로 자기 자신으로 묶인다.
     */
    @Query(
        """
        SELECT COALESCE(p.id, c.id) AS category_id,
               COALESCE(p.name, c.name) AS name,
               COALESCE(p.color_argb, c.color_argb) AS color_argb,
               SUM(t.amount) AS total,
               COUNT(t.id) AS entry_count
        FROM transactions AS t
        INNER JOIN categories AS c ON c.id = t.category_id
        LEFT JOIN categories AS p ON p.id = c.parent_id
        WHERE t.type = :type
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY COALESCE(p.id, c.id),
                 COALESCE(p.name, c.name),
                 COALESCE(p.color_argb, c.color_argb)
        ORDER BY total DESC
        """
    )
    fun observeCategoryTotals(
        type: TransactionType,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<CategoryTotal>>

    /**
     * 카테고리 상세의 소분류 breakdown.
     *
     * 대분류에 직접 붙은 거래도 한 줄로 함께 나온다(그 대분류 이름 그대로).
     * 소분류를 안 만든 카테고리면 결과가 한 줄뿐이라 화면에서 감춘다.
     */
    @Query(
        """
        SELECT c.id AS category_id,
               c.name AS name,
               c.color_argb AS color_argb,
               SUM(t.amount) AS total,
               COUNT(t.id) AS entry_count
        FROM transactions AS t
        INNER JOIN categories AS c ON c.id = t.category_id
        WHERE (t.category_id = :categoryId OR c.parent_id = :categoryId)
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY c.id, c.name, c.color_argb
        ORDER BY total DESC
        """
    )
    fun observeChildTotals(
        categoryId: Long,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<CategoryTotal>>

    /**
     * 카테고리 상세용. 한 카테고리의 기간 내 내역만 최신순으로.
     *
     * 대분류를 열면 그 아래 소분류에 붙은 거래까지 함께 나온다.
     * 카테고리는 그 자체로 수입용/지출용이 정해져 있어 type 조건이 따로 필요 없다.
     * 이체는 category_id 가 null 이라 여기에도 걸리지 않는다.
     */
    @Query(
        """
        SELECT t.id AS id,
               t.amount AS amount,
               t.type AS type,
               t.date_epoch_day AS date_epoch_day,
               t.merchant AS merchant,
               t.memo AS memo,
               c.name AS category_name,
               c.color_argb AS category_color,
               a.name AS account_name,
               ta.name AS to_account_name
        FROM transactions AS t
        LEFT JOIN categories AS c ON c.id = t.category_id
        LEFT JOIN accounts AS a ON a.id = t.account_id
        LEFT JOIN accounts AS ta ON ta.id = t.to_account_id
        WHERE (t.category_id = :categoryId OR c.parent_id = :categoryId)
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY t.date_epoch_day DESC, t.created_at DESC
        """
    )
    fun observeByCategory(
        categoryId: Long,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<TransactionDetail>>

    /**
     * 카테고리가 빠진 거래의 합계.
     *
     * 카테고리를 지우면서 미분류로 보낸 것들이다. 카테고리별 탭에서 이 줄을 감추면
     * 일별 탭의 지출 합계와 카테고리 합계가 조용히 어긋나 사용자가 이유를 알 수 없다.
     * 이체는 원래 카테고리가 없으므로 type 조건에서 걸러진다.
     */
    @Query(
        """
        SELECT COUNT(*) AS entry_count, COALESCE(SUM(amount), 0) AS total
        FROM transactions
        WHERE category_id IS NULL
          AND type = :type
          AND date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        """
    )
    fun observeUncategorizedTotal(
        type: TransactionType,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<UncategorizedTotal>

    /** 미분류 상세용. 카테고리가 없으니 종류로 좁힌다. */
    @Query(
        """
        SELECT t.id AS id,
               t.amount AS amount,
               t.type AS type,
               t.date_epoch_day AS date_epoch_day,
               t.merchant AS merchant,
               t.memo AS memo,
               NULL AS category_name,
               NULL AS category_color,
               a.name AS account_name,
               ta.name AS to_account_name
        FROM transactions AS t
        LEFT JOIN accounts AS a ON a.id = t.account_id
        LEFT JOIN accounts AS ta ON ta.id = t.to_account_id
        WHERE t.category_id IS NULL
          AND t.type = :type
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY t.date_epoch_day DESC, t.created_at DESC
        """
    )
    fun observeUncategorized(
        type: TransactionType,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<TransactionDetail>>

    /** 일괄 재분류. 고른 내역을 한 카테고리로 옮긴다. */
    @Query("UPDATE transactions SET category_id = :categoryId WHERE id IN (:ids)")
    suspend fun assignCategory(ids: List<Long>, categoryId: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
