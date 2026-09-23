package `in`.codelif.jportal.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import `in`.codelif.jportal.LocalGraph
import `in`.codelif.jportal.R
import `in`.codelif.jportal.data.AppIcon

/** whichever launcher icon is picked in settings, drawn the way a launcher shows it: the 72dp safe circle of the 108dp art */
@Composable
fun AppMark(size: Dp, modifier: Modifier = Modifier) {
    val icon by LocalGraph.current.prefs.iconState.collectAsState()
    val (bg, fg) = when (icon) {
        AppIcon.Classic -> R.color.launcher_classic_background to R.drawable.ic_launcher_classic_foreground
        AppIcon.Frog -> R.color.launcher_background to R.drawable.ic_launcher_foreground
    }
    Box(
        modifier.size(size).clip(CircleShape).background(colorResource(bg)).semantics { contentDescription = "JPortal" },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(fg), null, Modifier.requiredSize(size * 1.5f))
    }
}

/** jportal signed off with "made with big 🍆 energy by yash malik", so do we */
@Composable
fun BigEnergy(modifier: Modifier = Modifier) {
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    Text(
        buildAnnotatedString {
            append("Big 🍆 Energy lives on in ")
            withLink(LinkAnnotation.Url("https://github.com/codeblech", link)) { append("Yash Malik") }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
