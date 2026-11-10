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
 * Warm paper and forest teal keep controls distinct from the financial status colors.
 * Green means a suitable price, cream means near the target, and red means well below it.
 */
private object Palette {
    val Lapis = Color(0xFF174F46)
    val LapisDeep = Color(0xFF123A33)
    val Saffron = Color(0xFFE9D3A6)
    val Paper = Color(0xFFF7F8F4)
    val Ink = Color(0xFF202A25)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF174F46),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFEEE7),
    onPrimaryContainer = Color(0xFF173D35),
    secondary = Color(0xFF755D2E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFF1D7),
    onSecondaryContainer = Color(0xFF4B3716),
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
    surfaceVariant = Color(0xFFE9EDE8),
    onSurfaceVariant = Color(0xFF526158),
    surfaceTint = Color(0xFF174F46),
    outline = Color(0xFF7C8880),
    outlineVariant = Color(0xFFCCD5CD),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F5),
    inversePrimary = Color(0xFFA4D5C4),
    surfaceDim = Color(0xFFDBE1D9),
    surfaceBright = Palette.Paper,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F6EF),
    surfaceContainer = Color(0xFFEDF2EA),
    surfaceContainerHigh = Color(0xFFE6ECE3),
    surfaceContainerHighest = Color(0xFFDFE6DC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4D5C4),
    onPrimary = Color(0xFF12382F),
    primaryContainer = Color(0xFF28594C),
    onPrimaryContainer = Color(0xFFDBF1E5),
    secondary = Color(0xFFE0CAA4),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF594B34),
    onSecondaryContainer = Color(0xFFF5E8CE),
    tertiary = Color(0xFFA9D58A),
    onTertiary = Color(0xFF1A3700),
    tertiaryContainer = Color(0xFF2B5214),
    onTertiaryContainer = Color(0xFFD7EDBF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111914),
    onBackground = Color(0xFFDFE8E0),
    surface = Color(0xFF111914),
    onSurface = Color(0xFFDFE8E0),
    surfaceVariant = Color(0xFF3D4A41),
    onSurfaceVariant = Color(0xFFBFCFC3),
    surfaceTint = Color(0xFFA4D5C4),
    outline = Color(0xFF89998E),
    outlineVariant = Color(0xFF3D4A41),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF174F46),
    surfaceDim = Color(0xFF111914),
    surfaceBright = Color(0xFF354239),
    surfaceContainerLowest = Color(0xFF0C140F),
    surfaceContainerLow = Color(0xFF18231C),
    surfaceContainer = Color(0xFF1C281F),
    surfaceContainerHigh = Color(0xFF263329),
    surfaceContainerHighest = Color(0xFF313F35),
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

/** A dark teal panel for detailed financial figures. */
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
        DisplayColors(Palette.LapisDeep, Palette.Saffron, Color(0xFFF3FAF5), Color(0xFFC0D9CC), Color(0x29FFFFFF))
    } else {
        DisplayColors(Palette.Lapis, Palette.Saffron, Color(0xFFFFFFFF), Color(0xFFCCE1D5), Color(0x2EFFFFFF))
    }

/** Background and text colors for a price status. */
data class StatusColors(val container: Color, val content: Color)

@Composable
fun statusColors(status: PriceStatus): StatusColors {
    val dark = isSystemInDarkTheme()
    return when (status) {
        PriceStatus.OK -> if (dark) StatusColors(Color(0xFF254C34), Color(0xFFC6EBCF)) else StatusColors(Color(0xFFE7F3E9), Color(0xFF245A35))
        PriceStatus.NEAR -> if (dark) StatusColors(Color(0xFF4D4232), Color(0xFFF6E8CC)) else StatusColors(Color(0xFFFFF3DC), Color(0xFF654C29))
        PriceStatus.UNDER -> if (dark) StatusColors(Color(0xFF642C2B), Color(0xFFFFDBD6)) else StatusColors(Color(0xFFFDE9E7), Color(0xFFA12622))
        PriceStatus.NO_PRICE, PriceStatus.INCOMPLETE, PriceStatus.EMPTY ->
            StatusColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Restrained accents for the ingredients and dishes overview cards. */
data class FoodSectionColors(
    val container: Color,
    val heading: Color,
    val subheading: Color,
    val statPrimary: Color,
    val statSecondary: Color,
    val statLabel: Color,
    val statValue: Color,
    val itemTitle: Color,
)

@Composable
fun foodSectionColors(): FoodSectionColors =
    if (isSystemInDarkTheme()) {
        FoodSectionColors(
            Color(0xFF213A30), Color(0xFFE1F1E7), Color(0xFFC0D4C8),
            Color(0xFF2D4C3E), Color(0xFF344F43), Color(0xFFD0E2D7),
            Color(0xFFF0FAF4), Color(0xFFAFDAC5),
        )
    } else {
        FoodSectionColors(
            Color(0xFFE5EFE9), Color(0xFF214F40), Color(0xFF506C5D),
            Color(0xFFD5E7DC), Color(0xFFDBEADF), Color(0xFF476454),
            Color(0xFF1F4D3D), Color(0xFF275A46),
        )
    }
