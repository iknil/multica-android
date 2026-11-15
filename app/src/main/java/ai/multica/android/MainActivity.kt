package ai.multica.android

import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.multica.android.core.auth.AndroidGoogleCredentialProvider
import ai.multica.android.core.auth.AndroidKeystoreSessionStore
import ai.multica.android.core.config.AppConfig
import ai.multica.android.core.network.ClientMetadata
import ai.multica.android.core.network.HttpMulticaGateway
import ai.multica.android.core.network.LocalFixtureGateway
import ai.multica.android.feature.AppController
import ai.multica.android.ui.theme.MulticaTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppControllerViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == AppControllerViewModel::class.java)
                val config = AppConfig(
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    clientVersion = BuildConfig.VERSION_NAME,
                    fixtureMode = BuildConfig.FIXTURE_MODE,
                    googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID,
                )
                val gateway = if (config.fixtureMode) {
                    LocalFixtureGateway()
                } else {
                    HttpMulticaGateway(
                        baseUrl = config.apiBaseUrl,
                        metadata = ClientMetadata(config.clientVersion),
                    )
                }
                return AppControllerViewModel(
                    AppController(
                        gateway = gateway,
                        sessionStore = AndroidKeystoreSessionStore(applicationContext),
                        googleCredentialProvider = if (config.fixtureMode) null else {
                            AndroidGoogleCredentialProvider(this@MainActivity)
                        },
                        googleServerClientId = config.googleServerClientId,
                    ),
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MulticaTheme {
                val view = LocalView.current
                val darkTheme = isSystemInDarkTheme()
                SideEffect {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                }
                MulticaApp(
                    controller = appViewModel.controller,
                    fixtureMode = BuildConfig.FIXTURE_MODE,
                )
            }
        }
    }
}
