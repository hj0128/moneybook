package com.hj0128.moneybook.data.model

/**
 * 상위 자산 하나와 그 밑 하위 자산들. 카테고리의 [CategoryNode] 와 같은 구조다.
 *
 * 체크카드를 두 장 쓰면 "체크카드" 밑에 A카드, B카드를 둔다. 카드마다 실적·한도는 따로 보고,
 * 체크카드로 얼마 썼는지는 위에서 합계로 본다. 카테고리와 같은 모양이라 따로 익힐 것이 없다.
 */
data class AccountNode(
    val account: AccountBalance,
    val children: List<AccountBalance>,
) {
    /**
     * 내역을 적을 때 고를 수 있는 것. 카테고리와 같은 규칙이다.
     *
     * 하위 자산이 있으면 하위만 고른다. 상위까지 고를 수 있게 두면 "체크카드"로 적은 것과
     * "A카드"로 적은 것이 섞여, 카드별로 나눠 보려던 의미가 없어진다.
     */
    val selectable: List<AccountBalance> get() = children.ifEmpty { listOf(account) }

    val hasChildren: Boolean get() = children.isNotEmpty()

    /** 상위 자신과 하위를 합친 잔액. 하위를 만들기 전 상위에 쌓인 내역도 사라지지 않고 여기 들어간다. */
    val totalBalance: Long get() = account.balance + children.sumOf { it.balance }
}

/**
 * 평평한 자산 목록을 상위 > 하위로 묶는다. 순서는 받은 순서(자산 관리 순서)를 따른다.
 *
 * 부모가 사라진 하위 자산은 버리지 않고 상위로 올린다. 화면에서 사라지면 고칠 방법도 없어진다.
 */
fun List<AccountBalance>.toAccountNodes(): List<AccountNode> {
    val topLevelIds = filter { it.isTopLevel }.map { it.id }.toSet()
    val byParent = filter { it.parentId in topLevelIds }.groupBy { it.parentId }
    return filter { it.parentId !in topLevelIds }.map { parent ->
        AccountNode(account = parent, children = byParent[parent.id].orEmpty())
    }
}

/** 고를 수 있는 자산만 평평하게. 하위가 생긴 상위는 빠진다. */
fun List<AccountNode>.selectableAccounts(): List<AccountBalance> = flatMap { it.selectable }
