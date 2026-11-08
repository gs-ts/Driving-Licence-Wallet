package gts.drivinglicencewallet.data.backend

import gts.drivinglicencewallet.data.photo.PhotoReader
import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.applicant.ApplicantField
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.processing.BackendResult
import gts.drivinglicencewallet.domain.processing.IssuerBackend
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.simulatedbackend.Applicant
import gts.drivinglicencewallet.simulatedbackend.DataIssue
import gts.drivinglicencewallet.simulatedbackend.PhotoIssue
import gts.drivinglicencewallet.simulatedbackend.RiskFlag
import gts.drivinglicencewallet.simulatedbackend.SimulatedIssuer
import timber.log.Timber
import java.io.IOException
import kotlin.time.Instant

/**
 * The client of the issuer simulated in the app, which the domain uses as its [IssuerBackend]. A real app would
 * have an HTTP client here. It translates between the issuer's types and the domain's, so neither depends on
 * the other. Each issuer code maps in a `when` without `else`, so a new code doesn't compile until it's mapped.
 *
 * An [IOException], such as while the issuer is unreachable, is temporary: [BackendResult.Unavailable].
 * Whatever the issuer answers, including a failed check, is [BackendResult.Answered].
 */
internal class SimulatedIssuerClient(
    private val issuer: SimulatedIssuer,
    private val photoReader: PhotoReader,
) : IssuerBackend {

    override suspend fun checkData(details: ApplicantDetails, processingStartedAt: Instant): BackendResult<Set<DataProblem>> =
        answerOrUnavailable {
            val issues = issuer.checkData(
                applicant = details.toApplicant(),
                processingStartedAt = processingStartedAt
            )
            issues.map { issue -> issue.toDataProblem() }.toSet()
        }

    /** The issuer gets the photo's bytes, or null if the photo can't be read, which it reports as missing. */
    override suspend fun checkPhoto(photo: PhotoReference): BackendResult<Set<PhotoProblem>> {
        val bytes = photoReader.read(photo = photo)
        return answerOrUnavailable {
            val issues = issuer.checkPhoto(photo = bytes)
            issues.map { issue -> issue.toPhotoProblem() }.toSet()
        }
    }

    override suspend fun assessRisk(
        details: ApplicantDetails,
        photo: PhotoReference,
        processingStartedAt: Instant,
    ): BackendResult<Set<ReviewReason>> {
        val bytes = photoReader.read(photo = photo)
        return answerOrUnavailable {
            val flags = issuer.assessRisk(
                applicant = details.toApplicant(),
                photo = bytes,
                processingStartedAt = processingStartedAt
            )
            flags.map { flag -> flag.toReviewReason() }.toSet()
        }
    }

    /** Catches only an IOException: a bug still fails loudly, and cancellation still stops the call. Logs only the error's type. */
    private inline fun <T> answerOrUnavailable(call: () -> T): BackendResult<T> = try {
        BackendResult.Answered(answer = call())
    } catch (exception: IOException) {
        Timber.w("The issuer didn't answer: %s", exception.javaClass.simpleName)
        BackendResult.Unavailable
    }
}

/** A category's code is its name, such as "B": the domain names the categories as the EU directive does. */
private fun ApplicantDetails.toApplicant(): Applicant = Applicant(
    firstName = firstName.text,
    lastName = lastName.text,
    dateOfBirth = dateOfBirth,
    nationalId = nationalId.text,
    email = email.text,
    phoneNumber = phoneNumber.text,
    address = address.text,
    licenceCategory = licenceCategory.name
)

internal fun DataIssue.toDataProblem(): DataProblem {
    return when (this) {
        DataIssue.MissingFirstName -> DataProblem.MissingField(field = ApplicantField.FirstName)
        DataIssue.MissingLastName -> DataProblem.MissingField(field = ApplicantField.LastName)
        DataIssue.MissingDateOfBirth -> DataProblem.MissingField(field = ApplicantField.DateOfBirth)
        DataIssue.MissingNationalId -> DataProblem.MissingField(field = ApplicantField.NationalId)
        DataIssue.MissingEmail -> DataProblem.MissingField(field = ApplicantField.Email)
        DataIssue.MissingPhoneNumber -> DataProblem.MissingField(field = ApplicantField.PhoneNumber)
        DataIssue.MissingAddress -> DataProblem.MissingField(field = ApplicantField.Address)
        DataIssue.MissingLicenceCategory -> DataProblem.MissingField(field = ApplicantField.LicenceCategory)
        DataIssue.InvalidEmail -> DataProblem.InvalidEmail
        DataIssue.Under18 -> DataProblem.Under18
    }
}

internal fun PhotoIssue.toPhotoProblem(): PhotoProblem {
    return when (this) {
        PhotoIssue.Missing -> PhotoProblem.Missing
        PhotoIssue.UnsupportedType -> PhotoProblem.UnsupportedType
        PhotoIssue.TooSmall -> PhotoProblem.TooSmall
        PhotoIssue.LowQuality -> PhotoProblem.LowQuality
    }
}

internal fun RiskFlag.toReviewReason(): ReviewReason {
    return when (this) {
        RiskFlag.Under21 -> ReviewReason.Under21
        RiskFlag.CategoryNotB -> ReviewReason.CategoryNotB
        RiskFlag.OnWatchlist -> ReviewReason.OnWatchlist
        RiskFlag.LowPhotoQuality -> ReviewReason.LowPhotoQuality
    }
}
