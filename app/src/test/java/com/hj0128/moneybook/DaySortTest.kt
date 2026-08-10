package com.hj0128.moneybook

import com.hj0128.moneybook.data.model.DaySortOrder
import com.hj0128.moneybook.data.model.MonthlySummary
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.ledger.DayGroup
import com.hj0128.moneybook.ui.ledger.applySort
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DaySortTest {

    private fun detail(id: Long) = TransactionDetail(
        id = id,
        amount = 1_000,
        type = TransactionType.EXPENSE,
        dateEpochDay = 0,
        merchant = "",
        memo = "",
        categoryName = null,
        categoryColor = null,
        accountName = null,
        toAccountName = null,
    )

    /** DAO 가 주는 순서: 날짜 내림차순, 같은 날 안에서는 나중에 입력한 것이 먼저. */
    private val groups = listOf(
        DayGroup(LocalDate.of(2026, 8, 7), MonthlySummary(), listOf(detail(3), detail(2))),
        DayGroup(LocalDate.of(2026, 8, 1), MonthlySummary(), listOf(detail(1))),
    )

    @Test
    fun `최신순은 DAO 순서를 그대로 쓴다`() {
        val sorted = groups.applySort(DaySortOrder.NEWEST_FIRST)

        assertEquals(listOf(7, 1), sorted.map { it.date.dayOfMonth })
        assertEquals(listOf(3L, 2L), sorted.first().items.map { it.id })
    }

    @Test
    fun `오래된순은 날짜와 같은 날 안의 순서를 모두 뒤집는다`() {
        val sorted = groups.applySort(DaySortOrder.OLDEST_FIRST)

        assertEquals(listOf(1, 7), sorted.map { it.date.dayOfMonth })
        assertEquals(listOf(2L, 3L), sorted.last().items.map { it.id })
    }

    @Test
    fun `내역이 없으면 어느 정렬이든 빈 목록`() {
        val empty = emptyList<DayGroup>()

        assertEquals(empty, empty.applySort(DaySortOrder.NEWEST_FIRST))
        assertEquals(empty, empty.applySort(DaySortOrder.OLDEST_FIRST))
    }

    @Test
    fun `두 번 뒤집으면 원래대로 돌아온다`() {
        val roundTrip = groups
            .applySort(DaySortOrder.OLDEST_FIRST)
            .applySort(DaySortOrder.OLDEST_FIRST)

        assertEquals(groups, roundTrip)
    }
}
