package com.period.local

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.period.local.data.BackupCodec
import com.period.local.domain.PredictionEngine
import com.period.local.ui.shortText
import com.period.local.data.PeriodDatabase
import com.period.local.data.PeriodRepository
import com.period.local.ui.PeriodApp
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: PeriodDatabase
    private lateinit var repository: PeriodRepository
    private lateinit var model: PeriodViewModel
    private val fontScale = mutableFloatStateOf(1f)
    private val context get() = ApplicationProvider.getApplicationContext<PeriodApplication>()

    @Before fun start() {
        database = Room.inMemoryDatabaseBuilder(context, PeriodDatabase::class.java).build()
        repository = PeriodRepository(database)
        runBlocking { repository.initialize(BackupCodec.encode(SyntheticData.records(LocalDate.now())), LocalDate.now()) }
        compose.runOnUiThread { model = PeriodViewModel(context, repository) }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.floatValue)) { PeriodApp(model) }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("今天结束").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun finish() {
        compose.runOnUiThread { model.viewModelScope.cancel() }
        database.close()
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun homeShowsSyntheticPredictionAndCompletingRecordPersists() {
        val prediction = PredictionEngine.calculate(SyntheticData.records(LocalDate.now()))!!
        compose.onNodeWithText("预计 ${prediction.estimatedStart.shortText()}").assertExists()
        compose.onNodeWithText("补录或修改").assertDoesNotExist()
        compose.onNodeWithText("今天开始").assertIsNotEnabled()
        capture("qa-home.png")
        compose.onNodeWithText("今天结束").performScrollTo().performClick()
        compose.waitUntil(10_000) { !model.busy.value && model.state.value.records.last().end == LocalDate.now() }
        compose.onNodeWithText("保存记录").assertDoesNotExist()
        compose.onNodeWithText("今天结束").assertIsNotEnabled()
        compose.onNodeWithText("今天开始").assertIsNotEnabled()
        runBlocking {
            assertEquals(16, repository.all().size)
            assertEquals(LocalDate.now(), repository.all().last().end)
        }
    }

    @Test fun startShortcutUsesTodayAndPreventsRepeatedStarts() {
        runBlocking {
            val latest = repository.all().last()
            repository.save(latest.copy(end = LocalDate.now().minusDays(1)), latest, LocalDate.now())
        }
        compose.waitUntil(10_000) { model.state.value.records.last().end != null }
        compose.onNodeWithText("今天开始").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { !model.busy.value && model.state.value.records.size == 17 }
        compose.onNodeWithText("保存记录").assertDoesNotExist()
        compose.onNodeWithText("今天开始").assertIsNotEnabled()
        compose.onNodeWithText("今天结束").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { !model.busy.value && model.state.value.records.last().end == LocalDate.now() }
        runBlocking {
            assertEquals(17, repository.all().size)
            val record = repository.all().last()
            assertEquals(LocalDate.now(), record.start)
            assertEquals(LocalDate.now(), record.end)
            assertEquals(1, record.duration)
        }
    }

    @Test fun calendarCanAddAndEditAnActualRecord() {
        compose.onNodeWithText("日历").performClick()
        val date = LocalDate.now().minusDays(15)
        val monthDistance = java.time.temporal.ChronoUnit.MONTHS.between(
            java.time.YearMonth.from(date), java.time.YearMonth.from(LocalDate.now())).toInt()
        repeat(monthDistance) { compose.onNodeWithContentDescription("上个月").performClick() }
        val label = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日"))
        compose.onNodeWithContentDescription(label).performClick()
        compose.onNodeWithText("补录这一天").performScrollTo().performClick()
        compose.onNodeWithText(date.toString()).assertExists()
        compose.onNodeWithText("保存记录").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("修改这次记录").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { assertEquals(17, repository.all().size) }
        compose.onNodeWithText("修改这次记录").performScrollTo().performClick()
        compose.onNodeWithText("已结束，填写结束日期").performClick()
        compose.onNodeWithText("结束日期").performTextInput(date.plusDays(4).toString())
        compose.onNodeWithText("保存记录").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("修改经期记录").fetchSemanticsNodes().isEmpty() }
        runBlocking { assertEquals(date.plusDays(4), repository.all().find { it.start == date }!!.end) }
        capture("qa-calendar.png")
    }

    @Test fun trendsAndSettingsAreReachableAndBackWorks() {
        compose.onNodeWithText("趋势").performClick()
        compose.onNodeWithText("16次记录 · 15个完整周期").assertExists()
        compose.onNodeWithText("预测表现").performScrollTo().assertIsDisplayed()
        capture("qa-trends.png")
        compose.onNodeWithContentDescription("设置与备份").performScrollTo().performClick()
        compose.onNodeWithText("导出本地备份").assertIsDisplayed()
        compose.onNodeWithText("导入备份 / 美柚记录").assertIsDisplayed()
        capture("qa-settings.png")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("预测表现").assertExists()
    }

    @Test fun localBackupFileCanBeWrittenReadAndImportedThroughViewModel() {
        val file = File.createTempFile("period-backup-test-", ".json", context.cacheDir)
        try {
            val uri = android.net.Uri.fromFile(file)
            compose.runOnUiThread { model.export(uri) }
            compose.waitUntil(10_000) { !model.busy.value && file.length() > 0 }
            val restored = com.period.local.data.BackupCodec.decode(file.readText(), LocalDate.now())
            assertEquals(16, restored.size)
            assertNull(restored.last().end)
            compose.runOnUiThread { model.readImport(uri) }
            compose.waitUntil(10_000) { model.importPreview.value != null }
            compose.onNodeWithText("16条完全重复，将自动跳过").assertExists()
            compose.onNodeWithText("确认导入").performClick()
            compose.waitUntil(10_000) { model.importPreview.value == null && !model.busy.value }
            runBlocking { assertEquals(16, repository.all().size) }
        } finally { file.delete() }
    }

    @Test fun enlargedFontAllowsScrollingToPrimaryAction() {
        compose.runOnUiThread { fontScale.floatValue = 1.6f }
        compose.onNodeWithText("今天结束").performScrollTo().assertIsDisplayed()
        capture("qa-large-font.png")
        compose.onNodeWithText("今天开始").assertIsDisplayed()
        compose.onNodeWithText("今天结束").performClick()
        compose.waitUntil(10_000) { !model.busy.value && model.state.value.records.last().end == LocalDate.now() }
    }
}
