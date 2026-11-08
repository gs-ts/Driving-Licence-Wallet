package gts.drivinglicencewallet.simulatedbackend

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoCheckTest {

    private val issuer = SimulatedIssuer(config = IssuerConfig())

    @Test
    fun `given no photo, when the photo is checked, then it's missing`() = runTest {
        val issues = issuer.checkPhoto(photo = null)

        assertEquals(expected = setOf(element = PhotoIssue.Missing), actual = issues)
    }

    @Test
    fun `given a PNG, when the photo is checked, then its type is unsupported`() = runTest {
        val issues = issuer.checkPhoto(photo = png())

        assertEquals(expected = setOf(element = PhotoIssue.UnsupportedType), actual = issues)
    }

    @Test
    fun `given a JPEG under the minimum size, when the photo is checked, then it's too small`() = runTest {
        val issues = issuer.checkPhoto(photo = jpeg(width = 3000, height = 4000, fileBytes = 10_000))

        assertEquals(expected = setOf(element = PhotoIssue.TooSmall), actual = issues)
    }

    @Test
    fun `given a JPEG with a quality score under the minimum, when the photo is checked, then its quality is too low`() = runTest {
        // The shorter side is 300 pixels, so the score is 25, under the minimum of 40.
        val issues = issuer.checkPhoto(photo = jpeg(width = 300, height = 400))

        assertEquals(expected = setOf(element = PhotoIssue.LowQuality), actual = issues)
    }
}
