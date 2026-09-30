package com.myclinic.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myclinic.app.R
import com.myclinic.app.data.DataError

@Composable
fun DataError.message(): String = stringResource(
    when (this) {
        DataError.NETWORK -> R.string.error_network
        DataError.LICENSE_TAKEN -> R.string.error_license_taken
        DataError.NOT_ALLOWED, DataError.UNKNOWN -> R.string.error_unknown
    },
)
