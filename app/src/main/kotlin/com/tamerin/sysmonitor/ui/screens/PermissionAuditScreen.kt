package com.tamerin.sysmonitor.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.AppPermissions
import com.tamerin.sysmonitor.data.CategoryGroup
import com.tamerin.sysmonitor.data.PermCategory
import com.tamerin.sysmonitor.data.PermissionAuditReader
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.AccentBubble
import com.tamerin.sysmonitor.ui.theme.AccentSoft
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private enum class ViewMode { ByCategory, ByApp }

@Composable
fun PermissionAuditScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppPermissions>>(emptyList()) }
    var mode by remember { mutableStateOf(ViewMode.ByCategory) }
    var showSystem by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        apps = PermissionAuditReader.read(context)
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = mode == ViewMode.ByCategory,
                onClick = { mode = ViewMode.ByCategory },
                label = { Text("Nach Kategorie", fontSize = 11.sp) }
            )
            FilterChip(
                selected = mode == ViewMode.ByApp,
                onClick = { mode = ViewMode.ByApp },
                label = { Text("Nach App", fontSize = 11.sp) }
            )
            FilterChip(
                selected = showSystem,
                onClick = { showSystem = !showSystem },
                label = { Text("System", fontSize = 11.sp) }
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            label = { Text(if (mode == ViewMode.ByApp) "Filter (App, Package)" else "Filter (App)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))

        val filtered = remember(apps, showSystem, filterText) {
            val q = filterText.trim().lowercase()
            apps
                .asSequence()
                .filter { showSystem || !it.isSystem }
                .filter { app ->
                    q.isEmpty() ||
                        app.displayName.lowercase().contains(q) ||
                        app.packageName.lowercase().contains(q)
                }
                .toList()
        }

        Text(
            if (loading) "Lade installierte Pakete…" else "${filtered.size} Apps / ${apps.size} gesamt",
            color = OnSurfaceMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))

        when (mode) {
            ViewMode.ByCategory -> CategoryList(filtered)
            ViewMode.ByApp -> AppList(filtered, onOpenSettings = { pkg ->
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            })
        }
    }
}

@Composable
private fun CategoryList(apps: List<AppPermissions>) {
    val groups = remember(apps) { PermissionAuditReader.groupByCategory(apps) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(groups, key = { it.category.name }) { group ->
            CategoryCard(group)
        }
    }
}

@Composable
private fun CategoryCard(group: CategoryGroup) {
    var expanded by remember { mutableStateOf(false) }
    StatCard(group.category.label + "  ·  ${group.apps.size}") {
        Text(group.category.description, color = OnSurfaceMuted, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))
        val shown = if (expanded) group.apps else group.apps.take(6)
        shown.forEach { app ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.displayName, color = AccentSoft, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium)
                    Text(app.packageName, color = OnSurfaceMuted, fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace)
                }
                if (app.isSystem) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentBubble)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) { Text("SYS", color = OnSurfaceMuted, fontSize = 9.sp) }
                }
            }
        }
        if (group.apps.size > 6) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (expanded) "Weniger" else "+${group.apps.size - 6} weitere…",
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}

@Composable
private fun AppList(apps: List<AppPermissions>, onOpenSettings: (String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(apps, key = { it.packageName }) { app ->
            AppCard(app, onOpenSettings)
        }
    }
}

@Composable
private fun AppCard(app: AppPermissions, onOpenSettings: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val cats = remember(app) { app.categories().sortedBy { it.label } }
    StatCard(app.displayName) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(app.packageName, color = OnSurfaceMuted, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
            Text(
                "Settings →",
                color = Accent, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onOpenSettings(app.packageName) }
            )
        }
        Spacer(Modifier.height(6.dp))
        if (cats.isEmpty()) {
            Text("Keine sensiblen Rechte gewährt", color = OnSurfaceMuted, fontSize = 11.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cats.forEach { c ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentBubble)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(c.label, color = AccentSoft, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${app.granted.size} von ${app.requested.size} angefordert · " +
                if (expanded) "Weniger" else "Alle anzeigen",
            color = Accent, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { expanded = !expanded }
        )
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            app.requested.forEach { perm ->
                val isGranted = perm in app.granted
                Text(
                    (if (isGranted) "✓ " else "· ") + perm.removePrefix("android.permission."),
                    color = if (isGranted) AccentSoft else OnSurfaceMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
