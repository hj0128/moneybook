package com.hj0128.moneybook

import com.hj0128.moneybook.data.model.PerformanceCycle
import com.hj0128.moneybook.data.model.PeriodUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PerformanceCycleTest {

    private fun cycle(start: LocalDate, end: LocalDate) = PerformanceCycle(start, end)

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun `시작일이 없으면 달력 한 달이다`() {
        assertEquals(
            cycle(date(11, 1), date(11, 30)),
            PerformanceCycle.current(PeriodUnit.MONTH, null, date(11, 9)),
        )
        assertEquals(
            cycle(date(10, 1), date(10, 31)),
            PerformanceCycle.previous(PeriodUnit.MONTH, null, date(11, 9)),
        )
    }

    @Test
    fun `시작일이 지났으면 이번 달 시작일부터 다음 달 전날까지`() {
        assertEquals(
            cycle(date(11, 5), date(12, 4)),
            PerformanceCycle.current(PeriodUnit.MONTH, 5, date(11, 9)),
        )
        assertEquals(
            cycle(date(10, 5), date(11, 4)),
            PerformanceCycle.previous(PeriodUnit.MONTH, 5, date(11, 9)),
        )
    }

    @Test
    fun `시작일 전이면 지난달 시작일부터 센다`() {
        assertEquals(
            cycle(date(10, 15), date(11, 14)),
            PerformanceCycle.current(PeriodUnit.MONTH, 15, date(11, 9)),
        )
    }

    @Test
    fun `시작일 당일은 새 주기의 첫날이고 전날은 지난 주기의 끝날이다`() {
        val current = PerformanceCycle.current(PeriodUnit.MONTH, 15, date(11, 15))

        assertEquals(date(11, 15), current.start)
        assertEquals(date(11, 14), PerformanceCycle.previous(PeriodUnit.MONTH, 15, date(11, 15)).end)
    }

    @Test
    fun `그 날이 없는 달은 마지막 날부터 센다`() {
        // 31일 시작. 11월은 30일까지라 11월 30일에 시작해 12월 30일에 끝난다.
        assertEquals(
            cycle(date(11, 30), date(12, 30)),
            PerformanceCycle.current(PeriodUnit.MONTH, 31, date(12, 1)),
        )
        // 2월은 28일까지. 1월 31일 ~ 2월 27일, 2월 28일 ~ 3월 30일.
        assertEquals(
            cycle(date(1, 31), date(2, 27)),
            PerformanceCycle.current(PeriodUnit.MONTH, 31, date(2, 10)),
        )
        assertEquals(
            cycle(date(2, 28), date(3, 30)),
            PerformanceCycle.current(PeriodUnit.MONTH, 31, date(3, 1)),
        )
    }

    @Test
    fun `해를 넘기는 주기`() {
        assertEquals(
            cycle(date(12, 20, 2025), date(1, 19)),
            PerformanceCycle.current(PeriodUnit.MONTH, 20, date(1, 5)),
        )
        assertEquals(
            cycle(date(11, 20, 2025), date(12, 19, 2025)),
            PerformanceCycle.previous(PeriodUnit.MONTH, 20, date(1, 5)),
        )
    }

    @Test
    fun `매년 실적은 시작일과 상관없이 1월 1일부터다`() {
        assertEquals(
            cycle(date(1, 1), date(12, 31)),
            PerformanceCycle.current(PeriodUnit.YEAR, 15, date(11, 9)),
        )
        assertEquals(
            cycle(date(1, 1, 2025), date(12, 31, 2025)),
            PerformanceCycle.previous(PeriodUnit.YEAR, 15, date(11, 9)),
        )
    }

    @Test
    fun `양 끝 날을 모두 넣는다`() {
        val c = cycle(date(10, 5), date(11, 4))

        assertTrue(date(10, 5) in c)
        assertTrue(date(11, 4) in c)
        assertFalse(date(10, 4) in c)
        assertFalse(date(11, 5) in c)
    }
}
