package com.tamerin.sysmonitor.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

@Composable
fun TelephonyScreen() {
    val context = LocalContext.current
    val tm = remember { context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager }
    val sm = remember { context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager }
    val hasReadPhone = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Netzwerk-Betreiber") {
            KeyValueRow("Operator", tm.networkOperatorName ?: "—")
            KeyValueRow("MCC+MNC", tm.networkOperator?.ifBlank { "—" } ?: "—")
            KeyValueRow("Land", tm.networkCountryIso?.uppercase() ?: "—")
            KeyValueRow("SIM-Operator", tm.simOperatorName ?: "—")
            KeyValueRow("SIM-Status", simStateLabel(tm.simState))
            KeyValueRow("Phone-Typ", phoneTypeLabel(tm.phoneType))
            KeyValueRow(
                "Datennetz-Typ",
                if (hasReadPhone) dataNetworkLabel(safeNetworkType(tm)) else "Berechtigung fehlt"
            )
            KeyValueRow("Roaming", if (tm.isNetworkRoaming) "Ja" else "Nein")
        }

        StatCard("SIM-Karten") {
            val count = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tm.activeModemCount
                else tm.phoneCount
            }.getOrDefault(1)
            KeyValueRow("Modems aktiv", count.toString())

            val subs = runCatching {
                @Suppress("MissingPermission")
                sm.activeSubscriptionInfoList
            }.getOrNull()
            if (subs == null) {
                Text(
                    "SIM-Liste benötigt READ_PHONE_STATE (in den App-Einstellungen erteilen).",
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            } else {
                subs.forEachIndexed { idx, sub ->
                    Spacer(Modifier.height(8.dp))
                    Text("SIM ${idx + 1}: ${sub.displayName ?: "—"}", fontSize = 13.sp)
                    KeyValueRow("Carrier", sub.carrierName?.toString() ?: "—")
                    KeyValueRow("Land", sub.countryIso?.uppercase() ?: "—")
                    KeyValueRow("Slot-Index", sub.simSlotIndex.toString())
                }
            }
        }

        StatCard("Mehr") {
            KeyValueRow("VoLTE-fähig", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                runCatching { tm.isVoiceCapable }.getOrDefault(false).toString() else "?")
            KeyValueRow("SMS-fähig", runCatching { tm.isSmsCapable }.getOrDefault(false).toString())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                KeyValueRow("Hat ICC-Karte", tm.hasIccCard().toString())
            }
        }
    }
}

@Suppress("MissingPermission", "DEPRECATION")
private fun safeNetworkType(tm: TelephonyManager): Int = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) tm.dataNetworkType
    else tm.networkType
}.getOrDefault(0)

private fun simStateLabel(state: Int): String = when (state) {
    TelephonyManager.SIM_STATE_ABSENT -> "Keine SIM"
    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN erforderlich"
    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK erforderlich"
    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Netz gesperrt"
    TelephonyManager.SIM_STATE_READY -> "Bereit"
    TelephonyManager.SIM_STATE_NOT_READY -> "Nicht bereit"
    TelephonyManager.SIM_STATE_PERM_DISABLED -> "Deaktiviert"
    TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "I/O-Fehler"
    TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Eingeschränkt"
    else -> "Unbekannt"
}

private fun phoneTypeLabel(type: Int): String = when (type) {
    TelephonyManager.PHONE_TYPE_GSM -> "GSM"
    TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
    TelephonyManager.PHONE_TYPE_SIP -> "SIP"
    TelephonyManager.PHONE_TYPE_NONE -> "Keine"
    else -> "?"
}

private fun dataNetworkLabel(type: Int): String = when (type) {
    TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
    TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
    TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
    TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G+)"
    TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G+)"
    TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G+)"
    TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+ (3G+)"
    TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
    TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
    TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
    0 -> "—"
    else -> "Typ #$type"
}
