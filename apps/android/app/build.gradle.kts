import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.openapi.generator)
}

val openApiGeneratedDir = layout.buildDirectory.dir("generated/openapi")
val openApiSpecFile = rootProject.file("../../contracts/mobile/openapi-mobile.json")

android {
    namespace = "vn.unicom.thi360"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "vn.unicom.thi360"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystores/debug.keystore")
            storePassword = "thi360debug"
            keyAlias = "thi360-debug"
            keyPassword = "thi360debug"
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "ENVIRONMENT", "\"dev\"")
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "WEB_BASE_URL", "\"http://10.0.2.2:3000\"")
            manifestPlaceholders["webHost"] = "10.0.2.2"
        }
        create("demo") {
            dimension = "environment"
            buildConfigField("String", "ENVIRONMENT", "\"demo\"")
            buildConfigField("String", "API_BASE_URL", "\"https://thi360.zone17.click\"")
            buildConfigField("String", "WEB_BASE_URL", "\"https://thi360.zone17.click\"")
            manifestPlaceholders["webHost"] = "thi360.zone17.click"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }

    sourceSets {
        getByName("main") {
            kotlin.directories.add(openApiGeneratedDir.get().dir("src/main/kotlin").asFile.path)
        }
    }
}

val generateMobileOpenApiClient = tasks.register<GenerateTask>("generateMobileOpenApiClient") {
    inputs.file(openApiSpecFile)
    generatorName.set("kotlin")
    // Swagger 3.1 ReferenceUtils passes inputSpec to java.net.URI; file URI works on both Windows and Linux.
    inputSpec.set(openApiSpecFile.canonicalFile.toURI().toString())
    outputDir.set(openApiGeneratedDir)
    packageName.set("vn.unicom.thi360.core.network.generated")
    apiPackage.set("vn.unicom.thi360.core.network.generated.api")
    modelPackage.set("vn.unicom.thi360.core.network.generated.model")
    cleanupOutput.set(true)
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "library" to "jvm-retrofit2",
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "java8",
            "enumPropertyNaming" to "UPPERCASE",
            "omitGradleWrapper" to "true",
        ),
    )
}

tasks.named("preBuild").configure {
    dependsOn(generateMobileOpenApiClient)
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(generateMobileOpenApiClient)
}

roborazzi {
    outputDir.set(file("src/test/snapshots"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
