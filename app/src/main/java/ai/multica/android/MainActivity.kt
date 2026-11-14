package ai.multica.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import ai.multica.android.core.auth.AndroidKeystoreSessionStore
import ai.multica.android.core.network.ClientMetadata
import ai.multica.android.core.network.HttpMulticaGateway
import ai.multica.android.core.network.LocalFixtureGateway
import ai.multica.android.feature.AppController
import ai.multica.android.ui.theme.MulticaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val gateway = if (BuildConfig.FIXTURE_MODE) {
            LocalFixtureGateway()
        } else {
            HttpMulticaGateway(
                baseUrl = BuildConfig.API_BASE_URL,
                metadata = ClientMetadata(BuildConfig.VERSION_NAME),
            )
        }
        controller = AppController(gateway, AndroidKeystoreSessionStore(applicationContext))
        setContent {
            MulticaTheme {
                MulticaApp(controller = controller, fixtureMode = BuildConfig.FIXTURE_MODE)
            }
        }
        lifecycleScope.launch { controller.start() }
    }
}
