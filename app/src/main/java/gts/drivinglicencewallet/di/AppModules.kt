package gts.drivinglicencewallet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.ApplicationIdGenerator
import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.PhotoStorage
import gts.drivinglicencewallet.domain.application.ProcessingScheduler
import gts.drivinglicencewallet.domain.application.SubmitApplicationUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import java.util.UUID
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Marks the app-wide coroutine scope, for work that must continue when a
 * screen is closed, such as a submit in progress or the start-up tasks.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Provides the app-wide coroutine scope. */
@Module
@InstallIn(SingletonComponent::class)
internal object CoroutineScopesModule {

    /**
     * Logs only the type of a failed task's error, because the error's message
     * could contain the applicant's personal data.
     */
    private val failureLogger = CoroutineExceptionHandler { _, error ->
        Timber.w("A task in the application's scope failed: %s", error.javaClass.simpleName)
    }

    // The scope lives as long as the process, and a task that fails does not
    // cancel the others. The dispatcher does not need to be injected here,
    // because the tests replace the whole scope with their own.
    @Suppress("InjectDispatcher")
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(context = SupervisorJob() + Dispatchers.Default + failureLogger)
}

/** Provides the use cases that the screens call. */
@Module
@InstallIn(SingletonComponent::class)
internal object UseCaseModule {

    /** Each ID is a random UUID, so that no two applications share an ID. */
    @Provides
    fun provideApplicationIdGenerator(): ApplicationIdGenerator =
        ApplicationIdGenerator { ApplicationId(value = UUID.randomUUID().toString()) }

    @Provides
    fun provideSubmitApplicationUseCase(
        repository: LicenceApplicationRepository,
        photoStorage: PhotoStorage,
        scheduler: ProcessingScheduler,
        idGenerator: ApplicationIdGenerator,
        clock: Clock,
    ): SubmitApplicationUseCase = SubmitApplicationUseCase(
        repository = repository,
        photoStorage = photoStorage,
        scheduler = scheduler,
        idGenerator = idGenerator,
        clock = clock
    )
}
