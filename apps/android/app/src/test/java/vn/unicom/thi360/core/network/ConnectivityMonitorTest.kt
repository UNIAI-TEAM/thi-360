package vn.unicom.thi360.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNetworkCapabilities
import vn.unicom.thi360.Thi360Application

@RunWith(RobolectricTestRunner::class)
class ConnectivityMonitorTest {

    @Test
    fun androidConnectivityMonitorTracksNetworkAvailableAndLostCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = Shadows.shadowOf(cm)

        val activeNetwork = cm.activeNetwork!!
        val onlineCaps = ShadowNetworkCapabilities.newInstance().apply {
            Shadows.shadowOf(this).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        shadowCm.setNetworkCapabilities(activeNetwork, onlineCaps)

        val monitor = AndroidConnectivityMonitor(context)
        try {
            assertTrue("Expected initial state online", monitor.online.value)

            val callbacks = shadowCm.networkCallbacks.toList()
            assertTrue("Expected registered NetworkCallback", callbacks.isNotEmpty())

            // Simulate losing network connectivity.
            shadowCm.setNetworkCapabilities(activeNetwork, ShadowNetworkCapabilities.newInstance())
            shadowCm.setDefaultNetworkActive(false)
            callbacks.forEach { it.onLost(activeNetwork) }
            assertFalse("Monitor must emit offline when network is lost", monitor.online.value)

            // Simulate network coming back with internet capability.
            shadowCm.setNetworkCapabilities(activeNetwork, onlineCaps)
            callbacks.forEach { it.onAvailable(activeNetwork) }
            assertTrue("Monitor must emit online when network returns", monitor.online.value)
        } finally {
            monitor.close()
        }
    }
}
