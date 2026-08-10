package com.hj0128.moneybook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hj0128.moneybook.data.local.dao.AccountDao
import com.hj0128.moneybook.data.local.dao.CategoryDao
import com.hj0128.moneybook.data.local.dao.TransactionDao
import com.hj0128.moneybook.data.local.entity.AccountEntity
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao

    companion object {
        private const val DB_NAME = "household.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
