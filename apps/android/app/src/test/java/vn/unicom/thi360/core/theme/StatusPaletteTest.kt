package vn.unicom.thi360.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.nio.file.Files
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.ContractFixtureLoader

class StatusPaletteTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun statusColorsContractFile(): File {
        val contractsMobile = ContractFixtureLoader.openApiSpecFile().parentFile
            ?: error("Cannot locate contracts/mobile directory")
        return File(contractsMobile, "appearance/status-colors.json")
    }

    private fun parseHexColor(hex: String): Color {
        val clean = hex.trim().removePrefix("#")
        require(clean.length == 6 || clean.length == 8) { "Invalid hex color: $hex" }
        val argb = if (clean.length == 6) {
            (0xFF000000L or clean.toLong(16)).toInt()
        } else {
            clean.toLong(16).toInt()
        }
        return Color(argb)
    }

    private fun formatHexRgb(color: Color): String =
        String.format("#%06X", color.toArgb() and 0xFFFFFF)

    private fun parseModeColors(modeObj: JsonObject): Map<String, Color> {
        val expectedKeys = listOf(
            "success",
            "successBg",
            "warning",
            "warningBg",
            "info",
            "infoBg",
            "dangerSolid",
            "onDanger",
        )
        return expectedKeys.associateWith { key ->
            val raw = modeObj[key]?.jsonPrimitive?.content
                ?: error("Missing key '$key' in status-colors.json mode block")
            parseHexColor(raw)
        }
    }

    private fun comparePaletteAgainstFile(file: File): List<String> {
        val root = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        val lightMap = parseModeColors(root["light"]!!.jsonObject)
        val darkMap = parseModeColors(root["dark"]!!.jsonObject)

        val diffs = mutableListOf<String>()
        fun check(modeLabel: String, actual: StatusColors, expected: Map<String, Color>) {
            val actualMap = mapOf(
                "success" to actual.success,
                "successBg" to actual.successBg,
                "warning" to actual.warning,
                "warningBg" to actual.warningBg,
                "info" to actual.info,
                "infoBg" to actual.infoBg,
                "dangerSolid" to actual.dangerSolid,
                "onDanger" to actual.onDanger,
            )
            for ((key, expectedColor) in expected) {
                val actualColor = actualMap.getValue(key)
                if (actualColor.toArgb() != expectedColor.toArgb()) {
                    diffs += "$modeLabel.$key: expected ${formatHexRgb(expectedColor)} but was ${formatHexRgb(actualColor)}"
                }
            }
        }
        check("light", StatusPalette.Light, lightMap)
        check("dark", StatusPalette.Dark, darkMap)
        return diffs
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = foreground.luminance().toDouble()
        val l2 = background.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun eightStatusColorsMatchContractFileInLightAndDarkAndDriftDetectionCatchesEdits() {
        val contractFile = statusColorsContractFile()
        assertTrue(
            "Missing contracts/mobile/appearance/status-colors.json (waiting for WEB-1 merge): ${contractFile.canonicalPath}",
            contractFile.isFile,
        )

        val diffs = comparePaletteAgainstFile(contractFile)
        assertTrue("StatusPalette drifted from status-colors.json:\n${diffs.joinToString("\n")}", diffs.isEmpty())

        // Prove reading dynamically catches drift: mutate a single value in a temp copy.
        val tempCopy = Files.createTempFile("status-colors-drift-", ".json").toFile()
        try {
            val originalText = contractFile.readText(Charsets.UTF_8)
            val mutated = originalText.replaceFirst(Regex("#[0-9A-Fa-f]{6}"), "#123456")
            assertNotEquals("Temp drift mutation must alter contract file text", originalText, mutated)
            tempCopy.writeText(mutated, Charsets.UTF_8)

            val driftDiffs = comparePaletteAgainstFile(tempCopy)
            assertTrue(
                "Mutating a color in status-colors.json copy must produce a comparison mismatch",
                driftDiffs.isNotEmpty(),
            )
        } finally {
            tempCopy.delete()
        }
    }

    @Test
    fun fiveStatusAndDangerPairsMeetWcag45To1ContrastInLightAndDark() {
        val brand = BrandPalette.Default
        val checks = listOf(
            Triple("light.success/successBg", StatusPalette.Light.success, StatusPalette.Light.successBg),
            Triple("light.warning/warningBg", StatusPalette.Light.warning, StatusPalette.Light.warningBg),
            Triple("light.info/infoBg", StatusPalette.Light.info, StatusPalette.Light.infoBg),
            Triple("light.onDanger/dangerSolid", StatusPalette.Light.onDanger, StatusPalette.Light.dangerSolid),
            Triple("light.dangerText/dangerBg", brand.light.dangerText, brand.light.dangerBg),
            Triple("dark.success/successBg", StatusPalette.Dark.success, StatusPalette.Dark.successBg),
            Triple("dark.warning/warningBg", StatusPalette.Dark.warning, StatusPalette.Dark.warningBg),
            Triple("dark.info/infoBg", StatusPalette.Dark.info, StatusPalette.Dark.infoBg),
            Triple("dark.onDanger/dangerSolid", StatusPalette.Dark.onDanger, StatusPalette.Dark.dangerSolid),
            Triple("dark.dangerText/dangerBg", brand.dark.dangerText, brand.dark.dangerBg),
        )

        for ((label, fg, bg) in checks) {
            val ratio = contrastRatio(fg, bg)
            assertTrue(
                "Contrast ratio for $label was ${"%.2f".format(ratio)}, expected >= 4.50",
                ratio >= 4.5,
            )
        }
        assertEquals(10, checks.size)
    }
}
