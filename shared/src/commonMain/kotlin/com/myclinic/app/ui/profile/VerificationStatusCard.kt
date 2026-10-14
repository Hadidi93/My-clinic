package com.myclinic.app.ui.profile

import com.myclinic.shared.resources.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.domain.model.VerificationStatus

/** Explains where the account is in licence verification. */
@Composable
fun VerificationStatusCard(status: VerificationStatus, note: String?) {
    val colors = MaterialTheme.colorScheme
    val noteText = note?.let { stringResource(Res.string.status_note, it) }
    when (status) {
        VerificationStatus.VERIFIED -> MessageCard(
            title = stringResource(Res.string.status_verified),
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
        )
        VerificationStatus.PENDING -> MessageCard(
            title = stringResource(Res.string.status_pending),
            body = stringResource(Res.string.status_pending_body),
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
        )
        VerificationStatus.REJECTED -> MessageCard(
            title = stringResource(Res.string.status_rejected),
            body = listOfNotNull(stringResource(Res.string.status_rejected_body), noteText).joinToString("\n"),
        )
        VerificationStatus.SUSPENDED -> MessageCard(
            title = stringResource(Res.string.status_suspended),
            body = listOfNotNull(stringResource(Res.string.status_suspended_body), noteText).joinToString("\n"),
        )
    }
}
