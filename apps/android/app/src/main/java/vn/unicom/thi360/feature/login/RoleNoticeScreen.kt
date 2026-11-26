package vn.unicom.thi360.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme

@Composable
fun RoleNoticeScreen(
    username: String,
    onSwitchAccount: () -> Unit,
    onSelectLocale: (AppLocale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val currentLocale = LocalAppLocale.current
    val nextLocale = if (currentLocale == AppLocale.VI) AppLocale.EN else AppLocale.VI

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(LoginTestTags.ROLE_NOTICE_SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageToggleIconButton(onClick = { onSelectLocale(nextLocale) })
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colors.bgSubtle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = LoginIcons.UserBlocked,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(44.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.role_notice_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag(LoginTestTags.ROLE_NOTICE_TITLE),
                )

                val bodyText = if (username.isNotBlank()) {
                    val formatted = stringResource(R.string.role_notice_body_with_username, username)
                    val idx = formatted.indexOf(username)
                    if (idx >= 0) {
                        buildAnnotatedString {
                            append(formatted.substring(0, idx))
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = colors.text)) {
                                append(username)
                            }
                            append(formatted.substring(idx + username.length))
                        }
                    } else {
                        buildAnnotatedString { append(formatted) }
                    }
                } else {
                    buildAnnotatedString {
                        append(stringResource(R.string.role_notice_body_generic))
                    }
                }

                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(LoginTestTags.ROLE_NOTICE_BODY),
                )

                Text(
                    text = stringResource(R.string.role_notice_guidance),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Button(
                onClick = onSwitchAccount,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(LoginTestTags.ROLE_NOTICE_SWITCH_ACCOUNT),
            ) {
                Text(text = stringResource(R.string.role_notice_switch_account))
            }
        }
    }
}
