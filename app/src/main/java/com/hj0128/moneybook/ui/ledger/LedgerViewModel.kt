package com.hj0128.moneybook.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.DaySortOrder
import com.hj0128.moneybook.data.model.MonthTotal
import com.hj0128.moneybook.data.model.MonthlySummary
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
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

data class DailyUiState(
    val yearMonth: YearMonth,
    val summary: MonthlySummary = MonthlySummary(),
    /** 날짜별로 묶은 목록. 화면에서 다시 계산하지 않도록 여기서 만들어 둔다. */
    val groups: List<DayGroup> = emptyList(),
    val sortOrder: DaySortOrder = DaySortOrder.NEWEST_FIRST,
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
    val yearMonth: YearMonth,
    val type: TransactionType = TransactionType.EXPENSE,
    val totals: List<CategoryTotal> = emptyList(),
) {
    val sum: Long get() = totals.sumOf { it.total }

    fun ratioOf(item: CategoryTotal): Float =
        if (sum <= 0L) 0f else (item.total.toDouble() / sum).toFloat()
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

    private val _categoryType = MutableStateFlow(TransactionType.EXPENSE)
    val categoryType: StateFlow<TransactionType> = _categoryType.asStateFlow()

    private val monthTransactions = _yearMonth.flatMapLatest { month ->
        repository.observeMonth(month).map { items -> month to items }
    }

    val dailyState: StateFlow<DailyUiState> =
        combine(monthTransactions, settingsRepository.daySortOrder) { (month, items), sortOrder ->
            // DAO 가 날짜 내림차순으로 주므로 groupBy 결과도 최신순이다.
            val groups = items.groupBy { it.date }
                .map { (date, dayItems) ->
                    DayGroup(date = date, dayTotal = dayItems.toSummary(), items = dayItems)
                }
            DailyUiState(
                yearMonth = month,
                summary = items.toSummary(),
                groups = groups.applySort(sortOrder),
                sortOrder = sortOrder,
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
        combine(_yearMonth, _categoryType) { month, type -> month to type }
            .flatMapLatest { (month, type) ->
                repository.observeCategoryTotals(month, type)
                    .map { totals -> CategoryUiState(month, type, totals) }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = CategoryUiState(initialMonth),
            )

    fun selectTab(tab: LedgerTab) {
        _tab.value = tab
    }

    fun selectCategoryType(type: TransactionType) {
        _categoryType.value = type
    }

    /** 일별 탭 정렬 방향 전환. 저장까지 하므로 다음에 앱을 켜도 고른 방향이 유지된다. */
    fun toggleDaySortOrder() {
        val next = dailyState.value.sortOrder.toggled()
        viewModelScope.launch { settingsRepository.setDaySortOrder(next) }
    }

    /** 상단 화살표. 월별 탭에서는 한 해씩, 나머지 탭에서는 한 달씩 움직인다. */
    fun showPrevious() {
        _yearMonth.value = when (_tab.value) {
            LedgerTab.MONTHLY -> _yearMonth.value.minusYears(1)
            else -> _yearMonth.value.minusMonths(1)
        }
    }

    fun showNext() {
        _yearMonth.value = when (_tab.value) {
            LedgerTab.MONTHLY -> _yearMonth.value.plusYears(1)
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
