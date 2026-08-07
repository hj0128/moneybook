package com.hj0128.moneybook.di

import android.content.Context
import com.hj0128.moneybook.data.local.AppDatabase
import com.hj0128.moneybook.data.prefs.SettingsRepository
import com.hj0128.moneybook.data.repository.LedgerRepository

/**
 * 수동 DI 컨테이너.
 *
 * 화면 몇 개짜리 앱에 Hilt 를 넣으면 빌드 설정만 복잡해진다.
 * 의존성이 늘어나 손이 아프기 시작할 때 Hilt 로 옮기면 된다(생성 지점이 여기 한 곳뿐이라 옮기기 쉽다).
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)

    val settingsRepository: SettingsRepository = SettingsRepository(context)

    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
        )
    }
}
