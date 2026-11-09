package gts.drivinglicencewallet.ui.licenceapplication

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gts.drivinglicencewallet.R
import gts.drivinglicencewallet.domain.applicant.ApplicationForm
import gts.drivinglicencewallet.domain.applicant.FormCheck
import gts.drivinglicencewallet.domain.applicant.LicenceCategory
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.ui.theme.DrivingLicenceWalletTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * The status of one application. It is updated automatically while the
 * application is processed in the background. Once the application is
 * finished, the applicant can start a new one with [onNewApplication].
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

    LicenceApplicationContent(
        state = state,
        onNewApplication = onNewApplication,
        modifier = modifier
    )
}

/** The times are shown in the device's time zone and locale. */
@Composable
private fun LicenceApplicationContent(
    state: LicenceApplicationState,
    onNewApplication: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.licence_application_title)) })
        }
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

/** An application just submitted with the example from the assignment. */
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
    val application = LicenceApplication.submit(
        id = ApplicationId(value = "8c04d6e7-8e51-4b95-84e2-efc8a8ec4a5a"),
        details = (form.check() as FormCheck.Passed).details,
        photo = PhotoReference(value = "preview"),
        timestamp = Instant.parse(input = "2026-06-09T10:30:00Z")
    )

    DrivingLicenceWalletTheme {
        LicenceApplicationContent(
            state = LicenceApplicationState.Found(application = application),
            onNewApplication = {}
        )
    }
}
