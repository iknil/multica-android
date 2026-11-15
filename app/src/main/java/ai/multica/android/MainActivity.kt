package ai.multica.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.multica.android.core.auth.AndroidKeystoreSessionStore
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
                val gateway = if (BuildConfig.FIXTURE_MODE) {
                    LocalFixtureGateway()
                } else {
                    HttpMulticaGateway(
                        baseUrl = BuildConfig.API_BASE_URL,
                        metadata = ClientMetadata(BuildConfig.VERSION_NAME),
                    )
                }
                return AppControllerViewModel(
                    AppController(gateway, AndroidKeystoreSessionStore(applicationContext)),
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MulticaTheme {
                MulticaApp(
                    controller = appViewModel.controller,
                    fixtureMode = BuildConfig.FIXTURE_MODE,
                )
            }
        }
    }
}
