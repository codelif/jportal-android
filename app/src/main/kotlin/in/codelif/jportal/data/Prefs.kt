package `in`.codelif.jportal.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }
enum class Palette { Wallpaper, JPortal }
enum class AppIcon { Modern, Classic }

/** user settings, each one a state flow so compose redraws when it changes */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private inner class Pref<T>(private val key: String, default: T, private val read: (String, T) -> T, private val write: SharedPreferences.Editor.(String, T) -> Unit) {
        private val flow = MutableStateFlow(read(key, default))
        val state: StateFlow<T> = flow.asStateFlow()
        fun set(value: T) {
            flow.value = value
            sp.edit().apply { write(key, value) }.apply()
        }
    }

    private fun <E : Enum<E>> enumPref(key: String, default: E, values: Array<E>) = Pref(
        key, default,
        { k, d -> sp.getString(k, null)?.let { s -> values.firstOrNull { it.name == s } } ?: d },
        { k, v -> putString(k, v.name) },
    )

    private val themeMode = enumPref("theme", ThemeMode.System, ThemeMode.entries.toTypedArray())
    private val palette = enumPref("palette", Palette.Wallpaper, Palette.entries.toTypedArray())
    private val icon = enumPref("icon", AppIcon.Modern, AppIcon.entries.toTypedArray())
    private val amoled = Pref("amoled", false, { k, d -> sp.getBoolean(k, d) }, { k, v -> putBoolean(k, v) })
    private val target = Pref("target", 75, { k, d -> sp.getInt(k, d) }, { k, v -> putInt(k, v) })
    private val semester = Pref<String?>("semester", null, { k, d -> sp.getString(k, d) }, { k, v -> putString(k, v) })
    private val lastMarks = Pref<String?>("marks_seen", null, { k, d -> sp.getString(k, d) }, { k, v -> putString(k, v) })

    val themeModeState get() = themeMode.state
    val paletteState get() = palette.state
    val iconState get() = icon.state
    val amoledState get() = amoled.state
    val targetState get() = target.state
    /** registration code the user pinned, null means follow the current semester */
    val semesterState get() = semester.state
    /** fingerprint of the last marks we showed, for the "new marks" card */
    val marksSeenState get() = lastMarks.state

    fun setThemeMode(v: ThemeMode) = themeMode.set(v)
    fun setPalette(v: Palette) = palette.set(v)
    fun setIcon(v: AppIcon) = icon.set(v)
    fun setAmoled(v: Boolean) = amoled.set(v)
    fun setTarget(v: Int) = target.set(v.coerceIn(1, 100))
    fun setSemester(code: String?) = semester.set(code)
    fun setMarksSeen(v: String?) = lastMarks.set(v)

    fun clearAccountBits() {
        semester.set(null)
        lastMarks.set(null)
    }
}
