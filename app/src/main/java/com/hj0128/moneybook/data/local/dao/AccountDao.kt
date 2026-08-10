package com.hj0128.moneybook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
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

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}
