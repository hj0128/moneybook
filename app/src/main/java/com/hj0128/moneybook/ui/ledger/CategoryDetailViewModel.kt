package com.hj0128.moneybook.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.LedgerPeriod
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.repository.LedgerRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 재분류 후보 한 줄. [parentName] 은 소분류일 때만 채워진다. */
data class AssignableCategory(
    val category: CategoryEntity,
    val parentName: String?,
) {
    val label: String get() = parentName?.let { "$it > ${category.name}" } ?: category.name
}

/** 추이 막대 한 칸. 년 단위면 한 달, 월 단위면 달력 한 주(월~일). */
data class TrendBar(
    /** 고른 칸 옆에 적는 이름. "3월" / "2주차 (5~11일)" */
    val label: String,
    /** 막대 아래 눈금. "3" / "2주차" */
    val axisLabel: String,
    val amount: Long,
    /** 아직 오지 않은 칸. 0원과 구분해서 흐리게 그린다. "안 썼다"와 "아직 모른다"는 다르다. */
    val isFuture: Boolean,
)

/** 이 화면이 무엇을 보고 있는지. 카테고리 하나, 카테고리가 빠진 것들, 또는 결제수단 하나. */
sealed interface DetailSource {
    data class Category(val id: Long) : DetailSource

    /** 미분류. 카테고리가 없으니 수입/지출로 좁힌다. */
    data class Uncategorized(val type: TransactionType) : DetailSource

    /** 결제수단. 자산은 수입도 지출도 오가므로 어느 쪽을 볼지 함께 받는다. */
    data class Account(val id: Long, val type: TransactionType) : DetailSource
}

data class CategoryDetailUiState(
    val period: LedgerPeriod,
    /** 평균의 분모와 추이의 "아직 안 온 달"을 가르는 기준. 테스트에서 날짜를 고정할 수 있게 밖에서 받는다. */
    val today: LocalDate = LocalDate.now(),
    val categoryName: String = "",
    val categoryColor: Int? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val groups: List<DayGroup> = emptyList(),
    /**
     * 아래 단계 소계. 카테고리면 소분류별(대분류에 직접 붙은 거래도 한 줄),
     * 결제수단이면 카테고리별이다.
     */
    val childTotals: List<CategoryTotal> = emptyList(),
    val breakdownLabel: String = "소분류",
    /**
     * 소계 줄을 눌러 추이와 내역을 그 줄로 좁힐 수 있는지.
     *
     * 결제수단의 카테고리 소계는 소분류를 대분류로 합친 줄이라, 내역의 category_id(소분류일 수 있음)
     * 와 바로 맞대 볼 수 없다. 맞지 않는 막대를 보여주느니 고를 수 없게 둔다.
     */
    val breakdownFiltersTrend: Boolean = true,
    /**
     * 소계 줄이 하위 자산인지. 그러면 내역을 카테고리가 아니라 자산으로 걸러야 한다.
     * 상위 자산 상세가 카테고리 상세의 소분류처럼 하위 자산으로 쪼개 보여줄 때 쓴다.
     */
    val breakdownByAccount: Boolean = false,
    /** 결제수단 상세일 때 그 자산. 한도 막대를 그리는 데 쓴다. */
    val account: AccountBalance? = null,
    /** 대분류 지출 카테고리의 한 달 예산. 한 달을 볼 때만 막대를 그린다. */
    val budget: Long? = null,
) {
    /** 예산 막대에 쓸 금액. 한 해를 보면 한 달 예산과 비교할 수 없어 없다고 답한다. */
    val monthBudget: Long? get() = budget?.takeIf { period is LedgerPeriod.Month }

    val isEmpty: Boolean get() = groups.isEmpty()

    /**
     * 줄이 하나뿐이면 그 값이 곧 합계라 위 카드와 같은 숫자를 두 번 보여주는 꼴이다.
     * 쪼개져 있을 때만 breakdown 을 낸다.
     */
    val hasBreakdown: Boolean get() = childTotals.size > 1

    fun childRatioOf(item: CategoryTotal): Float =
        if (total <= 0L) 0f else (item.total.toDouble() / total).toFloat()

    val entryCount: Int get() = groups.sumOf { it.items.size }

    val total: Long get() = groups.sumOf { group -> group.items.sumOf { it.amount } }

    /**
     * 평균. 한 달을 보면 하루 평균, 한 해를 보면 월 평균이다.
     *
     * 거래가 있었던 날(달)이 아니라 달력 전체로 나눈다. "쓴 날만" 기준으로 하면 이틀에
     * 10만원씩 두 번 쓴 달이 하루 평균 10만원으로 나와 실제 씀씀이보다 과장돼 보인다.
     * 달력 기준이어야 다른 기간과 비교가 된다.
     *
     * 한 해에 하루 평균을 쓰지 않는 이유: 365로 나눈 몇천 원은 감이 안 온다.
     * 한 해를 볼 때 궁금한 건 "한 달에 얼마꼴"이다.
     *
     * 올해는 이번 달까지의 달 수로 나눈다. 아직 오지 않은 11·12월까지 12로 나누면
     * 실제 씀씀이보다 낮게 나온다. 이번 달은 합계에 이미 들어 있으니 분모에도 넣는다.
     */
    val averageLabel: String
        get() = when (period) {
            is LedgerPeriod.Month -> "하루 평균"
            is LedgerPeriod.Year -> "월 평균"
        }

    val average: Long
        get() = when (period) {
            is LedgerPeriod.Month -> total / elapsedDays(period.yearMonth)
            is LedgerPeriod.Year -> total / elapsedMonths(period.year)
        }

    /** 이번 달은 오늘까지의 일수. 월 평균과 같은 이유로 아직 오지 않은 날은 분모에서 뺀다. */
    private fun elapsedDays(month: YearMonth): Int = when {
        month == YearMonth.from(today) -> today.dayOfMonth
        else -> month.lengthOfMonth()
    }

    private fun elapsedMonths(year: Int): Int = when {
        year < today.year -> 12
        year == today.year -> today.monthValue
        // 미래 연도는 합계가 0 이라 무엇으로 나눠도 0 이다. 0 으로 나누지만 않으면 된다.
        else -> 12
    }

    /** 소계 줄 하나에 속하는지 볼 때 쓰는 값. 하위 자산으로 쪼갰으면 자산, 아니면 카테고리. */
    private fun TransactionDetail.breakdownKey(): Long? = if (breakdownByAccount) accountId else categoryId

    /** 년 단위는 달별, 월 단위는 주별 막대. 어느 쪽이든 보고 있는 기간 안만 쪼갠다. */
    val showsTrend: Boolean get() = !isEmpty

    val trendTitle: String get() = if (period is LedgerPeriod.Year) "월별 추이" else "주별 추이"

    /**
     * 추이 막대. [childId] 를 주면 그 소분류만.
     *
     * 월 단위의 주는 달력 주(월~일)로 끊는다. 10월 1일이 목요일이면 1~4일이 1주차다.
     * 달력에서 보던 "몇째 주"와 같아야 "2주차에 많이 썼네"가 바로 떠오른다.
     * 그래서 첫 주와 마지막 주는 7일보다 짧을 수 있고, 고른 칸 옆에 날짜 범위를 함께 적는다.
     */
    fun trend(childId: Long? = null): List<TrendBar> = when (val p = period) {
        is LedgerPeriod.Year -> monthlyTrend(childId).mapIndexed { index, amount ->
            val month = index + 1
            TrendBar(label = "${month}월", axisLabel = "$month", amount = amount, isFuture = isFutureMonth(month))
        }

        is LedgerPeriod.Month -> {
            val days = p.yearMonth.lengthOfMonth()
            // 1일이 무슨 요일인지만큼 앞으로 당겨 세면 월요일마다 주가 바뀐다(월요일 = 0).
            val offset = p.yearMonth.atDay(1).dayOfWeek.value - 1
            fun weekOf(day: Int) = (day - 1 + offset) / 7
            val byWeek = groups
                .flatMap { it.items }
                .filter { childId == null || it.breakdownKey() == childId }
                .groupBy { weekOf(it.date.dayOfMonth) }
                .mapValues { (_, items) -> items.sumOf { it.amount } }
            (0..weekOf(days)).map { week ->
                val first = maxOf(1, week * 7 - offset + 1)
                val last = minOf(week * 7 - offset + 7, days)
                val range = if (first == last) "${first}일" else "$first~${last}일"
                TrendBar(
                    label = "${week + 1}주차 ($range)",
                    axisLabel = "${week + 1}주차",
                    amount = byWeek[week] ?: 0L,
                    isFuture = p.yearMonth.atDay(first).isAfter(today),
                )
            }
        }
    }

    /**
     * 1월부터 12월까지의 달별 합계. 항상 12칸이다. [childId] 를 주면 그 소분류만.
     *
     * 쿼리를 따로 두지 않고 이미 불러온 그 해 내역을 달별로 묶는다. 같은 데이터에서 나오니
     * 위 합계와 막대의 합이 어긋날 일이 없다.
     */
    fun monthlyTrend(childId: Long? = null): List<Long> {
        val byMonth = groups
            .flatMap { it.items }
            // childId 가 있으면 그 소분류(또는 대분류에 직접 붙은 건)만 센다.
            .filter { childId == null || it.breakdownKey() == childId }
            .groupBy { it.date.monthValue }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
        return (1..12).map { byMonth[it] ?: 0L }
    }

    /**
     * 아래 내역 목록. [childId] 를 주면 그 소분류(또는 대분류에 직접 붙은 건)만.
     *
     * 추이 막대와 같은 조건으로 거른다. 막대는 외식인데 목록에 마트가 섞이면 무엇을 보는지 흐려진다.
     * 날짜별 소계도 걸러진 내역으로 다시 낸다.
     */
    fun groupsOf(childId: Long?): List<DayGroup> {
        if (childId == null) return groups
        return groups
            .flatMap { it.items }
            .filter { it.breakdownKey() == childId }
            .toDayGroups()
    }

    /** 아직 오지 않은 달. 0원과 구분해서 흐리게 그린다. "안 썼다"와 "아직 모른다"는 다르다. */
    fun isFutureMonth(month: Int): Boolean {
        val year = (period as? LedgerPeriod.Year)?.year ?: return false
        return year > today.year || (year == today.year && month > today.monthValue)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailViewModel(
    private val repository: LedgerRepository,
    val source: DetailSource,
    initialPeriod: LedgerPeriod,
) : ViewModel() {

    /**
     * 보고 있는 기간. 목록에서 넘겨받은 기간으로 시작해 이 화면 안에서 앞뒤로 옮길 수 있다.
     * 지난달과 비교하려고 목록으로 나갔다 다시 들어오지 않아도 된다.
     */
    private val _period = MutableStateFlow(initialPeriod)
    val period: StateFlow<LedgerPeriod> = _period.asStateFlow()

    /**
     * 선택 모드는 상단바 버튼으로만 켠다.
     *
     * 길게 누르기로 켜면 일별 탭에서는 삭제인 동작이 여기서는 선택이 되어,
     * 같은 목록인데 화면마다 다르게 반응한다.
     */
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val state: StateFlow<CategoryDetailUiState> = _period.flatMapLatest { period -> detailFlow(period) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryDetailUiState(initialPeriod),
        )

    private fun detailFlow(period: LedgerPeriod): Flow<CategoryDetailUiState> = when (source) {
        is DetailSource.Category -> combine(
            repository.observeCategoryTransactions(period, source.id),
            repository.observeCategory(source.id),
            repository.observeChildTotals(period, source.id),
            repository.observeBudgetAmounts(YearMonth.from(period.start)),
        ) { items, category, childTotals, budgets ->
            CategoryDetailUiState(
                period = period,
                // 카테고리가 지워지면 이름을 못 읽는다. 화면이 비지 않게 기본값을 둔다.
                categoryName = category?.name ?: "삭제된 카테고리",
                categoryColor = category?.colorArgb,
                type = category?.type ?: TransactionType.EXPENSE,
                groups = items.toDayGroups(),
                childTotals = childTotals,
                budget = category?.takeIf { it.isTopLevel }?.let { budgets[it.id] },
            )
        }

        is DetailSource.Account -> combine(
            repository.observeAccountTransactions(period, source.id, source.type),
            repository.observeAccountTree(),
            repository.observeAccountCategoryTotals(period, source.id, source.type),
            repository.observeAccountChildTotals(period, source.id, source.type),
        ) { items, nodes, categoryTotals, childTotals ->
            val node = nodes.firstOrNull { it.account.id == source.id }
            val account = node?.account ?: nodes.flatMap { it.children }.firstOrNull { it.id == source.id }
            // 하위 자산이 있으면 카테고리 상세의 소분류처럼 하위 자산으로 쪼갠다.
            // 없으면 예전처럼 카테고리로 쪼갠다("복지포인트로 무엇을 샀나").
            val byChild = node?.hasChildren == true
            CategoryDetailUiState(
                period = period,
                categoryName = account?.name ?: "삭제된 자산",
                categoryColor = account?.colorArgb,
                type = source.type,
                groups = items.toDayGroups(),
                childTotals = if (byChild) childTotals else categoryTotals,
                breakdownLabel = if (byChild) "하위 자산" else "카테고리",
                breakdownFiltersTrend = byChild,
                breakdownByAccount = byChild,
                account = account,
            )
        }

        is DetailSource.Uncategorized -> repository.observeUncategorized(period, source.type)
            .map { items ->
                CategoryDetailUiState(
                    period = period,
                    categoryName = "미분류",
                    categoryColor = null,
                    type = source.type,
                    groups = items.toDayGroups(),
                )
            }
    }

    /** 고른 내역은 그 기간의 것이라 기간을 옮기면 선택을 푼다. 안 보이는 내역이 옮겨지면 안 된다. */
    fun showPrevious() {
        exitSelection()
        _period.update { it.shifted(-1) }
    }

    fun showNext() {
        exitSelection()
        _period.update { it.shifted(1) }
    }

    /**
     * 재분류로 보낼 수 있는 카테고리.
     *
     * 입력 화면과 같은 규칙을 쓴다. 소분류가 있는 대분류는 후보에서 빠진다.
     * 정리하려고 연 화면에서 다시 애매한 자리로 보낼 수 있으면 정리가 안 된다.
     */
    val assignableCategories: StateFlow<List<AssignableCategory>> =
        state.map { it.type }.distinctUntilChanged()
            .flatMapLatest { type -> repository.observeCategoryTree(type) }
            .map { nodes ->
                nodes.flatMap { node ->
                    node.selectable.map { category ->
                        AssignableCategory(
                            category = category,
                            // 소분류면 어느 대분류 밑인지 같이 보여준다. 이름만으로는 헷갈린다.
                            parentName = node.category.name.takeIf { node.hasChildren },
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun startSelection() {
        _selectionMode.value = true
    }

    fun toggleSelection(id: Long) {
        _selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun exitSelection() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    /** 화면에 보이는 내역만 고른다. 소분류로 좁혀 둔 채 "전체"를 눌러 안 보이는 것까지 옮기면 안 된다. */
    fun selectAll(visibleIds: Collection<Long>) {
        _selectedIds.value = visibleIds.toSet()
    }

    /** 고른 내역을 한 카테고리로 옮긴다. 옮기고 나면 선택 모드를 끝낸다. */
    fun assignSelected(categoryId: Long) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.assignCategory(ids, categoryId)
            exitSelection()
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }

    companion object {
        fun factory(source: DetailSource, period: LedgerPeriod): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CategoryDetailViewModel(
                        repository = appContainer().ledgerRepository,
                        source = source,
                        initialPeriod = period,
                    )
                }
            }
    }
}
