package com.hj0128.moneybook.data.repository

import com.hj0128.moneybook.data.local.dao.AccountDao
import com.hj0128.moneybook.data.local.dao.CategoryDao
import com.hj0128.moneybook.data.local.dao.TransactionDao
import com.hj0128.moneybook.data.local.entity.AccountEntity
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.local.entity.TransactionEntity
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.MonthTotal
import com.hj0128.moneybook.data.model.toNodes
import com.hj0128.moneybook.data.model.MonthlySummary
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.UncategorizedTotal
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

    /** 카테고리 상세: 한 카테고리의 그 달 내역. */
    fun observeCategoryTransactions(
        yearMonth: YearMonth,
        categoryId: Long,
    ): Flow<List<TransactionDetail>> {
        val start = yearMonth.atDay(1).toEpochDay()
        val end = yearMonth.atEndOfMonth().toEpochDay()
        return transactionDao.observeByCategory(categoryId, start, end)
    }

    /** 카테고리 상세의 소분류별 소계. 소분류가 없으면 한 줄만 나온다. */
    fun observeChildTotals(
        yearMonth: YearMonth,
        categoryId: Long,
    ): Flow<List<CategoryTotal>> {
        val start = yearMonth.atDay(1).toEpochDay()
        val end = yearMonth.atEndOfMonth().toEpochDay()
        return transactionDao.observeChildTotals(categoryId, start, end)
    }

    fun observeUncategorizedTotal(
        yearMonth: YearMonth,
        type: TransactionType,
    ): Flow<UncategorizedTotal> {
        val start = yearMonth.atDay(1).toEpochDay()
        val end = yearMonth.atEndOfMonth().toEpochDay()
        return transactionDao.observeUncategorizedTotal(type, start, end)
    }

    fun observeUncategorized(
        yearMonth: YearMonth,
        type: TransactionType,
    ): Flow<List<TransactionDetail>> {
        val start = yearMonth.atDay(1).toEpochDay()
        val end = yearMonth.atEndOfMonth().toEpochDay()
        return transactionDao.observeUncategorized(type, start, end)
    }

    /** 고른 내역을 한 카테고리로 옮긴다. */
    suspend fun assignCategory(transactionIds: List<Long>, categoryId: Long) {
        if (transactionIds.isEmpty()) return
        transactionDao.assignCategory(transactionIds, categoryId)
    }

    fun observeCategory(id: Long): Flow<CategoryEntity?> = categoryDao.observeById(id)

    fun observeCategories(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(type)

    /** 대분류 > 소분류로 묶은 카테고리. 관리 화면과 입력 화면이 같이 쓴다. */
    fun observeCategoryTree(type: TransactionType): Flow<List<CategoryNode>> =
        categoryDao.observeByType(type).map { it.toNodes() }

    fun observeAccounts(): Flow<List<AccountBalance>> = accountDao.observeBalances()

    // ---- 카테고리 관리 ----

    /** 이 카테고리에 직접 붙은 거래 수와 소분류 수. 삭제 전 이전이 필요한지 판단한다. */
    suspend fun categoryUsage(categoryId: Long): CategoryUsage = CategoryUsage(
        directTransactions = categoryDao.countDirectUsage(categoryId),
        children = categoryDao.countChildren(categoryId),
    )

    /**
     * 카테고리 추가. 같은 부모 밑에 같은 이름이 있으면 거부한다.
     *
     * 이름이 같은 카테고리가 둘이면 목록에서도 통계에서도 어느 쪽인지 알 수 없다.
     */
    suspend fun addCategory(
        name: String,
        type: TransactionType,
        colorArgb: Int,
        parentId: Long?,
    ): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("이름을 입력해 주세요."))
        if (categoryDao.countSameName(type, trimmed, parentId, excludeId = 0L) > 0) {
            return Result.failure(IllegalArgumentException("같은 이름이 이미 있습니다."))
        }
        val id = categoryDao.insert(
            CategoryEntity(
                name = trimmed,
                type = type,
                colorArgb = colorArgb,
                sortOrder = categoryDao.nextSortOrder(type, parentId),
                parentId = parentId,
            )
        )
        return Result.success(id)
    }

    /** 이름과 색만 고친다. 부모와 종류는 바꾸지 않는다(바꾸면 과거 집계가 통째로 달라진다). */
    suspend fun renameCategory(id: Long, name: String, colorArgb: Int): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("이름을 입력해 주세요."))
        val current = categoryDao.findById(id)
            ?: return Result.failure(IllegalStateException("카테고리를 찾을 수 없습니다."))
        if (categoryDao.countSameName(current.type, trimmed, current.parentId, excludeId = id) > 0) {
            return Result.failure(IllegalArgumentException("같은 이름이 이미 있습니다."))
        }
        categoryDao.update(current.copy(name = trimmed, colorArgb = colorArgb))
        return Result.success(Unit)
    }

    /**
     * 카테고리 삭제.
     *
     * [reassignToId] 가 있으면 이 카테고리에 직접 붙은 거래를 그쪽으로 옮기고,
     * 소분류가 있으면 소분류째로 그 밑에 붙인다. 쓰인 적도 없고 소분류도 없으면 null 로 부른다.
     */
    suspend fun deleteCategory(id: Long, reassignToId: Long?) {
        categoryDao.deleteWithReassign(id, reassignToId)
    }

    suspend fun addTransaction(
        amount: Long,
        type: TransactionType,
        categoryId: Long?,
        accountId: Long,
        toAccountId: Long?,
        date: LocalDate,
        merchant: String,
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
                merchant = merchant.trim(),
                memo = memo.trim(),
                createdAt = nowMillis,
            )
        )
    }

    suspend fun findTransaction(id: Long): TransactionEntity? = transactionDao.findById(id)

    /**
     * 내역 수정.
     *
     * 종류(지출/수입/이체)까지 바꿀 수 있다. 잘못 넣은 것을 고치는 게 목적이라
     * 종류만 못 바꾸게 하면 지우고 다시 넣어야 한다.
     */
    suspend fun updateTransaction(
        id: Long,
        amount: Long,
        type: TransactionType,
        categoryId: Long?,
        accountId: Long,
        toAccountId: Long?,
        date: LocalDate,
        merchant: String,
        memo: String,
    ): Result<Unit> {
        if (amount <= 0) return Result.failure(IllegalArgumentException("금액은 0보다 커야 합니다."))
        if (type == TransactionType.TRANSFER) {
            if (toAccountId == null) {
                return Result.failure(IllegalArgumentException("이체는 입금 자산이 필요합니다."))
            }
            if (toAccountId == accountId) {
                return Result.failure(IllegalArgumentException("출금 자산과 입금 자산이 같습니다."))
            }
        }
        val current = transactionDao.findById(id)
            ?: return Result.failure(IllegalStateException("내역을 찾을 수 없습니다."))
        transactionDao.update(
            current.copy(
                amount = amount,
                type = type,
                // 이체는 카테고리를 쓰지 않는다.
                categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                accountId = accountId,
                toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
                dateEpochDay = date.toEpochDay(),
                merchant = merchant.trim(),
                memo = memo.trim(),
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)

    // ---- 자산 관리 ----

    suspend fun accountUsage(accountId: Long): Int = accountDao.countUsage(accountId)

    /** 이전 대상을 골랐을 때 뜻이 사라지는 이체 수. 대상이 바뀌면 다시 세야 한다. */
    suspend fun selfTransfersAfterMove(fromId: Long, toId: Long): Int =
        accountDao.countSelfTransfersAfterMove(fromId, toId)

    suspend fun addAccount(
        name: String,
        type: AccountType,
        colorArgb: Int,
        initialBalance: Long,
    ): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("이름을 입력해 주세요."))
        if (accountDao.countSameName(trimmed, excludeId = 0L) > 0) {
            return Result.failure(IllegalArgumentException("같은 이름이 이미 있습니다."))
        }
        val id = accountDao.insert(
            AccountEntity(
                name = trimmed,
                type = type,
                colorArgb = colorArgb,
                initialBalance = initialBalance,
                sortOrder = accountDao.nextSortOrder(),
            )
        )
        return Result.success(id)
    }

    suspend fun updateAccount(
        id: Long,
        name: String,
        type: AccountType,
        colorArgb: Int,
        initialBalance: Long,
    ): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("이름을 입력해 주세요."))
        val current = accountDao.findById(id)
            ?: return Result.failure(IllegalStateException("자산을 찾을 수 없습니다."))
        if (accountDao.countSameName(trimmed, excludeId = id) > 0) {
            return Result.failure(IllegalArgumentException("같은 이름이 이미 있습니다."))
        }
        accountDao.update(
            current.copy(
                name = trimmed,
                type = type,
                colorArgb = colorArgb,
                initialBalance = initialBalance,
            )
        )
        return Result.success(Unit)
    }

    /**
     * 자산 삭제.
     *
     * [toId] 가 있으면 거래를 그쪽으로 옮긴 뒤 지운다. 쓰인 적이 없으면 옮길 것이
     * 없으므로 null 로 부른다. 마지막 자산은 지울 수 없다(거래가 갈 곳이 없다).
     */
    suspend fun deleteAccount(id: Long, toId: Long?): Result<Unit> {
        if (accountDao.count() <= 1) {
            return Result.failure(IllegalStateException("자산이 하나뿐이라 지울 수 없습니다."))
        }
        if (toId == null) accountDao.deleteById(id) else accountDao.deleteWithReassign(id, toId)
        return Result.success(Unit)
    }
}

/** 삭제 다이얼로그가 무엇을 물어봐야 할지 정하는 근거. */
data class CategoryUsage(
    val directTransactions: Int,
    val children: Int,
) {
    /** 옮길 것이 없으면 이전 대상을 묻지 않고 바로 지운다. */
    val needsReassign: Boolean get() = directTransactions > 0 || children > 0
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
