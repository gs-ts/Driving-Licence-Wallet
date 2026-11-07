package gts.drivinglicencewallet.domain.application

import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import kotlin.time.Clock

/**
 * Stores the photo, saves the application and schedules its processing, all without the network.
 *
 * Refuses while another application is in progress. Don't run two submits at once: both could pass that check.
 */
class SubmitApplication(
    private val store: ApplicationStore,
    private val photoStorage: PhotoStorage,
    private val scheduler: ProcessingScheduler,
    private val idGenerator: ApplicationIdGenerator,
    private val clock: Clock,
) {

    suspend operator fun invoke(details: ApplicantDetails, photo: PhotoSource): SubmitResult {
        val inProgress = store.findInProgress()
        return if (inProgress == null) {
            submitNew(details = details, photo = photo)
        } else {
            SubmitResult.AnotherInProgress(application = inProgress)
        }
    }

    private suspend fun submitNew(details: ApplicantDetails, photo: PhotoSource): SubmitResult {
        val id = idGenerator.next()
        val photoReference = photoStorage.store(source = photo, id = id) ?: return SubmitResult.PhotoNotSaved
        val application = LicenceApplication.submit(
            id = id,
            details = details,
            photo = photoReference,
            timestamp = clock.now()
        )
        // Save first, so the worker always finds the application.
        store.add(application = application)
        scheduler.schedule(id = id)
        return SubmitResult.Submitted(application = application)
    }
}

sealed interface SubmitResult {

    data class Submitted(val application: LicenceApplication) : SubmitResult

    /** Refused: [application] is still in progress. */
    data class AnotherInProgress(val application: LicenceApplication) : SubmitResult

    /** Refused: the photo couldn't be read or saved. */
    data object PhotoNotSaved : SubmitResult
}

/** Keeps photos in the app's private storage. */
interface PhotoStorage {

    /** Copies the picked photo for application [id]. Null if it can't be read or saved. */
    suspend fun store(source: PhotoSource, id: ApplicationId): PhotoReference?
}

/** Schedules processing in the background, so it runs even when the app is closed. */
fun interface ProcessingScheduler {

    /** Scheduling an application twice doesn't start a second run. */
    fun schedule(id: ApplicationId)
}

/** Creates each new application's ID: a random UUID in the app, a fixed one in tests. */
fun interface ApplicationIdGenerator {

    fun next(): ApplicationId
}
