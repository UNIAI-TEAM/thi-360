package vn.unicom.thi360.core.session

import android.content.Context
import java.util.concurrent.atomic.AtomicReference
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.KeystoreTokenStore

object AppSessionContainer {
    private val currentLocale = AtomicReference(AppLocale.Default)

    @Volatile
    private var cachedAppContext: Context? = null

    @Volatile
    private var stack: Thi360SessionStack? = null

    fun setLocale(locale: AppLocale) {
        currentLocale.set(locale)
    }

    fun get(context: Context): Thi360SessionStack {
        val appContext = context.applicationContext
        val existing = stack
        if (existing != null && cachedAppContext === appContext) {
            return existing
        }
        return synchronized(this) {
            val current = stack
            if (current != null && cachedAppContext === appContext) {
                current
            } else {
                Thi360ApiFactory.createSessionStack(
                    baseUrl = AppEnvironment.apiBaseUrl,
                    environment = AppEnvironment.environment,
                    tokenStore = KeystoreTokenStore.create(appContext),
                    localeProvider = { currentLocale.get() },
                ).also {
                    cachedAppContext = appContext
                    stack = it
                }
            }
        }
    }
}
