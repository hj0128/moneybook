package com.hj0128.moneybook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hj0128.moneybook.data.local.entity.TransactionEntity
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.ExportRow
import com.hj0128.moneybook.data.model.MonthTotal
import com.hj0128.moneybook.data.model.SearchCandidate
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
               t.category_id AS category_id,
               c.name AS category_name,
               c.color_argb AS category_color,
               t.account_id AS account_id,
               t.excluded_from_performance AS excluded_from_performance,
               a.name AS account_name,
               ta.name AS to_account_name,
               t.split_group_id AS split_group_id,
               t.recurring_id AS recurring_id
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
               COUNT(t.id) AS entry_count,
               COALESCE(p.sort_order, c.sort_order) AS sort_order
        FROM transactions AS t
        INNER JOIN categories AS c ON c.id = t.category_id
        LEFT JOIN categories AS p ON p.id = c.parent_id
        WHERE t.type = :type
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY COALESCE(p.id, c.id),
                 COALESCE(p.name, c.name),
                 COALESCE(p.color_argb, c.color_argb),
                 COALESCE(p.sort_order, c.sort_order)
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
               COUNT(t.id) AS entry_count,
               c.sort_order AS sort_order
        FROM transactions AS t
        INNER JOIN categories AS c ON c.id = t.category_id
        WHERE (t.category_id = :categoryId OR c.parent_id = :categoryId)
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY c.id, c.name, c.color_argb, c.sort_order
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
               t.category_id AS category_id,
               c.name AS category_name,
               c.color_argb AS category_color,
               t.account_id AS account_id,
               t.excluded_from_performance AS excluded_from_performance,
               a.name AS account_name,
               ta.name AS to_account_name,
               t.split_group_id AS split_group_id,
               t.recurring_id AS recurring_id
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
               NULL AS category_id,
               NULL AS category_name,
               NULL AS category_color,
               t.account_id AS account_id,
               t.excluded_from_performance AS excluded_from_performance,
               a.name AS account_name,
               ta.name AS to_account_name,
               t.split_group_id AS split_group_id,
               t.recurring_id AS recurring_id
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

    /**
     * 결제수단별 집계. 카테고리별 탭에서 기준을 "결제수단"으로 바꿨을 때 쓴다.
     *
     * 줄 모양이 카테고리와 같아서 CategoryTotal 을 그대로 쓴다(category_id 자리에 자산 id).
     * 이체는 type 조건에서 빠진다. 자산 사이 이동이지 쓴 돈이 아니다.
     * 하위 자산 거래는 상위로 합친다(A카드 + B카드 -> 체크카드). 카테고리 집계와 같은 규칙이다.
     */
    @Query(
        """
        SELECT COALESCE(p.id, a.id) AS category_id,
               COALESCE(p.name, a.name) AS name,
               COALESCE(p.color_argb, a.color_argb) AS color_argb,
               SUM(t.amount) AS total,
               COUNT(t.id) AS entry_count,
               COALESCE(p.sort_order, a.sort_order) AS sort_order
        FROM transactions AS t
        INNER JOIN accounts AS a ON a.id = t.account_id
        LEFT JOIN accounts AS p ON p.id = a.parent_id
        WHERE t.type = :type
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY COALESCE(p.id, a.id),
                 COALESCE(p.name, a.name),
                 COALESCE(p.color_argb, a.color_argb),
                 COALESCE(p.sort_order, a.sort_order)
        ORDER BY total DESC
        """
    )
    fun observeAccountTotals(
        type: TransactionType,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<CategoryTotal>>

    /** 결제수단 상세용. 한 자산으로 낸(받은) 기간 내 내역을 최신순으로. 상위를 열면 하위 내역도 함께 나온다. */
    @Query(
        """
        SELECT t.id AS id,
               t.amount AS amount,
               t.type AS type,
               t.date_epoch_day AS date_epoch_day,
               t.merchant AS merchant,
               t.memo AS memo,
               t.category_id AS category_id,
               c.name AS category_name,
               c.color_argb AS category_color,
               t.account_id AS account_id,
               t.excluded_from_performance AS excluded_from_performance,
               a.name AS account_name,
               ta.name AS to_account_name,
               t.split_group_id AS split_group_id,
               t.recurring_id AS recurring_id
        FROM transactions AS t
        LEFT JOIN categories AS c ON c.id = t.category_id
        LEFT JOIN accounts AS a ON a.id = t.account_id
        LEFT JOIN accounts AS ta ON ta.id = t.to_account_id
        WHERE (t.account_id = :accountId OR a.parent_id = :accountId)
          AND t.type = :type
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY t.date_epoch_day DESC, t.created_at DESC
        """
    )
    fun observeByAccount(
        accountId: Long,
        type: TransactionType,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<TransactionDetail>>

    /**
     * 결제수단 상세의 카테고리별 소계("복지포인트로 무엇을 샀나").
     *
     * 소분류는 대분류로 합친다. 카테고리가 빠진 거래도 미분류 한 줄로 넣는다.
     * 빼면 이 소계의 합이 위 합계와 어긋난다. 미분류의 id 는 실제 id 와 겹치지 않게 -1 이다.
     */
    @Query(
        """
        SELECT COALESCE(p.id, c.id, -1) AS category_id,
               COALESCE(p.name, c.name, '미분류') AS name,
               COALESCE(p.color_argb, c.color_argb, -7303024) AS color_argb,
               SUM(t.amount) AS total,
               COUNT(t.id) AS entry_count,
               COALESCE(p.sort_order, c.sort_order, 2147483647) AS sort_order
        FROM transactions AS t
        LEFT JOIN categories AS c ON c.id = t.category_id
        LEFT JOIN categories AS p ON p.id = c.parent_id
        WHERE t.account_id = :accountId
          AND t.type = :type
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY COALESCE(p.id, c.id, -1)
        ORDER BY total DESC
        """
    )
    fun observeAccountCategoryTotals(
        accountId: Long,
        type: TransactionType,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<CategoryTotal>>

    /**
     * 결제수단 상세의 하위 자산 breakdown. 카테고리 상세의 소분류 breakdown 과 같다.
     *
     * 상위에 직접 붙은 거래도 한 줄로 함께 나온다(하위를 만들기 전에 쌓인 내역).
     */
    @Query(
        """
        SELECT a.id AS category_id,
               a.name AS name,
               a.color_argb AS color_argb,
               SUM(t.amount) AS total,
               COUNT(t.id) AS entry_count,
               a.sort_order AS sort_order
        FROM transactions AS t
        INNER JOIN accounts AS a ON a.id = t.account_id
        WHERE (t.account_id = :accountId OR a.parent_id = :accountId)
          AND t.type = :type
          AND t.date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY a.id, a.name, a.color_argb, a.sort_order
        ORDER BY total DESC
        """
    )
    fun observeAccountChildTotals(
        accountId: Long,
        type: TransactionType,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<CategoryTotal>>

    /**
     * 검색 대상 전체. 최신순.
     *
     * 걸러내기는 쿼리가 아니라 화면 쪽에서 한다. 여러 단어를 모두 포함하는지, "5,000원"을 금액으로 볼지 같은
     * 규칙을 LIKE 로 쓰면 단어 수만큼 쿼리가 달라진다. 개인 가계부는 몇 년치라도 수천 줄이라 메모리에서 충분하다.
     */
    @Query(
        """
        SELECT t.id AS id,
               t.amount AS amount,
               t.type AS type,
               t.date_epoch_day AS date_epoch_day,
               t.merchant AS merchant,
               t.memo AS memo,
               t.category_id AS category_id,
               c.name AS category_name,
               c.color_argb AS category_color,
               t.account_id AS account_id,
               t.excluded_from_performance AS excluded_from_performance,
               a.name AS account_name,
               ta.name AS to_account_name,
               t.split_group_id AS split_group_id,
               t.recurring_id AS recurring_id,
               p.name AS parent_category_name,
               c.parent_id AS parent_category_id
        FROM transactions AS t
        LEFT JOIN categories AS c ON c.id = t.category_id
        LEFT JOIN categories AS p ON p.id = c.parent_id
        LEFT JOIN accounts AS a ON a.id = t.account_id
        LEFT JOIN accounts AS ta ON ta.id = t.to_account_id
        ORDER BY t.date_epoch_day DESC, t.created_at DESC
        """
    )
    fun observeSearchCandidates(): Flow<List<SearchCandidate>>

    /** CSV 내보내기. 오래된 것부터, 같은 날은 입력한 순서대로. */
    @Query(
        """
        SELECT t.date_epoch_day AS date_epoch_day,
               t.type AS type,
               t.amount AS amount,
               COALESCE(p.name, c.name) AS parent_name,
               CASE WHEN p.id IS NULL THEN NULL ELSE c.name END AS child_name,
               t.account_id AS account_id,
               t.excluded_from_performance AS excluded_from_performance,
               a.name AS account_name,
               ta.name AS to_account_name,
               t.merchant AS merchant,
               t.memo AS memo,
               t.split_group_id AS split_group_id,
               t.recurring_id AS recurring_id
        FROM transactions AS t
        LEFT JOIN categories AS c ON c.id = t.category_id
        LEFT JOIN categories AS p ON p.id = c.parent_id
        LEFT JOIN accounts AS a ON a.id = t.account_id
        LEFT JOIN accounts AS ta ON ta.id = t.to_account_id
        ORDER BY t.date_epoch_day ASC, t.created_at ASC, t.id ASC
        """
    )
    suspend fun exportRows(): List<ExportRow>

    /** 일괄 재분류. 고른 내역을 한 카테고리로 옮긴다. */
    @Query("UPDATE transactions SET category_id = :categoryId WHERE id IN (:ids)")
    suspend fun assignCategory(ids: List<Long>, categoryId: Long)

    /**
     * 일괄 자산 옮기기. "체크카드"로 적어 둔 내역을 카드별 하위 자산으로 나눌 때 쓴다.
     *
     * 이체는 건드리지 않는다. 보내는 쪽과 받는 쪽이 함께 있어 한쪽만 바꾸면 뜻이 달라진다.
     * 상세 화면은 이체를 보여주지 않지만, 쿼리에서도 막아 둔다.
     */
    @Query("UPDATE transactions SET account_id = :accountId WHERE id IN (:ids) AND type != 'TRANSFER'")
    suspend fun assignAccount(ids: List<Long>, accountId: Long)

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

    /** 나눈 결제의 줄들. 입력한 순서대로. */
    @Query("SELECT * FROM transactions WHERE split_group_id = :groupId ORDER BY id")
    suspend fun findGroup(groupId: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE split_group_id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("UPDATE transactions SET split_group_id = :groupId WHERE id = :id")
    suspend fun setGroup(id: Long, groupId: Long)

    /**
     * 결제 하나를 통째로 바꾼다. [oldId] 가 있으면 그 결제(나눈 것이면 묶음 전체)를 지우고 [rows] 를 넣는다.
     *
     * 줄 수가 바뀌거나(2줄 -> 3줄, 나눔 <-> 한 줄) 줄마다 무엇이 바뀌었는지 맞춰 고치는 것보다
     * 지우고 다시 넣는 편이 단순하고 틀릴 곳이 없다. 내역 id 를 붙잡고 있는 곳이 없어서 id 가 바뀌어도 된다.
     */
    @Transaction
    suspend fun replacePayment(oldId: Long?, rows: List<TransactionEntity>) {
        if (oldId != null) deletePayment(oldId)
        if (rows.size == 1) {
            insert(rows.single().copy(splitGroupId = null))
            return
        }
        val firstId = insert(rows.first())
        setGroup(firstId, firstId)
        rows.drop(1).forEach { insert(it.copy(splitGroupId = firstId)) }
    }

    /** 결제 하나 삭제. 나눈 결제면 묶음 전체를 지운다. */
    @Transaction
    suspend fun deletePayment(id: Long) {
        val groupId = findById(id)?.splitGroupId
        if (groupId != null) deleteGroup(groupId) else deleteById(id)
    }
}
