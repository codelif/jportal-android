package `in`.codelif.jportal.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * run with ANDROID_SERIAL on a rooted emulator or a phone:
 * ./gradlew :app:generateBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(PACKAGE, includeInStartupProfile = true) {
        launch()
        attendance()
    }

    @Test
    fun everywhere() = rule.collect(PACKAGE) {
        launch()
        attendance()
        subject()
        academics()
        subjects()
        registration()
        me()
        swipeAround()
    }
}
