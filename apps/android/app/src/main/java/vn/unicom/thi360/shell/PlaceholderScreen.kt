package vn.unicom.thi360.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.brand.BrandMark
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme

object ShellTestTags {
    const val BOTTOM_BAR = "shell_bottom_bar"
    const val NAV_RAIL = "shell_nav_rail"
    const val PLACEHOLDER_EMPTY_TITLE = "placeholder_empty_title"
    const val ACCOUNT_SINGLE_PANE = "account_single_pane"
    const val ACCOUNT_TWO_PANE = "account_two_pane"
    const val LOCALE_OPTION_VI = "locale_option_vi"
    const val LOCALE_OPTION_EN = "locale_option_en"
    const val THEME_OPTION_LIGHT = "theme_option_light"
    const val THEME_OPTION_DARK = "theme_option_dark"

    fun navItem(destination: Destination): String = "nav_item_${destination.route}"
    fun screenTitle(destination: Destination): String = "screen_title_${destination.route}"
}

@Composable
fun PlaceholderTabScreen(
    destination: Destination,
    showCompactBrandHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val title = stringResource(destination.labelRes)
    val contentPadding = if (showCompactBrandHeader) 24.dp else 32.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = contentPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (showCompactBrandHeader) {
            BrandMark(markSize = 32.dp)
        }

        Text(
            text = title,
            style = if (showCompactBrandHeader) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = colors.text,
            modifier = Modifier
                .semantics { heading() }
                .testTag(ShellTestTags.screenTitle(destination)),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp),
            shape = RoundedCornerShape(24.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_empty_grid),
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = stringResource(R.string.placeholder_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE),
                )
                Text(
                    text = stringResource(R.string.placeholder_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
        }
    }
}

@Composable
fun AccountShellPlaceholder(
    isTwoPaneWidth: Boolean,
    preferences: AppPreferences,
    onSelectLocale: (AppLocale) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors

    if (isTwoPaneWidth) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .testTag(ShellTestTags.ACCOUNT_TWO_PANE)
                .padding(vertical = 20.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountPreferencesColumn(
                preferences = preferences,
                onSelectLocale = onSelectLocale,
                onSelectThemeMode = onSelectThemeMode,
                modifier = Modifier
                    .width(312.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            )
            VerticalDivider(color = colors.border)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.account_detail_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            }
        }
    } else {
        AccountPreferencesColumn(
            preferences = preferences,
            onSelectLocale = onSelectLocale,
            onSelectThemeMode = onSelectThemeMode,
            modifier = modifier
                .fillMaxSize()
                .testTag(ShellTestTags.ACCOUNT_SINGLE_PANE)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun AccountPreferencesColumn(
    preferences: AppPreferences,
    onSelectLocale: (AppLocale) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val selectedLocale = preferences.effectiveLocale
    val selectedMode = Thi360Theme.themeMode
    val buttonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colors.primaryContainer,
        activeContentColor = colors.primaryText,
        activeBorderColor = colors.borderStrong,
        inactiveContainerColor = colors.bgSurface,
        inactiveContentColor = colors.textSecondary,
        inactiveBorderColor = colors.border,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_account),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
            modifier = Modifier
                .semantics { heading() }
                .testTag(ShellTestTags.screenTitle(Destination.Account)),
        )

        Text(
            text = stringResource(R.string.account_placeholder_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )

        HorizontalDivider(color = colors.border)

        Text(
            text = stringResource(R.string.account_section_display),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primaryText,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.account_language_label),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedLocale == AppLocale.VI,
                    onClick = { onSelectLocale(AppLocale.VI) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = buttonColors,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ShellTestTags.LOCALE_OPTION_VI),
                    label = { Text(stringResource(R.string.account_language_vi)) },
                )
                SegmentedButton(
                    selected = selectedLocale == AppLocale.EN,
                    onClick = { onSelectLocale(AppLocale.EN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = buttonColors,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ShellTestTags.LOCALE_OPTION_EN),
                    label = { Text(stringResource(R.string.account_language_en)) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.account_theme_label),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode == ThemeMode.LIGHT,
                    onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = buttonColors,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ShellTestTags.THEME_OPTION_LIGHT),
                    label = { Text(stringResource(R.string.account_theme_light)) },
                )
                SegmentedButton(
                    selected = selectedMode == ThemeMode.DARK,
                    onClick = { onSelectThemeMode(ThemeMode.DARK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = buttonColors,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ShellTestTags.THEME_OPTION_DARK),
                    label = { Text(stringResource(R.string.account_theme_dark)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
