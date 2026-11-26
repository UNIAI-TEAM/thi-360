package vn.unicom.thi360.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import vn.unicom.thi360.R

const val COMPACT_WIDTH_BREAKPOINT_DP = 600
const val TWO_PANE_WIDTH_BREAKPOINT_DP = 840

enum class Destination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Home("home", R.string.nav_home, R.drawable.ic_nav_home),
    MyExams("my_exams", R.string.nav_my_exams, R.drawable.ic_nav_exams),
    History("history", R.string.nav_history, R.drawable.ic_nav_history),
    Account("account", R.string.nav_account, R.drawable.ic_nav_account);

    companion object {
        fun fromRouteOrNull(route: String?): Destination? =
            entries.firstOrNull { it.route == route }
    }
}

fun NavDestination?.shellDestination(): Destination? =
    this?.hierarchy?.firstNotNullOfOrNull { Destination.fromRouteOrNull(it.route) }

enum class NavLayout {
    BottomBar,
    Rail,
}

fun navLayoutForWidthDp(widthDp: Int): NavLayout =
    if (widthDp < COMPACT_WIDTH_BREAKPOINT_DP) NavLayout.BottomBar else NavLayout.Rail

fun isTwoPaneWidthDp(widthDp: Int): Boolean = widthDp >= TWO_PANE_WIDTH_BREAKPOINT_DP
