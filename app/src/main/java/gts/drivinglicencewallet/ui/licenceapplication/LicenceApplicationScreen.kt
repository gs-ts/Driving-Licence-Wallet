package gts.drivinglicencewallet.ui.licenceapplication

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gts.drivinglicencewallet.R
import gts.drivinglicencewallet.domain.applicant.ApplicantField
import gts.drivinglicencewallet.domain.applicant.ApplicationForm
import gts.drivinglicencewallet.domain.applicant.FormCheck
import gts.drivinglicencewallet.domain.applicant.LicenceCategory
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.RejectionReason
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import gts.drivinglicencewallet.ui.theme.DrivingLicenceWalletTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * The status of one application, with its reason and its history. It is
 * updated automatically while the application is processed in the background.
 * Once the application is finished, the applicant can start a new one with
 * [onNewApplication]. Below the details, a simulation section lets a tester act
 * as the issuer.
 */
@Composable
fun LicenceApplicationScreen(
    applicationId: String,
    onNewApplication: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LicenceApplicationViewModel = hiltViewModel<LicenceApplicationViewModel, LicenceApplicationViewModel.Factory>(
        creationCallback = { factory -> factory.create(applicationId = applicationId) }
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    val isServerUnreachable by viewModel.isServerUnreachable.collectAsStateWithLifecycle()

    LicenceApplicationContent(
        state = state,
        refusal = refusal,
        isServerUnreachable = isServerUnreachable,
        onNewApplication = onNewApplication,
        onAction = viewModel::onAction,
        modifier = modifier
    )
}

/** The times are shown in the device's time zone and locale. */
@Composable
private fun LicenceApplicationContent(
    state: LicenceApplicationState,
    refusal: ReviewRefusal?,
    isServerUnreachable: Boolean,
    onNewApplication: () -> Unit,
    onAction: (LicenceApplicationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val refusalMessage = refusal?.message()
    val currentOnAction by rememberUpdatedState(newValue = onAction)
    LaunchedEffect(key1 = refusal) {
        if (refusalMessage != null) {
            snackbarHostState.showSnackbar(message = refusalMessage, duration = SnackbarDuration.Long)
            currentOnAction(LicenceApplicationAction.MessageShown)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.licence_application_title)) })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(paddingValues = innerPadding)
                .consumeWindowInsets(paddingValues = innerPadding)
                .verticalScroll(state = rememberScrollState())
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 16.dp)
        ) {
            when (state) {
                // Reading the application usually takes a few milliseconds, so nothing is shown meanwhile.
                LicenceApplicationState.Loading -> Unit

                LicenceApplicationState.NotFound -> Text(text = stringResource(id = R.string.licence_application_not_found))

                is LicenceApplicationState.Found -> {
                    val application = state.application
                    DetailView(
                        label = R.string.licence_application_id,
                        value = application.id.value
                    )
                    DetailView(
                        label = R.string.licence_application_status,
                        value = stringResource(id = application.status.labelResource),
                        // The status changes while the screen is open, so that a screen reader announces each change.
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    // The latest change explains the status, for example why the application failed or was rejected.
                    val reason = application.history.last().event.detailsText()

                    if (reason != null) {
                        DetailView(
                            label = R.string.licence_application_reason,
                            value = reason,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    DetailView(
                        label = R.string.licence_application_created,
                        value = timeFormatter.format(application.createdAt.toJavaInstant())
                    )
                    DetailView(
                        label = R.string.licence_application_updated,
                        value = timeFormatter.format(application.updatedAt.toJavaInstant())
                    )

                    // Only one application can be in progress at a time, so a new one can start once this one is finished.
                    if (application.status is ApplicationStatus.Final) {
                        Button(
                            onClick = onNewApplication,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(id = R.string.licence_application_new))
                        }
                    }

                    HorizontalDivider()
                    SimulationView(
                        isServerUnreachable = isServerUnreachable,
                        onAction = onAction
                    )
                    HorizontalDivider()
                    HistoryView(
                        history = application.history,
                        timeFormatter = timeFormatter
                    )
                }
            }
        }
    }
}

/** A label with its value under it. A screen reader reads them together. */
@Composable
private fun DetailView(
    @StringRes label: Int,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = stringResource(id = label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Lets a tester act as the issuer: a reviewer approves or rejects the
 * application, and the server can be made unreachable to show how processing
 * retries. The buttons are always enabled, so that a refused decision shows
 * why.
 */
@Composable
private fun SimulationView(
    isServerUnreachable: Boolean,
    onAction: (LicenceApplicationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isRejecting by rememberSaveable { mutableStateOf(value = false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 8.dp)
    ) {
        Text(
            text = stringResource(id = R.string.simulation_title),
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            OutlinedButton(
                onClick = { onAction(LicenceApplicationAction.ApproveClicked) },
                modifier = Modifier.weight(weight = 1f)
            ) {
                Text(text = stringResource(id = R.string.simulation_approve))
            }
            OutlinedButton(
                onClick = { isRejecting = true },
                modifier = Modifier.weight(weight = 1f)
            ) {
                Text(text = stringResource(id = R.string.simulation_reject))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = isServerUnreachable,
                    role = Role.Switch,
                    onValueChange = { isUnreachable ->
                        onAction(LicenceApplicationAction.ServerUnreachableChanged(isUnreachable = isUnreachable))
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.simulation_server_unreachable),
                modifier = Modifier.weight(weight = 1f)
            )
            // The row handles taps, so that the label toggles the switch too, and a screen reader reads them together.
            Switch(checked = isServerUnreachable, onCheckedChange = null)
        }

        if (isRejecting) {
            RejectionDialogView(
                onReject = { reason ->
                    isRejecting = false
                    onAction(LicenceApplicationAction.RejectionConfirmed(reason = reason))
                },
                onDismissRequest = { isRejecting = false }
            )
        }
    }
}

/**
 * Asks for the reason of a rejection. An empty reason shows an error under
 * the field, and the dialog stays open.
 */
@Composable
private fun RejectionDialogView(
    onReject: (RejectionReason) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reasonText = rememberTextFieldState()
    // False until the first tap on Reject, so that an empty dialog shows no error.
    var shouldShowMissingReason by rememberSaveable { mutableStateOf(value = false) }
    val isReasonMissing = shouldShowMissingReason && RejectionReason.parse(text = reasonText.text.toString()) == null

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val reason = RejectionReason.parse(text = reasonText.text.toString())

                    if (reason == null) {
                        shouldShowMissingReason = true
                    } else {
                        onReject(reason)
                    }
                }
            ) {
                Text(text = stringResource(id = R.string.simulation_reject))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
        title = { Text(text = stringResource(id = R.string.simulation_rejection_title)) },
        text = {
            OutlinedTextField(
                state = reasonText,
                label = { Text(text = stringResource(id = R.string.simulation_rejection_reason)) },
                supportingText = if (isReasonMissing) {
                    { Text(text = stringResource(id = R.string.problem_missing_field)) }
                } else {
                    null
                },
                isError = isReasonMissing,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        }
    )
}

/** The snackbar's message for a refused decision. */
@Composable
private fun ReviewRefusal.message(): String = when (this) {
    is ReviewRefusal.NotPendingReview -> stringResource(
        id = R.string.simulation_not_pending_review,
        stringResource(id = status.labelResource)
    )
    ReviewRefusal.NotFound -> stringResource(id = R.string.licence_application_not_found)
}

/**
 * Every change of the application, the newest first. Each entry shows its
 * time, the status before and after it, the event and its details.
 */
@Composable
private fun HistoryView(
    history: List<HistoryEntry>,
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.history_title),
            style = MaterialTheme.typography.titleMedium
        )
        for (entry in history.asReversed()) {
            HistoryEntryView(
                entry = entry,
                timeFormatter = timeFormatter
            )
        }
    }
}

/** One change in the history. A screen reader reads it as one item. */
@Composable
private fun HistoryEntryView(
    entry: HistoryEntry,
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
) {
    val toStatus = stringResource(id = entry.toStatus.labelResource)
    val fromStatus = entry.fromStatus
    // The first entry is the submission, which has no status before it.
    val statusChange = if (fromStatus == null) {
        toStatus
    } else {
        stringResource(
            id = R.string.history_status_change,
            stringResource(id = fromStatus.labelResource),
            toStatus
        )
    }
    val details = entry.event.detailsText()

    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = timeFormatter.format(entry.timestamp.toJavaInstant()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        Text(text = statusChange, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(id = entry.event.labelResource),
            style = MaterialTheme.typography.bodyMedium
        )

        if (details != null) {
            Text(
                text = details,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * The event's details as text, one per line, or null if it has none. The
 * reviewer's reason is shown as the reviewer wrote it.
 */
@Composable
private fun WorkflowEvent.detailsText(): String? = when (this) {
    WorkflowEvent.ApplicationSubmitted,
    WorkflowEvent.ProcessingStarted,
    WorkflowEvent.DataValidationPassed,
    WorkflowEvent.PhotoCheckPassed,
    WorkflowEvent.AutoApproved,
    WorkflowEvent.ManuallyApproved -> null
    is WorkflowEvent.DataValidationFailed -> problems
        .map { problem -> problem.text() }
        .joinToString(separator = "\n")
    is WorkflowEvent.PhotoCheckFailed -> problems
        .map { problem -> stringResource(id = problem.textResource) }
        .joinToString(separator = "\n")
    is WorkflowEvent.ManualReviewRequired -> reasons
        .map { reason -> stringResource(id = reason.textResource) }
        .joinToString(separator = "\n")
    is WorkflowEvent.ManuallyRejected -> reason.text
    is WorkflowEvent.ProcessingAbandoned -> pluralStringResource(
        id = R.plurals.reason_processing_abandoned,
        count = attempts,
        attempts
    )
}

@Composable
private fun DataProblem.text(): String = when (this) {
    is DataProblem.MissingField -> stringResource(
        id = R.string.reason_missing_field,
        stringResource(id = field.labelResource)
    )
    DataProblem.InvalidEmail -> stringResource(id = R.string.reason_invalid_email)
    DataProblem.Under18 -> stringResource(id = R.string.reason_under_18)
}

@get:StringRes
private val WorkflowEvent.labelResource: Int
    get() = when (this) {
        WorkflowEvent.ApplicationSubmitted -> R.string.event_application_submitted
        WorkflowEvent.ProcessingStarted -> R.string.event_processing_started
        WorkflowEvent.DataValidationPassed -> R.string.event_data_validation_passed
        is WorkflowEvent.DataValidationFailed -> R.string.event_data_validation_failed
        WorkflowEvent.PhotoCheckPassed -> R.string.event_photo_check_passed
        is WorkflowEvent.PhotoCheckFailed -> R.string.event_photo_check_failed
        WorkflowEvent.AutoApproved -> R.string.event_auto_approved
        is WorkflowEvent.ManualReviewRequired -> R.string.event_manual_review_required
        WorkflowEvent.ManuallyApproved -> R.string.event_manually_approved
        is WorkflowEvent.ManuallyRejected -> R.string.event_manually_rejected
        is WorkflowEvent.ProcessingAbandoned -> R.string.event_processing_abandoned
    }

@get:StringRes
private val PhotoProblem.textResource: Int
    get() = when (this) {
        PhotoProblem.Missing -> R.string.reason_photo_missing
        PhotoProblem.UnsupportedType -> R.string.reason_photo_unsupported_type
        PhotoProblem.TooSmall -> R.string.reason_photo_too_small
        PhotoProblem.LowQuality -> R.string.reason_photo_low_quality
    }

@get:StringRes
private val ReviewReason.textResource: Int
    get() = when (this) {
        ReviewReason.Under21 -> R.string.reason_under_21
        ReviewReason.CategoryNotB -> R.string.reason_category_not_b
        ReviewReason.OnWatchlist -> R.string.reason_on_watchlist
        ReviewReason.LowPhotoQuality -> R.string.reason_review_photo_quality
    }

@get:StringRes
private val ApplicantField.labelResource: Int
    get() = when (this) {
        ApplicantField.FirstName -> R.string.field_first_name
        ApplicantField.LastName -> R.string.field_last_name
        ApplicantField.DateOfBirth -> R.string.field_date_of_birth
        ApplicantField.NationalId -> R.string.field_national_id
        ApplicantField.Email -> R.string.field_email
        ApplicantField.PhoneNumber -> R.string.field_phone_number
        ApplicantField.Address -> R.string.field_address
        ApplicantField.LicenceCategory -> R.string.field_licence_category
    }

@get:StringRes
private val ApplicationStatus.labelResource: Int
    get() = when (this) {
        ApplicationStatus.Submitted -> R.string.status_submitted
        ApplicationStatus.ValidatingData -> R.string.status_validating_data
        ApplicationStatus.CheckingPhoto -> R.string.status_checking_photo
        ApplicationStatus.RiskAssessment -> R.string.status_risk_assessment
        ApplicationStatus.PendingManualReview -> R.string.status_pending_manual_review
        ApplicationStatus.Approved -> R.string.status_approved
        ApplicationStatus.Rejected -> R.string.status_rejected
        ApplicationStatus.Failed -> R.string.status_failed
    }

/**
 * An application with the example from the assignment, which a reviewer
 * rejected after a manual review.
 */
@Preview
@Composable
private fun LicenceApplicationPreview() {
    val form = ApplicationForm(
        firstName = "John",
        lastName = "Doe",
        dateOfBirth = LocalDate.parse("1990-05-20"),
        nationalId = "AB123456",
        email = "john.doe@example.com",
        phoneNumber = "+306900000000",
        address = "Example Street 10",
        licenceCategory = LicenceCategory.B,
        photo = PhotoSource(value = "content://preview/photo.jpg")
    )
    val submittedAt = Instant.parse(input = "2026-06-09T10:30:00Z")
    val submitted = LicenceApplication.submit(
        id = ApplicationId(value = "8c04d6e7-8e51-4b95-84e2-efc8a8ec4a5a"),
        details = (form.check() as FormCheck.Passed).details,
        photo = PhotoReference(value = "preview"),
        timestamp = submittedAt
    )
    val changes = listOf(
        ApplicationStatus.Submitted.startProcessing(),
        ApplicationStatus.ValidatingData.pass(),
        ApplicationStatus.CheckingPhoto.pass(),
        ApplicationStatus.RiskAssessment.requireManualReview(reasons = setOf(ReviewReason.OnWatchlist)),
        ApplicationStatus.PendingManualReview.reject(reason = checkNotNull(RejectionReason.parse(text = "The face is not clear")))
    )
    // Each change is recorded one minute after the one before it.
    val rejected = changes.foldIndexed(initial = submitted) { index, application, change ->
        checkNotNull(application.record(change = change, timestamp = submittedAt + (index + 1).minutes))
    }

    DrivingLicenceWalletTheme {
        LicenceApplicationContent(
            state = LicenceApplicationState.Found(application = rejected),
            refusal = null,
            isServerUnreachable = false,
            onNewApplication = {},
            onAction = {}
        )
    }
}
