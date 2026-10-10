package com.tamerin.sysmonitor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private data class Step(val titleRes: Int, val bodyRes: Int, val ctaRes: Int)

private val STEPS = listOf(
    Step(R.string.comp_onb_step1_title, R.string.comp_onb_step1_body, R.string.comp_onb_step1_cta),
    Step(R.string.comp_onb_step2_title, R.string.comp_onb_step2_body, R.string.comp_onb_step2_cta),
    Step(R.string.comp_onb_step3_title, R.string.comp_onb_step3_body, R.string.comp_onb_step3_cta)
)

@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val current = STEPS[step]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentBubble)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${step + 1}/${STEPS.size}", color = AccentSoft, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(current.titleRes), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Text(stringResource(current.bodyRes), color = OnSurfaceMuted, fontSize = 13.sp, lineHeight = 18.sp)
        },
        confirmButton = {
            Button(onClick = {
                if (step < STEPS.lastIndex) step += 1 else onDismiss()
            }) { Text(stringResource(current.ctaRes)) }
        },
        dismissButton = {
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }) { Text(stringResource(R.string.comp_onb_back)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.comp_onb_skip)) }
            }
        }
    )
}
