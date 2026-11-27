package vn.unicom.thi360.shell.routes

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.legal.LegalDocumentScreen
import vn.unicom.thi360.core.legal.LegalId
import vn.unicom.thi360.core.legal.LegalRepository
import vn.unicom.thi360.shell.Destination

fun NavGraphBuilder.legalGraph(
    tab: Destination,
    navigator: AppNavigator,
    legalRepository: LegalRepository? = null,
    webBaseUrl: String = AppEnvironment.webBaseUrl,
    onOpenOnWeb: ((String) -> Unit)? = null,
) {
    composable(
        route = AppRoute.tabRoute(tab, AppRoute.LEGAL_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_LEGAL_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val rawId = backStackEntry.arguments?.getString(AppRoute.ARG_LEGAL_ID)
        val legalId = LegalId.fromRouteValue(rawId) ?: LegalId.TERMS
        val context = LocalContext.current
        val activeRepository = remember(legalRepository, context) {
            legalRepository ?: LegalRepository.create(context)
        }
        LegalDocumentScreen(
            id = legalId,
            repository = activeRepository,
            webBaseUrl = webBaseUrl,
            onBack = { navigator.back() },
            onOpenOnWeb = onOpenOnWeb,
        )
    }
}

fun NavGraphBuilder.authLegalGraph(
    onBack: () -> Unit,
    legalRepository: LegalRepository? = null,
    webBaseUrl: String = AppEnvironment.webBaseUrl,
    onOpenOnWeb: ((String) -> Unit)? = null,
) {
    composable(
        route = AppRoute.LEGAL_PATTERN,
        arguments = listOf(
            navArgument(AppRoute.ARG_LEGAL_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val rawId = backStackEntry.arguments?.getString(AppRoute.ARG_LEGAL_ID)
        val legalId = LegalId.fromRouteValue(rawId) ?: LegalId.TERMS
        val context = LocalContext.current
        val activeRepository = remember(legalRepository, context) {
            legalRepository ?: LegalRepository.create(context)
        }
        LegalDocumentScreen(
            id = legalId,
            repository = activeRepository,
            webBaseUrl = webBaseUrl,
            onBack = onBack,
            onOpenOnWeb = onOpenOnWeb,
        )
    }
}
