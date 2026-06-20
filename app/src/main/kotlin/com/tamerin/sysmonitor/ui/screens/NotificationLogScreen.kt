package com.tamerin.sysmonitor.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.NotificationEntry
import com.tamerin.sysmonitor.data.NotificationLog
import com.tamerin.sysmonitor.data.isNotificationListenerEnabled
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeOrange
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationLogScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    val entries by NotificationLog.entries.collectAsState()
    var filterText by remember { mutableStateOf("") }
    var showRemoved by remember { mutableStateOf(false) }
    var showOngoing by remember { mutableStateOf(true) }

    // Permission-Status alle 2s neu prüfen, damit Rückkehr aus Settings reagiert
    LaunchedEffect(Unit) {
        while (true) {
            enabled = isNotificationListenerEnabled(context)
            delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!enabled) {
            StatCard("Notification-Zugriff nötig") {
                Text(
                    "Um alle eingehenden Benachrichtigungen zu loggen, braucht RedMonitor " +
                        "den 'Notification-Access' (BIND_NOTIFICATION_LISTENER_SERVICE). " +
                        "Android verweigert das ohne explizite User-Erteilung.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Notification-Settings öffnen") }
            }
            Spacer(Modifier.height(8.dp))
            StatCard("Privacy") {
                Text(
                    "Notifications werden nur im RAM gehalten (max ${NotificationLog.MAX_ENTRIES}). " +
                        "Nichts wird auf Disk gespeichert oder verschickt — beim App-Beenden weg.",
                    color = OnSurfaceMuted, fontSize = 11.sp
                )
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { NotificationLog.clear() },
                modifier = Modifier.weight(1f)
            ) { Text("Log leeren", fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Settings", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            label = { Text("Filter (Package, Titel, Text)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = showRemoved,
                onClick = { showRemoved = !showRemoved },
                label = { Text("Removed-Events", fontSize = 11.sp) }
            )
            FilterChip(
                selected = showOngoing,
                onClick = { showOngoing = !showOngoing },
                label = { Text("Ongoing (FGS)", fontSize = 11.sp) }
            )
        }
        Spacer(Modifier.height(6.dp))

        val filtered = remember(entries, filterText, showRemoved, showOngoing) {
            entries.filter { e ->
                if (!showRemoved && e.event == NotificationEntry.Event.REMOVED) return@filter false
                if (!showOngoing && e.isOngoing) return@filter false
                if (filterText.isNotBlank()) {
                    val q = filterText.lowercase()
                    if (!e.packageName.lowercase().contains(q) &&
                        !e.displayName.lowercase().contains(q) &&
                        !e.title.lowercase().contains(q) &&
                        !e.text.lowercase().contains(q)
                    ) return@filter false
                }
                true
            }
        }

        Text(
            "${filtered.size} sichtbar / ${entries.size} im Log",
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))

        if (filtered.isEmpty()) {
            StatCard("Noch nichts eingegangen") {
                Text(
                    if (entries.isEmpty()) "Sobald eine Notification reinkommt, erscheint sie hier."
                    else "Filter passt auf keinen Eintrag.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.key + "_" + it.postedAt + "_" + it.event.name }) { e ->
                NotificationRow(e)
            }
        }
    }
}

private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun NotificationRow(e: NotificationEntry) {
    val eventColor = when (e.event) {
        NotificationEntry.Event.POSTED -> Accent
        NotificationEntry.Event.REMOVED -> GaugeOrange
    }
    StatCard(e.displayName) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentBubble)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    e.event.name + if (e.isOngoing) " · FGS" else "",
                    color = eventColor, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                TIME_FMT.format(Date(e.postedAt)),
                color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(e.packageName, color = OnSurfaceMuted, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace)
        if (e.title.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                e.title, color = AccentSoft, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (e.text.isNotBlank()) {
            Text(e.text, color = OnSurfaceMuted, fontSize = 12.sp, maxLines = 4)
        }
        if (e.subText.isNotBlank()) {
            Text(e.subText, color = OnSurfaceMuted, fontSize = 10.sp)
        }
        if (!e.channelId.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("Channel: ${e.channelId}", color = OnSurfaceMuted,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
