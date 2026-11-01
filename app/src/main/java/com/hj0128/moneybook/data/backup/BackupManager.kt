package com.hj0128.moneybook.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.hj0128.moneybook.data.local.AppDatabase
import com.hj0128.moneybook.data.local.dao.TransactionDao
import com.hj0128.moneybook.data.model.ExportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 고른 백업 파일을 열어 본 결과. 복원하기 전에 무엇이 들어 있는지 보여주는 데 쓴다. */
data class BackupInfo(
    val transactionCount: Int,
    val lastDateEpochDay: Long?,
)

/**
 * 백업과 내보내기.
 *
 * - CSV: 엑셀·구글 시트에서 열어 보는 용도. 사람이 읽는 파일이라 복원에는 쓰지 않는다.
 *   카테고리 색, 한도, 순서 같은 것은 담기지 않는다.
 * - 백업 파일: DB 파일 그대로. 카테고리·자산·한도·나눈 결제까지 앱 데이터 전체를 되살린다.
 *   형식을 따로 만들지 않고 DB 를 통째로 복사하면, 앞으로 컬럼이 늘어나도 백업 코드는 손댈 일이 없다.
 *   예전 버전 백업은 복원 후 앱이 켜질 때 Room 마이그레이션이 최신으로 올려 준다.
 *
 * 파일은 안드로이드 저장소 선택 창(SAF)으로 사용자가 고른 곳에 쓴다. 다운로드 폴더든
 * 구글 드라이브든 고를 수 있고, 앱은 저장소 권한이 필요 없다.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
) {

    private val dbFile: File get() = context.getDatabasePath(AppDatabase.DB_NAME)

    /** 모든 내역을 CSV 로. 돌려주는 값은 내보낸 줄 수. */
    suspend fun exportCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = transactionDao.exportRows()
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("파일을 열 수 없습니다.")
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                // BOM 이 없으면 엑셀이 한글을 깨뜨린다(UTF-8 인 줄 모르고 열어서).
                writer.write("﻿")
                writer.write(csvLine(CSV_HEADER))
                val groupNumbers = rows.mapNotNull { it.splitGroupId }.distinct()
                    .withIndex().associate { (index, id) -> id to index + 1 }
                rows.forEach { row -> writer.write(csvLine(row.toCells(groupNumbers))) }
            }
            rows.size
        }
    }

    /**
     * 백업 파일 만들기.
     *
     * Room 은 WAL 모드라 최근에 쓴 내용이 -wal 파일에만 있을 수 있다. 본 파일만 복사하면
     * 방금 넣은 내역이 빠지므로, 먼저 체크포인트로 본 파일에 다 밀어 넣고 복사한다.
     */
    suspend fun createBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val output = context.contentResolver.openOutputStream(uri) ?: error("파일을 열 수 없습니다.")
            output.use { out -> dbFile.inputStream().use { it.copyTo(out) } }
            Unit
        }
    }

    /**
     * 고른 파일이 이 앱의 백업인지 확인하고, 복원할 수 있게 임시 파일로 받아 둔다.
     *
     * 아무 파일이나 덮어쓰면 앱이 켜지지 않는다. SQLite 파일인지, 필요한 표가 있는지,
     * 이 앱보다 새 버전에서 만든 백업은 아닌지(그러면 마이그레이션 경로가 없다)를 본다.
     */
    suspend fun inspect(uri: Uri): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val staged = stagedFile()
            val input = context.contentResolver.openInputStream(uri) ?: error("파일을 열 수 없습니다.")
            input.use { inp -> staged.outputStream().use { inp.copyTo(it) } }
            try {
                readInfo(staged)
            } catch (e: Throwable) {
                staged.delete()
                throw e
            }
        }
    }

    private fun readInfo(file: File): BackupInfo {
        // SQLite 파일이 아니면 여는 순간이 아니라 처음 읽을 때 실패한다. 둘 다 같은 말로 알린다.
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { readInfo(it) }
        } catch (e: SQLiteException) {
            throw IllegalArgumentException("백업 파일이 아닙니다.", e)
        }
    }

    private fun readInfo(db: SQLiteDatabase): BackupInfo {
        return db.let {
            val version = it.version
            require(version in 1..AppDatabase.VERSION) {
                if (version > AppDatabase.VERSION) "더 새 버전의 앱에서 만든 백업입니다. 앱을 업데이트해 주세요."
                else "백업 파일이 아닙니다."
            }
            val tables = it.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            require(REQUIRED_TABLES.all { name -> name in tables }) { "가계부 백업 파일이 아닙니다." }
            it.rawQuery("SELECT COUNT(*), MAX(date_epoch_day) FROM transactions", null).use { c ->
                c.moveToFirst()
                BackupInfo(
                    transactionCount = c.getInt(0),
                    lastDateEpochDay = if (c.isNull(1)) null else c.getLong(1),
                )
            }
        }
    }

    /**
     * [inspect] 로 받아 둔 백업으로 바꾸고 앱을 다시 켠다.
     *
     * 열려 있는 DB 를 바꿔치기할 수는 없어서 닫고 파일을 갈아 끼운 뒤 프로세스를 새로 띄운다.
     * 지금 데이터는 지우기 전에 앱 안쪽(기기 백업에서 빠지는 곳)에 한 벌 남겨 둔다.
     * 잘못 고른 백업으로 덮어썼을 때 되살릴 마지막 길이다.
     */
    suspend fun restoreStaged(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staged = stagedFile()
            check(staged.exists()) { "복원할 백업을 다시 골라 주세요." }
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            dbFile.copyTo(File(context.noBackupFilesDir, BEFORE_RESTORE_NAME), overwrite = true)
            database.close()
            // 이전 DB 의 WAL 이 남아 있으면 새 파일 위에 옛 내용이 덧씌워진다.
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            staged.copyTo(dbFile, overwrite = true)
            staged.delete()
            Unit
        }
    }

    /** 복원 뒤 새 DB 로 앱을 처음부터 다시 띄운다. */
    fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val restart = Intent.makeRestartActivityTask(launch.component)
        context.startActivity(restart)
        Runtime.getRuntime().exit(0)
    }

    private fun stagedFile() = File(context.cacheDir, STAGED_NAME)

    private fun ExportRow.toCells(groupNumbers: Map<Long, Int>): List<String> = listOf(
        date.toString(),
        type.label,
        amount.toString(),
        parentName.orEmpty(),
        childName.orEmpty(),
        accountName.orEmpty(),
        toAccountName.orEmpty(),
        merchant,
        memo,
        // DB 의 묶음 번호는 의미 없는 큰 수라 파일 안에서 1, 2, 3 … 으로 다시 매긴다.
        splitGroupId?.let { groupNumbers[it]?.toString() }.orEmpty(),
    )

    private fun csvLine(cells: List<String>): String =
        cells.joinToString(",", postfix = "\r\n") { cell ->
            if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + cell.replace("\"", "\"\"") + "\""
            } else {
                cell
            }
        }

    private companion object {
        val CSV_HEADER = listOf("날짜", "구분", "금액", "대분류", "소분류", "결제수단", "입금 자산", "거래처", "메모", "나눈 결제")
        val REQUIRED_TABLES = listOf("transactions", "categories", "accounts")
        const val STAGED_NAME = "restore-staged.db"
        const val BEFORE_RESTORE_NAME = "before-restore.db"
    }
}
