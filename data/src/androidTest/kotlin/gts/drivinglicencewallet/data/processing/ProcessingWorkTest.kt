package gts.drivinglicencewallet.data.processing

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.crypto.tink.integration.android.AndroidKeystore
import gts.drivinglicencewallet.data.backend.SimulatedIssuerClient
import gts.drivinglicencewallet.data.database.LicenceDatabase
import gts.drivinglicencewallet.data.database.RoomLicenceApplicationRepository
import gts.drivinglicencewallet.data.database.TestDatabaseFiles
import gts.drivinglicencewallet.data.database.applicantDetails
import gts.drivinglicencewallet.data.database.submittedAt
import gts.drivinglicencewallet.data.photo.EncryptedPhotoStorage
import gts.drivinglicencewallet.data.photo.PhotoKey
import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.processing.BackendResult
import gts.drivinglicencewallet.domain.processing.IssuerBackend
import gts.drivinglicencewallet.domain.processing.ProcessApplicationUseCase
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import gts.drivinglicencewallet.simulatedbackend.IssuerConfig
import gts.drivinglicencewallet.simulatedbackend.SimulatedIssuer
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The worker on the real encrypted database, photo storage and issuer, and the scheduler on a test WorkManager. */
@RunWith(AndroidJUnit4::class)
class ProcessingWorkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = TestDatabaseFiles(name = "processing-test")
    private val photosDirectory = File(context.noBackupFilesDir, "processing-test-photos")
    private val keysetFile = File(context.noBackupFilesDir, "processing-test-photos.keyset")
    private val photoKeystoreAlias = "processing-test-photo-key"
    private val pickedPhotos = File(context.cacheDir, "processing-test-picked-photos")
    private val id = ApplicationId(value = "application-1")
    private val issuer = SimulatedIssuer(config = IssuerConfig(responseDelay = Duration.ZERO))
    private val clock = object : Clock {
        override fun now(): Instant = submittedAt + 1.minutes
    }
    private lateinit var database: LicenceDatabase
    private lateinit var repository: RoomLicenceApplicationRepository
    private lateinit var photoStorage: EncryptedPhotoStorage

    @BeforeTest
    fun openStorage() {
        deleteFiles()
        database = files.open()
        repository = RoomLicenceApplicationRepository(database = database)
        photoStorage = EncryptedPhotoStorage(
            contentResolver = context.contentResolver,
            directory = photosDirectory,
            key = PhotoKey(keysetFile = keysetFile, photosDirectory = photosDirectory, keystoreAlias = photoKeystoreAlias),
        )
    }

    @AfterTest
    fun closeStorage() {
        // If the setup failed, there's nothing to close, and its error is the one to see.
        if (::database.isInitialized) database.close()
        deleteFiles()
    }

    @Test
    fun givenASubmittedApplication_whenItsWorkRuns_thenItIsProcessedToTheEnd() = runTest {
        submitApplication()

        val result = worker(runAttemptCount = 0).doWork()

        assertEquals(expected = ListenableWorker.Result.success(), actual = result)
        // The details apply for category BE, which goes to a reviewer.
        val events = listOf(
            WorkflowEvent.ApplicationSubmitted,
            WorkflowEvent.ProcessingStarted,
            WorkflowEvent.DataValidationPassed,
            WorkflowEvent.PhotoCheckPassed,
            WorkflowEvent.ManualReviewRequired(reasons = setOf(ReviewReason.CategoryNotB)),
        )
        assertEquals(expected = events, actual = assertNotNull(actual = repository.find(id = id)).history.map { entry -> entry.event })
    }

    @Test
    fun givenAnUnreachableIssuer_whenTheFourthAndFifthAttemptsRun_thenOnlyTheFifthFailsTheApplication() = runTest {
        submitApplication()
        issuer.isUnreachable = true

        val fourthAttempt = worker(runAttemptCount = 3).doWork()
        val statusAfterTheFourth = repository.find(id = id)?.status
        val fifthAttempt = worker(runAttemptCount = 4).doWork()

        assertEquals(expected = ListenableWorker.Result.retry(), actual = fourthAttempt)
        assertEquals(expected = ApplicationStatus.ValidatingData, actual = statusAfterTheFourth)
        assertEquals(expected = ListenableWorker.Result.success(), actual = fifthAttempt)
        val application = assertNotNull(actual = repository.find(id = id))
        assertEquals(expected = ApplicationStatus.Failed, actual = application.status)
        assertEquals(expected = WorkflowEvent.ProcessingAbandoned(attempts = 5), actual = application.history.last().event)
    }

    @Test
    fun givenABugInAStep_whenItsWorkRuns_thenTheWorkFailsAndTheApplicationKeepsItsStatus() = runTest {
        submitApplication()

        val result = worker(runAttemptCount = 0, backend = BrokenBackend()).doWork()

        assertEquals(expected = ListenableWorker.Result.failure(), actual = result)
        assertEquals(expected = ApplicationStatus.ValidatingData, actual = repository.find(id = id)?.status)
    }

    @Test
    fun givenAnApplicationScheduledTwice_whenItsRunsAreListed_thenThereIsOneRunThatWaitsForTheNetwork() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val scheduler = WorkManagerProcessingScheduler(context = context)
        scheduler.schedule(id = id)
        val firstRun = runs().single()

        scheduler.schedule(id = id)

        val runs = runs()
        assertEquals(expected = listOf(firstRun.id), actual = runs.map { run -> run.id })
        assertEquals(expected = NetworkType.CONNECTED, actual = runs.single().constraints.requiredNetworkType)
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    /** Stores a photo that passes the photo check, then saves the application, as Submit does. */
    private suspend fun submitApplication() {
        pickedPhotos.mkdirs()
        val picked = File(pickedPhotos, "photo.jpg").apply { writeBytes(array = sharpJpeg()) }
        val photo = assertNotNull(actual = photoStorage.store(source = PhotoSource(value = Uri.fromFile(picked).toString()), id = id))
        repository.add(application = LicenceApplication.submit(id = id, details = applicantDetails(), photo = photo, timestamp = submittedAt))
    }

    /** The worker for run [runAttemptCount], counted from 0 as WorkManager does. */
    private fun worker(
        runAttemptCount: Int,
        backend: IssuerBackend = SimulatedIssuerClient(issuer = issuer, photoReader = photoStorage),
    ): ProcessApplicationWorker {
        val processApplication = ProcessApplicationUseCase(repository = repository, backend = backend, clock = clock, maxAttempts = 5)
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                ProcessApplicationWorker(context = appContext, parameters = workerParameters, processApplication = processApplication)
        }
        return TestListenableWorkerBuilder<ProcessApplicationWorker>(
            context = context,
            inputData = ProcessApplicationWorker.inputFor(id = id),
            runAttemptCount = runAttemptCount,
        ).setWorkerFactory(factory).build()
    }

    private fun runs(): List<WorkInfo> = WorkManager.getInstance(context = context)
        .getWorkInfosForUniqueWork(uniqueWorkName = WorkManagerProcessingScheduler.uniqueWorkName(id = id))
        .get()

    private fun deleteFiles() {
        files.delete()
        photosDirectory.deleteRecursively()
        keysetFile.delete()
        pickedPhotos.deleteRecursively()
        if (AndroidKeystore.hasKey(photoKeystoreAlias)) AndroidKeystore.deleteKey(photoKeystoreAlias)
    }

    /** Every step throws, as a bug in the engine or the client would. */
    private class BrokenBackend : IssuerBackend {

        override suspend fun checkData(details: ApplicantDetails, processingStartedAt: Instant): BackendResult<Set<DataProblem>> =
            error(message = "A bug in the data check")

        override suspend fun checkPhoto(photo: PhotoReference): BackendResult<Set<PhotoProblem>> = error(message = "A bug in the photo check")

        override suspend fun assessRisk(
            details: ApplicantDetails,
            photo: PhotoReference,
            processingStartedAt: Instant,
        ): BackendResult<Set<ReviewReason>> = error(message = "A bug in the risk assessment")
    }

    private companion object {

        /** The start of a 1200 × 1600 baseline JPEG, padded to 100 KB: large and sharp enough for the photo check. */
        fun sharpJpeg(): ByteArray {
            val headers = intArrayOf(
                0xFF, 0xD8, // start of image
                0xFF, 0xC0, 0x00, 0x0B, 0x08, // a baseline frame header, 11 bytes long, with 8-bit samples
                0x06, 0x40, 0x04, 0xB0, // 1600 pixels high, 1200 wide
                0x01, 0x01, 0x11, 0x00 // one colour component
            ).map { value -> value.toByte() }.toByteArray()
            return headers + ByteArray(size = 100_000 - headers.size)
        }
    }
}
