package com.hj0128.moneybook.ui.ledger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.model.TransactionType
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onAddTransaction: () -> Unit,
    onOpenCategory: (categoryId: Long, yearMonth: YearMonth) -> Unit,
    onOpenUncategorized: (type: TransactionType, yearMonth: YearMonth) -> Unit,
    onManageCategories: () -> Unit,
    onManageAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerViewModel = viewModel(
        factory = LedgerViewModel.factory(YearMonth.now())
    ),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val yearMonth by viewModel.yearMonth.collectAsStateWithLifecycle()
    val categoryType by viewModel.categoryType.collectAsStateWithLifecycle()
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    PeriodSelector(
                        // 월별 탭은 한 해를 통째로 보여주므로 제목도 연도만 쓴다.
                        label = when (tab) {
                            LedgerTab.MONTHLY -> "${yearMonth.year}년"
                            else -> "${yearMonth.year}년 ${yearMonth.monthValue}월"
                        },
                        onPrevious = viewModel::showPrevious,
                        onNext = viewModel::showNext,
                    )
                },
                actions = {
                    OverflowMenu(
                        onManageCategories = onManageCategories,
                        onManageAccounts = onManageAccounts,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Default.Add, contentDescription = "거래 추가")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = tab.ordinal) {
                LedgerTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { viewModel.selectTab(entry) },
                        text = { Text(entry.label) },
                    )
                }
            }

            when (tab) {
                LedgerTab.DAILY -> DailyTab(
                    viewModel = viewModel,
                    onRequestDelete = { pendingDeleteId = it },
                )

                LedgerTab.MONTHLY -> MonthlyTab(viewModel = viewModel)

                LedgerTab.CATEGORY -> CategoryTab(
                    viewModel = viewModel,
                    // 상단에서 고른 달을 그대로 넘긴다. 상세는 그 달에 고정된다.
                    onOpenCategory = { categoryId -> onOpenCategory(categoryId, yearMonth) },
                    onOpenUncategorized = { onOpenUncategorized(categoryType, yearMonth) },
                )
            }
        }
    }

    pendingDeleteId?.let { id ->
        DeleteTransactionDialog(
            onConfirm = {
                viewModel.delete(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

/** 설정거리가 관리 화면 둘뿐이라 설정 화면을 따로 두지 않고 여기에 단다. */
@Composable
private fun OverflowMenu(
    onManageCategories: () -> Unit,
    onManageAccounts: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text("카테고리 관리") },
            onClick = {
                expanded = false
                onManageCategories()
            },
        )
        DropdownMenuItem(
            text = { Text("자산 관리") },
            onClick = {
                expanded = false
                onManageAccounts()
            },
        )
    }
}

@Composable
private fun PeriodSelector(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전")
        }
        Text(text = label, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음")
        }
    }
}
