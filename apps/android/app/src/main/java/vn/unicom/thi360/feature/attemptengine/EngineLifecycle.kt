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
) : AutoCloseable {
    private var attached = false
    private var routerHook: AutoCloseable? = null

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (inboundLinkRouter == null) {
                engine.setForeground(true)
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            if (!isChangingConfigurations()) {
                engine.setForeground(false)
            }
        }
    }

    fun attach(): EngineLifecycle {
        if (attached) return this
        attached = true
        lifecycle?.addObserver(observer)
        routerHook = inboundLinkRouter?.onForeground {
            engine.setForeground(true)
        }
        return this
    }

    override fun close() {
        if (!attached) return
        attached = false
        lifecycle?.removeObserver(observer)
        routerHook?.close()
        routerHook = null
    }
}
