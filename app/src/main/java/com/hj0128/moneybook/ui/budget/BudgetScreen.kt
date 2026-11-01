package com.hj0128.moneybook.ui.budget

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.model.BudgetRow
import com.hj0128.moneybook.data.model.BudgetSummary
import com.hj0128.moneybook.ui.common.UsageGauge
import com.hj0128.moneybook.util.formatShortWon
import com.hj0128.moneybook.util.formatWon
import java.time.YearMonth

/**
 * 예산. 대분류 지출 카테고리마다 한 달 예산을 정하고, 그 달에 얼마나 썼는지 본다.
 *
 * 모든 카테고리를 늘어놓고 예산이 없는 것도 보여준다. 예산을 정하는 곳이 여기라서
 * 정한 것만 보이면 새로 정할 길이 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory),
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = { Text("예산") },
            )
        },
    ) { innerPadding ->
        val current = summary ?: return@Scaffold
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "month") {
                MonthSelector(month, onPrevious = viewModel::showPrevious, onNext = viewModel::showNext)
            }
            item(key = "summary") { SummaryCard(current) }
            item(key = "gap") { Spacer(Modifier.height(8.dp)) }
            // 카테고리 관리에서 정한 순서 그대로. 예산이 생기고 없어질 때마다 줄이 옮겨 다니면 찾던 줄을 놓친다.
            items(current.rows, key = { it.category.id }) { row ->
                BudgetRowItem(row = row, onClick = { editing = row })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 38.dp),
                )
            }
        }
    }

    editing?.let { row ->
        BudgetDialog(
            row = row,
            month = month,
            onSave = { amount ->
                viewModel.setBudget(row.category.id, amount)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
        }
        Text(
            text = "${month.year}년 ${month.monthValue}월",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
        }
    }
}

/** 그 달 예산 전체. 아래 줄들을 더한 값이다(예산이 없는 카테고리는 예산 0원). */
@Composable
private fun SummaryCard(summary: BudgetSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${summary.month.monthValue}월 예산 ${summary.totalBudget.formatWon()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            UsageGauge(
                periodLabel = "사용",
                used = summary.totalSpent,
                limit = summary.totalBudget,
                color = MaterialTheme.colorScheme.primary,
                accessibilityName = "${summary.month.monthValue}월 예산",
                // 카드 바탕이 막대 바탕과 같은 색이라 그대로 두면 막대 길이를 가늠할 수 없다.
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun BudgetRowItem(row: BudgetRow, onClick: () -> Unit) {
    val color = Color(row.category.colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "${row.category.name} 예산 정하기", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        // 예산이 없는 카테고리도 0원으로 같은 모양에 그린다. 줄마다 모양이 다르면 훑어보기 어렵다.
        // 위에 달이 이미 적혀 있어 줄마다 "11월"을 다시 붙이지 않는다.
        UsageGauge(
            periodLabel = "",
            used = row.spent,
            limit = row.budget ?: 0L,
            color = color,
            name = row.category.name,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 예산 금액 적기. 칸에는 지금 이 달에 적용되는 금액(대개 지난달에 정한 것)이 미리 들어 있다.
 * 이 달에 지금까지 쓴 돈을 함께 보여줘 얼마로 정할지 가늠하게 한다.
 */
@Composable
private fun BudgetDialog(
    row: BudgetRow,
    month: YearMonth,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(row.budget?.toString().orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    // 예산을 적으러 누른 것이니 바로 칠 수 있게 한다.
    // 다이얼로그 창이 붙기 전에 부르면 포커스가 들어가지 않는다. 한 프레임 기다린다.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${month.monthValue}월 ${row.category.name} 예산") },
        text = {
            Column {
                Text(
                    text = "이 달에 지금까지 ${row.spent.formatWon()} 썼습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                // 바꾸면 다음 달에도 이어진다는 것을 미리 알려준다. 이 달만 바꾸고 싶으면 다음 달에서 되돌리면 된다.
                Text(
                    text = "${month.monthValue}월부터 적용됩니다. 그 뒤 달도 따로 정하지 않으면 이 금액이 이어집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    // 앞에 붙은 0은 버린다("00500000"처럼 남으면 금액을 잘못 읽는다).
                    onValueChange = { input -> text = input.filter { it.isDigit() }.trimStart('0').take(12) },
                    label = { Text("예산") },
                    suffix = { Text("원") },
                    // 0이 여럿이면 자릿수를 잘못 세기 쉽다. "50만원"처럼 읽어 준다.
                    supportingText = { text.toLongOrNull()?.takeIf { it > 0 }?.let { Text(it.formatShortWon()) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.toLongOrNull()) },
                enabled = (text.toLongOrNull() ?: 0L) > 0L,
            ) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (row.budget != null) {
                    TextButton(onClick = { onSave(null) }) { Text("예산 없애기") }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
