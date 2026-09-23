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
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest

val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * the whole navigation: the four tabs side by side in one pager so a swipe
 * moves between them, a back stack of pages on top with one seekable
 * transition so predictive back scrubs the real animation, and a shared
 * transition layout for the ring that flies from the list into the subject page.
 */
@Composable
fun NavHost(nav: Navigator, pager: PagerState, modifier: Modifier = Modifier, content: @Composable (Route) -> Unit) {
    val seek = remember { SeekableTransitionState<Route>(nav.current) }
    val transition = rememberTransition(seek, label = "nav")
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    var gesture by remember { mutableStateOf(false) }
    // how far a back swipe from a non-home tab has gone, the tabs shrink with it like the system preview
    val tabBack = remember { Animatable(0f) }
    // last seen depth per route, popped routes are gone from the stack by the time we animate
    val depth = remember { HashMap<String, Int>() }

    LaunchedEffect(nav) {
        snapshotFlow { nav.stack.toList() to gesture }.collectLatest { (stack, swiping) ->
            stack.forEachIndexed { i, r -> depth[r.key] = if (r is Route.Tab) 0 else i }
            val last = stack.last()
            if (!swiping && (seek.currentState != last || seek.targetState != last)) {
                // a back swipe interrupting this animation cancels it, that must not end the loop or taps stop navigating
                try {
                    seek.animateTo(last)
                } catch (e: CancellationException) {
                    // not the bare ensureActive(): that's the LaunchedEffect's scope, alive even when collectLatest dropped us
                    currentCoroutineContext().ensureActive()
                }
            }
            // forget scroll state of screens that fell off the stack, tabs keep theirs
            (depth.keys - stack.map { it.key }.toSet()).filter { k -> Route.tabs.none { it.key == k } }.forEach {
                holder.removeState(it)
                depth.remove(it)
            }
        }
    }

    // only a finger on the pager may pick a tab by settling, a slide we started stopping halfway must not
    val swiped = remember { mutableStateOf(false) }
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { if (it is DragInteraction.Start) swiped.value = true }
    }
    // the bar or back picked a tab, bring the pager there
    LaunchedEffect(nav, pager) {
        snapshotFlow { Route.tabs.indexOf(nav.tab) }.collectLatest { i ->
            swiped.value = false
            // a touch anywhere on the pager stops the slide, keep going until we're there or a real swipe takes over
            while (pager.currentPage != i || pager.currentPageOffsetFraction != 0f) {
                try {
                    pager.animateScrollToPage(i)
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    if (swiped.value) break
                    // whatever preempted us gets a frame before we take the pager back, never spin
                    withFrameNanos {}
                }
            }
        }
    }
    // a swipe landed on a tab, tell the back stack
    LaunchedEffect(nav, pager) {
        snapshotFlow { pager.settledPage }.collect { p ->
            if (swiped.value && !pager.isScrollInProgress) {
                swiped.value = false
                if (nav.stack.size == 1) nav.switchTab(Route.tabs[p])
            }
        }
    }

    PredictiveBackHandler(enabled = nav.canPop) { events ->
        val prev = nav.previous ?: return@PredictiveBackHandler
        if (nav.stack.size == 1) {
            // tab root back to home: preview by shrinking, then let the pager carry it home
            try {
                events.collect { e -> tabBack.snapTo(e.progress) }
                nav.pop()
            } finally {
                scope.launch { tabBack.animateTo(0f) }
            }
            return@PredictiveBackHandler
        }
        gesture = true
        try {
            events.collect { e -> seek.seekTo(e.progress * 0.95f, prev) }
            nav.pop()
            seek.animateTo(prev)
        } finally {
            // a cancelled swipe is settled back by the loop above once this flips
            gesture = false
        }
    }

    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedScope provides this) {
            transition.AnimatedContent(
                // every tab is the same page here, the pager inside tells them apart
                contentKey = { if (it is Route.Tab) TABS else it.key },
                transitionSpec = { spec(depth[initialState.key] ?: 0, depth[targetState.key] ?: 0) },
            ) { route ->
                CompositionLocalProvider(LocalNavScope provides this) {
                    if (route is Route.Tab) {
                        holder.SaveableStateProvider(TABS) {
                            HorizontalPager(
                                pager,
                                beyondViewportPageCount = 1,
                                key = { Route.tabs[it].key },
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    val p = tabBack.value
                                    scaleX = 1f - 0.08f * p
                                    scaleY = 1f - 0.08f * p
                                    shape = RoundedCornerShape(32.dp * p)
                                    clip = p > 0f
                                },
                            ) { page -> content(Route.tabs[page]) }
                        }
                    } else {
                        holder.SaveableStateProvider(route.key) { content(route) }
                    }
                }
            }
        }
    }
}

private const val TABS = "tabs"

private fun AnimatedContentTransitionScope<Route>.spec(from: Int, to: Int): ContentTransform = when {
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
