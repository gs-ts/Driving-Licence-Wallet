package gts.drivinglicencewallet.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import gts.drivinglicencewallet.ui.licenceapplication.LicenceApplicationScreen
import gts.drivinglicencewallet.ui.submit.SubmitApplicationScreen
import kotlinx.serialization.Serializable

/**
 * The app's screens, starting with [startScreen]. The back stack is saved, so
 * it survives a rotation and process death, and each screen on it has its own
 * ViewModel. After process death, the saved back stack is used instead of
 * [startScreen].
 */
@Composable
fun AppNavigation(
    startScreen: NavKey,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(startScreen)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<SubmitApplicationKey> {
                SubmitApplicationScreen(
                    onSubmitSuccess = { applicationId ->
                        // Every screen is replaced, so that Back cannot return to the submitted form or to an older application.
                        backStack.clear()
                        backStack.add(element = LicenceApplicationKey(applicationId = applicationId.value))
                    }
                )
            }
            entry<LicenceApplicationKey> { key ->
                LicenceApplicationScreen(
                    applicationId = key.applicationId,
                    onNewApplication = { backStack.add(element = SubmitApplicationKey) }
                )
            }
        }
    )
}

/**
 * The form for a new application. The app opens on it when no application was
 * submitted yet.
 */
@Serializable
data object SubmitApplicationKey : NavKey

/**
 * The status screen of one application. It holds the ID as a String, because
 * the domain module does not use kotlinx.serialization.
 */
@Serializable
data class LicenceApplicationKey(val applicationId: String) : NavKey
