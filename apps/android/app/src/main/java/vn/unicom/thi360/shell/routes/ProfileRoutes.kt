package vn.unicom.thi360.shell.routes

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.feature.account.AccountDetailPane
import vn.unicom.thi360.feature.account.DeleteAccountScreen
import vn.unicom.thi360.feature.account.LocalProfileDependencies
import vn.unicom.thi360.feature.account.ProfileScreen
import vn.unicom.thi360.feature.account.VerifyEmailScreen
import vn.unicom.thi360.shell.Destination

fun NavGraphBuilder.profileGraph(
    tab: Destination,
    navigator: AppNavigator,
    embeddedInPane: Boolean = false,
    onOpenVerifyEmail: (() -> Unit)? = null,
    onOpenMyExams: (() -> Unit)? = null,
    onOpenOrders: (() -> Unit)? = null,
) {
    composable(AppRoute.tabRoute(tab, AppRoute.PROFILE_PATH)) {
        val shellWidth = LocalShellWidth.current
        val deps = LocalProfileDependencies.current
        if (!embeddedInPane && shellWidth == ShellWidth.TwoColumn) {
            LaunchedEffect(shellWidth) {
                deps?.draftStore?.preserveNextExit()
                deps?.onSelectAccountPane?.invoke(AccountDetailPane.PROFILE)
                navigator.back()
            }
        }
        ProfileScreen(
            embeddedInPane = embeddedInPane,
            onBack = { navigator.back() },
            onOpenVerifyEmail = onOpenVerifyEmail ?: { navigator.openProfileVerifyEmail() },
        )
    }

    composable(AppRoute.tabRoute(tab, AppRoute.PROFILE_VERIFY_EMAIL_PATH)) {
        VerifyEmailScreen(
            embeddedInPane = embeddedInPane,
            onBackToProfile = { navigator.back() },
        )
    }

    composable(AppRoute.tabRoute(tab, AppRoute.DELETE_ACCOUNT_PATH)) {
        val shellWidth = LocalShellWidth.current
        val deps = LocalProfileDependencies.current
        if (!embeddedInPane && shellWidth == ShellWidth.TwoColumn) {
            LaunchedEffect(shellWidth) {
                deps?.onSelectAccountPane?.invoke(AccountDetailPane.DELETE_ACCOUNT)
                navigator.back()
            }
        }
        DeleteAccountScreen(
            embeddedInPane = embeddedInPane,
            onBack = { navigator.back() },
            onOpenMyExams = onOpenMyExams ?: { navigator.switchTab(Destination.MyExams) },
            onOpenOrders = onOpenOrders ?: { navigator.openOrders() },
        )
    }
}
