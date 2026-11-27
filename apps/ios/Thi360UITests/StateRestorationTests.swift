import XCTest

final class StateRestorationTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
    }

    func testSelectionAndDraftTextSurviveRotationAndSizeClassChanges() {
        let suite = "vn.unicom.thi360.uitest.state.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-state-probe",
            "-uitest-authenticated"
        ]
        app.launch()

        openAccountDestination(in: app)

        let textField = app.textFields["shell.probe.textField"]
        XCTAssertTrue(textField.waitForExistence(timeout: 5), "Probe text field should exist on Account screen")

        textField.tap()
        let sampleText = "Thi360 state check"
        textField.typeText(sampleText)
        XCTAssertEqual(textField.value as? String, sampleText)

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.staticTexts["Tài khoản"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields["shell.probe.textField"].value as? String, sampleText)

        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.staticTexts["Tài khoản"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields["shell.probe.textField"].value as? String, sampleText)

        let toggleButton = app.buttons["shell.probe.toggleSizeClass"]
        XCTAssertTrue(toggleButton.waitForExistence(timeout: 5))

        let wasCompact = app.otherElements["shell.layout.compactTabBar"].exists
        toggleButton.tap()

        if wasCompact {
            let switchedRegular = app.otherElements["shell.layout.phoneTwoPaneBook"].waitForExistence(timeout: 5)
                || app.otherElements["shell.layout.iPadSidebar"].waitForExistence(timeout: 5)
            XCTAssertTrue(switchedRegular, "Layout must transition from compact to regular")

            if app.otherElements["shell.layout.phoneTwoPaneBook"].exists {
                let leftTabBar = app.otherElements["shell.duo.leftTabBar"]
                XCTAssertTrue(leftTabBar.waitForExistence(timeout: 5), "iPhone regular book mode must render tab bar in left half")
                XCTAssertLessThanOrEqual(
                    leftTabBar.frame.maxX,
                    app.windows.firstMatch.frame.midX + 2,
                    "Left tab bar must remain within the left half of the two-pane book layout"
                )
            }
        } else {
            XCTAssertTrue(
                app.otherElements["shell.layout.compactTabBar"].waitForExistence(timeout: 5),
                "Layout must transition from regular to compact"
            )
        }

        XCTAssertTrue(app.staticTexts["Tài khoản"].waitForExistence(timeout: 5))
        let afterFirstSwitch = app.textFields["shell.probe.textField"]
        XCTAssertTrue(afterFirstSwitch.waitForExistence(timeout: 5))
        XCTAssertEqual(afterFirstSwitch.value as? String, sampleText)

        app.buttons["shell.probe.toggleSizeClass"].tap()

        if wasCompact {
            XCTAssertTrue(app.otherElements["shell.layout.compactTabBar"].waitForExistence(timeout: 5))
        } else {
            let restoredRegular = app.otherElements["shell.layout.phoneTwoPaneBook"].waitForExistence(timeout: 5)
                || app.otherElements["shell.layout.iPadSidebar"].waitForExistence(timeout: 5)
            XCTAssertTrue(restoredRegular)
        }

        XCTAssertTrue(app.staticTexts["Tài khoản"].waitForExistence(timeout: 5))
        let afterSecondSwitch = app.textFields["shell.probe.textField"]
        XCTAssertTrue(afterSecondSwitch.waitForExistence(timeout: 5))
        XCTAssertEqual(afterSecondSwitch.value as? String, sampleText)
    }

    private func openAccountDestination(in app: XCUIApplication) {
        let tabBarAccount = app.tabBars.buttons["Tài khoản"]
        if tabBarAccount.waitForExistence(timeout: 3) {
            tabBarAccount.tap()
            return
        }
        let sidebarOrDuoAccount = app.buttons["shell.dest.account"].firstMatch
        XCTAssertTrue(sidebarOrDuoAccount.waitForExistence(timeout: 5))
        sidebarOrDuoAccount.tap()
    }
}
