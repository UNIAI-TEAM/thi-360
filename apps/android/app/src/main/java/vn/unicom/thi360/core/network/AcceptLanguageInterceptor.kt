package vn.unicom.thi360.core.network

import okhttp3.Interceptor
import okhttp3.Response
import vn.unicom.thi360.core.preferences.AppLocale

class AcceptLanguageInterceptor(
    private val localeProvider: () -> AppLocale,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header(HEADER_ACCEPT_LANGUAGE) != null) {
            return chain.proceed(original)
        }
        val localized = original.newBuilder()
            .header(HEADER_ACCEPT_LANGUAGE, localeProvider().languageTag)
            .build()
        return chain.proceed(localized)
    }

    companion object {
        const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"
    }
}
