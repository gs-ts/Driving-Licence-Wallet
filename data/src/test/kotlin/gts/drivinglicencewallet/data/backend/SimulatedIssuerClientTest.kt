package gts.drivinglicencewallet.data.backend

import gts.drivinglicencewallet.data.photo.PhotoReader
import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.applicant.ApplicantField
import gts.drivinglicencewallet.domain.applicant.ApplicationForm
import gts.drivinglicencewallet.domain.applicant.FormCheck
import gts.drivinglicencewallet.domain.applicant.LicenceCategory
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.processing.BackendResult
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.simulatedbackend.DataIssue
import gts.drivinglicencewallet.simulatedbackend.IssuerConfig
import gts.drivinglicencewallet.simulatedbackend.PhotoIssue
import gts.drivinglicencewallet.simulatedbackend.RiskFlag
import gts.drivinglicencewallet.simulatedbackend.SimulatedIssuer
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class SimulatedIssuerClientTest {

    // The real issuer, with its default rules. Its 2-second delay passes in virtual time.
    private val issuer = SimulatedIssuer(config = IssuerConfig())

    @Test
    fun `given each issuer code, when it's mapped, then it becomes the matching domain reason`() {
        val dataProblems = mapOf(
            DataIssue.MissingFirstName to DataProblem.MissingField(field = ApplicantField.FirstName),
            DataIssue.MissingLastName to DataProblem.MissingField(field = ApplicantField.LastName),
            DataIssue.MissingDateOfBirth to DataProblem.MissingField(field = ApplicantField.DateOfBirth),
            DataIssue.MissingNationalId to DataProblem.MissingField(field = ApplicantField.NationalId),
            DataIssue.MissingEmail to DataProblem.MissingField(field = ApplicantField.Email),
            DataIssue.MissingPhoneNumber to DataProblem.MissingField(field = ApplicantField.PhoneNumber),
            DataIssue.MissingAddress to DataProblem.MissingField(field = ApplicantField.Address),
            DataIssue.MissingLicenceCategory to DataProblem.MissingField(field = ApplicantField.LicenceCategory),
            DataIssue.InvalidEmail to DataProblem.InvalidEmail,
            DataIssue.Under18 to DataProblem.Under18
        )
        val photoProblems = mapOf(
            PhotoIssue.Missing to PhotoProblem.Missing,
            PhotoIssue.UnsupportedType to PhotoProblem.UnsupportedType,
            PhotoIssue.TooSmall to PhotoProblem.TooSmall,
            PhotoIssue.LowQuality to PhotoProblem.LowQuality
        )
        val reviewReasons = mapOf(
            RiskFlag.Under21 to ReviewReason.Under21,
            RiskFlag.CategoryNotB to ReviewReason.CategoryNotB,
            RiskFlag.OnWatchlist to ReviewReason.OnWatchlist,
            RiskFlag.LowPhotoQuality to ReviewReason.LowPhotoQuality
        )

        assertEquals(expected = dataProblems, actual = DataIssue.entries.associateWith { issue -> issue.toDataProblem() })
        assertEquals(expected = photoProblems, actual = PhotoIssue.entries.associateWith { issue -> issue.toPhotoProblem() })
        assertEquals(expected = reviewReasons, actual = RiskFlag.entries.associateWith { flag -> flag.toReviewReason() })
    }

    @Test
    fun `given an adult applying for B with a sharp photo, when each step runs, then the issuer finds nothing`() = runTest {
        val client = client(photos = mapOf(PHOTO to sharpJpeg()))

        val dataCheck = client.checkData(details = adultApplyingForB(), processingStartedAt = processingStartedAt)
        val photoCheck = client.checkPhoto(photo = PHOTO)
        val riskAssessment = client.assessRisk(details = adultApplyingForB(), photo = PHOTO, processingStartedAt = processingStartedAt)

        assertEquals(expected = BackendResult.Answered(answer = emptySet()), actual = dataCheck)
        assertEquals(expected = BackendResult.Answered(answer = emptySet()), actual = photoCheck)
        assertEquals(expected = BackendResult.Answered(answer = emptySet()), actual = riskAssessment)
    }

    @Test
    fun `given the issuer is unreachable, when any step is called, then the result is Unavailable`() = runTest {
        val client = client(photos = mapOf(PHOTO to sharpJpeg()))
        issuer.isUnreachable = true

        val dataCheck = client.checkData(details = adultApplyingForB(), processingStartedAt = processingStartedAt)
        val photoCheck = client.checkPhoto(photo = PHOTO)
        val riskAssessment = client.assessRisk(details = adultApplyingForB(), photo = PHOTO, processingStartedAt = processingStartedAt)

        assertEquals(expected = BackendResult.Unavailable, actual = dataCheck)
        assertEquals(expected = BackendResult.Unavailable, actual = photoCheck)
        assertEquals(expected = BackendResult.Unavailable, actual = riskAssessment)
    }

    @Test
    fun `given a photo that can't be read, when a step needs it, then the result is Unavailable`() = runTest {
        val client = client(photos = emptyMap())

        val photoCheck = client.checkPhoto(photo = PHOTO)
        val riskAssessment = client.assessRisk(details = adultApplyingForB(), photo = PHOTO, processingStartedAt = processingStartedAt)

        assertEquals(expected = BackendResult.Unavailable, actual = photoCheck)
        assertEquals(expected = BackendResult.Unavailable, actual = riskAssessment)
    }

    private fun client(photos: Map<PhotoReference, ByteArray>) =
        SimulatedIssuerClient(issuer = issuer, photoReader = FakePhotoReader(photos = photos))

    /** Reads the photos it was given. Any other photo can't be read, as if its file were gone. */
    private class FakePhotoReader(private val photos: Map<PhotoReference, ByteArray>) : PhotoReader {

        override suspend fun read(photo: PhotoReference): ByteArray? = photos[photo]
    }

    private companion object {

        val PHOTO = PhotoReference(value = "application-1")

        /** 10:00 in Brussels, the issuer's time zone. */
        val processingStartedAt: Instant = Instant.parse(input = "2026-11-08T09:00:00Z")

        /** Details that pass every check: 36 years old, category B, and not on the watchlist. */
        fun adultApplyingForB(): ApplicantDetails {
            val form = ApplicationForm(
                firstName = "Anna",
                lastName = "Svensson",
                dateOfBirth = LocalDate.of(1990, 5, 17),
                nationalId = "199005171234",
                email = "anna@example.com",
                phoneNumber = "+46 70 123 45 67",
                address = "Drottninggatan 1, 111 51 Stockholm",
                licenceCategory = LicenceCategory.B,
                photo = PhotoSource(value = "content://photos/1")
            )
            return assertIs<FormCheck.Passed>(value = form.check()).details
        }

        /**
         * A 100 KB JPEG of 1200 × 1600 pixels: big and sharp enough for the photo check and for risk assessment.
         * Only the headers the issuer reads are real.
         */
        fun sharpJpeg(): ByteArray {
            val headers = intArrayOf(
                0xFF, 0xD8, // start of image
                0xFF, 0xC0, 0x00, 0x0B, 0x08, // a baseline frame header, 11 bytes long, with 8-bit samples
                0x06, 0x40, 0x04, 0xB0, // 1600 pixels high, 1200 wide
                0x01, 0x01, 0x11, 0x00 // one colour component
            ).map { value -> value.toByte() }.toByteArray()
            return headers + ByteArray(size = 100_000 - headers.size)
        }
    }
}
