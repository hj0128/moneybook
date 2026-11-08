package com.hj0128.moneybook.data.repository

import com.hj0128.moneybook.data.local.dao.AccountDao
import com.hj0128.moneybook.data.local.dao.BudgetDao
import com.hj0128.moneybook.data.local.dao.CategoryDao
import com.hj0128.moneybook.data.local.dao.RecurringDao
import com.hj0128.moneybook.data.local.dao.TransactionDao
import com.hj0128.moneybook.data.local.entity.AccountEntity
import com.hj0128.moneybook.data.local.entity.BudgetEntity
import com.hj0128.moneybook.data.local.entity.amountsFor
import com.hj0128.moneybook.data.local.entity.toKey
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.local.entity.RecurringEntity
import com.hj0128.moneybook.data.local.entity.TransactionEntity
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountNode
import com.hj0128.moneybook.data.model.AccountType
import com.hj0128.moneybook.data.model.BudgetRow
import com.hj0128.moneybook.data.model.BudgetSummary
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.LedgerPeriod
import com.hj0128.moneybook.data.model.PeriodUnit
import com.hj0128.moneybook.data.model.MonthTotal
import com.hj0128.moneybook.data.model.toNodes
import com.hj0128.moneybook.data.model.MonthlySummary
import com.hj0128.moneybook.data.model.RecurringDetail
import com.hj0128.moneybook.data.model.RepeatUnit
import com.hj0128.moneybook.data.model.SearchCandidate
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.toAccountNodes
import com.hj0128.moneybook.data.model.UncategorizedTotal
import com.hj0128.moneybook.util.formatFull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/** UI 는 DAO 를 직접 보지 않고 이 클래스만 본다. 나중에 백업/동기화가 붙어도 UI 는 그대로다. */
class LedgerRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val recurringDao: RecurringDao,
    private val budgetDao: BudgetDao,
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

    // 카테고리 집계는 월/년 어느 쪽이든 같은 쿼리를 쓴다. 기간 계산은 LedgerPeriod 가 맡는다.

    fun observeCategoryTotals(
        period: LedgerPeriod,
        type: TransactionType,
    ): Flow<List<CategoryTotal>> =
        transactionDao.observeCategoryTotals(type, period.start.toEpochDay(), period.end.toEpochDay())

    /** 카테고리 상세: 한 카테고리의 그 기간 내역. */
    fun observeCategoryTransactions(
        period: LedgerPeriod,
        categoryId: Long,
    ): Flow<List<TransactionDetail>> =
        transactionDao.observeByCategory(categoryId, period.start.toEpochDay(), period.end.toEpochDay())

    /** 카테고리 상세의 소분류별 소계. 소분류가 없으면 한 줄만 나온다. */
    fun observeChildTotals(
        period: LedgerPeriod,
        categoryId: Long,
    ): Flow<List<CategoryTotal>> =
        transactionDao.observeChildTotals(categoryId, period.start.toEpochDay(), period.end.toEpochDay())

    fun observeUncategorizedTotal(
        period: LedgerPeriod,
        type: TransactionType,
    ): Flow<UncategorizedTotal> =
        transactionDao.observeUncategorizedTotal(type, period.start.toEpochDay(), period.end.toEpochDay())

    fun observeUncategorized(
        period: LedgerPeriod,
        type: TransactionType,
    ): Flow<List<TransactionDetail>> =
        transactionDao.observeUncategorized(type, period.start.toEpochDay(), period.end.toEpochDay())

    /** 고른 내역을 한 카테고리로 옮긴다. */
    suspend fun assignCategory(transactionIds: List<Long>, categoryId: Long) {
        if (transactionIds.isEmpty()) return
        transactionDao.assignCategory(transactionIds, categoryId)
    }

    /** 고른 내역을 한 자산으로 옮긴다. 이체는 그대로 둔다. */
    suspend fun assignAccount(transactionIds: List<Long>, accountId: Long) {
        if (transactionIds.isEmpty()) return
        transactionDao.assignAccount(transactionIds, accountId)
    }

    fun observeCategory(id: Long): Flow<CategoryEntity?> = categoryDao.observeById(id)

    fun observeCategories(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.observeByType(type)

    /** 대분류 > 소분류로 묶은 카테고리. 관리 화면과 입력 화면이 같이 쓴다. */
    fun observeCategoryTree(type: TransactionType): Flow<List<CategoryNode>> =
        categoryDao.observeByType(type).map { it.toNodes() }

    /**
     * 자산 목록. 한도 사용액과 실적은 [today] 가 속한 달/해 기준이고, 지난 실적은 그 바로 전 달/해다.
     *
     * 화면이 떠 있는 동안 날짜가 바뀌어도 다시 구독하기 전까지는 이전 기간 기준이다.
     * 가계부를 자정을 넘겨 켜 두는 일은 드물어서 그 정도는 감수한다.
     */
    fun observeAccounts(today: LocalDate = LocalDate.now()): Flow<List<AccountBalance>> {
        val month = YearMonth.from(today)
        val prevMonth = month.minusMonths(1)
        return accountDao.observeBalances(
            monthStart = month.atDay(1).toEpochDay(),
            monthEnd = month.atEndOfMonth().toEpochDay(),
            yearStart = LocalDate.of(today.year, 1, 1).toEpochDay(),
            yearEnd = LocalDate.of(today.year, 12, 31).toEpochDay(),
            prevMonthStart = prevMonth.atDay(1).toEpochDay(),
            prevMonthEnd = prevMonth.atEndOfMonth().toEpochDay(),
            prevYearStart = LocalDate.of(today.year - 1, 1, 1).toEpochDay(),
            prevYearEnd = LocalDate.of(today.year - 1, 12, 31).toEpochDay(),
        )
    }

    fun observeAccount(id: Long): Flow<AccountBalance?> =
        observeAccounts().map { accounts -> accounts.firstOrNull { it.id == id } }

    fun observeAccountTotals(
        period: LedgerPeriod,
        type: TransactionType,
    ): Flow<List<CategoryTotal>> =
        transactionDao.observeAccountTotals(type, period.start.toEpochDay(), period.end.toEpochDay())

    fun observeAccountTransactions(
        period: LedgerPeriod,
        accountId: Long,
        type: TransactionType,
    ): Flow<List<TransactionDetail>> =
        transactionDao.observeByAccount(accountId, type, period.start.toEpochDay(), period.end.toEpochDay())

    fun observeAccountCategoryTotals(
        period: LedgerPeriod,
        accountId: Long,
        type: TransactionType,
    ): Flow<List<CategoryTotal>> = transactionDao.observeAccountCategoryTotals(
        accountId, type, period.start.toEpochDay(), period.end.toEpochDay(),
    )

    /** 상위 자산 상세의 하위 자산별 소계. 상위에 직접 붙은 내역도 한 줄로 들어 있다. */
    fun observeAccountChildTotals(
        period: LedgerPeriod,
        accountId: Long,
        type: TransactionType,
    ): Flow<List<CategoryTotal>> = transactionDao.observeAccountChildTotals(
        accountId, type, period.start.toEpochDay(), period.end.toEpochDay(),
    )

    /** 상위 > 하위로 묶은 자산. 관리 화면과 입력 화면이 같이 쓴다(카테고리 트리와 같은 역할). */
    fun observeAccountTree(): Flow<List<AccountNode>> = observeAccounts().map { it.toAccountNodes() }

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

    /**
     * 카테고리 순서 저장. [orderedIds] 는 한 목록(같은 부모 밑) 전체가 화면에 보이는 순서다.
     *
     * 목록 하나만 다시 매기므로 다른 대분류의 소분류 순서는 건드리지 않는다.
     */
    suspend fun reorderCategories(orderedIds: List<Long>) {
        categoryDao.applyOrder(orderedIds)
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

    // ---- 예산 ----

    /**
     * 예산 화면: 대분류 지출 카테고리 전부와 그 달 예산, 그 달에 쓴 돈. 예산이 없는 카테고리도 넣는다(거기서 정하므로).
     *
     * 쓴 돈은 카테고리별 탭과 같은 쿼리로 센다(소분류를 대분류로 합친 것). 두 화면 숫자가 어긋나면 안 된다.
     */
    fun observeBudget(month: YearMonth): Flow<BudgetSummary> {
        val period = LedgerPeriod.Month(month)
        return combine(
            categoryDao.observeByType(TransactionType.EXPENSE),
            observeCategoryTotals(period, TransactionType.EXPENSE),
            observeBudgetAmounts(month),
        ) { categories, totals, budgets ->
            val spentById = totals.associate { it.categoryId to it.total }
            BudgetSummary(
                month = month,
                rows = categories.filter { it.isTopLevel }.map {
                    BudgetRow(category = it, budget = budgets[it.id], spent = spentById[it.id] ?: 0L)
                },
            )
        }
    }

    /** 그 달에 적용되는 카테고리별 예산(카테고리 id -> 금액). 정하지 않은 달은 그 전에 정한 금액이다. */
    fun observeBudgetAmounts(month: YearMonth): Flow<Map<Long, Long>> =
        budgetDao.observeAll().map { it.amountsFor(month) }

    /**
     * [month] 부터의 예산을 정한다. 0 이하나 null 이면 그 달부터 예산을 없앤다.
     *
     * 그 뒤 달 중 따로 정하지 않은 달은 이 금액을 이어받는다. 이미 따로 정한 달은 그대로다.
     */
    suspend fun setCategoryBudget(categoryId: Long, month: YearMonth, amount: Long?) {
        budgetDao.upsert(BudgetEntity(categoryId, month.toKey(), amount?.takeIf { it > 0L }))
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
        excludedFromPerformance: Boolean = false,
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
                // 실적은 쓴 돈의 이야기라 지출만 뺄 수 있다.
                excludedFromPerformance = excludedFromPerformance && type.affectsPerformance,
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
        excludedFromPerformance: Boolean = false,
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
                excludedFromPerformance = excludedFromPerformance && type.affectsPerformance,
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)

    /** 결제 단위 삭제. 일별 탭처럼 나눈 결제를 한 줄로 보여주는 곳에서 쓴다. */
    suspend fun deletePayment(id: Long) = transactionDao.deletePayment(id)

    /** 고칠 결제의 줄들. 나누지 않은 내역이면 그 한 줄. */
    suspend fun findPaymentParts(id: Long): List<TransactionEntity> {
        val entity = transactionDao.findById(id) ?: return emptyList()
        val groupId = entity.splitGroupId ?: return listOf(entity)
        return transactionDao.findGroup(groupId)
    }

    /**
     * 나눠서 입력한 결제를 저장한다. [replacingId] 가 있으면 그 결제(묶음 전체)를 바꾼다.
     *
     * 날짜·거래처·종류는 결제 하나에 하나라 모든 줄이 같은 값을 갖는다. 메모는 줄마다 다르다
     * (식비 줄에는 "라면", 생활용품 줄에는 "면봉").
     * 줄이 하나뿐이면 나누지 않은 평범한 내역으로 저장된다.
     */
    suspend fun savePayment(
        replacingId: Long?,
        parts: List<PaymentPart>,
        type: TransactionType,
        date: LocalDate,
        merchant: String,
        nowMillis: Long,
    ): Result<Unit> {
        if (parts.isEmpty()) return Result.failure(IllegalArgumentException("나눈 줄이 없습니다."))
        if (type == TransactionType.TRANSFER) {
            return Result.failure(IllegalArgumentException("이체는 나눌 수 없습니다."))
        }
        if (parts.any { it.amount <= 0 }) {
            return Result.failure(IllegalArgumentException("나눈 금액은 모두 0보다 커야 합니다."))
        }
        // 고치는 중이면 처음 넣은 시각을 지킨다. 바꿀 때마다 같은 날 안에서 맨 위로 올라가면 어색하다.
        val original = replacingId?.let { transactionDao.findById(it) }
        val createdAt = original?.createdAt ?: nowMillis
        val rows = parts.map { part ->
            TransactionEntity(
                amount = part.amount,
                type = type,
                categoryId = part.categoryId,
                accountId = part.accountId,
                dateEpochDay = date.toEpochDay(),
                merchant = merchant.trim(),
                memo = part.memo.trim(),
                createdAt = createdAt,
                // 고정 지출로 들어온 내역을 나눠도 어디서 왔는지는 남긴다. 지워지면 그 달이 아직 안 들어간 것처럼 보인다.
                recurringId = original?.recurringId,
                // 줄마다 따로 뺀다. 줄마다 카드가 다를 수 있고, 같은 카드여도 상품권 줄만 빼고 싶을 때가 있다.
                excludedFromPerformance = part.excludedFromPerformance && type.affectsPerformance,
            )
        }
        transactionDao.replacePayment(replacingId, rows)
        return Result.success(Unit)
    }

    /** 검색 대상 전체. 걸러내기는 검색 화면이 한다. */
    fun observeSearchCandidates(): Flow<List<SearchCandidate>> = transactionDao.observeSearchCandidates()

    // ---- 고정 지출 ----

    fun observeRecurring(): Flow<List<RecurringDetail>> = recurringDao.observeAll()

    suspend fun findRecurring(id: Long): RecurringEntity? = recurringDao.findById(id)

    /**
     * 차례가 온 고정 지출을 내역으로 넣는다. 앱이 앞으로 나올 때마다 부른다.
     * 돌려주는 값은 넣은 줄 수.
     */
    suspend fun applyRecurring(today: LocalDate = LocalDate.now()): Int =
        recurringDao.applyDue(today, System.currentTimeMillis())

    /**
     * 고정 지출 저장. [id] 가 있으면 그것을 고친다.
     *
     * [firstDate] 가 다음에 넣을 날이 되고, [anchor] 가 주기를 정한다(매주 그 요일, 매월 그 일, 매년 그 월·일).
     * 보통은 둘이 같은 날이다. 31일짜리의 다음 날짜가 30일인 경우처럼 고칠 때만 다를 수 있다.
     * 오늘이거나 지난 날이면 저장하자마자 그날 것(밀린 것까지)이 들어간다. 앞날이면 그날이 와야 들어간다.
     * 고쳐도 이미 들어간 내역은 그대로다. 지난달 통신비가 바뀐 요금으로 다시 쓰이면 기록이 틀어진다.
     */
    suspend fun saveRecurring(
        id: Long?,
        amount: Long,
        type: TransactionType,
        categoryId: Long?,
        accountId: Long,
        toAccountId: Long?,
        repeatUnit: RepeatUnit,
        firstDate: LocalDate,
        anchor: LocalDate,
        merchant: String,
        memo: String,
        excludedFromPerformance: Boolean = false,
    ): Result<Unit> {
        if (amount <= 0) return Result.failure(IllegalArgumentException("금액은 0보다 커야 합니다."))
        val transfer = type == TransactionType.TRANSFER
        if (transfer && (toAccountId == null || toAccountId == accountId)) {
            return Result.failure(IllegalArgumentException("출금 자산과 입금 자산을 확인해 주세요."))
        }
        // 이미 들어간 칸(그 달, 그 해)으로 되돌리면 그 칸이 한 번 더 들어간다.
        // 다음 날짜는 마지막으로 넣은 칸 뒤여야 한다.
        val lastEntered = id?.let { recurringDao.lastEnteredEpochDay(it) }?.let(LocalDate::ofEpochDay)
        if (lastEntered != null && repeatUnit.periodStart(firstDate) <= repeatUnit.periodStart(lastEntered)) {
            val earliest = repeatUnit.nextPeriodStart(lastEntered)
            return Result.failure(
                IllegalArgumentException(
                    "${lastEntered.formatFull()} 것까지 이미 들어갔습니다. 다음 날짜를 ${earliest.formatFull()}부터 골라 주세요."
                )
            )
        }
        val now = System.currentTimeMillis()
        val rule = RecurringEntity(
            id = id ?: 0L,
            amount = amount,
            type = type,
            categoryId = if (transfer) null else categoryId,
            accountId = accountId,
            toAccountId = if (transfer) toAccountId else null,
            merchant = merchant.trim(),
            memo = memo.trim(),
            repeatUnit = repeatUnit,
            anchorEpochDay = anchor.toEpochDay(),
            nextEpochDay = firstDate.toEpochDay(),
            createdAt = id?.let { recurringDao.findById(it)?.createdAt } ?: now,
            excludedFromPerformance = excludedFromPerformance && type.affectsPerformance,
        )
        if (id == null) recurringDao.insert(rule) else recurringDao.update(rule)
        applyRecurring()
        return Result.success(Unit)
    }

    suspend fun deleteRecurring(id: Long) = recurringDao.deleteById(id)

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
        limit: AccountLimit? = null,
        parentId: Long? = null,
        performance: AccountPerformance? = null,
    ): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("이름을 입력해 주세요."))
        limit?.validate()?.let { return Result.failure(it) }
        performance?.validate()?.let { return Result.failure(it) }
        validateParent(id = null, parentId = parentId)?.let { return Result.failure(it) }
        if (accountDao.countSameName(trimmed, excludeId = 0L) > 0) {
            return Result.failure(IllegalArgumentException("같은 이름이 이미 있습니다."))
        }
        val id = accountDao.insert(
            AccountEntity(
                name = trimmed,
                type = typeUnder(parentId, type),
                colorArgb = colorArgb,
                initialBalance = initialBalance,
                sortOrder = accountDao.nextSortOrder(parentId),
                limitAmount = limit?.amount,
                limitPeriod = limit?.period,
                parentId = parentId,
                performanceTarget = performance?.target,
                performancePeriod = performance?.period,
                performanceSteps = performance?.stepsText,
            )
        )
        accountDao.replaceExclusions(id, performance?.excludedCategoryIds.orEmpty())
        return Result.success(id)
    }

    /** 자산 순서 저장. 지금 보고 있는 목록(상위끼리, 또는 한 상위의 하위끼리)만 넘어온다. */
    suspend fun reorderAccounts(orderedIds: List<Long>) {
        accountDao.applyOrder(orderedIds)
    }

    suspend fun updateAccount(
        id: Long,
        name: String,
        type: AccountType,
        colorArgb: Int,
        initialBalance: Long,
        limit: AccountLimit? = null,
        parentId: Long? = null,
        performance: AccountPerformance? = null,
    ): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("이름을 입력해 주세요."))
        limit?.validate()?.let { return Result.failure(it) }
        performance?.validate()?.let { return Result.failure(it) }
        val current = accountDao.findById(id)
            ?: return Result.failure(IllegalStateException("자산을 찾을 수 없습니다."))
        validateParent(id = id, parentId = parentId)?.let { return Result.failure(it) }
        if (accountDao.countSameName(trimmed, excludeId = id) > 0) {
            return Result.failure(IllegalArgumentException("같은 이름이 이미 있습니다."))
        }
        val newType = typeUnder(parentId, type)
        accountDao.update(
            current.copy(
                name = trimmed,
                type = newType,
                colorArgb = colorArgb,
                initialBalance = initialBalance,
                limitAmount = limit?.amount,
                limitPeriod = limit?.period,
                parentId = parentId,
                performanceTarget = performance?.target,
                performancePeriod = performance?.period,
                performanceSteps = performance?.stepsText,
                // 다른 상위로 옮기면 그 목록의 맨 끝에 넣는다. 예전 번호는 옮겨 간 곳에서 뜻이 없다.
                sortOrder = if (parentId == current.parentId) current.sortOrder else accountDao.nextSortOrder(parentId),
            )
        )
        // 상위의 종류를 바꾸면 하위도 따라간다. "체크카드" 아래 A카드만 "카드"로 남으면 안 된다.
        if (newType != current.type) accountDao.updateChildrenType(id, newType)
        // 실적을 없애면 뺄 카테고리도 지운다. 남겨 두면 나중에 실적을 다시 켰을 때 모르는 사이에 빠져 있다.
        accountDao.replaceExclusions(id, performance?.excludedCategoryIds.orEmpty())
        return Result.success(Unit)
    }

    /** 수정 창을 열 때 채워 넣을 뺄 카테고리. */
    suspend fun performanceExclusions(accountId: Long): Set<Long> =
        accountDao.excludedCategoryIds(accountId).toSet()

    /**
     * 하위 자산의 종류는 상위를 따른다. 체크카드 아래 A카드는 당연히 카드라서 다시 묻지 않는다.
     * 상위가 없으면 고른 종류 그대로다.
     */
    private suspend fun typeUnder(parentId: Long?, chosen: AccountType): AccountType =
        parentId?.let { accountDao.findById(it)?.type } ?: chosen

    /**
     * 상위로 둘 수 있는지. 카테고리와 같이 2뎁스까지만 쓴다.
     * 하위가 있는 자산을 다른 자산 밑에 넣으면 3뎁스가 되고, 하위의 하위는 고를 화면이 없다.
     */
    private suspend fun validateParent(id: Long?, parentId: Long?): Throwable? {
        parentId ?: return null
        if (parentId == id) return IllegalArgumentException("자기 자신 아래에는 넣을 수 없습니다.")
        val parent = accountDao.findById(parentId) ?: return IllegalStateException("상위 자산을 찾을 수 없습니다.")
        if (!parent.isTopLevel) return IllegalArgumentException("하위 자산 아래에는 넣을 수 없습니다.")
        if (id != null && accountDao.countChildren(id) > 0) {
            return IllegalArgumentException("하위 자산이 있는 자산은 다른 자산 아래로 옮길 수 없습니다.")
        }
        return null
    }

    suspend fun accountChildCount(accountId: Long): Int = accountDao.countChildren(accountId)

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
        accountDao.deleteWithReassign(id, toId)
        return Result.success(Unit)
    }
}

/** 결제수단 한도. 금액과 주기는 늘 짝으로 다닌다. 한쪽만 있으면 무엇을 셀지 정할 수 없다. */
data class AccountLimit(val amount: Long, val period: PeriodUnit) {
    fun validate(): Throwable? =
        if (amount <= 0L) IllegalArgumentException("한도 금액을 입력해 주세요.") else null
}

/** 카드 실적 목표. 금액과 주기는 한도처럼 늘 짝이고, 뺄 카테고리는 실적이 있을 때만 뜻이 있다. */
data class AccountPerformance(
    val target: Long,
    val period: PeriodUnit,
    val excludedCategoryIds: Set<Long> = emptySet(),
    /** 1단계([target]) 위의 단계. 작은 것부터. */
    val steps: List<Long> = emptyList(),
) {
    fun validate(): Throwable? = when {
        target <= 0L || steps.any { it <= 0L } -> IllegalArgumentException("실적 금액을 입력해 주세요.")
        steps.any { it <= target } || steps.zipWithNext().any { (a, b) -> a >= b } ->
            IllegalArgumentException("단계마다 금액이 달라야 합니다.")
        else -> null
    }

    val stepsText: String? get() = steps.takeIf { it.isNotEmpty() }?.joinToString(",")

    companion object {
        /** 적은 순서와 상관없이 작은 것이 1단계다. 같은 금액이 둘이면 [validate] 가 막는다. */
        fun of(amounts: List<Long>, period: PeriodUnit, excludedCategoryIds: Set<Long>): AccountPerformance {
            val sorted = amounts.sorted()
            return AccountPerformance(
                target = sorted.firstOrNull() ?: 0L,
                period = period,
                excludedCategoryIds = excludedCategoryIds,
                steps = sorted.drop(1),
            )
        }
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

/** 나눈 결제의 한 줄. */
data class PaymentPart(
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val memo: String = "",
    val excludedFromPerformance: Boolean = false,
)

/**
 * 실적 셈에 들어가는 종류. 지출은 더하고 수입(환불)은 뺀다. 이체는 쓴 돈도 돌려받은 돈도 아니다.
 * 그래서 이체에는 "실적에서 빼기"를 저장하지 않는다.
 */
private val TransactionType.affectsPerformance: Boolean
    get() = this != TransactionType.TRANSFER
