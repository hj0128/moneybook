package com.hj0128.moneybook.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.ui.ledger.DayHeader
import com.hj0128.moneybook.ui.ledger.TransactionDivider
import com.hj0128.moneybook.ui.ledger.TransactionRow
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatAmount
import com.hj0128.moneybook.util.formatListHeader
import com.hj0128.moneybook.util.formatWon
import java.time.LocalDate

/**
 * 내역 검색. 달을 넘기며 찾지 않고 모든 기간에서 한 번에 찾는다.
 *
 * "넷플릭스 언제부터 냈지", "이 가게에 올해 얼마 썼지"처럼 기간을 모르는 채로 찾는 경우가 많다.
 * 그래서 기간을 고르게 하지 않고, 찾은 것의 합계를 위에 보여준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by viewModel.incomeCategories.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // 검색하러 들어온 화면이니 바로 칠 수 있게 한다. 내역을 열었다 돌아왔을 때는
    // 결과를 보던 중이라 키보드가 다시 튀어나오면 거슬린다.
    LaunchedEffect(Unit) {
        if (query.isEmpty() && !filters.isActive) focusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::updateQuery,
                        placeholder = { Text("거래처, 메모, 카테고리, 금액") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        // 결과는 치는 동안 이미 나와 있다. 검색 키는 키보드를 내려 결과를 넓게 보여주는 일만 한다.
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.updateQuery("")
                                    focusRequester.requestFocus()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "검색어 지우기")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            SearchFilterBar(
                filters = filters,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories,
                actions = remember(viewModel) {
                    SearchFilterActions(
                        onTypeChange = viewModel::setType,
                        onToggleCategory = viewModel::toggleCategory,
                        onClearCategories = viewModel::clearCategories,
                        onAmountChange = viewModel::setAmount,
                        onToggleMonth = viewModel::toggleMonth,
                        onAddMonths = viewModel::addMonths,
                        onRemoveMonths = viewModel::removeMonths,
                        onClearMonths = viewModel::clearMonths,
                        onClearAll = viewModel::clearFilters,
                    )
                },
                // 필터를 고르는 동안 키보드가 시트를 가리지 않게 내린다.
                onOpen = { focusManager.clearFocus() },
            )
            when {
                !state.searched -> SearchMessage(
                    "거래처, 메모, 카테고리, 결제수단, 금액으로 모든 기간의 내역을 찾습니다.\n" +
                        "띄어 쓰면 모두 들어간 것만 찾습니다. (예: 스타벅스 카드)\n\n" +
                        "검색어 없이 위의 기간·카테고리·금액만 골라도 찾습니다."
                )

                state.groups.isEmpty() -> SearchMessage(
                    if (query.isBlank()) "고른 조건에 맞는 내역이 없습니다."
                    else "'${query.trim()}'에 맞는 내역이 없습니다."
                )

                else -> SearchResults(state = state, onOpenTransaction = onOpenTransaction)
            }
        }
    }
}

@Composable
private fun SearchResults(state: SearchUiState, onOpenTransaction: (Long) -> Unit) {
    val thisYear = LocalDate.now().year
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ResultSummary(state) }
        state.groups.forEach { group ->
            item(key = "header-${group.date}") {
                // 여러 해가 섞이므로 올해가 아니면 연도를 붙인다. 올해 것까지 붙이면 머리글이 길어지기만 한다.
                val label = group.date.formatListHeader()
                DayHeader(group, if (group.date.year == thisYear) label else "${group.date.year}년 $label")
            }
            items(group.items, key = { it.id }) { item ->
                TransactionRow(
                    item = item,
                    parts = state.partsOf(item),
                    // 지우기는 내역을 열어서 한다. 찾다가 길게 눌러 잘못 지우면 어디 있던 건지 다시 찾기 어렵다.
                    onLongClick = {},
                    onClick = { onOpenTransaction(item.id) },
                )
                if (item.id != group.items.last().id) TransactionDivider()
            }
        }
    }
}

/** 찾은 건수와 합계. "이 가게에 얼마 썼지"의 답이 여기 있다. */
@Composable
private fun ResultSummary(state: SearchUiState) {
    val ledgerColors = LocalLedgerColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "${state.count.toLong().formatAmount()}건",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // 0원인 쪽은 적지 않는다. 지출만 찾았는데 "수입 0원"이 붙으면 읽을 것만 늘어난다.
            if (state.summary.income > 0) {
                Text(
                    text = "수입 ${state.summary.income.formatWon()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = ledgerColors.income,
                )
            }
            if (state.summary.expense > 0) {
                Text(
                    text = "지출 ${state.summary.expense.formatWon()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = ledgerColors.expense,
                )
            }
        }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
    )
}
