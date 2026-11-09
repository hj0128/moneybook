package com.hj0128.moneybook

import com.hj0128.moneybook.data.model.LedgerPeriod
import com.hj0128.moneybook.data.model.rangeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LedgerPeriodTest {

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `15일부터 다음 달 14일은 한 달로 떨어진다`() {
        assertEquals(1L, LedgerPeriod.Range(date(3, 15), date(4, 14)).wholeMonths)
        assertEquals(3L, LedgerPeriod.Range(date(1, 1), date(3, 31)).wholeMonths)
        assertNull(LedgerPeriod.Range(date(3, 15), date(4, 20)).wholeMonths)
        assertNull(LedgerPeriod.Range(date(3, 15), date(3, 15)).wholeMonths)
    }

    @Test
    fun `달로 떨어지는 기간은 달 단위로 옮긴다`() {
        // 3.15 ~ 4.14 를 한 칸 옮기면 4.15 ~ 5.14. 날 수(31일)로 옮기면 4.15 ~ 5.15 가 된다.
        assertEquals(
            LedgerPeriod.Range(date(4, 15), date(5, 14)),
            LedgerPeriod.Range(date(3, 15), date(4, 14)).shifted(1),
        )
        assertEquals(
            LedgerPeriod.Range(date(12, 15, 2025), date(1, 14)),
            LedgerPeriod.Range(date(1, 15), date(2, 14)).shifted(-1),
        )
    }

    @Test
    fun `달로 떨어지지 않는 기간은 그 날 수만큼 옮긴다`() {
        val week = LedgerPeriod.Range(date(3, 2), date(3, 8))

        assertEquals(7L, week.days)
        assertEquals(LedgerPeriod.Range(date(3, 9), date(3, 15)), week.shifted(1))
        assertEquals(LedgerPeriod.Range(date(2, 23), date(3, 1)), week.shifted(-1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `끝이 시작보다 빠르면 만들 수 없다`() {
        LedgerPeriod.Range(date(4, 14), date(3, 15))
    }

    @Test
    fun `기간 제목은 올해면 연도를 빼고 넘어가면 붙인다`() {
        assertEquals("3.15 ~ 4.14", rangeLabel(date(3, 15), date(4, 14), thisYear = 2026))
        assertEquals("2025.12.15 ~ 2026.1.14", rangeLabel(date(12, 15, 2025), date(1, 14), thisYear = 2026))
        assertEquals("2025.3.15 ~ 4.14", rangeLabel(date(3, 15, 2025), date(4, 14, 2025), thisYear = 2026))
        assertEquals("3.15", rangeLabel(date(3, 15), date(3, 15), thisYear = 2026))
    }

    @Test
    fun `월과 년은 그 단위로 옮긴다`() {
        assertEquals(LedgerPeriod.Year(2025), LedgerPeriod.Year(2026).shifted(-1))
        assertEquals(date(12, 1, 2025), LedgerPeriod.Month(java.time.YearMonth.of(2026, 1)).shifted(-1).start)
    }
}
