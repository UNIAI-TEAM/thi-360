package vn.unicom.thi360.feature.attemptengine

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import vn.unicom.thi360.shell.routes.InboundLinkRouter

class EngineLifecycle(
    private val engine: AttemptEngine,
    private val lifecycle: Lifecycle? = null,
    private val inboundLinkRouter: InboundLinkRouter? = null,
    private val isChangingConfigurations: () -> Boolean = { false },
    private val isSessionUnlocked: () -> Boolean = { true },
) : AutoCloseable {
    private var attached = false
    private var stoppedWhileAttached = false
    private var routerHook: AutoCloseable? = null

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (stoppedWhileAttached) {
                stoppedWhileAttached = false
                if (inboundLinkRouter == null && isSessionUnlocked()) {
                    engine.setForeground(true)
                }
            } else if (isSessionUnlocked()) {
                engine.setForeground(true)
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            if (!isChangingConfigurations()) {
                stoppedWhileAttached = true
                engine.setForeground(false)
            }
        }
    }

    fun attach(): EngineLifecycle {
        if (attached) return this
        attached = true
        stoppedWhileAttached = false
        lifecycle?.addObserver(observer)
        routerHook = inboundLinkRouter?.onForeground {
            stoppedWhileAttached = false
            engine.setForeground(true)
        }
        if (lifecycle == null && isSessionUnlocked()) {
            engine.setForeground(true)
        }
        return this
    }

    override fun close() {
        if (!attached) return
        attached = false
        stoppedWhileAttached = false
        lifecycle?.removeObserver(observer)
        routerHook?.close()
        routerHook = null
    }
}
