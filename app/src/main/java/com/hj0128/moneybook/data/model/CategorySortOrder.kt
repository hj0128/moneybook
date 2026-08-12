package com.hj0128.moneybook.data.model

/** 카테고리별 탭에서 줄을 늘어놓는 기준. */
enum class CategorySortOrder {
    /** 큰 금액부터. "어디에 많이 썼나"를 보는 기본 순서. */
    AMOUNT,

    /** 건수가 많은 것부터. 금액순과 다른 이야기를 한다(커피는 자주, 월세는 한 번 크게). */
    COUNT,

    /** 가나다순. 특정 카테고리를 눈으로 찾을 때. */
    NAME,

    /**
     * 카테고리 관리 화면과 같은 순서.
     *
     * 달마다 금액이 바뀌어도 줄 위치가 그대로라, 늘 보던 자리에서 늘 보던 카테고리를 찾게 된다.
     */
    MANUAL;

    val label: String
        get() = when (this) {
            AMOUNT -> "금액순"
            COUNT -> "건수순"
            NAME -> "이름순"
            MANUAL -> "관리순"
        }
}

/**
 * 정렬 적용.
 *
 * 금액과 건수는 같은 값이 흔해서(1건짜리가 여럿) 2차 기준이 없으면 순서가 들쭉날쭉해
 * 보인다. 화면을 다시 그릴 때마다 자리가 바뀌면 눌러야 할 줄을 놓친다.
 */
fun List<CategoryTotal>.sortedBy(order: CategorySortOrder): List<CategoryTotal> = when (order) {
    CategorySortOrder.AMOUNT -> sortedWith(compareByDescending<CategoryTotal> { it.total }.thenBy { it.name })
    CategorySortOrder.COUNT -> sortedWith(compareByDescending<CategoryTotal> { it.entryCount }.thenByDescending { it.total })
    // 한글 음절은 코드포인트 순서가 곧 가나다순이라 따로 비교기를 두지 않아도 된다.
    CategorySortOrder.NAME -> sortedBy { it.name }
    // sort_order 는 겹칠 수 있다(옛 데이터, 대분류가 다른 소분류들). id 로 마무리해 자리를 고정한다.
    CategorySortOrder.MANUAL -> sortedWith(compareBy<CategoryTotal> { it.sortOrder }.thenBy { it.categoryId })
}
