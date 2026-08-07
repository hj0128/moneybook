package com.hj0128.moneybook.data.model

import com.hj0128.moneybook.data.local.entity.CategoryEntity

/**
 * 대분류 하나와 그 밑 소분류들.
 *
 * 관리 화면과 입력 화면이 같은 묶음을 쓴다. 화면마다 따로 묶으면 한쪽만 고쳐져
 * 같은 카테고리가 화면마다 다르게 보이게 된다.
 */
data class CategoryNode(
    val category: CategoryEntity,
    val children: List<CategoryEntity>,
) {
    /**
     * 입력할 때 고를 수 있는 것.
     *
     * 소분류가 있으면 소분류만 고른다. 대분류까지 같이 고를 수 있게 두면
     * "언제 식비를 고르고 언제 외식을 고르나"가 애매해지고, 애매한 채로 쌓인
     * 기록은 나중에 소분류별로 집계해도 절반이 대분류에 남아 의미가 없다.
     *
     * 소분류가 없으면 대분류 자신이 곧 리프다.
     */
    val selectable: List<CategoryEntity> get() = children.ifEmpty { listOf(category) }

    val hasChildren: Boolean get() = children.isNotEmpty()
}

/**
 * 고를 수 있는 카테고리만 평평하게.
 *
 * 대분류에 소분류가 생기면 그 대분류는 여기서 빠진다. 입력 화면에서 고른 카테고리가
 * 아직 유효한지 볼 때 쓰므로, 소분류가 생긴 순간 선택이 풀리는 것이 맞다.
 */
fun List<CategoryNode>.flatten(): List<CategoryEntity> = flatMap { it.selectable }

/**
 * 평평한 목록을 대분류 > 소분류로 묶는다.
 *
 * 부모가 사라진 소분류(외래키가 SET NULL 되기 전 등)는 버리지 않고 대분류로 올린다.
 * 화면에서 사라지면 사용자는 지울 방법조차 없어진다.
 */
fun List<CategoryEntity>.toNodes(): List<CategoryNode> {
    val byParent = filterNot { it.isTopLevel }.groupBy { it.parentId }
    val topLevelIds = filter { it.isTopLevel }.map { it.id }.toSet()
    val orphans = filterNot { it.isTopLevel }.filter { it.parentId !in topLevelIds }

    return (filter { it.isTopLevel } + orphans).map { parent ->
        CategoryNode(category = parent, children = byParent[parent.id].orEmpty())
    }
}
