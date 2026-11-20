package ir.foodcost.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.foodcost.app.data.SqliteOperationStore
import ir.foodcost.app.ui.AppActions
import ir.foodcost.app.ui.BackupCard
import ir.foodcost.app.ui.theme.FoodCostTheme
import ir.foodcost.core.costing.CostingRules
import ir.foodcost.core.model.*
import ir.foodcost.core.money.*
import ir.foodcost.core.pricing.*
import ir.foodcost.core.units.*
import ir.foodcost.data.ops.BackupSeal
import ir.foodcost.presentation.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Release-audit regressions against real UI, using synthetic data in a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class CompleteAuditNativeTest {
    @get:Rule val rule=createEmptyComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val container get()=(context.applicationContext as FoodCostApplication).container
    private val today=LocalDate.now()
    private val shots get()=File(context.filesDir,"complete-audit-screens").apply{mkdirs()}
    private fun clean(){
        context.deleteDatabase(SqliteOperationStore.DATABASE)
        context.getSharedPreferences("foodcost",Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir,"calculator-draft.json").delete()
    }
    private fun seed(){
        clean()
        runBlocking {container.change { l ->
            l.saveSettings(CostingRules(BigDecimal.ZERO),PricingSettings(defaultMethod=PricingMethod.TargetFoodCost(BigDecimal("40")),vatPercent=BigDecimal.ZERO,rounding=PriceRounding(1),methodByCategory=mapOf("غذای اصلی" to PricingMethod.TargetFoodCost(BigDecimal("20")))))
            val input=QuickInput(dishName="غذای ممیزی",menuPrice="500000",category="غذای اصلی",categoryChosen=true,rows=listOf(QuickRow("ماده ممیزی","100000","1000")))
            val plan=QuickCalculator.check(input,l.state,MoneyFormat(container.currency.value),today) as Checked.Valid
            QuickCalculator.save(l,plan.value,today)
        }}
        container.snoozeBackupReminder()
        container.quickDraft.value=QuickInput();container.saveDraft()
    }
    @Test fun sectionRenameMustKeepFoodRule(){
        seed()
        val id=container.state.value!!.recipes.values.single().id
        val before=container.state.value!!.calculator().evaluate(id,today).suggestion.menuPrice
        ActivityScenario.launch(MainActivity::class.java).use {
            click("foodcost-nav-settings");click("settings-section-pricing");click("settings-section-sections")
            edit("category-0-name","غذاهای اصلی");click("settings-save-sections")
            rule.waitUntil(15000){container.state.value!!.pricing.methodByCategory.containsKey("غذاهای اصلی")}
            val after=container.state.value!!.calculator().evaluate(id,today).suggestion.menuPrice
            click("foodcost-nav-recipes")
            if(rule.onAllNodesWithTag("food-open-${id.value}").fetchSemanticsNodes().isEmpty())click("food-${id.value}")
            click("food-open-${id.value}")
            shot("01-renamed-section-food-price")
            File(shots,"section-rename.txt").writeText("beforeRials=$before\nafterRials=$after\nrecipeSection=${container.state.value!!.recipes[id]?.category}\nsectionRules=${container.state.value!!.pricing.methodByCategory}\n")
            assertEquals("Renaming the section should not halve its food price",before,after)
        }
    }
    private class DeferredRegistry:ActivityResultRegistry(){
        var launchedCode:Int?=null
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?){launchedCode=requestCode}
        fun complete(uri:Uri){dispatchResult(checkNotNull(launchedCode),Activity.RESULT_OK,Intent().setData(uri))}
    }
    @Test fun sealedExportMustNotBecomePlainAfterComponentRecreation() = exportBackup(recreate = true)
    @Test fun encryptedExportRemainsReadableWithItsPassword() = exportBackup(recreate = false)
    @Test fun explicitlyUnencryptedExportStillWorks() = exportBackup(recreate = false, encrypted = false)

    private fun exportBackup(recreate: Boolean, encrypted: Boolean = true) {
        seed()
        val registry=DeferredRegistry()
        val owner=object:ActivityResultRegistryOwner{override val activityResultRegistry=registry}
        val visible=mutableStateOf(true)
        val file=File(context.cacheDir,"audit-requested-encrypted-backup.json");file.delete()
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            scenario.onActivity {activity -> activity.setContent {
                FoodCostTheme {
                    val scope=rememberCoroutineScope()
                    val snackbar=remember{SnackbarHostState()}
                    val actions=remember{AppActions(container,scope,snackbar)}
                    val holder=rememberSaveableStateHolder()
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner){
                        Column(Modifier.fillMaxSize()) {
                            if(visible.value)holder.SaveableStateProvider("backup") {BackupCard(container,today,actions)}
                            SnackbarHost(snackbar)
                        }
                    }
                }
            }}
            rule.waitUntil(15000){rule.onAllNodesWithText("ساخت پشتیبان").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("ساخت پشتیبان").performClick()
            if (encrypted) {
                rule.onNodeWithText("رمز (اگر می‌خواهید)").performTextReplacement("AuditOnly123!")
                rule.onNodeWithText("تکرار رمز").performTextReplacement("AuditOnly123!")
            }
            // This harness tests export/lifecycle state, not IME key delivery. Escape can close
            // the dialog on devices where text replacement did not open the keyboard. Invoke
            // the real button's accessibility action; pointer/IME flows have separate coverage.
            rule.onNodeWithText(if (encrypted) "ساخت با رمز" else "ساخت بدون رمز")
                .assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
            rule.waitUntil(15000){registry.launchedCode!=null}
            if (recreate) {
                // Keep launcher identity and pending encryption intent, without saving the secret.
                rule.runOnIdle{visible.value=false};rule.waitForIdle()
                rule.runOnIdle{visible.value=true};rule.waitForIdle()
            }
            rule.runOnIdle{registry.complete(Uri.fromFile(file))}
            if (recreate) {
                rule.waitUntil(15000){rule.onAllNodesWithText("پشتیبان متوقف شد",substring=true).fetchSemanticsNodes().isNotEmpty()}
                File(shots,"export-recreation.txt").writeText("requestedEncrypted=true\ncomponentRecreated=true\nexportAborted=true\nbytes=${file.length()}\n")
                shot("02-export-after-recreation")
                assertEquals("A lost encryption password must never produce a plaintext backup",0L,file.length())
            } else {
                rule.waitUntil(30000){rule.onAllNodesWithText(if(encrypted) "پشتیبان رمزدار ذخیره شد" else "پشتیبان ذخیره شد", substring=true).fetchSemanticsNodes().isNotEmpty()}
                val text=file.readText()
                assertEquals(encrypted,BackupSeal.isSealed(text))
                val plain=if(encrypted) BackupSeal.open(text,"AuditOnly123!".toCharArray()) else text
                assertTrue(plain.contains("ماده ممیزی"))
                if(encrypted) assertFalse(text.contains("ماده ممیزی"))
            }
        }
    }
    @Test fun incompleteCostMustNotShowSuitablePrice(){
        seed()
        runBlocking{container.change{l->l.saveSettings(pricing=l.state.pricing.copy(scope=PricingScope.GENERAL,defaultMethod=PricingMethod.Markup(BigDecimal("100"),CostBasis.FULL_COST)))}}
        container.quickDraft.value=QuickInput(dishName="هزینه ناقص",menuPrice="500000",rows=listOf(QuickRow("ماده ممیزی","100000","1000")),extras=listOf(QuickExtra("دستمزد","")))
        ActivityScenario.launch(MainActivity::class.java).use{
            scroll("calculator-dashboard");shot("03-incomplete-cost-status")
            val result=QuickCalculator.compute(container.quickDraft.value,container.state.value!!,MoneyFormat(container.currency.value),today)
            File(shots,"incomplete-status.txt").writeText("provisional=${result.provisional}\nstatus=${result.status}\nstatusLabel=${result.statusLabel}\nsuggested=${result.suggested}\nwarning=${result.leftOutNote}\n")
            assertEquals(PriceStatus.INCOMPLETE,result.status)
        }
    }
    private fun scroll(tag:String){
        rule.waitUntil(15000){rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
        try{rule.onNodeWithTag(tag).performScrollTo()}catch(_:AssertionError){}
        rule.waitForIdle()
    }
    private fun click(tag:String){scroll(tag);rule.onNodeWithTag(tag).performClick();rule.waitForIdle()}
    private fun edit(tag:String,value:String){scroll(tag);rule.onNodeWithTag(tag).performTextReplacement(value);hideIme()}
    private fun hideIme(){android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("input keyevent 111")).use{it.readBytes()};rule.waitForIdle()}
    private fun shot(name:String){rule.waitForIdle();dismissEmulatorLauncherAnr(inst);val b=checkNotNull(inst.uiAutomation.takeScreenshot());File(shots,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}}
}
