import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AttachmentsViewTests: XCTestCase {
    private func makeDummyOpener() -> AttachmentOpener {
        let tokenStore = InMemoryTokenStore(initialTokens: nil)
        let session = AttemptAssetSource.makeNoDiskCacheSession(protocolClasses: [RecordingAssetURLProtocol.self])
        let source = AttemptAssetSource(
            attemptId: "00000000-0000-7000-8000-000000000001",
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: TokenRefresher(tokenStore: tokenStore, refreshExecutor: { _ in
                throw MobileApiError.unexpectedResponse(statusCode: 401)
            }),
            session: session
        )
        return AttachmentOpener(assetSource: source, registry: AttemptAudioSessionRegistry())
    }

    private func makeAttemptAttachment(assetId: String, fileName: String, mime: String) -> Components.Schemas.AttemptAttachmentView {
        Components.Schemas.AttemptAttachmentView(
            assetId: assetId,
            fileName: fileName,
            mime: mime,
            url: "/api/v1/me/attempts/00000000-0000-7000-8000-000000000001/assets/\(assetId)"
        )
    }

    private func makeChildItem(itemId: String, attachments: [Components.Schemas.AttemptAttachmentView]) -> Components.Schemas.AttemptItemView {
        Components.Schemas.AttemptItemView(
            attachments: attachments,
            displayNo: 1,
            itemId: itemId,
            questionType: .SINGLE_CHOICE
        )
    }

    func testQuestionSetDeduplicatesSharedAudioAndHoistsOnlyChildAudio() throws {
        let sharedAudioId = "11111111-1111-4111-8111-111111111101"
        let childPdfId = "22222222-2222-4222-8222-222222222202"
        let childAudioId = "33333333-3333-4333-8333-333333333303"

        let parentAudio = makeAttemptAttachment(assetId: sharedAudioId, fileName: "passage-audio.mp3", mime: "audio/mpeg")
        let childWithSameAudio = makeChildItem(itemId: "child-2", attachments: [parentAudio])

        let deduped = AttachmentMerge.mergeQuestionAttachments(
            parentAttachments: [parentAudio],
            childItems: [
                makeChildItem(itemId: "child-1", attachments: []),
                childWithSameAudio
            ]
        )
        XCTAssertEqual(deduped.count, 1)
        XCTAssertEqual(deduped.first?.assetId, sharedAudioId)
        XCTAssertTrue(deduped.first?.isAudio == true)

        let childPdf = makeAttemptAttachment(assetId: childPdfId, fileName: "child-secret.pdf", mime: "application/pdf")
        let childAudio = makeAttemptAttachment(assetId: childAudioId, fileName: "listening-part.wav", mime: "audio/wav")
        let hoisted = AttachmentMerge.mergeQuestionAttachments(
            parentAttachments: [],
            childItems: [
                makeChildItem(itemId: "child-1", attachments: [childPdf, childAudio])
            ]
        )
        XCTAssertEqual(hoisted.count, 1, "Only child audio may be hoisted to QuestionSet header; child PDF must never appear (AC-727)")
        XCTAssertEqual(hoisted.first?.assetId, childAudioId)
        XCTAssertFalse(hoisted.contains(where: { $0.assetId == childPdfId }))

        let opener = makeDummyOpener()
        defer { opener.close() }

        let (window, _) = QuestionViewTestHelper.host(
            AttachmentsView(
                parentAttachments: [],
                childItems: [makeChildItem(itemId: "child-1", attachments: [childPdf, childAudio])],
                opener: opener,
                locale: .vi
            ) { attachment in
                AttemptAudioPlayerView(
                    state: AudioPlayerUIState(
                        assetId: attachment.assetId,
                        fileName: attachment.fileName,
                        playbackState: .idle(playsLeft: 2),
                        playsUsed: 0,
                        playsLeft: 2,
                        maxPlays: 2,
                        positionMs: 0,
                        durationMs: 30_000,
                        isOnline: true,
                        hasPlayError: false
                    ),
                    locale: .vi
                )
            }
            .padding(16),
            size: CGSize(width: 390, height: 320)
        )
        defer { window.isHidden = true }

        let ids = QuestionViewTestHelper.allAccessibilityIdentifiers(in: window)
        XCTAssertTrue(ids.contains("audio.player.\(childAudioId)"))
        XCTAssertFalse(ids.contains("attachments.file.\(childPdfId)"))
    }

    func testAttachmentsRenderAboveQuestionTitleInQuestionView() throws {
        let opener = makeDummyOpener()
        defer { opener.close() }

        let pdfId = "44444444-4444-4444-8444-444444444404"
        let audioId = "55555555-5555-4555-8555-555555555505"
        let item = QuestionItem.singleChoice(
            id: "q-attach-order",
            displayNo: 3,
            points: 1,
            stem: "<p>Nghe đoạn hội thoại và chọn đáp án đúng.</p>",
            optionOrder: ["o1", "o2"],
            options: [
                ChoiceOption(id: "o1", content: "<p>A</p>"),
                ChoiceOption(id: "o2", content: "<p>B</p>")
            ]
        )

        let attachments = [
            QuestionAttachment(assetId: pdfId, fileName: "bang-so-lieu.pdf", mime: "application/pdf", url: nil),
            QuestionAttachment(assetId: audioId, fileName: "listening.mp3", mime: "audio/mpeg", url: nil)
        ]

        let (window, _) = QuestionViewTestHelper.host(
            ScrollView {
                QuestionView(
                    item: item,
                    answer: nil,
                    mode: .answering,
                    onAnswer: { _ in },
                    locale: .vi,
                    fixedWidth: 358
                ) {
                    AttachmentsView(attachments: attachments, opener: opener, locale: .vi) { att in
                        AttemptAudioPlayerView(
                            state: AudioPlayerUIState(
                                assetId: att.assetId,
                                fileName: att.fileName,
                                playbackState: .idle(playsLeft: 2),
                                playsUsed: 0,
                                playsLeft: 2,
                                maxPlays: 2,
                                positionMs: 0,
                                durationMs: 30_000,
                                isOnline: true,
                                hasPlayError: false
                            ),
                            locale: .vi
                        )
                    }
                }
                .padding(16)
            },
            size: CGSize(width: 390, height: 844)
        )
        defer { window.isHidden = true }

        let attachmentsEl = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "attachments.container", in: window)
        )
        let titleEl = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "question.q-attach-order.title", in: window)
        )
        XCTAssertLessThanOrEqual(
            attachmentsEl.accessibilityFrame.maxY,
            titleEl.accessibilityFrame.minY + 2,
            "Attachments container must appear above the question title (I-Q-Audio / R-132)"
        )

        let playButton = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "audio.primaryButton.\(audioId)", in: window)
        )
        XCTAssertGreaterThanOrEqual(playButton.accessibilityFrame.height, 43.99, "Audio play button touch target must be >= 44pt")

        let fileButton = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "attachments.file.\(pdfId)", in: window)
        )
        XCTAssertGreaterThanOrEqual(fileButton.accessibilityFrame.height, 43.99, "Attachment file row touch target must be >= 44pt")
    }

    func testSnapshotsLightDarkAndDynamicType130Percent() throws {
        let opener = makeDummyOpener()
        defer { opener.close() }

        let states: [(name: String, state: AudioPlayerUIState)] = [
            (
                "ready",
                AudioPlayerUIState(
                    assetId: "snap-ready",
                    fileName: "listening-part-1.mp3",
                    playbackState: .idle(playsLeft: 2),
                    playsUsed: 0,
                    playsLeft: 2,
                    maxPlays: 2,
                    positionMs: 0,
                    durationMs: 30_000,
                    isOnline: true,
                    hasPlayError: false
                )
            ),
            (
                "playing",
                AudioPlayerUIState(
                    assetId: "snap-playing",
                    fileName: "listening-part-1.mp3",
                    playbackState: .playing(playId: "p1", positionMs: 12_000),
                    playsUsed: 1,
                    playsLeft: 1,
                    maxPlays: 2,
                    positionMs: 12_000,
                    durationMs: 30_000,
                    isOnline: true,
                    hasPlayError: false
                )
            ),
            (
                "paused-resumable",
                AudioPlayerUIState(
                    assetId: "snap-paused",
                    fileName: "listening-part-1.mp3",
                    playbackState: .paused(playId: "p1", serverPositionMs: 20_000, reason: .background),
                    playsUsed: 1,
                    playsLeft: 1,
                    maxPlays: 2,
                    positionMs: 20_000,
                    durationMs: 30_000,
                    isOnline: true,
                    hasPlayError: false
                )
            ),
            (
                "exhausted",
                AudioPlayerUIState(
                    assetId: "snap-exhausted",
                    fileName: "listening-part-1.mp3",
                    playbackState: .exhausted,
                    playsUsed: 2,
                    playsLeft: 0,
                    maxPlays: 2,
                    positionMs: 30_000,
                    durationMs: 30_000,
                    isOnline: true,
                    hasPlayError: false
                )
            ),
            (
                "offline",
                AudioPlayerUIState(
                    assetId: "snap-offline",
                    fileName: "listening-part-1.mp3",
                    playbackState: .waitingNetwork(playId: "p1"),
                    playsUsed: 1,
                    playsLeft: 1,
                    maxPlays: 2,
                    positionMs: 15_000,
                    durationMs: 30_000,
                    isOnline: false,
                    hasPlayError: false
                )
            )
        ]

        for mode in ThemeMode.allCases {
            let size = CGSize(width: 390, height: 844)
            let (window, _) = QuestionViewTestHelper.host(
                ScrollView {
                    VStack(spacing: 12) {
                        AttachmentsView(
                            attachments: [
                                QuestionAttachment(
                                    assetId: "pdf-1",
                                    fileName: "bang-so-lieu.pdf",
                                    mime: "application/pdf",
                                    url: nil
                                ),
                                QuestionAttachment(
                                    assetId: "snap-ready",
                                    fileName: "listening-part-1.mp3",
                                    mime: "audio/mpeg",
                                    url: nil
                                )
                            ],
                            opener: opener,
                            locale: .vi
                        ) { _ in
                            AttemptAudioPlayerView(state: states[0].state, locale: .vi)
                        }
                        ForEach(states.dropFirst(), id: \.name) { entry in
                            AttemptAudioPlayerView(state: entry.state, locale: .vi)
                        }
                    }
                    .padding(16)
                },
                size: size,
                themeMode: mode
            )
            defer { window.isHidden = true }

            let (image, data) = try XCTUnwrap(QuestionViewTestHelper.snapshotPNG(window: window, size: size))
            XCTAssertGreaterThan(data.count, 1024)
            AttachmentsTestStack.saveSnapshotIfRequested(
                name: "attachments-audio-states-\(mode.rawValue)",
                image: image,
                data: data,
                testCase: self
            )
        }

        let axSize = CGSize(width: 390, height: 420)
        let (axWindow, _) = QuestionViewTestHelper.host(
            AttemptAudioPlayerView(state: states[2].state, locale: .vi)
                .padding(16),
            size: axSize,
            themeMode: .light,
            contentSizeCategory: .extraExtraLarge
        )
        defer { axWindow.isHidden = true }

        let btnEl = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "audio.primaryButton.snap-paused", in: axWindow)
        )
        let badgeEl = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "audio.playsBadge.snap-paused", in: axWindow)
        )
        let bannerEl = try XCTUnwrap(
            QuestionViewTestHelper.accessibilityElement(withIdentifier: "audio.banner.snap-paused", in: axWindow)
        )
        XCTAssertFalse(btnEl.accessibilityFrame.intersects(badgeEl.accessibilityFrame), "Play button and badge must not overlap at 130% Dynamic Type")
        XCTAssertLessThanOrEqual(btnEl.accessibilityFrame.maxX, axSize.width, "Play button must not clip past right edge at 130% Dynamic Type")
        XCTAssertLessThanOrEqual(badgeEl.accessibilityFrame.maxX, axSize.width, "Badge must not clip past right edge at 130% Dynamic Type")
        XCTAssertLessThanOrEqual(bannerEl.accessibilityFrame.maxX, axSize.width, "Banner must not clip past right edge at 130% Dynamic Type")

        let (axImage, axData) = try XCTUnwrap(QuestionViewTestHelper.snapshotPNG(window: axWindow, size: axSize))
        AttachmentsTestStack.saveSnapshotIfRequested(
            name: "attachments-audio-dynamic-type-130",
            image: axImage,
            data: axData,
            testCase: self
        )
    }
}
