package `in`.codelif.jportal.feature.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import `in`.codelif.jportal.ui.components.AttendanceRing
import `in`.codelif.jportal.ui.theme.NumberStyle

/** attendance goal picker: slider with a tick of haptics per percent */
@Composable
fun TargetSheet(current: Int, onDone: (Int) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    var value by remember { mutableIntStateOf(current) }
    ModalBottomSheet(onDismissRequest = { onDone(value) }, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Attendance goal", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Rings go flat and warm up when a subject falls under this. JIIT's rule is 75%, some profs want more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AttendanceRing(value.toFloat(), 75, size = 120.dp, stroke = 12.dp) {
                Text("$value%", style = NumberStyle.copy(fontSize = NumberStyle.fontSize * 0.75f))
            }
            Slider(
                value = value.toFloat(),
                onValueChange = {
                    val v = it.toInt()
                    if (v != value) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    value = v
                },
                valueRange = 50f..100f,
                steps = 49,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { onDone(value) }) { Text("Done") }
            }
        }
    }
}
