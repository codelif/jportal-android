package `in`.codelif.jportal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.feature.attendance.AttendanceScreen
import `in`.codelif.jportal.feature.exams.ExamsScreen
import `in`.codelif.jportal.feature.grades.GradeCardScreen
import `in`.codelif.jportal.feature.grades.GradesScreen
import `in`.codelif.jportal.feature.grades.MarksScreen
import `in`.codelif.jportal.feature.me.AboutScreen
import `in`.codelif.jportal.feature.me.BankScreen
import `in`.codelif.jportal.feature.me.FeedbackScreen
import `in`.codelif.jportal.feature.me.FeesScreen
import `in`.codelif.jportal.feature.me.HostelScreen
import `in`.codelif.jportal.feature.me.MeScreen
import `in`.codelif.jportal.feature.me.ProfileScreen
import `in`.codelif.jportal.feature.settings.SettingsScreen
import `in`.codelif.jportal.feature.signin.ReauthHost
import `in`.codelif.jportal.feature.signin.SignInScreen
import `in`.codelif.jportal.feature.signin.SignInSheet
import `in`.codelif.jportal.feature.subject.SubjectScreen
import `in`.codelif.jportal.session.AuthState
import `in`.codelif.jportal.ui.components.Ic
import `in`.codelif.jportal.ui.nav.NavHost
import `in`.codelif.jportal.ui.nav.Navigator
import `in`.codelif.jportal.ui.nav.Route

val LocalNavigator = compositionLocalOf<Navigator> { error("no navigator") }

/** room the floating bottom bar takes, lists pad by this so nothing hides under it */
val LocalBottomInset = compositionLocalOf { 0.dp }

@Composable
fun AppRoot() {
    val graph = LocalGraph.current
    val auth by graph.sessions.state.collectAsState()
    AnimatedContent(auth is AuthState.SignedIn, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "auth") { signedIn ->
        if (signedIn) SignedIn() else SignInScreen()
    }
}

@Composable
private fun SignedIn() {
    val graph = LocalGraph.current
    val nav = remember { Navigator() }
    val reauth by graph.sessions.reauth.collectAsState()
    val needsSheet by graph.sessions.needsSheet.collectAsState()
    val onTab = nav.stack.size == 1
    val barHeight: Dp = 80.dp

    CompositionLocalProvider(LocalNavigator provides nav, LocalBottomInset provides if (onTab) barHeight else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, Modifier.fillMaxSize()) { route -> Screen(route) }

            AnimatedVisibility(
                onTab,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) { BottomBar(nav) }

            if (reauth != null) ReauthHost()
        }
        if (needsSheet) SignInSheet()
    }
    LaunchedEffect(Unit) { graph.sessions.loadConfig() }
}

@Composable
private fun Screen(route: Route) = when (route) {
    Route.Attendance -> AttendanceScreen()
    Route.Exams -> ExamsScreen()
    Route.Grades -> GradesScreen()
    Route.Me -> MeScreen()
    is Route.Subject -> SubjectScreen(route)
    is Route.Marks -> MarksScreen(route)
    is Route.GradeCard -> GradeCardScreen(route)
    Route.Profile -> ProfileScreen()
    Route.Fees -> FeesScreen()
    Route.Bank -> BankScreen()
    Route.Hostel -> HostelScreen()
    Route.Feedback -> FeedbackScreen()
    Route.Settings -> SettingsScreen()
    Route.About -> AboutScreen()
}

private data class TabSpec(val tab: Route.Tab, val label: String, val icon: Int, val selectedIcon: Int)

private val tabs = listOf(
    TabSpec(Route.Attendance, "Attendance", R.drawable.ic_fact_check, R.drawable.ic_fact_check_filled),
    TabSpec(Route.Exams, "Exams", R.drawable.ic_event_note, R.drawable.ic_event_note_filled),
    TabSpec(Route.Grades, "Grades", R.drawable.ic_school, R.drawable.ic_school_filled),
    TabSpec(Route.Me, "Me", R.drawable.ic_person, R.drawable.ic_person_filled),
)

@Composable
private fun BottomBar(nav: Navigator) {
    val haptics = LocalHapticFeedback.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        ShortNavigationBar(Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            tabs.forEach { spec ->
                val selected = nav.tab == spec.tab
                ShortNavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        nav.switchTab(spec.tab)
                    },
                    icon = { Ic(if (selected) spec.selectedIcon else spec.icon, null) },
                    label = { Text(spec.label) },
                )
            }
        }
    }
}
