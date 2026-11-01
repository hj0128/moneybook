package com.hj0128.moneybook.data.model

import com.hj0128.moneybook.data.local.entity.CategoryEntity
import java.time.YearMonth

/** 예산 화면의 한 줄. 대분류 지출 카테고리 하나와 그 달 예산, 그 달에 쓴 돈(소분류 포함). */
data class BudgetRow(
    val category: CategoryEntity,
    /** 그 달 예산. 정하지 않았으면 null. */
    val budget: Long?,
    val spent: Long,
)

/**
 * 한 달 예산 전체. 예산이 없는 카테고리는 예산 0원으로 친다.
 *
 * 줄마다 0원 예산으로 보여주니 합계도 같은 셈이어야 줄을 더한 값과 맞는다.
 * 예산 없는 곳에 쓴 돈은 그만큼 초과로 잡힌다.
 */
data class BudgetSummary(
    val month: YearMonth,
    val rows: List<BudgetRow>,
) {
    val totalBudget: Long get() = rows.sumOf { it.budget ?: 0L }

    val totalSpent: Long get() = rows.sumOf { it.spent }
}
