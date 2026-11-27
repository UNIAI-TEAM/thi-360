import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.hypot
import org.gradle.api.artifacts.dsl.LockMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.openapi.generator)
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

val openApiGeneratedDir = layout.buildDirectory.dir("generated/openapi")
val openApiSpecFile = rootProject.file("../../contracts/mobile/openapi-mobile.json")
val legalSourceDir = rootProject.file("../../contracts/mobile/legal")
val legalGeneratedAssetsDir = layout.buildDirectory.dir("generated/legal/assets")
val brandIconSourceFile = rootProject.file("../../docs/design/app-hoc-vien/brand/thi360-icon.png")

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
            buildConfigField("String", "SUPPORT_PHONE", "\"\"")
            buildConfigField("String", "SUPPORT_EMAIL", "\"\"")
            buildConfigField("String", "SUPPORT_HOURS", "\"\"")
            manifestPlaceholders["webHost"] = "10.0.2.2"
        }
        create("demo") {
            dimension = "environment"
            buildConfigField("String", "ENVIRONMENT", "\"demo\"")
            buildConfigField("String", "API_BASE_URL", "\"https://thi360.zone17.click\"")
            buildConfigField("String", "WEB_BASE_URL", "\"https://thi360.zone17.click\"")
            buildConfigField("String", "SUPPORT_PHONE", "\"\"")
            buildConfigField("String", "SUPPORT_EMAIL", "\"\"")
            buildConfigField("String", "SUPPORT_HOURS", "\"\"")
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
            assets.directories.add(legalGeneratedAssetsDir.get().asFile.path)
        }
    }
}

val generateMobileOpenApiClient = tasks.register<GenerateTask>("generateMobileOpenApiClient") {
    inputs.file(openApiSpecFile)
    generatorName.set("kotlin")
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
    typeMappings.set(
        mapOf(
            "object" to "JsonElement",
            "AnyType" to "JsonElement",
        ),
    )
    importMappings.set(
        mapOf(
            "JsonElement" to "kotlinx.serialization.json.JsonElement",
        ),
    )
}

val copyLegalContent = tasks.register<Sync>("copyLegalContent") {
    from(legalSourceDir) {
        include("*.json")
    }
    into(legalGeneratedAssetsDir.map { it.dir("legal") })
}

tasks.register("generateBrandBitmaps") {
    group = "branding"
    description = "Generates adaptive launcher foreground mipmaps and BrandMark drawables from thi360-icon.png."
    inputs.file(brandIconSourceFile)
    val customOutDir = providers.gradleProperty("brandBitmapsOutputDir")
    val defaultResDir = layout.projectDirectory.dir("src/main/res").asFile

    doLast {
        require(brandIconSourceFile.isFile) {
            "Missing brand icon source: ${brandIconSourceFile.canonicalPath}"
        }
        val source = ImageIO.read(brandIconSourceFile)
            ?: error("Unable to decode ${brandIconSourceFile.canonicalPath}")

        var minX = source.width
        var minY = source.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                if (((source.getRGB(x, y) ushr 24) and 0xFF) > 0) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        require(maxX >= minX && maxY >= minY) { "Source brand icon has no opaque pixels." }

        val contentW = maxX - minX + 1
        val contentH = maxY - minY + 1
        val cropped = BufferedImage(contentW, contentH, BufferedImage.TYPE_INT_ARGB)
        cropped.createGraphics().apply {
            drawImage(source, 0, 0, contentW, contentH, minX, minY, maxX + 1, maxY + 1, null)
            dispose()
        }

        val contentCenterX = contentW / 2.0
        val contentCenterY = contentH / 2.0
        var maxContentRadius = 0.0
        for (y in 0 until contentH) {
            for (x in 0 until contentW) {
                if (((cropped.getRGB(x, y) ushr 24) and 0xFF) > 0) {
                    val dist = hypot((x + 0.5) - contentCenterX, (y + 0.5) - contentCenterY)
                    if (dist > maxContentRadius) maxContentRadius = dist
                }
            }
        }

        val outRoot = customOutDir.orNull?.let { File(it) } ?: defaultResDir
        val densities = linkedMapOf(
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0,
        )

        fun renderScaled(src: BufferedImage, canvasW: Int, canvasH: Int, drawW: Int, drawH: Int): BufferedImage {
            val out = BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB)
            val g: Graphics2D = out.createGraphics()
            g.composite = AlphaComposite.Src
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val dx = (canvasW - drawW) / 2
            val dy = (canvasH - drawH) / 2
            g.drawImage(src, dx, dy, drawW, drawH, null)
            g.dispose()
            return out
        }

        for ((qualifier, scale) in densities) {
            val fgCanvas = (108.0 * scale).toInt()
            val safeRadius = 33.0 * scale
            val usableRadius = safeRadius - 1.5
            val fitScale = minOf(usableRadius / maxContentRadius, 1.0)
            val drawW = floor(contentW * fitScale).toInt().coerceAtLeast(1)
            val drawH = floor(contentH * fitScale).toInt().coerceAtLeast(1)
            val fgImage = renderScaled(cropped, fgCanvas, fgCanvas, drawW, drawH)

            val center = fgCanvas / 2.0
            for (y in 0 until fgCanvas) {
                for (x in 0 until fgCanvas) {
                    val alpha = (fgImage.getRGB(x, y) ushr 24) and 0xFF
                    if (alpha > 0) {
                        val distCenter = hypot((x + 0.5) - center, (y + 0.5) - center)
                        val distCorner = hypot(x - center, y - center)
                        if (distCenter > safeRadius || distCorner > safeRadius) {
                            fgImage.setRGB(x, y, 0x00000000)
                        }
                    }
                }
            }

            val mipmapDir = File(outRoot, "mipmap-$qualifier").apply { mkdirs() }
            ImageIO.write(fgImage, "PNG", File(mipmapDir, "ic_launcher_foreground.png"))

            val markCanvas = (96.0 * scale).toInt()
            val markScale = minOf(markCanvas.toDouble() / source.width, markCanvas.toDouble() / source.height, 1.0)
            val markW = floor(source.width * markScale).toInt().coerceAtLeast(1)
            val markH = floor(source.height * markScale).toInt().coerceAtLeast(1)
            val markImage = renderScaled(source, markCanvas, markCanvas, markW, markH)

            val drawableDir = File(outRoot, "drawable-$qualifier").apply { mkdirs() }
            ImageIO.write(markImage, "PNG", File(drawableDir, "ic_brand_mark.png"))
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateMobileOpenApiClient, copyLegalContent)
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(generateMobileOpenApiClient)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyLegalContent)
}

tasks.matching { it.name.startsWith("generate") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyLegalContent)
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
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // AND-PAY: Custom Tabs browser launcher for VNPAY checkout (R-39, Spec 7.1, B1.13).
    implementation(libs.androidx.browser)

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
