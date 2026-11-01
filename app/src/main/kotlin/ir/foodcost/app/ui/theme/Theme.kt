package ir.foodcost.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.foodcost.app.R
import ir.foodcost.core.pricing.PriceStatus

/** Vazirmatn, with Persian digits even where Latin digits are typed. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/*
 * Colors from a Persian kitchen and its tiles: lapis for the calculator's display and the
 * buttons, saffron for the result, pistachio for a price that covers its cost and pomegranate
 * for one that does not. Everything else is quiet paper and ink, so the numbers stand out.
 */
private object Palette {
    val Lapis = Color(0xFF1F3A93)
    val LapisDeep = Color(0xFF16296B)
    val Saffron = Color(0xFFFFC93C)
    val Paper = Color(0xFFF4F5F9)
    val Ink = Color(0xFF1B1C22)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A47A8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF0A1F66),
    secondary = Color(0xFF7A5900),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = Color(0xFF3D6B22),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7EDBF),
    onTertiaryContainer = Color(0xFF0E2000),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFE2E4EC),
    onSurfaceVariant = Color(0xFF464954),
    surfaceTint = Color(0xFF2A47A8),
    outline = Color(0xFF767987),
    outlineVariant = Color(0xFFC8CAD5),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F5),
    inversePrimary = Color(0xFFB8C4FF),
    surfaceDim = Color(0xFFDADBE1),
    surfaceBright = Palette.Paper,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFC),
    surfaceContainer = Color(0xFFEEF0F5),
    surfaceContainerHigh = Color(0xFFE8EAF0),
    surfaceContainerHighest = Color(0xFFE2E4EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF0D2878),
    primaryContainer = Color(0xFF26409A),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFF2C13A),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5B4300),
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = Color(0xFFA9D58A),
    onTertiary = Color(0xFF1A3700),
    tertiaryContainer = Color(0xFF2B5214),
    onTertiaryContainer = Color(0xFFD7EDBF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    surfaceTint = Color(0xFFB8C4FF),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF2A47A8),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393F),
    surfaceContainerLowest = Color(0xFF1B1D23),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceContainerHighest = Color(0xFF33343B),
)

// Corners grow with what they hold: fields and chips, then cards, then the display and dialogs.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Vazirmatn),
        displayMedium = base.displayMedium.copy(fontFamily = Vazirmatn),
        displaySmall = base.displaySmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = Vazirmatn),
        bodyMedium = base.bodyMedium.copy(fontFamily = Vazirmatn),
        bodySmall = base.bodySmall.copy(fontFamily = Vazirmatn, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = Vazirmatn),
        labelSmall = base.labelSmall.copy(fontFamily = Vazirmatn),
    )
}

@Composable
fun FoodCostTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
        // The app is in Persian, so it reads right to left whatever the phone's language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

/** The calculator's display: a lapis window with the result in saffron. */
data class DisplayColors(
    val container: Color,
    val result: Color,
    val value: Color,
    val label: Color,
    val divider: Color,
)

@Composable
fun displayColors(): DisplayColors =
    if (isSystemInDarkTheme()) {
        DisplayColors(Palette.LapisDeep, Palette.Saffron, Color(0xFFF4F5FF), Color(0xFFB4C0EE), Color(0x29FFFFFF))
    } else {
        DisplayColors(Palette.Lapis, Palette.Saffron, Color(0xFFFFFFFF), Color(0xFFC5D0F7), Color(0x2EFFFFFF))
    }

/** Background and text colors for a price status. */
data class StatusColors(val container: Color, val content: Color)

@Composable
fun statusColors(status: PriceStatus): StatusColors {
    val dark = isSystemInDarkTheme()
    return when (status) {
        // Pistachio: the price covers the cost.
        PriceStatus.OK -> if (dark) StatusColors(Color(0xFF2B4A14), Color(0xFFCDEBB0)) else StatusColors(Color(0xFFDCEFC8), Color(0xFF2D5A0F))
        // Saffron: close to the line.
        PriceStatus.NEAR -> if (dark) StatusColors(Color(0xFF5A4300), Color(0xFFFFE08A)) else StatusColors(Color(0xFFFFE9A8), Color(0xFF6B4E00))
        // Pomegranate: the price no longer covers the cost.
        PriceStatus.UNDER -> if (dark) StatusColors(Color(0xFF7A1216), Color(0xFFFFDAD7)) else StatusColors(Color(0xFFFFDAD7), Color(0xFFA4161A))
        PriceStatus.NO_PRICE, PriceStatus.INCOMPLETE, PriceStatus.EMPTY ->
            StatusColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
