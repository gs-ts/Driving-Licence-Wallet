package gts.drivinglicencewallet.ui.licenceapplication

import app.cash.turbine.test
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.FakeLicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.submittedApplication
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LicenceApplicationViewModelTest {

    private val id = ApplicationId(value = "application-1")
    private val submittedAt = Instant.parse(input = "2026-11-09T09:00:00Z")

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(dispatcher = StandardTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `given the status screen is open, when processing moves the application on, then it shows the new status`() = runTest {
        val repository = FakeLicenceApplicationRepository()
        repository.add(application = submittedApplication(id = id, timestamp = submittedAt))
        val viewModel = LicenceApplicationViewModel(applicationId = id.value, repository = repository)

        viewModel.state.test {
            assertEquals(expected = LicenceApplicationState.Loading, actual = awaitItem())
            assertEquals(expected = ApplicationStatus.Submitted, actual = assertIs<LicenceApplicationState.Found>(value = awaitItem()).application.status)

            repository.record(
                id = id,
                change = ApplicationStatus.Submitted.startProcessing(),
                timestamp = submittedAt + 1.minutes
            )

            val moved = assertIs<LicenceApplicationState.Found>(value = awaitItem())
            assertEquals(expected = ApplicationStatus.ValidatingData, actual = moved.application.status)
        }
    }
}
