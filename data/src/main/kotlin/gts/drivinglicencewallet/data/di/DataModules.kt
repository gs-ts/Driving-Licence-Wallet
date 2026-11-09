package gts.drivinglicencewallet.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gts.drivinglicencewallet.data.backend.SimulatedIssuerClient
import gts.drivinglicencewallet.data.backend.SimulatedIssuerSwitch
import gts.drivinglicencewallet.data.database.LicenceDatabase
import gts.drivinglicencewallet.data.database.RoomLicenceApplicationRepository
import gts.drivinglicencewallet.data.database.encryption.DatabaseKey
import gts.drivinglicencewallet.data.database.openLicenceDatabase
import gts.drivinglicencewallet.data.photo.EncryptedPhotoStorage
import gts.drivinglicencewallet.data.photo.PhotoCleanUp
import gts.drivinglicencewallet.data.photo.PhotoKey
import gts.drivinglicencewallet.data.processing.WorkManagerProcessingScheduler
import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.PhotoStorage
import gts.drivinglicencewallet.domain.application.ProcessingScheduler
import gts.drivinglicencewallet.domain.processing.IssuerBackend
import gts.drivinglicencewallet.domain.processing.ProcessApplicationUseCase
import gts.drivinglicencewallet.domain.processing.ResumeProcessingUseCase
import gts.drivinglicencewallet.simulatedbackend.IssuerConfig
import gts.drivinglicencewallet.simulatedbackend.SimulatedIssuer
import java.io.File
import javax.inject.Singleton
import kotlin.time.Clock

/** The encrypted database, and the repository on it. */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    /** One per process: two Room instances on one file track their changes apart, so observe() on one misses the other's writes. */
    @Provides
    @Singleton
    fun provideLicenceDatabase(@ApplicationContext context: Context): LicenceDatabase {
        val databaseFile = context.getDatabasePath("licence.db")
        val key = DatabaseKey(
            keyFile = File(context.noBackupFilesDir, "database.key"),
            databaseFile = databaseFile,
            keystoreAlias = "licence-database-key"
        )
        return openLicenceDatabase(
            context = context,
            databaseFile = databaseFile,
            key = key::get
        )
    }

    @Provides
    fun provideLicenceApplicationRepository(database: LicenceDatabase): LicenceApplicationRepository =
        RoomLicenceApplicationRepository(database = database)
}

/** The encrypted photos. */
@Module
@InstallIn(SingletonComponent::class)
internal object PhotoModule {

    /** One per process: it makes the key on first use, and two instances could each make one, so some photos couldn't be read. */
    @Provides
    @Singleton
    fun provideEncryptedPhotoStorage(@ApplicationContext context: Context): EncryptedPhotoStorage {
        val directory = File(context.noBackupFilesDir, "photos")
        return EncryptedPhotoStorage(
            contentResolver = context.contentResolver,
            directory = directory,
            key = PhotoKey(
                keysetFile = File(context.noBackupFilesDir, "photo.keyset"),
                photosDirectory = directory,
                keystoreAlias = "licence-photo-key"
            )
        )
    }

    @Provides
    fun providePhotoStorage(storage: EncryptedPhotoStorage): PhotoStorage = storage

    @Provides
    fun providePhotoCleanUp(database: LicenceDatabase, storage: EncryptedPhotoStorage): PhotoCleanUp = PhotoCleanUp(
        dao = database.licenceApplicationDao(),
        photoStorage = storage
    )
}

/** Processing in the background, and the simulated issuer it calls. */
@Module
@InstallIn(SingletonComponent::class)
internal object ProcessingModule {

    /** After this many attempts with the issuer unavailable, the application fails. */
    private const val MAX_ATTEMPTS = 5

    /** One per process, like the issuer's server, so its unreachable switch reaches every call. */
    @Provides
    @Singleton
    fun provideSimulatedIssuer(): SimulatedIssuer = SimulatedIssuer(config = IssuerConfig())

    @Provides
    fun provideSimulatedIssuerSwitch(issuer: SimulatedIssuer): SimulatedIssuerSwitch = object : SimulatedIssuerSwitch {
        override var isUnreachable: Boolean by issuer::isUnreachable
    }

    @Provides
    fun provideIssuerBackend(issuer: SimulatedIssuer, photoStorage: EncryptedPhotoStorage): IssuerBackend =
        SimulatedIssuerClient(issuer = issuer, photoReader = photoStorage)

    @Provides
    fun provideClock(): Clock = Clock.System

    @Provides
    fun provideProcessingScheduler(@ApplicationContext context: Context): ProcessingScheduler =
        WorkManagerProcessingScheduler(context = context)

    @Provides
    fun provideProcessApplicationUseCase(
        repository: LicenceApplicationRepository,
        backend: IssuerBackend,
        clock: Clock,
    ): ProcessApplicationUseCase = ProcessApplicationUseCase(
        repository = repository,
        backend = backend,
        clock = clock,
        maxAttempts = MAX_ATTEMPTS
    )

    @Provides
    fun provideResumeProcessingUseCase(repository: LicenceApplicationRepository, scheduler: ProcessingScheduler): ResumeProcessingUseCase =
        ResumeProcessingUseCase(repository = repository, scheduler = scheduler)
}
