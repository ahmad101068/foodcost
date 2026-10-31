package ir.foodcost.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import ir.foodcost.app.R
import ir.foodcost.core.pricing.PriceStatus

/** Vazirmatn, with Persian digits even where Latin digits are typed. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

// A saffron palette: warm, readable in a bright kitchen.
private val LightColors = lightColorScheme(
    primary = Color(0xFF9C4500),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF6F5B40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADEBC),
    onSecondaryContainer = Color(0xFF271904),
    tertiary = Color(0xFF3F6837),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFF0B1),
    onTertiaryContainer = Color(0xFF002201),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED3),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85746B),
    outlineVariant = Color(0xFFD7C2B8),
    inverseSurface = Color(0xFF382E29),
    inverseOnSurface = Color(0xFFFFEDE5),
    inversePrimary = Color(0xFFFFB68A),
    surfaceDim = Color(0xFFE8D7CF),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),
    surfaceContainer = Color(0xFFFCEBE2),
    surfaceContainerHigh = Color(0xFFF6E5DC),
    surfaceContainerHighest = Color(0xFFF0DFD7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF542200),
    primaryContainer = Color(0xFF773300),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFDDC2A1),
    onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF56432A),
    onSecondaryContainer = Color(0xFFFADEBC),
    tertiary = Color(0xFFA4D397),
    onTertiary = Color(0xFF0C390C),
    tertiaryContainer = Color(0xFF275020),
    onTertiaryContainer = Color(0xFFBFF0B1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A120D),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF1A120D),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF52443C),
    inverseSurface = Color(0xFFF0DFD7),
    inverseOnSurface = Color(0xFF382E29),
    inversePrimary = Color(0xFF9C4500),
    surfaceDim = Color(0xFF1A120D),
    surfaceBright = Color(0xFF42372F),
    surfaceContainerLowest = Color(0xFF140C08),
    surfaceContainerLow = Color(0xFF221A15),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF322823),
    surfaceContainerHighest = Color(0xFF3D332D),
)

private val AppTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Vazirmatn),
        displayMedium = base.displayMedium.copy(fontFamily = Vazirmatn),
        displaySmall = base.displaySmall.copy(fontFamily = Vazirmatn),
        headlineLarge = base.headlineLarge.copy(fontFamily = Vazirmatn),
        headlineMedium = base.headlineMedium.copy(fontFamily = Vazirmatn),
        headlineSmall = base.headlineSmall.copy(fontFamily = Vazirmatn),
        titleLarge = base.titleLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = Vazirmatn),
        bodyMedium = base.bodyMedium.copy(fontFamily = Vazirmatn),
        bodySmall = base.bodySmall.copy(fontFamily = Vazirmatn),
        labelLarge = base.labelLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = Vazirmatn),
        labelSmall = base.labelSmall.copy(fontFamily = Vazirmatn),
    )
}

@Composable
fun FoodCostTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography) {
        // The app is in Persian, so it reads right to left whatever the phone's language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

/** Background and text colors for a price status. */
data class StatusColors(val container: Color, val content: Color)

@Composable
fun statusColors(status: PriceStatus): StatusColors {
    val dark = isSystemInDarkTheme()
    return when (status) {
        PriceStatus.OK -> if (dark) StatusColors(Color(0xFF234D22), Color(0xFFBFF0B1)) else StatusColors(Color(0xFFD4EDD0), Color(0xFF1E5A1A))
        PriceStatus.NEAR -> if (dark) StatusColors(Color(0xFF5A4100), Color(0xFFFFDF9E)) else StatusColors(Color(0xFFFFE7B0), Color(0xFF6B4A00))
        PriceStatus.UNDER -> if (dark) StatusColors(Color(0xFF8C1D18), Color(0xFFFFDAD6)) else StatusColors(Color(0xFFFFDAD6), Color(0xFF8C1D18))
        PriceStatus.NO_PRICE, PriceStatus.INCOMPLETE, PriceStatus.EMPTY ->
            StatusColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
