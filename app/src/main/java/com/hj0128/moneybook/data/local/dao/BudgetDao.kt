package com.hj0128.moneybook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hj0128.moneybook.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /** 전부. 카테고리 수 × 바꾼 횟수라 몇십 줄이다. 어느 달에 얼마인지는 받는 쪽에서 고른다. */
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    /** 그 달부터의 금액을 적는다. 같은 달에 이미 적은 것이 있으면 바꾼다. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)
}
