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

        if (state.isEmpty) {
            EmptyPlaceholder("이 달의 내역이 없습니다.\n오른쪽 아래 + 버튼으로 추가해 보세요.")
        } else {
            SortToggle(
                sortOrder = state.sortOrder,
                onToggle = viewModel::toggleDaySortOrder,
            )
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
