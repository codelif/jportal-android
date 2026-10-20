package `in`.codelif.jportal.feature.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.AppClock
import `in`.codelif.jportal.domain.Paper
import `in`.codelif.jportal.domain.short
import `in`.codelif.jportal.feature.attendance.titleCase
import `in`.codelif.jportal.session.AuthState
import `in`.codelif.ktjiit.model.SubjectFaculty
import `in`.codelif.jportal.ui.LocalBottomInset
import `in`.codelif.jportal.ui.LocalNavigator
import `in`.codelif.jportal.ui.components.CenteredLoading
import `in`.codelif.jportal.ui.components.Frog
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.components.MessageState
import `in`.codelif.jportal.ui.components.ScreenScaffold
import `in`.codelif.jportal.ui.theme.LocalExtraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

sealed interface ScheduleTimelineItem {
    data class ClassSlot(val item: ClassItem) : ScheduleTimelineItem
    data class LunchBreakSlot(val start: LocalTime, val end: LocalTime, val durationMinutes: Long) : ScheduleTimelineItem
    data class BreakSlot(val start: LocalTime, val end: LocalTime, val durationMinutes: Long) : ScheduleTimelineItem
}

@Composable
fun ScheduleScreen() {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val auth by graph.sessions.state.collectAsState()
    val session = (auth as? AuthState.SignedIn)?.session
    val personal by graph.repo.personal.state.collectAsState()

    var campusOverride by remember { mutableStateOf<String?>(null) }
    var batchOverride by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var selectedClassDetail by remember { mutableStateOf<ClassItem?>(null) }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshCount by remember { mutableIntStateOf(0) }

    // Live clock ticker every 10 seconds to keep progress bar and countdown fresh
    val nowTime by produceState(initialValue = AppClock.now().toLocalTime()) {
        while (true) {
            value = AppClock.now().toLocalTime()
            delay(10_000)
        }
    }
    val nowDateTime = remember(nowTime) { AppClock.now() }

    val scheduleData by produceState<LoadedSchedule?>(initialValue = null, campusOverride, batchOverride, personal.data, refreshCount) {
        value = ScheduleLoader.loadSchedule(
            context = context,
            session = session,
            personal = personal.data,
            campusOverride = campusOverride,
            batchOverride = batchOverride,
            forceRefresh = refreshCount > 0,
        )
    }

    val userBatch = personal.data?.general?.batch?.trim()?.takeIf { it.isNotEmpty() }
    val selectedBatch = scheduleData?.batch?.trim()
    val isUserBatch = userBatch != null && selectedBatch != null && userBatch.equals(selectedBatch, ignoreCase = true)

    val examPapers by produceState<List<Paper>>(initialValue = emptyList(), personal.data, refreshCount) {
        value = graph.repo.currentExamPapers(force = refreshCount > 0)
    }

    val effectiveExamPapers = remember(examPapers, isUserBatch) {
        if (isUserBatch) examPapers else emptyList()
    }

    val registeredSubjects by produceState<List<SubjectFaculty>>(initialValue = emptyList(), personal.data, refreshCount) {
        value = graph.repo.currentRegisteredSubjects(force = refreshCount > 0)
    }

    val portalSubjectCodes = remember(registeredSubjects) {
        registeredSubjects.map { it.code.trim().uppercase(Locale.US) }.filter { it.isNotEmpty() }.toSet()
    }
    val portalSubjectNames = remember(registeredSubjects) {
        registeredSubjects.map { it.name.trim().uppercase(Locale.US) }.filter { it.isNotEmpty() }.toSet()
    }

    val today = remember { AppClock.today().dayOfWeek }
    val todayName = remember(today) { ScheduleLoader.dayDisplayName(today) }

    val daysOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val examDaysOfWeek = remember(effectiveExamPapers) {
        effectiveExamPapers.mapNotNull { it.day }.map { ScheduleLoader.dayDisplayName(it.dayOfWeek) }.toSet()
    }

    val availableDays = remember(scheduleData, examDaysOfWeek) {
        val baseDays = scheduleData?.availableDays.orEmpty()
        val combined = daysOrder.filter { baseDays.contains(it) || examDaysOfWeek.contains(it) }
        if (combined.isNotEmpty()) combined else daysOrder
    }

    var selectedDay by remember(scheduleData, availableDays) {
        mutableStateOf(if (availableDays.contains(todayName)) todayName else availableDays.firstOrNull() ?: "Monday")
    }

    val selectedDate = remember(selectedDay, today) {
        computeDateForDay(selectedDay, AppClock.today())
    }

    val currentDayExams = remember(effectiveExamPapers, selectedDate) {
        if (selectedDate != null) effectiveExamPapers.filter { it.day == selectedDate } else emptyList()
    }

    val todayExams = remember(effectiveExamPapers) {
        effectiveExamPapers.filter { it.day == AppClock.today() }
    }

    val currentDayRawClasses = remember(scheduleData, selectedDay) {
        scheduleData?.classesByDay?.get(selectedDay).orEmpty()
    }

    val currentDayClasses = remember(
        currentDayRawClasses,
        scheduleData?.batch,
        selectedDay,
        portalSubjectCodes,
        portalSubjectNames,
        ElectiveMemoryStore.slotChoices.toMap(),
        ElectiveMemoryStore.chosenSubjects.toMap(),
    ) {
        filterElectivesForDay(
            classes = currentDayRawClasses,
            batch = scheduleData?.batch.orEmpty(),
            day = selectedDay,
            portalCodes = portalSubjectCodes,
            portalNames = portalSubjectNames,
        )
    }

    val timelineItems = remember(currentDayClasses) {
        buildTimeline(currentDayClasses)
    }

    val todayRawClasses = remember(scheduleData, todayName) {
        scheduleData?.classesByDay?.get(todayName).orEmpty()
    }

    val todayClasses = remember(
        todayRawClasses,
        scheduleData?.batch,
        todayName,
        portalSubjectCodes,
        portalSubjectNames,
        ElectiveMemoryStore.slotChoices.toMap(),
        ElectiveMemoryStore.chosenSubjects.toMap(),
    ) {
        filterElectivesForDay(
            classes = todayRawClasses,
            batch = scheduleData?.batch.orEmpty(),
            day = todayName,
            portalCodes = portalSubjectCodes,
            portalNames = portalSubjectNames,
        )
    }

    // Determine current class status for today
    val currentStatus = remember(todayClasses, nowTime) {
        computeClassStatus(todayClasses, nowTime, today)
    }

    val onRefresh: () -> Unit = {
        scope.launch {
            isRefreshing = true
            refreshCount++
            delay(400)
            isRefreshing = false
        }
    }

    ScreenScaffold(
        title = "Planner",
        subtitle = scheduleData?.let { "${it.campusDisplayName} · Batch ${it.batch}" },
        bottomPadding = LocalBottomInset.current,
        refreshing = isRefreshing,
        onRefresh = onRefresh,
        actions = {
            if (scheduleData != null) {
                Surface(
                    onClick = { showPicker = true },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            scheduleData?.batch ?: "Batch",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Ic(R.drawable.ic_expand_more, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
    ) {
        if (scheduleData == null && currentDayExams.isEmpty()) {
            item("empty") {
                MessageState(
                    frog = Frog.Sleep,
                    title = "No schedule found",
                    body = "Pull down to refresh or check your connection.",
                )
            }
        } else {
            // Top Hero Card (Exams if today has exams, otherwise regular class progress)
            item("hero") {
                if (selectedDay == todayName && todayExams.isNotEmpty()) {
                    val ongoingExam = todayExams.firstOrNull { it.status(nowDateTime) == Paper.Status.Live }
                    val nextExam = todayExams.firstOrNull { it.status(nowDateTime) == Paper.Status.Upcoming }
                    val allDone = todayExams.all { it.status(nowDateTime) == Paper.Status.Done }
                    HeroExamCard(
                        ongoingExam = ongoingExam,
                        nextExam = nextExam,
                        allExamsDone = allDone,
                        now = nowDateTime,
                        onClick = { nav.openAcademicsExams() },
                    )
                } else {
                    HeroClassCard(
                        status = currentStatus,
                        todayName = todayName,
                        isViewingToday = selectedDay == todayName,
                        onClassClick = { selectedClassDetail = it },
                    )
                }
            }

            // Day Selector Chips
            item("day_chips") {
                DaySelector(
                    availableDays = availableDays,
                    selectedDay = selectedDay,
                    todayName = todayName,
                    examDaysOfWeek = examDaysOfWeek,
                    onSelectDay = { selectedDay = it },
                )
            }

            // Schedule timeline for selected day:
            // "If the user has an exam on that day, only the exams should show up in the planner"
            if (currentDayExams.isNotEmpty()) {
                item("exams_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Examinations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                if (currentDayExams.size == 1) "1 Exam" else "${currentDayExams.size} Exams",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                items(currentDayExams, key = { it.key }) { exam ->
                    ExamPlannerCard(
                        exam = exam,
                        isToday = selectedDay == todayName,
                        now = nowDateTime,
                        onClick = { nav.openAcademicsExams() },
                    )
                }
            } else if (currentDayClasses.isEmpty()) {
                item("empty_day") {
                    MessageState(
                        frog = Frog.Sleep,
                        title = "No classes on $selectedDay",
                        body = "Take a break and recharge!",
                    )
                }
            } else {
                items(timelineItems, key = { item ->
                    when (item) {
                        is ScheduleTimelineItem.ClassSlot -> "class_${item.item.day}_${item.item.start}_${item.item.subjectCode}_${item.item.venue}"
                        is ScheduleTimelineItem.LunchBreakSlot -> "lunch_${item.start}_${item.end}"
                        is ScheduleTimelineItem.BreakSlot -> "break_${item.start}_${item.end}"
                    }
                }) { item ->
                    when (item) {
                        is ScheduleTimelineItem.ClassSlot -> {
                            ClassCard(
                                item = item.item,
                                isToday = selectedDay == todayName,
                                nowTime = nowTime,
                                onClick = { selectedClassDetail = item.item },
                            )
                        }
                        is ScheduleTimelineItem.LunchBreakSlot -> {
                            LunchBreakCard(item)
                        }
                        is ScheduleTimelineItem.BreakSlot -> {
                            BreakDivider(item)
                        }
                    }
                }
            }
        }
    }

    if (showPicker && scheduleData != null) {
        CampusBatchPickerSheet(
            currentCampus = scheduleData!!.campus,
            currentBatch = scheduleData!!.batch,
            onDismiss = { showPicker = false },
            onSelect = { campus, batch ->
                campusOverride = campus
                batchOverride = batch
                showPicker = false
            },
        )
    }

    if (selectedClassDetail != null && scheduleData != null) {
        val detail = selectedClassDetail!!
        val dayClasses = scheduleData!!.classesByDay.entries.firstOrNull {
            it.key.equals(detail.day, ignoreCase = true)
        }?.value.orEmpty()

        val electiveAlternatives = if (detail.isElective) {
            dayClasses.filter {
                it.isElective &&
                    it.start.trim().equals(detail.start.trim(), ignoreCase = true) &&
                    it.end.trim().equals(detail.end.trim(), ignoreCase = true)
            }
        } else {
            emptyList()
        }

        val isSlotPortalMatched = electiveAlternatives.any { alt ->
            portalSubjectCodes.contains(alt.subjectCode.trim().uppercase(Locale.US)) ||
                portalSubjectNames.contains(alt.subject.trim().uppercase(Locale.US))
        }

        ClassDetailSheet(
            item = detail,
            currentBatch = scheduleData!!.batch,
            availableElectives = if (!isSlotPortalMatched) electiveAlternatives else emptyList(),
            onSelectElective = { chosen ->
                ElectiveMemoryStore.setChoice(
                    batch = scheduleData!!.batch,
                    day = chosen.day,
                    start = chosen.start,
                    end = chosen.end,
                    chosen = chosen,
                )
                selectedClassDetail = chosen
            },
            onDismiss = { selectedClassDetail = null },
        )
    }
}

/** In-memory store for user elective selections */
object ElectiveMemoryStore {
    val slotChoices = mutableStateMapOf<String, String>()
    val chosenSubjects = mutableStateMapOf<String, String>()

    fun getChoice(batch: String, day: String, start: String, end: String, candidates: List<ClassItem>): ClassItem {
        val sKey = slotKey(batch, day, start, end)
        val directCode = slotChoices[sKey]
        if (directCode != null) {
            candidates.firstOrNull {
                it.subjectCode.equals(directCode, ignoreCase = true) ||
                    it.subject.equals(directCode, ignoreCase = true)
            }?.let { return it }
        }
        val batchLower = batch.trim().lowercase(Locale.US)
        for (c in candidates) {
            val codeKey = "${batchLower}_${c.subjectCode.trim().lowercase(Locale.US)}"
            val nameKey = "${batchLower}_${c.subject.trim().lowercase(Locale.US)}"
            if (c.subjectCode.isNotBlank() && chosenSubjects.containsKey(codeKey)) {
                return c
            }
            if (chosenSubjects.containsKey(nameKey)) {
                return c
            }
        }
        return candidates.first()
    }

    fun setChoice(batch: String, day: String, start: String, end: String, chosen: ClassItem) {
        val sKey = slotKey(batch, day, start, end)
        val id = chosen.subjectCode.ifBlank { chosen.subject }
        slotChoices[sKey] = id
        val batchLower = batch.trim().lowercase(Locale.US)
        if (chosen.subjectCode.isNotBlank()) {
            chosenSubjects["${batchLower}_${chosen.subjectCode.trim().lowercase(Locale.US)}"] = id
        }
        chosenSubjects["${batchLower}_${chosen.subject.trim().lowercase(Locale.US)}"] = id
    }

    fun slotKey(batch: String, day: String, start: String, end: String): String =
        "${batch.trim().lowercase(Locale.US)}_${day.trim().lowercase(Locale.US)}_${start.trim()}_${end.trim()}"
}

/** Filters electives for a day: displays portal matching elective if present, else user memory choice or first candidate */
internal fun filterElectivesForDay(
    classes: List<ClassItem>,
    batch: String,
    day: String,
    portalCodes: Set<String>,
    portalNames: Set<String>,
): List<ClassItem> {
    val nonElectives = classes.filter { !it.isElective }
    val electives = classes.filter { it.isElective }
    if (electives.isEmpty()) return classes

    val slotGroups = electives.groupBy { "${it.start.trim()}_${it.end.trim()}" }
    val chosenElectives = mutableListOf<ClassItem>()

    for ((_, candidates) in slotGroups) {
        val portalMatch = candidates.firstOrNull { candidate ->
            portalCodes.contains(candidate.subjectCode.trim().uppercase(Locale.US)) ||
                portalNames.contains(candidate.subject.trim().uppercase(Locale.US))
        }

        if (portalMatch != null) {
            chosenElectives.add(portalMatch)
        } else {
            val chosen = ElectiveMemoryStore.getChoice(
                batch = batch,
                day = day,
                start = candidates.first().start,
                end = candidates.first().end,
                candidates = candidates,
            )
            chosenElectives.add(chosen)
        }
    }

    return (nonElectives + chosenElectives).sortedBy { it.startTime }
}

private enum class DesignatedLunchSlot {
    SLOT_12_TO_1,
    SLOT_1_TO_2,
    NONE,
}

/** Builds a list of timeline items interleaving classes with breaks and lunch break */
internal fun buildTimeline(classes: List<ClassItem>): List<ScheduleTimelineItem> {
    if (classes.isEmpty()) return emptyList()
    val result = mutableListOf<ScheduleTimelineItem>()
    val noon = LocalTime.of(12, 0)
    val onePm = LocalTime.of(13, 0)
    val twoPm = LocalTime.of(14, 0)
    val lunch12to1StartTarget = LocalTime.of(11, 50)
    val lunch1to2StartTarget = LocalTime.of(12, 50)

    // Check whether the batch has classes occupying 12-1 or 1-2
    val hasClassIn12to1 = classes.any { c ->
        val s = c.startTime
        val e = c.endTime
        s != null && e != null && s.isBefore(onePm) && e.isAfter(noon)
    }

    val hasClassIn1to2 = classes.any { c ->
        val s = c.startTime
        val e = c.endTime
        s != null && e != null && s.isBefore(twoPm) && e.isAfter(onePm)
    }

    // "if the slot for a batch is free from 12-1, then that would be lunch break,
    // otherwise if free from 1-2, then that would be lunch break otherwise no lunch break"
    val designatedLunch = when {
        !hasClassIn12to1 -> DesignatedLunchSlot.SLOT_12_TO_1
        !hasClassIn1to2 -> DesignatedLunchSlot.SLOT_1_TO_2
        else -> DesignatedLunchSlot.NONE
    }

    for (i in classes.indices) {
        val current = classes[i]
        result.add(ScheduleTimelineItem.ClassSlot(current))

        if (i < classes.lastIndex) {
            val next = classes[i + 1]
            val currentEnd = current.endTime
            val nextStart = next.startTime
            if (currentEnd != null && nextStart != null && nextStart.isAfter(currentEnd)) {
                val duration = ChronoUnit.MINUTES.between(currentEnd, nextStart)
                if (duration > 0) {
                    when {
                        designatedLunch == DesignatedLunchSlot.SLOT_12_TO_1 &&
                            currentEnd.isBefore(onePm) && !nextStart.isBefore(onePm) -> {
                            val lunchStart = if (currentEnd.isBefore(lunch12to1StartTarget)) lunch12to1StartTarget else currentEnd
                            val lunchEnd = onePm

                            // Free time before lunchStart (>= 30 mins) -> regular break
                            if (currentEnd.isBefore(lunchStart)) {
                                val preDuration = ChronoUnit.MINUTES.between(currentEnd, lunchStart)
                                if (preDuration >= 30) {
                                    result.add(ScheduleTimelineItem.BreakSlot(currentEnd, lunchStart, preDuration))
                                }
                            }

                            // Lunch Break card
                            val lunchDuration = ChronoUnit.MINUTES.between(lunchStart, lunchEnd)
                            if (lunchDuration >= 30) {
                                result.add(ScheduleTimelineItem.LunchBreakSlot(lunchStart, lunchEnd, lunchDuration))
                            }

                            // Free time after lunchEnd (>= 30 mins) -> regular break
                            if (nextStart.isAfter(lunchEnd)) {
                                val postDuration = ChronoUnit.MINUTES.between(lunchEnd, nextStart)
                                if (postDuration >= 30) {
                                    result.add(ScheduleTimelineItem.BreakSlot(lunchEnd, nextStart, postDuration))
                                }
                            }
                        }

                        designatedLunch == DesignatedLunchSlot.SLOT_1_TO_2 &&
                            currentEnd.isBefore(twoPm) && !nextStart.isBefore(twoPm) -> {
                            val lunchStart = if (currentEnd.isBefore(lunch1to2StartTarget)) lunch1to2StartTarget else currentEnd
                            val lunchEnd = twoPm

                            // Free time before lunchStart (>= 30 mins) -> regular break
                            if (currentEnd.isBefore(lunchStart)) {
                                val preDuration = ChronoUnit.MINUTES.between(currentEnd, lunchStart)
                                if (preDuration >= 30) {
                                    result.add(ScheduleTimelineItem.BreakSlot(currentEnd, lunchStart, preDuration))
                                }
                            }

                            // Lunch Break card
                            val lunchDuration = ChronoUnit.MINUTES.between(lunchStart, lunchEnd)
                            if (lunchDuration >= 30) {
                                result.add(ScheduleTimelineItem.LunchBreakSlot(lunchStart, lunchEnd, lunchDuration))
                            }

                            // Free time after lunchEnd (>= 30 mins) -> regular break
                            if (nextStart.isAfter(lunchEnd)) {
                                val postDuration = ChronoUnit.MINUTES.between(lunchEnd, nextStart)
                                if (postDuration >= 30) {
                                    result.add(ScheduleTimelineItem.BreakSlot(lunchEnd, nextStart, postDuration))
                                }
                            }
                        }

                        duration >= 30 -> {
                            result.add(ScheduleTimelineItem.BreakSlot(currentEnd, nextStart, duration))
                        }
                    }
                }
            }
        }
    }
    return result
}

fun formatBreakDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/** Computes the real-time class progress and status for today */
private fun computeClassStatus(
    classes: List<ClassItem>,
    nowTime: LocalTime,
    dayOfWeek: DayOfWeek,
): ClassProgressStatus {
    if (dayOfWeek == DayOfWeek.SUNDAY || classes.isEmpty()) {
        return ClassProgressStatus.FreeDay(
            if (dayOfWeek == DayOfWeek.SUNDAY) "Sunday · Enjoy your weekend!" else "No classes scheduled for today"
        )
    }

    val ongoing = classes.firstOrNull { it.isOngoingAt(nowTime) }
    if (ongoing != null) {
        return ClassProgressStatus.Ongoing(
            currentClass = ongoing,
            progress = ongoing.progressAt(nowTime),
            minutesRemaining = ongoing.minutesRemainingAt(nowTime),
        )
    }

    val next = classes.firstOrNull { it.isUpcomingAt(nowTime) }
    if (next != null) {
        return ClassProgressStatus.Upcoming(
            nextClass = next,
            minutesUntil = next.minutesUntilAt(nowTime),
        )
    }

    // All classes ended for today
    if (classes.all { it.isFinishedAt(nowTime) }) {
        return ClassProgressStatus.DayEnded
    }

    return ClassProgressStatus.FreeDay()
}

/** Top hero card displaying ongoing class progress, next class, or day ended */
@Composable
private fun HeroClassCard(
    status: ClassProgressStatus,
    todayName: String,
    isViewingToday: Boolean,
    onClassClick: (ClassItem) -> Unit,
) {
    val extra = LocalExtraColors.current

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (status) {
                is ClassProgressStatus.Ongoing -> {
                    val c = status.currentClass
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = extra.good,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Text(
                                    "ONGOING CLASS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }

                        Text(
                            "${status.minutesRemaining}m left",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Text(
                        c.subject,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onClassClick(c) },
                    )

                    // Details row: Type, Venue, Teacher
                    ClassMetaRow(c)

                    // Progress Bar
                    Spacer(Modifier.height(2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                c.start,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                            Text(
                                "${(status.progress * 100).toInt()}% completed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                            Text(
                                c.end,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                is ClassProgressStatus.Upcoming -> {
                    val next = status.nextClass
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                "NEXT CLASS",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        ) {
                            Text(
                                if (status.minutesUntil <= 60) "In ${status.minutesUntil} mins" else "At ${next.start}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    Text(
                        next.subject,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onClassClick(next) },
                    )

                    ClassMetaRow(next)
                }

                is ClassProgressStatus.DayEnded -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = extra.goodContainer,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Ic(R.drawable.ic_check_circle, null, Modifier.size(28.dp), tint = extra.good)
                            }
                        }
                        Column {
                            Text(
                                "Day Ended",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "All classes finished for today. Relax!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                is ClassProgressStatus.FreeDay -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Ic(R.drawable.ic_celebration, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column {
                            Text(
                                "No Classes Today",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                status.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Meta row for venue, teacher, and type */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassMetaRow(c: ClassItem) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Type Badge (LEC, LAB, TUT)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        ) {
            Text(
                c.typeDescription,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Elective Badge
        if (c.isElective) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Ic(
                        R.drawable.ic_school,
                        contentDescription = "Elective",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        "Elective",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        // Time slot
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Ic(R.drawable.ic_schedule, null, Modifier.size(13.dp))
                Text(
                    "${c.start} – ${c.end}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Venue
        if (c.venue.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Ic(R.drawable.ic_meeting_room, null, Modifier.size(13.dp))
                    Text(
                        c.venue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Teacher
        if (c.teacher.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Ic(R.drawable.ic_person, null, Modifier.size(13.dp))
                    Text(
                        c.teacher,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Calculates the calendar date for a given day name relative to a reference date (defaults to today) */
internal fun computeDateForDay(dayName: String, referenceDate: LocalDate = AppClock.today()): LocalDate? {
    val targetDayOfWeek = runCatching {
        DayOfWeek.valueOf(dayName.uppercase(Locale.US))
    }.getOrNull() ?: return null
    val monday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val offset = (targetDayOfWeek.value - DayOfWeek.MONDAY.value).toLong()
    return monday.plusDays(offset)
}

/** Day selector chips */
@Composable
private fun DaySelector(
    availableDays: List<String>,
    selectedDay: String,
    todayName: String,
    examDaysOfWeek: Set<String> = emptySet(),
    onSelectDay: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(availableDays) { day ->
            val isSelected = day == selectedDay
            val isToday = day == todayName
            val hasExam = examDaysOfWeek.contains(day)

            FilterChip(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelectDay(day)
                    }
                },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            day.take(3),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (hasExam) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                                        CircleShape
                                    )
                            )
                        } else if (isToday) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                            )
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        }
    }
}

/** Top hero card for exam days */
@Composable
private fun HeroExamCard(
    ongoingExam: Paper?,
    nextExam: Paper?,
    allExamsDone: Boolean,
    now: java.time.LocalDateTime,
    onClick: () -> Unit,
) {
    val extra = LocalExtraColors.current

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                ongoingExam != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = extra.good,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Text(
                                    "ONGOING EXAM",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }

                        val over = ongoingExam.over
                        val remainingMinutes = if (over != null) ChronoUnit.MINUTES.between(now, over).coerceAtLeast(0L) else 0L
                        Text(
                            "${remainingMinutes}m left",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }

                    Text(
                        ongoingExam.slot.subjectName.titleCase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (ongoingExam.slot.room.isNotBlank()) {
                            Text(
                                "Room ${ongoingExam.slot.room}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (ongoingExam.slot.seat.isNotBlank()) {
                            Text(
                                "· Seat ${ongoingExam.slot.seat}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "View in Academics →",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                nextExam != null -> {
                    val s = nextExam.start
                    val minutesUntil = if (s != null) ChronoUnit.MINUTES.between(now, s).coerceAtLeast(0L) else 0L

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                "NEXT EXAM",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        ) {
                            Text(
                                if (minutesUntil in 1..60) "In $minutesUntil mins"
                                else nextExam.slot.start?.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US)) ?: nextExam.slot.from,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }

                    Text(
                        nextExam.slot.subjectName.titleCase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val where = listOfNotNull(
                            nextExam.slot.room.takeIf { it.isNotBlank() }?.let { "Room $it" },
                            nextExam.slot.seat.takeIf { it.isNotBlank() }?.let { "Seat $it" }
                        ).joinToString(" · ")
                        Text(
                            where.ifBlank { "Room & seat not out yet" },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            "View in Academics →",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                allExamsDone -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = extra.goodContainer,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Ic(R.drawable.ic_check_circle, null, Modifier.size(28.dp), tint = extra.good)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Exams Completed",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "All exams for today are finished. Well done!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        "Exam Day",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Individual exam card for the planner daily schedule */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamPlannerCard(
    exam: Paper,
    isToday: Boolean,
    now: java.time.LocalDateTime,
    onClick: () -> Unit,
) {
    val status = exam.status(now)
    val isLive = isToday && status == Paper.Status.Live
    val isDone = isToday && status == Paper.Status.Done
    val extra = LocalExtraColors.current

    val cardColor by animateColorAsState(
        targetValue = when {
            isLive -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
            isDone -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(400),
        label = "exam_card_color",
    )

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = cardColor,
        border = if (isLive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Left Time Column
            Column(
                modifier = Modifier.width(76.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                val startTimeStr = exam.slot.start?.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
                    ?: exam.slot.from.ifBlank { "TBA" }
                val endTimeStr = exam.slot.end?.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US)) ?: ""

                Text(
                    startTimeStr,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isLive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
                if (endTimeStr.isNotBlank()) {
                    Text(
                        endTimeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Event Badge (e.g. T1, T2, End sem)
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        exam.event.short.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Main Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        exam.slot.subjectName.titleCase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                    )

                    if (isLive) {
                        Surface(
                            shape = CircleShape,
                            color = extra.good,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                "NOW",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    } else if (isDone) {
                        Ic(
                            R.drawable.ic_done,
                            "Completed",
                            Modifier.size(18.dp).padding(start = 6.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (exam.slot.code.isNotBlank()) {
                    Text(
                        exam.slot.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Venue & Seat Tags
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (exam.slot.room.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Ic(R.drawable.ic_meeting_room, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Room ${exam.slot.room}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (exam.slot.seat.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Ic(R.drawable.ic_event_seat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text(
                                "Seat ${exam.slot.seat}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else if (!exam.slot.seated && !isDone) {
                        Text(
                            "Room & seat not out yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Redirect link to Academics
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "View in Academics",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Ic(
                        R.drawable.ic_chevron_right,
                        null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}


/** Individual class card for the daily timetable (without subject code) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassCard(
    item: ClassItem,
    isToday: Boolean,
    nowTime: LocalTime,
    onClick: () -> Unit,
) {
    val extra = LocalExtraColors.current
    val isOngoing = isToday && item.isOngoingAt(nowTime)
    val isPast = isToday && item.isFinishedAt(nowTime)

    val cardColor by animateColorAsState(
        targetValue = when {
            isOngoing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            isPast -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(400),
        label = "class_card_color",
    )

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = cardColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Left Time Column
            Column(
                modifier = Modifier.width(72.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    item.start,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isOngoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    item.end,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                // Type Pill (LEC, LAB, TUT)
                val (pillColor, textColor) = when (item.type.uppercase()) {
                    "P" -> extra.cgpa to Color.White
                    "T" -> extra.warn to Color.Black
                    else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                }

                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = pillColor,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        item.typeDescription,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Main Info Column (Subject code removed from card)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                    )

                    if (isOngoing) {
                        Surface(
                            shape = CircleShape,
                            color = extra.good,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                "NOW",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    } else if (isPast) {
                        Ic(
                            R.drawable.ic_done,
                            "Completed",
                            Modifier.size(18.dp).padding(start = 6.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Venue, Teacher & Elective Tags
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (item.isElective) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Ic(
                                    R.drawable.ic_school,
                                    contentDescription = "Elective",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    "Elective",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }

                    if (item.venue.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Ic(R.drawable.ic_meeting_room, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                item.venue,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (item.teacher.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Ic(R.drawable.ic_person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                item.teacher,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Lunch break card for empty slots between 12 PM and 2 PM highlighted in yellow */
@Composable
private fun LunchBreakCard(slot: ScheduleTimelineItem.LunchBreakSlot) {
    val extra = LocalExtraColors.current
    val yellow = extra.warn
    val yellowContainer = yellow.copy(alpha = 0.16f)
    val yellowBorder = yellow.copy(alpha = 0.38f)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = yellowContainer,
        border = BorderStroke(1.dp, yellowBorder),
        contentColor = yellow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = yellow.copy(alpha = 0.22f),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Ic(
                        R.drawable.ic_schedule,
                        null,
                        Modifier.size(20.dp),
                        tint = yellow,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Lunch Break",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = yellow,
                )
                val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
                Text(
                    "${slot.start.format(formatter)} – ${slot.end.format(formatter)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = yellow.copy(alpha = 0.22f),
            ) {
                Text(
                    formatBreakDuration(slot.durationMinutes),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = yellow,
                )
            }
        }
    }
}

/** Break divider drawing a straight line with the break length written next to the line */
@Composable
private fun BreakDivider(slot: ScheduleTimelineItem.BreakSlot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        Text(
            "${formatBreakDuration(slot.durationMinutes)} break",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Popup drawer / bottom sheet showing full class details and associated batches */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ClassDetailSheet(
    item: ClassItem,
    currentBatch: String,
    availableElectives: List<ClassItem> = emptyList(),
    onSelectElective: (ClassItem) -> Unit = {},
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: Type badge + Subject Code
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (item.type.uppercase()) {
                        "P" -> MaterialTheme.colorScheme.secondaryContainer
                        "T" -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                ) {
                    Text(
                        when (item.type.uppercase()) {
                            "P" -> "LABORATORY"
                            "T" -> "TUTORIAL"
                            else -> "LECTURE"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (item.type.uppercase()) {
                            "P" -> MaterialTheme.colorScheme.onSecondaryContainer
                            "T" -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                }

                if (item.subjectCode.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            item.subjectCode,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Subject Title
            Text(
                item.subject,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            // Elective Selection Dropdown (when multiple options exist and slot is not fixed by portal)
            if (availableElectives.size > 1) {
                ElectiveDropdownSelector(
                    currentElective = item,
                    availableElectives = availableElectives,
                    onSelect = onSelectElective,
                )
            }

            // Info Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val dayInfo = item.dayLabel.ifBlank { item.day }
                val daySuffix = if (dayInfo.isNotBlank()) " ($dayInfo)" else ""
                DetailRow(
                    icon = R.drawable.ic_schedule,
                    label = "Time Slot",
                    value = "${item.start} – ${item.end}$daySuffix",
                )
                if (item.venue.isNotBlank()) {
                    DetailRow(
                        icon = R.drawable.ic_meeting_room,
                        label = "Room / Venue",
                        value = item.venue,
                    )
                }
                if (item.teacher.isNotBlank()) {
                    DetailRow(
                        icon = R.drawable.ic_person,
                        label = "Faculty / Teacher",
                        value = item.teacher,
                    )
                }
                if (!item.category.isNullOrBlank()) {
                    DetailRow(
                        icon = R.drawable.ic_school,
                        label = "Category",
                        value = "${item.category}${if (item.isElective) " (Elective)" else ""}",
                    )
                }
            }

            // Associated Batches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val batches = item.batches
                val otherBatches = batches.filter { !it.equals(currentBatch, ignoreCase = true) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Batches Attending",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (otherBatches.isNotEmpty()) {
                        Text(
                            "Shared with ${otherBatches.size} other ${if (otherBatches.size == 1) "batch" else "batches"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (batches.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        batches.forEach { b ->
                            val isCurrent = b.equals(currentBatch, ignoreCase = true)
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = if (isCurrent) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        b,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isCurrent) {
                                        Text(
                                            "• You",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "Exclusive to Batch $currentBatch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ElectiveDropdownSelector(
    currentElective: ClassItem,
    availableElectives: List<ClassItem>,
    onSelect: (ClassItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Change Elective",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { expanded = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentElective.subject,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentElective.subjectCode.isNotBlank()) {
                            Text(
                                text = currentElective.subjectCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Ic(
                        R.drawable.ic_expand_more,
                        contentDescription = "Select Elective",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                availableElectives.forEach { elective ->
                    val isSelected = elective.subjectCode.isNotBlank() &&
                        elective.subjectCode.equals(currentElective.subjectCode, ignoreCase = true) ||
                        elective.subject.equals(currentElective.subject, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = elective.subject,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (elective.subjectCode.isNotBlank()) {
                                    Text(
                                        text = elective.subjectCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        trailingIcon = if (isSelected) {
                            { Ic(R.drawable.ic_done, null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelect(elective)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: Int,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Ic(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Bottom sheet to switch campus or batch */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusBatchPickerSheet(
    currentCampus: String,
    currentBatch: String,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var selectedCampus by remember { mutableStateOf(currentCampus) }
    val batches by produceState<List<String>>(initialValue = emptyList(), selectedCampus) {
        value = ScheduleLoader.getAvailableBatches(context, selectedCampus)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Select Campus & Batch",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Campus toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf("62" to "Sector 62", "128" to "Sector 128").forEach { (code, name) ->
                    val isSelected = selectedCampus == code
                    Surface(
                        onClick = { selectedCampus = code },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            name,
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            Text(
                "Batches (${batches.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Batches grid in natural ascending order
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(batches) { b ->
                    val isSelected = b.equals(currentBatch, ignoreCase = true) && selectedCampus == currentCampus
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(selectedCampus, b) },
                        label = { Text(b) },
                    )
                }
            }
        }
    }
}
