package com.tamerin.sysmonitor.legal

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tamerin.sysmonitor.R
import com.tamerin.sysmonitor.legal.LegalConstants as L

object TermsText {

    val SUMMARY: String
        @Composable get() = stringResource(R.string.legal_terms_summary)

    val FULL: String
        @Composable get() = listOf(
            stringResource(R.string.legal_terms_title, L.LEGAL_LAST_UPDATED),
            stringResource(
                R.string.legal_terms_s1,
                L.BACKEND_HOST,
                L.PROVIDER_NAME,
                L.PROVIDER_REPRESENTATIVE,
                L.PROVIDER_STREET,
                L.PROVIDER_CITY,
                L.PROVIDER_COUNTRY,
                L.PROVIDER_EMAIL,
                L.IMPRINT_URL
            ),
            stringResource(R.string.legal_terms_s2),
            stringResource(R.string.legal_terms_s3_intro),
            stringResource(R.string.legal_terms_s3_1),
            stringResource(R.string.legal_terms_s3_2),
            stringResource(R.string.legal_terms_s4),
            stringResource(R.string.legal_terms_s4_1, L.BACKEND_HOST),
            stringResource(R.string.legal_terms_s4_2),
            stringResource(R.string.legal_terms_s4_3),
            stringResource(R.string.legal_terms_s5, L.PROVIDER_EMAIL),
            stringResource(R.string.legal_terms_s6),
            stringResource(R.string.legal_terms_s7),
            stringResource(R.string.legal_terms_s8)
        ).joinToString("\n\n")
}
