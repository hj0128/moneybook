package com.hj0128.moneybook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hj0128.moneybook.data.model.TransactionType

/**
 * 카테고리(식비, 교통, 월급 ...). 수입용/지출용이 구분된다.
 *
 * 대분류 아래에 소분류를 둘 수 있다(식비 > 마트/외식/편의점). 2뎁스까지만 쓴다.
 *
 * 거래는 대분류에도 소분류에도 붙을 수 있고, 집계할 때만 부모로 합친다.
 * 소분류를 강제하면 쪼갤 생각이 없는 카테고리까지 "식비 > 기타" 식으로
 * 억지로 나눠야 하고, 기존 데이터도 전부 손봐야 한다.
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            // 부모가 사라지면 소분류는 대분류로 올라온다. 지워지는 것보다 낫다.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("type"), Index("parent_id")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: TransactionType,
    /** 리스트에서 쓰는 색상 (ARGB). */
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    /** 대분류면 null, 소분류면 부모 카테고리 id. */
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,
) {
    val isTopLevel: Boolean get() = parentId == null
}
