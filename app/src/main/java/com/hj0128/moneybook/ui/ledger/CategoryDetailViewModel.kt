package com.hj0128.moneybook.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.CategoryTotal
import com.hj0128.moneybook.data.model.LedgerPeriod
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.repository.LedgerRepository
import java.time.LocalDate
import java.time.YearMonth
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 재분류 후보 한 줄. [parentName] 은 소분류일 때만 채워진다. */
data class AssignableCategory(
    val category: CategoryEntity,
    val parentName: String?,
) {
    val label: String get() = parentName?.let { "$it > ${category.name}" } ?: category.name
}

/** 이 화면이 무엇을 보고 있는지. 카테고리 하나이거나, 카테고리가 빠진 것들이거나. */
sealed interface DetailSource {
    data class Category(val id: Long) : DetailSource

    /** 미분류. 카테고리가 없으니 수입/지출로 좁힌다. */
    data class Uncategorized(val type: TransactionType) : DetailSource
}

data class CategoryDetailUiState(
    val period: LedgerPeriod,
    /** 평균의 분모와 추이의 "아직 안 온 달"을 가르는 기준. 테스트에서 날짜를 고정할 수 있게 밖에서 받는다. */
    val today: LocalDate = LocalDate.now(),
    val categoryName: String = "",
    val categoryColor: Int? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val groups: List<DayGroup> = emptyList(),
    /** 소분류별 소계. 대분류에 직접 붙은 거래도 한 줄로 들어 있다. */
    val childTotals: List<CategoryTotal> = emptyList(),
) {
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

    /** 년 단위일 때만 추이를 보여준다. 한 달짜리에 막대 하나는 위 합계와 같은 숫자다. */
    val showsTrend: Boolean get() = period is LedgerPeriod.Year && !isEmpty

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
            .filter { childId == null || it.categoryId == childId }
            .groupBy { it.date.monthValue }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
        return (1..12).map { byMonth[it] ?: 0L }
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
    period: LedgerPeriod,
) : ViewModel() {

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

    val state: StateFlow<CategoryDetailUiState> = when (source) {
        is DetailSource.Category -> combine(
            repository.observeCategoryTransactions(period, source.id),
            repository.observeCategory(source.id),
            repository.observeChildTotals(period, source.id),
        ) { items, category, childTotals ->
            CategoryDetailUiState(
                period = period,
                // 카테고리가 지워지면 이름을 못 읽는다. 화면이 비지 않게 기본값을 둔다.
                categoryName = category?.name ?: "삭제된 카테고리",
                categoryColor = category?.colorArgb,
                type = category?.type ?: TransactionType.EXPENSE,
                groups = items.toDayGroups(),
                childTotals = childTotals,
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryDetailUiState(period),
    )

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

    fun selectAll() {
        _selectedIds.value = state.value.groups.flatMap { group -> group.items.map { it.id } }.toSet()
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
                        period = period,
                    )
                }
            }
    }
}
