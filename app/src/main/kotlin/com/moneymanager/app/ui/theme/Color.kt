package com.moneymanager.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class OneUiColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceVariant: Color,
    val bannerSurface: Color,
    val floatingSurface: Color,
    val border: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primaryAccent: Color,
    val primaryAccentContainer: Color,
    val secondaryCyan: Color,
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val switchTrackOff: Color,
    val badgeBlue: Color,
    val badgeIndigo: Color,
    val badgePurple: Color,
    val badgeOrange: Color,
    val badgeGreen: Color,
    val badgeTeal: Color,
    val cardShadow: Color,
    val isDark: Boolean
)

// Samsung One UI 9 Light Color Palette
val OneUiLightColors = OneUiColors(
    background = Color(0xFFF2F2F7),          // One UI neutral light canvas
    surface = Color(0xFFFFFFFF),             // Borderless white squircle islands
    surfaceElevated = Color(0xFFE5E5EC),     // Search capsule, chips, date pill
    surfaceVariant = Color(0xFFEBEBF2),      // Secondary pill buttons
    bannerSurface = Color(0xFFE8F1FF),       // Soft blue-tinted insight banner
    floatingSurface = Color(0xF2FFFFFF),     // Translucent search bar
    border = Color.Transparent,              // Tonal contrast only, no artificial borders
    divider = Color(0xFFE4E4EB),             // 0.5dp inset hairline dividers
    textPrimary = Color(0xFF121216),         // Crisp primary text
    textSecondary = Color(0xFF5C5C66),       // Secondary subtext
    textMuted = Color(0xFF8A8A96),           // Muted hints and axes
    primaryAccent = Color(0xFF0072DE),       // Samsung One UI signature Royal Blue
    primaryAccentContainer = Color(0xFFDCEEFF),
    secondaryCyan = Color(0xFF0097B2),
    income = Color(0xFF1E9E52),
    incomeContainer = Color(0xFFE1F7EA),
    expense = Color(0xFFD9383A),
    expenseContainer = Color(0xFFFFECEC),
    transfer = Color(0xFF0072DE),
    transferContainer = Color(0xFFE3F1FF),
    warning = Color(0xFFE07A00),
    warningContainer = Color(0xFFFFF2DF),
    switchTrackOff = Color(0xFFCCCCD4),
    badgeBlue = Color(0xFF2B75FF),
    badgeIndigo = Color(0xFF5E5CE6),
    badgePurple = Color(0xFF7B61FF),
    badgeOrange = Color(0xFFF26419),
    badgeGreen = Color(0xFF30B050),
    badgeTeal = Color(0xFF00B4D8),
    cardShadow = Color(0x00000000),
    isDark = false
)

// Samsung One UI Dark Color Palette (Reference Screenshots 1–5)
val OneUiDarkColors = OneUiColors(
    background = Color(0xFF000000),          // Pure OLED pitch black canvas
    surface = Color(0xFF17171A),             // Borderless dark slate squircle island
    surfaceElevated = Color(0xFF24252A),     // Active date pill [29], chips, search bar (Screenshot 1 & 5)
    surfaceVariant = Color(0xFF2C2C32),      // Compact pill buttons ("Set timer" in Screenshot 2)
    bannerSurface = Color(0xFF111827),       // Deep navy banner ("Build healthy digital habits")
    floatingSurface = Color(0xF2222227),     // Floating bottom search pill
    border = Color.Transparent,              // Strictly zero card borders in One UI dark mode
    divider = Color(0xFF222226),             // Ultra-subtle hairline divider
    textPrimary = Color(0xFFF5F5F7),         // High-contrast crisp white
    textSecondary = Color(0xFF9A9AA2),       // Muted gray subtitles ("Total screen time")
    textMuted = Color(0xFF6E6E75),           // Axis labels & inactive days
    primaryAccent = Color(0xFF387AFF),       // Vibrant Samsung One UI Blue ("Show categories", Wi-Fi subtext)
    primaryAccentContainer = Color(0xFF152C5B),
    secondaryCyan = Color(0xFF25C2E3),       // Gradient accent cyan
    income = Color(0xFF4CD964),              // One UI Digital Wellbeing vibrant green
    incomeContainer = Color(0xFF11331B),
    expense = Color(0xFFFF5A5F),             // Refined coral red
    expenseContainer = Color(0xFF3B1416),
    transfer = Color(0xFF387AFF),            // One UI Blue
    transferContainer = Color(0xFF152C5B),
    warning = Color(0xFFFF9F43),             // Warm amber
    warningContainer = Color(0xFF3B2605),
    switchTrackOff = Color(0xFF36363B),      // Unselected One UI toggle pill track
    badgeBlue = Color(0xFF2B75FF),           // Connections / Wi-Fi badge
    badgeIndigo = Color(0xFF5E5CE6),         // Modes & Routines / Sounds badge
    badgePurple = Color(0xFF7B61FF),         // Appearance badge
    badgeOrange = Color(0xFFF26419),         // Notifications / Backup badge
    badgeGreen = Color(0xFF30B050),          // Currencies / Vault badge
    badgeTeal = Color(0xFF00B4D8),           // Tags badge
    cardShadow = Color(0x00000000),
    isDark = true
)

val LocalOneUiColors = staticCompositionLocalOf { OneUiDarkColors }

object OneUiTheme {
    val colors: OneUiColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOneUiColors.current
}

// Legacy Aliases for compatibility
val AmoledBlack = Color(0xFF000000)
val AmoledSurface = Color(0xFF17171A)
val AmoledSurfaceElevated = Color(0xFF25252A)
val AmoledSurfaceVariant = Color(0xFF2C2C32)
val AmoledBorder = Color(0xFF2B2B30)
val AmoledBorderSubtle = Color(0xFF1F1F24)

val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFF9A9AA2)
val TextMuted = Color(0xFF72727A)

val IncomeGreen = Color(0xFF4CD964)
val IncomeGreenContainer = Color(0xFF11331B)
val ExpenseRed = Color(0xFFFF5A5F)
val ExpenseRedContainer = Color(0xFF3B1416)
val TransferBlue = Color(0xFF387AFF)
val TransferBlueContainer = Color(0xFF152C5B)
val WarningAmber = Color(0xFFFF9F43)
val WarningAmberContainer = Color(0xFF3B2605)

val AccentPrimary = Color(0xFF387AFF)
val AccentSecondary = Color(0xFF25C2E3)
val CoralAccent = Color(0xFF387AFF)
val IncomeBlue = Color(0xFF4CD964)
val DailyHeaderBadge = Color(0xFF25252A)
val SundayBadge = Color(0xFFFF5A5F)
val SaturdayBadge = Color(0xFF387AFF)

// One UI 9 Digital Wellbeing Chart & Vertical Accent Bar Spectrum (Screenshots 3 & 4)
val StatsPieColors = listOf(
    Color(0xFF2B75FF), // 1. Samsung Royal Blue (Primary series)
    Color(0xFF26C6DA), // 2. Vibrant Cyan / Teal (Secondary series)
    Color(0xFF4CD964), // 3. Emerald / Mint Green (Tertiary series)
    Color(0xFF8E8E93), // 4. Neutral Slate Gray (Other / 4th series)
    Color(0xFF7B61FF), // 5. One UI Purple
    Color(0xFFFF9F43), // 6. Warm Orange
    Color(0xFFFF5A5F), // 7. Coral Red
    Color(0xFFFFD147), // 8. Golden Amber
    Color(0xFFD86EE0), // 9. Orchid
    Color(0xFF00B4D8), // 10. Sky Cyan
    Color(0xFF5E5CE6), // 11. Indigo
    Color(0xFFFF85A2)  // 12. Soft Rose
)
