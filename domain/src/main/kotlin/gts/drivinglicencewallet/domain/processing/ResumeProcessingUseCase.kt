package gts.drivinglicencewallet.domain.processing

import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.ProcessingScheduler
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus

/**
 * Schedules the unfinished application again when the app starts. This
 * recovers a run that was lost because the app crashed or was killed between
 * saving the application and scheduling it, or because a bug stopped the run.
 * Scheduling twice does not start a second run, so this is safe while a run
 * is still queued or running.
 */
class ResumeProcessingUseCase(
    private val repository: LicenceApplicationRepository,
    private val scheduler: ProcessingScheduler,
) {

    suspend operator fun invoke() {
        val application = repository.findInProgress() ?: return

        // Processing has nothing left to do while a reviewer decides.
        if (application.status != ApplicationStatus.PendingManualReview) {
            scheduler.schedule(id = application.id)
        }
    }
}
