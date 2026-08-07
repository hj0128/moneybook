package com.hj0128.moneybook

import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.ledger.CategoryDetailUiState
import com.hj0128.moneybook.ui.ledger.toDayGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CategoryDetailTest {

    private val august = YearMonth.of(2026, 8)

    private fun expense(amount: Long, date: LocalDate) = TransactionDetail(
        id = date.toEpochDay() * 100 + amount,
        amount = amount,
        type = TransactionType.EXPENSE,
        dateEpochDay = date.toEpochDay(),
        memo = "",
        categoryName = "식비",
        categoryColor = null,
        accountName = "현금",
        toAccountName = null,
    )

    private fun stateOf(vararg items: TransactionDetail) = CategoryDetailUiState(
        yearMonth = august,
        categoryName = "식비",
        groups = items.toList().toDayGroups(),
    )

    @Test
    fun `같은 날 내역은 하나로 묶인다`() {
        val sixth = LocalDate.of(2026, 8, 6)
        val fourth = LocalDate.of(2026, 8, 4)

        val state = stateOf(
            expense(24_000, sixth),
            expense(8_000, sixth),
            expense(12_500, fourth),
        )

        assertEquals(2, state.groups.size)
        assertEquals(sixth, state.groups[0].date)
        assertEquals(2, state.groups[0].items.size)
        assertEquals(32_000L, state.groups[0].dayTotal.expense)
    }

    @Test
    fun `합계와 건수는 모든 그룹을 가로질러 센다`() {
        val state = stateOf(
            expense(24_000, LocalDate.of(2026, 8, 6)),
            expense(8_000, LocalDate.of(2026, 8, 6)),
            expense(12_500, LocalDate.of(2026, 8, 4)),
        )

        assertEquals(44_500L, state.total)
        assertEquals(3, state.entryCount)
    }

    @Test
    fun `하루 평균은 거래한 날이 아니라 그 달의 일수로 나눈다`() {
        // 8월은 31일. 이틀 동안 62,000원을 썼어도 하루 평균은 2,000원이다.
        val state = stateOf(
            expense(31_000, LocalDate.of(2026, 8, 6)),
            expense(31_000, LocalDate.of(2026, 8, 7)),
        )

        assertEquals(62_000L, state.total)
        assertEquals(2_000L, state.dailyAverage)
    }

    @Test
    fun `2월은 28일로 나눈다`() {
        val february = CategoryDetailUiState(
            yearMonth = YearMonth.of(2026, 2),
            groups = listOf(expense(28_000, LocalDate.of(2026, 2, 3))).toDayGroups(),
        )

        assertEquals(1_000L, february.dailyAverage)
    }

    @Test
    fun `내역이 없으면 비어 있고 합계는 0`() {
        val state = stateOf()

        assertTrue(state.isEmpty)
        assertEquals(0L, state.total)
        assertEquals(0, state.entryCount)
        assertEquals(0L, state.dailyAverage)
    }
}
