package com.myclinic.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.myclinic.app.R
import com.myclinic.app.ui.components.AppTextField
import com.myclinic.app.ui.components.ErrorMessage
import com.myclinic.app.ui.components.LanguageSwitch
import com.myclinic.app.ui.components.PrimaryButton
import com.myclinic.app.ui.components.SecondaryButton
import com.myclinic.app.ui.components.errorMessage
import com.myclinic.app.ui.components.message
import com.myclinic.app.ui.components.setAppLanguage
import com.myclinic.domain.validation.ProfileField
import com.myclinic.domain.model.AccountType
import com.myclinic.app.ui.components.MessageCard
import com.myclinic.app.ui.patients.FacilityPicker
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.heightIn

/**
 * Doctor profile. In [setupMode] it is the first screen after sign-up
 * ("complete your profile"); otherwise it is opened from the home screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    setupMode: Boolean,
    onBack: (() -> Unit)?,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.profile_saved)

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onPhotoPicked)
    }
    val pickLicense = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onLicensePhotoPicked)
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    LaunchedEffect(state.savedEvent) {
        if (state.savedEvent) {
            viewModel.onSavedEventHandled()
            snackbar.showSnackbar(savedText)
        }
    }

    LaunchedEffect(state.signOutConfirmed) {
        if (state.signOutConfirmed) {
            viewModel.onSignOutHandled()
            onSignOut()
        }
    }
    state.signOutWarning?.let { count ->
        AlertDialog(
            onDismissRequest = viewModel::cancelSignOut,
            title = { Text(stringResource(R.string.sign_out_unsynced_title)) },
            text = { Text(pluralStringResource(R.plurals.sign_out_unsynced_body, count, count)) },
            confirmButton = { TextButton(onClick = viewModel::confirmSignOut) { Text(stringResource(R.string.sign_out_anyway)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelSignOut) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (setupMode) R.string.profile_setup_title else R.string.profile_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::requestSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.sign_out))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (setupMode) Text(stringResource(R.string.profile_setup_body), style = MaterialTheme.typography.bodyLarge)
                VerificationStatusCard(state.verificationStatus, state.verificationNote)

                // Photo
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(url = state.photoUrl, loading = state.uploadingPhoto)
                    SecondaryButton(
                        text = stringResource(if (state.photoUrl == null) R.string.add_photo else R.string.change_photo),
                        onClick = { pickPhoto.launch(imageOnly) },
                        enabled = !state.uploadingPhoto,
                        modifier = Modifier.weight(1f),
                    )
                }

                fun err(field: ProfileField) = state.showErrors && field in state.invalidFields

                // Doctor or lab/radiology staff
                Text(stringResource(R.string.account_type), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { type ->
                        FilterChip(
                            selected = state.accountType == type,
                            onClick = { viewModel.onAccountTypeChange(type) },
                            label = { Text(stringResource(if (type == AccountType.STAFF) R.string.account_staff else R.string.account_doctor)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                Text(
                    stringResource(if (state.isStaff) R.string.account_staff_help else R.string.account_doctor_help),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!setupMode && state.accountType != state.savedAccountType) {
                    MessageCard(title = stringResource(R.string.account_type_change_warning))
                }

                AppTextField(
                    value = state.fullName, onValueChange = viewModel::onFullNameChange,
                    label = stringResource(R.string.full_name),
                    capitalization = KeyboardCapitalization.Words,
                    error = if (err(ProfileField.FULL_NAME)) ProfileField.FULL_NAME.errorMessage() else null,
                )
                AppTextField(
                    value = state.specialty, onValueChange = viewModel::onSpecialtyChange,
                    label = stringResource(if (state.isStaff) R.string.job_title else R.string.specialty),
                    capitalization = KeyboardCapitalization.Words,
                    error = if (err(ProfileField.SPECIALTY)) ProfileField.SPECIALTY.errorMessage() else null,
                )
                if (!state.isStaff) {
                    SpecialtySuggestions(state.specialty, viewModel::onSpecialtyChange)
                    GradeDropdown(state.grade, viewModel::onGradeChange)
                    if (state.gradePending || (state.grade != null && state.grade != state.approvedGrade && state.verificationStatus == com.myclinic.domain.model.VerificationStatus.VERIFIED)) {
                        Text(
                            stringResource(R.string.grade_pending,
                                state.approvedGrade?.let { com.myclinic.domain.forms.Vocabulary.option(it).get(com.myclinic.app.ui.components.currentAppLanguage()) } ?: "—"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AppTextField(
                    value = state.hospital, onValueChange = viewModel::onHospitalChange,
                    label = stringResource(R.string.hospital),
                    capitalization = KeyboardCapitalization.Words,
                    error = if (err(ProfileField.HOSPITAL)) ProfileField.HOSPITAL.errorMessage() else null,
                )
                AppTextField(
                    value = state.licenseNumber, onValueChange = viewModel::onLicenseChange,
                    label = stringResource(if (state.isStaff) R.string.staff_id else R.string.license_number),
                    capitalization = KeyboardCapitalization.Characters,
                    supportingText = if (!setupMode) stringResource(R.string.license_change_warning) else null,
                    error = if (err(ProfileField.LICENSE_NUMBER)) ProfileField.LICENSE_NUMBER.errorMessage() else null,
                )
                AppTextField(
                    value = state.phone, onValueChange = viewModel::onPhoneChange,
                    label = stringResource(R.string.phone),
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                    error = if (err(ProfileField.PHONE)) ProfileField.PHONE.errorMessage() else null,
                )

                if (state.isStaff) {
                    FacilityPicker(
                        label = stringResource(R.string.your_department),
                        selectedId = state.facilityId,
                        facilities = state.facilities,
                        requestKind = "",
                        noneLabel = null,
                        onSelect = viewModel::onFacilityChange,
                        onAdd = viewModel::addFacility,
                        error = if (state.showErrors && state.facilityMissing) stringResource(R.string.department_required) else null,
                    )
                }

                // Licence document
                Text(stringResource(R.string.license_document), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (state.isStaff) R.string.staff_document_help else R.string.license_document_help),
                    style = MaterialTheme.typography.bodyMedium)
                if (state.hasLicenseDocument) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.license_document_uploaded))
                    }
                }
                SecondaryButton(
                    text = stringResource(
                        if (state.hasLicenseDocument) R.string.replace_license_document else R.string.upload_license_document,
                    ),
                    onClick = { pickLicense.launch(imageOnly) },
                    enabled = !state.uploadingLicense,
                )
                if (state.uploadingLicense) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                LanguageSwitch(
                    selected = state.language,
                    onSelect = {
                        viewModel.onLanguageChange(it)
                        setAppLanguage(it) // switches immediately; saved to the profile with Save
                    },
                )

                ErrorMessage(state.error?.message())
                PrimaryButton(text = stringResource(R.string.save), onClick = viewModel::save, loading = state.saving)
            }
        }
    }
}

@Composable
private fun Avatar(url: String?, loading: Boolean) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            url != null -> AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.profile_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
