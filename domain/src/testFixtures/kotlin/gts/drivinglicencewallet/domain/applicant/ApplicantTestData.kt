package gts.drivinglicencewallet.domain.applicant

import java.time.LocalDate

/** A form that passes every check. Tests change only the fields they test. */
val completeForm = ApplicationForm(
    firstName = "Anna",
    lastName = "Svensson",
    dateOfBirth = LocalDate.of(1990, 5, 17),
    nationalId = "1234567890",
    email = "anna@example.com",
    phoneNumber = "+46 70 123 45 67",
    address = "Drottninggatan 1, 111 51 Stockholm",
    licenceCategory = LicenceCategory.B,
    photo = PhotoSource("content://photos/1"),
)

/** The details of [completeForm] after its check has passed. */
fun completeDetails(): ApplicantDetails = when (val check = completeForm.check()) {
    is FormCheck.Passed -> check.details
    is FormCheck.Failed -> error(message = "The complete form failed its check: ${check.problems}")
}
