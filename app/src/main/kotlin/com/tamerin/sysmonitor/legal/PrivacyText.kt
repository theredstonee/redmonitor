package com.tamerin.sysmonitor.legal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.legal.LegalConstants as L

object PrivacyText {

    val SUMMARY: String
        @Composable get() = stringResource(R.string.legal_privacy_summary)

    val FULL: String
        @Composable get() = listOf(
            stringResource(R.string.legal_privacy_title, L.LEGAL_LAST_UPDATED),
            stringResource(
                R.string.legal_privacy_s1,
                L.PROVIDER_NAME,
                L.PROVIDER_REPRESENTATIVE,
                L.PROVIDER_STREET,
                L.PROVIDER_CITY,
                L.PROVIDER_COUNTRY,
                L.PROVIDER_PHONE,
                L.PROVIDER_EMAIL,
                L.IMPRINT_URL
            ),
            stringResource(R.string.legal_privacy_s2, L.BACKEND_HOST),
            stringResource(R.string.legal_privacy_s3),
            stringResource(R.string.legal_privacy_s4_intro, L.BACKEND_HOST),
            stringResource(R.string.legal_privacy_s4_1),
            stringResource(R.string.legal_privacy_s4_2),
            stringResource(R.string.legal_privacy_s4_3),
            stringResource(R.string.legal_privacy_s5),
            stringResource(R.string.legal_privacy_s6),
            stringResource(R.string.legal_privacy_s7),
            stringResource(R.string.legal_privacy_s8, L.PROVIDER_EMAIL),
            stringResource(R.string.legal_privacy_s9, L.PROVIDER_EMAIL),
            stringResource(R.string.legal_privacy_s10),
            stringResource(R.string.legal_privacy_s11)
        ).joinToString("\n\n")
}
