package ir.foodcost.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Tab icons drawn here because the bundled icon set has neither.

/** A calculator: the calculator tab. */
val CalculatorIcon: ImageVector = ImageVector.Builder(
    name = "Calculator",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
    moveTo(7f, 2f)
    lineTo(17f, 2f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
    lineTo(19f, 20f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
    lineTo(7f, 22f)
    arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
    lineTo(5f, 4f)
    arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
    close()
    moveTo(7.5f, 4.5f)
    lineTo(16.5f, 4.5f)
    lineTo(16.5f, 8.5f)
    lineTo(7.5f, 8.5f)
    close()
    for (row in 0..2) {
        for (col in 0..2) {
            val x = 7.5f + col * 3.25f
            val y = 11f + row * 3.25f
            moveTo(x, y)
            lineTo(x + 2.5f, y)
            lineTo(x + 2.5f, y + 2.5f)
            lineTo(x, y + 2.5f)
            close()
        }
    }
}.build()

/** A serving dome on its plate: the dishes tab. */
val DishesIcon: ImageVector = ImageVector.Builder(
    name = "Dishes",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(fill = SolidColor(Color.Black)) {
    // The knob.
    moveTo(12f, 4.6f)
    arcToRelative(1.6f, 1.6f, 0f, true, true, 0f, 3.2f)
    arcToRelative(1.6f, 1.6f, 0f, true, true, 0f, -3.2f)
    close()
    // The dome.
    moveTo(3.5f, 17f)
    arcTo(8.5f, 8.5f, 0f, false, true, 20.5f, 17f)
    close()
    // The plate.
    moveTo(3f, 18.2f)
    lineTo(21f, 18.2f)
    arcToRelative(1f, 1f, 0f, false, true, 0f, 2f)
    lineTo(3f, 20.2f)
    arcToRelative(1f, 1f, 0f, false, true, 0f, -2f)
    close()
}.build()
