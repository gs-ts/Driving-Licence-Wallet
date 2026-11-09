package gts.drivinglicencewallet.data.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import gts.drivinglicencewallet.data.database.entities.EventCode
import gts.drivinglicencewallet.data.database.entities.HistoryEventEntity
import gts.drivinglicencewallet.data.database.entities.toEntity
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.application.RecordResult
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.RejectionReason
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

// Device tests can't have spaces in their names: minSdk is 29, and DEX allows spaces only from API 30.
@RunWith(AndroidJUnit4::class)
class RoomLicenceApplicationRepositoryTest {

    private val files = TestDatabaseFiles(name = "repository-test")
    private lateinit var database: LicenceDatabase
    private lateinit var repository: RoomLicenceApplicationRepository

    @BeforeTest
    fun openRepository() {
        files.delete()
        database = files.open()
        repository = RoomLicenceApplicationRepository(database = database)
    }

    @AfterTest
    fun deleteRepository() {
        // If the setup failed, there's nothing to close, and its error is the one to see.
        if (::database.isInitialized) database.close()
        files.delete()
    }

    @Test
    fun givenApplicationsOnEveryPathThroughTheWorkflow_whenTheyAreReadBack_thenEveryFieldEventAndReasonIsTheSame() = runTest {
        val rejection = assertNotNull(actual = RejectionReason.parse(text = "The photo, «sadly», doesn't match."))
        val applications = listOf(
            submittedApplication(id = "auto-approved").recordAll(
                ApplicationStatus.Submitted.startProcessing(),
                ApplicationStatus.ValidatingData.pass(),
                ApplicationStatus.CheckingPhoto.pass(),
                ApplicationStatus.RiskAssessment.approveAutomatically(),
            ),
            submittedApplication(id = "data-failed").recordAll(
                ApplicationStatus.Submitted.startProcessing(),
                ApplicationStatus.ValidatingData.fail(problems = everyDataProblem),
            ),
            submittedApplication(id = "photo-failed").recordAll(
                ApplicationStatus.Submitted.startProcessing(),
                ApplicationStatus.ValidatingData.pass(),
                ApplicationStatus.CheckingPhoto.fail(problems = PhotoProblem.entries.toSet()),
            ),
            submittedApplication(id = "manually-approved").recordAll(
                ApplicationStatus.Submitted.startProcessing(),
                ApplicationStatus.ValidatingData.pass(),
                ApplicationStatus.CheckingPhoto.pass(),
                ApplicationStatus.RiskAssessment.requireManualReview(reasons = ReviewReason.entries.toSet()),
                ApplicationStatus.PendingManualReview.approve(),
            ),
            submittedApplication(id = "manually-rejected").recordAll(
                ApplicationStatus.Submitted.startProcessing(),
                ApplicationStatus.ValidatingData.pass(),
                ApplicationStatus.CheckingPhoto.pass(),
                ApplicationStatus.RiskAssessment.requireManualReview(reasons = setOf(ReviewReason.OnWatchlist)),
                ApplicationStatus.PendingManualReview.reject(reason = rejection),
            ),
            submittedApplication(id = "abandoned").recordAll(
                ApplicationStatus.Submitted.startProcessing(),
                ApplicationStatus.ValidatingData.pass(),
                ApplicationStatus.CheckingPhoto.abandon(attempts = 3),
            ),
        )

        applications.forEach { application -> repository.add(application = application) }

        applications.forEach { application -> assertEquals(expected = application, actual = repository.find(id = application.id)) }
    }

    @Test
    fun givenASubmittedApplication_whenProcessingStartIsRecorded_thenTheChangeIsSavedAndReturned() = runTest {
        val submitted = submittedApplication(id = "application-1")
        repository.add(application = submitted)
        val processing = submitted.recordAll(ApplicationStatus.Submitted.startProcessing())

        val result = repository.record(id = submitted.id, change = ApplicationStatus.Submitted.startProcessing(), timestamp = processing.updatedAt)

        assertEquals(expected = RecordResult.Recorded(application = processing), actual = result)
        assertEquals(expected = processing, actual = repository.find(id = submitted.id))
    }

    @Test
    fun givenASubmittedApplication_whenAMoveFromAnotherStatusIsRecorded_thenItIsRefusedAndNothingIsSaved() = runTest {
        val submitted = submittedApplication(id = "application-1")
        repository.add(application = submitted)

        val result = repository.record(id = submitted.id, change = ApplicationStatus.ValidatingData.pass(), timestamp = submittedAt + 1.minutes)

        assertEquals(expected = RecordResult.Refused(current = submitted), actual = result)
        assertEquals(expected = submitted, actual = repository.find(id = submitted.id))
    }

    @Test
    fun givenNoSuchApplication_whenAMoveIsRecorded_thenItIsNotFound() = runTest {
        val result = repository.record(
            id = ApplicationId(value = "missing"),
            change = ApplicationStatus.Submitted.startProcessing(),
            timestamp = submittedAt
        )

        assertEquals(expected = RecordResult.NotFound, actual = result)
    }

    @Test
    fun givenTenRunsRecordingTheSameMoveAtOnce_whenTheyFinish_thenExactlyOneIsRecorded() = runTest {
        val submitted = submittedApplication(id = "application-1")
        repository.add(application = submitted)

        val results = List(size = 10) {
            async(context = Dispatchers.IO) {
                repository.record(id = submitted.id, change = ApplicationStatus.Submitted.startProcessing(), timestamp = submittedAt + 1.minutes)
            }
        }.awaitAll()

        assertEquals(expected = 1, actual = results.count { result -> result is RecordResult.Recorded })
        assertEquals(expected = 9, actual = results.count { result -> result is RecordResult.Refused })
        assertEquals(expected = 2, actual = repository.find(id = submitted.id)?.history?.size)
    }

    @Test
    fun givenAnObserver_whenAMoveIsRecorded_thenItGetsTheApplicationWithTheNewStatusAndEntry() = runTest {
        val submitted = submittedApplication(id = "application-1")
        repository.add(application = submitted)
        val processing = submitted.recordAll(ApplicationStatus.Submitted.startProcessing())

        repository.observe(id = submitted.id).test {
            assertEquals(expected = submitted, actual = awaitItem())
            repository.record(id = submitted.id, change = ApplicationStatus.Submitted.startProcessing(), timestamp = processing.updatedAt)
            assertEquals(expected = processing, actual = awaitItem())
        }
    }

    @Test
    fun givenAFinishedAndAnUnfinishedApplication_whenTheOneInProgressIsLookedUp_thenItIsTheUnfinishedOne() = runTest {
        val approved = submittedApplication(id = "approved").recordAll(
            ApplicationStatus.Submitted.startProcessing(),
            ApplicationStatus.ValidatingData.pass(),
            ApplicationStatus.CheckingPhoto.pass(),
            ApplicationStatus.RiskAssessment.approveAutomatically(),
        )
        val inProgress = submittedApplication(id = "in-progress").recordAll(ApplicationStatus.Submitted.startProcessing())
        repository.add(application = approved)
        repository.add(application = inProgress)

        assertEquals(expected = inProgress, actual = repository.findInProgress())
    }

    @Test
    fun givenAFinishedApplicationAndANewerOne_whenTheLatestIsLookedUp_thenItIsTheNewerOne() = runTest {
        val finished = submittedApplication(id = "finished").recordAll(
            ApplicationStatus.Submitted.startProcessing(),
            ApplicationStatus.ValidatingData.pass(),
            ApplicationStatus.CheckingPhoto.pass(),
            ApplicationStatus.RiskAssessment.approveAutomatically(),
        )
        val newer = LicenceApplication.submit(
            id = ApplicationId(value = "newer"),
            details = applicantDetails(),
            photo = PhotoReference(value = "photo-newer"),
            timestamp = finished.updatedAt + 1.minutes,
        )
        // The newer one is added first, so that the order of the rows cannot decide the result.
        repository.add(application = newer)
        repository.add(application = finished)

        assertEquals(expected = newer, actual = repository.findLatest())
    }

    @Test
    fun givenSavedHistoriesThatCantBeRead_whenTheyAreLookedUp_thenTheyAreLeftOut() = runTest {
        val dao = database.licenceApplicationDao()
        val skipsSteps = submittedApplication(id = "skips-steps")
        val unknownEvent = submittedApplication(id = "unknown-event")
        for (application in listOf(skipsSteps, unknownEvent)) {
            dao.insertApplication(application = application.toEntity())
            dao.insertHistory(events = listOf(element = application.history.single().toEntity(applicationId = application.id)))
        }
        // Written straight to the database, because the repository only saves moves the workflow allows.
        dao.insertHistory(
            events = listOf(
                historyEvent(applicationId = skipsSteps.id, event = EventCode.AutoApproved.name),
                historyEvent(applicationId = unknownEvent.id, event = "Teleported"),
            )
        )

        assertNull(actual = repository.find(id = skipsSteps.id))
        assertNull(actual = repository.find(id = unknownEvent.id))
        assertNull(actual = repository.findInProgress())
    }

    private fun historyEvent(applicationId: ApplicationId, event: String) = HistoryEventEntity(
        applicationId = applicationId.value,
        timestamp = (submittedAt + 1.minutes).toEpochMilliseconds(),
        event = event,
        reasons = null,
        rejectionReason = null,
        attempts = null,
    )
}
