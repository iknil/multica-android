package ai.multica.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.multica.android.feature.AppController
import kotlinx.coroutines.launch

/** Retains navigation/domain state while Android recreates the activity for window changes. */
class AppControllerViewModel(
    val controller: AppController,
) : ViewModel() {
    init {
        viewModelScope.launch { controller.start() }
    }
}
