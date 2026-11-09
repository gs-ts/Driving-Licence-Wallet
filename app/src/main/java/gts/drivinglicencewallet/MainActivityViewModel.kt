package gts.drivinglicencewallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.ui.LicenceApplicationKey
import gts.drivinglicencewallet.ui.SubmitApplicationKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Chooses the screen that the app opens on.
 *
 * The app opens on the latest application, whether it is still in progress or
 * finished, so that the applicant also sees the result of an application that
 * finished while the app was closed. It opens on the form only when no
 * application was submitted yet.
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(repository: LicenceApplicationRepository) : ViewModel() {

    /** The first screen, or null while the latest application is read. */
    val startScreen: StateFlow<NavKey?> = flow<NavKey?> {
        val latest = repository.findLatest()
        val screen = if (latest == null) SubmitApplicationKey else LicenceApplicationKey(applicationId = latest.id.value)
        emit(value = screen)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )
}
