package `in`.codelif.jportal.feature.schedule

import androidx.annotation.VisibleForTesting
import android.content.Context
import `in`.codelif.ktjiit.auth.Session
import `in`.codelif.ktjiit.model.PersonalInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

data class LoadedSchedule(
    val campus: String,         // "62" or "128"
    val campusDisplayName: String, // "Sector 62" or "Sector 128"
    val batch: String,          // e.g. "F7"
    val semesterLabel: String,  // e.g. "Semester 3"
    val scheduleKey: String,
    val classesByDay: Map<String, List<ClassItem>>,
    val availableDays: List<String>,
    val holidays: List<HolidayItem> = emptyList(),
)

object ScheduleLoader {
    const val METADATA_URL = "https://raw.githubusercontent.com/codelif/jiit-planner-cdn/refs/heads/main/metadata.json"
    const val CLASSES_URL = "https://raw.githubusercontent.com/codelif/jiit-planner-cdn/refs/heads/main/classes.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var cachedRoot: JsonObject? = null

    @VisibleForTesting
    internal var testRoot: JsonObject? = null

    private fun fetchUrl(urlStr: String): String? = runCatching {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "JPortal-Android")
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private suspend fun getRoot(context: Context, forceRefresh: Boolean = false): JsonObject =
        testRoot ?: withContext(Dispatchers.IO) {
            if (!forceRefresh && cachedRoot != null) {
                return@withContext cachedRoot!!
            }
            val classesFile = File(context.filesDir, "planner_classes.json")
            val versionFile = File(context.filesDir, "planner_version.txt")
            val localVersion = if (versionFile.exists()) runCatching { versionFile.readText().trim() }.getOrNull() else null

            // Check metadata.json from CDN for cache version
            val metaText = fetchUrl(METADATA_URL)
            val remoteVersion = metaText?.let {
                runCatching { json.decodeFromString<PlannerMetadata>(it).cacheVersion }.getOrNull()
            }

            val shouldDownload = forceRefresh ||
                !classesFile.exists() ||
                classesFile.length() == 0L ||
                (remoteVersion != null && (localVersion == null || remoteVersion != localVersion))

            if (shouldDownload && remoteVersion != null) {
                val classesText = fetchUrl(CLASSES_URL)
                if (classesText != null) {
                    runCatching {
                        classesFile.writeText(classesText)
                        versionFile.writeText(remoteVersion)
                    }
                }
            }

            val jsonString = if (classesFile.exists()) {
                runCatching { classesFile.readText() }.getOrNull()
            } else null

            val root = jsonString?.let {
                runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
            } ?: cachedRoot ?: JsonObject(emptyMap())

            cachedRoot = root
            root
        }

    /** Campus is either "62" or "128" */
    fun detectCampus(session: Session?, personal: PersonalInfo?, batchOverride: String? = null): String {
        val instituteName = session?.instituteName.orEmpty()
        val instituteId = session?.instituteId.orEmpty()
        val generalInstitute = personal?.general?.institute.orEmpty()

        if (instituteName.contains("128") || instituteId.contains("128") || generalInstitute.contains("128")) {
            return "128"
        }
        if (instituteName.contains("62") || instituteId.contains("62") || generalInstitute.contains("62")) {
            return "62"
        }

        val b = (batchOverride ?: personal?.general?.batch.orEmpty()).trim().uppercase()
        if (b.startsWith("F") || b.startsWith("E") || b.startsWith("BCA3") || b.startsWith("BCA4")) {
            return "128"
        }
        return "62"
    }

    /** Course id: bca-62, btech-128, or btech-62 */
    fun detectCourse(session: Session?, personal: PersonalInfo?, campus: String): String {
        val prog = personal?.general?.program.orEmpty().uppercase()
        val branch = personal?.general?.branch.orEmpty().uppercase()
        if (prog.contains("BCA") || branch.contains("BCA")) {
            return "bca-62"
        }
        return "btech-$campus"
    }

    fun campusDisplayName(campus: String): String = when (campus) {
        "128" -> "Sector 128"
        else -> "Sector 62"
    }

    fun naturalSort(batches: Collection<String>): List<String> {
        val regex = Regex("([a-zA-Z]+)(\\d+)?(.*)")
        return batches.sortedWith(
            compareBy<String> { b ->
                val match = regex.matchEntire(b)
                match?.groupValues?.get(1)?.uppercase() ?: b
            }.thenBy { b ->
                val match = regex.matchEntire(b)
                match?.groupValues?.get(2)?.toIntOrNull() ?: 0
            }.thenBy { b ->
                val match = regex.matchEntire(b)
                match?.groupValues?.get(3) ?: ""
            }
        )
    }

    suspend fun getAvailableBatches(context: Context, campus: String): List<String> = withContext(Dispatchers.IO) {
        val root = getRoot(context)
        val targetCampus = "-$campus"
        val batches = mutableSetOf<String>()
        root.keys.forEach { key ->
            if (key.contains(targetCampus) && !key.equals("electives", ignoreCase = true) && !key.equals("holidays", ignoreCase = true)) {
                val parts = key.split('_')
                if (parts.size >= 4) {
                    val batchName = parts.drop(3).joinToString("_").uppercase()
                    batches.add(batchName)
                }
            }
        }
        naturalSort(batches)
    }

    suspend fun loadSchedule(
        context: Context,
        session: Session?,
        personal: PersonalInfo?,
        campusOverride: String? = null,
        batchOverride: String? = null,
        registeredSubjectCodes: Set<String> = emptySet(),
        forceRefresh: Boolean = false,
    ): LoadedSchedule? = withContext(Dispatchers.IO) {
        val root = getRoot(context, forceRefresh)
        if (root.isEmpty()) return@withContext null

        val userBatch = (batchOverride ?: personal?.general?.batch.orEmpty()).trim().ifEmpty { "F7" }
        val campus = campusOverride ?: detectCampus(session, personal, userBatch)
        val course = detectCourse(session, personal, campus)
        val semesterNumber = personal?.general?.semester?.takeIf { it > 0 }

        // Find candidate keys for this campus and batch
        // Key format: <prog>-<campus>_sem<sem>_phase1_<batch>
        val targetCampusPattern = "-$campus"
        val targetBatchLower = userBatch.lowercase()

        val matchingKeys = root.keys.filter { key ->
            if (key == "electives" || key == "holidays") return@filter false
            val parts = key.split('_')
            if (parts.size < 4) return@filter false
            val progCampus = parts[0]
            val keyBatch = parts.drop(3).joinToString("_").lowercase()
            (progCampus == course || progCampus.contains(targetCampusPattern)) &&
                (keyBatch == targetBatchLower || keyBatch.contains(targetBatchLower))
        }

        // Pick exact semester if available, or latest available semester
        val chosenKey = if (semesterNumber != null) {
            matchingKeys.firstOrNull { it.contains("sem$semesterNumber") }
                ?: matchingKeys.maxByOrNull { extractSemNumber(it) }
        } else {
            matchingKeys.maxByOrNull { extractSemNumber(it) }
        } ?: root.keys.firstOrNull {
            it.contains(targetCampusPattern) && it != "electives" && it != "holidays"
        } ?: return@withContext null

        val batchElement = root[chosenKey] ?: return@withContext null
        val batchSchedule = json.decodeFromJsonElement<BatchScheduleJson>(batchElement)

        val holidays = runCatching {
            root["holidays"]?.let { json.decodeFromJsonElement<List<HolidayItem>>(it) }
        }.getOrNull() ?: emptyList()

        val semNum = extractSemNumber(chosenKey)
        val semLabel = if (semNum > 0) "Semester $semNum" else "Semester 1"

        // Sanitize classes (fixing 8 AM class ending at 08:50 PM bug) and sort classes
        val sortedClasses = batchSchedule.classes.mapValues { (_, classes) ->
            classes.map { it.sanitized() }
                .sortedBy { it.startTime }
        }

        val daysOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val availableDays = daysOrder.filter { sortedClasses.containsKey(it) && sortedClasses[it]?.isNotEmpty() == true }

        LoadedSchedule(
            campus = campus,
            campusDisplayName = campusDisplayName(campus),
            batch = userBatch.uppercase(),
            semesterLabel = semLabel,
            scheduleKey = chosenKey,
            classesByDay = sortedClasses,
            availableDays = if (availableDays.isNotEmpty()) availableDays else daysOrder,
            holidays = holidays,
        )
    }

    private fun extractSemNumber(key: String): Int {
        val parts = key.split('_')
        if (parts.size > 1) {
            val semPart = parts[1]
            return semPart.removePrefix("sem").toIntOrNull() ?: 0
        }
        return 0
    }

    fun dayDisplayName(dayOfWeek: DayOfWeek): String =
        dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
}
