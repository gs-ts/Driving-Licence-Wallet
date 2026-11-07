package gts.drivinglicencewallet.domain.applicant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApplicationFormTest {

    @Test
    fun `given a complete form, when it is checked, then it passes with the values trimmed`() {
        val form = completeForm.copy(
            firstName = " Anna ",
            lastName = " Svensson ",
            nationalId = " 1234567890 ",
            email = " anna@example.com ",
            phoneNumber = " +46 70 123 45 67 ",
            address = " Drottninggatan 1, 111 51 Stockholm ",
        )

        val passed = assertIs<FormCheck.Passed>(form.check())

        with(passed.details) {
            assertEquals("Anna", firstName.text)
            assertEquals("Svensson", lastName.text)
            assertEquals(completeForm.dateOfBirth, dateOfBirth)
            assertEquals("1234567890", nationalId.text)
            assertEquals("anna@example.com", email.text)
            assertEquals("+46 70 123 45 67", phoneNumber.text)
            assertEquals("Drottninggatan 1, 111 51 Stockholm", address.text)
            assertEquals(LicenceCategory.B, licenceCategory)
        }
        assertEquals(completeForm.photo, passed.photo)
    }

    @Test
    fun `given a blank form without a photo, when it is checked, then every field and the photo are reported missing`() {
        val form = ApplicationForm(firstName = " ", lastName = " ", nationalId = " ", email = " ", phoneNumber = " ", address = " ")

        val failed = assertIs<FormCheck.Failed>(form.check())

        val missingFields = ApplicantField.entries.map { field -> FormProblem.MissingField(field) }
        assertEquals(missingFields.toSet() + FormProblem.MissingPhoto, failed.problems)
    }

    @Test
    fun `given names without a letter, when the form is checked, then both names are reported`() {
        val form = completeForm.copy(firstName = "&", lastName = "123")

        val failed = assertIs<FormCheck.Failed>(form.check())

        val expected = setOf(
            FormProblem.NameWithoutLetters(ApplicantField.FirstName),
            FormProblem.NameWithoutLetters(ApplicantField.LastName),
        )
        assertEquals(expected, failed.problems)
    }

    @Test
    fun `given names with accents, apostrophes, hyphens, one letter or a non-Latin script, when the form is checked, then they pass`() {
        val names = listOf("Zoë", "O'Brien", "Jean-Luc", "Ó", "Γιάννης", "Мария")

        names.forEach { name ->
            assertIs<FormCheck.Passed>(completeForm.copy(firstName = name, lastName = name).check(), message = name)
        }
    }

    @Test
    fun `given malformed emails, when the form is checked, then each one is reported invalid`() {
        val emails = listOf("anna", "anna@", "@example.com", "anna@example", "anna@.com", "anna @example.com", "anna@@example.com")

        emails.forEach { email ->
            val failed = assertIs<FormCheck.Failed>(completeForm.copy(email = email).check(), message = email)
            assertEquals(setOf(FormProblem.InvalidEmail), failed.problems, message = email)
        }
    }

    @Test
    fun `given common email shapes, when the form is checked, then they pass`() {
        val emails = listOf("anna.svensson@example.com", "anna+licence@mail.example.co.uk", "ANNA@EXAMPLE.SE")

        emails.forEach { email ->
            assertIs<FormCheck.Passed>(completeForm.copy(email = email).check(), message = email)
        }
    }
}
