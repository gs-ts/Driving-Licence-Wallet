package gts.drivinglicencewallet.simulatedbackend

import kotlinx.coroutines.delay
import java.io.IOException
import java.time.LocalDate
import java.time.Period
import kotlin.concurrent.Volatile
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * The issuer's server, simulated inside the app. It decides each processing step with the brief's rules.
 *
 * Each call waits [IssuerConfig.responseDelay], like a real network. The answers depend only on
 * what the call sends, so asking twice gives the same answer.
 */
class SimulatedIssuer(private val config: IssuerConfig) {

    /** While true, every call fails with an [IOException], like a dropped connection. For the network switch on the status screen. */
    @Volatile
    var isUnreachable: Boolean = false

    /** The data's issues; none means the data check passes. Ages are measured on the issuer's date at [processingStartedAt]. */
    suspend fun checkData(applicant: Applicant, processingStartedAt: Instant): Set<DataIssue> {
        waitForResponse()
        val age = ageOn(instant = processingStartedAt, dateOfBirth = applicant.dateOfBirth)
        return buildSet {
            addAll(elements = missingFields(applicant = applicant))
            if (applicant.email.isNotBlank() && !EMAIL_SHAPE.matches(input = applicant.email.trim())) add(element = DataIssue.InvalidEmail)
            if (age != null && age < MINIMUM_AGE) add(element = DataIssue.Under18)
        }
    }

    /** The photo's issues; none means the photo check passes. [photo] is the file's bytes, or null if there's no photo. */
    suspend fun checkPhoto(photo: ByteArray?): Set<PhotoIssue> {
        waitForResponse()
        if (photo == null) return setOf(element = PhotoIssue.Missing)
        val size = PixelSize.fromJpeg(bytes = photo)
        return buildSet {
            if (size == null) add(element = PhotoIssue.UnsupportedType)
            if (photo.size < config.minPhotoBytes) add(element = PhotoIssue.TooSmall)
            if (size != null && size.qualityScore < config.minQualityScore) add(element = PhotoIssue.LowQuality)
        }
    }

    /**
     * The reasons to send the application to a reviewer; none means automatic approval.
     * An unknown age or an unreadable photo goes to a reviewer too, although the earlier checks fail them first.
     */
    suspend fun assessRisk(
        applicant: Applicant,
        photo: ByteArray?,
        processingStartedAt: Instant,
    ): Set<RiskFlag> {
        waitForResponse()
        val age = ageOn(instant = processingStartedAt, dateOfBirth = applicant.dateOfBirth)
        val qualityScore = photo?.let { bytes -> PixelSize.fromJpeg(bytes = bytes) }?.qualityScore ?: 0
        return buildSet {
            if (age == null || age < AGE_FOR_AUTOMATIC_APPROVAL) add(element = RiskFlag.Under21)
            if (applicant.licenceCategory.trim() != CAR_CATEGORY) add(element = RiskFlag.CategoryNotB)
            if (isOnWatchlist(nationalId = applicant.nationalId)) add(element = RiskFlag.OnWatchlist)
            if (qualityScore < config.reviewQualityScore) add(element = RiskFlag.LowPhotoQuality)
        }
    }

    /** Waits like a network call, then fails if the issuer is unreachable. */
    private suspend fun waitForResponse() {
        delay(duration = config.responseDelay)
        if (isUnreachable) throw IOException("The issuer is unreachable.")
    }

    private fun missingFields(applicant: Applicant): Set<DataIssue> = buildSet {
        if (applicant.firstName.isBlank()) add(element = DataIssue.MissingFirstName)
        if (applicant.lastName.isBlank()) add(element = DataIssue.MissingLastName)
        if (applicant.dateOfBirth == null) add(element = DataIssue.MissingDateOfBirth)
        if (applicant.nationalId.isBlank()) add(element = DataIssue.MissingNationalId)
        if (applicant.email.isBlank()) add(element = DataIssue.MissingEmail)
        if (applicant.phoneNumber.isBlank()) add(element = DataIssue.MissingPhoneNumber)
        if (applicant.address.isBlank()) add(element = DataIssue.MissingAddress)
        if (applicant.licenceCategory.isBlank()) add(element = DataIssue.MissingLicenceCategory)
    }

    /**
     * The age in whole years on the issuer's local date at [instant], or null without a date of birth.
     * Someone born on 29 February turns a year older on 1 March in other years, so no one passes a day early.
     */
    private fun ageOn(instant: Instant, dateOfBirth: LocalDate?): Int? {
        val date = instant
            .toJavaInstant()
            .atZone(config.timeZone)
            .toLocalDate()
        return dateOfBirth?.let { birthDate -> Period.between(birthDate, date).years }
    }

    /** Ignores surrounding spaces, and letter case too, because some national IDs contain letters. */
    private fun isOnWatchlist(nationalId: String): Boolean =
        config.watchlist.any { listedId -> listedId.trim().equals(other = nationalId.trim(), ignoreCase = true) }
}

/** 100 when the shorter side has [FULL_QUALITY_PIXELS] or more, and proportionally less below that. */
private val PixelSize.qualityScore: Int
    get() = (minOf(a = width, b = height) * MAX_QUALITY_SCORE / FULL_QUALITY_PIXELS).coerceAtMost(maximumValue = MAX_QUALITY_SCORE)

/** Younger than this fails the data check. */
private const val MINIMUM_AGE = 18

/** Younger than this goes to a reviewer. */
private const val AGE_FOR_AUTOMATIC_APPROVAL = 21
private const val CAR_CATEGORY = "B"
private const val MAX_QUALITY_SCORE = 100
private const val FULL_QUALITY_PIXELS = 1200

/** The same simple shape as the app's check: one @, no spaces, and a dot after the @. */
private val EMAIL_SHAPE = Regex(pattern = """[^@\s]+@[^@\s]+\.[^@\s]+""")
