package gts.drivinglicencewallet.ui.submit

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.viewModelScope
import gts.drivinglicencewallet.domain.applicant.ApplicantField
import gts.drivinglicencewallet.domain.applicant.ApplicationForm
import gts.drivinglicencewallet.domain.applicant.FormProblem
import gts.drivinglicencewallet.domain.applicant.completeForm
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.ApplicationIdGenerator
import gts.drivinglicencewallet.domain.application.FakeLicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.FakePhotoStorage
import gts.drivinglicencewallet.domain.application.FixedClock
import gts.drivinglicencewallet.domain.application.PhotoStorage
import gts.drivinglicencewallet.domain.application.SubmitApplicationUseCase
import gts.drivinglicencewallet.domain.application.submittedApplication
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitApplicationViewModelTest {

    private val repository = FakeLicenceApplicationRepository()
    private val scheduled = mutableListOf<ApplicationId>()
    private val submittedAt = Instant.parse(input = "2026-11-09T09:00:00Z")
    private val firstId = ApplicationId(value = "application-1")

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher = StandardTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `given an empty form, when Submit is tapped, then every problem shows and nothing is saved`() = runTest {
        val viewModel = viewModel()
        // A form that the applicant has not filled in yet shows no errors.
        assertEquals(expected = emptySet(), actual = viewModel.problems)

        viewModel.onAction(action = SubmitAction.SubmitClicked)
        advanceUntilIdle()

        val missingFields = ApplicantField.entries.map { field -> FormProblem.MissingField(field = field) }
        assertEquals(expected = missingFields.toSet() + FormProblem.MissingPhoto, actual = viewModel.problems)
        assertEquals(expected = Submission.NotStarted, actual = viewModel.submission)
        assertEquals(expected = emptyList(), actual = scheduled)
    }

    @Test
    fun `given the problems are showing, when the applicant fixes a field, then its problem goes away`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(action = SubmitAction.SubmitClicked)

        viewModel.firstName.setTextAndPlaceCursorAtEnd(text = "Anna")

        assertFalse(actual = FormProblem.MissingField(field = ApplicantField.FirstName) in viewModel.problems)
        assertTrue(actual = FormProblem.MissingField(field = ApplicantField.LastName) in viewModel.problems)
    }

    @Test
    fun `given a complete form, when Submit is tapped, then the application is saved and scheduled, and its ID is ready for the status screen`() =
        runTest {
            val viewModel = viewModel()
            viewModel.fillIn(form = completeForm)

            viewModel.onAction(action = SubmitAction.SubmitClicked)
            advanceUntilIdle()

            assertEquals(expected = Submission.Succeeded(applicationId = firstId), actual = viewModel.submission)
            assertEquals(expected = ApplicationStatus.Submitted, actual = repository[firstId]?.status)
            assertEquals(expected = listOf(firstId), actual = scheduled)
        }

    @Test
    fun `given a submit still running, when Submit is tapped again, then only one application is saved`() = runTest {
        var created = 0
        val viewModel = viewModel(
            photoStorage = FakePhotoStorage(storeDuration = 1.seconds),
            idGenerator = { ApplicationId(value = "application-${++created}") }
        )
        viewModel.fillIn(form = completeForm)
        viewModel.onAction(action = SubmitAction.SubmitClicked)

        viewModel.onAction(action = SubmitAction.SubmitClicked)
        advanceUntilIdle()

        // Without the guard, both submits would pass the check for an
        // application in progress while their photos are being stored.
        assertEquals(expected = listOf(firstId), actual = scheduled)
    }

    @Test
    fun `given a submit that succeeded, when Submit is tapped again, then the screen still opens that application`() = runTest {
        val viewModel = viewModel()
        viewModel.fillIn(form = completeForm)
        viewModel.onAction(action = SubmitAction.SubmitClicked)
        advanceUntilIdle()

        viewModel.onAction(action = SubmitAction.SubmitClicked)
        advanceUntilIdle()

        // Without the guard, the second submit would be refused, because the
        // first application is still in progress. The screen would then show
        // an error instead of opening that application.
        assertEquals(expected = Submission.Succeeded(applicationId = firstId), actual = viewModel.submission)
        assertEquals(expected = listOf(firstId), actual = scheduled)
    }

    @Test
    fun `given the applicant leaves while Submit runs, when it finishes, then the application is still saved and scheduled`() = runTest {
        val viewModel = viewModel(photoStorage = FakePhotoStorage(storeDuration = 1.seconds))
        viewModel.fillIn(form = completeForm)
        viewModel.onAction(action = SubmitAction.SubmitClicked)
        runCurrent()

        // Leaving the screen clears the ViewModel and cancels viewModelScope
        // while the photo is still being stored.
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()

        assertEquals(expected = ApplicationStatus.Submitted, actual = repository[firstId]?.status)
        assertEquals(expected = listOf(firstId), actual = scheduled)
    }

    @Test
    fun `given another application in progress, when Submit is tapped, then the form stays and says why`() = runTest {
        val inProgress = submittedApplication(id = ApplicationId(value = "application-0"), timestamp = submittedAt)
        repository.add(application = inProgress)
        val viewModel = viewModel()
        viewModel.fillIn(form = completeForm)

        viewModel.onAction(action = SubmitAction.SubmitClicked)
        advanceUntilIdle()

        assertEquals(expected = Submission.Refused.AnotherInProgress, actual = viewModel.submission)
        assertEquals(expected = completeForm.firstName, actual = viewModel.firstName.text.toString())
        assertEquals(expected = emptyList(), actual = scheduled)
    }

    @Test
    fun `given a photo that can't be read, when Submit is tapped, then the form stays and asks for another photo`() = runTest {
        val viewModel = viewModel(photoStorage = FakePhotoStorage(canStore = false))
        viewModel.fillIn(form = completeForm)

        viewModel.onAction(action = SubmitAction.SubmitClicked)
        advanceUntilIdle()

        assertEquals(expected = Submission.Refused.PhotoNotSaved, actual = viewModel.submission)
        assertEquals(expected = completeForm.photo, actual = viewModel.photo)
        assertEquals(expected = emptyList(), actual = scheduled)
    }

    /**
     * Creates the ViewModel with the real submit use case and fakes for its
     * dependencies. The test's scope stands in for the application's scope,
     * since both outlive viewModelScope. backgroundScope would not work here,
     * because advanceUntilIdle() does not wait for its work.
     */
    private fun TestScope.viewModel(
        photoStorage: PhotoStorage = FakePhotoStorage(),
        idGenerator: ApplicationIdGenerator = ApplicationIdGenerator { firstId },
    ): SubmitApplicationViewModel = SubmitApplicationViewModel(
        submitApplication = SubmitApplicationUseCase(
            repository = repository,
            photoStorage = photoStorage,
            scheduler = { id -> scheduled += id },
            idGenerator = idGenerator,
            clock = FixedClock(instant = submittedAt)
        ),
        applicationScope = this
    )

    /** Fills in [form] as the applicant would, by typing and picking values. */
    private fun SubmitApplicationViewModel.fillIn(form: ApplicationForm) {
        firstName.setTextAndPlaceCursorAtEnd(text = form.firstName)
        lastName.setTextAndPlaceCursorAtEnd(text = form.lastName)
        nationalId.setTextAndPlaceCursorAtEnd(text = form.nationalId)
        email.setTextAndPlaceCursorAtEnd(text = form.email)
        phoneNumber.setTextAndPlaceCursorAtEnd(text = form.phoneNumber)
        address.setTextAndPlaceCursorAtEnd(text = form.address)
        onAction(action = SubmitAction.DateOfBirthChosen(date = checkNotNull(value = form.dateOfBirth)))
        onAction(action = SubmitAction.LicenceCategoryChosen(category = checkNotNull(value = form.licenceCategory)))
        onAction(action = SubmitAction.PhotoChosen(photo = checkNotNull(value = form.photo)))
    }
}
