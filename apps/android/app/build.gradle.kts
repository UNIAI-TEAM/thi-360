import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
// AND-RICH: Reference rich-text corpus and samples bundled only in androidTest assets.
val richTextContractsDir = rootProject.file("../../contracts/mobile/rich-text")
val richTextSamplesFile = rootProject.file("../../docs/specs/fixtures/rich-text-samples.json")
val richTextAndroidTestAssetsDir = layout.buildDirectory.dir("generated/richcontent/androidTestAssets")

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
        getByName("androidTest") {
            assets.directories.add(richTextAndroidTestAssetsDir.get().asFile.path)
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

// AND-RICH: Copy read-only rich-text reference corpus into androidTest assets (excluded from main APK).
val copyRichTextAndroidTestAssets = tasks.register<Sync>("copyRichTextAndroidTestAssets") {
    from(richTextContractsDir) {
        include("formulas.json")
        include("formula-exemptions.json")
        include("web-renders/**")
    }
    from(richTextSamplesFile)
    into(richTextAndroidTestAssetsDir.map { it.dir("rich-text") })
}

// AND-RICH: Verify 16 KB APK zip alignment (zipalign -c -P 16) and ELF64 PT_LOAD p_align >= 16384 for every .so.
tasks.register("check16KbAlignment") {
    group = "verification"
    description = "Verifies 16 KB page-size alignment for all native libraries in devDebug APK."
    dependsOn("assembleDevDebug")
    val apkProvider = layout.buildDirectory.file("outputs/apk/dev/debug/app-dev-debug.apk")
    val sdkDirProvider = androidComponents.sdkComponents.sdkDirectory

    doLast {
        val apkFile = apkProvider.get().asFile
        require(apkFile.isFile) { "Missing APK for 16 KB alignment check: ${apkFile.canonicalPath}" }

        val buildToolsDir = File(sdkDirProvider.get().asFile, "build-tools")
        val zipalignNames = listOf("zipalign.exe", "zipalign")
        val zipalignBin = buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.firstNotNullOfOrNull { dir -> zipalignNames.map { File(dir, it) }.firstOrNull { it.isFile } }
            ?: error("Could not locate zipalign in ${buildToolsDir.canonicalPath}")

        val proc = ProcessBuilder(zipalignBin.absolutePath, "-c", "-P", "16", "4", apkFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val zipalignOut = proc.inputStream.bufferedReader().readText()
        val zipalignExit = proc.waitFor()
        if (zipalignExit != 0) {
            error("zipalign -c -P 16 failed (exit=$zipalignExit) for ${apkFile.name}:\n$zipalignOut")
        }

        val inspected64BitLibs = mutableListOf<String>()
        val failures = mutableListOf<String>()
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries().toList().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
            require(entries.isNotEmpty()) { "Expected native .so libraries inside ${apkFile.name}, found none." }
            for (entry in entries) {
                if (entry.method != ZipEntry.STORED) {
                    failures += "${entry.name}: compressed in APK (method=${entry.method}), must be STORED for 16 KB alignment"
                }
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (bytes.size < 64 ||
                    bytes[0] != 0x7F.toByte() ||
                    bytes[1] != 'E'.code.toByte() ||
                    bytes[2] != 'L'.code.toByte() ||
                    bytes[3] != 'F'.code.toByte()
                ) {
                    failures += "${entry.name}: invalid ELF header"
                    continue
                }
                val elfClass = bytes[4].toInt() and 0xFF
                if (elfClass != 2) {
                    // 32-bit ELF (e.g. armeabi-v7a) uses 4 KB pages on Android; 16 KB applies to 64-bit ABIs.
                    continue
                }
                inspected64BitLibs += entry.name
                val byteOrder = if ((bytes[5].toInt() and 0xFF) == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                val buf = ByteBuffer.wrap(bytes).order(byteOrder)
                val ePhOff = buf.getLong(32).toInt()
                val ePhEntSize = buf.getShort(54).toInt() and 0xFFFF
                val ePhNum = buf.getShort(56).toInt() and 0xFFFF
                var loadSegments = 0
                for (i in 0 until ePhNum) {
                    val phOff = ePhOff + i * ePhEntSize
                    val pType = buf.getInt(phOff)
                    if (pType == 1) {
                        loadSegments++
                        val pAlign = buf.getLong(phOff + 48)
                        if (pAlign < 16384L || pAlign % 16384L != 0L) {
                            failures += "${entry.name}: PT_LOAD[$i] p_align=$pAlign is not 16 KB aligned"
                        }
                    }
                }
                if (loadSegments == 0) {
                    failures += "${entry.name}: no PT_LOAD program headers found"
                }
            }
        }

        val requiredRatexAbis = listOf("lib/arm64-v8a/libratex_ffi.so", "lib/x86_64/libratex_ffi.so")
        for (req in requiredRatexAbis) {
            if (req !in inspected64BitLibs) {
                failures += "Missing required 64-bit RaTeX library in APK: $req"
            }
        }

        if (failures.isNotEmpty()) {
            error("16 KB alignment check failed:\n" + failures.joinToString("\n"))
        }
        logger.lifecycle(
            "check16KbAlignment PASSED: zipalign -c -P 16 OK, ${inspected64BitLibs.size} 64-bit .so libraries verified (${inspected64BitLibs.joinToString()}).",
        )
    }
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
    dependsOn(generateMobileOpenApiClient, copyLegalContent, copyRichTextAndroidTestAssets)
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(generateMobileOpenApiClient)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyLegalContent)
    if (name.contains("AndroidTest")) {
        dependsOn(copyRichTextAndroidTestAssets)
    }
}

tasks.matching { it.name.startsWith("generate") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyLegalContent)
    if (name.contains("AndroidTest")) {
        dependsOn(copyRichTextAndroidTestAssets)
    }
}

tasks.matching { it.name.contains("AndroidTest") && it.name.contains("Lint") }.configureEach {
    dependsOn(copyRichTextAndroidTestAssets)
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
    // AND-RICH: Pinned RaTeX LaTeX engine (0.1.16) and Jsoup HTML tree parser.
    implementation(libs.ratex.android)
    implementation(libs.jsoup)
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
