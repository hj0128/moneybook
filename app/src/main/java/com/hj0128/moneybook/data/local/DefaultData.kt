package com.hj0128.moneybook.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DB 최초 생성 시 넣어주는 기본 카테고리/자산.
 *
 * 빈 화면으로 시작하면 사용자가 첫 입력을 하기 전에 카테고리부터 만들어야 해서 이탈한다.
 * 기본값을 깔아두고 설정에서 고치게 하는 편이 낫다.
 */
internal object DefaultData {

    private data class Seed(val name: String, val color: Long)

    private val expenseCategories = listOf(
        Seed("식비", 0xFFEF5350),
        Seed("카페/간식", 0xFFFF7043),
        Seed("교통", 0xFF42A5F5),
        Seed("생활용품", 0xFF66BB6A),
        Seed("주거/통신", 0xFF26A69A),
        Seed("의료/건강", 0xFFAB47BC),
        Seed("문화/여가", 0xFF7E57C2),
        Seed("의류/미용", 0xFFEC407A),
        Seed("교육", 0xFF5C6BC0),
        Seed("경조사", 0xFF8D6E63),
        Seed("기타", 0xFF78909C),
    )

    private val incomeCategories = listOf(
        Seed("월급", 0xFF2E7D32),
        Seed("용돈", 0xFF43A047),
        Seed("부수입", 0xFF00897B),
        Seed("금융소득", 0xFF00ACC1),
        Seed("기타", 0xFF78909C),
    )

    private val accounts = listOf(
        Triple("현금", "CASH", 0L),
        Triple("은행 계좌", "BANK", 0L),
        Triple("체크/신용카드", "CARD", 0L),
    )

    fun seed(db: SupportSQLiteDatabase) {
        expenseCategories.forEachIndexed { index, seed ->
            insertCategory(db, seed, "EXPENSE", index)
        }
        incomeCategories.forEachIndexed { index, seed ->
            insertCategory(db, seed, "INCOME", index)
        }
        accounts.forEachIndexed { index, (name, type, initialBalance) ->
            db.execSQL(
                "INSERT INTO accounts (name, type, initial_balance, sort_order) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(name, type, initialBalance, index),
            )
        }
    }

    private fun insertCategory(db: SupportSQLiteDatabase, seed: Seed, type: String, order: Int) {
        db.execSQL(
            "INSERT INTO categories (name, type, color_argb, sort_order) VALUES (?, ?, ?, ?)",
            // color 는 Int 컬럼이라 0xFFEF5350 같은 값은 부호 있는 Int 로 변환해서 넣는다.
            arrayOf<Any>(seed.name, type, seed.color.toInt(), order),
        )
    }
}
