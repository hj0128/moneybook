package com.hj0128.moneybook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = GreenGrey80,
    tertiary = Sand80,
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    secondary = GreenGrey40,
    tertiary = Sand40,
)

/** 수입/지출 색은 MaterialTheme 색상표에 없으므로 따로 내려준다. */
data class LedgerColors(
    val income: Color,
    val expense: Color,
)

val LocalLedgerColors: ProvidableCompositionLocal<LedgerColors> =
    staticCompositionLocalOf { LedgerColors(IncomeBlue, ExpenseRed) }

@Composable
fun MoneyBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 안드로이드 12+ 의 배경화면 기반 동적 색상
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val ledgerColors = if (darkTheme) {
        LedgerColors(income = IncomeBlueDark, expense = ExpenseRedDark)
    } else {
        LedgerColors(income = IncomeBlue, expense = ExpenseRed)
    }

    CompositionLocalProvider(LocalLedgerColors provides ledgerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
