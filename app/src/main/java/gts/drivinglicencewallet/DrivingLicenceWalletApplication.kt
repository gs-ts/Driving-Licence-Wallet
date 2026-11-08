package gts.drivinglicencewallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import gts.drivinglicencewallet.data.photo.PhotoCleanUp
import gts.drivinglicencewallet.domain.processing.ResumeProcessingUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Sets up WorkManager with Hilt's worker factory and the debug logs, then starts the start-up tasks:
 * scheduling the unfinished application again, and the photo clean-up.
 */
@HiltAndroidApp
class DrivingLicenceWalletApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var resumeProcessing: ResumeProcessingUseCase

    @Inject
    lateinit var photoCleanUp: PhotoCleanUp

    @Inject
    lateinit var clock: Clock

    /**
     * A start-up task that fails, such as when the database can't be opened, is logged by its error's type and
     * runs again at the next start, instead of crashing the app.
     */
    private val startUpFailureLogger = CoroutineExceptionHandler { _, error ->
        Timber.w("A start-up task failed: %s", error.javaClass.simpleName)
    }

    /** Runs the start-up tasks on Dispatchers.Default. One that fails doesn't stop the other. */
    private val startUpScope = CoroutineScope(context = SupervisorJob() + startUpFailureLogger)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory = workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Before any screen opens, so every photo written after it may belong to a submit in progress.
        val startedAt = clock.now()
        startUpScope.launch { resumeProcessing() }
        startUpScope.launch { photoCleanUp.deleteUnreferencedPhotos(appStartedAt = startedAt) }
    }
}
