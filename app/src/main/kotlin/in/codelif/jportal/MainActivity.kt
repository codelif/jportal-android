package `in`.codelif.jportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import `in`.codelif.jportal.ui.AppRoot
import `in`.codelif.jportal.ui.theme.JPortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
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
