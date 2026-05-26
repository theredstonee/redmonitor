package com.tamerin.sysmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.data.SystemProps
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.Accent
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SystemPropertiesScreen() {
    var props by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        props = withContext(Dispatchers.IO) {
            SystemProps.readAll().toList()
        }
        loading = false
    }

    val filtered = remember(props, query) {
        if (query.isBlank()) props
        else props.filter { (k, v) ->
            k.contains(query, ignoreCase = true) || v.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Filter (Schlüssel oder Wert)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (loading) "Lade…" else "${filtered.size} von ${props.size} Properties",
            color = OnSurfaceMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        if (props.isEmpty() && !loading) {
            StatCard("Keine Daten") {
                Text(
                    "Konnte `getprop` nicht ausführen — auf diesem Gerät gesperrt.",
                    fontSize = 13.sp
                )
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(filtered, key = { it.first }) { (k, v) ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(k, color = Accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        v.ifBlank { "(leer)" },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
