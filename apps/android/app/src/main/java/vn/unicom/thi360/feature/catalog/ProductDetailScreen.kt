package vn.unicom.thi360.feature.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateView
import vn.unicom.thi360.core.ui.RefreshableContainer
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.routes.AppNavigator

@Composable
fun ProductDetailRoute(
    code: String,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val injected = LocalCatalogDependencies.current
    val dependencies = remember(injected, context) {
        injected ?: CatalogDependencies.defaultFor(context)
    }

    ProductDetailPane(
        code = code,
        dependencies = dependencies,
        navigator = navigator,
        showBackButton = true,
        onBack = { navigator.back() },
        onNavigateToCatalog = {
            if (!navigator.back()) {
                navigator.switchTab(Destination.Home, reload = false)
            }
        },
        onOpenNestedProduct = { childCode ->
            navigator.openProduct(childCode)
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductDetailPane(
    code: String,
    dependencies: CatalogDependencies,
    navigator: AppNavigator,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onOpenNestedProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val tapGuard = rememberDoubleTapGuard(dependencies.clockMillis)
    val controller = remember(code, dependencies) {
        ProductDetailController(
            code = code,
            gateway = dependencies.gateway,
            sessionManager = dependencies.sessionManager,
            connectivityMonitor = dependencies.connectivityMonitor,
        )
    }

    DisposableEffect(controller) {
        controller.loadInitial()
        onDispose { controller.clear() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        var skippedFirstResume = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skippedFirstResume) {
                    controller.refreshAccessOnReturn()
                } else {
                    skippedFirstResume = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state by controller.state.collectAsStateWithLifecycle()

    val onOpenCheckout: (String, Int, Long) -> Unit = { productCode, versionNo, price ->
        tapGuard.run {
            navigator.openCheckout(
                code = productCode,
                seenVersionNo = versionNo,
                seenPrice = price,
            )
        }
    }
    val onOpenPrepare: (String) -> Unit = { entitlementId ->
        tapGuard.run { navigator.openPrepare(entitlementId) }
    }
    val onOpenAttempt: (String) -> Unit = { attemptId ->
        tapGuard.run { navigator.openAttempt(attemptId) }
    }
    val onOpenMyExams: () -> Unit = {
        tapGuard.run { navigator.switchTab(Destination.MyExams) }
    }
    val onOpenExamGuarded: (String) -> Unit = { examCode ->
        tapGuard.run { onOpenNestedProduct(examCode) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(ProductDetailTestTags.SCREEN),
    ) {
        if (showBackButton) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgSurface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(ProductDetailTestTags.BACK_BUTTON),
                ) {
                    Icon(
                        imageVector = CatalogIcons.ArrowBack,
                        contentDescription = stringResource(R.string.storefront_catalog_backToCatalog),
                        tint = colors.text,
                    )
                }
                Text(
                    text = stringResource(R.string.storefront_catalog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = colors.border)
        }

        if (state.refreshFailed && state.loadState is LoadState.Loaded) {
            Surface(
                color = colors.dangerBg,
                border = BorderStroke(1.dp, colors.dangerText),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag(CatalogTestTags.REFRESH_ERROR_BANNER),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.errorPage_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dangerText,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = controller::pullToRefresh,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(text = stringResource(R.string.common_retry), color = colors.dangerText)
                    }
                }
            }
        }

        RefreshableContainer(
            isRefreshing = state.isRefreshing,
            onRefresh = controller::pullToRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LoadStateView(
                state = state.loadState,
                onRetry = controller::retry,
                emptyContent = {
                    ProductNotFoundView(onToCatalog = onNavigateToCatalog)
                },
                clockMillis = dependencies.clockMillis,
            ) { outcome ->
                when (outcome) {
                    is ProductOutcome.Exam -> ExamDetailContent(
                        detail = outcome.detail,
                        accessCell = state.accessCell,
                        apiBaseUrl = dependencies.apiBaseUrl,
                        imageLoader = dependencies.imageLoader,
                        supportContact = dependencies.supportContact,
                        onRetryAccess = controller::retryAccess,
                        onOpenCheckout = onOpenCheckout,
                        onOpenPrepare = onOpenPrepare,
                        onOpenAttempt = onOpenAttempt,
                        onOpenMyExams = onOpenMyExams,
                    )

                    is ProductOutcome.Bundle -> BundleDetailContent(
                        detail = outcome.detail,
                        sellable = outcome.sellable,
                        accessCell = state.accessCell,
                        supportContact = dependencies.supportContact,
                        onRetryAccess = controller::retryAccess,
                        onOpenExam = onOpenExamGuarded,
                        onOpenCheckout = onOpenCheckout,
                    )

                    is ProductOutcome.Gone -> ProductGoneView(
                        code = outcome.code,
                        name = outcome.name,
                        onToCatalog = onNavigateToCatalog,
                        onToMyExams = onOpenMyExams,
                    )

                    ProductOutcome.NotFound -> ProductNotFoundView(
                        onToCatalog = onNavigateToCatalog,
                    )

                    ProductOutcome.BundleUnavailable -> BundleUnavailableStatusView(
                        onToCatalog = onNavigateToCatalog,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProductGoneView(
    code: String,
    name: String?,
    onToCatalog: () -> Unit,
    onToMyExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val displayName = name?.trim()?.takeIf { it.isNotEmpty() } ?: code

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(ProductDetailTestTags.STATUS_GONE),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = status.warningBg,
            border = BorderStroke(1.dp, status.warning),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.storefront_gone_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = status.warning,
                )
                Text(
                    text = stringResource(R.string.storefront_gone_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.storefront_gone_description, displayName, code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = stringResource(R.string.storefront_gone_owned),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Button(
                    onClick = onToCatalog,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ProductDetailTestTags.STATUS_TO_CATALOG),
                ) {
                    Text(text = stringResource(R.string.storefront_gone_toCatalog))
                }
                OutlinedButton(
                    onClick = onToMyExams,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ProductDetailTestTags.STATUS_TO_MY_EXAMS),
                ) {
                    Text(text = stringResource(R.string.storefront_gone_myExams))
                }
            }
        }
    }
}

@Composable
internal fun ProductNotFoundView(
    onToCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(ProductDetailTestTags.STATUS_NOT_FOUND),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.storefront_notFound_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textMuted,
                )
                Text(
                    text = stringResource(R.string.storefront_notFound_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.storefront_notFound_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Button(
                    onClick = onToCatalog,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ProductDetailTestTags.STATUS_TO_CATALOG),
                ) {
                    Text(text = stringResource(R.string.storefront_notFound_toCatalog))
                }
            }
        }
    }
}

@Composable
internal fun BundleUnavailableStatusView(
    onToCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(ProductDetailTestTags.STATUS_UNAVAILABLE),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = status.warningBg,
            border = BorderStroke(1.dp, status.warning),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.bundle_unavailablePage_eyebrow),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = status.warning,
                )
                Text(
                    text = stringResource(R.string.bundle_unavailablePage_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.bundle_unavailablePage_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Button(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON),
                ) {
                    Text(text = stringResource(R.string.bundle_buyLabel))
                }
                OutlinedButton(
                    onClick = onToCatalog,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ProductDetailTestTags.STATUS_TO_CATALOG),
                ) {
                    Text(text = stringResource(R.string.bundle_unavailablePage_toCatalog))
                }
            }
        }
    }
}
