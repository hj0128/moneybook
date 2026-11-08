package com.hj0128.moneybook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 실적에서 빼는 카테고리. 카드마다 따로 정한다.
 *
 * 카드사는 공과금, 상품권, 아파트 관리비 같은 결제를 실적으로 쳐 주지 않는다. 그런 것까지 세면
 * "달성"이라고 나와도 실제로는 혜택을 못 받는다. 빼는 기준이 카드마다 달라서 자산 쪽에 붙인다.
 *
 * 대분류를 넣으면 그 아래 소분류도 함께 빠진다. "공과금"을 빼면서 전기·가스를 하나씩 고르게 하지 않는다.
 */
@Entity(
    tableName = "account_performance_exclusions",
    primaryKeys = ["account_id", "category_id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            // 카테고리가 없어지면 뺄 것도 없다.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class PerformanceExclusionEntity(
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
)
