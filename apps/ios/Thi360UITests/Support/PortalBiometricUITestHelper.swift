import Foundation
import XCTest

enum PortalBiometricUITestHelper {
    static func acceptFaceIDPermissionIfPresent(timeout: TimeInterval = 2.0) {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let alert = springboard.alerts.firstMatch
        guard alert.waitForExistence(timeout: timeout) else { return }

        // Only tap explicit Face ID permission buttons; never tap an arbitrary last button,
        // which is "Cancel" on Touch ID authentication sheets (e.g., iPad Air).
        for label in ["Allow", "OK", "Cho phép", "Đồng ý"] {
            let button = alert.buttons[label]
            if button.exists {
                button.tap()
                Thread.sleep(forTimeInterval: 0.4)
                return
            }
        }
    }

    static func waitForBiometricMatch(until element: XCUIElement, timeout: TimeInterval) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.exists {
                return
            }
            SimulatorBiometricHelper.triggerMatch()
            if element.waitForExistence(timeout: 1.0) {
                return
            }
        }
    }
}
