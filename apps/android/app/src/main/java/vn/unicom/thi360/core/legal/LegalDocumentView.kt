package vn.unicom.thi360.core.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.password.RecoveryTopBar

object LegalTestTags {
    const val SCREEN = "legal_document_screen"
    const val BACK_BUTTON = "legal_back_button"
    const val TITLE = "legal_document_title"
    const val EFFECTIVE_DATE = "legal_effective_date"
    const val DRAFT_BANNER = "legal_draft_banner"
    const val NOTE = "legal_document_note"
    const val OPEN_ON_WEB = "legal_open_on_web_button"
    const val ERROR_CONTAINER = "legal_error_container"
    const val ERROR_TITLE = "legal_error_title"
    const val ERROR_DESCRIPTION = "legal_error_description"

    fun block(index: Int): String = "legal_block_$index"
    fun tableScroll(index: Int): String = "legal_table_scroll_$index"
}

@Composable
fun LegalDocumentScreen(
    id: LegalId,
    repository: LegalRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    webBaseUrl: String = AppEnvironment.webBaseUrl,
    onOpenOnWeb: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val locale = LocalAppLocale.current
    val loadResult = remember(repository, id, locale) {
        repository.load(id, locale)
    }
    val targetUrl = remember(id, webBaseUrl) { id.webUrl(webBaseUrl) }
    val openWebAction: () -> Unit = {
        val custom = onOpenOnWeb
        if (custom != null) {
            custom(targetUrl)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
            context.startActivity(intent)
        }
    }

    val fallbackTitle = stringResource(
        when (id) {
            LegalId.TERMS -> R.string.legal_terms_title
            LegalId.PRIVACY -> R.string.legal_privacy_title
        },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Thi360Theme.colors.bgPage)
            .testTag(LegalTestTags.SCREEN),
    ) {
        RecoveryTopBar(
            title = loadResult.getOrNull()?.title ?: fallbackTitle,
            onNavigationClick = onBack,
            navigationContentDescription = stringResource(R.string.recovery_back),
            navigationTestTag = LegalTestTags.BACK_BUTTON,
        )

        val document = loadResult.getOrNull()
        if (document != null) {
            LegalDocumentView(
                document = document,
                onOpenOnWeb = openWebAction,
                modifier = Modifier.weight(1f),
            )
        } else {
            LegalDocumentErrorView(
                onOpenOnWeb = openWebAction,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun LegalDocumentView(
    document: LegalDocument,
    onOpenOnWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag(LegalTestTags.TITLE),
            )

            document.effectiveDate?.let { date ->
                Text(
                    text = stringResource(R.string.legal_effective_from, formatEffectiveDate(date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.testTag(LegalTestTags.EFFECTIVE_DATE),
                )
            }

            if (document.status == LegalStatus.DRAFT) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColors.warningBg,
                    border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag(LegalTestTags.DRAFT_BANNER),
                ) {
                    Text(
                        text = stringResource(R.string.legal_draft_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColors.warning,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            if (!document.note.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColors.infoBg,
                    border = BorderStroke(1.dp, statusColors.info.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag(LegalTestTags.NOTE),
                ) {
                    Text(
                        text = document.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColors.info,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            document.blocks.forEachIndexed { index, block ->
                LegalBlockItem(
                    block = block,
                    index = index,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenOnWeb,
                border = BorderStroke(1.dp, colors.borderStrong),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(LegalTestTags.OPEN_ON_WEB),
            ) {
                Text(
                    text = stringResource(R.string.legal_open_on_web),
                    color = colors.primaryText,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LegalBlockItem(
    block: LegalBlock,
    index: Int,
) {
    val colors = Thi360Theme.colors
    val blockTag = LegalTestTags.block(index)

    when (block) {
        is LegalBlock.Heading -> {
            val style = if (block.level == 2) {
                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = block.text,
                style = style,
                color = colors.text,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .semantics { heading() }
                    .testTag(blockTag),
            )
        }

        is LegalBlock.Paragraph -> {
            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.testTag(blockTag),
            )
        }

        is LegalBlock.ListBlock -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(blockTag),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.items.forEachIndexed { itemIdx, itemText ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        val marker = if (block.ordered) "${itemIdx + 1}." else "\u2022"
                        Text(
                            text = marker,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.textSecondary,
                        )
                        Text(
                            text = itemText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        is LegalBlock.Table -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(blockTag),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag(LegalTestTags.tableScroll(index)),
                ) {
                    val columnCount = maxOf(
                        block.headers.size,
                        block.rows.maxOfOrNull { it.size } ?: 0,
                    )
                    val columnWidth = (TABLE_COLUMN_WIDTH_DP * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)).dp
                    val tableWidth = columnWidth * columnCount
                    Column {
                        if (block.headers.isNotEmpty()) {
                            Row(
                                modifier = Modifier.background(colors.bgSubtle),
                            ) {
                                for (col in 0 until columnCount) {
                                    Text(
                                        text = block.headers.getOrElse(col) { "" },
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.text,
                                        softWrap = true,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier
                                            .width(columnWidth)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.width(tableWidth),
                                color = colors.border,
                            )
                        }

                        block.rows.forEachIndexed { rowIdx, row ->
                            Row {
                                for (col in 0 until columnCount) {
                                    Text(
                                        text = row.getOrElse(col) { "" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary,
                                        softWrap = true,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier
                                            .width(columnWidth)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                            }
                            if (rowIdx < block.rows.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.width(tableWidth),
                                    color = colors.border,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalDocumentErrorView(
    onOpenOnWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(LegalTestTags.ERROR_CONTAINER),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.errorPage_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag(LegalTestTags.ERROR_TITLE),
            )
            Text(
                text = stringResource(R.string.errorPage_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.testTag(LegalTestTags.ERROR_DESCRIPTION),
            )
            OutlinedButton(
                onClick = onOpenOnWeb,
                border = BorderStroke(1.dp, colors.borderStrong),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(LegalTestTags.OPEN_ON_WEB),
            ) {
                Text(
                    text = stringResource(R.string.legal_open_on_web),
                    color = colors.primaryText,
                )
            }
        }
    }
}

private val EFFECTIVE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private const val TABLE_COLUMN_WIDTH_DP = 168

internal fun formatEffectiveDate(date: LocalDate): String = EFFECTIVE_DATE_FORMATTER.format(date)
