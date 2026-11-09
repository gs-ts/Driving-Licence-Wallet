package gts.drivinglicencewallet.ui.submit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.getSelectedDate
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import gts.drivinglicencewallet.R
import gts.drivinglicencewallet.domain.applicant.ApplicantField
import gts.drivinglicencewallet.domain.applicant.FormProblem
import gts.drivinglicencewallet.domain.applicant.LicenceCategory
import gts.drivinglicencewallet.domain.applicant.PhotoSource
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.ui.theme.DrivingLicenceWalletTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The form for a new licence application. Once the application is saved, the
 * screen calls [onSubmitSuccess] with its ID.
 */
@Composable
fun SubmitApplicationScreen(
    onSubmitSuccess: (ApplicationId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubmitApplicationViewModel = hiltViewModel(),
) {
    val submission = viewModel.submission
    val currentOnSubmitSuccess by rememberUpdatedState(newValue = onSubmitSuccess)
    // The effect runs once for each new submission, as in the architecture
    // guide's example for ViewModel events. A SideEffect would run after every
    // recomposition, so it could navigate more than once.
    @Suppress("UnnecessaryLaunchedEffect")
    LaunchedEffect(key1 = submission) {
        if (submission is Submission.Succeeded) {
            currentOnSubmitSuccess(submission.applicationId)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onAction(action = SubmitAction.PhotoChosen(photo = PhotoSource(value = uri.toString())))
            }
        }
    )

    SubmitApplicationContent(
        firstName = viewModel.firstName,
        lastName = viewModel.lastName,
        dateOfBirth = viewModel.dateOfBirth,
        nationalId = viewModel.nationalId,
        email = viewModel.email,
        phoneNumber = viewModel.phoneNumber,
        address = viewModel.address,
        licenceCategory = viewModel.licenceCategory,
        photo = viewModel.photo,
        submission = submission,
        problems = viewModel.problems,
        onAction = viewModel::onAction,
        onChoosePhoto = {
            photoPicker.launch(
                input = PickVisualMediaRequest(
                    mediaType = ActivityResultContracts.PickVisualMedia.SingleMimeType(mimeType = "image/jpeg")
                )
            )
        },
        modifier = modifier
    )
}

@Composable
private fun SubmitApplicationContent(
    firstName: TextFieldState,
    lastName: TextFieldState,
    dateOfBirth: LocalDate?,
    nationalId: TextFieldState,
    email: TextFieldState,
    phoneNumber: TextFieldState,
    address: TextFieldState,
    licenceCategory: LicenceCategory?,
    photo: PhotoSource?,
    submission: Submission,
    problems: Set<FormProblem>,
    onAction: (SubmitAction) -> Unit,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val anotherInProgressMessage = stringResource(id = R.string.submit_another_in_progress)
    val photoNotSavedMessage = stringResource(id = R.string.submit_photo_not_saved)
    val currentOnAction by rememberUpdatedState(newValue = onAction)
    LaunchedEffect(key1 = submission) {
        val refusalMessage = when (submission) {
            Submission.Refused.AnotherInProgress -> anotherInProgressMessage
            Submission.Refused.PhotoNotSaved -> photoNotSavedMessage
            Submission.NotStarted, Submission.InProgress, is Submission.Succeeded -> null
        }

        if (refusalMessage != null) {
            snackbarHostState.showSnackbar(message = refusalMessage, duration = SnackbarDuration.Long)
            currentOnAction(SubmitAction.MessageShown)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.submit_title)) })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(paddingValues = innerPadding)
                .consumeWindowInsets(paddingValues = innerPadding)
                // Keeps the form above the keyboard, so the focused field can scroll into view.
                .fitInside(rulers = WindowInsetsRulers.Ime.current)
                .verticalScroll(state = rememberScrollState())
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            FormTextFieldView(
                state = firstName,
                label = R.string.field_first_name,
                problem = problems.findByField(field = ApplicantField.FirstName),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                )
            )
            FormTextFieldView(
                state = lastName,
                label = R.string.field_last_name,
                problem = problems.findByField(field = ApplicantField.LastName),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                )
            )
            DateOfBirthView(
                dateOfBirth = dateOfBirth,
                problem = problems.findByField(field = ApplicantField.DateOfBirth),
                onDateChange = { date -> onAction(SubmitAction.DateOfBirthChosen(date = date)) }
            )
            FormTextFieldView(
                state = nationalId,
                label = R.string.field_national_id,
                problem = problems.findByField(field = ApplicantField.NationalId),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                )
            )
            FormTextFieldView(
                state = email,
                label = R.string.field_email,
                problem = problems.findByField(field = ApplicantField.Email),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            FormTextFieldView(
                state = phoneNumber,
                label = R.string.field_phone_number,
                problem = problems.findByField(field = ApplicantField.PhoneNumber),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
            )
            FormTextFieldView(
                state = address,
                label = R.string.field_address,
                problem = problems.findByField(field = ApplicantField.Address),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                lineLimits = TextFieldLineLimits.MultiLine()
            )
            LicenceCategoryView(
                licenceCategory = licenceCategory,
                problem = problems.findByField(field = ApplicantField.LicenceCategory),
                onCategoryChange = { category -> onAction(SubmitAction.LicenceCategoryChosen(category = category)) }
            )
            PhotoView(
                photo = photo,
                problem = FormProblem.MissingPhoto.takeIf { problem -> problem in problems },
                onChoosePhoto = onChoosePhoto
            )
            Button(
                onClick = { onAction(SubmitAction.SubmitClicked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = submission !is Submission.InProgress
            ) {
                Text(text = stringResource(id = R.string.submit))
            }
        }
    }
}

/** A text field of the form, with its problem under it. */
@Composable
private fun FormTextFieldView(
    state: TextFieldState,
    @StringRes label: Int,
    problem: FormProblem?,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
) {
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = stringResource(id = label)) },
        supportingText = problem.toSupportingText(),
        isError = problem != null,
        keyboardOptions = keyboardOptions,
        lineLimits = lineLimits
    )
}

/** A read-only field for the date of birth. Tapping it opens a date picker. */
@Composable
private fun DateOfBirthView(
    dateOfBirth: LocalDate?,
    problem: FormProblem?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPickerOpen by rememberSaveable { mutableStateOf(value = false) }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    // The field only shows a value, so it takes the text directly rather than a TextFieldState, as the date picker documentation does.
    OutlinedTextField(
        value = dateOfBirth?.format(dateFormatter).orEmpty(),
        onValueChange = {},
        modifier = modifier
            .fillMaxWidth()
            .opensOnClick(
                label = stringResource(id = R.string.date_of_birth_choose),
                onOpen = { isPickerOpen = true }
            ),
        readOnly = true,
        label = { Text(text = stringResource(id = R.string.field_date_of_birth)) },
        supportingText = problem.toSupportingText(),
        isError = problem != null,
        singleLine = true
    )

    if (isPickerOpen) {
        DateOfBirthPickerView(
            initialDate = dateOfBirth,
            onDateChange = onDateChange,
            onDismissRequest = { isPickerOpen = false }
        )
    }
}

/**
 * The date picker for the date of birth. It opens in input mode, because
 * typing a date of birth is quicker than paging back through the years. It
 * does not allow dates in the future.
 */
@Composable
private fun DateOfBirthPickerView(
    initialDate: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pastDates = remember { PastDates(today = LocalDate.now()) }
    val state = rememberDatePickerState(
        initialSelectedDate = initialDate,
        initialDisplayMode = DisplayMode.Input,
        selectableDates = pastDates
    )

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    state.getSelectedDate()?.let { date -> onDateChange(date) }
                    onDismissRequest()
                },
                enabled = state.selectedDateMillis != null
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

/**
 * A dropdown of the licence categories. Each category has a short
 * description, such as "B: cars".
 */
@Composable
private fun LicenceCategoryView(
    licenceCategory: LicenceCategory?,
    problem: FormProblem?,
    onCategoryChange: (LicenceCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(value = false) }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { expanded -> isExpanded = expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = licenceCategory?.let { category -> stringResource(id = category.labelResource) }.orEmpty(),
            onValueChange = {},
            modifier = Modifier
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            readOnly = true,
            label = { Text(text = stringResource(id = R.string.field_licence_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            supportingText = problem.toSupportingText(),
            isError = problem != null,
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            LicenceCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = category.labelResource)) },
                    onClick = {
                        onCategoryChange(category)
                        isExpanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/**
 * A button that opens the Photo Picker, which shows only JPEG photos, and a
 * line that says whether a photo is chosen.
 */
@Composable
private fun PhotoView(
    photo: PhotoSource?,
    problem: FormProblem?,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp)
    ) {
        Text(
            text = stringResource(id = R.string.field_photo),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(id = if (photo == null) R.string.photo_hint else R.string.photo_chosen),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(onClick = onChoosePhoto) {
            Text(text = stringResource(id = if (photo == null) R.string.photo_choose else R.string.photo_change))
        }

        if (problem != null) {
            Text(
                text = stringResource(id = problem.messageResource),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Calls [onOpen] when the applicant taps the read-only field, or activates it
 * with a screen reader. A text field consumes taps, so a click modifier would
 * not see them. This modifier observes each tap before the field does, and
 * leaves it for the field, as Material's dropdown does.
 */
private fun Modifier.opensOnClick(label: String, onOpen: () -> Unit): Modifier = this
    .pointerInput(key1 = onOpen) {
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)

            if (up != null) {
                onOpen()
            }
        }
    }
    .semantics {
        onClick(label = label) {
            onOpen()
            true
        }
    }

/** The problem of [field], if it has one. Each field has at most one. */
private fun Set<FormProblem>.findByField(field: ApplicantField): FormProblem? = firstOrNull { problem ->
    when (problem) {
        is FormProblem.MissingField -> problem.field == field
        is FormProblem.NameWithoutLetters -> problem.field == field
        FormProblem.InvalidEmail -> field == ApplicantField.Email
        FormProblem.MissingPhoto -> false
    }
}

/** The message to show under a field, or null if the field has no problem. */
private fun FormProblem?.toSupportingText(): (@Composable () -> Unit)? = this?.let { problem ->
    { Text(text = stringResource(id = problem.messageResource)) }
}

@get:StringRes
private val FormProblem.messageResource: Int
    get() = when (this) {
        is FormProblem.MissingField -> R.string.problem_missing_field
        is FormProblem.NameWithoutLetters -> R.string.problem_name_without_letters
        FormProblem.InvalidEmail -> R.string.problem_invalid_email
        FormProblem.MissingPhoto -> R.string.problem_missing_photo
    }

@get:StringRes
private val LicenceCategory.labelResource: Int
    get() = when (this) {
        LicenceCategory.AM -> R.string.licence_category_am
        LicenceCategory.A1 -> R.string.licence_category_a1
        LicenceCategory.A2 -> R.string.licence_category_a2
        LicenceCategory.A -> R.string.licence_category_a
        LicenceCategory.B1 -> R.string.licence_category_b1
        LicenceCategory.B -> R.string.licence_category_b
        LicenceCategory.BE -> R.string.licence_category_be
        LicenceCategory.C1 -> R.string.licence_category_c1
        LicenceCategory.C1E -> R.string.licence_category_c1e
        LicenceCategory.C -> R.string.licence_category_c
        LicenceCategory.CE -> R.string.licence_category_ce
        LicenceCategory.D1 -> R.string.licence_category_d1
        LicenceCategory.D1E -> R.string.licence_category_d1e
        LicenceCategory.D -> R.string.licence_category_d
        LicenceCategory.DE -> R.string.licence_category_de
    }

/**
 * The dates that the date picker allows for a date of birth, which are today
 * and the days before it.
 *
 * The picker passes each date as the time of its midnight in UTC, so the date
 * is read in UTC. Read in the device's time zone, it would be the day before
 * on a device west of UTC.
 */
internal class PastDates(private val today: LocalDate) : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = Instant
            .ofEpochMilli(utcTimeMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        return date <= today
    }

    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}

/** The form filled in with the example from the assignment. */
@Preview
@Composable
private fun SubmitApplicationPreview() {
    DrivingLicenceWalletTheme {
        SubmitApplicationContent(
            firstName = rememberTextFieldState(initialText = "John"),
            lastName = rememberTextFieldState(initialText = "Doe"),
            dateOfBirth = LocalDate.parse("1990-05-20"),
            nationalId = rememberTextFieldState(initialText = "AB123456"),
            email = rememberTextFieldState(initialText = "john.doe@example.com"),
            phoneNumber = rememberTextFieldState(initialText = "+306900000000"),
            address = rememberTextFieldState(initialText = "Example Street 10"),
            licenceCategory = LicenceCategory.B,
            photo = PhotoSource(value = "content://preview/photo.jpg"),
            submission = Submission.NotStarted,
            problems = emptySet(),
            onAction = {},
            onChoosePhoto = {}
        )
    }
}
