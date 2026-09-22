package com.aimforge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aimforge.app.data.AimForgeRepository
import com.aimforge.app.data.CaptureEntity
import com.aimforge.app.data.PlayerProfileEntity
import com.aimforge.app.data.TestSessionEntity
import com.aimforge.app.domain.CaptureGrant
import com.aimforge.app.domain.CaptureSnapshot
import com.aimforge.app.domain.ScopeType
import com.aimforge.app.domain.SensType
import com.aimforge.app.domain.SessionDraft
import com.aimforge.app.domain.SessionManager
import com.aimforge.app.domain.SessionResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUi(val loaded: Boolean, val profile: PlayerProfileEntity?)

class AppViewModel(
    private val repo: AimForgeRepository,
    private val manager: SessionManager,
    /** Live, real state of the capture engine (frames received so far, running, stopped, failed). */
    val captureState: StateFlow<CaptureSnapshot>
) : ViewModel() {

    val profileUi: StateFlow<ProfileUi> = repo.profile
        .map { ProfileUi(loaded = true, profile = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUi(false, null))

    /** Key = (scope name, type name). */
    val sensitivities: StateFlow<Map<Pair<String, String>, Int>> = repo.sensitivities
        .map { list -> list.associate { (it.scope to it.type) to it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val sessions: StateFlow<List<TestSessionEntity>> = repo.sessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val captures: StateFlow<List<CaptureEntity>> = repo.captures
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The one open session (READY, waiting or capturing), if any. Survives app restart because it comes from Room. */
    val activeSession: StateFlow<TestSessionEntity?> = sessions
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding(device: String, control: String, gyro: Boolean) {
        viewModelScope.launch { repo.completeOnboarding(device, control, gyro) }
    }

    fun saveSensitivities(scope: ScopeType, values: Map<SensType, Int?>, onSaved: () -> Unit) {
        viewModelScope.launch {
            values.forEach { (type, v) -> repo.saveSensitivity(scope, type, v) }
            onSaved()
        }
    }

    fun deleteAllData() {
        viewModelScope.launch { repo.deleteAllData() }
    }

    // ---- sessions ----
    private fun launchSession(onResult: (SessionResult) -> Unit, block: suspend () -> SessionResult) {
        viewModelScope.launch { onResult(block()) }
    }

    fun createSession(draft: SessionDraft, onResult: (SessionResult) -> Unit) = launchSession(onResult) { manager.createSession(draft) }

    /** [grant] = result of Android's screen-capture dialog; null = the user denied or cancelled it. */
    fun startCapture(id: String, grant: CaptureGrant?, onResult: (SessionResult) -> Unit) =
        launchSession(onResult) { manager.startCapture(id, grant) }

    fun pauseSession(id: String, onResult: (SessionResult) -> Unit) = launchSession(onResult) { manager.pause(id) }
    fun resumeSession(id: String, onResult: (SessionResult) -> Unit) = launchSession(onResult) { manager.resume(id) }
    fun endSession(id: String, onResult: (SessionResult) -> Unit) = launchSession(onResult) { manager.end(id) }
    fun cancelSession(id: String, onResult: (SessionResult) -> Unit) = launchSession(onResult) { manager.cancel(id) }

    fun deleteSession(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            manager.delete(id)
            onDone()
        }
    }

    fun elapsedMs(s: TestSessionEntity, now: Long): Long = manager.elapsedMs(s, now)

    class Factory(
        private val repo: AimForgeRepository,
        private val manager: SessionManager,
        private val captureState: StateFlow<CaptureSnapshot>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repo, manager, captureState) as T
    }
}
