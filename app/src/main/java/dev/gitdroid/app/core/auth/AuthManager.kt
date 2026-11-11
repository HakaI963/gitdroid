package dev.gitdroid.app.core.auth

import android.content.Context
import dev.gitdroid.app.core.api.GitHubApi
import dev.gitdroid.app.core.api.GitHubException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who is signed in, if anybody. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(
        val login: String,
        val avatarUrl: String,
        val name: String?,
        val method: AuthMethod,
    ) : SessionState
}

/**
 * Owns the GitHub session.
 *
 * The token lives in [SecureStore]; this class decides *when* the session is
 * valid, refreshes expiring device-flow tokens, and exposes the current user
 * as a [StateFlow] so every screen reacts to sign-in and sign-out.
 */
class AuthManager(
    context: Context,
    private val store: SecureStore = SecureStore(context),
    private val deviceFlow: DeviceFlowClient = DeviceFlowClient(DeviceFlowClient.CLIENT_ID),
    private val apiProvider: () -> GitHubApi,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val isSignedIn: Boolean get() = _state.value is SessionState.SignedIn

    /** The bearer token used for API calls, if any. */
    suspend fun currentToken(): String? = store.githubToken

    /**
     * Restores a previous session from disk.
     *
     * Called on app start. Never blocks the UI on the network: the cached
     * identity is published immediately and the profile is refreshed in the
     * background.
     */
    suspend fun restore() {
        val token = store.githubToken
        if (token.isNullOrBlank()) {
            _state.value = SessionState.SignedOut
            return
        }
        val login = store.githubLogin
        val method = store.authMethod?.let { runCatching { AuthMethod.valueOf(it) }.getOrNull() }
            ?: AuthMethod.PAT
        if (login != null) {
            _state.value = SessionState.SignedIn(
                login = login,
                avatarUrl = "",
                name = null,
                method = method,
            )
        }
        runCatching { apiProvider().currentUser() }
            .onSuccess { user ->
                store.githubLogin = user.login
                _state.value = SessionState.SignedIn(
                    login = user.login,
                    avatarUrl = user.avatarUrl,
                    name = user.name,
                    method = method,
                )
            }
            .onFailure { error ->
                if (error is GitHubException && error.isAuthFailure) {
                    signOut()
                } else if (login == null) {
                    _state.value = SessionState.SignedOut
                }
            }
    }

    /** Signs in with a personal access token the user pasted. */
    suspend fun signInWithToken(token: String): Result<SessionState> = runCatching {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "The token is empty" }
        store.githubToken = trimmed
        val user = apiProvider().currentUser()
        store.githubLogin = user.login
        store.authMethod = AuthMethod.PAT.name
        val signedIn = SessionState.SignedIn(
            login = user.login,
            avatarUrl = user.avatarUrl,
            name = user.name,
            method = AuthMethod.PAT,
        )
        _state.value = signedIn
        signedIn
    }.onFailure { error ->
        // Do not keep a token we could not validate.
        if (error is GitHubException && error.isAuthFailure) store.githubToken = null
    }

    /** Begins the OAuth device flow and returns the codes to show the user. */
    suspend fun beginDeviceFlow(): DeviceFlowClient.DeviceCode =
        deviceFlow.requestCode()

    /**
     * Completes the device flow.
     *
     * [onTick] reports the poll count so the UI can show "still waiting".
     */
    suspend fun completeDeviceFlow(
        code: DeviceFlowClient.DeviceCode,
        onTick: (attempt: Int) -> Unit = {},
    ): Result<SessionState> = runCatching {
        val token = deviceFlow.awaitToken(code, onTick = onTick)
        store.githubToken = token.accessToken
        store.refreshToken = token.refreshToken
        store.authMethod = AuthMethod.DEVICE_FLOW.name
        val user = apiProvider().currentUser()
        store.githubLogin = user.login
        val signedIn = SessionState.SignedIn(
            login = user.login,
            avatarUrl = user.avatarUrl,
            name = user.name,
            method = AuthMethod.DEVICE_FLOW,
        )
        _state.value = signedIn
        signedIn
    }

    /** Signs the user out and clears every stored credential. */
    fun signOut() {
        store.clearSession()
        _state.value = SessionState.SignedOut
    }

    /** Re-reads the profile, e.g. after the user changes their avatar. */
    suspend fun refreshProfile() {
        if (!isSignedIn) return
        runCatching { apiProvider().currentUser() }.onSuccess { user ->
            store.githubLogin = user.login
            _state.value = SessionState.SignedIn(
                login = user.login,
                avatarUrl = user.avatarUrl,
                name = user.name,
                method = (store.authMethod?.let { runCatching { AuthMethod.valueOf(it) }.getOrNull() })
                    ?: AuthMethod.PAT,
            )
        }
    }
}
