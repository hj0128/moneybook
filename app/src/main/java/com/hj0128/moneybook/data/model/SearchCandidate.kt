package com.hj0128.moneybook.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * 검색 대상 한 줄. 목록용 [TransactionDetail] 에 대분류 이름을 더한다.
 *
 * "식비"로 찾으면 외식·마트·편의점에 붙은 내역도 나와야 한다. 목록용 모델에는 소분류 이름만 있다.
 */
data class SearchCandidate(
    @Embedded
    val detail: TransactionDetail,
    @ColumnInfo(name = "parent_category_name")
    val parentCategoryName: String?,
    /** 카테고리 필터에서 대분류("식비")를 고르면 그 소분류에 붙은 내역도 걸러야 한다. */
    @ColumnInfo(name = "parent_category_id")
    val parentCategoryId: Long?,
)
