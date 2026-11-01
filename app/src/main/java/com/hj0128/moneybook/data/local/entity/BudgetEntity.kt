package com.hj0128.moneybook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.time.YearMonth

/**
 * 카테고리 예산. 대분류 지출 카테고리에만 둔다.
 *
 * 달마다 한 줄씩 미리 만들지 않고 "이 달부터 이 금액"만 적는다. 정하지 않은 달은 그 전에 정한 금액을
 * 이어받는다. 매달 같은 금액을 다시 넣게 하면 아무도 안 쓰고, 그렇다고 금액을 하나만 두면
 * 명절이 낀 달처럼 그 달만 다르게 정할 수가 없다.
 *
 * 소분류 지출은 대분류 예산에 함께 센다. 카테고리별 탭이 소분류를 대분류로 합쳐 보여주니
 * 예산도 같은 덩어리여야 "식비 42만 / 50만"이 탭의 식비 금액과 맞는다.
 */
@Entity(
    tableName = "budgets",
    primaryKeys = ["category_id", "year_month"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            // 카테고리가 없어지면 예산도 뜻이 없다.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetEntity(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    /** 이 금액이 시작되는 달. 2026년 11월이면 202611. 정수라 크기 비교가 곧 달 순서다. */
    @ColumnInfo(name = "year_month")
    val yearMonth: Int,
    /** 한 달 예산(원). null 이면 이 달부터 예산을 없앤 것이다. */
    val amount: Long?,
)

fun YearMonth.toKey(): Int = year * 100 + monthValue

/**
 * [month] 에 적용되는 카테고리별 예산. 그 달이나 그 전에 마지막으로 정한 금액이다.
 * 예산을 없앤 카테고리(마지막 값이 null)는 결과에 없다.
 */
fun List<BudgetEntity>.amountsFor(month: YearMonth): Map<Long, Long> {
    val key = month.toKey()
    return filter { it.yearMonth <= key }
        .groupBy { it.categoryId }
        .mapNotNull { (categoryId, entries) ->
            entries.maxBy { it.yearMonth }.amount?.let { categoryId to it }
        }
        .toMap()
}
