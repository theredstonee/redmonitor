package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ProcessReader
import com.tamerin.sysmonitor.data.RunningApp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.DividerWhite
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import com.tamerin.sysmonitor.ui.theme.SurfaceDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun TaskManagerScreen(onSelect: (String) -> Unit = {}) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<RunningApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready

    LaunchedEffect(Unit) {
        while (true) {
            apps = withContext(Dispatchers.IO) { ProcessReader.read(context) }
            loading = false
            delay(3000)
        }
    }

    val filtered = remember(apps, showSystem, query) {
        apps.filter { (showSystem || !it.isSystem) &&
            (query.isBlank() || it.displayName.contains(query, true) || it.pkg.contains(query, true)) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (!shizukuReady) {
            StatCard("Shizuku empfohlen") {
                Text(
                    "Ohne Shizuku zeigt Android nur den eigenen Prozess dieser App. Aktiviere Shizuku unter System → Akku oder System → Floating HUD → echte PD-Watt freischalten — danach siehst du alle laufenden Apps mit RAM, kannst sie stoppen und disablen.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("App suchen") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                if (loading) "Lade…" else "${filtered.size} aktive Apps",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = !showSystem },
                label = { Text("System-Apps") }
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.pid.toString() + it.pkg }) { app ->
                AppRow(app, onClick = { onSelect(app.pkg) })
            }
        }
    }
}

@Composable
private fun AppRow(app: RunningApp, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, DividerWhite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBubble),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    app.displayName.firstOrNull()?.uppercase() ?: "?",
                    color = AccentSoft,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "${app.pkg}  ·  PID ${app.pid}  ·  ${app.importanceLabel}",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                formatMem(app.rssKb),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatMem(kb: Long): String = when {
    kb <= 0 -> "—"
    kb < 1024 -> "${kb} KB"
    kb < 1024L * 1024 -> "${"%.1f".format(kb / 1024f)} MB"
    else -> "${"%.2f".format(kb / 1024f / 1024f)} GB"
}
