package com.hj0128.moneybook.ui.entry

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.AccountBalance
import com.hj0128.moneybook.data.model.AccountNode
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.RepeatUnit
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.model.toAccountNodes
import com.hj0128.moneybook.ui.theme.LocalLedgerColors
import com.hj0128.moneybook.util.formatAmount
import com.hj0128.moneybook.util.formatFull
import com.hj0128.moneybook.util.formatWon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val entryTypes = listOf(
    TransactionType.EXPENSE,
    TransactionType.INCOME,
    TransactionType.TRANSFER,
)

/** 라벨 칸 너비. 값이 어느 줄에서나 같은 자리에서 시작해야 눈으로 훑기 쉽다. */
private val LabelWidth = 88.dp

/**
 * 같은 칸이라도 종류에 따라 부르는 이름이 다르다.
 * 수입에 "결제수단"이라고 써 두면 무엇을 고르라는 건지 알 수 없다.
 */
private val TransactionType.accountLabel: String
    get() = if (this == TransactionType.INCOME) "입금 자산" else "결제수단"

/** 어느 자산 칸을 고르는 중인지. 이체는 출금/입금 두 칸을 쓴다. */
private enum class AccountTarget { FROM, TO }

/** 열린 선택 시트. [partIndex] 가 있으면 나눠서 입력 중인 그 줄의 값을 고른다. */
private sealed interface EntrySheet {
    data class Category(val partIndex: Int? = null) : EntrySheet
    data class Account(
        val target: AccountTarget,
        val title: String,
        val partIndex: Int? = null,
    ) : EntrySheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    editingId: Long? = null,
    recurringId: Long? = null,
    startRepeating: Boolean = false,
    onOpenRecurring: (Long) -> Unit = {},
    viewModel: EntryViewModel = viewModel(
        // 고치는 내역이 바뀌면 다른 ViewModel 이어야 한다. key 를 안 주면 같은 것을 재사용해
        // 먼저 열었던 내역이 그대로 남는다.
        key = editingId?.let { "entry-$it" } ?: recurringId?.let { "recurring-$it" },
        factory = EntryViewModel.factory(editingId, recurringId, startRepeating),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var showDatePicker by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<EntrySheet?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 금액 말고 다른 것을 만지기 시작하면 숫자 키패드도 시스템 키보드도 자리를 비켜준다.
    val dismissInputs = {
        focusManager.clearFocus()
        viewModel.hideKeypad()
    }

    // 저장이나 삭제가 끝나면 화면을 닫는다.
    LaunchedEffect(state.isSaved, state.isDeleted) {
        if (state.isSaved || state.isDeleted) onSaved()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.isEditingRecurring -> "고정 지출 수정"
                            state.isEditing -> "내역 수정"
                            // 반복을 켜면 저장되는 것이 달라진다. 제목도 따라 바꿔 무엇을 만드는지 알린다.
                            state.repeatUnit != null -> "고정 지출 추가"
                            else -> "내역 추가"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                },
                actions = {
                    // 목록에서 길게 눌러 지우는 길만 있으면 찾기 어렵다. 여기에도 둔다.
                    if (state.isEditing || state.isEditingRecurring) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = if (state.isEditingRecurring) "고정 지출 그만두기" else "이 내역 삭제",
                            )
                        }
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
                    // 빈 곳을 누르면 열려 있던 키패드와 키보드를 닫는다. 줄이나 입력칸 위의
                    // 탭은 그쪽에서 먼저 먹으므로 여기까지 오지 않는다.
                    .pointerInput(Unit) { detectTapGestures { dismissInputs() } },
            ) {
                TypeSelector(
                    selected = state.type,
                    onSelect = { dismissInputs(); viewModel.selectType(it) },
                )
                AmountDisplay(
                    state = state,
                    // 메모를 적다가 금액을 누르면 시스템 키보드는 내려가야 한다.
                    onClick = { focusManager.clearFocus(); viewModel.showKeypad() },
                )
                Spacer(Modifier.height(8.dp))

                // 줄들을 카드 하나로 묶는다. 화면 끝까지 그어진 실선은 값과 라벨을
                // 나누는 게 아니라 화면을 잘라 보이게 해서, 입력 칸이 여러 덩어리로 읽힌다.
                RowCard {
                    // 나눠서 입력 중이면 카테고리와 결제수단은 줄마다 따로 고르므로 아래 칸으로 옮겨 간다.
                    if (!state.isTransfer && !state.isSplit) {
                        PickerRow(
                            label = "카테고리",
                            value = state.selectedCategory?.label,
                            colorArgb = state.selectedCategory?.category?.colorArgb,
                            onClick = { dismissInputs(); sheet = EntrySheet.Category() },
                        )
                    }
                    if (!state.isTransfer) {
                        InputRow(
                            label = "거래처",
                            value = state.merchant,
                            onValueChange = viewModel::updateMerchant,
                            onFocused = viewModel::hideKeypad,
                        )
                    }

                    // 나눠서 입력 중이면 메모는 줄마다 따로 적는다.
                    if (!state.isSplit) {
                        InputRow(
                            label = "메모",
                            value = state.memo,
                            onValueChange = viewModel::updateMemo,
                            onFocused = viewModel::hideKeypad,
                        )
                    }

                    PickerRow(
                        // 고정 지출을 고칠 때 날짜는 언제 썼는지가 아니라 언제 넣을지다.
                        label = if (state.isEditingRecurring) "다음 날짜" else "날짜",
                        value = state.date.formatFull(),
                        divider = !state.isSplit,
                        onClick = { dismissInputs(); showDatePicker = true },
                    )

                    // 이미 있는 내역을 고칠 때는 고르지 않는다. 그 내역 한 줄을 고치는 화면이라
                    // 여기서 반복을 켜면 무엇이 생기는지 알기 어렵다. 나눈 결제도 반복하지 않는다.
                    if (!state.isEditing && !state.isSplit) {
                        RepeatRow(
                            selected = state.repeatUnit,
                            anchor = state.repeatAnchor,
                            date = state.date,
                            // 고정 지출을 고치는 중에 "안 함"을 고르면 무엇이 되는지 애매하다. 그만두기는 삭제 버튼으로 한다.
                            allowNone = !state.isEditingRecurring,
                            onOpen = dismissInputs,
                            onSelect = viewModel::setRepeatUnit,
                        )
                    }

                    if (state.isTransfer) {
                        PickerRow(
                            label = "출금 자산",
                            value = state.selectedAccountName,
                            onClick = {
                                dismissInputs()
                                sheet = EntrySheet.Account(AccountTarget.FROM, "출금 자산")
                            },
                        )
                        PickerRow(
                            label = "입금 자산",
                            value = state.selectedToAccountName,
                            divider = false,
                            onClick = {
                                dismissInputs()
                                sheet = EntrySheet.Account(AccountTarget.TO, "입금 자산")
                            },
                        )
                    } else if (!state.isSplit) {
                        PickerRow(
                            label = state.type.accountLabel,
                            value = state.selectedAccountName,
                            divider = false,
                            onClick = {
                                dismissInputs()
                                sheet = EntrySheet.Account(
                                    AccountTarget.FROM,
                                    state.type.accountLabel,
                                )
                            },
                        )
                    }
                }

                state.sourceRecurring?.let { source ->
                    RecurringNotice(
                        label = source.label,
                        onOpen = { onOpenRecurring(source.id) },
                    )
                }

                if (state.repeatUnit != null && !state.isEditing) {
                    RepeatHint(state)
                }

                if (!state.isTransfer && !state.savesRecurring) {
                    if (state.isSplit) {
                        Spacer(Modifier.height(16.dp))
                        SplitSection(
                            state = state,
                            onPickCategory = { index ->
                                dismissInputs()
                                sheet = EntrySheet.Category(partIndex = index)
                            },
                            onPickAccount = { index ->
                                dismissInputs()
                                sheet = EntrySheet.Account(
                                    AccountTarget.FROM,
                                    state.type.accountLabel,
                                    partIndex = index,
                                )
                            },
                            onEditAmount = { index ->
                                focusManager.clearFocus()
                                viewModel.editPartAmount(index)
                            },
                            onRemove = viewModel::removePart,
                            onMemoChange = viewModel::updatePartMemo,
                            onMemoFocused = viewModel::hideKeypad,
                            onAdd = { focusManager.clearFocus(); viewModel.addPart() },
                        )
                    } else {
                        // 한 번 긁은 결제에 여러 카테고리가 섞였거나(다이소: 면봉 + 라면),
                        // 여러 수단으로 나눠 냈을 때(체크카드 + 현금) 쓴다.
                        TextButton(
                            onClick = { focusManager.clearFocus(); viewModel.startSplit() },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Text("나눠서 입력 (카테고리·결제수단별)")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 키패드는 금액을 누를 때만. 저장은 키패드와 무관하게 항상 손에 닿아야 한다.
            if (state.keypadVisible) {
                Keypad(
                    onDigit = viewModel::appendDigit,
                    onZeros = viewModel::appendZeros,
                    onBackspace = viewModel::deleteLastDigit,
                    onClear = viewModel::clearAmount,
                )
            }
            SaveBar(onSave = viewModel::save, enabled = state.canSave)
        }
    }

    when (val open = sheet) {
        null -> Unit

        is EntrySheet.Category -> CategoryPickerSheet(
            nodes = state.categoryNodes,
            selectedId = open.partIndex?.let { state.parts.getOrNull(it)?.categoryId }
                ?: state.selectedCategoryId.takeIf { open.partIndex == null },
            onPick = {
                val index = open.partIndex
                if (index != null) viewModel.selectPartCategory(index, it) else viewModel.selectCategory(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        is EntrySheet.Account -> PickerSheet(
            title = open.title,
            onDismiss = { sheet = null },
        ) {
            AccountSheetContent(
                state = state,
                target = open.target,
                partIndex = open.partIndex,
                onPick = {
                    val index = open.partIndex
                    when {
                        index != null -> viewModel.selectPartAccount(index, it)
                        open.target == AccountTarget.FROM -> viewModel.selectAccount(it)
                        else -> viewModel.selectToAccount(it)
                    }
                    sheet = null
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    when {
                        state.isEditingRecurring -> "고정 지출을 그만둘까요?"
                        state.editingSplit -> "나눈 결제 전체를 삭제할까요?"
                        else -> "이 내역을 삭제할까요?"
                    }
                )
            },
            text = {
                Text(
                    when {
                        state.isEditingRecurring -> "다음 달부터 자동으로 들어가지 않습니다. 이미 들어간 내역은 그대로 남습니다."
                        state.editingSplit -> "나눠 저장한 줄이 모두 삭제됩니다. 되돌릴 수 없습니다."
                        else -> "삭제하면 되돌릴 수 없습니다."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            },
        )
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
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

/**
 * 금액.
 *
 * 이 화면에서 가장 중요한 값이고 눌러야 키패드가 올라오므로, 카드로 띄워
 * "여기가 중심이고 눌러도 되는 자리"임을 형태로 알린다.
 * 배경에 종류 색을 아주 연하게 깔아 지출/수입을 숫자 색 말고도 읽히게 한다.
 */
@Composable
private fun AmountDisplay(state: EntryUiState, onClick: () -> Unit) {
    val ledgerColors = LocalLedgerColors.current
    val color = when (state.type) {
        TransactionType.INCOME -> ledgerColors.income
        TransactionType.EXPENSE -> ledgerColors.expense
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClickLabel = "금액 입력", onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "${state.type.label} 금액",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = state.amount.formatAmount(),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.amount == 0L) MaterialTheme.colorScheme.outline else color,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "원",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

/** 입력 줄들을 담는 카드. */
@Composable
private fun RowCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large,
            ),
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(LabelWidth),
    )
}

/** 눌러서 고르는 줄. 값이 없으면 "선택"으로 비어 있음을 알린다. */
@Composable
private fun PickerRow(
    label: String,
    value: String?,
    colorArgb: Int? = null,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "$label 선택", onClick = onClick)
            .padding(horizontal = 16.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label)
        Spacer(Modifier.weight(1f))
        colorArgb?.let { argb ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(argb)),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = value ?: "선택",
            style = MaterialTheme.typography.bodyLarge,
            color = if (value == null) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
    if (divider) RowDivider()
}

/**
 * 반복 고르기. 안 함 / 매주 / 매월 / 매년.
 *
 * 고를 것마다 지금 날짜로 무엇이 되는지("매주 일요일", "매년 11월 1일")를 같이 보여준다.
 * "매주"만 적어 두면 무슨 요일인지 고르고 나서야 알 수 있다.
 */
@Composable
private fun RepeatRow(
    selected: RepeatUnit?,
    anchor: LocalDate,
    date: LocalDate,
    allowNone: Boolean,
    onOpen: () -> Unit,
    onSelect: (RepeatUnit?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 메뉴를 줄 바로 아래에 띄우려고 Box 로 감싼다. 줄과 구분선은 Column 에 담아야 Box 안에서 겹치지 않는다.
    Box {
        Column {
            PickerRow(
                label = "반복",
                value = selected?.describe(anchor) ?: "안 함",
                onClick = {
                    onOpen()
                    expanded = true
                },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = LabelWidth, y = 0.dp),
        ) {
            if (allowNone) {
                RepeatOption(text = "안 함", checked = selected == null) {
                    expanded = false
                    onSelect(null)
                }
            }
            RepeatUnit.entries.forEach { unit ->
                // 지금 고른 주기는 지켜 둔 기준일로, 다른 주기는 바꾸면 기준이 될 화면의 날짜로 보여준다.
                RepeatOption(
                    text = unit.describe(if (unit == selected) anchor else date),
                    checked = unit == selected,
                ) {
                    expanded = false
                    onSelect(unit)
                }
            }
        }
    }
}

@Composable
private fun RepeatOption(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, fontWeight = if (checked) FontWeight.SemiBold else null) },
        trailingIcon = {
            if (checked) Icon(Icons.Default.Check, contentDescription = "선택됨", modifier = Modifier.size(18.dp))
        },
        onClick = onClick,
    )
}

/**
 * 반복을 골랐을 때 저장하면 무엇이 일어나는지. 오늘·지난 날·앞날에 따라 달라서 미리 알려준다.
 * 지난 날이면 몇 건이 한꺼번에 들어가는지까지 센다. 1월 1일 시작 매주로 저장했다가 40건이 쏟아지면 놀란다.
 */
@Composable
private fun RepeatHint(state: EntryUiState) {
    val unit = state.repeatUnit ?: return
    val today = LocalDate.now()
    val text = when {
        state.isEditingRecurring -> "바꾼 내용은 다음 날짜부터 들어갑니다. 이미 들어간 내역은 바뀌지 않습니다."
        state.date.isAfter(today) -> "${state.date.formatFull()}부터 ${unit.describe(state.repeatAnchor)}에 자동으로 들어갑니다."
        else -> {
            val count = generateSequence(state.date) { unit.occurrenceAfter(it, state.repeatAnchor) }
                .takeWhile { !it.isAfter(today) }
                .count()
            if (count <= 1) {
                "저장하면 이번 것이 바로 들어가고, 다음부터 자동으로 들어갑니다."
            } else {
                "저장하면 ${state.date.formatFull()}부터 오늘까지 밀린 ${count}건이 한꺼번에 들어갑니다."
            }
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

/** 고정 지출이 넣은 내역을 고칠 때. 이 줄만 바뀐다는 것과, 앞으로의 것을 바꾸는 길을 같이 보여준다. */
@Composable
private fun RecurringNotice(label: String, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Autorenew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label 고정 지출로 들어간 내역입니다. 여기서 고치면 이 내역만 바뀝니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpen) { Text("고정 지출") }
    }
}

/** 카드 안쪽 구분선. 라벨 시작점에 맞춰 들여써서 카드 테두리까지 닿지 않게 한다. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp),
    )
}

/**
 * 줄 안에서 바로 적는 칸.
 *
 * OutlinedTextField 를 쓰면 테두리 때문에 줄 높이가 두 배가 되어 다른 줄과 어긋난다.
 * 값만 오른쪽에 두어 고르는 줄들과 같은 자리에 값이 오게 한다.
 */
@Composable
private fun InputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit,
    divider: Boolean = true,
) {
    // 줄 어디를 눌러도 커서가 들어와야 한다. 값 칸만 반응하면 라벨 쪽을 눌렀을 때
    // 아무 일도 일어나지 않아 고장 난 것처럼 보인다.
    //
    // requestFocus() 는 포커스만 준다. 키보드는 따로 띄워야 한다. 안 그러면
    // 커서만 깜빡이고 키보드가 안 올라와서 더 고장 난 것처럼 보인다.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 커서 위치를 직접 들고 있는다. 값만 넘기면 이미 글자가 있는 칸에 들어갔을 때
    // 커서가 맨 앞에 놓여 새로 친 글자가 앞에 끼어든다("기술서적" -> "Kotlin기술서적").
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if (field.text != value) {
        field = TextFieldValue(value, TextRange(value.length))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "$label 입력") {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .padding(horizontal = 16.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label)
        // 입력칸은 글자 폭만 차지하고, 왼쪽 빈 공간은 이 Box 가 받는다.
        // 입력칸이 남은 폭을 다 먹으면 오른쪽 정렬된 글자 왼쪽을 누른 것이
        // "맨 앞 글자 앞"으로 해석되어 새로 친 글자가 앞에 끼어든다.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    onValueChange(it.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .widthIn(min = 1.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            onFocused()
                            // 줄의 빈 곳을 눌러 들어온 경우다. 이어서 적는 게 보통이므로 끝에 둔다.
                            field = field.copy(selection = TextRange(field.text.length))
                        }
                    },
            )
        }
        // 고르는 줄의 화살표만큼 자리를 비워 값 끝이 세로로 맞는다.
        Spacer(Modifier.width(20.dp))
    }
    if (divider) RowDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // 내용은 좌우 여백 없이 넘긴다. 줄을 누르는 자리(물결)가 시트 끝까지 닿아야 줄 전체가 버튼으로 읽힌다.
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * 카테고리 선택. 왼쪽에 대분류, 오른쪽에 그 대분류에서 고를 수 있는 것.
 *
 * 예전에는 대분류 타일을 눌러 소분류 타일로 들어갔다 나오는 방식이었다. 들어가야 소분류가 보이고,
 * 다른 대분류로 가려면 ‹ 로 나왔다 다시 들어가야 했다. 두 칸으로 나누면 대분류를 바꿔 가며
 * 소분류를 바로 훑을 수 있고, 들어갔다 나오는 단계가 없다.
 *
 * 소분류가 없는 대분류는 왼쪽에서 누르면 바로 골라진다. 오른쪽에 자기 이름을 한 번 더 띄우면
 * 대분류인지 소분류인지 헷갈린다. 펼칠 것이 있는 대분류에만 › 를 달아 누르면 무엇이 일어날지
 * 미리 보이게 한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    nodes: List<CategoryNode>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // 이미 고른 소분류가 있으면 그 대분류를 펼쳐 둔다. 다시 열었을 때 어디 있었는지 바로 보인다.
    // 펼칠 수 있는 건 소분류가 있는 대분류뿐이다.
    var focusedId by remember {
        mutableStateOf(
            nodes.firstOrNull { node -> node.hasChildren && node.children.any { it.id == selectedId } }
                ?.category?.id
                ?: nodes.firstOrNull { it.hasChildren }?.category?.id
        )
    }
    val focused = nodes.firstOrNull { it.category.id == focusedId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "카테고리 선택",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.height(CategoryPaneHeight)) {
                Column(
                    modifier = Modifier
                        .width(ParentPaneWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState()),
                ) {
                    nodes.forEach { node ->
                        ParentItem(
                            node = node,
                            focused = node.category.id == focusedId,
                            holdsSelection = node.selectable.any { it.id == selectedId },
                            onClick = {
                                if (node.hasChildren) focusedId = node.category.id else onPick(node.category.id)
                            },
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    focused?.children?.forEach { category ->
                        CategoryOption(
                            category = category,
                            selected = category.id == selectedId,
                            onClick = { onPick(category.id) },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

private val CategoryPaneHeight = 420.dp
private val ParentPaneWidth = 132.dp

/**
 * 왼쪽 대분류 한 줄. 펼친 줄은 바탕을 오른쪽과 같은 색으로 칠해 오른쪽 칸과 이어져 보이게 한다.
 * 고른 것(자신 또는 그 소분류)이 있으면 체크를 단다. 다른 대분류를 보는 중에도 지금 무엇을
 * 골라 뒀는지 잃지 않는다. 펼칠 소분류가 있으면 › 를 단다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentItem(
    node: CategoryNode,
    focused: Boolean,
    holdsSelection: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) BottomSheetDefaults.ContainerColor else Color.Transparent)
            .clickable(
                onClickLabel = if (node.hasChildren) "${node.category.name} 펼치기" else "${node.category.name} 선택",
                onClick = onClick,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(node.category.colorArgb)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = node.category.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (holdsSelection) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "선택됨",
                modifier = Modifier.size(16.dp),
                tint = Color(node.category.colorArgb),
            )
        }
        if (node.hasChildren) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 오른쪽에서 실제로 고르는 한 줄. 누르면 바로 골라지고 시트가 닫힌다. */
@Composable
private fun CategoryOption(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(category.colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "선택됨",
                modifier = Modifier.size(20.dp),
                tint = color,
            )
        }
    }
}

@Composable
private fun AccountSheetContent(
    state: EntryUiState,
    target: AccountTarget,
    partIndex: Int? = null,
    onPick: (Long) -> Unit,
) {
    // 이체에서 출금과 입금이 같은 자산이면 안 되므로 반대쪽에 고른 것은 막는다.
    // 나눈 줄은 이체가 아니라 막을 것이 없다.
    val blockedId = when {
        partIndex != null -> null
        target == AccountTarget.FROM -> state.selectedToAccountId
        else -> state.selectedAccountId
    }
    val selectedId = when {
        partIndex != null -> state.parts.getOrNull(partIndex)?.accountId
        target == AccountTarget.FROM -> state.selectedAccountId
        else -> state.selectedToAccountId
    }

    val nodes = remember(state.accounts) { state.accounts.toAccountNodes() }

    // 하위 자산이 하나도 없으면 오른쪽 칸이 늘 빈다. 그때는 예전처럼 한 줄 목록이 낫다.
    if (nodes.none { it.hasChildren }) {
        Column(
            modifier = Modifier
                .heightIn(max = AccountListMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            nodes.forEach { node ->
                AccountOption(
                    account = node.account,
                    selected = node.account.id == selectedId,
                    enabled = node.account.id != blockedId,
                    onClick = { onPick(node.account.id) },
                )
            }
        }
        return
    }

    // 카테고리 선택과 같은 두 칸. 왼쪽에 상위, 오른쪽에 그 상위에서 고를 수 있는 하위.
    // 이미 고른 하위가 있으면 그 상위를 펼쳐 둔다. 다시 열었을 때 어디 있었는지 바로 보인다.
    var focusedId by remember {
        mutableStateOf(
            nodes.firstOrNull { node -> node.hasChildren && node.children.any { it.id == selectedId } }
                ?.account?.id
                ?: nodes.firstOrNull { it.hasChildren }?.account?.id
        )
    }
    val focused = nodes.firstOrNull { it.account.id == focusedId }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(modifier = Modifier.height(AccountPaneHeight)) {
        Column(
            modifier = Modifier
                .width(AccountParentPaneWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .verticalScroll(rememberScrollState()),
        ) {
            nodes.forEach { node ->
                AccountParentItem(
                    node = node,
                    focused = node.account.id == focusedId,
                    holdsSelection = node.selectable.any { it.id == selectedId },
                    // 하위가 없는 상위는 여기서 바로 고른다. 이체 반대쪽에 고른 것이면 막는다.
                    enabled = node.hasChildren || node.account.id != blockedId,
                    onClick = {
                        if (node.hasChildren) focusedId = node.account.id else onPick(node.account.id)
                    },
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            focused?.children?.forEach { account ->
                AccountOption(
                    account = account,
                    selected = account.id == selectedId,
                    enabled = account.id != blockedId,
                    onClick = { onPick(account.id) },
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 자산이 많아도 시트가 화면을 다 덮지 않게. 넘치면 안에서 스크롤한다. */
private val AccountListMaxHeight = 480.dp
private val AccountPaneHeight = 400.dp
/** 카테고리 칸보다 조금 넓다. 상위 줄에 "-5,112,450원" 같은 잔액이 함께 들어가야 한다. */
private val AccountParentPaneWidth = 156.dp

/**
 * 왼쪽 상위 자산 한 줄. 카테고리 선택의 대분류 줄([ParentItem])과 같은 모양이다.
 * 잔액은 하위까지 합친 값을 작게 붙인다. 고르기 전에 "이 묶음에 얼마 남았나"를 본다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountParentItem(
    node: AccountNode,
    focused: Boolean,
    holdsSelection: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val account = node.account
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) BottomSheetDefaults.ContainerColor else Color.Transparent)
            .clickable(
                enabled = enabled,
                onClickLabel = if (node.hasChildren) "${account.name} 펼치기" else "${account.name} 선택",
                onClick = onClick,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(account.colorArgb)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                color = if (focused) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = node.totalBalance.formatWon(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (holdsSelection) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "선택됨",
                modifier = Modifier.size(16.dp),
                tint = Color(account.colorArgb),
            )
        }
        if (node.hasChildren) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * 결제수단 한 줄. [색 배지] 이름 / 종류 · 잔액 (· 한도 남은 금액) ……… ✓
 *
 * 고를 때 보고 싶은 값을 한 줄에 모은다. 잔액은 "이걸로 내도 되나", 한도는 복지포인트처럼
 * "얼마 남았나"를 고르기 전에 확인하려는 것이다. 자산 관리 화면까지 가지 않아도 된다.
 *
 * 배지는 자산 색에 이름 첫 글자를 얹는다. 점 하나보다 커서 색으로 먼저 찾을 수 있다.
 */
@Composable
private fun AccountOption(
    account: AccountBalance,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(account.colorArgb)
    val ledgerColors = LocalLedgerColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) color.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // 이체에서 반대쪽에 이미 고른 자산은 흐리게. 숨기면 목록 순서가 달라져 찾기 어렵다.
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.name.take(1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 이름이 곧 종류면("현금") 종류는 빼고 잔액만. "현금 / 현금 · 1,000원"은 군더더기다.
            Text(
                text = if (account.name == account.type.label) {
                    account.balance.formatWon()
                } else {
                    "${account.type.label} · ${account.balance.formatWon()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (account.hasLimit) {
                val over = account.limitRemaining < 0
                Text(
                    text = if (over) {
                        "${account.limitPeriodLabel} ${(-account.limitRemaining).formatWon()} 초과"
                    } else {
                        "${account.limitPeriodLabel} 남은 ${account.limitRemaining.formatWon()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) ledgerColors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "선택됨",
                modifier = Modifier.size(20.dp),
                tint = color,
            )
        }
    }
}

/**
 * 나눠서 입력하는 칸. 줄마다 카테고리 · 결제수단 · 금액.
 *
 * 마지막 줄은 남은 금액을 자동으로 받는다. 둘로 나누는 흔한 경우에는 첫 줄 금액만 적으면 끝난다.
 */
@Composable
private fun SplitSection(
    state: EntryUiState,
    onPickCategory: (Int) -> Unit,
    onPickAccount: (Int) -> Unit,
    onEditAmount: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMemoChange: (Int, String) -> Unit,
    onMemoFocused: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "나눠서 입력",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAdd) { Text("+ 줄 추가") }
    }
    RowCard {
        state.parts.forEachIndexed { index, part ->
            SplitPartRow(
                state = state,
                index = index,
                part = part,
                onPickCategory = { onPickCategory(index) },
                onPickAccount = { onPickAccount(index) },
                onEditAmount = { onEditAmount(index) },
                onRemove = { onRemove(index) },
                onMemoChange = { onMemoChange(index, it) },
                onMemoFocused = onMemoFocused,
            )
            if (index != state.parts.lastIndex) RowDivider()
        }
    }
    Text(
        text = "마지막 줄에는 남은 금액이 자동으로 들어갑니다. 한 줄만 남기면 나누기가 끝납니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun SplitPartRow(
    state: EntryUiState,
    index: Int,
    part: SplitPart,
    onPickCategory: () -> Unit,
    onPickAccount: () -> Unit,
    onEditAmount: () -> Unit,
    onRemove: () -> Unit,
    onMemoChange: (String) -> Unit,
    onMemoFocused: () -> Unit,
) {
    val category = state.categoryNodes.findSelected(part.categoryId)
    val accountName = state.accounts.firstOrNull { it.id == part.accountId }?.name
    val isRemainder = index == state.parts.lastIndex
    val amount = state.partAmount(index)
    val editing = state.keypadVisible && state.keypadTarget == index
    val amountColor = when {
        // 앞 줄들이 총액을 넘겨 남은 금액이 0 이하면 저장할 수 없다. 색으로 먼저 알린다.
        isRemainder && amount <= 0 -> MaterialTheme.colorScheme.error
        editing -> MaterialTheme.colorScheme.primary
        !isRemainder && part.amountText.isEmpty() -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "카테고리 선택", onClick = onPickCategory)
                    .heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            category?.category?.colorArgb?.let { Color(it) }
                                ?: MaterialTheme.colorScheme.outlineVariant
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = category?.label ?: "카테고리 선택",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (category == null) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = !isRemainder, onClickLabel = "금액 입력", onClick = onEditAmount)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (!isRemainder && part.amountText.isEmpty()) "금액" else "${amount.formatAmount()}원",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                )
                if (isRemainder) {
                    Text(
                        text = "남은 금액",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "이 줄 삭제",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = "결제수단 선택", onClick = onPickAccount)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = accountName ?: "${state.type.accountLabel} 선택",
                style = MaterialTheme.typography.bodyMedium,
                color = if (accountName == null) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        PartMemoField(
            value = part.memo,
            onValueChange = onMemoChange,
            onFocused = onMemoFocused,
        )
    }
}

/**
 * 나눈 줄의 메모. 같은 결제라도 줄마다 산 것이 다르다(식비 "라면", 생활용품 "면봉").
 *
 * 결제수단 글자와 같은 들여쓰기·크기로 두어 그 줄에 딸린 값으로 읽히게 한다.
 */
@Composable
private fun PartMemoField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit,
) {
    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        if (value.isEmpty()) {
            Text(
                text = "메모",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused() },
        )
    }
}

/** 저장 버튼. 키패드가 접혀 있어도 눌러야 하므로 키패드 밖에 둔다. */
@Composable
private fun SaveBar(onSave: () -> Unit, enabled: Boolean) {
    Surface(tonalElevation = 2.dp) {
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 52.dp),
        ) {
            Text("저장", style = MaterialTheme.typography.titleMedium)
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
