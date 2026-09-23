package `in`.codelif.jportal.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.codelif.jportal.R

/**
 * one bundled variable font (google sans flex, subset to latin, weight and
 * roundness axes kept). rounder at display sizes, flat and crisp for body text.
 */
private fun flex(weight: Int, round: Float) = Font(
    R.font.sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("ROND", round)),
)

private fun family(round: Float) = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { flex(it, round) },
)

val FlexRounded = family(100f)
val FlexPlain = family(0f)

private val base = Typography()

private fun TextStyle.with(family: FontFamily, weight: Int, tracking: Float? = null) =
    copy(fontFamily = family, fontWeight = FontWeight(weight), letterSpacing = tracking?.sp ?: letterSpacing)

val JPortalTypography = Typography(
    displayLarge = base.displayLarge.with(FlexRounded, 600, -1.5f),
    displayMedium = base.displayMedium.with(FlexRounded, 600, -1f),
    displaySmall = base.displaySmall.with(FlexRounded, 600, -0.5f),
    headlineLarge = base.headlineLarge.with(FlexRounded, 600, -0.5f),
    headlineMedium = base.headlineMedium.with(FlexRounded, 600, -0.25f),
    headlineSmall = base.headlineSmall.with(FlexRounded, 600),
    titleLarge = base.titleLarge.with(FlexRounded, 600),
    titleMedium = base.titleMedium.with(FlexPlain, 600),
    titleSmall = base.titleSmall.with(FlexPlain, 600),
    bodyLarge = base.bodyLarge.with(FlexPlain, 400),
    bodyMedium = base.bodyMedium.with(FlexPlain, 400),
    bodySmall = base.bodySmall.with(FlexPlain, 400),
    labelLarge = base.labelLarge.with(FlexPlain, 600),
    labelMedium = base.labelMedium.with(FlexPlain, 600),
    labelSmall = base.labelSmall.with(FlexPlain, 600),
)

/** big friendly numbers: attendance percentages, gpa, countdowns */
val NumberStyle = TextStyle(fontFamily = FlexRounded, fontWeight = FontWeight(700), fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.5).sp)

val JPortalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
