package vn.unicom.thi360

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment

class FlavorConfigTest {

    @Test
    fun flavorEndpointsMatchEnvironmentContract() {
        when (BuildConfig.FLAVOR) {
            "dev" -> {
                assertEquals(Environment.DEV, AppEnvironment.environment)
                assertEquals("http://10.0.2.2:8080", AppEnvironment.apiBaseUrl)
                assertEquals("http://10.0.2.2:3000", AppEnvironment.webBaseUrl)
            }
            "demo" -> {
                assertEquals(Environment.DEMO, AppEnvironment.environment)
                assertEquals("https://thi360.zone17.click", AppEnvironment.apiBaseUrl)
                assertEquals("https://thi360.zone17.click", AppEnvironment.webBaseUrl)
                assertTrue(AppEnvironment.apiBaseUrl.startsWith("https://"))
                assertTrue(AppEnvironment.webBaseUrl.startsWith("https://"))
            }
            else -> throw AssertionError("Unexpected flavor: ${BuildConfig.FLAVOR}")
        }
    }
}
