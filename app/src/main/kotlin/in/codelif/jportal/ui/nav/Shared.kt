package `in`.codelif.jportal.ui.nav

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

private val springy = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow) }

/** marks an element that flies between screens, a no-op outside the nav host */
@Composable
fun Modifier.shared(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) {
        this@shared.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = nav, boundsTransform = springy)
    }
}

/** like [shared] but for containers whose content changes shape, e.g. a card becoming a page header */
@Composable
fun Modifier.sharedBounds(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) {
        this@sharedBounds.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope = nav, boundsTransform = springy)
    }
}
