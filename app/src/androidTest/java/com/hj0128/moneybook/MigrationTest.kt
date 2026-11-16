package com.hj0128.moneybook

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hj0128.moneybook.data.local.ALL_MIGRATIONS
import com.hj0128.moneybook.data.local.AppDatabase
import com.hj0128.moneybook.data.local.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 예전 버전 DB 가 지금 버전으로 올라오는지 본다.
 *
 * 앱을 업데이트한 사람만이 아니라, 예전 백업을 복원하는 사람도 이 마이그레이션을 거친다.
 * 스키마가 엔티티와 하나라도 다르면 Room 이 DB 를 열지 못하고, 데이터를 고치는 단계가 틀리면
 * 조용히 잘못된 값이 남는다. 둘 다 내보낸 스키마(app/schemas)로 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun 마이그레이션이_버전마다_빠짐없이_있다() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until AppDatabase.VERSION).map { it to it + 1 }, steps)
    }

    @Test
    fun 한_단계씩_올린_스키마가_그_버전의_스키마와_같다() {
        ALL_MIGRATIONS.forEach { migration ->
            val name = "step-${migration.startVersion}.db"
            helper.createDatabase(name, migration.startVersion).close()
            helper.runMigrationsAndValidate(name, migration.endVersion, true, migration).close()
        }
    }

    @Test
    fun v1_데이터가_최신_버전까지_남는다() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO categories (id, name, type, color_argb, sort_order) VALUES (1, '식비', 'EXPENSE', 0, 0)")
            db.execSQL("INSERT INTO categories (id, name, type, color_argb, sort_order) VALUES (2, '급여', 'INCOME', 0, 0)")
            db.execSQL("INSERT INTO categories (id, name, type, color_argb, sort_order) VALUES (3, '기타', 'INCOME', 0, 1)")
            db.execSQL("INSERT INTO accounts (id, name, type, initial_balance, sort_order) VALUES (1, '지갑', 'CASH', 10000, 0)")
            db.execSQL("INSERT INTO accounts (id, name, type, initial_balance, sort_order) VALUES (2, '통장', 'BANK', 0, 1)")
            db.execSQL("INSERT INTO accounts (id, name, type, initial_balance, sort_order) VALUES (3, '카드', 'CARD', 0, 2)")
            db.execSQL(
                "INSERT INTO transactions (id, amount, type, category_id, account_id, to_account_id, date_epoch_day, memo, created_at) " +
                    "VALUES (1, 8000, 'EXPENSE', 1, 3, NULL, 20000, '점심', 1)"
            )
        }

        helper.runMigrationsAndValidate(DB, AppDatabase.VERSION, true, *ALL_MIGRATIONS).use { db ->
            // 내역: 예전 칸은 그대로, 새 칸은 기본값.
            db.query(
                "SELECT amount, category_id, account_id, memo, merchant, split_group_id, recurring_id, " +
                    "excluded_from_performance FROM transactions WHERE id = 1"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(8000L, it.getLong(0))
                assertEquals(1L, it.getLong(1))
                assertEquals(3L, it.getLong(2))
                assertEquals("점심", it.getString(3))
                assertEquals("", it.getString(4))
                assertTrue(it.isNull(5))
                assertTrue(it.isNull(6))
                assertEquals(0, it.getInt(7))
            }

            // v4: 이미 있던 자산은 종류별 색을 받는다.
            assertEquals(DefaultData.ACCOUNT_COLOR_CASH, db.longOf("SELECT color_argb FROM accounts WHERE id = 1").toInt())
            assertEquals(DefaultData.ACCOUNT_COLOR_BANK, db.longOf("SELECT color_argb FROM accounts WHERE id = 2").toInt())
            assertEquals(DefaultData.ACCOUNT_COLOR_CARD, db.longOf("SELECT color_argb FROM accounts WHERE id = 3").toInt())

            // 한도·상위·실적 칸은 모두 비어 있다(예전처럼 없음).
            db.query(
                "SELECT limit_amount, parent_id, performance_target, performance_steps, " +
                    "performance_start_day, performance_benefits FROM accounts WHERE id = 3"
            ).use {
                assertTrue(it.moveToFirst())
                (0 until it.columnCount).forEach { i -> assertTrue("${it.getColumnName(i)} 이 비어 있지 않다", it.isNull(i)) }
            }

            // v11: "환불"이 "기타" 바로 앞에 들어가고 "기타"는 맨 끝에 남는다.
            assertEquals(
                listOf("급여", DefaultData.REFUND_CATEGORY, "기타"),
                db.strings("SELECT name FROM categories WHERE type = 'INCOME' ORDER BY sort_order"),
            )
        }
    }

    @Test
    fun 환불_카테고리가_이미_있으면_더_넣지_않는다() {
        helper.createDatabase(DB, 10).use { db ->
            db.execSQL(
                "INSERT INTO categories (name, type, color_argb, sort_order) VALUES ('${DefaultData.REFUND_CATEGORY}', 'INCOME', 0, 0)"
            )
        }

        helper.runMigrationsAndValidate(DB, 11, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(
                1L,
                db.longOf("SELECT COUNT(*) FROM categories WHERE name = '${DefaultData.REFUND_CATEGORY}'"),
            )
        }
    }

    private fun SupportSQLiteDatabase.longOf(sql: String): Long =
        query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> =
        query(sql).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    private companion object {
        const val DB = "migration-test.db"
    }
}
