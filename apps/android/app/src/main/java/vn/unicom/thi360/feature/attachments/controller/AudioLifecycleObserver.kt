package vn.unicom.thi360.feature.attachments.controller

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable

class AudioLifecycleBridge(
    private val onAppBackgrounded: () -> Unit,
    private val onScreenStopped: () -> Unit,
    private val application: Application? = null,
    private val processLifecycle: Lifecycle? = resolveProcessLifecycle(),
    private val screenLifecycle: Lifecycle? = null,
    private val isScreenChangingConfigurations: () -> Boolean = { false },
) : Closeable {
    private var attached = false
    private var startedActivityCount = if (screenLifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) 1 else 0

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            onAppBackgrounded()
        }
    }

    private val screenObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            if (!isScreenChangingConfigurations()) {
                onScreenStopped()
            }
        }
    }

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivityCount += 1
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            // Fires immediately (< 50 ms) on Home / app switch without waiting for ProcessLifecycleOwner's 700 ms timeout.
            if (startedActivityCount == 0 && !activity.isChangingConfigurations) {
                onAppBackgrounded()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    fun attach(): AudioLifecycleBridge {
        if (attached) return this
        attached = true
        processLifecycle?.addObserver(processObserver)
        screenLifecycle?.addObserver(screenObserver)
        application?.registerActivityLifecycleCallbacks(activityCallbacks)
        return this
    }

    override fun close() {
        if (!attached) return
        attached = false
        processLifecycle?.removeObserver(processObserver)
        screenLifecycle?.removeObserver(screenObserver)
        application?.unregisterActivityLifecycleCallbacks(activityCallbacks)
    }

    companion object {
        fun forContext(
            context: Context,
            screenLifecycle: Lifecycle? = null,
            isScreenChangingConfigurations: () -> Boolean = { false },
            onAppBackgrounded: () -> Unit,
            onScreenStopped: () -> Unit,
        ): AudioLifecycleBridge = AudioLifecycleBridge(
            onAppBackgrounded = onAppBackgrounded,
            onScreenStopped = onScreenStopped,
            application = context.applicationContext as? Application,
            processLifecycle = resolveProcessLifecycle(),
            screenLifecycle = screenLifecycle,
            isScreenChangingConfigurations = isScreenChangingConfigurations,
        )

        // androidx.lifecycle:lifecycle-process is present on the runtime classpath via Compose/AndroidX.
        internal fun resolveProcessLifecycle(): Lifecycle? = runCatching {
            val clazz = Class.forName("androidx.lifecycle.ProcessLifecycleOwner")
            val owner = clazz.getMethod("get").invoke(null) as? LifecycleOwner
            owner?.lifecycle
        }.getOrNull()
    }
}
