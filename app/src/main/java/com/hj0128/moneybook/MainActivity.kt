package com.hj0128.moneybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hj0128.moneybook.ui.navigation.AppNavHost
import com.hj0128.moneybook.ui.theme.MoneyBookTheme

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
}
