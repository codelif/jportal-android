package `in`.codelif.jportal.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** frame times for the moves people make all day: scroll the list, swipe tabs, open a subject */
@RunWith(AndroidJUnit4::class)
class JankBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun frames(block: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { launch() },
        measureBlock = block,
    )

    @Test
    fun attendanceScroll() = frames { attendance() }

    @Test
    fun tabSwipes() = frames { swipeAround() }

    @Test
    fun subjectPage() = frames { subject() }

    @Test
    fun academicsViews() = frames { academics() }

    @Test
    fun subjectsSearch() = frames { subjects() }
}
