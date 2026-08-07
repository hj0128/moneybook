package com.hj0128.moneybook.util

/**
 * 앞말의 받침에 따라 조사를 고른다. "1건" -> "이", "2개" -> "가".
 *
 * 개수가 들어가는 문장은 단위가 바뀌면 조사도 바뀐다("건"은 받침 있음, "개"는 없음).
 * "1건이(가)" 같은 표기는 읽기 나쁘고, 문장을 바꿔 피하려 들면 어색해진다.
 */
fun josaFor(word: String, withFinal: String, withoutFinal: String): String {
    val last = word.lastOrNull() ?: return withoutFinal
    // 한글 음절이 아니면(숫자, 영문 등) 판단할 수 없으니 받침 없는 쪽으로 둔다.
    if (last !in '가'..'힣') return withoutFinal
    val hasFinal = (last.code - 0xAC00) % 28 != 0
    return if (hasFinal) withFinal else withoutFinal
}

/** "내역 1건" + 이/가 */
fun String.withSubjectJosa(): String = this + josaFor(this, "이", "가")
