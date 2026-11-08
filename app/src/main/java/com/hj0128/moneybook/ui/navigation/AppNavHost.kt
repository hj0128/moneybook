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
import com.hj0128.moneybook.ui.backup.BackupScreen
import com.hj0128.moneybook.ui.budget.BudgetScreen
import com.hj0128.moneybook.ui.category.CategoryManagerScreen
import com.hj0128.moneybook.ui.entry.EntryScreen
import com.hj0128.moneybook.ui.ledger.CategoryDetailScreen
import com.hj0128.moneybook.ui.ledger.DetailSource
import com.hj0128.moneybook.ui.ledger.LedgerScreen
import com.hj0128.moneybook.ui.recurring.RecurringScreen
import com.hj0128.moneybook.ui.search.SearchScreen
import java.time.LocalDate
import java.time.YearMonth

object Routes {
    const val LEDGER = "ledger"
    const val ENTRY = "entry"

    /** 기존 내역 수정. 입력 화면을 그대로 쓰고 채워둘 내역 id 만 넘긴다. */
    const val ENTRY_EDIT = "entry/{transactionId}"

    fun entryEdit(transactionId: Long): String = "entry/$transactionId"
    const val CATEGORY_MANAGER = "categories"
    const val ACCOUNT_MANAGER = "accounts"
    const val BACKUP = "backup"
    const val RECURRING = "recurring"
    const val SEARCH = "search"
    const val BUDGET = "budget"

    /** 고정 지출 추가. 입력 화면을 반복을 켠 채로 연다. */
    const val RECURRING_NEW = "recurring/new"

    /** 고정 지출 수정. 입력 화면에 그 고정 지출을 채워서 연다. */
    const val RECURRING_EDIT = "recurring/edit/{recurringId}"

    fun recurringEdit(recurringId: Long): String = "recurring/edit/$recurringId"

    /**
     * 직접 고른 기간은 연/월 두 칸으로 나타낼 수 없어 시작·끝 날짜(epoch day)를 뒤에 붙인다.
     * 연/월 자리에는 시작한 달을 넣어 둔다. 날짜가 없으면 예전처럼 연/월로 읽는다.
     */
    const val RANGE_QUERY = "?from={from}&to={to}"
    const val NO_DATE = Long.MIN_VALUE

    /**
     * 카테고리 상세. 보고 있던 기간을 같이 넘긴다.
     *
     * 기간을 통째로 넘기는 대신 연/월 정수로 쪼갠 이유는, 프로세스가 죽었다 살아나도
     * 문자열 파싱 없이 그대로 복원되고 딥링크로도 읽기 쉬운 형태이기 때문이다.
     * month 가 0 이면 그 해 전체다. 실제 달은 1~12 라 겹치지 않는다.
     */
    const val CATEGORY_DETAIL = "category/{categoryId}/{year}/{month}$RANGE_QUERY"

    fun categoryDetail(categoryId: Long, period: LedgerPeriod): String =
        "category/$categoryId/${period.toPathSegments()}"

    /** 미분류는 카테고리 id 가 없으니 수입/지출로 좁힌다. */
    const val UNCATEGORIZED = "uncategorized/{type}/{year}/{month}$RANGE_QUERY"

    fun uncategorized(type: TransactionType, period: LedgerPeriod): String =
        "uncategorized/${type.name}/${period.toPathSegments()}"

    /** 결제수단 상세. 자산은 수입·지출이 다 오가므로 어느 쪽을 볼지도 넘긴다. */
    const val ACCOUNT_DETAIL = "account/{accountId}/{type}/{year}/{month}$RANGE_QUERY"

    fun accountDetail(accountId: Long, type: TransactionType, period: LedgerPeriod): String =
        "account/$accountId/${type.name}/${period.toPathSegments()}"

    private const val WHOLE_YEAR = 0

    private fun LedgerPeriod.toPathSegments(): String = when (this) {
        is LedgerPeriod.Month -> "${yearMonth.year}/${yearMonth.monthValue}"
        is LedgerPeriod.Year -> "$year/$WHOLE_YEAR"
        is LedgerPeriod.Range ->
            "${start.year}/${start.monthValue}?from=${start.toEpochDay()}&to=${end.toEpochDay()}"
    }

    fun periodFrom(year: Int, month: Int, from: Long = NO_DATE, to: Long = NO_DATE): LedgerPeriod = when {
        from != NO_DATE && to != NO_DATE && to >= from ->
            LedgerPeriod.Range(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to))
        month == WHOLE_YEAR -> LedgerPeriod.Year(year)
        else -> LedgerPeriod.Month(YearMonth.of(year, month))
    }
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
                onOpenAccount = { accountId, type, period ->
                    navController.navigate(Routes.accountDetail(accountId, type, period))
                },
                onManageCategories = { navController.navigate(Routes.CATEGORY_MANAGER) },
                onManageAccounts = { navController.navigate(Routes.ACCOUNT_MANAGER) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenBudget = { navController.navigate(Routes.BUDGET) },
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
                onOpenRecurring = { navController.navigate(Routes.recurringEdit(it)) },
            )
        }
        composable(Routes.CATEGORY_MANAGER) {
            CategoryManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNT_MANAGER) {
            AccountManagerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BUDGET) {
            BudgetScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { navController.navigate(Routes.entryEdit(it)) },
            )
        }
        composable(Routes.RECURRING) {
            RecurringScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Routes.RECURRING_NEW) },
                onOpen = { navController.navigate(Routes.recurringEdit(it)) },
            )
        }
        composable(Routes.RECURRING_NEW) {
            EntryScreen(
                onClose = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                startRepeating = true,
            )
        }
        composable(
            route = Routes.RECURRING_EDIT,
            arguments = listOf(navArgument("recurringId") { type = NavType.LongType }),
        ) { backStackEntry ->
            EntryScreen(
                onClose = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                recurringId = backStackEntry.arguments?.getLong("recurringId"),
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
            ) + rangeArguments,
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            CategoryDetailScreen(
                source = DetailSource.Category(args?.getLong("categoryId") ?: 0L),
                period = args.toPeriod(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ACCOUNT_DETAIL,
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType },
                navArgument("type") { type = NavType.StringType },
                navArgument("year") { type = NavType.IntType },
                navArgument("month") { type = NavType.IntType },
            ) + rangeArguments,
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            CategoryDetailScreen(
                source = DetailSource.Account(
                    id = args?.getLong("accountId") ?: 0L,
                    type = args.toTransactionType(),
                ),
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
            ) + rangeArguments,
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            CategoryDetailScreen(
                source = DetailSource.Uncategorized(args.toTransactionType()),
                period = args.toPeriod(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun Bundle?.toTransactionType(): TransactionType = runCatching {
    TransactionType.valueOf(this?.getString("type").orEmpty())
}.getOrDefault(TransactionType.EXPENSE)

private fun Bundle?.toPeriod(): LedgerPeriod = Routes.periodFrom(
    year = this?.getInt("year") ?: YearMonth.now().year,
    month = this?.getInt("month") ?: YearMonth.now().monthValue,
    from = this?.getLong("from", Routes.NO_DATE) ?: Routes.NO_DATE,
    to = this?.getLong("to", Routes.NO_DATE) ?: Routes.NO_DATE,
)

/** 상세 화면 세 곳이 함께 받는 직접 고른 기간의 양 끝. 연/월로 열면 비어 있다. */
private val rangeArguments = listOf(
    navArgument("from") {
        type = NavType.LongType
        defaultValue = Routes.NO_DATE
    },
    navArgument("to") {
        type = NavType.LongType
        defaultValue = Routes.NO_DATE
    },
)
