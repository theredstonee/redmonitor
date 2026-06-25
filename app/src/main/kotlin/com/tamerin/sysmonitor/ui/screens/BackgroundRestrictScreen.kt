package com.tamerin.sysmonitor.ui.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.ui.components.ShizukuCard
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppBg(val pkg: String, val label: String, val isSystem: Boolean, val restricted: Boolean)

@Composable
fun BackgroundRestrictScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppBg>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(shizukuReady, refresh) {
        if (!shizukuReady) return@LaunchedEffect
        apps = withContext(Dispatchers.IO) { loadApps(context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        if (!shizukuReady) {
            ShizukuCard(
                title = "Shizuku für Background-Restrictions",
                description = "`cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` — " +
                    "killt Background-Wakeups gezielt pro App ohne durch lange System-Menüs zu klicken."
            )
            return@Column
        }

        StatCard("Background-Restrictions") {
            Text(
                "Roter Status = App darf nichts mehr im Hintergrund (kein Wakeup, kein Job). " +
                    "Grüner Status = Stock-Verhalten. Tap auf eine Reihe togglet.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = filter, onValueChange = { filter = it },
            label = { Text("Filter") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        FilterChip(selected = showSystem, onClick = { showSystem = !showSystem },
            label = { Text("System-Apps", fontSize = 11.sp) })
        Spacer(Modifier.height(8.dp))

        val filtered = remember(apps, filter, showSystem) {
            apps.filter { (showSystem || !it.isSystem) &&
                (filter.isBlank() || it.label.contains(filter, true) || it.pkg.contains(filter, true)) }
        }
        Text("${filtered.count { it.restricted }} restricted von ${filtered.size} sichtbar",
            color = OnSurfaceMuted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(filtered, key = { it.pkg }) { app ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(Color(0x14FFFFFF)).clickable {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ShizukuHelper.runCommand(context,
                                    "cmd", "appops", "set", app.pkg, "RUN_ANY_IN_BACKGROUND",
                                    if (app.restricted) "allow" else "ignore")
                            }
                            refresh += 1
                        }
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, color = AccentSoft, fontSize = 12.sp,
                            fontWeight = FontWeight.Medium)
                        Text(app.pkg, color = OnSurfaceMuted, fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace)
                    }
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (app.restricted) GaugeRed.copy(alpha = 0.25f)
                                    else GaugeGreen.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(if (app.restricted) "BLOCKED" else "allowed",
                            color = if (app.restricted) GaugeRed else GaugeGreen,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

private fun loadApps(context: android.content.Context): List<AppBg> {
    val pm = context.packageManager
    // Statt für jede App appops zu queryen (langsam), eine Batch-Abfrage:
    val opsOut = ShizukuHelper.runShell(context,
        "cmd appops get -1 RUN_ANY_IN_BACKGROUND 2>/dev/null").stdout
    val restrictedSet = mutableSetOf<String>()
    Regex("""Uid (\d+):.*?RUN_ANY_IN_BACKGROUND: ignore""", RegexOption.DOT_MATCHES_ALL)
        .findAll(opsOut).forEach { m ->
            val uid = m.groupValues[1].toIntOrNull() ?: return@forEach
            pm.getPackagesForUid(uid)?.forEach { restrictedSet += it }
        }
    return pm.getInstalledApplications(0).map { ai ->
        AppBg(
            pkg = ai.packageName,
            label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName),
            isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            restricted = ai.packageName in restrictedSet
        )
    }.sortedBy { it.label.lowercase() }
}
