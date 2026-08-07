package com.hj0128.moneybook.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CategoryDetailUiState(
    val yearMonth: YearMonth,
    val categoryName: String = "",
    val categoryColor: Int? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val groups: List<DayGroup> = emptyList(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()

    val entryCount: Int get() = groups.sumOf { it.items.size }

    val total: Long get() = groups.sumOf { group -> group.items.sumOf { it.amount } }

    /**
     * 하루 평균. 거래가 있었던 날이 아니라 그 달의 전체 일수로 나눈다.
     *
     * "쓴 날만" 기준으로 하면 이틀에 10만원씩 두 번 쓴 달이 하루 평균 10만원으로 나와
     * 실제 한 달 씀씀이보다 과장돼 보인다. 달력 기준이어야 다른 달과 비교가 된다.
     */
    val dailyAverage: Long get() = total / yearMonth.lengthOfMonth()
}

class CategoryDetailViewModel(
    private val repository: LedgerRepository,
    private val categoryId: Long,
    yearMonth: YearMonth,
) : ViewModel() {

    val state: StateFlow<CategoryDetailUiState> =
        combine(
            repository.observeCategoryTransactions(yearMonth, categoryId),
            repository.observeCategory(categoryId),
        ) { items, category ->
            CategoryDetailUiState(
                yearMonth = yearMonth,
                // 카테고리가 지워지면 이름을 못 읽는다. 화면이 비지 않게 기본값을 둔다.
                categoryName = category?.name ?: "삭제된 카테고리",
                categoryColor = category?.colorArgb,
                type = category?.type ?: TransactionType.EXPENSE,
                groups = items.toDayGroups(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryDetailUiState(yearMonth),
        )

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }

    companion object {
        fun factory(categoryId: Long, yearMonth: YearMonth): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CategoryDetailViewModel(
                        repository = appContainer().ledgerRepository,
                        categoryId = categoryId,
                        yearMonth = yearMonth,
                    )
                }
            }
    }
}
