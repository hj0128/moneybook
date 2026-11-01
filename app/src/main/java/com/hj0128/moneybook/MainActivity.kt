package com.hj0128.moneybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.hj0128.moneybook.ui.navigation.AppNavHost
import com.hj0128.moneybook.ui.theme.MoneyBookTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MoneyBookTheme {
                AppNavHost()
            }
        }
    }

    /**
     * 앱이 앞으로 나올 때마다 차례가 온 고정 지출을 넣는다.
     *
     * 처음 켤 때만 하면 앱을 띄워 둔 채 날짜가 바뀐 경우를 놓친다. 넣을 것이 없으면 쿼리 한 번으로 끝난다.
     * 목록은 Flow 라서 넣고 나면 저절로 다시 그려진다.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            (application as MoneyBookApp).container.ledgerRepository.applyRecurring()
        }
    }
}
