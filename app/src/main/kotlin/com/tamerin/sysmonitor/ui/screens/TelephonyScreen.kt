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
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tamerin.sysmonitor.ui.components.KeyValueRow
import com.tamerin.sysmonitor.ui.components.StatCard
import com.tamerin.sysmonitor.ui.theme.OnSurfaceMuted

private data class TelephonySnapshot(
    val operator: String, val mccMnc: String, val country: String,
    val simOperator: String, val simState: String, val phoneType: String,
    val dataNetwork: String, val roaming: String,
    val modemCount: String, val subs: List<SimSummary>?,
    val volte: String, val sms: String, val hasIcc: String
)
private data class SimSummary(val name: String, val carrier: String, val country: String, val slot: Int)

@Composable
fun TelephonyScreen() {
    val context = LocalContext.current
    val tm = remember { context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager }
    val sm = remember { context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager }
    val hasReadPhone = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    }
    var snap by remember { mutableStateOf<TelephonySnapshot?>(null) }
    LaunchedEffect(Unit) {
        snap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            readTelephony(context, tm, sm, hasReadPhone)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val s = snap
        if (s == null) {
            StatCard(stringResource(R.string.bench_tel_title)) {
                Text(stringResource(R.string.bench_tel_loading), color = OnSurfaceMuted, fontSize = 13.sp)
            }
            return@Column
        }
        StatCard(stringResource(R.string.bench_tel_operator_card)) {
            KeyValueRow("Operator", s.operator)
            KeyValueRow("MCC+MNC", s.mccMnc)
            KeyValueRow(stringResource(R.string.bench_country), s.country)
            KeyValueRow(stringResource(R.string.bench_tel_sim_operator), s.simOperator)
            KeyValueRow(stringResource(R.string.bench_tel_sim_status), s.simState)
            KeyValueRow(stringResource(R.string.bench_tel_phone_type), s.phoneType)
            KeyValueRow(stringResource(R.string.bench_tel_data_network_type), s.dataNetwork)
            KeyValueRow("Roaming", s.roaming)
        }

        StatCard(stringResource(R.string.bench_tel_sim_cards)) {
            KeyValueRow(stringResource(R.string.bench_tel_modems_active), s.modemCount)
            if (s.subs == null) {
                Text(
                    stringResource(R.string.bench_tel_sim_list_perm),
                    color = OnSurfaceMuted,
                    fontSize = 11.sp
                )
            } else {
                s.subs.forEachIndexed { idx, sub ->
                    Spacer(Modifier.height(8.dp))
                    Text("SIM ${idx + 1}: ${sub.name}", fontSize = 13.sp)
                    KeyValueRow("Carrier", sub.carrier)
                    KeyValueRow(stringResource(R.string.bench_country), sub.country)
                    KeyValueRow("Slot-Index", sub.slot.toString())
                }
            }
        }

        StatCard(stringResource(R.string.bench_tel_more)) {
            KeyValueRow(stringResource(R.string.bench_tel_volte_capable), s.volte)
            KeyValueRow(stringResource(R.string.bench_tel_sms_capable), s.sms)
            KeyValueRow(stringResource(R.string.bench_tel_has_icc), s.hasIcc)
        }
    }
}

@Suppress("MissingPermission")
private fun readTelephony(
    context: Context,
    tm: TelephonyManager,
    sm: SubscriptionManager,
    hasReadPhone: Boolean
): TelephonySnapshot {
    val modemCount = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tm.activeModemCount else tm.phoneCount
    }.getOrDefault(1)
    val subs = runCatching { sm.activeSubscriptionInfoList }.getOrNull()?.map { sub ->
        SimSummary(
            name = sub.displayName?.toString() ?: "—",
            carrier = sub.carrierName?.toString() ?: "—",
            country = sub.countryIso?.uppercase() ?: "—",
            slot = sub.simSlotIndex
        )
    }
    return TelephonySnapshot(
        operator = tm.networkOperatorName ?: "—",
        mccMnc = tm.networkOperator?.ifBlank { "—" } ?: "—",
        country = tm.networkCountryIso?.uppercase() ?: "—",
        simOperator = tm.simOperatorName ?: "—",
        simState = simStateLabel(context, tm.simState),
        phoneType = phoneTypeLabel(context, tm.phoneType),
        dataNetwork = if (hasReadPhone) dataNetworkLabel(context, safeNetworkType(tm)) else context.getString(R.string.bench_tel_perm_missing),
        roaming = if (tm.isNetworkRoaming) context.getString(R.string.bench_yes) else context.getString(R.string.bench_no),
        modemCount = modemCount.toString(),
        subs = subs,
        volte = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            runCatching { tm.isVoiceCapable }.getOrDefault(false).toString() else "?",
        sms = runCatching { tm.isSmsCapable }.getOrDefault(false).toString(),
        hasIcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            runCatching { tm.hasIccCard() }.getOrDefault(false).toString() else "?"
    )
}

@Suppress("MissingPermission", "DEPRECATION")
private fun safeNetworkType(tm: TelephonyManager): Int = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) tm.dataNetworkType
    else tm.networkType
}.getOrDefault(0)

private fun simStateLabel(context: Context, state: Int): String = when (state) {
    TelephonyManager.SIM_STATE_ABSENT -> context.getString(R.string.bench_tel_sim_absent)
    TelephonyManager.SIM_STATE_PIN_REQUIRED -> context.getString(R.string.bench_tel_sim_pin)
    TelephonyManager.SIM_STATE_PUK_REQUIRED -> context.getString(R.string.bench_tel_sim_puk)
    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> context.getString(R.string.bench_tel_sim_netlock)
    TelephonyManager.SIM_STATE_READY -> context.getString(R.string.bench_ready)
    TelephonyManager.SIM_STATE_NOT_READY -> context.getString(R.string.bench_tel_sim_not_ready)
    TelephonyManager.SIM_STATE_PERM_DISABLED -> context.getString(R.string.bench_tel_sim_disabled)
    TelephonyManager.SIM_STATE_CARD_IO_ERROR -> context.getString(R.string.bench_tel_sim_io_error)
    TelephonyManager.SIM_STATE_CARD_RESTRICTED -> context.getString(R.string.bench_tel_sim_restricted)
    else -> context.getString(R.string.bench_tel_unknown)
}

private fun phoneTypeLabel(context: Context, type: Int): String = when (type) {
    TelephonyManager.PHONE_TYPE_GSM -> "GSM"
    TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
    TelephonyManager.PHONE_TYPE_SIP -> "SIP"
    TelephonyManager.PHONE_TYPE_NONE -> context.getString(R.string.bench_tel_phone_none)
    else -> "?"
}

private fun dataNetworkLabel(context: Context, type: Int): String = when (type) {
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
    else -> context.getString(R.string.bench_tel_net_type_num, type)
}
