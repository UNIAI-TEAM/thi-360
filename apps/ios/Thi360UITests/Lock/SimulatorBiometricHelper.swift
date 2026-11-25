import Foundation
import XCTest
import notify

enum SimulatorBiometricHelper {
    private static let enrollmentNotification = "com.apple.BiometricKit.enrollmentChanged"
    private static let pearlMatchNotification = "com.apple.BiometricKit_Sim.pearl.match"
    private static let pearlNoMatchNotification = "com.apple.BiometricKit_Sim.pearl.nomatch"
    private static let touchMatchNotification = "com.apple.BiometricKit_Sim.fingerTouch.match"
    private static let touchNoMatchNotification = "com.apple.BiometricKit_Sim.fingerTouch.nomatch"

    static func setEnrolled(_ enrolled: Bool) {
        var token: Int32 = 0
        notify_register_check(enrollmentNotification, &token)
        notify_set_state(token, enrolled ? 1 : 0)
        notify_post(enrollmentNotification)
        notify_cancel(token)
        Thread.sleep(forTimeInterval: 0.4)
    }

    static func reEnroll() {
        setEnrolled(false)
        setEnrolled(true)
    }

    static func triggerMatch() {
        notify_post(pearlMatchNotification)
        notify_post(touchMatchNotification)
    }

    static func triggerNoMatch() {
        notify_post(pearlNoMatchNotification)
        notify_post(touchNoMatchNotification)
    }

    static func acceptFaceIDPermissionIfPresent(timeout: TimeInterval = 2.0) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let alert = springboard.alerts.firstMatch
        guard alert.waitForExistence(timeout: timeout) else { return }

        for label in ["Allow", "OK", "Cho phép", "Đồng ý"] {
            let button = alert.buttons[label]
            if button.exists {
                button.tap()
                Thread.sleep(forTimeInterval: 0.4)
                return
            }
        }
        if alert.buttons.count > 0 {
            alert.buttons.element(boundBy: alert.buttons.count - 1).tap()
            Thread.sleep(forTimeInterval: 0.4)
        }
    }
}
