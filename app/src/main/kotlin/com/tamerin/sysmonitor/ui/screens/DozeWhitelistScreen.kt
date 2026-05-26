package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.ShizukuHelper
import com.tamerin.sysmonitor.data.SystemTweaks
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.GaugeGreen
import com.tamerin.sysmonitor.ui.theme.GaugeRed
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DozeWhitelistScreen() {
    val context = LocalContext.current
    val shizukuReady = ShizukuHelper.state(context) == ShizukuHelper.State.Ready
    val pm = context.packageManager
    var list by remember { mutableStateOf<List<SystemTweaks.WhitelistEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var lastAction by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        list = withContext(Dispatchers.IO) { SystemTweaks.listDozeWhitelist(context) }
        loading = false
    }

    LaunchedEffect(shizukuReady) {
        if (shizukuReady) refresh()
    }

    fun act(label: String, block: suspend () -> ShizukuHelper.CmdResult) {
        scope.launch {
            val res = withContext(Dispatchers.IO) { block() }
            lastAction = if (res.ok) "✓ $label" else "✗ $label: ${res.stderr.ifBlank { "Exit ${res.exitCode}" }}"
            refresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (!shizukuReady) {
            StatCard("Shizuku benötigt") {
                Text(
                    "Doze-Whitelist (Akku-Optimierung) lesen/schreiben braucht shell-Zugriff via Shizuku.",
                    color = OnSurfaceMuted, fontSize = 12.sp
                )
            }
            return@Column
        }

        StatCard("Doze-Whitelist") {
            Text(
                "Apps auf der Whitelist dürfen während Doze (Tiefschlaf) Netzwerk nutzen und Wakelocks halten. " +
                    "Push-Notifications + Sync. Je weniger Apps drauf, desto bessere Akku-Laufzeit.",
                color = OnSurfaceMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            KeyValueRow("Anzahl Apps", list.size.toString())
            KeyValueRow("Davon System-Apps", list.count { !it.isUser }.toString())
            lastAction?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = if (it.startsWith("✓")) GaugeGreen else GaugeRed, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Filter") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        val filtered = remember(list, query) {
            if (query.isBlank()) list
            else list.filter { it.pkg.contains(query, true) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.pkg }) { entry ->
                val ai = runCatching { pm.getApplicationInfo(entry.pkg, 0) }.getOrNull()
                val label = ai?.loadLabel(pm)?.toString() ?: entry.pkg
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Row {
                            Text(
                                entry.pkg,
                                color = OnSurfaceMuted, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                if (entry.isUser) "USER" else "SYSTEM",
                                color = AccentSoft, fontSize = 10.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { act("Entfernt: ${entry.pkg}") { SystemTweaks.removeFromWhitelist(context, entry.pkg) } },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("Entfernen", fontSize = 11.sp) }
                }
            }
        }
    }
}
