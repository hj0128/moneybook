package com.hj0128.moneybook.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.model.RecurringDetail
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatFull
import com.hj0128.moneybook.util.formatWon

/**
 * 고정 지출 목록. 매달 정한 날에 자동으로 들어가는 것들을 한곳에서 본다.
 *
 * 추가와 수정은 내역 입력 화면을 그대로 쓴다. 고르는 칸(카테고리, 결제수단, 키패드)이 똑같은데
 * 화면을 따로 만들면 같은 것을 두 번 손봐야 하고, 둘이 조금씩 달라진다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringViewModel = viewModel(factory = RecurringViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = { Text("고정 지출") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "고정 지출 추가")
            }
        },
    ) { innerPadding ->
        if (state.loaded && state.items.isEmpty()) {
            EmptyRecurring(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            // 마지막 줄이 FAB 에 가리지 않게.
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            val sections = state.sections
            if (sections.isNotEmpty()) {
                item { TotalCard(sections) }
            }
            sections.forEach { section ->
                item(key = "header-${section.unit}") { SectionHeader(section.unit.label) }
                items(section.items, key = { it.id }) { item ->
                    RecurringRow(item = item, onClick = { onOpen(item.id) })
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 72.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRecurring(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("등록된 고정 지출이 없습니다.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "월세, 통신비, 자동차보험처럼 정해진 날마다 나가는 돈을 등록해 두면 그날 자동으로 내역에 들어갑니다. " +
                "매주·매월·매년 중에 고를 수 있고, 월급이나 적금 이체도 됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 주기마다 고정으로 얼마가 나가고 들어오는지. 목록을 다 더해 보지 않아도 되게. */
@Composable
private fun TotalCard(sections: List<RecurringSection>) {
    val ledgerColors = LocalLedgerColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sections.forEach { section ->
                val spoken = section.unit.spokenLabel
                if (section.expense > 0) {
                    TotalLine("$spoken 나가는 돈", "-${section.expense.formatWon()}", ledgerColors.expense)
                }
                if (section.income > 0) {
                    TotalLine("$spoken 들어오는 돈", "+${section.income.formatWon()}", ledgerColors.income)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun TotalLine(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun RecurringRow(item: RecurringDetail, onClick: () -> Unit) {
    val ledgerColors = LocalLedgerColors.current
    val (amountText, amountColor) = when (item.type) {
        TransactionType.INCOME -> "+${item.amount.formatWon()}" to ledgerColors.income
        TransactionType.EXPENSE -> "-${item.amount.formatWon()}" to ledgerColors.expense
        TransactionType.TRANSFER -> item.amount.formatWon() to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 매달 며칠인지가 이 목록의 순서이자 가장 먼저 찾는 값이라 맨 앞에 크게 둔다.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    item.categoryColor?.let { Color(it).copy(alpha = 0.18f) }
                        ?: MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.repeatUnit.badge(item.anchor),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.memo.isNotBlank()) {
                Text(
                    text = item.memo,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "다음 ${item.nextDate.formatFull()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = amountText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
        )
    }
}
