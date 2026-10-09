package com.wildtwins.deerdeck

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.wildtwins.deerdeck.data.model.ObservationEntity
import com.wildtwins.deerdeck.data.model.ObservationSessionEntity
import com.wildtwins.deerdeck.data.settings.AppThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Actual app UI, rendered on the host. No device, external storage, network, or real records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PublicGalleryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: MainActivity

    @Test
    fun fictionalGalleryAndNavigation() {
        val app = ApplicationProvider.getApplicationContext<DeerDeckApplication>()
        WorkManager.initialize(app, Configuration.Builder().build())
        app.settingsRepository.acceptPrivacyNotice(environmentCaptureEnabled = false)
        app.settingsRepository.setAutoBackupEnabled(false)
        app.settingsRepository.setThemeMode(AppThemeMode.Light)
        runBlocking {
            app.repository.ensureDefaultSpecies()
            val dao = app.database.dao()
            val species = dao.getActiveSpecies("wildlife_watching")
            val now = System.currentTimeMillis()
            for (day in 0..20) {
                val start = now - day * 86_400_000L - 20 * 60_000L
                val session = dao.insertSession(ObservationSessionEntity(
                    startedAt = start,
                    endedAt = if (day == 0) null else start + 60 * 60_000L,
                    lastActivityAt = if (day == 0) now else start + 60 * 60_000L,
                    title = if (day == 0) "Fictional meadow watch" else "Sample outing ${21 - day}",
                    notes = "Invented demonstration records. Not a real place or sighting.",
                    fieldLocationName = if (day % 2 == 0) "Demo Meadow" else "Sample Grove",
                ))
                listOf("Doe", "Buck", "Turkey", "Squirrel").forEachIndexed { index, name ->
                    species.firstOrNull { it.name == name }?.let {
                        dao.insertObservation(ObservationEntity(
                            sessionId = session, speciesId = it.id,
                            observedAt = start + (index + 1) * 60_000L,
                            quantity = 1 + (day + index) % 4,
                            notes = "Fictional sample",
                        ))
                    }
                }
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity = it }
            compose.waitUntil(20_000) { compose.onAllNodesWithText("Doe").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Doe").assertIsDisplayed()
            capture("01-observe")
            val before = runBlocking { app.database.dao().getAllObservationsForBackup().sumOf { it.quantity } }
            click("Doe")
            compose.waitUntil(10_000) {
                ShadowLooper.idleMainLooper()
                runBlocking { app.database.dao().getAllObservationsForBackup().sumOf { it.quantity } } == before + 1
            }
            compose.waitForIdle()
            capture("02-count")
            click("Stats")
            compose.waitUntil(20_000) {
                ShadowLooper.idleMainLooper()
                compose.onAllNodesWithText("20h").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
            compose.onNodeWithText("30-day wildlife trend").assertIsDisplayed()
            compose.waitForIdle()
            capture("03-stats")
            click("History")
            compose.waitUntil(10_000) {
                ShadowLooper.idleMainLooper()
                compose.onAllNodesWithText("Invented demonstration records.", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            capture("04-history")
            click("Profiles")
            compose.onNodeWithText("Birds").assertIsDisplayed()
            compose.waitForIdle()
            capture("05-profiles")
        }
    }

    private fun click(text: String) {
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val out = File("build/public-gallery").apply { mkdirs() }
        compose.runOnIdle {
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(out, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }
}
