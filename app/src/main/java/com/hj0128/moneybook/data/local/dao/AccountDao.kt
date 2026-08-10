package com.hj0128.moneybook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hj0128.moneybook.data.local.entity.AccountEntity
import com.hj0128.moneybook.data.model.AccountBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    /**
     * 잔액은 따로 저장하지 않고 항상 거래에서 계산한다.
     * 잔액 컬럼을 두면 거래 수정/삭제 때마다 동기화가 어긋나 결국 틀린 숫자가 남는다.
     */
    @Query(
        """
        SELECT a.id AS id,
               a.name AS name,
               a.type AS type,
               a.color_argb AS color_argb,
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
        ORDER BY a.sort_order ASC, a.id ASC
        """
    )
    fun observeBalances(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM accounts WHERE name = :name AND id != :excludeId")
    suspend fun countSameName(name: String, excludeId: Long): Int

    /** 이 자산이 쓰인 거래 수. 이체는 출금/입금 어느 쪽이든 걸린다. */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE account_id = :accountId OR to_account_id = :accountId
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

    @Query("UPDATE transactions SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassignOutgoing(fromId: Long, toId: Long)

    @Query("UPDATE transactions SET to_account_id = :toId WHERE to_account_id = :fromId")
    suspend fun reassignIncoming(fromId: Long, toId: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order) + 1, 0) FROM accounts")
    suspend fun nextSortOrder(): Int

    /**
     * 이전 후 삭제.
     *
     * account_id 는 NOT NULL 이라 카테고리처럼 비워둘 수 없고, 외래키가 CASCADE 라
     * 그냥 지우면 그 자산의 거래가 함께 사라진다. 반드시 옮긴 뒤에 지운다.
     */
    @Transaction
    suspend fun deleteWithReassign(accountId: Long, toId: Long) {
        deleteSelfTransfersAfterMove(accountId, toId)
        reassignOutgoing(accountId, toId)
        reassignIncoming(accountId, toId)
        deleteById(accountId)
    }

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}
