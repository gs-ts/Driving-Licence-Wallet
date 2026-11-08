package gts.drivinglicencewallet.simulatedbackend

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DataCheckTest {

    private val issuer = SimulatedIssuer(config = IssuerConfig())

    @Test
    fun `given blank fields and no date of birth, when the data is checked, then each missing field is reported`() = runTest {
        val blankApplicant = Applicant(
            firstName = "",
            lastName = " ",
            dateOfBirth = null,
            nationalId = "",
            email = "",
            phoneNumber = "",
            address = "",
            licenceCategory = "",
        )

        val issues = issuer.checkData(applicant = blankApplicant, processingStartedAt = processingStartedAt)

        val everyFieldMissing = setOf(
            DataIssue.MissingFirstName,
            DataIssue.MissingLastName,
            DataIssue.MissingDateOfBirth,
            DataIssue.MissingNationalId,
            DataIssue.MissingEmail,
            DataIssue.MissingPhoneNumber,
            DataIssue.MissingAddress,
            DataIssue.MissingLicenceCategory,
        )
        assertEquals(expected = everyFieldMissing, actual = issues)
    }

    @Test
    fun `given an email without the usual shape, when the data is checked, then the email is invalid`() = runTest {
        val applicant = adultApplicant.copy(email = "anna@example")

        val issues = issuer.checkData(applicant = applicant, processingStartedAt = processingStartedAt)

        assertEquals(expected = setOf(element = DataIssue.InvalidEmail), actual = issues)
    }

    @Test
    fun `given an applicant who turns 18 the day after the processing date, when the data is checked, then they are under 18`() = runTest {
        val applicant = adultApplicant.copy(dateOfBirth = LocalDate.of(2008, 11, 9))

        val issues = issuer.checkData(applicant = applicant, processingStartedAt = processingStartedAt)

        assertEquals(expected = setOf(element = DataIssue.Under18), actual = issues)
    }

    @Test
    fun `given processing started just after midnight in the issuer's time zone, when the data is checked, then the issuer's date counts, not UTC's`() = runTest {
        // 23:30 UTC on 8 November is 00:30 on 9 November in Brussels: the applicant's 18th birthday.
        val justAfterMidnightInBrussels = Instant.parse(input = "2026-11-08T23:30:00Z")
        val applicant = adultApplicant.copy(dateOfBirth = LocalDate.of(2008, 11, 9))
        val issuerInBrussels = SimulatedIssuer(config = IssuerConfig(timeZone = ZoneId.of("Europe/Brussels")))
        val issuerInUtc = SimulatedIssuer(config = IssuerConfig(timeZone = ZoneOffset.UTC))

        val issuesInBrussels = issuerInBrussels.checkData(applicant = applicant, processingStartedAt = justAfterMidnightInBrussels)
        val issuesInUtc = issuerInUtc.checkData(applicant = applicant, processingStartedAt = justAfterMidnightInBrussels)

        assertEquals(expected = emptySet(), actual = issuesInBrussels)
        assertEquals(expected = setOf(element = DataIssue.Under18), actual = issuesInUtc)
    }

    @Test
    fun `given an applicant born on 29 February, when processing is on 28 February of a non-leap year, then they are under 18 until 1 March`() = runTest {
        val applicant = adultApplicant.copy(dateOfBirth = LocalDate.of(2008, 2, 29))

        val issuesOn28February = issuer.checkData(applicant = applicant, processingStartedAt = Instant.parse(input = "2026-02-28T12:00:00Z"))
        val issuesOn1March = issuer.checkData(applicant = applicant, processingStartedAt = Instant.parse(input = "2026-03-01T12:00:00Z"))

        assertEquals(expected = setOf(element = DataIssue.Under18), actual = issuesOn28February)
        assertEquals(expected = emptySet(), actual = issuesOn1March)
    }
}
