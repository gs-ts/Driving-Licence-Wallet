package gts.drivinglicencewallet.ui.submit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import gts.drivinglicencewallet.di.ApplicationScope
import gts.drivinglicencewallet.domain.applicant.ApplicationForm
import gts.drivinglicencewallet.domain.applicant.FormCheck
import gts.drivinglicencewallet.domain.applicant.FormProblem
import gts.drivinglicencewallet.domain.applicant.LicenceCategory
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.SubmitApplicationUseCase
import gts.drivinglicencewallet.domain.application.SubmitResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The form for a new licence application. It holds what the applicant enters,
 * shows what is missing or invalid, and makes sure that the form is submitted
 * only once.
 *
 * All of its state is Compose state, so the screen and [problems] are updated
 * as the applicant types. Each text field is a [TextFieldState], which the
 * Compose documentation recommends holding in the ViewModel. The form survives
 * a rotation, but it is deliberately not restored after process death.
 */
@HiltViewModel
class SubmitApplicationViewModel @Inject constructor(
    private val submitApplication: SubmitApplicationUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    val firstName = TextFieldState()
    val lastName = TextFieldState()
    val nationalId = TextFieldState()
    val email = TextFieldState()
    val phoneNumber = TextFieldState()
    val address = TextFieldState()

    var dateOfBirth: LocalDate? by mutableStateOf(value = null)
        private set

    var licenceCategory: LicenceCategory? by mutableStateOf(value = null)
        private set

    var photo: PhotoSource? by mutableStateOf(value = null)
        private set

    var submission: Submission by mutableStateOf(value = Submission.NotStarted)
        private set

    /**
     * False until the first tap on Submit, so that a form the applicant has not
     * filled in yet shows no errors.
     */
    private var shouldShowProblems by mutableStateOf(value = false)

    /**
     * The reasons why the form cannot be submitted. It is empty until the
     * first tap on Submit, and then it changes as the applicant edits the form.
     */
    val problems: Set<FormProblem> by derivedStateOf {
        val check = form().check()
        if (shouldShowProblems && check is FormCheck.Failed) check.problems else emptySet()
    }

    /**
     * False while a submit is running and after one has succeeded, so that
     * another tap on Submit cannot start a second one.
     */
    private val isSubmitAllowed: Boolean
        get() = submission !is Submission.InProgress && submission !is Submission.Succeeded

    fun onAction(action: SubmitAction) {
        when (action) {
            is SubmitAction.DateOfBirthChosen -> dateOfBirth = action.date
            is SubmitAction.LicenceCategoryChosen -> licenceCategory = action.category
            is SubmitAction.PhotoChosen -> photo = action.photo
            SubmitAction.SubmitClicked -> submit()
            SubmitAction.MessageShown -> dismissRefusal()
        }
    }

    /**
     * Checks the form with the domain's rules. If the check passed and
     * [isSubmitAllowed] is true, it submits the form.
     *
     * The use case checks that no other application is in progress before it
     * saves the new one, so two submits running at the same time could both
     * pass that check. To prevent this, [submission] changes on the main
     * thread before anything suspends, so a second tap is ignored, even in the
     * same frame.
     */
    private fun submit() {
        shouldShowProblems = true
        val check = form().check()

        if (isSubmitAllowed && check is FormCheck.Passed) {
            submission = Submission.InProgress
            viewModelScope.launch {
                // The submit runs in the application's scope, so that leaving
                // the screen cannot cancel it after the application is saved
                // but before its processing is scheduled.
                val result = applicationScope.async {
                    submitApplication(details = check.details, photo = check.photo)
                }.await()
                submission = result.toSubmission()
            }
        }
    }

    private fun form(): ApplicationForm = ApplicationForm(
        firstName = firstName.text.toString(),
        lastName = lastName.text.toString(),
        dateOfBirth = dateOfBirth,
        nationalId = nationalId.text.toString(),
        email = email.text.toString(),
        phoneNumber = phoneNumber.text.toString(),
        address = address.text.toString(),
        licenceCategory = licenceCategory,
        photo = photo
    )

    private fun SubmitResult.toSubmission(): Submission = when (this) {
        is SubmitResult.Submitted -> Submission.Succeeded(applicationId = application.id)
        is SubmitResult.AnotherInProgress -> Submission.Refused.AnotherInProgress
        SubmitResult.PhotoNotSaved -> Submission.Refused.PhotoNotSaved
    }

    private fun dismissRefusal() {
        if (submission is Submission.Refused) {
            submission = Submission.NotStarted
        }
    }
}

/**
 * What the applicant does on the form, apart from typing. Typing needs no
 * action, because the text fields update their [TextFieldState] directly.
 */
sealed interface SubmitAction {

    data class DateOfBirthChosen(val date: LocalDate) : SubmitAction

    data class LicenceCategoryChosen(val category: LicenceCategory) : SubmitAction

    data class PhotoChosen(val photo: PhotoSource) : SubmitAction

    data object SubmitClicked : SubmitAction

    /**
     * The screen has shown the message for a [Submission.Refused], so it is
     * not shown again after a rotation.
     */
    data object MessageShown : SubmitAction
}

/** The progress and the result of submitting the form. */
sealed interface Submission {

    data object NotStarted : Submission

    data object InProgress : Submission

    /**
     * The application is saved and its processing is scheduled. The screen
     * then navigates to the application's status screen.
     */
    data class Succeeded(val applicationId: ApplicationId) : Submission

    /**
     * The submit was refused. The screen tells the applicant why, and then
     * reports [SubmitAction.MessageShown].
     */
    sealed interface Refused : Submission {

        /**
         * Another application is still in progress, and only one can be at a
         * time. The app does not open the form while one is in progress, so
         * this is only a safety net.
         */
        data object AnotherInProgress : Refused

        /** The photo could not be read or saved. */
        data object PhotoNotSaved : Refused
    }
}
