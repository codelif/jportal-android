package `in`.codelif.jportal.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.TextAutoSize
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.AppClock
import `in`.codelif.jportal.data.Resource
import `in`.codelif.jportal.session.SignInRequired
import `in`.codelif.ktjiit.http.PortalException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Ic(@DrawableRes id: Int, contentDescription: String? = null, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) =
    Icon(painterResource(id), contentDescription, modifier, tint)

/**
 * stand-in for m3 expressive's loading indicator, which stable material3 doesn't ship:
 * a scalloped blob that breathes between 5 and 8 lobes while it spins.
 */
@Composable
fun Loading(modifier: Modifier = Modifier, size: Dp = 48.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val t = rememberInfiniteTransition(label = "loading")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "spin")
    val morph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "morph")
    val path = remember { Path() }
    Canvas(
        modifier.size(size).graphicsLayer { rotationZ = spin }
            .semantics { contentDescription = "Loading"; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val r = this.size.minDimension / 2f * 0.82f
        val lobes = 5f + 3f * morph
        val depth = 0.10f + 0.06f * (1f - morph)
        path.rewind()
        val steps = 120
        for (i in 0..steps) {
            val a = (2 * PI * i / steps).toFloat()
            val rr = r * (1f - depth + depth * cos(lobes * a))
            val x = center.x + rr * cos(a)
            val y = center.y + rr * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

/** a soft pulsing block that holds the exact space of text still on its way */
@Composable
fun Placeholder(width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "placeholder")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "pulse")
    Box(
        modifier.size(width, height)
            .graphicsLayer { alpha = a }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall),
    )
}

@Composable
fun CenteredLoading() = Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) { Loading() }

fun ago(ms: Long?, now: Long = AppClock.millis()): String {
    if (ms == null) return "never"
    val m = (now - ms) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}

fun describe(e: Throwable?): String = when (e) {
    null -> ""
    is SignInRequired -> "Sign in again to refresh"
    is PortalException.Untrusted -> "The portal is having trouble on its end"
    is PortalException.Network -> "You're offline"
    is PortalException.ServerUnavailable -> "The portal is down right now"
    is PortalException.PortalError -> e.errors.firstOrNull()?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "The portal said no"
    is PortalException.SessionExpired -> "Session expired"
    is PortalException.EmptyResponse, is PortalException.Malformed -> "The portal sent something odd"
    else -> "Something went wrong"
}

/** thin strip above content when what you see is cached and the refresh failed */
@Composable
fun StaleNotice(res: Resource<*>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(res.error != null && res.data != null, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(
                    when (res.error) {
                        is PortalException.Network -> R.drawable.ic_wifi_off
                        is PortalException.Untrusted -> R.drawable.ic_warning
                        else -> R.drawable.ic_history
                    },
                    null,
                    Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${describe(res.error)} · updated ${ago(res.fetchedAt)}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

/** one line that shrinks instead of breaking mid-word, for labels in spots that can't grow at big font sizes */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) = Text(
    text, modifier, color = color, style = style, maxLines = 1, softWrap = false,
    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
)

/** the frog's moods, one per kind of empty */
enum class Frog(@DrawableRes val art: Int) {
    Sleep(R.drawable.frog_sleep),
    Plunger(R.drawable.frog_plunger),
    Party(R.drawable.frog_party),
    Look(R.drawable.frog_look),
}

@Composable
fun MessageState(frog: Frog, title: String, body: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(painterResource(frog.art), null, Modifier.size(width = 150.dp, height = 128.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

/** the no-data-yet branches every screen shares; returns true when the caller should draw content */
fun <T> LazyListScope.resourceStates(res: Resource<T>, onRetry: () -> Unit, empty: (T) -> Boolean = { false }, emptyTitle: String = "Nothing here yet"): Boolean {
    val data = res.data
    when {
        data == null && res.error == null && !res.checked -> {}
        data == null && res.error == null -> item("loading") { CenteredLoading() }
        data == null -> item("error") {
            MessageState(
                if (res.error is PortalException.Network) Frog.Sleep else Frog.Plunger,
                describe(res.error), "Pull down or tap retry.", "Retry", onRetry,
            )
        }
        empty(data) -> item("empty") { MessageState(Frog.Sleep, emptyTitle) }
        else -> return true
    }
    return false
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/**
 * every top level screen: large flexible app bar that collapses on scroll,
 * pull to refresh, and a lazy column.
 */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottomPadding: Dp = 0.dp,
    pinned: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // a long title gets a second line when open, the bar grows by that line so nothing is cut
    val big = MaterialTheme.typography.headlineMedium
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val width = LocalWindowInfo.current.containerSize.width - with(density) { 32.dp.roundToPx() }
    val extra = remember(title, big, density, width) {
        val lines = measurer.measure(title, big, maxLines = 2, constraints = Constraints(maxWidth = width.coerceAtLeast(0))).lineCount
        if (lines > 1) with(density) { big.lineHeight.toDp() } else 0.dp
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        // the bar draws this slot twice, big in the open row and small in the collapsed one
                        val expanded = LocalTextStyle.current.fontSize == big.fontSize
                        Column {
                            Text(
                                title,
                                maxLines = if (expanded) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                            if (subtitle != null) {
                                Text(subtitle, maxLines = 1, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Ic(R.drawable.ic_arrow_back, "Back") }
                    },
                    actions = actions,
                    collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
                    expandedHeight = TopAppBarDefaults.LargeAppBarExpandedHeight + extra,
                    scrollBehavior = behavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        // a pinned row below can't follow the bar's scroll tint, so the bar stays flat with it
                        scrolledContainerColor = if (pinned != null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
                pinned?.invoke()
            }
        },
    ) { inner ->
        val list = @Composable {
            // the app bar sits closest to the list so a downward swipe grows the header first,
            // pull to refresh only gets what's left once it's fully open
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
                contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content,
            )
        }
        // pad the box, not the list, so the refresh indicator drops out from under the app bar
        val box = Modifier.fillMaxSize().padding(top = inner.calculateTopPadding())
        if (onRefresh != null) {
            // the big spinner only answers a pull, background refreshes get a quiet bar
            var pulled by remember { mutableStateOf(false) }
            val busy by rememberUpdatedState(refreshing)
            LaunchedEffect(pulled) {
                if (!pulled) return@LaunchedEffect
                withTimeoutOrNull(1500) { snapshotFlow { busy }.first { it } }
                snapshotFlow { busy }.first { !it }
                pulled = false
            }
            val haptics = LocalHapticFeedback.current
            PullToRefreshBox(
                isRefreshing = pulled && refreshing,
                onRefresh = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate); pulled = true; onRefresh() },
                modifier = box,
            ) {
                list()
                if (refreshing && !pulled) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            }
        } else Box(box) { list() }
    }
}
