package gts.drivinglicencewallet.domain.applicant

import java.time.LocalDate

/**
 * The applicant's details. Every value has a checked type, so the details are always complete.
 *
 * [ApplicationForm.check] creates them from what the applicant typed.
 */
data class ApplicantDetails(
    val firstName: PersonName,
    val lastName: PersonName,
    val dateOfBirth: LocalDate,
    val nationalId: NationalId,
    val email: Email,
    val phoneNumber: PhoneNumber,
    val address: Address,
    val licenceCategory: LicenceCategory,
)

/**
 * A first or last name. It contains at least one letter, from any alphabet.
 *
 * Nothing stricter on purpose: real names can be one letter long and can have apostrophes,
 * hyphens and accents.
 */
@JvmInline
value class PersonName private constructor(val text: String) {

    companion object {

        /** Returns null when [text] has no letter. Removes surrounding spaces. */
        fun parse(text: String): PersonName? {
            val trimmedText = text.trim()
            return if (trimmedText.any { character -> character.isLetter() }) PersonName(text = trimmedText) else null
        }
    }
}

/**
 * The applicant's national identity number. Formats differ between countries, so it only has to be filled in.
 *
 * It's sensitive: never log it.
 */
@JvmInline
value class NationalId private constructor(val text: String) {

    companion object {

        /** Returns null when [text] is blank. Removes surrounding spaces. */
        fun parse(text: String): NationalId? {
            val trimmedText = text.trim()
            return if (trimmedText.isEmpty()) null else NationalId(text = trimmedText)
        }
    }
}

/**
 * An email address with the usual shape: one @, no spaces, and a dot after the @.
 *
 * The check is simple on purpose. Only sending an email proves that the address works.
 */
@JvmInline
value class Email private constructor(val text: String) {

    companion object {

        private val SHAPE = Regex(pattern = """[^@\s]+@[^@\s]+\.[^@\s]+""")

        /** Returns null when [text] doesn't have the shape of an email address. Removes surrounding spaces. */
        fun parse(text: String): Email? {
            val trimmedText = text.trim()
            return if (SHAPE.matches(input = trimmedText)) Email(text = trimmedText) else null
        }
    }
}

/** The applicant's phone number. Formats differ between countries, so it only has to be filled in. */
@JvmInline
value class PhoneNumber private constructor(val text: String) {

    companion object {

        /** Returns null when [text] is blank. Removes surrounding spaces. */
        fun parse(text: String): PhoneNumber? {
            val trimmedText = text.trim()
            return if (trimmedText.isEmpty()) null else PhoneNumber(text = trimmedText)
        }
    }
}

/** The applicant's postal address. Formats differ between countries, so it only has to be filled in. */
@JvmInline
value class Address private constructor(val text: String) {

    companion object {

        /** Returns null when [text] is blank. Removes surrounding spaces. */
        fun parse(text: String): Address? {
            val trimmedText = text.trim()
            return if (trimmedText.isEmpty()) null else Address(text = trimmedText)
        }
    }
}

/** A driving licence category from the EU directive on driving licences (2006/126/EC), such as B for cars. */
enum class LicenceCategory {

    AM,
    A1,
    A2,
    A,
    B1,
    B,
    BE,
    C1,
    C1E,
    C,
    CE,
    D1,
    D1E,
    D,
    DE,
}
