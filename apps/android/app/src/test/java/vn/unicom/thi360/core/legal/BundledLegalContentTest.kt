package vn.unicom.thi360.core.legal

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.preferences.AppLocale

@RunWith(RobolectricTestRunner::class)
class BundledLegalContentTest {

    private fun repoLegalDir(): File {
        val contractsMobile = ContractFixtureLoader.openApiSpecFile().parentFile
            ?: error("Cannot locate contracts/mobile")
        return File(contractsMobile, "legal")
    }

    @Test
    fun allFourLegalAssetsMatchRepoContractByteForByteAndShareTermsVersion() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val repoDir = repoLegalDir()
        val expectedFiles = listOf(
            "terms.vi.json",
            "terms.en.json",
            "privacy.vi.json",
            "privacy.en.json",
        )

        for (fileName in expectedFiles) {
            val contractFile = File(repoDir, fileName)
            assertTrue(
                "Missing contract legal file contracts/mobile/legal/$fileName (waiting for WEB-1 merge)",
                contractFile.isFile,
            )
            val assetBytes = context.assets.open("legal/$fileName").use { it.readBytes() }
            val contractBytes = contractFile.readBytes()
            assertArrayEquals(
                "Bundled APK asset legal/$fileName must match contracts/mobile/legal/$fileName byte-for-byte",
                contractBytes,
                assetBytes,
            )
        }

        val repo = LegalRepository.create(context)
        val termsVi = repo.load(LegalId.TERMS, AppLocale.VI).getOrThrow()
        val termsEn = repo.load(LegalId.TERMS, AppLocale.EN).getOrThrow()
        val privacyVi = repo.load(LegalId.PRIVACY, AppLocale.VI).getOrThrow()
        val privacyEn = repo.load(LegalId.PRIVACY, AppLocale.EN).getOrThrow()

        assertEquals("terms.vi and terms.en must share identical version", termsVi.version, termsEn.version)
        assertEquals("privacy.vi and privacy.en must share identical version", privacyVi.version, privacyEn.version)
        assertEquals(termsVi.version, repo.termsVersion().getOrThrow())
        assertTrue(termsVi.version.isNotBlank())
    }
}
