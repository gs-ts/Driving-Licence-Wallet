package gts.drivinglicencewallet.domain.applicant

import java.time.LocalDate
import kotlin.test.assertIs

/** A form that passes every check. Tests change only the fields they are about. */
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

/** The checked details of [completeForm]. */
fun completeDetails(): ApplicantDetails = assertIs<FormCheck.Passed>(completeForm.check()).details
