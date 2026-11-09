package gts.drivinglicencewallet.ui.licenceapplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * A screen for a single licence application. It observes the corresponding
 * application in the database, so its status is updated automatically as
 * background processing progresses.
 *
 * The ID comes from the navigation key. It is a String rather than
 * [ApplicationId] because Dagger's code generator cannot handle value classes
 * as assisted injection parameters.
 */
@HiltViewModel(assistedFactory = LicenceApplicationViewModel.Factory::class)
class LicenceApplicationViewModel @AssistedInject constructor(
    @Assisted applicationId: String,
    repository: LicenceApplicationRepository,
) : ViewModel() {

    val state: StateFlow<LicenceApplicationState> = repository
        .observe(id = ApplicationId(value = applicationId))
        .map { application ->
            if (application == null) LicenceApplicationState.NotFound else LicenceApplicationState.Found(application = application)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS),
            initialValue = LicenceApplicationState.Loading
        )

    @AssistedFactory
    interface Factory {

        fun create(applicationId: String): LicenceApplicationViewModel
    }

    private companion object {

        /**
         * How long the database is still observed after the screen stops
         * collecting the state. A rotation takes less time than this, so it
         * does not restart the observation.
         */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface LicenceApplicationState {

    /**
     * The application is still being read from the database. This usually
     * takes only a few milliseconds.
     */
    data object Loading : LicenceApplicationState

    data class Found(val application: LicenceApplication) : LicenceApplicationState

    /** No application has this ID. */
    data object NotFound : LicenceApplicationState
}
