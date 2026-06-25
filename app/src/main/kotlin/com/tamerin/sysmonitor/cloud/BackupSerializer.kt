package com.tamerin.sysmonitor.cloud

import android.content.Context
import android.content.SharedPreferences
import com.tamerin.sysmonitor.data.battery.BatteryHistoryDatabase
import com.tamerin.sysmonitor.data.battery.BatterySample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sammelt App-Daten zu JSON-Backup und wendet Backup wieder an.
 *
 * Aktuelle Scopes:
 *   - SharedPreferences "app_prefs" (AppPrefs)
 *   - SharedPreferences "hud_prefs" (HudPrefs)
 *   - SharedPreferences "update_prefs"
 *   - Battery-Samples (Room) - letzte 30 Tage
 *
 * JSON-Format:
 * {
 *   "v": 1,
 *   "createdAt": <ms>,
 *   "appVersion": "x.y.z",
 *   "prefs": { "app_prefs": {...}, "hud_prefs": {...}, ... },
 *   "batterySamples": [ {t,c,p,s,ch}, ... ]
 * }
 */
object BackupSerializer {

    private val PREF_NAMES = listOf("app_prefs", "hud_prefs", "update_prefs")
    private const val BATTERY_LOOKBACK_MS = 30L * 24L * 3600_000L

    suspend fun collect(context: Context): ByteArray = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("v", CloudConfig.BACKUP_BLOB_VERSION)
        root.put("createdAt", System.currentTimeMillis())
        root.put("appVersion", appVersion(context))

        val prefs = JSONObject()
        for (name in PREF_NAMES) {
            prefs.put(name, prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE)))
        }
        root.put("prefs", prefs)

        val battery = JSONArray()
        runCatching {
            val dao = BatteryHistoryDatabase.get(context).sampleDao()
            val since = System.currentTimeMillis() - BATTERY_LOOKBACK_MS
            dao.samplesSince(since).forEach { s ->
                battery.put(JSONObject().apply {
                    put("t", s.timestampMs)
                    put("c", s.chargeCounterUah)
                    put("p", s.percent)
                    put("s", s.screenOn)
                    put("ch", s.charging)
                })
            }
        }
        root.put("batterySamples", battery)

        root.toString().toByteArray(Charsets.UTF_8)
    }

    suspend fun apply(context: Context, jsonBytes: ByteArray): RestoreSummary = withContext(Dispatchers.IO) {
        val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
        var prefsApplied = 0
        var samplesInserted = 0

        val prefs = root.optJSONObject("prefs") ?: JSONObject()
        for (name in PREF_NAMES) {
            val pj = prefs.optJSONObject(name) ?: continue
            val ed = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            val keys = pj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                when (val v = pj.opt(k)) {
                    is Boolean -> ed.putBoolean(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is Double -> ed.putFloat(k, v.toFloat())
                    is String -> ed.putString(k, v)
                    else -> Unit
                }
                prefsApplied += 1
            }
            ed.apply()
        }

        runCatching {
            val dao = BatteryHistoryDatabase.get(context).sampleDao()
            val arr = root.optJSONArray("batterySamples") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                dao.insert(BatterySample(
                    timestampMs = o.optLong("t"),
                    chargeCounterUah = o.optLong("c"),
                    percent = o.optDouble("p", 0.0).toFloat(),
                    screenOn = o.optBoolean("s", true),
                    charging = o.optBoolean("ch", false)
                ))
                samplesInserted += 1
            }
        }

        RestoreSummary(
            createdAt = root.optLong("createdAt"),
            sourceVersion = root.optString("appVersion"),
            prefsApplied = prefsApplied,
            batterySamplesInserted = samplesInserted
        )
    }

    private fun prefsToJson(sp: SharedPreferences): JSONObject {
        val o = JSONObject()
        for ((k, v) in sp.all) {
            when (v) {
                is Boolean, is Int, is Long, is Float, is String -> o.put(k, v)
                // Sets etc. überspringen wir bewusst
                else -> Unit
            }
        }
        return o
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    data class RestoreSummary(
        val createdAt: Long,
        val sourceVersion: String,
        val prefsApplied: Int,
        val batterySamplesInserted: Int
    )
}
