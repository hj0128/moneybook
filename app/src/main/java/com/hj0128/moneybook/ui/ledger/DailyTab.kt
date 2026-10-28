package com.hj0128.moneybook.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hj0128.moneybook.data.model.DaySortOrder

/** 일별 탭: 한 달을 날짜별로 묶어 보여준다. */
@Composable
fun DailyTab(
    viewModel: LedgerViewModel,
    onRequestDelete: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.dailyState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        SummaryCard(summary = state.summary)

        if (!state.hasAnyInMonth) {
            EmptyPlaceholder("이 달의 내역이 없습니다.\n오른쪽 아래 + 버튼으로 추가해 보세요.")
        } else {
            // return@Column 으로 일찍 빠져나가면 안 된다. 첫 화면(빈 상태)에서 빠져나간 뒤
            // 데이터가 들어와 다시 그릴 때 아래 줄이 제대로 구성되지 않는다.
            FilteredList(
                state = state,
                onSelectFilter = viewModel::selectDailyFilter,
                onToggleSort = viewModel::toggleDaySortOrder,
                onRequestDelete = onRequestDelete,
                onOpenTransaction = onOpenTransaction,
            )
        }
    }
}

@Composable
private fun FilteredList(
    state: DailyUiState,
    onSelectFilter: (DailyFilter) -> Unit,
    onToggleSort: () -> Unit,
    onRequestDelete: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterSelector(
                selected = state.filter,
                onSelect = onSelectFilter,
            )
            Spacer(Modifier.weight(1f))
            SortToggle(
                sortOrder = state.sortOrder,
                onToggle = onToggleSort,
            )
        }

        if (state.isEmpty) {
            // 필터를 풀면 내역이 있다는 걸 알려야 한다. 그냥 "없음"이면 데이터가 날아간 줄 안다.
            EmptyPlaceholder("이 달에는 ${state.filter.label} 내역이 없습니다.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                state.groups.forEach { group ->
                    item(key = "header-${group.date}") {
                        DayHeader(group)
                    }
                    items(group.items, key = { it.id }) { item ->
                        TransactionRow(
                            item = item,
                            onLongClick = { onRequestDelete(item.id) },
                            onClick = { onOpenTransaction(item.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 전체/수입/지출 중 하나만 고른다.
 *
 * 분할 버튼은 남는 폭을 나눠 갖는 방식이라 정렬 버튼과 한 줄에 두면 폭을 못 받아 찌그러진다.
 * 칩은 글자만큼만 차지해서 옆에 무엇이 와도 모양이 그대로다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSelector(
    selected: DailyFilter,
    onSelect: (DailyFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DailyFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

/**
 * 정렬 방향 토글.
 *
 * 정렬 기준이 날짜 하나뿐이라 선택지를 늘어놓는 대신 한 번 눌러 뒤집는 버튼으로 뒀다.
 * 지금 어떤 순서인지 글자로 계속 보이므로 눌러보지 않아도 상태를 알 수 있다.
 */
@Composable
private fun SortToggle(
    sortOrder: DaySortOrder,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.semantics {
                contentDescription = "날짜 정렬 ${sortOrder.label}. 눌러서 ${sortOrder.toggled().label}으로 변경"
            },
        ) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = sortOrder.label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun EmptyPlaceholder(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
