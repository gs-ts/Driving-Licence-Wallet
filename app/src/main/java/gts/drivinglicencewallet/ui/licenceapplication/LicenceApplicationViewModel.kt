package gts.drivinglicencewallet.ui.licenceapplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import gts.drivinglicencewallet.data.backend.SimulatedIssuerSwitch
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.LicenceApplicationRepository
import gts.drivinglicencewallet.domain.application.ManualReviewUseCase
import gts.drivinglicencewallet.domain.application.ReviewResult
import gts.drivinglicencewallet.domain.workflow.ApplicationStatus
import gts.drivinglicencewallet.domain.workflow.RejectionReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A screen for a single licence application. It observes the corresponding
 * application in the database, so its status is updated automatically as
 * background processing progresses.
 *
 * Its simulation section lets a tester act as the issuer: a reviewer approves
 * or rejects the application, and the issuer's server can be made
 * unreachable. A decision runs in [viewModelScope], not in the application's
 * scope as a submit does, because it is one database transaction. Leaving the
 * screen can cancel it, but cannot leave it half saved.
 *
 * The ID comes from the navigation key. It is a String rather than
 * [ApplicationId] because Dagger's code generator cannot handle value classes
 * as assisted injection parameters.
 */
@HiltViewModel(assistedFactory = LicenceApplicationViewModel.Factory::class)
class LicenceApplicationViewModel @AssistedInject constructor(
    @Assisted applicationId: String,
    repository: LicenceApplicationRepository,
    private val manualReview: ManualReviewUseCase,
    private val issuerSwitch: SimulatedIssuerSwitch,
) : ViewModel() {

    private val id = ApplicationId(value = applicationId)

    val state: StateFlow<LicenceApplicationState> = repository
        .observe(id = id)
        .map { application ->
            if (application == null) LicenceApplicationState.NotFound else LicenceApplicationState.Found(application = application)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MILLIS),
            initialValue = LicenceApplicationState.Loading
        )

    /** Why the last decision was refused, until the screen has shown it. */
    val refusal: StateFlow<ReviewRefusal?>
        field = MutableStateFlow<ReviewRefusal?>(value = null)

    val isServerUnreachable: StateFlow<Boolean>
        field = MutableStateFlow(value = issuerSwitch.isUnreachable)

    fun onAction(action: LicenceApplicationAction) {
        when (action) {
            LicenceApplicationAction.ApproveClicked -> approve()
            is LicenceApplicationAction.RejectionConfirmed -> reject(reason = action.reason)
            is LicenceApplicationAction.ServerUnreachableChanged -> changeServerReachability(isUnreachable = action.isUnreachable)
            LicenceApplicationAction.MessageShown -> refusal.value = null
        }
    }

    private fun approve() {
        viewModelScope.launch {
            refusal.value = manualReview.approve(id = id).toRefusal()
        }
    }

    private fun reject(reason: RejectionReason) {
        viewModelScope.launch {
            refusal.value = manualReview.reject(id = id, reason = reason).toRefusal()
        }
    }

    private fun changeServerReachability(isUnreachable: Boolean) {
        issuerSwitch.isUnreachable = isUnreachable
        isServerUnreachable.value = isUnreachable
    }

    private fun ReviewResult.toRefusal(): ReviewRefusal? = when (this) {
        is ReviewResult.Recorded -> null
        is ReviewResult.NotPendingReview -> ReviewRefusal.NotPendingReview(status = status)
        ReviewResult.NotFound -> ReviewRefusal.NotFound
    }

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

/** What the tester does in the simulation section of the status screen. */
sealed interface LicenceApplicationAction {

    data object ApproveClicked : LicenceApplicationAction

    /** The tester entered a [reason] and confirmed the rejection. */
    data class RejectionConfirmed(val reason: RejectionReason) : LicenceApplicationAction

    data class ServerUnreachableChanged(val isUnreachable: Boolean) : LicenceApplicationAction

    /**
     * The screen has shown the message for a [ReviewRefusal], so it is not
     * shown again after a rotation.
     */
    data object MessageShown : LicenceApplicationAction
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

/** Why a decision in the simulation section was refused. */
sealed interface ReviewRefusal {

    /**
     * The application is in [status], so it is not waiting for a review. This
     * includes a second tap on Approve, after the first one has approved it.
     */
    data class NotPendingReview(val status: ApplicationStatus) : ReviewRefusal

    data object NotFound : ReviewRefusal
}
