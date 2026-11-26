package vn.unicom.thi360.core.legal

import java.time.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class LegalId(
    val routeValue: String,
    val webPath: String,
) {
    @SerialName("terms")
    TERMS("terms", "/dieu-khoan-giao-dich-chung"),

    @SerialName("privacy")
    PRIVACY("privacy", "/chinh-sach-bao-mat-thong-tin");

    fun assetFileName(localeCode: String): String = "$routeValue.$localeCode.json"

    fun webUrl(webBaseUrl: String): String = "${webBaseUrl.trimEnd('/')}$webPath"

    companion object {
        fun fromRouteValue(value: String?): LegalId? =
            entries.firstOrNull { it.routeValue == value }
    }
}

@Serializable
enum class LegalStatus {
    @SerialName("draft")
    DRAFT,

    @SerialName("published")
    PUBLISHED,
}

object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateIso", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface LegalBlock {
    @Serializable
    @SerialName("heading")
    data class Heading(
        val level: Int,
        val text: String,
    ) : LegalBlock {
        init {
            require(level == 2 || level == 3) { "Unsupported legal heading level: $level" }
        }
    }

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val text: String,
    ) : LegalBlock

    @Serializable
    @SerialName("list")
    data class ListBlock(
        val ordered: Boolean,
        val items: List<String>,
    ) : LegalBlock

    @Serializable
    @SerialName("table")
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : LegalBlock
}

@Serializable
data class LegalDocument(
    val id: LegalId,
    val locale: String,
    val version: String,
    val status: LegalStatus,
    @Serializable(with = LocalDateIsoSerializer::class)
    val effectiveDate: LocalDate? = null,
    val title: String,
    val note: String? = null,
    val contentHash: String? = null,
    val blocks: List<LegalBlock>,
)

sealed class LegalLoadError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Missing(val assetPath: String, cause: Throwable? = null) :
        LegalLoadError("Missing legal asset: $assetPath", cause)

    class Malformed(val assetPath: String, cause: Throwable? = null) :
        LegalLoadError("Malformed legal asset: $assetPath", cause)
}
