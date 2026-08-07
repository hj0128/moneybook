package com.hj0128.moneybook.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hj0128.moneybook.data.model.DaySortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 사용자가 고른 화면 설정. 거래 데이터가 아니라 취향이라 Room 이 아닌 DataStore 에 둔다.
 *
 * 정렬 순서 같은 건 한 번 고르면 계속 유지돼야 한다.
 * 앱을 껐다 켤 때마다 기본값으로 돌아가면 매번 다시 누르게 된다.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val daySortOrder: Flow<DaySortOrder> = dataStore.data.map { preferences ->
        // 저장된 값이 없거나 알 수 없는 값이면 기본값(최신순)으로 떨어진다.
        preferences[KEY_DAY_SORT_ORDER]
            ?.let { name -> runCatching { DaySortOrder.valueOf(name) }.getOrNull() }
            ?: DaySortOrder.NEWEST_FIRST
    }

    suspend fun setDaySortOrder(order: DaySortOrder) {
        dataStore.edit { preferences ->
            preferences[KEY_DAY_SORT_ORDER] = order.name
        }
    }

    private companion object {
        val KEY_DAY_SORT_ORDER = stringPreferencesKey("day_sort_order")
    }
}
