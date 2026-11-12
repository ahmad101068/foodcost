package ir.foodcost.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import ir.foodcost.presentation.DigitGroups

/** Geometry and visual primitives measured from the approved reference, at a 411dp viewport. */
object ReferenceIcons {
    private fun line(name: String, draw: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { draw() }
    }.build()
    val calculator = line("Reference calculator") {
        moveTo(6f,2.5f); lineTo(18f,2.5f); lineTo(18f,21.5f); lineTo(6f,21.5f); close()
        moveTo(8.5f,5.5f); lineTo(15.5f,5.5f); lineTo(15.5f,8.5f); lineTo(8.5f,8.5f); close()
        moveTo(9f,12f); lineTo(9.1f,12f); moveTo(12f,12f); lineTo(12.1f,12f); moveTo(15f,12f); lineTo(15.1f,12f)
        moveTo(9f,15.5f); lineTo(9.1f,15.5f); moveTo(12f,15.5f); lineTo(12.1f,15.5f); moveTo(15f,15.5f); lineTo(15.1f,15.5f)
        moveTo(9f,19f); lineTo(9.1f,19f); moveTo(12f,19f); lineTo(12.1f,19f); moveTo(15f,19f); lineTo(15.1f,19f)
    }
    val dishes = line("Reference dishes") {
        moveTo(3f,17f); curveTo(3f,6f,21f,6f,21f,17f); close()
        moveTo(1.5f,20f); lineTo(22.5f,20f); moveTo(11f,5f); lineTo(13f,5f); moveTo(12f,5f); lineTo(12f,8f)
    }
    val layers = line("Reference materials") {
        moveTo(12f,2.5f); lineTo(22f,8f); lineTo(12f,13.5f); lineTo(2f,8f); close()
        moveTo(2f,12f); lineTo(12f,17.5f); lineTo(22f,12f)
        moveTo(2f,16f); lineTo(12f,21.5f); lineTo(22f,16f)
    }
    val settings = line("Reference settings") {
        moveTo(9.5f,2f); lineTo(14.5f,2f); lineTo(15f,5f); lineTo(17f,6f); lineTo(19.5f,5f)
        lineTo(22f,9f); lineTo(19.5f,11f); lineTo(19.5f,13f); lineTo(22f,15f); lineTo(19.5f,19f)
        lineTo(17f,18f); lineTo(15f,19f); lineTo(14.5f,22f); lineTo(9.5f,22f); lineTo(9f,19f)
        lineTo(7f,18f); lineTo(4.5f,19f); lineTo(2f,15f); lineTo(4.5f,13f); lineTo(4.5f,11f)
        lineTo(2f,9f); lineTo(4.5f,5f); lineTo(7f,6f); lineTo(9f,5f); close()
        moveTo(16f,12f); curveTo(16f,17.3f,8f,17.3f,8f,12f); curveTo(8f,6.7f,16f,6.7f,16f,12f); close()
    }
    val tag = line("Reference pricing") {
        moveTo(3f,3f); lineTo(13f,3f); lineTo(22f,12f); lineTo(12f,22f); lineTo(3f,13f); close()
        moveTo(7f,7f); lineTo(7.2f,7f)
    }
    val coins = line("Reference costs") {
        moveTo(3f,5f); curveTo(3f,1f,17f,1f,17f,5f); curveTo(17f,9f,3f,9f,3f,5f); close()
        moveTo(3f,5f); lineTo(3f,15f); curveTo(3f,19f,17f,19f,17f,15f); lineTo(17f,5f)
        moveTo(3f,10f); curveTo(3f,14f,17f,14f,17f,10f)
        moveTo(20f,10f); lineTo(21f,11f); lineTo(21f,20f); curveTo(21f,23f,9f,23f,9f,20f)
    }
    val grid = line("Reference sections") {
        moveTo(3f,3f); lineTo(9f,3f); lineTo(9f,9f); lineTo(3f,9f); close()
        moveTo(15f,3f); lineTo(21f,3f); lineTo(21f,9f); lineTo(15f,9f); close()
        moveTo(3f,15f); lineTo(9f,15f); lineTo(9f,21f); lineTo(3f,21f); close()
        moveTo(15f,15f); lineTo(21f,15f); lineTo(21f,21f); lineTo(15f,21f); close()
    }
    val money = line("Reference currency") {
        moveTo(2f,5f); lineTo(22f,5f); lineTo(22f,19f); lineTo(2f,19f); close()
        moveTo(15f,12f); curveTo(15f,16f,9f,16f,9f,12f); curveTo(9f,8f,15f,8f,15f,12f); close()
        moveTo(5f,8f); lineTo(6f,8f); moveTo(18f,16f); lineTo(19f,16f)
    }
    val backup = line("Reference backup") {
        moveTo(7f,18f); curveTo(0f,18f,0f,9f,6f,9f); curveTo(6f,1f,19f,1f,19f,9f)
        curveTo(25f,10f,24f,18f,18f,18f); moveTo(12f,22f); lineTo(12f,11f)
        moveTo(8f,15f); lineTo(12f,11f); lineTo(16f,15f)
    }
    val info = line("Reference help") {
        moveTo(22f,12f); curveTo(22f,25.3f,2f,25.3f,2f,12f); curveTo(2f,-1.3f,22f,-1.3f,22f,12f); close()
        moveTo(12f,10f); lineTo(12f,17f); moveTo(12f,6.5f); lineTo(12f,6.6f)
    }
    val exit = line("Reference exit") {
        moveTo(13f,3f); lineTo(5f,3f); lineTo(5f,21f); lineTo(13f,21f)
        moveTo(11f,12f); lineTo(22f,12f); moveTo(18f,8f); lineTo(22f,12f); lineTo(18f,16f)
    }
    val trash = line("Reference remove") {
        moveTo(4f,6f); lineTo(20f,6f); moveTo(9f,6f); lineTo(9f,3f); lineTo(15f,3f); lineTo(15f,6f)
        moveTo(6f,6f); lineTo(7f,21f); lineTo(17f,21f); lineTo(18f,6f)
        moveTo(10f,10f); lineTo(10.5f,17f); moveTo(14f,10f); lineTo(13.5f,17f)
    }
}

/** Compact editable well; label and field layout are supplied by the reference row around it. */
@Composable
fun ReferenceField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    number: Boolean = false, suffix: String? = null, imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null, centered: Boolean = false, height: androidx.compose.ui.unit.Dp = 36.dp,
    trailing: (@Composable () -> Unit)? = null, leading: (@Composable () -> Unit)? = null,
    fontSize: TextUnit = 16.sp, verticalPadding: Dp = 3.dp,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var width by remember { mutableIntStateOf(0) }
    val base = MaterialTheme.typography.bodyLarge.copy(
        color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = fontSize, lineHeight = fontSize * 1.35f,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
    )
    val style = remember(value, suffix, width, base, number, density) {
        val room = width - with(density) { 24.dp.roundToPx() }
        if (number && width > 0) fitted(measurer, DigitGroups.of(value)?.shown ?: value, suffix, base, room) else base
    }
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true, textStyle = style,
        interactionSource = interactions, cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text, imeAction = imeAction),
        keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
        visualTransformation = if (number) GroupDigits else VisualTransformation.None,
        modifier = modifier.fillMaxWidth().onSizeChanged { width = it.width }.semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = height)
                    .background(colors.surfaceContainerLowest, RoundedCornerShape(9.dp))
                    .border(if (focused) 1.4.dp else 0.8.dp, if (focused) colors.primary else colors.outlineVariant, RoundedCornerShape(9.dp))
                    .padding(horizontal = 9.dp, vertical = verticalPadding),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f), contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    inner()
                }
                if (suffix != null) Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(6.dp)) {
                    Text(suffix, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun ReferenceBottomBar(nav: Navigator, actions: AppActions) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerLowest, shadowElevation = 1.dp) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.65f), thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Tab.CALC, Tab.INGREDIENTS, Tab.RECIPES, Tab.SETTINGS).forEach { tab ->
                    val selected = nav.tab == tab
                    val brush = if (selected) Brush.verticalGradient(listOf(scheme.primary, scheme.primary.copy(red = scheme.primary.red * .85f, green = scheme.primary.green * .85f, blue = scheme.primary.blue * .85f)))
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    Column(
                        Modifier.weight(1f).background(brush, RoundedCornerShape(14.dp))
                            .semantics { this.selected = selected; role = Role.Tab }.clickable(onClickLabel = tab.label) { actions.snackbar.currentSnackbarData?.dismiss(); nav.tab(tab) }
                            .padding(horizontal = 2.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(when (tab) { Tab.CALC -> ReferenceIcons.calculator; Tab.RECIPES -> ReferenceIcons.dishes; Tab.INGREDIENTS -> ReferenceIcons.layers; Tab.SETTINGS -> ReferenceIcons.settings },
                            contentDescription = null, tint = if (selected) scheme.onPrimary else scheme.onSurface, modifier = Modifier.size(24.dp))
                        FitText(tab.label, MaterialTheme.typography.labelLarge, color = if (selected) scheme.onPrimary else scheme.onSurface)
                    }
                }
            }
        }
    }
}
