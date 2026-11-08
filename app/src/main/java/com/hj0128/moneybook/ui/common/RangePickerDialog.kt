package com.hj0128.moneybook.ui.common

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 시작일과 끝나는 날을 고르는 창. 처음에는 [initialStart]~[initialEnd] 가 골라져 있다.
 *
 * 날짜 고르기는 UTC 자정 밀리초로 주고받는다. 기기 시간대로 바꾸면 한국에서는 날짜가 하루 밀릴 수 있어
 * 날짜만 UTC 로 오간다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart.toUtcMillis(),
        initialSelectedEndDateMillis = initialEnd.toUtcMillis(),
        initialDisplayedMonthMillis = initialStart.toUtcMillis(),
    )
    val start = state.selectedStartDateMillis?.toUtcDate()
    // 하루만 누르고 확인하면 그날 하루로 본다. 끝을 꼭 다시 눌러야 한다면 "오늘 하루"를 보기가 번거롭다.
    val end = state.selectedEndDateMillis?.toUtcDate() ?: start

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (start != null && end != null) onConfirm(start, end) },
                enabled = start != null,
            ) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    ) {
        // 기본 머리글은 "2026년 11월 1일 - 2026년 11월 30일"을 큰 글씨로 써서 창 폭에서 글자 단위로 깨진다.
        // 짧게 고쳐 쓰고, 글자로 날짜를 치는 모드는 뺀다. 달력을 누르는 편이 빠르고 그만큼 달력 칸이 넓어진다.
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    text = "기간 선택",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            headline = {
                Text(
                    text = rangeHeadline(start, state.selectedEndDateMillis?.toUtcDate()),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
            showModeToggle = false,
        )
    }
}

/** "11월 1일 ~ 11월 30일". 올해가 아니면 연도를 붙인다. 아직 안 고른 쪽은 무엇을 누를 차례인지 적는다. */
private fun rangeHeadline(start: LocalDate?, end: LocalDate?): String {
    val thisYear = LocalDate.now().year
    fun text(date: LocalDate) =
        (if (date.year == thisYear) "" else "${date.year}년 ") + "${date.monthValue}월 ${date.dayOfMonth}일"
    return "${start?.let(::text) ?: "시작일"} ~ ${end?.let(::text) ?: "끝나는 날"}"
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
