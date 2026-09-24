package `in`.codelif.jportal.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

// release builds of these variants carry the demo student, see app/build.gradle.kts
const val PACKAGE = "in.codelif.jportal.android"

private const val WAIT = 5_000L

fun MacrobenchmarkScope.launch() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.res("tab-attendance")), WAIT)
}

private fun MacrobenchmarkScope.tap(res: String) {
    device.wait(Until.findObject(By.res(res)), WAIT)?.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.tapText(text: String) {
    device.wait(Until.findObject(By.text(text)), WAIT)?.click()
    device.waitForIdle()
}

/** a thumb's fling down the page and back up */
private fun MacrobenchmarkScope.flingPage() {
    val x = device.displayWidth / 2
    val h = device.displayHeight
    repeat(2) {
        device.swipe(x, h * 3 / 4, x, h / 4, 12)
        device.waitForIdle()
    }
    repeat(2) {
        device.swipe(x, h / 4, x, h * 3 / 4, 12)
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.swipeTab(forward: Boolean) {
    val w = device.displayWidth
    val y = device.displayHeight / 2
    if (forward) device.swipe(w * 4 / 5, y, w / 5, y, 16) else device.swipe(w / 5, y, w * 4 / 5, y, 16)
    device.waitForIdle()
}

fun MacrobenchmarkScope.attendance() {
    device.wait(Until.hasObject(By.text("Compiler Design")), WAIT)
    flingPage()
}

fun MacrobenchmarkScope.subject() {
    tapText("Compiler Design")
    device.wait(Until.hasObject(By.text("Calendar")), WAIT)
    flingPage()
    device.pressBack()
    device.waitForIdle()
}

fun MacrobenchmarkScope.academics() {
    tap("tab-academics")
    flingPage()
    tapText("Marks")
    flingPage()
    tapText("Exams")
    flingPage()
    tapText("Overview")
}

fun MacrobenchmarkScope.subjects() {
    tap("tab-subjects")
    flingPage()
    device.findObject(By.text("Search subjects, teachers, codes"))?.let {
        it.click()
        it.text = "lab"
        device.waitForIdle()
        device.pressBack()
        device.findObject(By.desc("Clear"))?.click()
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.me() {
    tap("tab-me")
    tapText("Yash Malik")
    device.wait(Until.hasObject(By.text("Contact")), WAIT)
    flingPage()
    device.pressBack()
    device.waitForIdle()
}

/** tab to tab by swiping, the way people actually move around */
fun MacrobenchmarkScope.swipeAround() {
    tap("tab-attendance")
    repeat(3) { swipeTab(forward = true) }
    repeat(3) { swipeTab(forward = false) }
}

