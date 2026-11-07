package gts.drivinglicencewallet.domain.applicant

import java.time.LocalDate

/**
 * The application form as the applicant filled it in, before any checks.
 *
 * The app keeps it while the applicant types, and [check] says whether it can be submitted.
 */
data class ApplicationForm(
    val firstName: String = "",
    val lastName: String = "",
    val dateOfBirth: LocalDate? = null,
    val nationalId: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val licenceCategory: LicenceCategory? = null,
    val photo: PhotoSource? = null,
) {

    /**
     * Runs the app's checks: every field and the photo are required, names need a letter, and the
     * email needs the usual shape. The issuer checks more while processing, such as the minimum age.
     */
    fun check(): FormCheck {
        val problems = findProblems()
        if (problems.isNotEmpty()) return FormCheck.Failed(problems = problems)
        return FormCheck.Passed(details = toDetails(), photo = checkNotNull(value = photo))
    }

    private fun findProblems(): Set<FormProblem> = buildSet {
        for (field in ApplicantField.entries) {
            if (isMissing(field = field)) add(element = FormProblem.MissingField(field = field))
        }
        if (hasNoLetters(name = firstName)) add(element = FormProblem.NameWithoutLetters(field = ApplicantField.FirstName))
        if (hasNoLetters(name = lastName)) add(element = FormProblem.NameWithoutLetters(field = ApplicantField.LastName))
        if (email.isNotBlank() && Email.parse(text = email) == null) add(element = FormProblem.InvalidEmail)
        if (photo == null) add(element = FormProblem.MissingPhoto)
    }

    /** Every field is required. Blank text counts as missing. */
    private fun isMissing(field: ApplicantField): Boolean = when (field) {
        ApplicantField.FirstName -> firstName.isBlank()
        ApplicantField.LastName -> lastName.isBlank()
        ApplicantField.DateOfBirth -> dateOfBirth == null
        ApplicantField.NationalId -> nationalId.isBlank()
        ApplicantField.Email -> email.isBlank()
        ApplicantField.PhoneNumber -> phoneNumber.isBlank()
        ApplicantField.Address -> address.isBlank()
        ApplicantField.LicenceCategory -> licenceCategory == null
    }

    /** A name that is filled in but has no letter, such as "&". */
    private fun hasNoLetters(name: String): Boolean = name.isNotBlank() && PersonName.parse(text = name) == null

    /** Only called when [findProblems] found nothing, so every value is there and valid. */
    private fun toDetails(): ApplicantDetails = ApplicantDetails(
        firstName = checkNotNull(value = PersonName.parse(text = firstName)),
        lastName = checkNotNull(value = PersonName.parse(text = lastName)),
        dateOfBirth = checkNotNull(value = dateOfBirth),
        nationalId = checkNotNull(value = NationalId.parse(text = nationalId)),
        email = checkNotNull(value = Email.parse(text = email)),
        phoneNumber = checkNotNull(value = PhoneNumber.parse(text = phoneNumber)),
        address = checkNotNull(value = Address.parse(text = address)),
        licenceCategory = checkNotNull(value = licenceCategory)
    )
}

/** The result of [ApplicationForm.check]. */
sealed interface FormCheck {

    /** The form can be submitted: the details are checked, and a photo is picked. */
    data class Passed(val details: ApplicantDetails, val photo: PhotoSource) : FormCheck

    /** The form can't be submitted yet. It lists every problem, so the app can show them all at once. */
    data class Failed(val problems: Set<FormProblem>) : FormCheck {

        init {
            require(value = problems.isNotEmpty()) { "A failed form check needs at least one problem." }
        }
    }
}

/** Why the application form can't be submitted yet. The app shows each problem next to its field. */
sealed interface FormProblem {

    data class MissingField(val field: ApplicantField) : FormProblem

    /** A first or last name with no letter in it, such as "&" or "123". */
    data class NameWithoutLetters(val field: ApplicantField) : FormProblem

    /** The email doesn't have the shape of an email address. */
    data object InvalidEmail : FormProblem

    data object MissingPhoto : FormProblem
}

/** A field of the application form. */
enum class ApplicantField {

    FirstName,
    LastName,
    DateOfBirth,
    NationalId,
    Email,
    PhoneNumber,
    Address,
    LicenceCategory,
}

/**
 * The photo the applicant picked, before the app stores it.
 *
 * [value] tells the platform where to read it from, such as a content URI.
 */
@JvmInline
value class PhotoSource(val value: String)
