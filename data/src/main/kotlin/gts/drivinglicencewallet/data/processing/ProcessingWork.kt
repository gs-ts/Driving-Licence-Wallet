package gts.drivinglicencewallet.data.processing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.ProcessingScheduler
import gts.drivinglicencewallet.domain.processing.ProcessApplicationUseCase
import gts.drivinglicencewallet.domain.processing.ProcessingResult
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs each application's processing as unique work, so scheduling it again while it's queued or running does nothing
 * ([ExistingWorkPolicy.KEEP]). REPLACE would cancel the running work, which only stops at its next suspension point,
 * so two runs could overlap.
 *
 * The work waits for a network, as it would with a real issuer, and retries with exponential backoff from 10 seconds,
 * WorkManager's minimum.
 */
internal class WorkManagerProcessingScheduler(private val context: Context) : ProcessingScheduler {

    override fun schedule(id: ApplicationId) {
        val request = OneTimeWorkRequestBuilder<ProcessApplicationWorker>()
            .setInputData(inputData = ProcessApplicationWorker.inputFor(id = id))
            .setConstraints(
                constraints = Constraints.Builder()
                    .setRequiredNetworkType(networkType = NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                backoffPolicy = BackoffPolicy.EXPONENTIAL,
                backoffDelay = WorkRequest.MIN_BACKOFF_MILLIS,
                timeUnit = TimeUnit.MILLISECONDS
            )
            .build()

        // Looked up on each call, not when created: Hilt can create this while it injects the Application, before the
        // worker factory that WorkManager's configuration needs.
        WorkManager.getInstance(context = context).enqueueUniqueWork(
            uniqueWorkName = uniqueWorkName(id = id),
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            request = request
        )
    }

    companion object {

        fun uniqueWorkName(id: ApplicationId): String = "processing-${id.value}"
    }
}

/**
 * Runs one attempt of an application's processing. Its only input is the application's ID: WorkManager's database
 * isn't encrypted, so the applicant's details stay in ours.
 *
 * WorkManager counts the runs from 0, including the ones the system stopped. A temporary error retries with
 * backoff, until the engine gives up on its last attempt. A bug fails the work, because a retry would fail the
 * same way: the application keeps its status, and the next app start schedules it again.
 */
@HiltWorker
internal class ProcessApplicationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val processApplication: ProcessApplicationUseCase,
) : CoroutineWorker(context, parameters) {

    // Whatever a bug throws must end the work with failure(). Cancellation passes through, so a stopped run stays stopped.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val id = ApplicationId(value = checkNotNull(value = inputData.getString(key = APPLICATION_ID)))
        val attempt = runAttemptCount + 1
        return try {
            val result = processApplication(id = id, attempt = attempt)
            Timber.i(
                "Application %s, attempt %d: %s",
                id.value,
                attempt,
                result
            )
            when (result) {
                ProcessingResult.Done -> Result.success()
                ProcessingResult.RetryLater -> Result.retry()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Only the error's type: its message could hold the applicant's data.
            Timber.e(
                "Application %s, attempt %d: stopped by %s",
                id.value,
                attempt,
                exception.javaClass.simpleName
            )
            Result.failure()
        }
    }

    companion object {

        private const val APPLICATION_ID = "applicationId"

        fun inputFor(id: ApplicationId): Data = workDataOf(APPLICATION_ID to id.value)
    }
}
