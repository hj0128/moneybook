package com.hj0128.moneybook.ui.entry

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatAmount
import com.hj0128.moneybook.util.formatFull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val entryTypes = listOf(
    TransactionType.EXPENSE,
    TransactionType.INCOME,
    TransactionType.TRANSFER,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onManageCategories: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntryViewModel = viewModel(factory = EntryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    // 저장이 끝나면 화면을 닫는다.
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("내역 추가") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                TypeSelector(
                    selected = state.type,
                    onSelect = viewModel::selectType,
                )
                AmountDisplay(state = state)
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                DateRow(date = state.date, onClick = { showDatePicker = true })

                if (state.isTransfer) {
                    SectionLabel("출금 자산")
                    AccountChips(
                        state = state,
                        selectedId = state.selectedAccountId,
                        onSelect = viewModel::selectAccount,
                        disabledId = state.selectedToAccountId,
                    )
                    SectionLabel("입금 자산")
                    AccountChips(
                        state = state,
                        selectedId = state.selectedToAccountId,
                        onSelect = viewModel::selectToAccount,
                        disabledId = state.selectedAccountId,
                    )
                } else {
                    SectionLabel("카테고리")
                    CategoryChips(
                        nodes = state.categoryNodes,
                        selectedId = state.selectedCategoryId,
                        onSelect = viewModel::selectCategory,
                        onManage = onManageCategories,
                    )
                    SectionLabel("자산")
                    AccountChips(
                        state = state,
                        selectedId = state.selectedAccountId,
                        onSelect = viewModel::selectAccount,
                        disabledId = null,
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = viewModel::updateMemo,
                    label = { Text("메모") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }

            Keypad(
                onDigit = viewModel::appendDigit,
                onZeros = viewModel::appendZeros,
                onBackspace = viewModel::deleteLastDigit,
                onClear = viewModel::clearAmount,
                onSave = viewModel::save,
                saveEnabled = state.canSave,
            )
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initialDate = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.selectDate(it)
                showDatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        entryTypes.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, entryTypes.size),
            ) {
                Text(type.label)
            }
        }
    }
}

@Composable
private fun AmountDisplay(state: EntryUiState) {
    val ledgerColors = LocalLedgerColors.current
    val color = when (state.type) {
        TransactionType.INCOME -> ledgerColors.income
        TransactionType.EXPENSE -> ledgerColors.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = state.amount.formatAmount(),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = if (state.amount == 0L) MaterialTheme.colorScheme.outline else color,
        )
        Spacer(Modifier.padding(horizontal = 2.dp))
        Text(
            text = "원",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun DateRow(date: LocalDate, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(date.formatFull())
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

/**
 * 카테고리 칩.
 *
 * 2뎁스지만 고르는 건 언제나 탭 한 번이다. 대분류를 누르고 소분류를 또 누르게 하면
 * 입력이 두 배로 귀찮아지는데, 가계부는 입력이 귀찮아지는 순간 안 쓰게 된다.
 * 그래서 소분류를 전부 펼쳐 놓고 대분류는 머리글로만 쓴다.
 *
 * 소분류가 없는 대분류는 머리글 없이 한 줄에 모은다. 머리글 하나에 칩 하나씩
 * 늘어놓으면 세로로 길어지기만 하고 묶어주는 값어치가 없다.
 */
@Composable
private fun CategoryChips(
    nodes: List<CategoryNode>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onManage: () -> Unit,
) {
    val grouped = nodes.filter { it.children.isNotEmpty() }
    val standalone = nodes.filter { it.children.isEmpty() }.map { it.category }

    grouped.forEach { node ->
        Text(
            text = node.category.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 소분류가 있으면 소분류만 고른다. 대분류는 머리글 역할만 한다.
            node.selectable.forEach { category ->
                CategoryChip(category, category.id == selectedId, onSelect)
            }
        }
    }

    if (standalone.isNotEmpty() || grouped.isNotEmpty()) {
        if (grouped.isNotEmpty()) Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            standalone.forEach { category ->
                CategoryChip(category, category.id == selectedId, onSelect)
            }
            // 쓰려던 카테고리가 없을 때 여기서 바로 만들러 갈 수 있다.
            AssistChip(
                onClick = onManage,
                label = { Text("관리") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.height(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: CategoryEntity,
    selected: Boolean,
    onSelect: (Long) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(category.id) },
        label = { Text(category.name) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(category.colorArgb).copy(alpha = 0.25f),
        ),
    )
}

@Composable
private fun AccountChips(
    state: EntryUiState,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    disabledId: Long?,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.accounts.forEach { account ->
            FilterChip(
                selected = account.id == selectedId,
                onClick = { onSelect(account.id) },
                enabled = account.id != disabledId,
                label = { Text(account.name) },
            )
        }
    }
}

/**
 * 숫자 키패드.
 *
 * 가계부에서 가장 자주 쓰는 화면이므로 시스템 키보드 대신 큰 버튼을 직접 그린다.
 * (시스템 키보드는 뜨고 지는 사이 화면이 흔들리고, 숫자 외 키가 섞여 오입력이 난다.)
 */
@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onZeros: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { digit ->
                        KeypadKey(text = digit.toString(), modifier = Modifier.weight(1f)) {
                            onDigit(digit)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeypadKey(text = "000", modifier = Modifier.weight(1f)) { onZeros(3) }
                KeypadKey(text = "0", modifier = Modifier.weight(1f)) { onDigit('0') }
                KeypadKey(
                    text = null,
                    modifier = Modifier.weight(1f),
                    onLongClick = onClear,
                    onClick = onBackspace,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("저장", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadKey(
    text: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                // 지우기 키는 길게 누르면 전체 삭제
                onLongClickLabel = "전체 지우기",
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        } else {
            Icon(
                Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "한 자리 지우기",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // DatePicker 는 UTC 자정 기준 millis 를 쓴다. 로컬 타임존으로 변환하면 하루가 밀린다.
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * MILLIS_PER_DAY,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        onConfirm(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    } else {
                        onDismiss()
                    }
                }
            ) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
