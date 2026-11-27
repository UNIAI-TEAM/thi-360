package vn.unicom.thi360.shell.routes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.feature.recovery.ResetLinkIntentResult
import vn.unicom.thi360.shell.ActiveResetOverlay

fun interface InboundLinkParser {
    fun parse(uri: Uri): (() -> Unit)?
}

class InboundLinkRouter(
    private val allowedHostProvider: () -> String,
) {
    private data class RegisteredParser(
        val parser: InboundLinkParser,
        val requiresUnlockedSession: Boolean,
    )

    private val lock = Any()
    private val parsers = CopyOnWriteArrayList<RegisteredParser>()
    private val appStartedHooks = CopyOnWriteArrayList<() -> Unit>()
    private val foregroundHooks = CopyOnWriteArrayList<() -> Unit>()
    private val pendingActions = ArrayDeque<() -> Unit>()

    private var currentSessionState: SessionState = SessionState.SignedOut()
    private var appStartPending = false
    private var appStartDispatched = false
    private var foregroundPending = false

    private val _activeResetOverlay = MutableStateFlow<ActiveResetOverlay?>(null)
    internal val activeResetOverlay: StateFlow<ActiveResetOverlay?> = _activeResetOverlay.asStateFlow()

    init {
        register(
            parser = InboundLinkParser { uri ->
                when (val parsed = ResetLink.parseUri(uri, allowedHostProvider())) {
                    is ResetLinkIntentResult.Valid -> ({
                        _activeResetOverlay.value = ActiveResetOverlay.Valid(parsed.link)
                    })
                    ResetLinkIntentResult.MissingToken -> ({
                        _activeResetOverlay.value = ActiveResetOverlay.InvalidLink
                    })
                    ResetLinkIntentResult.Ignored -> null
                }
            },
            requiresUnlockedSession = false,
        )
    }

    fun register(
        parser: InboundLinkParser,
        requiresUnlockedSession: Boolean = true,
    ): AutoCloseable {
        val entry = RegisteredParser(parser = parser, requiresUnlockedSession = requiresUnlockedSession)
        parsers.add(entry)
        return AutoCloseable { parsers.remove(entry) }
    }

    fun onAppStarted(action: () -> Unit): AutoCloseable {
        appStartedHooks.add(action)
        return AutoCloseable { appStartedHooks.remove(action) }
    }

    fun onForeground(action: () -> Unit): AutoCloseable {
        foregroundHooks.add(action)
        return AutoCloseable { foregroundHooks.remove(action) }
    }

    fun dismissResetOverlay(activity: ComponentActivity? = null) {
        _activeResetOverlay.value = null
        clearConsumedResetIntent(activity)
    }

    fun markResetLinkInvalid(activity: ComponentActivity? = null) {
        _activeResetOverlay.value = ActiveResetOverlay.InvalidLink
        clearConsumedResetIntent(activity)
    }

    fun handleIntent(intent: Intent?, activity: ComponentActivity? = null): Boolean {
        if (intent == null || intent.action != Intent.ACTION_VIEW) {
            return false
        }
        val uri = intent.data ?: return false
        val handled = handleUri(uri)
        if (handled) {
            clearConsumedIntent(activity, intent)
        }
        return handled
    }

    fun handleUri(uri: Uri): Boolean {
        for (entry in parsers) {
            if (!entry.requiresUnlockedSession) {
                val action = entry.parser.parse(uri)
                if (action != null) {
                    action()
                    return true
                }
            }
        }
        for (entry in parsers) {
            if (entry.requiresUnlockedSession) {
                val action = entry.parser.parse(uri)
                if (action != null) {
                    val runImmediately = synchronized(lock) {
                        if (currentSessionState is SessionState.Active) {
                            true
                        } else {
                            pendingActions.addLast(action)
                            false
                        }
                    }
                    if (runImmediately) {
                        action()
                    }
                    return true
                }
            }
        }
        return false
    }

    fun onSessionStateChanged(state: SessionState) {
        val actionsToRun = mutableListOf<() -> Unit>()
        var dispatchAppStart = false
        var dispatchForeground = false

        synchronized(lock) {
            currentSessionState = state
            when (state) {
                is SessionState.Active -> {
                    while (pendingActions.isNotEmpty()) {
                        actionsToRun.add(pendingActions.removeFirst())
                    }
                    if (appStartPending && !appStartDispatched) {
                        appStartDispatched = true
                        appStartPending = false
                        dispatchAppStart = true
                    }
                    if (foregroundPending) {
                        foregroundPending = false
                        dispatchForeground = true
                    }
                }
                is SessionState.SignedOut -> {
                    foregroundPending = false
                }
                is SessionState.Locked, is SessionState.MustChangePassword -> Unit
            }
        }

        actionsToRun.forEach { it() }
        if (dispatchAppStart) {
            appStartedHooks.forEach { it() }
        }
        if (dispatchForeground) {
            foregroundHooks.forEach { it() }
        }
    }

    fun notifyAppStarted() {
        val shouldDispatchNow = synchronized(lock) {
            if (appStartDispatched) {
                false
            } else if (currentSessionState is SessionState.Active) {
                appStartDispatched = true
                appStartPending = false
                true
            } else {
                appStartPending = true
                false
            }
        }
        if (shouldDispatchNow) {
            appStartedHooks.forEach { it() }
        }
    }

    fun notifyForegroundReturn() {
        val shouldDispatchNow = synchronized(lock) {
            when (currentSessionState) {
                is SessionState.Active -> {
                    foregroundPending = false
                    true
                }
                is SessionState.Locked, is SessionState.MustChangePassword -> {
                    foregroundPending = true
                    false
                }
                is SessionState.SignedOut -> {
                    foregroundPending = false
                    false
                }
            }
        }
        if (shouldDispatchNow) {
            foregroundHooks.forEach { it() }
        }
    }

    fun clearConsumedResetIntent(activity: ComponentActivity?) {
        val current = activity?.intent ?: return
        if (ResetLink.parseIntent(current, allowedHostProvider()) is ResetLinkIntentResult.Ignored) {
            return
        }
        clearConsumedIntent(activity, current)
    }

    private fun clearConsumedIntent(activity: ComponentActivity?, intent: Intent) {
        val savedComponent = intent.component
        intent.action = Intent.ACTION_MAIN
        intent.data = null
        intent.replaceExtras(null as Bundle?)
        if (activity != null && activity.intent === intent) {
            activity.intent = Intent(Intent.ACTION_MAIN).apply {
                component = savedComponent
            }
        }
    }
}
