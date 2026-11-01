package com.hj0128.moneybook.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.model.RecurringDetail
import com.hj0128.moneybook.data.model.RepeatUnit
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RecurringUiState(
    /** 처음 읽기 전에는 빈 화면 안내를 띄우지 않는다. 잠깐 "등록된 것이 없습니다"가 비쳤다 사라지면 어색하다. */
    val loaded: Boolean = false,
    val items: List<RecurringDetail> = emptyList(),
) {
    /**
     * 주기별로 묶은 목록. 짧은 주기부터(매주 → 매월 → 매년) 둔다.
     * 따로 설명하지 않아도 순서의 규칙이 보이고, 입력 화면의 반복 메뉴와 같은 순서다.
     *
     * 주기를 섞어 한 줄로 세우면 "15일"과 "3/15"와 "수"가 나란히 놓여 순서를 읽을 수 없다.
     */
    val sections: List<RecurringSection>
        get() = RepeatUnit.entries.mapNotNull { unit ->
            items.filter { it.repeatUnit == unit }
                .sortedBy { it.sortKey }
                .takeIf { it.isNotEmpty() }
                ?.let { RecurringSection(unit, it) }
        }
}

data class RecurringSection(
    val unit: RepeatUnit,
    val items: List<RecurringDetail>,
) {
    /**
     * 주기마다 나가는 돈. 이체는 내 자산 사이에서 옮겨지는 것이라 더하지 않는다.
     *
     * 매년·매주를 매달로 환산해 한데 더하지 않는다. 매주는 달마다 네 번일 때도 다섯 번일 때도 있고,
     * 매년 60만원을 "매달 5만원"으로 적으면 실제로 나가는 달에 놀란다.
     */
    val expense: Long get() = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    val income: Long get() = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
}

class RecurringViewModel(repository: LedgerRepository) : ViewModel() {

    val state: StateFlow<RecurringUiState> = repository.observeRecurring()
        .map { RecurringUiState(loaded = true, items = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecurringUiState(),
        )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { RecurringViewModel(appContainer().ledgerRepository) }
        }
    }
}
