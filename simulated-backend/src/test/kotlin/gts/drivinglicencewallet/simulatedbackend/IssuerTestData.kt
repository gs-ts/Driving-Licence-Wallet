package gts.drivinglicencewallet.simulatedbackend

import java.time.LocalDate
import kotlin.time.Instant

/** When processing started in most tests: 10:00 in Brussels. */
val processingStartedAt = Instant.parse(input = "2026-11-08T09:00:00Z")

/** An applicant who passes every check: an adult with category B, not on the default watchlist. */
val adultApplicant = Applicant(
    firstName = "Anna",
    lastName = "Svensson",
    dateOfBirth = LocalDate.of(1990, 5, 17),
    nationalId = "1234567890",
    email = "anna@example.com",
    phoneNumber = "+46 70 123 45 67",
    address = "Drottninggatan 1, 111 51 Stockholm",
    licenceCategory = "B",
)

/** Big enough to pass the size check. */
const val LARGE_FILE_BYTES = 100_000

/**
 * A JPEG of [width] × [height] pixels, [fileBytes] long. Only the headers the issuer reads are real:
 * the start-of-image marker, an APP0 segment to skip, and a baseline frame header.
 */
fun jpeg(width: Int, height: Int, fileBytes: Int = LARGE_FILE_BYTES): ByteArray {
    val headers = bytes(
        0xFF, 0xD8, // start of image
        0xFF, 0xE0, 0x00, 0x04, 0x00, 0x00, // an APP0 segment with 2 bytes of data
        0xFF, 0xC0, 0x00, 0x0B, 0x08, // a baseline frame header, 11 bytes long, with 8-bit samples
        height / 256, height % 256, width / 256, width % 256,
        0x01, 0x01, 0x11, 0x00, // one colour component
    )
    return headers + ByteArray(size = fileBytes - headers.size)
}

/** A PNG, [fileBytes] long. Only its signature is real. */
fun png(fileBytes: Int = LARGE_FILE_BYTES): ByteArray {
    val signature = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    return signature + ByteArray(size = fileBytes - signature.size)
}

private fun bytes(vararg values: Int): ByteArray = ByteArray(size = values.size) { index -> values[index].toByte() }
