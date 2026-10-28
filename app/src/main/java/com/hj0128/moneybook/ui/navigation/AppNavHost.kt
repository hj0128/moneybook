package com.hj0128.moneybook.ui.navigation

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hj0128.moneybook.data.model.LedgerPeriod
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.ui.account.AccountManagerScreen
import com.hj0128.moneybook.ui.category.CategoryManagerScreen
import com.hj0128.moneybook.ui.entry.EntryScreen
import com.hj0128.moneybook.ui.ledger.CategoryDetailScreen
import com.hj0128.moneybook.ui.ledger.DetailSource
import com.hj0128.moneybook.ui.ledger.LedgerScreen
import java.time.YearMonth

object Routes {
    const val LEDGER = "ledger"
    const val ENTRY = "entry"

    /** 기존 내역 수정. 입력 화면을 그대로 쓰고 채워둘 내역 id 만 넘긴다. */
    const val ENTRY_EDIT = "entry/{transactionId}"

    fun entryEdit(transactionId: Long): String = "entry/$transactionId"
    const val CATEGORY_MANAGER = "categories"
    const val ACCOUNT_MANAGER = "accounts"

    /**
     * 카테고리 상세. 보고 있던 기간을 같이 넘긴다.
     *
     * 기간을 통째로 넘기는 대신 연/월 정수로 쪼갠 이유는, 프로세스가 죽었다 살아나도
     * 문자열 파싱 없이 그대로 복원되고 딥링크로도 읽기 쉬운 형태이기 때문이다.
     * month 가 0 이면 그 해 전체다. 실제 달은 1~12 라 겹치지 않는다.
     */
    const val CATEGORY_DETAIL = "category/{categoryId}/{year}/{month}"

    fun categoryDetail(categoryId: Long, period: LedgerPeriod): String =
        "category/$categoryId/${period.toPathSegments()}"

    /** 미분류는 카테고리 id 가 없으니 수입/지출로 좁힌다. */
    const val UNCATEGORIZED = "uncategorized/{type}/{year}/{month}"

    fun uncategorized(type: TransactionType, period: LedgerPeriod): String =
        "uncategorized/${type.name}/${period.toPathSegments()}"

    private const val WHOLE_YEAR = 0

    private fun LedgerPeriod.toPathSegments(): String = when (this) {
        is LedgerPeriod.Month -> "${yearMonth.year}/${yearMonth.monthValue}"
        is LedgerPeriod.Year -> "$year/$WHOLE_YEAR"
    }

    fun periodFrom(year: Int, month: Int): LedgerPeriod =
        if (month == WHOLE_YEAR) LedgerPeriod.Year(year) else LedgerPeriod.Month(YearMonth.of(year, month))
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
                onOpenCategory = { categoryId, period ->
                    navController.navigate(Routes.categoryDetail(categoryId, period))
                },
                onOpenUncategorized = { type, period ->
                    navController.navigate(Routes.uncategorized(type, period))
                },
                onManageCategories = { navController.navigate(Routes.CATEGORY_MANAGER) },
                onManageAccounts = { navController.navigate(Routes.ACCOUNT_MANAGER) },
                onOpenTransaction = { navController.navigate(Routes.entryEdit(it)) },
            )
        }
        composable(
            route = Routes.ENTRY_EDIT,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
        ) { backStackEntry ->
            EntryScreen(
                onClose = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                editingId = backStackEntry.arguments?.getLong("transactionId"),
            )
        }
        composable(Routes.CATEGORY_MANAGER) {
            CategoryManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNT_MANAGER) {
            AccountManagerScreen(onBack = { navController.popBackStack() })
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
                source = DetailSource.Category(args?.getLong("categoryId") ?: 0L),
                period = args.toPeriod(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.UNCATEGORIZED,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            val transactionType = runCatching {
                TransactionType.valueOf(args?.getString("type").orEmpty())
            }.getOrDefault(TransactionType.EXPENSE)
            CategoryDetailScreen(
                source = DetailSource.Uncategorized(transactionType),
                period = args.toPeriod(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun Bundle?.toPeriod(): LedgerPeriod = Routes.periodFrom(
    year = this?.getInt("year") ?: YearMonth.now().year,
    month = this?.getInt("month") ?: YearMonth.now().monthValue,
)
