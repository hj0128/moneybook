package com.hj0128.moneybook.data.repository

import com.hj0128.moneybook.data.local.dao.AccountDao
import com.hj0128.moneybook.data.local.dao.CategoryDao
import com.hj0128.moneybook.data.local.dao.TransactionDao
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.local.entity.TransactionEntity
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.MonthTotal
import com.hj0128.moneybook.data.model.MonthlySummary
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/** UI 는 DAO 를 직접 보지 않고 이 클래스만 본다. 나중에 백업/동기화가 붙어도 UI 는 그대로다. */
class LedgerRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
) {

    fun observeMonth(yearMonth: YearMonth): Flow<List<TransactionDetail>> {
        val start = yearMonth.atDay(1).toEpochDay()
        val end = yearMonth.atEndOfMonth().toEpochDay()
        return transactionDao.observeBetween(start, end)
    }

    fun observeMonthSummary(yearMonth: YearMonth): Flow<MonthlySummary> =
        observeMonth(yearMonth).map { transactions -> transactions.toSummary() }

    /** 한 해의 12개월 합계. 거래가 없는 달은 결과에 없으므로 화면에서 0으로 채운다. */
    fun observeYear(year: Int): Flow<List<MonthTotal>> {
        val start = LocalDate.of(year, 1, 1).toEpochDay()
        val end = LocalDate.of(year, 12, 31).toEpochDay()
        return transactionDao.observeMonthlyTotals(start, end)
    }

    fun observeCategoryTotals(
        yearMonth: YearMonth,
        type: TransactionType,
    ): Flow<List<CategoryTotal>> {
        val start = yearMonth.atDay(1).toEpochDay()
        val end = yearMonth.atEndOfMonth().toEpochDay()
        return transactionDao.observeCategoryTotals(type, start, end)
    }

    fun observeCategories(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(type)

    fun observeAccounts(): Flow<List<AccountBalance>> = accountDao.observeBalances()

    suspend fun addTransaction(
        amount: Long,
        type: TransactionType,
        categoryId: Long?,
        accountId: Long,
        toAccountId: Long?,
        date: LocalDate,
        memo: String,
        nowMillis: Long,
    ): Long {
        require(amount > 0) { "금액은 0보다 커야 합니다." }
        if (type == TransactionType.TRANSFER) {
            requireNotNull(toAccountId) { "이체는 입금 자산이 필요합니다." }
            require(toAccountId != accountId) { "출금 자산과 입금 자산이 같습니다." }
        }
        return transactionDao.insert(
            TransactionEntity(
                amount = amount,
                type = type,
                // 이체는 카테고리를 쓰지 않는다.
                categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                accountId = accountId,
                toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
                dateEpochDay = date.toEpochDay(),
                memo = memo.trim(),
                createdAt = nowMillis,
            )
        )
    }

    suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)
}

/** 이체는 자산 간 이동이라 수입/지출 어느 쪽에도 더하지 않는다. */
internal fun List<TransactionDetail>.toSummary(): MonthlySummary {
    var income = 0L
    var expense = 0L
    for (item in this) {
        when (item.type) {
            TransactionType.INCOME -> income += item.amount
            TransactionType.EXPENSE -> expense += item.amount
            TransactionType.TRANSFER -> Unit
        }
    }
    return MonthlySummary(income = income, expense = expense)
}
