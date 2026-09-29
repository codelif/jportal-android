package `in`.codelif.jportal

import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import `in`.codelif.jportal.ui.AppRoot
import `in`.codelif.jportal.ui.theme.JPortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splash ->
            // the app is ready before the blep is done, let it land, then zoom the frog out over the app
            val left = (splash.iconAnimationStartMillis + splash.iconAnimationDurationMillis - SystemClock.uptimeMillis()).coerceIn(0, 720)
            splash.iconView.animate().setStartDelay(left).scaleX(1.15f).scaleY(1.15f).setDuration(250).start()
            splash.view.animate().setStartDelay(left).alpha(0f).setDuration(250).withEndAction { splash.remove() }.start()
        }
        WindowCompat.enableEdgeToEdge(window)
        // WindowCompat drops the 3-button nav scrim, keep it so the buttons stay readable over content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = true
        super.onCreate(savedInstanceState)
        val graph = graph
        setContent {
            val mode by graph.prefs.themeModeState.collectAsState()
            val palette by graph.prefs.paletteState.collectAsState()
            val amoled by graph.prefs.amoledState.collectAsState()
            CompositionLocalProvider(LocalGraph provides graph) {
                JPortalTheme(mode, palette, amoled) { AppRoot() }
            }
        }
    }
}
