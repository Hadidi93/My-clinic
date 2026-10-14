package com.myclinic.app.ui.components

import com.myclinic.shared.resources.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.myclinic.app.data.DataError

@Composable
fun DataError.message(): String = stringResource(
    when (this) {
        DataError.NETWORK -> Res.string.error_network
        DataError.LICENSE_TAKEN -> Res.string.error_license_taken
        DataError.NOT_ALLOWED -> Res.string.error_not_allowed
        DataError.FILE_TOO_LARGE -> Res.string.error_file_too_large
        DataError.DUPLICATE -> Res.string.error_duplicate
        DataError.UNKNOWN -> Res.string.error_unknown
    },
)
