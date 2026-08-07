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
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
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
