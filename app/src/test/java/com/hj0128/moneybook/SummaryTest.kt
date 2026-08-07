package com.hj0128.moneybook

import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.repository.toSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTest {

    private fun detail(amount: Long, type: TransactionType) = TransactionDetail(
        id = 0,
        amount = amount,
        type = type,
        dateEpochDay = 20_000,
        memo = "",
        categoryName = null,
        categoryColor = null,
        accountName = null,
        toAccountName = null,
    )

    @Test
    fun `수입과 지출을 각각 더한다`() {
        val summary = listOf(
            detail(3_000_000, TransactionType.INCOME),
            detail(12_500, TransactionType.EXPENSE),
            detail(7_500, TransactionType.EXPENSE),
        ).toSummary()

        assertEquals(3_000_000L, summary.income)
        assertEquals(20_000L, summary.expense)
        assertEquals(2_980_000L, summary.net)
    }

    @Test
    fun `이체는 수입에도 지출에도 잡히지 않는다`() {
        val summary = listOf(
            detail(1_000_000, TransactionType.TRANSFER),
            detail(5_000, TransactionType.EXPENSE),
        ).toSummary()

        assertEquals(0L, summary.income)
        assertEquals(5_000L, summary.expense)
        assertEquals(-5_000L, summary.net)
    }

    @Test
    fun `내역이 없으면 합계는 0`() {
        val summary = emptyList<TransactionDetail>().toSummary()

        assertEquals(0L, summary.income)
        assertEquals(0L, summary.expense)
        assertEquals(0L, summary.net)
    }
}
