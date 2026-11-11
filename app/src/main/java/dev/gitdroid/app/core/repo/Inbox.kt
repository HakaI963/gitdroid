package dev.gitdroid.app.core.repo

import dev.gitdroid.app.core.api.GitHubApi
import dev.gitdroid.app.core.api.Notification
import dev.gitdroid.app.core.cache.DiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The notification inbox.
 *
 * GitHub's own app leads with this screen, and it is the one place where a
 * phone genuinely beats a browser: unread mentions and review requests,
 * cached so they are there the moment the app opens.
 */
class Inbox(
    private val api: GitHubApi,
    private val cache: DiskCache,
    private val scope: CoroutineScope,
) {

    private val _items = MutableStateFlow<List<Notification>>(emptyList())
    val items: StateFlow<List<Notification>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Unread count, for the tab badge. */
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** Shows the cache first, then refreshes — the same rule every screen follows. */
    fun start() {
        scope.launch {
            cache.read<List<Notification>>(KEY)?.let {
                _items.value = it.value
                _unread.value = it.value.count { n -> n.unread }
            }
            refresh()
        }
    }

    suspend fun refresh(): Result<List<Notification>> = runCatching {
        _loading.value = true
        _error.value = null
        val items = api.listNotifications(all = false, participating = false)
        _items.value = items
        _unread.value = items.count { it.unread }
        cache.write(KEY, items)
        items
    }.onFailure {
        // Keep whatever is already on screen; say why the refresh failed.
        _error.value = it.message
    }.also {
        _loading.value = false
    }

    /** Marks everything read locally; GitHub's own read state is a PATCH away. */
    fun markAllRead() {
        _items.value = _items.value.map { it.copy(unread = false) }
        _unread.value = 0
        scope.launch { cache.write(KEY, _items.value) }
    }

    private companion object {
        const val KEY = "inbox"
    }
}

/** The repository an inbox entry points at, if GitHub included one. */
val Notification.repoFullName: String
    get() = repository?.fullName ?: ""

/** A short, human label for what the notification is about. */
val Notification.kindLabel: String
    get() = when (subject.type.lowercase()) {
        "issue" -> "issue"
        "pullrequest" -> "pull request"
        "commit" -> "commit"
        "release" -> "release"
        "discussion" -> "discussion"
        "checkrun" -> "check"
        else -> subject.type.lowercase().ifBlank { "update" }
    }
