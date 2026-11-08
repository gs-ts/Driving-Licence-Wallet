package gts.drivinglicencewallet.simulatedbackend

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RiskAssessmentTest {

    private val issuer = SimulatedIssuer(config = IssuerConfig())
    private val sharpPhoto = jpeg(width = 3000, height = 4000)

    @Test
    fun `given an applicant who turns 21 the day after the processing date, when risk is assessed, then they are flagged as under 21`() = runTest {
        val applicant = adultApplicant.copy(dateOfBirth = LocalDate.of(2005, 11, 9))

        val flags = issuer.assessRisk(applicant = applicant, photo = sharpPhoto, processingStartedAt = processingStartedAt)

        assertEquals(expected = setOf(element = RiskFlag.Under21), actual = flags)
    }

    @Test
    fun `given a category other than B, when risk is assessed, then it's flagged`() = runTest {
        val applicant = adultApplicant.copy(licenceCategory = "C")

        val flags = issuer.assessRisk(applicant = applicant, photo = sharpPhoto, processingStartedAt = processingStartedAt)

        assertEquals(expected = setOf(element = RiskFlag.CategoryNotB), actual = flags)
    }

    @Test
    fun `given a national ID on the watchlist in different letter case, when risk is assessed, then it's flagged`() = runTest {
        val issuer = SimulatedIssuer(config = IssuerConfig(watchlist = setOf(element = "AB123456")))
        val applicant = adultApplicant.copy(nationalId = "ab123456")

        val flags = issuer.assessRisk(applicant = applicant, photo = sharpPhoto, processingStartedAt = processingStartedAt)

        assertEquals(expected = setOf(element = RiskFlag.OnWatchlist), actual = flags)
    }

    @Test
    fun `given a photo whose quality score is under the configured review threshold, when risk is assessed, then it's flagged`() = runTest {
        // The shorter side is 900 pixels, so the score is 75: enough for the default threshold of 70, but not for 80.
        val issuer = SimulatedIssuer(config = IssuerConfig(reviewQualityScore = 80))
        val photo = jpeg(width = 900, height = 1200)

        val flags = issuer.assessRisk(applicant = adultApplicant, photo = photo, processingStartedAt = processingStartedAt)

        assertEquals(expected = setOf(element = RiskFlag.LowPhotoQuality), actual = flags)
    }
}
