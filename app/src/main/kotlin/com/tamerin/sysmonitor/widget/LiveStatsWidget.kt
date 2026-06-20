package com.tamerin.sysmonitor.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tamerin.sysmonitor.MainActivity

/**
 * Home-screen widget that shows live CPU% / RAM% / battery °C.
 * Updated by [LiveStatsWidgetWorker] every ~15 minutes (WorkManager minimum)
 * plus on each app launch.
 */
class LiveStatsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val cpu = prefs[CPU_PCT] ?: -1f
            val ram = prefs[RAM_PCT] ?: -1f
            val tempC = prefs[BATT_TEMP] ?: -1f
            val updatedAt = prefs[UPDATED_AT] ?: 0L
            WidgetContent(cpu, ram, tempC, updatedAt, context)
        }
    }

    companion object {
        val CPU_PCT = floatPreferencesKey("widget_cpu_pct")
        val RAM_PCT = floatPreferencesKey("widget_ram_pct")
        val BATT_TEMP = floatPreferencesKey("widget_batt_temp")
        val UPDATED_AT = longPreferencesKey("widget_updated_at")
    }
}

@Composable
private fun WidgetContent(cpu: Float, ram: Float, tempC: Float, updatedAt: Long, context: Context) {
    val launchIntent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header — RedMonitor brand line
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Red",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFDC2626)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
                Text(
                    "Monitor",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Metric("CPU", if (cpu < 0) "—" else "${cpu.toInt()}%", metricColor(cpu))
                Spacer(GlanceModifier.width(12.dp))
                Metric("RAM", if (ram < 0) "—" else "${ram.toInt()}%", metricColor(ram))
                Spacer(GlanceModifier.width(12.dp))
                Metric("°C", if (tempC < 0) "—" else "%.1f".format(tempC), tempColor(tempC))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(Color(0xFF9CA3AF)),
                fontSize = 10.sp
            )
        )
        Text(
            value,
            style = TextStyle(
                color = ColorProvider(color),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

private fun metricColor(pct: Float): Color = when {
    pct < 0 -> Color(0xFF9CA3AF)
    pct < 30 -> Color(0xFF22C55E)
    pct < 60 -> Color(0xFFF87171)
    pct < 85 -> Color(0xFFF97316)
    else -> Color(0xFFDC2626)
}

private fun tempColor(c: Float): Color = when {
    c < 0 -> Color(0xFF9CA3AF)
    c < 35 -> Color(0xFF22C55E)
    c < 42 -> Color(0xFFF87171)
    c < 48 -> Color(0xFFF97316)
    else -> Color(0xFFDC2626)
}
