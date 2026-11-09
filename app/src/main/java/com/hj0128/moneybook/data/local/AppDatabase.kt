package com.hj0128.moneybook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hj0128.moneybook.data.local.dao.AccountDao
import com.hj0128.moneybook.data.local.dao.BudgetDao
import com.hj0128.moneybook.data.local.dao.CategoryDao
import com.hj0128.moneybook.data.local.dao.RecurringDao
import com.hj0128.moneybook.data.local.dao.TransactionDao
import com.hj0128.moneybook.data.local.entity.AccountEntity
import com.hj0128.moneybook.data.local.entity.BudgetEntity
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.local.entity.PerformanceExclusionEntity
import com.hj0128.moneybook.data.local.entity.RecurringEntity
import com.hj0128.moneybook.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        RecurringEntity::class,
        BudgetEntity::class,
        PerformanceExclusionEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun recurringDao(): RecurringDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val DB_NAME = "household.db"

        /** 백업을 복원할 때 이보다 새 버전에서 만든 파일은 받지 않는다(내려가는 마이그레이션이 없다). */
        const val VERSION = 13

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                // 외래키 제약을 실제로 켜준다(Room 은 기본으로 켜지 않는다).
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DefaultData.seed(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
    }
}

/**
 * v2: 카테고리에 2뎁스(대분류 > 소분류)를 연다.
 *
 * 기존 카테고리는 parent_id 가 null 이라 전부 대분류로 남는다.
 * 쪼갤 카테고리만 나중에 소분류를 붙이면 되므로 데이터를 손댈 필요가 없다.
 *
 * 테이블을 다시 만들지 않고 컬럼만 더한다. categories 를 DROP 하면
 * transactions 가 걸어둔 외래키까지 흔들린다.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE categories ADD COLUMN parent_id INTEGER " +
                "REFERENCES categories(id) ON DELETE SET NULL"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parent_id ON categories(parent_id)")
    }
}

/**
 * v3: 사용처를 따로 받는다.
 *
 * 지금까지는 "어디서 썼는지"를 메모에 적을 수밖에 없었다. 메모는 자유 서술이라
 * 목록에서 무엇이었는지 알아보기 어렵고, 나중에 사용처로 묶어 보는 것도 못 한다.
 *
 * 기존 내역은 빈 문자열이 된다. 그때는 예전처럼 카테고리 이름이 제목으로 쓰인다.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN merchant TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v4: 자산에도 색을 준다.
 *
 * 기본값을 그대로 두면 이미 있던 자산이 전부 같은 색이 되어 선택 화면에서 구분이 안 된다.
 * 종류별로 한 번 칠해 두고, 이후에는 자산마다 따로 고칠 수 있게 한다.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE accounts ADD COLUMN color_argb INTEGER NOT NULL DEFAULT " +
                DefaultData.ACCOUNT_COLOR_CASH
        )
        db.execSQL(
            "UPDATE accounts SET color_argb = ${DefaultData.ACCOUNT_COLOR_BANK} WHERE type = 'BANK'"
        )
        db.execSQL(
            "UPDATE accounts SET color_argb = ${DefaultData.ACCOUNT_COLOR_CARD} WHERE type = 'CARD'"
        )
    }
}

/**
 * v5: 결제수단에 사용 한도를 둔다(복지포인트 연 100만원 같은 것).
 *
 * 둘 다 null 을 허용하는 컬럼이라 기존 자산은 한도 없음으로 남는다. 데이터를 손댈 필요가 없다.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN limit_amount INTEGER")
        db.execSQL("ALTER TABLE accounts ADD COLUMN limit_period TEXT")
    }
}

/**
 * v6: 한 결제를 여러 줄로 나눠 저장한다(다이소 7,500원 = 생필품 1,500원 + 식비 6,000원).
 *
 * 기존 내역은 null 이라 나누지 않은 결제로 남는다. 인덱스 이름은 Room 이 엔티티에서 만드는 이름과
 * 같아야 스키마 검증을 통과한다.
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN split_group_id INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transactions_split_group_id ON transactions(split_group_id)"
        )
    }
}

/**
 * v7: 고정 지출. 매주·매월·매년 정한 날에 내역을 자동으로 넣는다.
 *
 * 새 표 하나와, 자동으로 들어간 내역이 어느 고정 지출에서 왔는지 적을 칸을 더한다.
 * 기존 내역은 null 이라 손으로 넣은 내역으로 남는다. 표 만드는 SQL 은 Room 이 내보낸 스키마와 같아야 한다.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recurring` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, `category_id` INTEGER, `account_id` INTEGER NOT NULL, " +
                "`to_account_id` INTEGER, `merchant` TEXT NOT NULL, `memo` TEXT NOT NULL, " +
                "`repeat_unit` TEXT NOT NULL, `anchor_epoch_day` INTEGER NOT NULL, `next_epoch_day` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`to_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_category_id` ON `recurring` (`category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_account_id` ON `recurring` (`account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_to_account_id` ON `recurring` (`to_account_id`)")
        db.execSQL(
            "ALTER TABLE transactions ADD COLUMN recurring_id INTEGER " +
                "REFERENCES recurring(id) ON DELETE SET NULL"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transactions_recurring_id ON transactions(recurring_id)"
        )
    }
}

/**
 * v8: 카테고리 예산. "이 카테고리는 이 달부터 얼마"를 적는 표를 더한다.
 *
 * 새 표라 기존 데이터는 손댈 것이 없다. 표 만드는 SQL 은 Room 이 내보낸 스키마와 같아야 한다.
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(BUDGETS_CREATE_SQL)
    }
}

internal const val BUDGETS_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `budgets` (" +
    "`category_id` INTEGER NOT NULL, `year_month` INTEGER NOT NULL, `amount` INTEGER, " +
    "PRIMARY KEY(`category_id`, `year_month`), " +
    "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

/**
 * v9: 자산에 상위/하위(체크카드 > A카드/B카드). 카테고리의 parent_id 와 같은 구조다.
 *
 * null 을 허용하는 칸이라 기존 자산은 모두 상위로 남고, 순서 번호도 그대로 상위 순서가 된다.
 */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `accounts` ADD COLUMN `parent_id` INTEGER " +
                "REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_parent_id` ON `accounts` (`parent_id`)")
    }
}

/**
 * v10: 카드 실적. 자산에 실적 목표 칸 둘, 실적에서 뺄 카테고리를 적는 표,
 * 내역 하나(와 고정 지출이 넣을 내역)를 실적에서 빼는 칸을 더한다.
 *
 * null 을 허용하는 칸과 새 표라 기존 자산은 실적 없음으로 남는다. 표 만드는 SQL 은 Room 이 내보낸 스키마와 같아야 한다.
 */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `performance_target` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `performance_period` TEXT")
        db.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `excluded_from_performance` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `recurring` ADD COLUMN `excluded_from_performance` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `account_performance_exclusions` (" +
                "`account_id` INTEGER NOT NULL, `category_id` INTEGER NOT NULL, " +
                "PRIMARY KEY(`account_id`, `category_id`), " +
                "FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_account_performance_exclusions_category_id` " +
                "ON `account_performance_exclusions` (`category_id`)"
        )
    }
}

/**
 * v11: 수입 카테고리에 "환불"을 더한다. 표 구조는 그대로다.
 *
 * 처음 깔 때 들어가는 기본 카테고리에 더했지만, 이미 쓰던 사람은 그 목록을 다시 받지 않는다.
 * 같은 이름이 이미 있으면 넣지 않는다(직접 만들어 쓰던 사람). "기타"가 있으면 그 바로 앞에 둔다.
 * "기타"는 늘 맨 끝이어야 고를 때 찾기 쉽다.
 */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val exists = db.query(
            "SELECT 1 FROM categories WHERE type = 'INCOME' AND name = ?",
            arrayOf<Any>(DefaultData.REFUND_CATEGORY),
        ).use { it.moveToFirst() }
        if (exists) return
        val etcOrder = db.query(
            "SELECT sort_order FROM categories WHERE type = 'INCOME' AND parent_id IS NULL AND name = '기타' LIMIT 1"
        ).use { if (it.moveToFirst()) it.getInt(0) else null }
        val order = if (etcOrder != null) {
            db.execSQL(
                "UPDATE categories SET sort_order = sort_order + 1 " +
                    "WHERE type = 'INCOME' AND parent_id IS NULL AND sort_order >= ?",
                arrayOf<Any>(etcOrder),
            )
            etcOrder
        } else {
            db.query(
                "SELECT IFNULL(MAX(sort_order), -1) + 1 FROM categories WHERE type = 'INCOME' AND parent_id IS NULL"
            ).use { it.moveToFirst(); it.getInt(0) }
        }
        db.execSQL(
            "INSERT INTO categories (name, type, color_argb, sort_order) VALUES (?, 'INCOME', ?, ?)",
            arrayOf<Any>(DefaultData.REFUND_CATEGORY, DefaultData.REFUND_COLOR.toInt(), order),
        )
    }
}

/** v12: 실적 단계. 1단계 위의 단계를 적는 칸 하나를 더한다. 비어 있으면 예전처럼 목표 하나다. */
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `performance_steps` TEXT")
    }
}

/** v13: 카드마다 매월 실적을 세기 시작하는 날. 비어 있으면 예전처럼 1일이다. */
internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `performance_start_day` INTEGER")
    }
}
