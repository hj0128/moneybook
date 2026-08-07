package com.hj0128.moneybook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hj0128.moneybook.ui.entry.EntryScreen
import com.hj0128.moneybook.ui.ledger.LedgerScreen

object Routes {
    const val LEDGER = "ledger"
    const val ENTRY = "entry"
}

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LEDGER,
        modifier = modifier,
    ) {
        composable(Routes.LEDGER) {
            LedgerScreen(
                onAddTransaction = { navController.navigate(Routes.ENTRY) },
            )
        }
        composable(Routes.ENTRY) {
            EntryScreen(
                onClose = { navController.popBackStack() },
                // 저장 후에도 목록으로 돌아간다. 목록은 Flow 라서 따로 새로고침할 필요가 없다.
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
