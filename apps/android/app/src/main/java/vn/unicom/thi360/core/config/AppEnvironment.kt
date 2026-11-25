package vn.unicom.thi360.core.config

import vn.unicom.thi360.BuildConfig

enum class Environment(val flavorName: String) {
    DEV("dev"),
    DEMO("demo");

    companion object {
        fun fromFlavor(name: String): Environment =
            entries.firstOrNull { it.flavorName.equals(name, ignoreCase = true) }
                ?: error("Unknown environment flavor: $name")
    }
}

object AppEnvironment {
    val environment: Environment = Environment.fromFlavor(BuildConfig.ENVIRONMENT)
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
    val webBaseUrl: String = BuildConfig.WEB_BASE_URL
}
