package ir.foodcost.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * FoodCost design tokens, direction C: Warm Contemporary.
 *
 * Use MaterialTheme.colorScheme in components. Semantic dashboard/status colors are resolved
 * by Theme.kt. Values are kept separate from accounting/business status semantics.
 * Reference: docs/FOODCOST-PREMIUM-VISUAL-AUDIT.md
 */
internal object FoodCostTokens {
    val Primary = Color(0xFF075B49)
    val PrimaryDeep = Color(0xFF073F34)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFE3F2E6)
    val OnPrimaryContainer = Color(0xFF153D31)
    val Secondary = Color(0xFF446E60)
    val Paper = Color(0xFFF7F8F5)
    val Ink = Color(0xFF172A24)
    val Muted = Color(0xFF52645B)
    val Border = Color(0xFFCAD4CB)
    val SurfaceVariant = Color(0xFFE9EEEA)
    val WarmResult = Color(0xFFF0D5A2)

    val SalesBackground = Color(0xFFEDF2FB)
    val SalesForeground = Color(0xFF204A89)
    val FoodBackground = Color(0xFFFFF1DC)
    val FoodForeground = Color(0xFF964619)
    val SuccessBackground = Color(0xFFE6F5E9)
    val SuccessForeground = Color(0xFF20693B)
    val WarningBackground = Color(0xFFFFF3DC)
    val WarningForeground = Color(0xFF805615)
    val ErrorBackground = Color(0xFFFCE7E4)
    val ErrorForeground = Color(0xFFA52725)
    // Premium surfaces are presentational: financial rules remain in the pricing model.
    val HeroSurface = Color(0xFFE3F2DE)
    val HeroText = Color(0xFF073F34)
    val GoldenAccent = Color(0xFFE0AA35)
    val GoldenSurface = Color(0xFFFFF3DD)
    val ProfitSurface = Color(0xFFE6F5E9)
    val PurpleSurface = Color(0xFFF1ECF9)
    val PurpleText = Color(0xFF55358A)
    val HeaderTop = Color(0xFF084F42)
    val HeaderBottom = Color(0xFF073C33)
}

internal object FoodCostSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val MinimumTouchTarget = 48.dp
}
