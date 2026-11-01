package com.hj0128.moneybook.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.MonthlySummary
import com.hj0128.moneybook.data.model.SearchCandidate
import com.hj0128.moneybook.data.model.TransactionDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.repository.LedgerRepository
import com.hj0128.moneybook.data.repository.toSummary
import com.hj0128.moneybook.ui.ledger.DayGroup
import com.hj0128.moneybook.ui.ledger.collapseSplits
import com.hj0128.moneybook.ui.ledger.splitPartsByGroup
import com.hj0128.moneybook.ui.ledger.toDayGroups
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

data class SearchUiState(
    /** 검색어도 필터도 비어 있으면 결과 대신 무엇으로 찾을 수 있는지 안내한다. */
    val searched: Boolean = false,
    /** 찾은 결제 수. 화면에 보이는 줄 수와 같다(나눈 결제를 통째로 찾았으면 한 건). */
    val count: Int = 0,
    val summary: MonthlySummary = MonthlySummary(),
    val groups: List<DayGroup> = emptyList(),
    val splitParts: Map<Long, List<TransactionDetail>> = emptyMap(),
) {
    fun partsOf(item: TransactionDetail): List<TransactionDetail> =
        item.splitGroupId?.let { splitParts[it] }.orEmpty()
}

class SearchViewModel(repository: LedgerRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _filters = MutableStateFlow(SearchFilters())

    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    /** 카테고리 필터 시트용. 지출/수입을 오가며 고르므로 둘 다 들고 있는다. */
    val expenseCategories: StateFlow<List<CategoryNode>> = repository.observeCategoryTree(TransactionType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeCategories: StateFlow<List<CategoryNode>> = repository.observeCategoryTree(TransactionType.INCOME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 입력칸은 이 값을 바로 그린다. 결과만 잠깐 늦게 따라온다. */
    val query: StateFlow<String> = _query.asStateFlow()

    // 한 글자 칠 때마다 전체를 다시 거르지 않게 잠깐 멈췄을 때만 거른다.
    @OptIn(FlowPreview::class)
    val state: StateFlow<SearchUiState> = combine(
        _query.debounce(150),
        _filters,
        repository.observeSearchCandidates(),
    ) { query, filters, candidates -> search(query, filters, candidates) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(),
        )

    fun updateQuery(query: String) {
        _query.value = query
    }

    fun setType(type: TransactionType?) {
        _filters.update { filters ->
            // 지출만 보겠다면서 수입 카테고리가 걸려 있으면 그 카테고리는 아무것도 못 찾는다. 맞지 않는 것은 푼다.
            filters.copy(
                type = type,
                categories = filters.categories.filter { type == null || it.type == type },
            )
        }
    }

    /**
     * 카테고리 켜고 끄기. 수입 카테고리를 더했는데 "지출"이 걸려 있으면 더한 것이 아무것도 못 찾으므로
     * 종류 필터를 푼다.
     */
    fun toggleCategory(category: CategoryFilter) {
        _filters.update { filters ->
            val selected = filters.categories.any { it.id == category.id }
            if (selected) {
                filters.copy(categories = filters.categories.filterNot { it.id == category.id })
            } else {
                filters.copy(
                    categories = filters.categories + category,
                    type = filters.type?.takeIf { it == category.type },
                )
            }
        }
    }

    fun clearCategories() {
        _filters.update { it.copy(categories = emptyList()) }
    }

    fun setAmount(amount: AmountRange?) {
        _filters.update { it.copy(amount = amount) }
    }

    fun toggleMonth(month: YearMonth) {
        _filters.update { filters ->
            filters.copy(months = if (month in filters.months) filters.months - month else filters.months + month)
        }
    }

    /** 빠른 선택은 그 달들을 더한다. 이미 고른 달을 지우지 않는다("작년" + "올해 3월"도 된다). */
    fun addMonths(range: MonthRange) {
        _filters.update { it.copy(months = it.months + range.months) }
    }

    /** 칩 하나 지우기. 이어진 달 묶음("7월 ~ 9월")을 통째로 뺀다. */
    fun removeMonths(range: MonthRange) {
        _filters.update { it.copy(months = it.months - range.months.toSet()) }
    }

    fun clearMonths() {
        _filters.update { it.copy(months = emptySet()) }
    }

    fun clearFilters() {
        _filters.value = SearchFilters()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(appContainer().ledgerRepository) }
        }
    }
}

/**
 * [filters] 를 통과하고, 띄어쓰기로 나눈 단어가 모두 들어 있는 내역을 찾는다("스타벅스 카드" → 스타벅스에서 카드로 쓴 것).
 * 한 단어는 거래처·메모·카테고리(대분류 포함)·결제수단 중 어디에 있어도 되고, 숫자면 금액과도 맞춰 본다.
 *
 * 나눈 결제는 줄이 모두 맞으면 평소처럼 한 결제로 묶어 보여준다. 일부만 맞으면(다이소 결제에서 "라면"만)
 * 맞은 줄만 따로 보여준다. 묶어 보이면 합계에 안 들어간 면봉까지 결과처럼 보인다.
 */
internal fun search(query: String, filters: SearchFilters, candidates: List<SearchCandidate>): SearchUiState {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty() && !filters.isActive) return SearchUiState()

    val matched = candidates.filter { candidate ->
        filters.matches(candidate) && terms.all { candidate.matches(it) }
    }
    val groupSizes = candidates.mapNotNull { it.detail.splitGroupId }.groupingBy { it }.eachCount()
    val matchedSizes = matched.mapNotNull { it.detail.splitGroupId }.groupingBy { it }.eachCount()
    val wholeGroups = matchedSizes.filter { (groupId, count) -> count == groupSizes[groupId] }.keys

    val details = matched.map { candidate ->
        val detail = candidate.detail
        if (detail.splitGroupId != null && detail.splitGroupId !in wholeGroups) {
            detail.copy(splitGroupId = null)
        } else {
            detail
        }
    }
    val payments = details.collapseSplits()
    return SearchUiState(
        searched = true,
        count = payments.size,
        summary = details.toSummary(),
        groups = payments.toDayGroups(),
        splitParts = details.splitPartsByGroup(),
    )
}

private fun SearchCandidate.matches(term: String): Boolean {
    val fields = listOf(
        detail.merchant,
        detail.memo,
        detail.categoryName,
        parentCategoryName,
        detail.accountName,
        detail.toAccountName,
    )
    if (fields.any { it?.lowercase()?.contains(term) == true }) return true
    // "5000", "5,000", "5,000원" 모두 금액으로 본다. 일부만 맞는 것(55,000)은 섞이면 헷갈려서 정확히 같을 때만.
    val digits = term.replace(",", "").removeSuffix("원")
    return digits.isNotEmpty() && digits.all { it.isDigit() } && digits.toLongOrNull() == detail.amount
}
