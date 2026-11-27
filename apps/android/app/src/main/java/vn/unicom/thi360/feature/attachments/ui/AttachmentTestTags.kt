package vn.unicom.thi360.feature.attachments.ui

import java.util.UUID

object AttachmentTestTags {
    const val ATTACHMENTS_CONTAINER = "attachments_container"
    const val ATTACHMENTS_LABEL = "attachments_label"
    const val ATTACHMENTS_OPEN_ERROR = "attachments_open_error"

    fun fileRow(assetId: UUID): String = "attachment_file_$assetId"
    fun audioPlayer(assetId: UUID): String = "audio_player_$assetId"
    fun audioFileName(assetId: UUID): String = "audio_player_filename_$assetId"
    fun audioPrimaryButton(assetId: UUID): String = "audio_player_button_$assetId"
    fun audioPlaysBadge(assetId: UUID): String = "audio_player_badge_$assetId"
    fun audioTimeLabel(assetId: UUID): String = "audio_player_time_$assetId"
    fun audioProgressBar(assetId: UUID): String = "audio_player_progress_$assetId"
    fun audioBanner(assetId: UUID): String = "audio_player_banner_$assetId"
}
