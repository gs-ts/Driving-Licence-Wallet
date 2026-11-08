package gts.drivinglicencewallet.data.database

import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.integration.android.AndroidKeystore
import gts.drivinglicencewallet.data.database.encryption.DatabaseKey
import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.applicant.ApplicantField
import gts.drivinglicencewallet.domain.applicant.ApplicationForm
import gts.drivinglicencewallet.domain.applicant.FormCheck
import gts.drivinglicencewallet.domain.applicant.LicenceCategory
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.StatusChange
import java.io.File
import java.time.LocalDate
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A database with its own files and Keystore key, so tests don't share anything. [delete] removes all of them. */
internal class TestDatabaseFiles(name: String) {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    val databaseFile: File = context.getDatabasePath("$name.db")
    val keyFile = File(context.noBackupFilesDir, "$name.key")
    val keystoreAlias = "$name-key"

    /** A new reader of the saved key, like the one the app makes after a restart. */
    fun databaseKey(): DatabaseKey = DatabaseKey(keyFile = keyFile, databaseFile = databaseFile, keystoreAlias = keystoreAlias)

    fun open(key: () -> ByteArray = databaseKey()::get): LicenceDatabase =
        openLicenceDatabase(context = context, databaseFile = databaseFile, key = key)

    fun delete() {
        context.deleteDatabase(databaseFile.name)
        keyFile.delete()
        if (AndroidKeystore.hasKey(keystoreAlias)) AndroidKeystore.deleteKey(keystoreAlias)
    }
}

/** When the test applications are submitted. Whole minutes, because the database keeps milliseconds. */
internal val submittedAt: Instant = Instant.parse(input = "2026-03-02T09:00:00Z")

internal const val NATIONAL_ID = "199005171234"

/** Details that pass every check. The letters beyond ASCII check that text is saved as it is. */
internal fun applicantDetails(): ApplicantDetails {
    val form = ApplicationForm(
        firstName = "Åsa",
        lastName = "Öberg-Lindqvist",
        dateOfBirth = LocalDate.of(1990, 5, 17),
        nationalId = NATIONAL_ID,
        email = "asa@example.com",
        phoneNumber = "+46 70 123 45 67",
        address = "Drottninggatan 1, 111 51 Stockholm",
        licenceCategory = LicenceCategory.BE,
        photo = PhotoSource(value = "content://photos/1"),
    )
    return assertIs<FormCheck.Passed>(value = form.check()).details
}

internal fun submittedApplication(id: String): LicenceApplication = LicenceApplication.submit(
    id = ApplicationId(value = id),
    details = applicantDetails(),
    photo = PhotoReference(value = "photo-$id"),
    timestamp = submittedAt,
)

/** Records each change in turn, a minute apart. Fails the test if the workflow refuses one. */
internal fun LicenceApplication.recordAll(vararg changes: StatusChange<ApplicationStatus>): LicenceApplication =
    changes.fold(initial = this) { application, change ->
        assertNotNull(actual = application.record(change = change, timestamp = application.updatedAt + 1.minutes))
    }

/** Each missing field, the email and the age. */
internal val everyDataProblem: Set<DataProblem> =
    ApplicantField.entries.map { field -> DataProblem.MissingField(field = field) }.toSet() + DataProblem.InvalidEmail + DataProblem.Under18

/** Whether [sequence] appears anywhere in these bytes. */
internal fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
    (0..size - sequence.size).any { start -> sequence.indices.all { offset -> this[start + offset] == sequence[offset] } }
