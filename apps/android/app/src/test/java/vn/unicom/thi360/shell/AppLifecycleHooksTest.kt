package vn.unicom.thi360.shell

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.shell.routes.InboundLinkRouter

@RunWith(RobolectricTestRunner::class)
class AppLifecycleHooksTest {

    @Test
    fun appStartedRunsOncePerProcessAndForegroundRunsOnEveryReturnOnlyWhenActiveAndUnlocked() {
        val router = InboundLinkRouter { "thi360.zone17.click" }
        var appStartedRuns = 0
        var foregroundRuns = 0
        router.onAppStarted { appStartedRuns++ }
        router.onForeground { foregroundRuns++ }

        // 1. SignedOut: neither hook runs.
        router.onSessionStateChanged(SessionState.SignedOut(SignOutReason.None))
        router.notifyAppStarted()
        router.notifyForegroundReturn()
        assertEquals(0, appStartedRuns)
        assertEquals(0, foregroundRuns)

        // 2. Locked: appStarted & foreground defer until unlocked.
        router.onSessionStateChanged(SessionState.Locked("student.demo"))
        router.notifyForegroundReturn()
        assertEquals(0, appStartedRuns)
        assertEquals(0, foregroundRuns)

        // 3. Unlocks to Active -> deferred appStarted (once) and deferred foreground run immediately.
        router.onSessionStateChanged(SessionState.Active("student.demo", isOffline = false))
        assertEquals(1, appStartedRuns)
        assertEquals(1, foregroundRuns)

        // 4. Extra notifyAppStarted in same process must NOT run again.
        router.notifyAppStarted()
        assertEquals(1, appStartedRuns)

        // 5. Each subsequent foreground return while Active runs foreground hook again.
        router.notifyForegroundReturn()
        router.notifyForegroundReturn()
        assertEquals(1, appStartedRuns)
        assertEquals(3, foregroundRuns)

        // 6. Returning to foreground while Locked defers until unlock, then runs once.
        router.onSessionStateChanged(SessionState.Locked("student.demo"))
        router.notifyForegroundReturn()
        assertEquals(3, foregroundRuns)
        router.onSessionStateChanged(SessionState.Active("student.demo", isOffline = false))
        assertEquals(4, foregroundRuns)
        assertEquals(1, appStartedRuns)
    }
}
