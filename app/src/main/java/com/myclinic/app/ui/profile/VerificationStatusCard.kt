package com.myclinic.app.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myclinic.app.R
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.domain.model.VerificationStatus

/** Explains where the account is in licence verification. */
@Composable
fun VerificationStatusCard(status: VerificationStatus, note: String?) {
    val colors = MaterialTheme.colorScheme
    val noteText = note?.let { stringResource(R.string.status_note, it) }
    when (status) {
        VerificationStatus.VERIFIED -> MessageCard(
            title = stringResource(R.string.status_verified),
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
        )
        VerificationStatus.PENDING -> MessageCard(
            title = stringResource(R.string.status_pending),
            body = stringResource(R.string.status_pending_body),
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
        )
        VerificationStatus.REJECTED -> MessageCard(
            title = stringResource(R.string.status_rejected),
            body = listOfNotNull(stringResource(R.string.status_rejected_body), noteText).joinToString("\n"),
        )
        VerificationStatus.SUSPENDED -> MessageCard(
            title = stringResource(R.string.status_suspended),
            body = listOfNotNull(stringResource(R.string.status_suspended_body), noteText).joinToString("\n"),
        )
    }
}
