package vn.unicom.thi360

import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class NetworkSecurityConfigTest {

    private data class ParsedNetworkSecurityConfig(
        val baseCleartextPermitted: Boolean?,
        val cleartextAllowedDomains: Set<String>,
        val anySubdomainsIncluded: Boolean,
    )

    private fun parseActiveConfig(): ParsedNetworkSecurityConfig {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val parser: XmlResourceParser = context.resources.getXml(R.xml.network_security_config)
        var baseCleartext: Boolean? = null
        var inCleartextDomainConfig = false
        var anySubdomains = false
        val allowedDomains = mutableSetOf<String>()

        parser.use { xml ->
            var event = xml.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (xml.name) {
                        "base-config" -> {
                            baseCleartext = xml.getAttributeBooleanValue(null, "cleartextTrafficPermitted", true)
                        }
                        "domain-config" -> {
                            inCleartextDomainConfig = xml.getAttributeBooleanValue(null, "cleartextTrafficPermitted", false)
                        }
                        "domain" -> {
                            if (inCleartextDomainConfig) {
                                if (xml.getAttributeBooleanValue(null, "includeSubdomains", false)) {
                                    anySubdomains = true
                                }
                                allowedDomains += xml.nextText().trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (xml.name == "domain-config") {
                            inCleartextDomainConfig = false
                        }
                    }
                }
                event = xml.next()
            }
        }
        return ParsedNetworkSecurityConfig(
            baseCleartextPermitted = baseCleartext,
            cleartextAllowedDomains = allowedDomains,
            anySubdomainsIncluded = anySubdomains,
        )
    }

    @Test
    fun networkSecurityPolicyMatchesFlavor() {
        val config = parseActiveConfig()

        assertEquals(
            "Base config must reject cleartext HTTP in all flavors",
            false,
            config.baseCleartextPermitted,
        )
        assertFalse(
            "Domain cleartext rules must not include arbitrary subdomains",
            config.anySubdomainsIncluded,
        )

        when (BuildConfig.FLAVOR) {
            "demo" -> {
                assertTrue(
                    "demo flavor must forbid all cleartext HTTP hosts, found: ${config.cleartextAllowedDomains}",
                    config.cleartextAllowedDomains.isEmpty(),
                )
            }
            "dev" -> {
                assertEquals(
                    setOf("10.0.2.2", "localhost", "127.0.0.1"),
                    config.cleartextAllowedDomains,
                )
                assertFalse(config.cleartextAllowedDomains.contains("thi360.zone17.click"))
                assertFalse(config.cleartextAllowedDomains.contains("example.com"))
            }
            else -> throw AssertionError("Unexpected flavor: ${BuildConfig.FLAVOR}")
        }
    }
}
