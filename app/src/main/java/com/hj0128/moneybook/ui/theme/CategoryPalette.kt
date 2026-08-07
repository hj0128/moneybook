package com.hj0128.moneybook.ui.theme

/**
 * 카테고리에 고를 수 있는 색.
 *
 * 임의의 색을 고르게 하면 배경과 구분이 안 되는 색을 골라 목록에서 사라지거나,
 * 비슷한 색이 여럿 생겨 막대 그래프에서 서로 구별되지 않는다.
 * 라이트/다크 양쪽에서 안전하고 서로 충분히 떨어진 12색만 남긴다.
 *
 * DefaultData 의 기본 카테고리가 쓰는 색도 모두 이 안에 있다.
 */
val CategoryPalette: List<Int> = listOf(
    0xFFEF5350, // red
    0xFFEC407A, // pink
    0xFFAB47BC, // purple
    0xFF7E57C2, // deep purple
    0xFF5C6BC0, // indigo
    0xFF42A5F5, // blue
    0xFF26A69A, // teal
    0xFF66BB6A, // green
    0xFF9CCC65, // light green
    0xFFFFA726, // orange
    0xFFFF7043, // deep orange
    0xFF8D6E63, // brown
).map { it.toInt() }

/** 팔레트에 없는 색이 들어와도 화면이 깨지지 않게 첫 색으로 떨어뜨린다. */
fun paletteColorOrDefault(argb: Int?): Int =
    argb?.takeIf { it in CategoryPalette } ?: CategoryPalette.first()
