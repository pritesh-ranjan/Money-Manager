package com.moneymanager.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val OneUiShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),      // Signature One UI 9 squircle island
    extraLarge = RoundedCornerShape(28.dp) // One UI Bottom Dialogs & Sheets
)

@Composable
fun MoneyManagerTheme(
    content: @Composable () -> Unit
) {
    val themeMode by ThemePreferenceManager.themeMode.collectAsState()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val oneUiColors = if (isDark) OneUiDarkColors else OneUiLightColors

    val materialColorScheme = if (isDark) {
        darkColorScheme(
            primary = oneUiColors.primaryAccent,
            onPrimary = Color.White,
            primaryContainer = oneUiColors.primaryAccentContainer,
            onPrimaryContainer = Color.White,
            secondary = oneUiColors.secondaryCyan,
            onSecondary = Color.White,
            secondaryContainer = oneUiColors.surfaceElevated,
            onSecondaryContainer = oneUiColors.textPrimary,
            background = oneUiColors.background,
            onBackground = oneUiColors.textPrimary,
            surface = oneUiColors.surface,
            onSurface = oneUiColors.textPrimary,
            surfaceVariant = oneUiColors.surfaceVariant,
            onSurfaceVariant = oneUiColors.textSecondary,
            outline = oneUiColors.divider,
            outlineVariant = oneUiColors.divider,
            error = oneUiColors.expense,
            onError = Color.White,
            errorContainer = oneUiColors.expenseContainer,
            onErrorContainer = oneUiColors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = oneUiColors.primaryAccent,
            onPrimary = Color.White,
            primaryContainer = oneUiColors.primaryAccentContainer,
            onPrimaryContainer = oneUiColors.primaryAccent,
            secondary = oneUiColors.secondaryCyan,
            onSecondary = Color.White,
            secondaryContainer = oneUiColors.surfaceElevated,
            onSecondaryContainer = oneUiColors.textPrimary,
            background = oneUiColors.background,
            onBackground = oneUiColors.textPrimary,
            surface = oneUiColors.surface,
            onSurface = oneUiColors.textPrimary,
            surfaceVariant = oneUiColors.surfaceVariant,
            onSurfaceVariant = oneUiColors.textSecondary,
            outline = oneUiColors.divider,
            outlineVariant = oneUiColors.divider,
            error = oneUiColors.expense,
            onError = Color.White,
            errorContainer = oneUiColors.expenseContainer,
            onErrorContainer = oneUiColors.expense
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = oneUiColors.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalOneUiColors provides oneUiColors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            shapes = OneUiShapes,
            content = content
        )
    }
}
