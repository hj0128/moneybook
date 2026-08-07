package com.hj0128.moneybook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hj0128.moneybook.ui.entry.EntryScreen
import com.hj0128.moneybook.ui.ledger.CategoryDetailScreen
import com.hj0128.moneybook.ui.ledger.LedgerScreen
import java.time.YearMonth

object Routes {
    const val LEDGER = "ledger"
    const val ENTRY = "entry"

    /**
     * 카테고리 상세. 보고 있던 달을 같이 넘긴다.
     *
     * YearMonth 를 통째로 넘기는 대신 연/월 정수로 쪼갠 이유는, 프로세스가 죽었다 살아나도
     * 문자열 파싱 없이 그대로 복원되고 딥링크로도 읽기 쉬운 형태이기 때문이다.
     */
    const val CATEGORY_DETAIL = "category/{categoryId}/{year}/{month}"

    fun categoryDetail(categoryId: Long, yearMonth: YearMonth): String =
        "category/$categoryId/${yearMonth.year}/${yearMonth.monthValue}"
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
                onOpenCategory = { categoryId, yearMonth ->
                    navController.navigate(Routes.categoryDetail(categoryId, yearMonth))
                },
            )
        }
        composable(Routes.ENTRY) {
            EntryScreen(
                onClose = { navController.popBackStack() },
                // 저장 후에도 목록으로 돌아간다. 목록은 Flow 라서 따로 새로고침할 필요가 없다.
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.CATEGORY_DETAIL,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.LongType },
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            CategoryDetailScreen(
                categoryId = args?.getLong("categoryId") ?: 0L,
                yearMonth = YearMonth.of(
                    args?.getInt("year") ?: YearMonth.now().year,
                    args?.getInt("month") ?: YearMonth.now().monthValue,
                ),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
