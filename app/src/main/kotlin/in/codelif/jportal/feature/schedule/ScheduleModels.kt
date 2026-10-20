package `in`.codelif.jportal.feature.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Serializable
data class ClassItem(
    @SerialName("subject") val subject: String = "",
    @SerialName("subjectcode") val subjectCode: String = "",
    @SerialName("teacher") val teacher: String = "",
    @SerialName("venue") val venue: String = "",
    @SerialName("type") val type: String = "L",
    @SerialName("start") val start: String = "",
    @SerialName("end") val end: String = "",
    @SerialName("day") val day: String = "",
    @SerialName("day_label") val dayLabel: String = "",
    @SerialName("batches") val batches: List<String> = emptyList(),
    @SerialName("is_elective") val isElective: Boolean = false,
    @SerialName("category") val category: String? = null,
    @SerialName("subgroup") val subgroup: String? = null,
) {
    val startTime: LocalTime? get() = parseTime(start)
    val endTime: LocalTime? get() = parseTime(end)

    val typeDescription: String
        get() = when (type.uppercase()) {
            "L" -> "LEC"
            "P" -> "LAB"
            "T" -> "TUT"
            else -> type.uppercase()
        }

    fun isOngoingAt(time: LocalTime): Boolean {
        val s = startTime ?: return false
        val e = endTime ?: return false
        return !time.isBefore(s) && time.isBefore(e)
    }

    fun isFinishedAt(time: LocalTime): Boolean {
        val e = endTime ?: return false
        return !time.isBefore(e)
    }

    fun isUpcomingAt(time: LocalTime): Boolean {
        val s = startTime ?: return false
        return time.isBefore(s)
    }

    fun progressAt(time: LocalTime): Float {
        val s = startTime ?: return 0f
        val e = endTime ?: return 0f
        val totalSeconds = ChronoUnit.SECONDS.between(s, e)
        if (totalSeconds <= 0) return 0f
        val elapsedSeconds = ChronoUnit.SECONDS.between(s, time)
        return (elapsedSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    }

    fun minutesRemainingAt(time: LocalTime): Long {
        val e = endTime ?: return 0L
        return ChronoUnit.MINUTES.between(time, e).coerceAtLeast(0L)
    }

    fun minutesUntilAt(time: LocalTime): Long {
        val s = startTime ?: return 0L
        return ChronoUnit.MINUTES.between(time, s).coerceAtLeast(0L)
    }

    fun sanitized(): ClassItem {
        val s = parseTime(start)
        val e = parseTime(end)
        if (s != null && e != null) {
            val durationHours = java.time.Duration.between(s, e).toHours()
            if (durationHours > 3) {
                val fixedEnd = end.replace("PM", "AM", ignoreCase = true).replace("pm", "am")
                return copy(end = fixedEnd)
            }
        }
        return this
    }

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        fun parseTime(timeStr: String): LocalTime? = runCatching {
            LocalTime.parse(timeStr.trim().uppercase(), TIME_FORMATTER)
        }.getOrNull()
    }
}

@Serializable
data class NodeItem(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class PlannerMetadata(
    val cacheVersion: String = "",
    val courses: List<NodeItem> = emptyList(),
    val semesters: Map<String, List<NodeItem>> = emptyMap(),
    val phases: Map<String, Map<String, List<NodeItem>>> = emptyMap(),
    val batches: Map<String, Map<String, Map<String, List<NodeItem>>>> = emptyMap(),
)

@Serializable
data class BatchScheduleJson(
    val cacheVersion: String = "",
    val classes: Map<String, List<ClassItem>> = emptyMap(),
)

@Serializable
data class HolidayItem(
    val date: String = "",
    val event: String = "",
    val day: String = "",
)

sealed interface ClassProgressStatus {
    data class Ongoing(
        val currentClass: ClassItem,
        val progress: Float,
        val minutesRemaining: Long,
    ) : ClassProgressStatus

    data class Upcoming(
        val nextClass: ClassItem,
        val minutesUntil: Long,
    ) : ClassProgressStatus

    data object DayEnded : ClassProgressStatus

    data class FreeDay(val message: String = "No classes scheduled today") : ClassProgressStatus
}
