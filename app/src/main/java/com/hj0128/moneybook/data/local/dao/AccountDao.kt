package com.hj0128.moneybook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hj0128.moneybook.data.local.entity.AccountEntity
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    /**
     * 잔액은 따로 저장하지 않고 항상 거래에서 계산한다.
     * 잔액 컬럼을 두면 거래 수정/삭제 때마다 동기화가 어긋나 결국 틀린 숫자가 남는다.
     *
     * 한도 사용액도 같은 이유로 여기서 계산한다. 주기가 자산마다 달라서(매월/매년)
     * 이번 달과 올해 범위를 둘 다 받아 자산별로 골라 쓴다.
     *
     * 순서는 화면에 놓이는 순서다. 상위 다음에 그 하위가 붙어 나온다.
     */
    @Query(
        """
        SELECT a.id AS id,
               a.name AS name,
               a.type AS type,
               a.color_argb AS color_argb,
               a.initial_balance AS initial_balance,
               a.limit_amount AS limit_amount,
               a.limit_period AS limit_period,
               a.parent_id AS parent_id,
               a.sort_order AS sort_order,
               CASE WHEN a.limit_amount IS NULL THEN 0 ELSE IFNULL((
                   SELECT SUM(t.amount)
                   FROM transactions AS t
                   WHERE t.account_id = a.id
                     AND t.type = 'EXPENSE'
                     AND t.date_epoch_day BETWEEN
                         (CASE a.limit_period WHEN 'YEAR' THEN :yearStart ELSE :monthStart END)
                         AND (CASE a.limit_period WHEN 'YEAR' THEN :yearEnd ELSE :monthEnd END)
               ), 0) END AS limit_used,
               a.initial_balance
                 + IFNULL((
                     SELECT SUM(CASE t.type WHEN 'INCOME' THEN t.amount ELSE -t.amount END)
                     FROM transactions AS t WHERE t.account_id = a.id
                   ), 0)
                 + IFNULL((
                     SELECT SUM(t.amount)
                     FROM transactions AS t WHERE t.to_account_id = a.id
                   ), 0) AS balance
        FROM accounts AS a
        LEFT JOIN accounts AS p ON p.id = a.parent_id
        ORDER BY COALESCE(p.sort_order, a.sort_order) ASC,
                 COALESCE(a.parent_id, a.id) ASC,
                 a.parent_id IS NOT NULL ASC,
                 a.sort_order ASC, a.id ASC
        """
    )
    fun observeBalances(
        monthStart: Long,
        monthEnd: Long,
        yearStart: Long,
        yearEnd: Long,
    ): Flow<List<AccountBalance>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE name = :name AND id != :excludeId")
    suspend fun countSameName(name: String, excludeId: Long): Int

    /**
     * 이 자산이 쓰인 거래 수. 이체는 출금/입금 어느 쪽이든 걸린다.
     *
     * 고정 지출도 센다. 아직 한 번도 들어가지 않은 고정 지출만 걸린 자산을 "쓰인 적 없음"으로 보고
     * 그냥 지우면, 외래키 CASCADE 로 고정 지출이 말없이 사라진다.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions
                WHERE account_id = :accountId OR to_account_id = :accountId)
             + (SELECT COUNT(*) FROM recurring
                WHERE account_id = :accountId OR to_account_id = :accountId)
        """
    )
    suspend fun countUsage(accountId: Long): Int

    /**
     * 이전했을 때 출금과 입금이 같아져 버리는 이체 수.
     *
     * 현금 -> 은행 이체가 있는데 현금을 지우고 은행으로 옮기면 은행 -> 은행이 된다.
     * 자산 간 이동이라는 뜻이 사라지므로 그대로 둘 수 없다. 몇 건인지 미리 알려준다.
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE type = 'TRANSFER'
          AND ((account_id = :fromId AND to_account_id = :toId)
            OR (account_id = :toId AND to_account_id = :fromId))
        """
    )
    suspend fun countSelfTransfersAfterMove(fromId: Long, toId: Long): Int

    @Query(
        """
        DELETE FROM transactions
        WHERE type = 'TRANSFER'
          AND ((account_id = :fromId AND to_account_id = :toId)
            OR (account_id = :toId AND to_account_id = :fromId))
        """
    )
    suspend fun deleteSelfTransfersAfterMove(fromId: Long, toId: Long)

    /** 옮기면 출금과 입금이 같아지는 고정 이체. 내역과 같은 이유로 남겨 둘 수 없다. */
    @Query(
        """
        DELETE FROM recurring
        WHERE type = 'TRANSFER'
          AND ((account_id = :fromId AND to_account_id = :toId)
            OR (account_id = :toId AND to_account_id = :fromId))
        """
    )
    suspend fun deleteSelfRecurringAfterMove(fromId: Long, toId: Long)

    @Query("UPDATE transactions SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassignOutgoing(fromId: Long, toId: Long)

    @Query("UPDATE recurring SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassignRecurringOutgoing(fromId: Long, toId: Long)

    @Query("UPDATE recurring SET to_account_id = :toId WHERE to_account_id = :fromId")
    suspend fun reassignRecurringIncoming(fromId: Long, toId: Long)

    @Query("UPDATE transactions SET to_account_id = :toId WHERE to_account_id = :fromId")
    suspend fun reassignIncoming(fromId: Long, toId: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 같은 부모 밑에서 맨 뒤에 붙일 정렬값. */
    @Query(
        """
        SELECT COALESCE(MAX(sort_order) + 1, 0) FROM accounts
        WHERE (:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId
        """
    )
    suspend fun nextSortOrder(parentId: Long?): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE parent_id = :accountId")
    suspend fun countChildren(accountId: Long): Int

    @Query("SELECT id FROM accounts WHERE parent_id = :accountId ORDER BY sort_order ASC, id ASC")
    suspend fun childIds(accountId: Long): List<Long>

    @Query("UPDATE accounts SET type = :type WHERE parent_id = :parentId")
    suspend fun updateChildrenType(parentId: Long, type: AccountType)

    @Query("UPDATE accounts SET parent_id = :parentId, type = :type, sort_order = :sortOrder WHERE id = :id")
    suspend fun moveUnder(id: Long, parentId: Long, type: AccountType, sortOrder: Int)

    @Query("UPDATE accounts SET sort_order = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /** 넘겨준 순서대로 0,1,2... 를 다시 매긴다. 자세한 이유는 CategoryDao.applyOrder 참고. */
    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }

    /**
     * 이전 후 삭제.
     *
     * account_id 는 NOT NULL 이라 카테고리처럼 비워둘 수 없고, 외래키가 CASCADE 라
     * 그냥 지우면 그 자산의 거래가 함께 사라진다. 반드시 옮긴 뒤에 지운다.
     *
     * 하위 자산은 거래째로 [toId] 밑으로 옮긴다(CategoryDao.deleteWithReassign 과 같다).
     * 하위 거래까지 [toId] 로 몰면 "A카드 결제"가 다른 카드 결제로 바뀌어 기록이 사실과 달라진다.
     */
    @Transaction
    suspend fun deleteWithReassign(accountId: Long, toId: Long?) {
        if (toId == null) {
            deleteById(accountId)
            return
        }
        deleteSelfTransfersAfterMove(accountId, toId)
        reassignOutgoing(accountId, toId)
        reassignIncoming(accountId, toId)
        deleteSelfRecurringAfterMove(accountId, toId)
        reassignRecurringOutgoing(accountId, toId)
        reassignRecurringIncoming(accountId, toId)
        moveChildren(accountId, toId)
        deleteById(accountId)
    }

    /**
     * 하위 자산을 [toParentId] 밑 맨 끝으로 옮긴다. 종류는 새 상위를 따른다.
     *
     * 부모 번호만 바꾸면 하위에서 쓰던 순서 번호(0,1..)가 새 상위의 하위 번호와 겹친다.
     */
    private suspend fun moveChildren(fromParentId: Long, toParentId: Long) {
        val type = findById(toParentId)?.type ?: return
        var next = nextSortOrder(toParentId)
        childIds(fromParentId).forEach { moveUnder(it, toParentId, type, next++) }
    }

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}
