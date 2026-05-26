package com.tamerin.sysmonitor.ui.screens

import android.content.pm.FeatureInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun FeaturesScreen() {
    val context = LocalContext.current
    val features = remember {
        context.packageManager.systemAvailableFeatures
            .sortedBy { it.name ?: "" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            StatCard("Hardware-Features") {
                KeyValueRow("Anzahl", features.size.toString())
                Text(
                    "Vom System gemeldete Features (PackageManager.systemAvailableFeatures).",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            }
        }
        items(features, key = { (it.name ?: "openGL") + it.version }) { f: FeatureInfo ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    f.name ?: "android.opengles.version",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (f.version != 0) {
                    Text(
                        "v${f.version}",
                        color = OnSurfaceMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
