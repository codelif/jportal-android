package `in`.codelif.jportal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class GroupScope {
    internal val rows = mutableListOf<Pair<Any?, @Composable () -> Unit>>()

    /** [key] only matters in a lazy [group], where it keeps a row's state and animation when rows around it come and go */
    fun row(key: Any? = null, content: @Composable () -> Unit) {
        rows += key to content
    }
}

private val shapes = List(4) { i ->
    val top = if (i and 1 != 0) 24.dp else 6.dp
    val bottom = if (i and 2 != 0) 24.dp else 6.dp
    RoundedCornerShape(top, top, bottom, bottom)
}

private fun shape(first: Boolean, last: Boolean) = shapes[(if (first) 1 else 0) or (if (last) 2 else 0)]

/**
 * m3 expressive grouped list, the android 16 settings look: rows sit a hair
 * apart, the group rounds its outer corners and barely rounds the inner ones.
 */
@Composable
fun Group(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLow, build: GroupScope.() -> Unit) {
    val rows = GroupScope().apply(build).rows
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEachIndexed { i, (_, row) ->
            Surface(shape = shape(i == 0, i == rows.lastIndex), color = color, modifier = Modifier.fillMaxWidth()) { row() }
        }
    }
}

/** [Group] for lazy lists, one item per row, so a long group builds as it scrolls in instead of all in one frame */
fun LazyListScope.group(
    key: String,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    contentType: Any = "group-row",
    animate: Boolean = false,
    build: GroupScope.() -> Unit,
) {
    val rows = GroupScope().apply(build).rows
    rows.forEachIndexed { i, (k, row) ->
        val first = i == 0
        val last = i == rows.lastIndex
        item("$key/${k ?: i}", contentType) {
            Surface(
                shape = shape(first, last),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = (if (animate) Modifier.animateItem() else Modifier).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = if (first) top else 3.dp, bottom = if (last) bottom else 0.dp),
            ) { row() }
        }
    }
}
