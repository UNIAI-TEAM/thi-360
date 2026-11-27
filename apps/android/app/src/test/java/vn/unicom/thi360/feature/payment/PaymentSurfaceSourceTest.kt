package vn.unicom.thi360.feature.payment

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentSurfaceSourceTest {

    @Test
    fun paymentSourceNeverUsesWebViewOrCookieManager() {
        val mainDir = locatePaymentMainDir()
        val kotlinFiles = mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Expected payment source files under ${mainDir.path}", kotlinFiles.isNotEmpty())

        val forbidden = listOf("android.webkit", "WebView", "CookieManager")
        val violations = mutableListOf<String>()
        for (file in kotlinFiles) {
            val content = file.readText(Charsets.UTF_8)
            for (token in forbidden) {
                if (content.contains(token)) {
                    violations += "${file.name}: contains forbidden token '$token'"
                }
            }
        }
        assertTrue("Forbidden WebView/Cookie API found:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    private fun locatePaymentMainDir(): File {
        val candidates = listOf(
            File("src/main/java/vn/unicom/thi360/feature/payment"),
            File("app/src/main/java/vn/unicom/thi360/feature/payment"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate feature/payment main directory from ${File(".").canonicalPath}")
    }
}
