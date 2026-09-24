package `in`.codelif.jportal.data

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** the ui's idea of now. screenshot tests pin it so dates in the pictures never drift */
object AppClock {
    @Volatile
    var clock: Clock = Clock.systemDefaultZone()

    fun millis(): Long = clock.millis()
    fun today(): LocalDate = LocalDate.now(clock)
    fun now(): LocalDateTime = LocalDateTime.now(clock)
}
