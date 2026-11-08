package gts.drivinglicencewallet.simulatedbackend

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class SimulatedIssuerTest {

    private val photo = jpeg(width = 3000, height = 4000)

    @Test
    fun `given the configured delay, when each step is called, then each answer comes after the delay, on virtual time`() = runTest {
        val issuer = SimulatedIssuer(config = IssuerConfig(responseDelay = 2.seconds))

        val elapsed = testScheduler.timeSource.measureTime {
            issuer.checkData(applicant = adultApplicant, processingStartedAt = processingStartedAt)
            issuer.checkPhoto(photo = photo)
            issuer.assessRisk(applicant = adultApplicant, photo = photo, processingStartedAt = processingStartedAt)
        }

        assertEquals(expected = 6.seconds, actual = elapsed)
    }

    @Test
    fun `given the issuer is unreachable, when a step is called, then it fails with an IOException, and it answers again once reachable`() = runTest {
        val issuer = SimulatedIssuer(config = IssuerConfig())

        issuer.isUnreachable = true
        assertFailsWith<IOException> { issuer.checkData(applicant = adultApplicant, processingStartedAt = processingStartedAt) }
        assertFailsWith<IOException> { issuer.checkPhoto(photo = photo) }
        assertFailsWith<IOException> { issuer.assessRisk(applicant = adultApplicant, photo = photo, processingStartedAt = processingStartedAt) }
        issuer.isUnreachable = false

        assertEquals(expected = emptySet(), actual = issuer.checkPhoto(photo = photo))
    }
}
