import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AttemptLogRedactionTests: XCTestCase {
    func testDemoLogsNeverExposeAnswersTokensOrAttemptSessionHeader() async throws {
        #if THI360_DEMO
        XCTAssertEqual(AppEnvironment.current.kind, .demo)
        #endif

        let accessTokenSentinel = "demo-attempt-access-secret-99102"
        let refreshTokenSentinel = "demo-attempt-refresh-secret-88203"
        let sessionSentinel = "sess-secret-9f8e7d6c-attempt-001"
        let singleChoiceSentinel = "opt-secret-choice-77304"
        let shortTextSentinel = "ShortAnswerSecretPhrase-66405"
        let clozeTextSentinel = "ClozeSecretToken-55506"
        let essayHtmlSentinel = "<p>EssaySecretParagraph-44607</p>"
        let finalAnswerSentinel = "FinalAnswerSubmitSecret-33708"
        let latexMathSentinel = "SecretMathFormula-11223"
        let clozeLatexSentinel = "ClozeLaTeXSecret-22334"
        let escapedQuoteTextSentinel = "EscapedQuoteAnswerSecret-44556"
        let colonSessionSentinel = "sess-colon-secret-33445"

        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let items = server.leafItemIds(inPartIndex: 0)
        XCTAssertGreaterThanOrEqual(items.count, 1)
        let item1 = items[0]
        let item2 = items.count > 1 ? items[1] : items[0]

        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: accessTokenSentinel,
                refreshToken: refreshTokenSentinel,
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                username: "student.demo",
                accountId: "account-demo-1"
            )
        )
        let logSink = InMemoryLogSink()
        let api = server.makeAttemptAPI(
            tokenStore: tokenStore,
            environment: .demo,
            logSink: logSink
        )

        let (_, store, rootURL) = try AttemptEngineTestHarness.makeStore(accountId: "account-demo-1")
        defer { try? FileManager.default.removeItem(at: rootURL) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity(initialOnline: true)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-demo-1",
            api: api,
            store: store,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { sessionSentinel }
        )
        defer { engine.dispose() }

        let claimed = await clock.waitUntil {
            engine.state.session == .editing(sessionId: sessionSentinel)
        }
        XCTAssertTrue(claimed)

        engine.answer(itemId: item1, value: .singleChoice(optionId: singleChoiceSentinel))
        await clock.settle()
        engine.answer(itemId: item1, value: .shortAnswer(text: shortTextSentinel))
        await clock.advance(by: .milliseconds(900))
        engine.answer(itemId: item1, value: .cloze(blanks: ["b1": clozeTextSentinel]))
        await clock.advance(by: .milliseconds(900))
        engine.answer(itemId: item1, value: .essay(html: essayHtmlSentinel, wordCount: 2))
        await clock.advance(by: .seconds(3))

        let saved = await clock.waitUntil {
            engine.state.save == .saved
        }
        XCTAssertTrue(saved)

        await clock.advance(by: .seconds(30))
        let heartbeatSent = await clock.waitUntil { server.callCount(for: .heartbeat) >= 1 }
        XCTAssertTrue(heartbeatSent)

        engine.answer(itemId: item2, value: .shortAnswer(text: finalAnswerSentinel))
        engine.submit()
        let submitted = await clock.waitUntil {
            engine.state.closed == .submitted
        }
        XCTAssertTrue(submitted)

        let demoLogger = RedactingAppLogger(environment: .demo, sink: logSink)
        demoLogger.logHTTP(
            method: "PUT",
            path: "/api/v1/me/attempts/\(server.attemptId)/answers",
            statusCode: 200,
            headers: [
                ("Authorization", "Bearer \(accessTokenSentinel)"),
                ("X-Attempt-Session", sessionSentinel)
            ]
        )
        demoLogger.log(
            .info,
            category: "attempt",
            message: #"{"X-Attempt-Session":"\#(sessionSentinel)","answer":{"type":"SHORT_ANSWER","text":"\#(shortTextSentinel)"},"html":"\#(essayHtmlSentinel)","finalAnswers":[{"itemId":"\#(item2)","text":"\#(finalAnswerSentinel)"}]}"#
        )
        demoLogger.log(
            .info,
            category: "attempt",
            message: #"{"changes":[{"itemId":"\#(item1)","seq":1,"answer":{"type":"ESSAY","html":"<span class=\"math-tex\">\\frac{\\sqrt{\#(latexMathSentinel)}}{2}</span>"}}]}"#
        )
        demoLogger.log(
            .info,
            category: "attempt",
            message: #"{"changes":[{"itemId":"\#(item1)","seq":2,"answer":{"type":"CLOZE","blanks":{"b1":"\\frac{\\sqrt{\#(clozeLatexSentinel)}}{2}"}}}]}"#
        )
        demoLogger.log(
            .info,
            category: "attempt",
            message: #"{"html":"<p class=\"lead\">\#(escapedQuoteTextSentinel)</p>","text":"quote \"\#(escapedQuoteTextSentinel)\""}"#
        )
        demoLogger.log(
            .info,
            category: "attempt",
            message: "X-Attempt-Session: \(colonSessionSentinel)"
        )

        let devSink = InMemoryLogSink()
        let devLogger = RedactingAppLogger(environment: .dev, sink: devSink)
        devLogger.logHTTP(
            method: "PUT",
            path: "/api/v1/me/attempts/\(server.attemptId)/answers",
            statusCode: 200,
            headers: [
                ("Authorization", "Bearer \(accessTokenSentinel)"),
                ("X-Attempt-Session", sessionSentinel)
            ]
        )

        let allEntries = logSink.recordedEntries + devSink.recordedEntries
        XCTAssertFalse(logSink.recordedEntries.isEmpty, "Expected demo HTTP and attempt logs to be captured")
        XCTAssertFalse(devSink.recordedEntries.isEmpty, "Expected dev HTTP log to be captured")

        let secrets = [
            accessTokenSentinel,
            refreshTokenSentinel,
            sessionSentinel,
            singleChoiceSentinel,
            shortTextSentinel,
            clozeTextSentinel,
            essayHtmlSentinel,
            finalAnswerSentinel,
            latexMathSentinel,
            clozeLatexSentinel,
            escapedQuoteTextSentinel,
            colonSessionSentinel
        ]
        for entry in allEntries {
            for secret in secrets {
                XCTAssertFalse(
                    entry.message.contains(secret),
                    "Log entry leaked sensitive attempt data '\(secret)': \(entry.message)"
                )
            }
        }
    }
}
