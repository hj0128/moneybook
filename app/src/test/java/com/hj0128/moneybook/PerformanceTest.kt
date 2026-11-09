package com.hj0128.moneybook

import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.model.PerformanceDay
import com.hj0128.moneybook.data.model.PeriodUnit
import com.hj0128.moneybook.data.repository.AccountPerformance
import com.hj0128.moneybook.data.repository.withPerformance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PerformanceTest {

    private val today = LocalDate.of(2026, 11, 9)

    private fun card(
        target: Long? = 300_000,
        period: PeriodUnit? = PeriodUnit.MONTH,
        steps: String? = null,
        startDay: Int? = null,
        used: Long = 0,
        prev: Long = 0,
    ) = AccountBalance(
        id = 1,
        name = "국민",
        type = AccountType.values().first(),
        colorArgb = 0,
        balance = 0,
        performanceTarget = target,
        performancePeriod = period,
        performanceSteps = steps,
        performanceStartDay = startDay,
        performanceUsed = used,
        performancePrev = prev,
    )

    private fun day(month: Int, day: Int, amount: Long) =
        PerformanceDay(accountId = 1, epochDay = LocalDate.of(2026, month, day).toEpochDay(), amount = amount)

    // ---- 주기별로 묶기 ----

    @Test
    fun `이번 달과 지난달을 따로 더한다`() {
        val result = card().withPerformance(
            listOf(day(11, 1, 20_000), day(11, 8, 3_000), day(10, 31, 50_000), day(9, 30, 99_000)),
            today,
        )

        assertEquals(23_000L, result.performanceUsed)
        assertEquals(50_000L, result.performancePrev)
    }

    @Test
    fun `시작일을 정하면 그 주기로 묶는다`() {
        // 5일 시작: 이번 주기 11.5 ~ 12.4, 지난 주기 10.5 ~ 11.4.
        val result = card(startDay = 5).withPerformance(
            listOf(day(11, 1, 20_000), day(11, 5, 3_000), day(10, 4, 50_000)),
            today,
        )

        assertEquals(3_000L, result.performanceUsed)
        assertEquals(20_000L, result.performancePrev)
    }

    @Test
    fun `환불이 더 많아도 0 아래로 내려가지 않는다`() {
        val result = card().withPerformance(listOf(day(11, 2, 10_000), day(11, 3, -40_000)), today)

        assertEquals(0L, result.performanceUsed)
    }

    @Test
    fun `실적 목표가 없으면 건드리지 않는다`() {
        val plain = card(target = null, period = null)

        assertEquals(plain, plain.withPerformance(listOf(day(11, 2, 10_000)), today))
    }

    // ---- 단계 ----

    @Test
    fun `단계는 목표보다 큰 것만 작은 순으로`() {
        val tiered = card(steps = "1000000, 700000,abc,200000")

        assertEquals(listOf(300_000L, 700_000L, 1_000_000L), tiered.performanceTiers)
        assertTrue(tiered.isTiered)
        assertFalse(card().isTiered)
    }

    @Test
    fun `다음 단계까지 남은 금액`() {
        val halfway = card(steps = "700000", used = 450_000)

        assertEquals(1, halfway.tiersReached(halfway.performanceUsed))
        assertEquals(700_000L, halfway.performanceNextTarget)
        assertEquals(250_000L, halfway.performanceRemaining)
        assertFalse(halfway.performanceAchieved)
    }

    @Test
    fun `모든 단계를 채우면 달성`() {
        val done = card(steps = "700000", used = 700_000)

        assertNull(done.performanceNextTarget)
        assertEquals(0L, done.performanceRemaining)
        assertTrue(done.performanceAchieved)
    }

    @Test
    fun `지난달은 1단계만 채워도 혜택을 받는다`() {
        assertTrue(card(steps = "700000", prev = 300_000).performancePrevAchieved)
        assertFalse(card(steps = "700000", prev = 299_999).performancePrevAchieved)
    }

    @Test
    fun `시작일 표시는 매월이고 1일이 아닐 때만`() {
        assertEquals(15, card(startDay = 15).performanceCustomStartDay)
        assertNull(card(startDay = 1).performanceCustomStartDay)
        assertNull(card(startDay = 15, period = PeriodUnit.YEAR).performanceCustomStartDay)
    }

    // ---- 저장할 값 ----

    @Test
    fun `적은 순서와 상관없이 작은 금액이 1단계`() {
        val performance = AccountPerformance.of(listOf(700_000, 300_000), PeriodUnit.MONTH, emptySet())

        assertEquals(300_000L, performance.target)
        assertEquals(listOf(700_000L), performance.steps)
        assertEquals("700000", performance.stepsText)
        assertNull(performance.validate())
    }

    @Test
    fun `같은 금액 단계와 범위 밖 시작일은 막는다`() {
        assertNotNull(AccountPerformance.of(listOf(300_000, 300_000), PeriodUnit.MONTH, emptySet()).validate())
        assertNotNull(AccountPerformance(target = 300_000, period = PeriodUnit.MONTH, startDay = 0).validate())
        assertNotNull(AccountPerformance(target = 300_000, period = PeriodUnit.MONTH, startDay = 32).validate())
        assertNotNull(AccountPerformance(target = 0, period = PeriodUnit.MONTH).validate())
    }

    @Test
    fun `1일이나 매년이면 시작일을 저장하지 않는다`() {
        assertNull(AccountPerformance(target = 1, period = PeriodUnit.MONTH, startDay = 1).storedStartDay)
        assertNull(AccountPerformance(target = 1, period = PeriodUnit.YEAR, startDay = 15).storedStartDay)
        assertEquals(15, AccountPerformance(target = 1, period = PeriodUnit.MONTH, startDay = 15).storedStartDay)
    }
}
