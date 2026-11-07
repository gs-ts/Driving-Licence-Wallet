package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.applicant.completeDetails
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SubmitApplicationTest {

    private val now = Instant.parse("2026-11-07T10:00:00Z")
    private val photo = PhotoSource("content://photos/1")
    private val newId = ApplicationId("application-2")
    private val store = FakeApplicationStore()
    private val scheduled = mutableListOf<ApplicationId>()
    private var savedBeforeScheduling = false
    private val submitApplication = submitApplicationWith(photoStorage = FakePhotoStorage())

    @Test
    fun `given no application in progress, when the applicant submits, then it is saved as submitted and then scheduled for processing`() = runTest {
        val result = submitApplication(details = completeDetails(), photo = photo)

        val application = assertIs<SubmitResult.Submitted>(value = result).application
        assertEquals(expected = newId, actual = application.id)
        assertEquals(expected = ApplicationStatus.Submitted, actual = application.status)
        assertEquals(expected = now, actual = application.createdAt)
        assertEquals(expected = PhotoReference(value = "photo-application-2"), actual = application.photo)
        assertEquals(expected = application, actual = store[newId])
        assertEquals(expected = listOf(newId), actual = scheduled)
        assertTrue(actual = savedBeforeScheduling)
    }

    @Test
    fun `given an application in progress, when the applicant submits another, then it is refused and nothing new is saved or scheduled`() = runTest {
        val inProgress = submittedApplication(id = ApplicationId(value = "application-1"), timestamp = now)
        store.add(inProgress)

        val result = submitApplication(details = completeDetails(), photo = photo)

        assertEquals(expected = SubmitResult.AnotherInProgress(application = inProgress), actual = result)
        assertNull(actual = store[newId])
        assertTrue(actual = scheduled.isEmpty())
    }

    @Test
    fun `given a failed application, when the applicant submits again, then a new application is submitted`() = runTest {
        val failed = submittedApplication(id = ApplicationId(value = "application-1"), timestamp = now)
            .recordOrFail(change = ApplicationStatus.Submitted.startProcessing(), timestamp = now)
            .recordOrFail(change = ApplicationStatus.ValidatingData.abandon(attempts = 3), timestamp = now)
        store.add(failed)

        val result = submitApplication(details = completeDetails(), photo = photo)

        assertEquals(expected = newId, actual = assertIs<SubmitResult.Submitted>(value = result).application.id)
        assertEquals(expected = listOf(newId), actual = scheduled)
    }

    @Test
    fun `given a photo that cannot be saved, when the applicant submits, then it is refused and nothing is saved or scheduled`() = runTest {
        val submitApplication = submitApplicationWith(photoStorage = FakePhotoStorage(canStore = false))

        val result = submitApplication(details = completeDetails(), photo = photo)

        assertEquals(expected = SubmitResult.PhotoNotSaved, result)
        assertNull(actual = store[newId])
        assertTrue(actual = scheduled.isEmpty())
    }

    private fun submitApplicationWith(photoStorage: PhotoStorage) = SubmitApplication(
        store = store,
        photoStorage = photoStorage,
        scheduler = { id ->
            savedBeforeScheduling = store[id] != null
            scheduled += id
        },
        idGenerator = { newId },
        clock = FixedClock(now),
    )
}
