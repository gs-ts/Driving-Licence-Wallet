package gts.drivinglicencewallet.domain.processing

import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.ProcessingScheduler
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus

/**
 * Schedules the unfinished application again, such as at each app start. The database is the queue, so this
 * recovers a run that was lost: the app crashed between saving the application and scheduling it, or a bug
 * stopped its run. Scheduling twice doesn't start a second run, so it's safe while the run is still queued.
 */
class ResumeProcessingUseCase(
    private val repository: LicenceApplicationRepository,
    private val scheduler: ProcessingScheduler,
) {

    suspend operator fun invoke() {
        val application = repository.findInProgress() ?: return

        // Waiting for a reviewer: processing has nothing left to do.
        if (application.status != ApplicationStatus.PendingManualReview) {
            scheduler.schedule(id = application.id)
        }
    }
}
