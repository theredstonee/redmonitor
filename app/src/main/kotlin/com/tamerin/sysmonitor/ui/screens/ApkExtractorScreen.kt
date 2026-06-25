package com.tamerin.sysmonitor.ui.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExtractApp(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val versionName: String
)

@Composable
fun ApkExtractorScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<ExtractApp>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var extracting by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(0).mapNotNull { ai ->
                val name = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName)
                val sys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val ver = runCatching { pm.getPackageInfo(ai.packageName, 0).versionName }
                    .getOrNull().orEmpty()
                ExtractApp(ai.packageName, name, sys, ver)
            }.sortedBy { it.label.lowercase() }
        }
    }

    fun extract(app: ExtractApp) {
        extracting = app.pkg
        scope.launch {
            val ok = withContext(Dispatchers.IO) { extractApk(context, app) }
            lastResult = app.pkg to ok
            extracting = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für APK-Extract",
                description = "`pm path <pkg>` listet die APK-Dateien (Split-APKs inkl.). " +
                    "Das Kopieren aus /data/app/.../ braucht shell-Rechte — Shizuku liefert die."
            )
            return@Column
        }

        StatCard("APK-Extractor") {
            Text(
                "Extrahiert die installierte APK (Base + alle Splits) nach " +
                    "/sdcard/Download/redmonitor-apks/<pkg>/. Dort kannst du sie z.B. mit einem Datei-Manager teilen.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            lastResult?.let { (pkg, ok) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "${if (ok) "✓ Extrahiert" else "✗ Fehler"}: $pkg",
                    color = if (ok) GaugeGreen else GaugeRed,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = filter, onValueChange = { filter = it },
            label = { Text("Filter") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        FilterChip(
            selected = showSystem,
            onClick = { showSystem = !showSystem },
            label = { Text("System-Apps", fontSize = 11.sp) }
        )
        Spacer(Modifier.height(8.dp))

        val filtered = remember(apps, filter, showSystem) {
            apps.filter { (showSystem || !it.isSystem) &&
                (filter.isBlank() || it.label.contains(filter, true) || it.pkg.contains(filter, true)) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.pkg }) { app ->
                AppRow(app, extracting == app.pkg) { extract(app) }
            }
        }
    }
}

@Composable
private fun AppRow(app: ExtractApp, busy: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(androidx.compose.ui.graphics.Color(0x14FFFFFF))
            .clickable(enabled = !busy, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(app.label, color = AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("${app.pkg} · v${app.versionName}", color = OnSurfaceMuted,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        if (app.isSystem) {
            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(AccentBubble).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text("SYS", color = OnSurfaceMuted, fontSize = 9.sp)
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (busy) "..." else "→",
            color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
    }
}

private fun extractApk(context: android.content.Context, app: ExtractApp): Boolean {
    val paths = ShizukuHelper.runShell(context, "pm path ${app.pkg}")
    if (!paths.ok || paths.stdout.isBlank()) return false
    val out = "/sdcard/Download/redmonitor-apks/${app.pkg}"
    ShizukuHelper.runShell(context, "mkdir -p '$out'")
    var allOk = true
    for (line in paths.stdout.lines()) {
        val p = line.removePrefix("package:").trim()
        if (p.isEmpty()) continue
        val res = ShizukuHelper.runShell(context, "cp '$p' '$out/'")
        if (!res.ok) allOk = false
    }
    return allOk
}
