package `in`.codelif.jportal.screens

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import `in`.codelif.jportal.AppGraph
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.data.AppClock
import `in`.codelif.jportal.data.Palette
import `in`.codelif.jportal.data.ThemeMode
import `in`.codelif.jportal.demo.Demo
import `in`.codelif.jportal.ui.AppRoot
import `in`.codelif.jportal.ui.theme.JPortalTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * every main screen with the demo student, light, dark, and light at twice the
 * font size. record: ./gradlew :app:recordRoborazziGithubDebug
 * check: ./gradlew :app:verifyRoborazziGithubDebug
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class ScreensTest(private val look: Look) {

    enum class Look(val dark: Boolean, val font: Float) { Light(false, 1f), Dark(true, 1f), Huge(false, 2f) }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks() = Look.entries.map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        // a thursday morning in the middle of the odd semester
        AppClock.clock = Clock.fixed(Instant.parse("2026-09-24T04:30:00Z"), ZoneId.of("Asia/Kolkata"))
        graph = AppGraph(ApplicationProvider.getApplicationContext<Context>(), demo = true)
        Demo.seed(graph.cache)
        graph.prefs.setThemeMode(if (look.dark) ThemeMode.Dark else ThemeMode.Light)
        graph.prefs.setPalette(Palette.JPortal)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, look.font), LocalGraph provides graph) {
                JPortalTheme(if (look.dark) ThemeMode.Dark else ThemeMode.Light, Palette.JPortal) { AppRoot() }
            }
        }
        settle()
    }

    @After
    fun tearDown() {
        AppClock.clock = Clock.systemDefaultZone()
        graph.cache.close()
    }

    /** lets disk reads land and every animation finish, the waves included */
    private fun settle(ms: Long = 3000) {
        repeat((ms / 50).toInt()) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(50)
            Thread.sleep(2)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun shot(name: String) {
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_${look.name.lowercase()}.png")
    }

    private fun tab(key: String) {
        compose.onNodeWithTag("tab-$key").performClick()
        settle()
    }

    /** taps the on-screen [text], swiping the page up until it shows. other tabs are built too, so their copies are skipped */
    private fun open(text: String) {
        repeat(6) {
            val shown = compose.onAllNodesWithText(text).fetchSemanticsNodes().indexOfFirst { n ->
                val b = n.boundsInRoot
                b.left >= 0f && b.right <= root().width && b.top >= 0f && b.bottom <= root().height * 0.88f
            }
            if (shown >= 0) {
                compose.onAllNodesWithText(text)[shown].performClick()
                settle()
                return
            }
            compose.onRoot().performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.3f) }
            settle(600)
        }
        error("\"$text\" never showed up")
    }

    private fun root() = compose.onRoot().fetchSemanticsNode().size

    @Test
    fun attendance() = shot("attendance")

    @Test
    fun subject() {
        open("Compiler Design")
        shot("subject")
    }

    @Test
    fun academics() {
        tab("academics")
        shot("academics")
    }

    @Test
    fun marks() {
        tab("academics")
        open("Marks")
        shot("marks")
    }

    @Test
    fun exams() {
        tab("academics")
        open("Exams")
        shot("exams")
    }

    @Test
    fun gradeCard() {
        tab("academics")
        open("Semester 4")
        shot("gradecard")
    }

    @Test
    fun subjects() {
        tab("subjects")
        shot("subjects")
    }

    @Test
    fun me() {
        tab("me")
        shot("me")
    }

    @Test
    fun profile() {
        tab("me")
        open("Yash Malik")
        shot("profile")
    }

    @Test
    fun settings() {
        tab("me")
        open("Settings")
        shot("settings")
    }
}
