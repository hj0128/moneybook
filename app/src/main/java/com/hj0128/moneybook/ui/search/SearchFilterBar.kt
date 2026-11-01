package com.hj0128.moneybook.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.util.formatShortWon
import java.time.YearMonth

/** 지금 열린 필터 시트. 종류(지출·수입)는 고를 것이 셋뿐이라 시트 대신 버튼 아래 메뉴로 연다. */
private enum class FilterSheet { CATEGORY, AMOUNT, PERIOD }

/** 필터 바가 바꿀 수 있는 것들. 화면에서 ViewModel 함수를 그대로 이어 준다. */
class SearchFilterActions(
    val onTypeChange: (TransactionType?) -> Unit,
    val onToggleCategory: (CategoryFilter) -> Unit,
    val onClearCategories: () -> Unit,
    val onAmountChange: (AmountRange?) -> Unit,
    val onToggleMonth: (YearMonth) -> Unit,
    val onAddMonths: (MonthRange) -> Unit,
    val onRemoveMonths: (MonthRange) -> Unit,
    val onClearMonths: () -> Unit,
    val onClearAll: () -> Unit,
)

/**
 * 검색창 아래 필터.
 *
 * 윗줄은 고르는 버튼, 아랫줄은 지금 고른 조건이다. 기간과 카테고리는 여럿 고를 수 있어서 버튼 하나에
 * 값을 다 적을 수 없다. 고른 것을 하나씩 칩으로 늘어놓고 ✕ 로 따로 지운다.
 * 두 줄 모두 넘치면 옆으로 밀어 본다. 줄을 바꾸면 결과가 자꾸 밀려 내려간다.
 */
@Composable
fun SearchFilterBar(
    filters: SearchFilters,
    expenseCategories: List<CategoryNode>,
    incomeCategories: List<CategoryNode>,
    actions: SearchFilterActions,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<FilterSheet?>(null) }
    var typeMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterButton(
                label = countLabel("기간", filters.months.size, "개월"),
                active = filters.months.isNotEmpty(),
                onClick = { onOpen(); sheet = FilterSheet.PERIOD },
            )
            FilterButton(
                label = countLabel("카테고리", filters.categories.size, "개"),
                active = filters.categories.isNotEmpty(),
                onClick = { onOpen(); sheet = FilterSheet.CATEGORY },
            )
            FilterButton(
                label = "금액",
                active = filters.amount != null,
                onClick = { onOpen(); sheet = FilterSheet.AMOUNT },
            )
            Box {
                FilterButton(
                    label = "지출·수입",
                    active = filters.type != null,
                    onClick = { onOpen(); typeMenuOpen = true },
                )
                DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    listOf(null, TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type?.label ?: "전체 (이체 포함)") },
                            trailingIcon = {
                                if (type == filters.type) {
                                    Icon(Icons.Default.Check, contentDescription = "선택됨", modifier = Modifier.size(18.dp))
                                }
                            },
                            onClick = {
                                typeMenuOpen = false
                                actions.onTypeChange(type)
                            },
                        )
                    }
                }
            }
        }

        if (filters.isActive) {
            SelectedFilters(filters = filters, actions = actions)
        }
    }

    when (sheet) {
        null -> Unit
        FilterSheet.CATEGORY -> CategoryFilterSheet(
            selected = filters.categories,
            initialType = filters.categories.lastOrNull()?.type ?: filters.type ?: TransactionType.EXPENSE,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            onToggle = actions.onToggleCategory,
            onClear = actions.onClearCategories,
            onDismiss = { sheet = null },
        )

        FilterSheet.AMOUNT -> AmountFilterSheet(
            current = filters.amount,
            onApply = { actions.onAmountChange(it); sheet = null },
            onDismiss = { sheet = null },
        )

        FilterSheet.PERIOD -> PeriodFilterSheet(
            selected = filters.months,
            onToggle = actions.onToggleMonth,
            onAddRange = actions.onAddMonths,
            onClear = actions.onClearMonths,
            onDismiss = { sheet = null },
        )
    }
}

/** "카테고리" / "카테고리 2개". 여럿 고를 수 있는 필터는 몇 개 골랐는지 버튼에도 적는다. */
private fun countLabel(name: String, count: Int, unit: String): String =
    if (count == 0) name else "$name $count$unit"

/** 고르는 버튼. 고른 것이 있으면 칠해서 걸려 있다는 것을 보여준다. 값은 아랫줄 칩에 있다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

/**
 * 지금 고른 조건들. 기간 → 카테고리 → 금액 → 종류 순으로 늘어놓고, 하나씩 ✕ 로 지운다.
 * 이어진 달은 "2023년 7월 ~ 9월" 한 칩으로 묶는다. 7·8·9월을 따로 세 칩으로 늘어놓으면 줄만 길어진다.
 */
@Composable
private fun SelectedFilters(filters: SearchFilters, actions: SearchFilterActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.months.toRanges().forEach { range ->
            SelectedChip(range.label) { actions.onRemoveMonths(range) }
        }
        filters.categories.forEach { category ->
            SelectedChip(category.name) { actions.onToggleCategory(category) }
        }
        filters.amount?.let { amount ->
            SelectedChip(amount.label) { actions.onAmountChange(null) }
        }
        filters.type?.let { type ->
            SelectedChip(type.label) { actions.onTypeChange(null) }
        }
        TextButton(onClick = actions.onClearAll) { Text("모두 지우기") }
    }
}

/** 고른 조건 하나. 칩 어디를 눌러도 지워진다. ✕ 만 누르게 하면 손가락으로 맞추기 어렵다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectedChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "$label 지우기", modifier = Modifier.size(16.dp)) },
    )
}

/** 시트 제목 줄. 오른쪽에 이 필터를 전부 푸는 버튼을 둔다(고른 것이 있을 때만). */
@Composable
private fun SheetTitle(title: String, onClear: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (onClear != null) TextButton(onClick = onClear) { Text("모두 해제") }
    }
}

@Composable
private fun DoneButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { Text("완료") }
}

/**
 * 카테고리 필터. 누를 때마다 켜고 끄며, 여럿 고를 수 있다(외식 + 마트).
 * 대분류를 고르면 그 밑 소분류까지 포함한다("식비" = 외식 + 마트 + 편의점).
 *
 * 고르는 대로 결과에 바로 반영된다. 시트 뒤로 결과가 바뀌는 게 보이므로 따로 "적용"을 누를 필요가 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterSheet(
    selected: List<CategoryFilter>,
    initialType: TransactionType,
    expenseCategories: List<CategoryNode>,
    incomeCategories: List<CategoryNode>,
    onToggle: (CategoryFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    val nodes = if (type == TransactionType.INCOME) incomeCategories else expenseCategories
    val selectedIds = selected.map { it.id }.toSet()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            SheetTitle("카테고리", onClear = if (selected.isNotEmpty()) onClear else null)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = type == option,
                        onClick = { type = option },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(option.label) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                nodes.forEach { node ->
                    item(key = "parent-${node.category.id}") {
                        CategoryFilterRow(
                            name = node.category.name,
                            // 소분류가 있는 대분류는 고르면 무엇이 포함되는지 적어 둔다.
                            hint = if (node.hasChildren) "소분류 포함" else null,
                            colorArgb = node.category.colorArgb,
                            indent = false,
                            selected = node.category.id in selectedIds,
                            onClick = { onToggle(CategoryFilter(node.category.id, node.category.name, type)) },
                        )
                    }
                    node.children.forEach { child ->
                        item(key = "child-${child.id}") {
                            CategoryFilterRow(
                                name = child.name,
                                hint = null,
                                colorArgb = child.colorArgb,
                                indent = true,
                                selected = child.id in selectedIds,
                                onClick = { onToggle(CategoryFilter(child.id, child.name, type)) },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DoneButton(onDismiss)
        }
    }
}

@Composable
private fun CategoryFilterRow(
    name: String,
    hint: String?,
    colorArgb: Int,
    indent: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = Color(colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(start = if (indent) 44.dp else 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (indent) 8.dp else 10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = if (indent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        hint?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "선택됨", tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

/** 금액 필터. 최소·최대를 직접 적는다. 한쪽만 적어도 된다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountFilterSheet(
    current: AmountRange?,
    onApply: (AmountRange?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minText by remember { mutableStateOf(current?.min?.toString().orEmpty()) }
    var maxText by remember { mutableStateOf(current?.max?.toString().orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            SheetTitle("금액", onClear = current?.let { { onApply(null) } })
            Text(
                text = "한쪽만 적어도 됩니다. 최소만 적으면 그 이상, 최대만 적으면 그 이하.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmountField(value = minText, onValueChange = { minText = it }, label = "최소", modifier = Modifier.weight(1f))
                Text("~", modifier = Modifier.padding(horizontal = 8.dp))
                AmountField(value = maxText, onValueChange = { maxText = it }, label = "최대", modifier = Modifier.weight(1f))
            }
            Button(
                onClick = { onApply(AmountRange.of(minText.toLongOrNull(), maxText.toLongOrNull())) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) { Text("적용") }
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        // 숫자 말고는 받지 않는다. 쉼표를 허용하면 "1,0,000" 같은 것도 들어와 무엇인지 알 수 없다.
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(12)) },
        label = { Text(label) },
        suffix = { Text("원") },
        // 적는 중에도 "10만원"처럼 읽어 준다. 0이 여럿이면 자릿수를 잘못 세기 쉽다.
        supportingText = { value.toLongOrNull()?.let { Text(it.formatShortWon()) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/**
 * 기간 필터. 달을 누를 때마다 켜고 끈다. 이어지지 않아도 된다(2023년 3월과 7월).
 * 범위는 이어서 누르면 된다(7·8·9월). 흔한 기간은 빠른 선택으로 한꺼번에 더한다.
 *
 * 날짜까지 고르게 하지 않는다. 지난 일을 찾을 때는 "작년 여름", "7월부터 9월"처럼 달로 기억한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodFilterSheet(
    selected: Set<YearMonth>,
    onToggle: (YearMonth) -> Unit,
    onAddRange: (MonthRange) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val thisMonth = remember { YearMonth.now() }
    // 고른 달이 있으면 가장 최근 것이 있는 해부터 보여준다. 다시 열었을 때 고른 것이 바로 보인다.
    var shownYear by remember { mutableIntStateOf(selected.maxOrNull()?.year ?: thisMonth.year) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            SheetTitle("기간", onClear = if (selected.isNotEmpty()) onClear else null)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PeriodPreset.entries.forEach { preset ->
                    OutlinedButton(onClick = {
                        val range = preset.range(thisMonth)
                        onAddRange(range)
                        shownYear = range.end.year
                    }) { Text(preset.label) }
                }
            }
            Text(
                text = "달을 누를 때마다 켜고 끕니다. 여러 달을 고를 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { shownYear-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 해")
                }
                Text(
                    text = "${shownYear}년",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp),
                )
                // 아직 오지 않은 해에는 내역이 없다.
                IconButton(onClick = { shownYear++ }, enabled = shownYear < thisMonth.year) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 해")
                }
            }
            MonthGrid(year = shownYear, selected = selected, latest = thisMonth, onToggle = onToggle)
            // 다른 해를 보고 있어도 무엇을 골라 뒀는지 보이게 고른 것을 모두 적는다.
            Text(
                text = selected.toRanges().joinToString(", ") { it.label }.ifEmpty { "고른 달이 없습니다" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            DoneButton(onDismiss)
        }
    }
}

/** 한 해 12달. 고른 달은 칠한다. */
@Composable
private fun MonthGrid(
    year: Int,
    selected: Set<YearMonth>,
    latest: YearMonth,
    onToggle: (YearMonth) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0 until 3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { col ->
                    val month = YearMonth.of(year, row * 4 + col)
                    val isSelected = month in selected
                    val enabled = month <= latest
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (isSelected) colors.primary else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else colors.outlineVariant,
                                shape = MaterialTheme.shapes.medium,
                            )
                            .clickable(enabled = enabled) { onToggle(month) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${month.monthValue}월",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                isSelected -> colors.onPrimary
                                !enabled -> colors.outline.copy(alpha = 0.5f)
                                else -> colors.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}
