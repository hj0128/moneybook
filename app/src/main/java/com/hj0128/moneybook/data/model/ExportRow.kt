package com.hj0128.moneybook.data.model

import androidx.room.ColumnInfo
import java.time.LocalDate

/**
 * 내보내기(CSV) 한 줄. 화면용 [TransactionDetail] 과 따로 두는 건 필요한 값이 달라서다.
 * 엑셀에서 대분류·소분류로 나눠 피벗할 수 있게 카테고리를 두 칸으로 받는다.
 */
data class ExportRow(
    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,
    val type: TransactionType,
    val amount: Long,
    /** 대분류. 소분류에 붙은 거래면 그 부모, 대분류에 직접 붙었으면 자기 자신. */
    @ColumnInfo(name = "parent_name")
    val parentName: String?,
    /** 소분류. 대분류에 직접 붙은 거래면 null. */
    @ColumnInfo(name = "child_name")
    val childName: String?,
    @ColumnInfo(name = "account_name")
    val accountName: String?,
    @ColumnInfo(name = "to_account_name")
    val toAccountName: String?,
    val merchant: String,
    val memo: String,
    @ColumnInfo(name = "split_group_id")
    val splitGroupId: Long?,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(dateEpochDay)
}
