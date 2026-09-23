package `in`.codelif.jportal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.codelif.jportal.ui.theme.LocalExtraColors

/** jportal's grade column: the letter in its colour, sitting on a faint wash of the same colour */
@Composable
fun GradeLetter(grade: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val color = LocalExtraColors.current.grade(grade)
    Surface(
        shape = RoundedCornerShape(size * 0.3f),
        color = color.copy(alpha = 0.14f),
        modifier = modifier.size(size).semantics { contentDescription = "Grade ${grade.ifBlank { "not out" }}" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                grade.ifBlank { "–" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp),
                color = color,
            )
        }
    }
}
