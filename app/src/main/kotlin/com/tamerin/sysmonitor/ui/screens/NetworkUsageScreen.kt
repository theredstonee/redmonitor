package com.tamerin.sysmonitor.ui.screens

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tamerin.sysmonitor.data.AppNetworkUsage
import com.tamerin.sysmonitor.data.NetworkUsageReader
import com.tamerin.sysmonitor.data.formatBytes
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private enum class TimeRange(val ms: Long, val label: String) {
    H1(3600_000L, "1 h"),
    H24(24L * 3600_000L, "24 h"),
    D7(7L * 24L * 3600_000L, "7 Tg"),
    D30(30L * 24L * 3600_000L, "30 Tg")
}

@Composable
fun NetworkUsageScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var usage by remember { mutableStateOf<List<AppNetworkUsage>>(emptyList()) }
    var range by remember { mutableStateOf(TimeRange.H24) }
    var showSystem by remember { mutableStateOf(false) }
    var hasAccess by remember { mutableStateOf(checkUsageAccess(context)) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner, range, hasAccess) {
        if (!hasAccess) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                loading = true
                usage = NetworkUsageReader.read(context, sinceMs = range.ms)
                loading = false
                hasAccess = checkUsageAccess(context)
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (!hasAccess) {
            StatCard("Nutzungsstatistiken-Permission benötigt") {
                Text(
                    "Pro-App-Netzwerk-Traffic braucht 'Nutzungszugriff' (PACKAGE_USAGE_STATS). " +
                        "Android: Einstellungen → Apps → Spezieller App-Zugriff → Nutzungsdaten → RedMonitor erlauben.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Einstellungen öffnen") }
            }
            return
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TimeRange.values().forEach { r ->
                FilterChip(
                    selected = range == r,
                    onClick = { range = r },
                    label = { Text(r.label, fontSize = 11.sp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = !showSystem },
                label = { Text("System-Apps", fontSize = 11.sp) }
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (loading) "lädt…" else "${usage.size} Apps",
                color = OnSurfaceMuted, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(8.dp))

        val filtered = remember(usage, showSystem) {
            if (showSystem) usage else usage.filterNot { it.isSystem }
        }
        if (filtered.isEmpty() && !loading) {
            StatCard("Keine Daten") {
                Text(
                    "Im gewählten Zeitraum hat keine ${if (showSystem) "" else "User-"}App Netzwerk-Traffic erzeugt.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            return
        }

        val totalBytes = remember(filtered) { filtered.sumOf { it.totalBytes } }
        val maxBytes = (filtered.firstOrNull()?.totalBytes ?: 1L).coerceAtLeast(1L)

        StatCard("Gesamt ${range.label}") {
            KeyValueRow("Gesamt-Volumen", totalBytes.formatBytes())
            KeyValueRow("Mobil", filtered.sumOf { it.mobileBytes }.formatBytes())
            KeyValueRow("WLAN", filtered.sumOf { it.wifiBytes }.formatBytes())
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.uid.toString() + it.packageName }) { app ->
                AppRow(app, maxBytes)
            }
        }
    }
}

@Composable
private fun AppRow(app: AppNetworkUsage, maxBytes: Long) {
    StatCard(app.displayName + if (app.isSystem) "  · System" else "") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(app.packageName, color = OnSurfaceMuted,
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(2.dp))
                Text(
                    "↓↑ ${app.totalBytes.formatBytes()}",
                    color = Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "WLAN ${app.wifiBytes.formatBytes()} · Mobil ${app.mobileBytes.formatBytes()}",
                    color = OnSurfaceMuted, fontSize = 10.sp
                )
            }
            Text("UID ${app.uid}", color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(androidx.compose.ui.graphics.Color(0x22FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(app.totalBytes.toFloat() / maxBytes)
                    .fillMaxHeight()
                    .background(if (app.isSystem) OnSurfaceMuted else Accent)
            )
        }
    }
}

private fun checkUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
