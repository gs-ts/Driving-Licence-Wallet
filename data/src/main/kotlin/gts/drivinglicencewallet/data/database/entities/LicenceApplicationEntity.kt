package gts.drivinglicencewallet.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import gts.drivinglicencewallet.domain.applicant.Address
import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.applicant.Email
import gts.drivinglicencewallet.domain.applicant.LicenceCategory
import gts.drivinglicencewallet.domain.applicant.NationalId
import gts.drivinglicencewallet.domain.applicant.PersonName
import gts.drivinglicencewallet.domain.applicant.PhoneNumber
import gts.drivinglicencewallet.domain.application.LicenceApplication
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * A saved application: the applicant's details and the photo reference, as text.
 *
 * There's no status column. The status comes from the history, so the two can't disagree.
 */
@Entity(tableName = "applications")
internal data class LicenceApplicationEntity(
    @PrimaryKey val id: String,
    val firstName: String,
    val lastName: String,
    /** ISO 8601, such as 1990-05-17. */
    val dateOfBirth: String,
    val nationalId: String,
    val email: String,
    val phoneNumber: String,
    val address: String,
    /** The licence category's name, such as B. */
    val licenceCategory: String,
    val photoReference: String,
)

internal fun LicenceApplication.toEntity(): LicenceApplicationEntity = LicenceApplicationEntity(
    id = id.value,
    firstName = details.firstName.text,
    lastName = details.lastName.text,
    dateOfBirth = details.dateOfBirth.toString(),
    nationalId = details.nationalId.text,
    email = details.email.text,
    phoneNumber = details.phoneNumber.text,
    address = details.address.text,
    licenceCategory = details.licenceCategory.name,
    photoReference = photo.value
)

/** The applicant's details as saved, or null if one of them doesn't pass its check. */
internal fun LicenceApplicationEntity.toDetailsOrNull(): ApplicantDetails? = ApplicantDetails(
    firstName = PersonName.parse(text = firstName) ?: return null,
    lastName = PersonName.parse(text = lastName) ?: return null,
    dateOfBirth = dateOrNull(text = dateOfBirth) ?: return null,
    nationalId = NationalId.parse(text = nationalId) ?: return null,
    email = Email.parse(text = email) ?: return null,
    phoneNumber = PhoneNumber.parse(text = phoneNumber) ?: return null,
    address = Address.parse(text = address) ?: return null,
    licenceCategory = enumOrNull<LicenceCategory>(name = licenceCategory) ?: return null
)

/** The exception isn't logged: its message holds the text, which is the date of birth. */
private fun dateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (ignored: DateTimeParseException) {
    null
}
