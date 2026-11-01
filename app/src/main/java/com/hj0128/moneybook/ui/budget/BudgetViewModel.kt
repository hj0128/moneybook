package com.hj0128.moneybook.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.BudgetSummary
import com.hj0128.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

class BudgetViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())

    /** 달마다 예산을 따로 정하고, 지난달을 넘겨 보며 지켰는지 확인한다. */
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<BudgetSummary?> = _month
        .flatMapLatest { repository.observeBudget(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showPrevious() {
        _month.update { it.minusMonths(1) }
    }

    fun showNext() {
        _month.update { it.plusMonths(1) }
    }

    /** 보고 있는 달부터의 예산을 정한다. */
    fun setBudget(categoryId: Long, amount: Long?) {
        val month = _month.value
        viewModelScope.launch { repository.setCategoryBudget(categoryId, month, amount) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BudgetViewModel(appContainer().ledgerRepository) }
        }
    }
}
