package `in`.codelif.jportal.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import `in`.codelif.jportal.ui.theme.JPortalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class ClockTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // a clock wider than its column wraps the am/pm onto a line maxLines throws away
    @Test
    fun `every time of day fits the clock column`() {
        val misfits = mutableListOf<String>()
        val times = (0 until 24 * 60).map { LocalTime.of(it / 60, it % 60) }
        compose.setContent {
            val d = LocalDensity.current
            for (scale in listOf(1f, 1.3f, 2f)) {
                CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                    JPortalTheme {
                        val column = with(LocalDensity.current) { rememberClockWidth().roundToPx() }
                        val measurer = rememberTextMeasurer()
                        val fit = Constraints(maxWidth = column)
                        val small = MaterialTheme.typography.labelMedium
                        for (weight in listOf(FontWeight.Bold, FontWeight.Normal)) {
                            val big = MaterialTheme.typography.titleLarge.copy(fontWeight = weight)
                            for (t in times) {
                                if (measurer.measure(face(t, small), big, maxLines = 1, constraints = fit).hasVisualOverflow) misfits += "$t $weight x$scale"
                            }
                        }
                        // ends only ever come after their start, so the am-start/pm-end pairs cover every form
                        for (start in listOf(LocalTime.MIDNIGHT, LocalTime.NOON)) for (end in times.filter { it >= start }) {
                            val s = until(start, end)
                            if (measurer.measure(s, small, maxLines = 1, constraints = fit).hasVisualOverflow) misfits += "\"$s\" x$scale"
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), misfits.distinct().take(20))
    }
}
