package com.hj0128.moneybook.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.CategorySortOrder
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.DaySortOrder
import com.hj0128.moneybook.data.model.LedgerPeriod
import com.hj0128.moneybook.data.model.PeriodMode
import com.hj0128.moneybook.data.model.sortedBy
import com.hj0128.moneybook.data.model.MonthTotal
import com.hj0128.moneybook.data.model.MonthlySummary
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.UncategorizedTotal
import com.hj0128.moneybook.data.prefs.SettingsRepository
import com.hj0128.moneybook.data.repository.LedgerRepository
import com.hj0128.moneybook.data.repository.toSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** 같은 데이터를 보는 세 가지 축. */
enum class LedgerTab(val label: String) {
    DAILY("일별"),
    MONTHLY("월별"),
    CATEGORY("카테고리별"),
}

/**
 * 일별 탭에서 어떤 거래만 볼지.
 *
 * 이체는 수입도 지출도 아니라 '전체'에서만 보인다. 이체만 따로 볼 일은 드물어서
 * 칸을 하나 더 늘리지 않았다.
 */
enum class DailyFilter(val label: String) {
    ALL("전체"),
    INCOME("수입"),
    EXPENSE("지출");

    fun matches(type: TransactionType): Boolean = when (this) {
        ALL -> true
        INCOME -> type == TransactionType.INCOME
        EXPENSE -> type == TransactionType.EXPENSE
    }
}

data class DailyUiState(
    val yearMonth: YearMonth,
    /** 필터와 무관한 그 달 전체 합계. 위 카드는 늘 같은 숫자를 보여줘야 기준점이 된다. */
    val summary: MonthlySummary = MonthlySummary(),
    /** 필터를 거친 뒤 날짜별로 묶은 목록. 화면에서 다시 계산하지 않도록 여기서 만들어 둔다. */
    val groups: List<DayGroup> = emptyList(),
    val sortOrder: DaySortOrder = DaySortOrder.NEWEST_FIRST,
    val filter: DailyFilter = DailyFilter.ALL,
    /** 필터 전 기준으로 그 달에 거래가 하나라도 있는지. "내역 없음"과 "걸러져서 없음"을 구분한다. */
    val hasAnyInMonth: Boolean = false,
    /** 나눈 결제의 줄들(묶음 번호 -> 입력 순서대로). 한 줄로 합친 결제 아래에 펼쳐 보인다. */
    val splitParts: Map<Long, List<TransactionDetail>> = emptyMap(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    fun partsOf(item: TransactionDetail): List<TransactionDetail> =
        item.splitGroupId?.let { splitParts[it] }.orEmpty()
}

data class DayGroup(
    val date: LocalDate,
    val dayTotal: MonthlySummary,
    val items: List<TransactionDetail>,
)

data class MonthlyUiState(
    val year: Int,
    /** 1월부터 12월까지 항상 12칸. 거래가 없는 달도 0 으로 채운다. */
    val months: List<MonthTotal> = emptyList(),
) {
    val yearTotal: MonthlySummary
        get() = MonthlySummary(
            income = months.sumOf { it.income },
            expense = months.sumOf { it.expense },
        )

    /** 막대 길이 기준값. 수입/지출 중 가장 큰 달에 맞춘다. */
    val maxAmount: Long
        get() = months.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0L
}

/**
 * 카테고리별 탭에서 무엇으로 묶을지.
 *
 * 탭을 하나 더 만들지 않고 기준만 바꾼다. 기간(월/년)과 수입/지출 선택, 정렬, 비중 막대가
 * 결제수단에서도 똑같이 필요해서 화면을 둘로 나누면 같은 것을 두 번 만들게 된다.
 */
enum class BreakdownBasis(val label: String) {
    CATEGORY("카테고리"),
    ACCOUNT("결제수단"),
}

data class CategoryUiState(
    val period: LedgerPeriod,
    val type: TransactionType = TransactionType.EXPENSE,
    val basis: BreakdownBasis = BreakdownBasis.CATEGORY,
    /** 한도나 실적이 걸린 결제수단. 결제수단 기준으로 지출을 볼 때 그 줄 아래에 남은 금액을 적는다. */
    val limitAccounts: List<AccountBalance> = emptyList(),
    val totals: List<CategoryTotal> = emptyList(),
    /** 카테고리가 빠진 거래. 0건이면 화면에 내지 않는다. */
    val uncategorized: UncategorizedTotal = UncategorizedTotal(entryCount = 0, total = 0L),
    val sortOrder: CategorySortOrder = CategorySortOrder.AMOUNT,
    /** 대분류 지출 카테고리의 한 달 예산(카테고리 id -> 금액). 예산이 없는 카테고리는 없다. */
    val budgets: Map<Long, Long> = emptyMap(),
) {
    /**
     * 예산을 보여줄지. 예산은 한 달 단위라 한 해를 볼 때는 비교할 것이 없고,
     * 수입이나 결제수단으로 묶었을 때는 대분류 지출 카테고리 줄이 아니다.
     */
    val showsBudget: Boolean
        get() = basis == BreakdownBasis.CATEGORY && type == TransactionType.EXPENSE &&
            period is LedgerPeriod.Month && budgets.isNotEmpty()

    fun budgetOf(item: CategoryTotal): Long? = if (showsBudget) budgets[item.categoryId] else null

    val budgetTotal: Long get() = budgets.values.sum()

    /** 카테고리에 쓴 돈 전부. 예산 화면 합계와 같은 셈이다(예산 없는 카테고리는 예산 0원). */
    val budgetSpent: Long get() = totals.sumOf { it.total }

    /**
     * 미분류까지 더한 진짜 합계.
     *
     * 카테고리 줄만 더하면 일별 탭의 지출 합계와 숫자가 어긋난다.
     * 두 화면이 다른 값을 말하면 어느 쪽이 맞는지 알 수 없다.
     */
    val sum: Long get() = totals.sumOf { it.total } + uncategorized.total

    val isEmpty: Boolean get() = totals.isEmpty() && uncategorized.isEmpty

    /** 그 줄의 한도 자산. 한도는 쓴 돈의 이야기라 수입을 볼 때는 없다고 답한다. */
    fun limitOf(item: CategoryTotal): AccountBalance? =
        if (basis == BreakdownBasis.ACCOUNT && type == TransactionType.EXPENSE) {
            limitAccounts.firstOrNull { it.id == item.categoryId }
        } else {
            null
        }

    fun ratioOf(item: CategoryTotal): Float = ratioOf(item.total)

    fun ratioOf(amount: Long): Float =
        if (sum <= 0L) 0f else (amount.toDouble() / sum).toFloat()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    initialMonth: YearMonth,
) : ViewModel() {

    private val _tab = MutableStateFlow(LedgerTab.DAILY)
    val tab: StateFlow<LedgerTab> = _tab.asStateFlow()

    /**
     * 기간 상태는 이것 하나뿐이다.
     * 월별 탭은 여기서 연도만 꺼내 쓰므로, 월별에서 한 달을 고르면 일별/카테고리별이 자동으로 따라온다.
     */
    private val _yearMonth = MutableStateFlow(initialMonth)
    val yearMonth: StateFlow<YearMonth> = _yearMonth.asStateFlow()

    /** 앱을 다시 켜면 전체로 돌아온다. 걸러진 채로 열리면 내역이 사라진 줄 안다. */
    private val _dailyFilter = MutableStateFlow(DailyFilter.ALL)

    private val _categoryType = MutableStateFlow(TransactionType.EXPENSE)
    val categoryType: StateFlow<TransactionType> = _categoryType.asStateFlow()

    /**
     * 카테고리별 탭의 집계 방식. 월/년이면 기간은 [_yearMonth] 하나에서 나온다.
     * 년으로 봤다가 월로 돌아오면 보던 달이 그대로 남아 있어야 해서 방식만 따로 둔다.
     */
    private val _categoryMode = MutableStateFlow(PeriodMode.MONTH)
    val categoryMode: StateFlow<PeriodMode> = _categoryMode.asStateFlow()

    /**
     * 직접 고른 기간. 월로 돌아갔다가 다시 "기간"을 눌러도 고른 날짜가 남아 있다.
     * 카드 명세서 기간처럼 매번 같은 모양을 고르는 일이 많아서다.
     */
    private val _customRange = MutableStateFlow<LedgerPeriod.Range?>(null)
    val customRange: StateFlow<LedgerPeriod.Range?> = _customRange.asStateFlow()

    private val _categoryBasis = MutableStateFlow(BreakdownBasis.CATEGORY)

    /** 카테고리별 탭이 지금 보는 기간. 상단 제목과 상세 화면으로 넘기는 값도 이것이다. */
    val categoryPeriod: StateFlow<LedgerPeriod> =
        combine(_categoryMode, _yearMonth, _customRange) { mode, month, range ->
            when (mode) {
                PeriodMode.MONTH -> LedgerPeriod.Month(month)
                PeriodMode.YEAR -> LedgerPeriod.Year(month.year)
                PeriodMode.RANGE -> range ?: LedgerPeriod.Month(month)
            }
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, LedgerPeriod.Month(initialMonth))

    private val monthTransactions = _yearMonth.flatMapLatest { month ->
        repository.observeMonth(month).map { items -> month to items }
    }

    val dailyState: StateFlow<DailyUiState> =
        combine(
            monthTransactions,
            settingsRepository.daySortOrder,
            _dailyFilter,
        ) { (month, items), sortOrder, filter ->
            // 하루 머리글의 합계도 걸러진 것만으로 다시 계산되도록 묶기 전에 거른다.
            val filtered = items.filter { filter.matches(it.type) }
            DailyUiState(
                yearMonth = month,
                summary = items.toSummary(),
                groups = filtered.collapseSplits().toDayGroups().applySort(sortOrder),
                sortOrder = sortOrder,
                filter = filter,
                hasAnyInMonth = items.isNotEmpty(),
                splitParts = filtered.splitPartsByGroup(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DailyUiState(initialMonth),
        )

    val monthlyState: StateFlow<MonthlyUiState> =
        _yearMonth.map { it.year }.distinctUntilChanged()
            .flatMapLatest { year ->
                repository.observeYear(year).map { totals -> year to totals }
            }.map { (year, totals) ->
                MonthlyUiState(year = year, months = totals.fillMissingMonths())
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MonthlyUiState(initialMonth.year, emptyList<MonthTotal>().fillMissingMonths()),
            )

    val categoryState: StateFlow<CategoryUiState> =
        combine(categoryPeriod, _categoryType, _categoryBasis) { period, type, basis ->
            Triple(period, type, basis)
        }
            .flatMapLatest { (period, type, basis) ->
                when (basis) {
                    BreakdownBasis.CATEGORY -> combine(
                        repository.observeCategoryTotals(period, type),
                        repository.observeUncategorizedTotal(period, type),
                        settingsRepository.categorySortOrder,
                        // 한 해를 볼 때는 예산을 쓰지 않는다. 아무 달이나 넘겨도 되지만 첫 달로 둔다.
                        repository.observeBudgetAmounts(YearMonth.from(period.start)),
                    ) { totals, uncategorized, sortOrder, budgets ->
                        // 미분류는 카테고리가 아니라 정리해야 할 것이므로 정렬에 끼지 않고
                        // 화면에서 늘 맨 아래에 붙는다.
                        CategoryUiState(
                            period = period,
                            type = type,
                            basis = basis,
                            totals = totals.sortedBy(sortOrder),
                            uncategorized = uncategorized,
                            sortOrder = sortOrder,
                            budgets = budgets,
                        )
                    }

                    // 거래에는 자산이 반드시 붙어 있어서(account_id NOT NULL) 미분류 줄이 없다.
                    BreakdownBasis.ACCOUNT -> combine(
                        repository.observeAccountTotals(period, type),
                        repository.observeAccounts(),
                        settingsRepository.categorySortOrder,
                    ) { totals, accounts, sortOrder ->
                        CategoryUiState(
                            period = period,
                            type = type,
                            basis = basis,
                            limitAccounts = accounts.filter { it.hasLimit || it.hasPerformance },
                            totals = totals.sortedBy(sortOrder),
                            sortOrder = sortOrder,
                        )
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CategoryUiState(LedgerPeriod.Month(initialMonth)),
            )

    fun selectTab(tab: LedgerTab) {
        _tab.value = tab
    }

    fun selectDailyFilter(filter: DailyFilter) {
        _dailyFilter.value = filter
    }

    fun selectCategoryType(type: TransactionType) {
        _categoryType.value = type
    }

    /** 일별 탭 정렬 방향 전환. 저장까지 하므로 다음에 앱을 켜도 고른 방향이 유지된다. */
    fun toggleDaySortOrder() {
        val next = dailyState.value.sortOrder.toggled()
        viewModelScope.launch { settingsRepository.setDaySortOrder(next) }
    }

    fun selectCategorySortOrder(order: CategorySortOrder) {
        viewModelScope.launch { settingsRepository.setCategorySortOrder(order) }
    }

    fun selectCategoryBasis(basis: BreakdownBasis) {
        _categoryBasis.value = basis
    }

    /** 월/년으로 바꾼다. "기간"은 날짜를 골라야 해서 [selectCustomRange] 로 들어간다. */
    fun selectCategoryMode(mode: PeriodMode) {
        if (mode == PeriodMode.RANGE && _customRange.value == null) return
        _categoryMode.value = mode
    }

    /** 날짜 고르기 창에서 정한 기간으로 본다. */
    fun selectCustomRange(start: LocalDate, end: LocalDate) {
        _customRange.value = LedgerPeriod.Range(minOf(start, end), maxOf(start, end))
        _categoryMode.value = PeriodMode.RANGE
    }

    /** 카테고리별 탭에서 직접 고른 기간을 보는 중인지. 그때는 상단 제목과 화살표가 그 기간을 따른다. */
    fun isRangeView(tab: LedgerTab, mode: PeriodMode): Boolean =
        tab == LedgerTab.CATEGORY && mode == PeriodMode.RANGE

    /**
     * 지금 탭이 한 해 단위로 보는지. 상단 제목과 화살표 보폭이 이 값을 따른다.
     * 월별 탭은 늘 한 해, 카테고리별 탭은 고른 방식에 따라 다르다.
     */
    fun isYearView(tab: LedgerTab, mode: PeriodMode): Boolean = when (tab) {
        LedgerTab.MONTHLY -> true
        LedgerTab.CATEGORY -> mode == PeriodMode.YEAR
        LedgerTab.DAILY -> false
    }

    /** 상단 화살표. 한 해 단위로 보는 중이면 한 해씩, 직접 고른 기간이면 그 길이만큼, 아니면 한 달씩. */
    fun showPrevious() = shift(-1)

    fun showNext() = shift(1)

    private fun shift(by: Int) {
        val range = _customRange.value
        if (isRangeView(_tab.value, _categoryMode.value) && range != null) {
            _customRange.value = range.shifted(by) as LedgerPeriod.Range
            return
        }
        _yearMonth.value = when {
            isYearView(_tab.value, _categoryMode.value) -> _yearMonth.value.plusYears(by.toLong())
            else -> _yearMonth.value.plusMonths(by.toLong())
        }
    }

    /** 월별 탭에서 한 달을 누르면 그 달의 일별 목록으로 넘어간다. */
    fun openMonth(monthValue: Int) {
        _yearMonth.value = YearMonth.of(_yearMonth.value.year, monthValue)
        _tab.value = LedgerTab.DAILY
    }

    /** 일별 탭의 한 줄은 결제 하나라, 나눈 결제면 묶음 전체를 지운다. */
    fun delete(id: Long) {
        viewModelScope.launch { repository.deletePayment(id) }
    }

    companion object {
        fun factory(initialMonth: YearMonth): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                LedgerViewModel(
                    repository = container.ledgerRepository,
                    settingsRepository = container.settingsRepository,
                    initialMonth = initialMonth,
                )
            }
        }
    }
}

/**
 * 하루 단위로 묶는다. 일별 탭과 카테고리 상세가 같은 모양의 목록을 쓴다.
 *
 * DAO 가 날짜 내림차순으로 주므로 groupBy 결과도 최신순이다.
 */
/**
 * 나눈 결제를 한 줄로 합친다. 일별 탭은 카드 명세서와 한 줄씩 맞춰 보는 곳이라 결제 단위가 맞다.
 *
 * 합친 줄은 처음 입력한 줄을 바탕으로 금액은 합계, 카테고리·결제수단은 "생필품 외 1"처럼 적는다.
 * id 도 첫 줄 것이라 누르면 입력 화면이 묶음 전체를 불러온다.
 */
internal fun List<TransactionDetail>.collapseSplits(): List<TransactionDetail> {
    val partsByGroup = filter { it.splitGroupId != null }.groupBy { it.splitGroupId }
    val seen = mutableSetOf<Long>()
    return mapNotNull { item ->
        val groupId = item.splitGroupId ?: return@mapNotNull item
        if (!seen.add(groupId)) return@mapNotNull null
        // 같은 날 같은 시각이라 쿼리 순서로는 줄 순서가 정해지지 않는다. 입력한 순서(id)로 맞춘다.
        val parts = partsByGroup.getValue(groupId).sortedBy { it.id }
        parts.first().copy(
            amount = parts.sumOf { it.amount },
            categoryName = parts.summaryLabel { it.categoryName ?: "미분류" },
            accountName = parts.summaryLabel { it.accountName },
        )
    }
}

/** 나눈 결제의 줄을 묶음 번호별로, 입력한 순서대로. */
internal fun List<TransactionDetail>.splitPartsByGroup(): Map<Long, List<TransactionDetail>> =
    mapNotNull { item -> item.splitGroupId?.let { it to item } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, parts) -> parts.sortedBy { it.id } }

/** 값이 하나면 그대로, 여럿이면 "첫 값 외 N". */
private fun List<TransactionDetail>.summaryLabel(pick: (TransactionDetail) -> String?): String? {
    val names = mapNotNull(pick).distinct()
    return when (names.size) {
        0 -> null
        1 -> names.single()
        else -> "${names.first()} 외 ${names.size - 1}"
    }
}

internal fun List<TransactionDetail>.toDayGroups(): List<DayGroup> =
    groupBy { it.date }.map { (date, dayItems) ->
        DayGroup(date = date, dayTotal = dayItems.toSummary(), items = dayItems)
    }

/**
 * 정렬 방향 적용. DAO 가 최신순으로 주므로 최신순은 그대로 두고 오래된순만 뒤집는다.
 *
 * 같은 날 안의 순서도 같이 뒤집는다. 날짜만 뒤집으면 "1일부터 보기"인데 하루 안에서는
 * 나중에 쓴 것이 위에 오는 어색한 순서가 된다.
 */
internal fun List<DayGroup>.applySort(order: DaySortOrder): List<DayGroup> = when (order) {
    DaySortOrder.NEWEST_FIRST -> this
    DaySortOrder.OLDEST_FIRST -> reversed().map { it.copy(items = it.items.reversed()) }
}

/** 거래가 없는 달은 쿼리 결과에 아예 없다. 화면에서 12칸을 유지하려면 여기서 채워야 한다. */
private fun List<MonthTotal>.fillMissingMonths(): List<MonthTotal> {
    val byMonth = associateBy { it.monthValue }
    return (1..12).map { month ->
        byMonth[month] ?: MonthTotal(monthValue = month, income = 0L, expense = 0L)
    }
}
