package vn.unicom.thi360.core.storage

import android.content.Context
import java.io.File

class AccountScopedStoreProvider(
    val rootDir: File,
    private val accountIdProvider: () -> String?,
) {
    fun current(): AccountScopedStore? {
        val accountId = accountIdProvider()?.takeIf { it.isNotBlank() } ?: return null
        return AccountScopedStore(rootDir = rootDir, accountId = accountId)
    }

    fun storeFor(accountId: String): AccountScopedStore =
        AccountScopedStore(rootDir = rootDir, accountId = accountId)

    companion object {
        fun create(
            context: Context,
            accountIdProvider: () -> String?,
        ): AccountScopedStoreProvider = AccountScopedStoreProvider(
            rootDir = AccountScopedStore.defaultRootDir(context),
            accountIdProvider = accountIdProvider,
        )
    }
}
