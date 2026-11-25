package vn.unicom.thi360.core.network

import java.io.File
import okhttp3.mockwebserver.MockResponse
import vn.unicom.thi360.core.storage.TokenStore
import vn.unicom.thi360.core.storage.Tokens

internal object ContractFixtureLoader {
    private val contractsMobileDir: File by lazy { locateContractsMobileDir() }

    private fun locateContractsMobileDir(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            val candidate = File(dir, "contracts/mobile")
            if (File(candidate, "openapi-mobile.json").isFile) {
                return candidate
            }
            dir = dir.parentFile
        }
        error("Cannot locate contracts/mobile/openapi-mobile.json from ${File(".").canonicalPath}")
    }

    fun openApiSpecFile(): File = File(contractsMobileDir, "openapi-mobile.json")

    fun authFixtureFiles(): List<File> =
        File(contractsMobileDir, "auth")
            .listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedBy { it.name }
            ?: error("Missing contracts/mobile/auth directory")

    fun apiFixtureFiles(): List<File> =
        File(contractsMobileDir, "api")
            .listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedBy { it.name }
            ?: error("Missing contracts/mobile/api directory")

    fun readAuthFixture(name: String): String =
        File(contractsMobileDir, "auth/$name").readText(Charsets.UTF_8)

    fun readApiFixture(name: String): String =
        File(contractsMobileDir, "api/$name").readText(Charsets.UTF_8)

    fun readAppearanceFixture(name: String): String =
        File(contractsMobileDir, "appearance/$name").readText(Charsets.UTF_8)

    fun jsonResponse(statusCode: Int, body: String): MockResponse =
        MockResponse()
            .setResponseCode(statusCode)
            .addHeader("Content-Type", "application/json")
            .setBody(body)
}

internal class InMemoryTokenStore(initial: Tokens? = null) : TokenStore {
    @Volatile
    private var current: Tokens? = initial

    var writeCount: Int = 0
        private set

    var clearCount: Int = 0
        private set

    override fun read(): Tokens? = current

    override fun write(tokens: Tokens) {
        writeCount++
        current = tokens
    }

    override fun clear() {
        clearCount++
        current = null
    }
}
