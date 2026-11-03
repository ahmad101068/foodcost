package ir.foodcost.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.foodcost.app.AppContainer
import ir.foodcost.presentation.Glossary
import ir.foodcost.presentation.Guide
import ir.foodcost.presentation.GuideStep
import ir.foodcost.presentation.MoneyFormat
import ir.foodcost.presentation.Persian
import ir.foodcost.presentation.QuickInput
import ir.foodcost.presentation.Term

/**
 * The guide, from the settings or the empty calculator: the words of the calculations, searchable,
 * each with its meaning, formula and an example; and how to use the app, step by step, with the
 * examples' koobideh ready to open in the calculator.
 */
@Composable
fun HelpScreen(screen: Screen.Help, container: AppContainer, money: MoneyFormat, nav: Navigator, actions: AppActions) {
    var section by rememberSaveable(screen.key) { mutableStateOf(screen.section) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmSample by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    fun openSample() {
        container.quickDraft.value = Guide.sample(money)
        nav.tab(Tab.CALC)
    }

    DetailScaffold(title = "راهنما", nav = nav, actions = actions) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Segmented(listOf(Screen.Help.TERMS, Screen.Help.GUIDE), section, { if (it == Screen.Help.TERMS) "واژه‌ها" else "کار با برنامه" }) {
                    section = it
                }
            }
            if (section == Screen.Help.TERMS) {
                item { SearchField(query, { query = it }, "جستجوی واژه") }
                val terms = Glossary.search(query)
                if (terms.isEmpty()) {
                    item { EmptyState(title = "واژه‌ای پیدا نشد", body = "واژهٔ دیگری را جستجو کنید.") }
                }
                items(terms, key = { it.id }) { term -> TermCard(term, open = term.id == screen.term) }
            } else {
                itemsIndexed(Guide.STEPS) { i, step -> GuideCard(i + 1, step) }
                item {
                    Button(
                        onClick = { if (container.quickDraft.value == QuickInput()) openSample() else confirmSample = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text("نمونهٔ چلوکباب کوبیده را در ماشین‌حساب باز کن") }
                }
            }
        }
    }
    // Opened at a term from the calculator: that term is in sight, already open.
    LaunchedEffect(screen.term) {
        val index = Glossary.TERMS.indexOfFirst { it.id == screen.term }
        // The first term is already in sight under the search field, which stays in sight with it.
        if (index > 0 && section == Screen.Help.TERMS && query.isBlank()) list.scrollToItem(index + 2)
    }
    if (confirmSample) {
        ConfirmDialog(
            title = "نمونه جای حساب فعلی بیاید؟",
            text = "حسابی که الان در ماشین‌حساب است پاک می‌شود. غذاهای ذخیره‌شده دست نمی‌خورند.",
            confirm = "باز کن",
            onConfirm = {
                confirmSample = false
                openSample()
            },
            onDismiss = { confirmSample = false },
        )
    }
}

/** A word of the glossary: its name and the start of its meaning; a tap opens the rest. */
@Composable
private fun TermCard(term: Term, open: Boolean) {
    var expanded by rememberSaveable(term.id) { mutableStateOf(open) }
    Card(onClick = { expanded = !expanded }, colors = whiteCardColors(), modifier = Modifier.fillMaxWidth().testTag("term-${term.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(term.title, style = MaterialTheme.typography.titleMedium)
                    if (term.aka.isNotEmpty()) {
                        Text(term.aka.joinToString("، "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            if (expanded) {
                TermBody(term, live = null, showWhere = true)
            } else {
                Text(
                    term.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * What a word means, its formula, then either the calculation with the user's own numbers ([live])
 * or the glossary's example, and where the app shows it.
 */
@Composable
fun TermBody(term: Term, live: String?, showWhere: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(term.meaning, style = MaterialTheme.typography.bodyMedium)
        term.formula?.let { formula ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Text(formula, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
        if (live != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("در این حساب", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(live, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        } else {
            term.example?.let { Text("مثال: $it", style = MaterialTheme.typography.bodyMedium) }
        }
        if (showWhere) {
            term.where?.let { Text("کجا: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * Words from the calculator, explained where they are: each with its meaning, formula and the
 * calculation behind the number on the screen. [more] opens the whole glossary at the first one.
 */
@Composable
fun TermDialog(ids: List<String>, live: Map<String, String>, onDismiss: () -> Unit, more: () -> Unit) {
    val terms = ids.mapNotNull { Glossary.term(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(terms.joinToString(" و ") { it.title }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                terms.forEach { term ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (terms.size > 1) Text(term.title, style = MaterialTheme.typography.titleMedium)
                        TermBody(term, live[term.id], showWhere = false)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
        dismissButton = { TextButton(onClick = more) { Text("همهٔ واژه‌ها") } },
    )
}

/** One step of the guide, numbered in the order a new user goes. */
@Composable
private fun GuideCard(number: Int, step: GuideStep) {
    Card(colors = whiteCardColors(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(30.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Text(Persian.digits(number), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(step.title, style = MaterialTheme.typography.titleSmall)
                Text(step.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
