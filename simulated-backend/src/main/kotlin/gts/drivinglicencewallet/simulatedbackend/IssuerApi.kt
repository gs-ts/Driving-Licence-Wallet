package gts.drivinglicencewallet.simulatedbackend

import java.time.LocalDate

/**
 * The applicant's details, as the app sends them.
 *
 * Blank text, or no date of birth, counts as missing: the issuer never trusts the app.
 */
data class Applicant(
    val firstName: String,
    val lastName: String,
    val dateOfBirth: LocalDate?,
    val nationalId: String,
    val email: String,
    val phoneNumber: String,
    val address: String,
    /** A category code from the EU directive on driving licences, such as "B". */
    val licenceCategory: String,
)

/** Why the data check fails. */
enum class DataIssue {

    MissingFirstName,
    MissingLastName,
    MissingDateOfBirth,
    MissingNationalId,
    MissingEmail,
    MissingPhoneNumber,
    MissingAddress,
    MissingLicenceCategory,
    InvalidEmail,

    /** Younger than 18 on the processing date. */
    Under18,
}

/** Why the photo check fails. */
enum class PhotoIssue {

    Missing,

    /** Not a JPEG, or a JPEG whose size in pixels can't be read. */
    UnsupportedType,

    /** The file is smaller than the minimum. */
    TooSmall,

    /** The quality score is under the minimum. */
    LowQuality,
}

/** Why risk assessment sends the application to a reviewer. */
enum class RiskFlag {

    /** Younger than 21 on the processing date. */
    Under21,

    CategoryNotB,

    /** The national ID is on the watchlist. */
    OnWatchlist,

    /** The quality score is under the review threshold. */
    LowPhotoQuality,
}
