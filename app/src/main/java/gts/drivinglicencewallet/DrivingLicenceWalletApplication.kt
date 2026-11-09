package gts.drivinglicencewallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import gts.drivinglicencewallet.data.photo.PhotoCleanUp
import gts.drivinglicencewallet.di.ApplicationScope
import gts.drivinglicencewallet.domain.processing.ResumeProcessingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Sets up WorkManager with Hilt's worker factory and the debug logs, and then
 * starts two start-up tasks. One schedules the processing of an unfinished
 * application again, and the other deletes the photos that no application
 * refers to.
 *
 * A start-up task that fails, for example because the database cannot be
 * opened, does not crash the app. The type of its error is logged, and the
 * task runs again at the next start. A task that fails does not stop the
 * other one.
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

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory = workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // The time is read before any screen opens, so no submit can have
        // started before it. The clean-up keeps the photos written after this
        // time, because they may belong to a submit in progress.
        val startedAt = clock.now()
        applicationScope.launch { resumeProcessing() }
        applicationScope.launch { photoCleanUp.deleteUnreferencedPhotos(appStartedAt = startedAt) }
    }
}
