package vn.unicom.thi360.feature.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.login.LoginIcons
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.feature.recovery.RecoveryIcons

@Composable
internal fun thi360FieldColors(): TextFieldColors {
    val colors = Thi360Theme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primaryFill,
        unfocusedBorderColor = colors.borderStrong,
        errorBorderColor = colors.dangerText,
        focusedLabelColor = colors.primaryText,
        unfocusedLabelColor = colors.textMuted,
        errorLabelColor = colors.dangerText,
        focusedTextColor = colors.text,
        unfocusedTextColor = colors.text,
        cursorColor = colors.primaryFill,
        focusedContainerColor = colors.bgSurface,
        unfocusedContainerColor = colors.bgSurface,
        disabledContainerColor = colors.bgSubtle,
    )
}

@Composable
internal fun RecoveryTopBar(
    title: String,
    onNavigationClick: (() -> Unit)? = null,
    navigationContentDescription: String? = null,
    navigationTestTag: String? = null,
    useCloseIcon: Boolean = false,
    navigationEnabled: Boolean = true,
) {
    val colors = Thi360Theme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onNavigationClick != null) {
            IconButton(
                onClick = onNavigationClick,
                enabled = navigationEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .then(if (navigationTestTag != null) Modifier.testTag(navigationTestTag) else Modifier),
            ) {
                Icon(
                    imageVector = if (useCloseIcon) RecoveryIcons.Close else RecoveryIcons.ArrowBack,
                    contentDescription = navigationContentDescription,
                    tint = colors.textSecondary,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            modifier = Modifier
                .padding(start = if (onNavigationClick == null) 20.dp else 4.dp)
                .semantics { heading() },
        )
    }
}

@Composable
internal fun RecoveryBanner(
    message: String,
    isError: Boolean,
    tag: String,
    isLinkInfo: Boolean = false,
) {
    val colors = Thi360Theme.colors
    val containerColor = if (isError) colors.dangerBg else colors.primaryContainer
    val contentColor = if (isError) colors.dangerText else colors.primaryText
    val icon = when {
        isError -> LoginIcons.AlertCircle
        isLinkInfo -> RecoveryIcons.Link
        else -> LoginIcons.Check
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
internal fun SecurePasswordField(
    state: TextFieldState,
    label: String,
    visible: Boolean,
    enabled: Boolean,
    errorText: String?,
    showContentDescription: String,
    hideContentDescription: String,
    fieldTag: String,
    errorTag: String,
    toggleTag: String,
    imeAction: ImeAction,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    onKeyboardAction: (() -> Unit)? = null,
) {
    val colors = Thi360Theme.colors
    OutlinedSecureTextField(
        state = state,
        label = { Text(label) },
        enabled = enabled,
        isError = errorText != null,
        textObfuscationMode = if (visible) {
            TextObfuscationMode.Visible
        } else {
            TextObfuscationMode.Hidden
        },
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(toggleTag),
            ) {
                Icon(
                    imageVector = if (visible) LoginIcons.VisibilityOff else LoginIcons.Visibility,
                    contentDescription = if (visible) hideContentDescription else showContentDescription,
                    tint = colors.textMuted,
                )
            }
        },
        supportingText = if (errorText != null) {
            {
                Text(
                    text = errorText,
                    color = colors.dangerText,
                    modifier = Modifier.testTag(errorTag),
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        onKeyboardAction = onKeyboardAction?.let { handler -> KeyboardActionHandler { handler() } },
        colors = thi360FieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .testTag(fieldTag),
    )
}

@Composable
fun PasswordRulesChecklist(
    evaluation: PasswordRuleEvaluation,
    modifier: Modifier = Modifier,
) {
    val groupLabel = stringResource(R.string.password_rules_label)
    val items = listOf(
        Triple(stringResource(R.string.password_rule_length), evaluation.hasValidLength, PasswordTestTags.RULE_LENGTH),
        Triple(stringResource(R.string.password_rule_upper), evaluation.hasUppercase, PasswordTestTags.RULE_UPPER),
        Triple(stringResource(R.string.password_rule_lower), evaluation.hasLowercase, PasswordTestTags.RULE_LOWER),
        Triple(stringResource(R.string.password_rule_digit), evaluation.hasDigit, PasswordTestTags.RULE_DIGIT),
        Triple(stringResource(R.string.password_rule_special), evaluation.hasSpecial, PasswordTestTags.RULE_SPECIAL),
    )
    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = groupLabel },
    ) {
        val useSingleColumn = maxWidth < 320.dp || fontScale >= 1.5f
        if (useSingleColumn) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { (label, satisfied, tag) ->
                    PasswordRuleItem(
                        label = label,
                        satisfied = satisfied,
                        tag = tag,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { (label, satisfied, tag) ->
                            PasswordRuleItem(
                                label = label,
                                satisfied = satisfied,
                                tag = tag,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordRuleItem(
    label: String,
    satisfied: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val tint = if (satisfied) colors.primaryText else colors.textMuted
    val statusText = stringResource(
        if (satisfied) R.string.password_rule_satisfied else R.string.password_rule_unsatisfied,
    )

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { stateDescription = statusText }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (satisfied) LoginIcons.Check else RecoveryIcons.CircleOutline,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}
