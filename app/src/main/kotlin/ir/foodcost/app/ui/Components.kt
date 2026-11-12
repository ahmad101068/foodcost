package ir.foodcost.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import ir.foodcost.app.ui.theme.displayColors
import ir.foodcost.app.ui.theme.statusColors
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.presentation.BackupTexts
import ir.foodcost.presentation.DigitGroups

/** A tab's screen: a title on top and the tabs at the bottom. */
@Composable
fun TabScaffold(
    title: String,
    nav: Navigator,
    actions: AppActions,
    topActions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var snackbarHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    Scaffold(
        topBar = {
            Column {
                TopAppBar(expandedHeight = 56.dp, title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = navigationIcon, actions = topActions)
                UnreadableWarning(actions, nav)
                BackupReminderLine(actions, nav)
            }
        },
        bottomBar = { ReferenceBottomBar(nav, actions) },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(actions.snackbar, Modifier.onSizeChanged { snackbarHeight = it.height }) },
        content = { padding ->
            // A snackbar must never cover a card or Save control, including with 2x text.
            val extra = with(density) { if (actions.snackbar.currentSnackbarData != null) snackbarHeight.toDp() else 0.dp }
            content(PaddingValues(
                start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + extra,
            ))
        },
    )
}

/**
 * A line under the title on every screen while the stored data was not read whole: no figure is
 * shown and nothing is recorded until the user chooses what to do, which a tap on it leads to.
 */
@Composable
private fun UnreadableWarning(actions: AppActions, nav: Navigator) {
    val count = actions.unreadableRows
    if (count == 0) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "دیدن راه‌ها در پشتیبان") { nav.tab(Tab.SETTINGS) }
            .testTag("unreadable-warning"),
    ) {
        Text(
            BackupTexts.LOCKED_BANNER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/**
 * A line under the title of the tabs when a backup is due: Android's own backup leaves this app's
 * data out, so after the app is removed or the phone changed, the data comes back only from one.
 */
@Composable
private fun BackupReminderLine(actions: AppActions, nav: Navigator) {
    val text = actions.backupReminder ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("backup-reminder")) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = { actions.snoozeBackupReminder() }) { Text("بعداً") }
                TextButton(onClick = { nav.tab(Tab.SETTINGS) }) { Text("رفتن به پشتیبان") }
            }
        }
    }
}

/** A screen opened from a tab, with a way back. */
@Composable
fun DetailScaffold(
    title: String,
    nav: Navigator,
    actions: AppActions,
    topActions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var snackbarHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    expandedHeight = 56.dp,
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { nav.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                        }
                    },
                    actions = topActions,
                )
                UnreadableWarning(actions, nav)
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(actions.snackbar, Modifier.onSizeChanged { snackbarHeight = it.height }) },
        content = { padding ->
            val extra = with(density) { if (actions.snackbar.currentSnackbarData != null) snackbarHeight.toDp() else 0.dp }
            content(PaddingValues(
                start = padding.calculateStartPadding(direction), end = padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + extra,
            ))
        },
    )
}

/**
 * The floating "add" button. Its label stays in the accessibility tree, so screen readers
 * (and tests) can find it; the icon-and-text variant of the Material button hides the text.
 */
@Composable
fun AddButton(text: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

/** An add action in the header leaves the list's entire touch area clear. */
@Composable
fun HeaderAddButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** White cards on the paper background: calculator rows, dishes, prices and settings. */
@Composable
fun whiteCardColors(): CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)

/**
 * The lapis window that shows a cost: the calculator's display and the top of a recipe. Inside
 * it, text is light, the result color is saffron (as `primary`) and dividers and buttons follow.
 */
@Composable
fun DisplayPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val display = displayColors()
    val scheme = MaterialTheme.colorScheme.copy(
        primary = display.result,
        onPrimary = display.container,
        onSurface = display.value,
        onSurfaceVariant = display.label,
        surfaceVariant = display.divider,
        outline = display.label,
        outlineVariant = display.divider,
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = display.container,
            contentColor = display.value,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
fun StatusChip(status: PriceStatus, label: String, modifier: Modifier = Modifier) {
    val colors = statusColors(status)
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = colors.container, contentColor = colors.content) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * An amount on one line that gets smaller rather than being cut off when it does not fit, as with
 * the phone's largest text: a number with its last digits cut off reads as another number.
 */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val width = LocalConfiguration.current.screenWidthDp
    // Starts at the full size for each new amount; an amount that fits never changes size.
    var scale by remember(text, style, width) { mutableFloatStateOf(1f) }
    Text(
        text,
        modifier = modifier,
        color = color,
        style = if (style.fontSize.isSpecified) style.copy(fontSize = style.fontSize * scale) else style,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && scale > MIN_FIT_SCALE) scale = (scale * FIT_STEP).coerceAtLeast(MIN_FIT_SCALE)
        },
    )
}

private const val FIT_STEP = 0.9f
private const val MIN_FIT_SCALE = 0.5f

/**
 * An amount with its unit, as the screens write it (۳٬۹۲۹٬۴۵۰ ریال): the number stays whole on one
 * line, smaller if it must, and the unit goes beside it or below it. Anything else is plain text.
 */
@Composable
fun AmountText(value: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val parts = AMOUNT.matchEntire(value)?.groupValues
    if (parts == null) {
        Text(value, style = style, color = color, modifier = modifier)
    } else {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FitText(parts[1], style, color = color)
            Text(parts[2], style = style, color = color)
        }
    }
}

private val AMOUNT = Regex("""([+\-−\u200E\u200F]*[۰-۹0-9][۰-۹0-9٬٫,.]*)\s+(\S.*)""")

/** A small caption over a value. An [amount] keeps its number whole, as [AmountText] does. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false, amount: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
        if (amount) AmountText(value, style) else Text(value, style = style)
    }
}

/**
 * A one-line text field. A number field opens the number keyboard and shows long numbers in groups
 * of three. [imeAction] sets the keyboard's action key: Next moves to the following field unless
 * [onImeAction] says otherwise.
 */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    number: Boolean = false,
    suffix: String? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    val message = error ?: supporting
    var width by remember { mutableIntStateOf(0) }
    val base = LocalTextStyle.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // A long number gets smaller rather than losing its first digits past the edge of the field.
    val textStyle = remember(value, suffix, width, base, number, density) {
        val room = width - with(density) { FIELD_PADDING.roundToPx() }
        if (number && width > 0) fitted(measurer, DigitGroups.of(value)?.shown ?: value, suffix, base, room) else base
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().onSizeChanged { width = it.width },
        textStyle = textStyle,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        singleLine = true,
        isError = error != null,
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else {
            null
        },
        supportingText = if (message != null) {
            { Text(message) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
        visualTransformation = if (number) GroupDigits else VisualTransformation.None,
    )
}

/** A text field's own padding on both sides, and a little room. */
private val FIELD_PADDING = 40.dp

/** [style], made smaller until [text] and its [suffix] fit in [room] pixels. */
internal fun fitted(measurer: TextMeasurer, text: String, suffix: String?, style: TextStyle, room: Int): TextStyle {
    if (!style.fontSize.isSpecified) return style
    val available = room - (suffix?.let { measurer.measure(it, style = style, softWrap = false, maxLines = 1).size.width } ?: 0)
    var scale = 1f
    var current = style
    while (scale > MIN_FIT_SCALE && measurer.measure(text, style = current, softWrap = false, maxLines = 1).size.width > available) {
        scale = (scale * FIT_STEP).coerceAtLeast(MIN_FIT_SCALE)
        current = style.copy(fontSize = style.fontSize * scale)
    }
    return current
}

/** Shows 1450000 as 1٬450٬000 while it is typed; the typed text itself is left alone. */
internal object GroupDigits : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val groups = DigitGroups.of(text.text) ?: return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(
            AnnotatedString(groups.shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = groups.shownOffset(offset)

                override fun transformedToOriginal(offset: Int): Int = groups.typedOffset(offset)
            },
        )
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    ReferenceField(query, onQueryChange, placeholder, modifier.padding(vertical = 4.dp), height = 40.dp,
        leading = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) })
}

/** A choice among a few short options, side by side. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                // The fill already shows the choice; a check mark would crowd the label.
                icon = {},
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A choice among options that may not fit on one line. */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = whiteCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm")) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        title = { Text(title) },
        text = { Text(text) },
    )
}
