package com.hj0128.moneybook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sort_order ASC, id ASC")
    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>>

    /** 상세 화면 제목용. 카테고리가 지워지면 null 이 흘러온다. */
    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeById(id: Long): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    /** 같은 부모 밑 이름 중복 검사. 대분류끼리는 parent_id 가 둘 다 null 인 경우다. */
    @Query(
        """
        SELECT COUNT(*) FROM categories
        WHERE type = :type
          AND name = :name
          AND id != :excludeId
          AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
        """
    )
    suspend fun countSameName(
        type: TransactionType,
        name: String,
        parentId: Long?,
        excludeId: Long,
    ): Int

    /** 이 카테고리에 '직접' 붙은 거래 수. 소분류 것은 세지 않는다. */
    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
    suspend fun countDirectUsage(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM categories WHERE parent_id = :categoryId")
    suspend fun countChildren(categoryId: Long): Int

    @Query("UPDATE transactions SET category_id = :toId WHERE category_id = :fromId")
    suspend fun reassignTransactions(fromId: Long, toId: Long)

    @Query("UPDATE categories SET parent_id = :toParentId WHERE parent_id = :fromParentId")
    suspend fun reassignChildren(fromParentId: Long, toParentId: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 이전 후 삭제.
     *
     * 소분류에 붙은 거래는 건드리지 않고 소분류째로 새 부모 밑에 옮긴다.
     * 소분류 거래까지 대상 카테고리로 몰아버리면 "외식 55,000"이 "교통 55,000"이 되어
     * 기록이 사실과 달라진다. 옮기는 건 이 카테고리에 직접 붙은 거래뿐이다.
     */
    @Transaction
    suspend fun deleteWithReassign(categoryId: Long, reassignToId: Long?) {
        if (reassignToId != null) {
            reassignTransactions(categoryId, reassignToId)
            reassignChildren(categoryId, reassignToId)
        }
        deleteById(categoryId)
    }

    /** 같은 부모 밑에서 맨 뒤에 붙일 정렬값. */
    @Query(
        """
        SELECT COALESCE(MAX(sort_order) + 1, 0) FROM categories
        WHERE type = :type
          AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
        """
    )
    suspend fun nextSortOrder(type: TransactionType, parentId: Long?): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
