package `in`.codelif.jportal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class GroupScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * m3 expressive grouped list, the android 16 settings look: rows sit a hair
 * apart, the group rounds its outer corners and barely rounds the inner ones.
 */
@Composable
fun Group(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerLow, build: GroupScope.() -> Unit) {
    val rows = GroupScope().apply(build).rows
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEachIndexed { i, row ->
            val top = if (i == 0) 24.dp else 6.dp
            val bottom = if (i == rows.lastIndex) 24.dp else 6.dp
            Surface(shape = RoundedCornerShape(top, top, bottom, bottom), color = color, modifier = Modifier.fillMaxWidth()) { row() }
        }
    }
}
