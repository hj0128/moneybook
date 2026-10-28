package com.hj0128.moneybook.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.CategorySortOrder
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.DaySortOrder
import com.hj0128.moneybook.data.model.LedgerPeriod
import com.hj0128.moneybook.data.model.PeriodUnit
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
) {
    val isEmpty: Boolean get() = groups.isEmpty()
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

data class CategoryUiState(
    val period: LedgerPeriod,
    val type: TransactionType = TransactionType.EXPENSE,
    val totals: List<CategoryTotal> = emptyList(),
    /** 카테고리가 빠진 거래. 0건이면 화면에 내지 않는다. */
    val uncategorized: UncategorizedTotal = UncategorizedTotal(entryCount = 0, total = 0L),
    val sortOrder: CategorySortOrder = CategorySortOrder.AMOUNT,
) {
    /**
     * 미분류까지 더한 진짜 합계.
     *
     * 카테고리 줄만 더하면 일별 탭의 지출 합계와 숫자가 어긋난다.
     * 두 화면이 다른 값을 말하면 어느 쪽이 맞는지 알 수 없다.
     */
    val sum: Long get() = totals.sumOf { it.total } + uncategorized.total

    val isEmpty: Boolean get() = totals.isEmpty() && uncategorized.isEmpty

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
     * 카테고리별 탭의 집계 단위. 기간 자체는 [_yearMonth] 하나에서 나온다.
     * 년으로 봤다가 월로 돌아오면 보던 달이 그대로 남아 있어야 해서 단위만 따로 둔다.
     */
    private val _categoryUnit = MutableStateFlow(PeriodUnit.MONTH)
    val categoryUnit: StateFlow<PeriodUnit> = _categoryUnit.asStateFlow()

    private val categoryPeriod = combine(_categoryUnit, _yearMonth) { unit, month ->
        LedgerPeriod.of(unit, month)
    }.distinctUntilChanged()

    private val monthTransactions = _yearMonth.flatMapLatest { month ->
        repository.observeMonth(month).map { items -> month to items }
    }

    val dailyState: StateFlow<DailyUiState> =
        combine(
            monthTransactions,
            settingsRepository.daySortOrder,
            _dailyFilter,
        ) { (month, items), sortOrder, filter ->
            DailyUiState(
                yearMonth = month,
                summary = items.toSummary(),
                // 하루 머리글의 합계도 걸러진 것만으로 다시 계산되도록 묶기 전에 거른다.
                groups = items.filter { filter.matches(it.type) }.toDayGroups().applySort(sortOrder),
                sortOrder = sortOrder,
                filter = filter,
                hasAnyInMonth = items.isNotEmpty(),
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
        combine(categoryPeriod, _categoryType) { period, type -> period to type }
            .flatMapLatest { (period, type) ->
                combine(
                    repository.observeCategoryTotals(period, type),
                    repository.observeUncategorizedTotal(period, type),
                    settingsRepository.categorySortOrder,
                ) { totals, uncategorized, sortOrder ->
                    // 미분류는 카테고리가 아니라 정리해야 할 것이므로 정렬에 끼지 않고
                    // 화면에서 늘 맨 아래에 붙는다.
                    CategoryUiState(period, type, totals.sortedBy(sortOrder), uncategorized, sortOrder)
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

    fun selectCategoryUnit(unit: PeriodUnit) {
        _categoryUnit.value = unit
    }

    /**
     * 지금 탭이 한 해 단위로 보는지. 상단 제목과 화살표 보폭이 이 값을 따른다.
     * 월별 탭은 늘 한 해, 카테고리별 탭은 고른 단위에 따라 다르다.
     */
    fun isYearView(tab: LedgerTab, categoryUnit: PeriodUnit): Boolean = when (tab) {
        LedgerTab.MONTHLY -> true
        LedgerTab.CATEGORY -> categoryUnit == PeriodUnit.YEAR
        LedgerTab.DAILY -> false
    }

    /** 상단 화살표. 한 해 단위로 보는 중이면 한 해씩, 아니면 한 달씩 움직인다. */
    fun showPrevious() {
        _yearMonth.value = when {
            isYearView(_tab.value, _categoryUnit.value) -> _yearMonth.value.minusYears(1)
            else -> _yearMonth.value.minusMonths(1)
        }
    }

    fun showNext() {
        _yearMonth.value = when {
            isYearView(_tab.value, _categoryUnit.value) -> _yearMonth.value.plusYears(1)
            else -> _yearMonth.value.plusMonths(1)
        }
    }

    /** 월별 탭에서 한 달을 누르면 그 달의 일별 목록으로 넘어간다. */
    fun openMonth(monthValue: Int) {
        _yearMonth.value = YearMonth.of(_yearMonth.value.year, monthValue)
        _tab.value = LedgerTab.DAILY
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
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
