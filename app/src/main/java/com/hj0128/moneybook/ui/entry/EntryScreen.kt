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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

private sealed interface EntrySheet {
    data object Category : EntrySheet
    data class Account(val target: AccountTarget, val title: String) : EntrySheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntryViewModel = viewModel(factory = EntryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var showDatePicker by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<EntrySheet?>(null) }

    // 금액 말고 다른 것을 만지기 시작하면 숫자 키패드도 시스템 키보드도 자리를 비켜준다.
    val dismissInputs = {
        focusManager.clearFocus()
        viewModel.hideKeypad()
    }

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
                    if (!state.isTransfer) {
                        PickerRow(
                            label = "카테고리",
                            value = state.selectedCategory?.label,
                            colorArgb = state.selectedCategory?.category?.colorArgb,
                            onClick = { dismissInputs(); sheet = EntrySheet.Category },
                        )
                        InputRow(
                            label = "거래처",
                            value = state.merchant,
                            onValueChange = viewModel::updateMerchant,
                            onFocused = viewModel::hideKeypad,
                        )
                    }

                    InputRow(
                        label = "메모",
                        value = state.memo,
                        onValueChange = viewModel::updateMemo,
                        onFocused = viewModel::hideKeypad,
                    )

                    PickerRow(
                        label = "날짜",
                        value = state.date.formatFull(),
                        onClick = { dismissInputs(); showDatePicker = true },
                    )

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
                    } else {
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

        EntrySheet.Category -> CategoryPickerSheet(
            nodes = state.categoryNodes,
            selectedId = state.selectedCategoryId,
            onPick = {
                viewModel.selectCategory(it)
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
                onPick = {
                    when (open.target) {
                        AccountTarget.FROM -> viewModel.selectAccount(it)
                        AccountTarget.TO -> viewModel.selectToAccount(it)
                    }
                    sheet = null
                },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.type.label} 금액",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                // 키패드가 올라와 있으면 굳이 "누르세요" 표시를 남길 이유가 없다.
                if (!state.keypadVisible) {
                    Icon(
                        imageVector = Icons.Default.Dialpad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocused() },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    inner()
                }
            },
        )
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
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/**
 * 카테고리 선택.
 *
 * 대분류를 타일로 모두 깔고, 소분류가 있는 것을 누르면 그 자리에서 소분류 타일로 바뀐다.
 *
 * 이전에는 소분류가 있는 대분류만 머리글 + 칩이고 나머지는 칩 한 덩어리였는데,
 * 같은 대분류인데 화면에서 두 가지 모양으로 갈려 어색했다. 타일로 통일하면
 * 모든 대분류가 같은 모양이고, 더 들어갈 곳이 있는지는 › 하나로만 구분된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    nodes: List<CategoryNode>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var openParentId by remember { mutableStateOf<Long?>(null) }
    val openParent = nodes.firstOrNull { it.category.id == openParentId }

    // 대분류로 돌아가는 길은 ‹ 버튼뿐이다. ModalBottomSheet 는 자체 창에서 뒤로가기를
    // 먼저 처리해 시트 안의 BackHandler 까지 오지 않으므로, 하드웨어 뒤로가기는
    // 시트를 통째로 닫는다(시트의 표준 동작이라 어색하지 않다).
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            if (openParent == null) {
                Text(
                    text = "카테고리 선택",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                TileGrid(
                    tiles = nodes.map { node ->
                        PickerTile(
                            id = node.category.id,
                            name = node.category.name,
                            colorArgb = node.category.colorArgb,
                            hasChildren = node.hasChildren,
                            // selectable 은 소분류가 있으면 소분류들, 없으면 자기 자신이다.
                            // 그래서 이 한 줄로 두 경우가 함께 처리된다.
                            selected = node.selectable.any { it.id == selectedId },
                        )
                    },
                    onClick = { id ->
                        val node = nodes.first { it.category.id == id }
                        if (node.hasChildren) openParentId = id else onPick(id)
                    },
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { openParentId = null }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "대분류로",
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(openParent.category.colorArgb)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = openParent.category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TileGrid(
                    tiles = openParent.children.map {
                        PickerTile(
                            id = it.id,
                            name = it.name,
                            colorArgb = it.colorArgb,
                            selected = it.id == selectedId,
                        )
                    },
                    onClick = onPick,
                )
            }
        }
    }
}

/**
 * 시트 타일 하나.
 *
 * [colorArgb] 가 null 이면 중립색으로 그린다. 자산에는 색이 없는데, 목록이나 차트
 * 어디에도 안 쓰이는 색을 여기서만 새로 부여하면 근거 없는 색이 된다.
 */
private data class PickerTile(
    val id: Long,
    val name: String,
    val colorArgb: Int? = null,
    /** 이름 아래 작게 붙는 한 줄. 자산의 잔액처럼 고를 때 도움이 되는 값. */
    val caption: String? = null,
    val hasChildren: Boolean = false,
    val enabled: Boolean = true,
    /**
     * 켜진 상태인지. 부르는 쪽이 정한다.
     *
     * 대분류 타일은 자기가 골라진 것이 아니라 "고른 소분류를 품고 있는" 경우에도 켜진다.
     * 그렇지 않으면 소분류를 고르고 시트를 다시 열었을 때 어느 대분류 안에 있었는지 알 수 없다.
     */
    val selected: Boolean = false,
)

private const val TILE_COLUMNS = 3
private const val VISIBLE_TILE_ROWS = 4

/**
 * 타일 높이.
 *
 * 아이콘 없이 글자만 넣으므로 정사각형에 가깝게 키우면 가운데 글자 한 줄만 뜨고
 * 위아래가 비어 보인다. 두 줄짜리 이름이 들어갈 만큼만 두고 글자를 키우는 편이 낫다.
 */
private val TileHeight = 68.dp
private val TileGap = 8.dp

/**
 * 타일 영역 높이. 모든 선택 시트가 이 높이를 함께 쓴다.
 *
 * 개수에 따라 커졌다 작아졌다 하면 대분류/소분류를 오갈 때도, 카테고리와 결제수단을
 * 번갈아 열 때도 시트가 튄다. 항목이 적은 시트는 아래가 비지만 그게 덜 거슬린다.
 */
private val TileGridHeight = TileHeight * VISIBLE_TILE_ROWS + TileGap * (VISIBLE_TILE_ROWS - 1)

/**
 * 타일 격자.
 *
 * LazyVerticalGrid 는 높이가 정해지지 않은 시트 안에서 높이를 못 정한다.
 * 줄 수가 많지 않으므로 직접 세 칸씩 끊고, 높이는 [TileGridHeight] 로 못 박아
 * 넘치는 만큼만 안에서 스크롤한다.
 */
@Composable
private fun TileGrid(
    tiles: List<PickerTile>,
    onClick: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .height(TileGridHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        tiles.chunked(TILE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                row.forEach { tile ->
                    TileItem(
                        tile = tile,
                        onClick = { onClick(tile.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 마지막 줄이 덜 찼을 때 남은 칸을 비워 타일 폭을 일정하게 유지한다.
                repeat(TILE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileItem(
    tile: PickerTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 카테고리 색을 타일에 연하게 깐다. 목록과 차트가 쓰는 색과 같아서 눈으로 이어진다.
    // 고르면 같은 색을 진하게 올려 "다른 색"이 아니라 "같은 색이 켜진 것"으로 읽히게 한다.
    val selected = tile.selected
    val accent = tile.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val background = when {
        selected -> accent.copy(alpha = 0.30f)
        tile.colorArgb != null -> accent.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = when {
        selected -> accent
        tile.colorArgb != null -> accent.copy(alpha = 0.40f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .height(TileHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(enabled = tile.enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tile.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (tile.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            tile.caption?.let { caption ->
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tile.hasChildren) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun AccountSheetContent(
    state: EntryUiState,
    target: AccountTarget,
    onPick: (Long) -> Unit,
) {
    // 이체에서 출금과 입금이 같은 자산이면 안 되므로 반대쪽에 고른 것은 막는다.
    val blockedId = when (target) {
        AccountTarget.FROM -> state.selectedToAccountId
        AccountTarget.TO -> state.selectedAccountId
    }
    val selectedId = when (target) {
        AccountTarget.FROM -> state.selectedAccountId
        AccountTarget.TO -> state.selectedToAccountId
    }

    TileGrid(
        tiles = state.accounts.map { account ->
            PickerTile(
                id = account.id,
                name = account.name,
                colorArgb = account.colorArgb,
                // 잔액은 이미 계산해 두고 있었다. 어느 자산으로 낼지 고를 때
                // 가장 먼저 보고 싶은 값이라 타일에 함께 낸다.
                caption = account.balance.formatWon(),
                enabled = account.id != blockedId,
                selected = account.id == selectedId,
            )
        },
        onClick = onPick,
    )
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
