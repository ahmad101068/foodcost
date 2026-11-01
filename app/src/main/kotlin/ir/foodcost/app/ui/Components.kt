package ir.foodcost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.foodcost.app.ui.theme.displayColors
import ir.foodcost.app.ui.theme.statusColors
import ir.foodcost.core.pricing.PriceStatus
import ir.foodcost.presentation.DigitGroups

/** A tab's screen: a title on top and the tabs at the bottom. */
@Composable
fun TabScaffold(
    title: String,
    nav: Navigator,
    actions: AppActions,
    topActions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, actions = topActions) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = nav.tab == tab,
                        onClick = { nav.tab(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(actions.snackbar) },
        content = content,
    )
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = topActions,
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(actions.snackbar) },
        content = content,
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
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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

/** A small caption over a value. */
@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
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

/** Shows 1450000 as 1٬450٬000 while it is typed; the typed text itself is left alone. */
private object GroupDigits : VisualTransformation {
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
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "پاک کردن")
                }
            }
        } else {
            null
        },
    )
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
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        title = { Text(title) },
        text = { Text(text) },
    )
}
