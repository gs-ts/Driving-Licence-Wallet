package gts.drivinglicencewallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import gts.drivinglicencewallet.ui.AppNavigation
import gts.drivinglicencewallet.ui.theme.DrivingLicenceWalletTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The splash screen stays until the first screen is known, so that the applicant never sees another screen first.
        splashScreen.setKeepOnScreenCondition { viewModel.startScreen.value == null }
        enableEdgeToEdge()
        setContent {
            val startScreen by viewModel.startScreen.collectAsStateWithLifecycle()
            DrivingLicenceWalletTheme {
                startScreen?.let { screen -> AppNavigation(startScreen = screen) }
            }
        }
    }
}
