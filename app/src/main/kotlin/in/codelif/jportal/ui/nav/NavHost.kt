package `in`.codelif.jportal.ui.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * the whole navigation: a back stack, one seekable transition so predictive
 * back scrubs the real animation, and a shared transition layout for the
 * ring that flies from the list into the subject page.
 */
@Composable
fun NavHost(nav: Navigator, modifier: Modifier = Modifier, content: @Composable (Route) -> Unit) {
    val seek = remember { SeekableTransitionState<Route>(nav.current) }
    val transition = rememberTransition(seek, label = "nav")
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    var gesture by remember { mutableStateOf(false) }
    // last seen depth per route, popped routes are gone from the stack by the time we animate
    val depth = remember { HashMap<String, Int>() }

    LaunchedEffect(nav) {
        snapshotFlow { nav.stack.toList() }.collect { stack ->
            stack.forEachIndexed { i, r -> depth[r.key] = if (r is Route.Tab) 0 else i }
            if (!gesture && seek.currentState != stack.last()) seek.animateTo(stack.last())
            // forget scroll state of screens that fell off the stack, tabs keep theirs
            (depth.keys - stack.map { it.key }.toSet()).filter { k -> Route.tabs.none { it.key == k } }.forEach {
                holder.removeState(it)
                depth.remove(it)
            }
        }
    }

    PredictiveBackHandler(enabled = nav.canPop) { events ->
        val prev = nav.previous ?: return@PredictiveBackHandler
        gesture = true
        try {
            events.collect { e -> seek.seekTo(e.progress * 0.95f, prev) }
            nav.pop()
            seek.animateTo(prev)
        } catch (c: CancellationException) {
            scope.launch { seek.animateTo(nav.current) }
            throw c
        } finally {
            gesture = false
        }
    }

    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedScope provides this) {
            transition.AnimatedContent(
                contentKey = { it.key },
                transitionSpec = { spec(depth[initialState.key] ?: 0, depth[targetState.key] ?: 0) },
            ) { route ->
                CompositionLocalProvider(LocalNavScope provides this) {
                    holder.SaveableStateProvider(route.key) { content(route) }
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<Route>.spec(from: Int, to: Int): ContentTransform = when {
    initialState is Route.Tab && targetState is Route.Tab ->
        // fade through between tabs
        (fadeIn(tween(210, 90, LinearOutSlowInEasing)) + scaleIn(tween(210, 90, LinearOutSlowInEasing), initialScale = 0.96f))
            .togetherWith(fadeOut(tween(90)))
    to > from ->
        // forward: shared axis z
        (fadeIn(tween(240, 60, LinearOutSlowInEasing)) + scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = 0.9f))
            .togetherWith(fadeOut(tween(120)) + scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 1.06f))
    else ->
        // back: the leaving page shrinks and drifts toward the edge, like the system back preview
        (fadeIn(tween(240, 40)) + scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = 1.06f))
            .togetherWith(
                fadeOut(tween(220)) + scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.88f) +
                    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 8 },
            )
}.apply { targetContentZIndex = to.toFloat() }
