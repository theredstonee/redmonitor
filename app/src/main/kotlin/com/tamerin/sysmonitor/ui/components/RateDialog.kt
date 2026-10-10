package com.tamerin.sysmonitor.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.settings.AppPrefs
import com.tamerin.sysmonitor.settings.Haptic
import com.tamerin.sysmonitor.settings.HapticType
import com.tamerin.sysmonitor.ui.theme.Accent

private const val TRUSTPILOT_EVALUATE = "https://de.trustpilot.com/evaluate/theredstonee.de"

@Composable
fun RateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {
            // Tap outside = same as "Später" (snooze 3 days)
            AppPrefs.markRatePromptShown(context)
            onDismiss()
        },
        icon = {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Accent, modifier = Modifier.size(40.dp))
        },
        title = {
            Text(
                stringResource(R.string.comp_rate_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                stringResource(R.string.comp_rate_body),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Haptic.perform(context, HapticType.CONFIRM)
                AppPrefs.setHasRated(context, true)
                AppPrefs.markRatePromptShown(context)
                openTrustpilot(context)
                onDismiss()
            }) {
                Text(stringResource(R.string.comp_rate_confirm), color = Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                Haptic.perform(context, HapticType.TAP)
                AppPrefs.markRatePromptShown(context)
                onDismiss()
            }) {
                Text(stringResource(R.string.comp_rate_later))
            }
        }
    )
}

private fun openTrustpilot(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TRUSTPILOT_EVALUATE))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
