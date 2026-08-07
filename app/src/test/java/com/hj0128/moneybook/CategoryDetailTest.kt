package com.hj0128.moneybook

import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.UncategorizedTotal
import com.hj0128.moneybook.ui.ledger.CategoryDetailUiState
import com.hj0128.moneybook.ui.ledger.CategoryUiState
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
    fun `소분류가 갈려 있을 때만 breakdown 을 낸다`() {
        val split = CategoryDetailUiState(
            yearMonth = august,
            categoryName = "식비",
            childTotals = listOf(
                CategoryTotal(categoryId = 20, name = "외식", colorArgb = 0, total = 55_000, entryCount = 2),
                CategoryTotal(categoryId = 21, name = "마트", colorArgb = 0, total = 20_500, entryCount = 1),
            ),
            groups = listOf(expense(75_500, LocalDate.of(2026, 8, 6))).toDayGroups(),
        )
        // 소분류를 안 만든 카테고리는 자기 자신 한 줄만 나온다. 합계 카드와 같은 숫자라 감춘다.
        val flat = split.copy(
            childTotals = listOf(
                CategoryTotal(categoryId = 1, name = "식비", colorArgb = 0, total = 75_500, entryCount = 1),
            ),
        )

        assertTrue(split.hasBreakdown)
        assertEquals(false, flat.hasBreakdown)
    }

    @Test
    fun `소분류 비중은 전체 합계 기준으로 센다`() {
        val state = CategoryDetailUiState(
            yearMonth = august,
            childTotals = listOf(
                CategoryTotal(categoryId = 20, name = "외식", colorArgb = 0, total = 30_000, entryCount = 1),
                CategoryTotal(categoryId = 21, name = "마트", colorArgb = 0, total = 10_000, entryCount = 1),
            ),
            groups = listOf(
                expense(30_000, LocalDate.of(2026, 8, 6)),
                expense(10_000, LocalDate.of(2026, 8, 4)),
            ).toDayGroups(),
        )

        assertEquals(40_000L, state.total)
        assertEquals(0.75f, state.childRatioOf(state.childTotals[0]), 0.001f)
        assertEquals(0.25f, state.childRatioOf(state.childTotals[1]), 0.001f)
    }

    @Test
    fun `카테고리별 합계는 미분류까지 더한다`() {
        // 카테고리 줄만 더하면 일별 탭의 지출 합계와 숫자가 어긋난다.
        val state = CategoryUiState(
            yearMonth = august,
            totals = listOf(
                CategoryTotal(categoryId = 1, name = "식비", colorArgb = 0, total = 75_500, entryCount = 4),
                CategoryTotal(categoryId = 2, name = "교통", colorArgb = 0, total = 1_500, entryCount = 1),
            ),
            uncategorized = UncategorizedTotal(entryCount = 2, total = 23_000),
        )

        assertEquals(100_000L, state.sum)
        assertEquals(0.23f, state.ratioOf(23_000L), 0.001f)
        assertEquals(false, state.isEmpty)
    }

    @Test
    fun `미분류만 있어도 빈 화면이 아니다`() {
        val state = CategoryUiState(
            yearMonth = august,
            totals = emptyList(),
            uncategorized = UncategorizedTotal(entryCount = 3, total = 9_000),
        )

        assertEquals(false, state.isEmpty)
        assertEquals(9_000L, state.sum)
    }

    @Test
    fun `카테고리도 미분류도 없으면 빈 화면`() {
        assertTrue(CategoryUiState(yearMonth = august).isEmpty)
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
