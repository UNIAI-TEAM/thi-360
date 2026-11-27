import Foundation

struct QuestionAttachment: Identifiable, Hashable, Sendable {
    let assetId: String
    let fileName: String
    let mime: String
    let url: String?

    var id: String { assetId }

    var isAudio: Bool {
        Self.isAudioMime(mime)
    }

    var fileExtension: String? {
        if let dotIndex = fileName.lastIndex(of: ".") {
            let ext = String(fileName[fileName.index(after: dotIndex)...])
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()
            if !ext.isEmpty, ext.count <= 10, ext.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber) }) {
                return ext
            }
        }
        return Self.mimeExtensions[mime.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()]
    }

    static func isAudioMime(_ mime: String?) -> Bool {
        guard let mime else { return false }
        return mime.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().hasPrefix("audio/")
    }

    private static let mimeExtensions: [String: String] = [
        "application/pdf": "pdf",
        "image/png": "png",
        "image/jpeg": "jpg",
        "image/webp": "webp",
        "text/plain": "txt",
        "application/msword": "doc",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "docx",
        "application/vnd.ms-excel": "xls",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "xlsx"
    ]
}

enum AttachmentMerge {
    static func mergeQuestionAttachments(
        parentAttachments: [Components.Schemas.AttemptAttachmentView]?,
        childItems: [Components.Schemas.AttemptItemView]? = nil
    ) -> [QuestionAttachment] {
        let parent = (parentAttachments ?? []).compactMap(toQuestionAttachment)
        let children = (childItems ?? []).map { child in
            (child.attachments ?? []).compactMap(toQuestionAttachment)
        }
        return merge(parent: parent, children: children)
    }

    static func merge(
        parent: [QuestionAttachment],
        children: [[QuestionAttachment]] = []
    ) -> [QuestionAttachment] {
        var seenAssetIds = Set<String>()
        var merged: [QuestionAttachment] = []
        merged.reserveCapacity(parent.count)

        for attachment in parent where seenAssetIds.insert(attachment.assetId).inserted {
            merged.append(attachment)
        }

        // Only audio attachments from child items are hoisted to the set header (R-132, A-115, AC-727).
        for childList in children {
            for attachment in childList where attachment.isAudio {
                if seenAssetIds.insert(attachment.assetId).inserted {
                    merged.append(attachment)
                }
            }
        }
        return merged
    }

    private static func toQuestionAttachment(_ raw: Components.Schemas.AttemptAttachmentView) -> QuestionAttachment? {
        guard let assetId = resolveAssetId(raw) else { return nil }
        let trimmedFileName = raw.fileName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let urlFallback: String? = raw.url.flatMap { rawURL in
            let withoutQuery = rawURL.split(separator: "?", maxSplits: 1).first.map(String.init) ?? rawURL
            let tail = withoutQuery.split(separator: "/").last.map(String.init)?.trimmingCharacters(in: .whitespacesAndNewlines)
            return (tail?.isEmpty == false) ? tail : nil
        }
        let displayName = (trimmedFileName?.isEmpty == false ? trimmedFileName : nil) ?? urlFallback ?? assetId
        return QuestionAttachment(
            assetId: assetId,
            fileName: displayName,
            mime: (raw.mime ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
            url: raw.url
        )
    }

    private static func resolveAssetId(_ raw: Components.Schemas.AttemptAttachmentView) -> String? {
        let trimmedId = raw.assetId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let trimmedURL = raw.url?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        switch AttemptAssetRef.resolve(src: trimmedURL, dataAssetId: trimmedId) {
        case let .attempt(assetId):
            return assetId
        case .solution:
            return nil
        case .blocked:
            guard trimmedURL.isEmpty,
                  !trimmedId.isEmpty,
                  !trimmedId.contains("/"),
                  !trimmedId.contains("\\"),
                  !trimmedId.contains("..")
            else {
                return nil
            }
            return trimmedId
        }
    }
}

enum ServerAudioPlayState: String, Equatable, Sendable {
    case idle = "IDLE"
    case playing = "PLAYING"
    case ended = "ENDED"
}

enum AudioEndReason: String, Equatable, Sendable {
    case finished = "FINISHED"
    case closed = "CLOSED"
}

struct AudioPlaySnapshot: Equatable, Sendable {
    static let defaultMaxPlays = 2

    var playId: String?
    var playsUsed: Int
    var playsLeft: Int
    var durationMs: Int64
    var positionMs: Int64
    var state: ServerAudioPlayState
    var endReason: AudioEndReason?

    static func empty(maxPlays: Int = defaultMaxPlays) -> AudioPlaySnapshot {
        AudioPlaySnapshot(
            playId: nil,
            playsUsed: 0,
            playsLeft: max(0, maxPlays),
            durationMs: 0,
            positionMs: 0,
            state: .idle,
            endReason: nil
        )
    }

    static func fromResponse(_ response: Components.Schemas.AttemptAudioPlayResponse) -> AudioPlaySnapshot {
        let mappedState: ServerAudioPlayState = switch response.state {
        case .IDLE: .idle
        case .PLAYING: .playing
        case .ENDED: .ended
        }
        let mappedEndReason: AudioEndReason? = switch response.endReason {
        case .FINISHED: .finished
        case .CLOSED: .closed
        case nil: nil
        }
        return AudioPlaySnapshot(
            playId: response.playId,
            playsUsed: max(0, Int(response.playsUsed)),
            playsLeft: max(0, Int(response.playsLeft)),
            durationMs: max(0, response.durationMs),
            positionMs: max(0, response.positionMs),
            state: mappedState,
            endReason: mappedEndReason
        )
    }

    static func fromView(
        _ view: Components.Schemas.AttemptAudioPlayView?,
        maxPlays: Int = defaultMaxPlays
    ) -> AudioPlaySnapshot {
        guard let view else { return empty(maxPlays: maxPlays) }
        let used = max(0, Int(view.playsUsed ?? 0))
        let left = max(0, Int(view.playsLeft ?? Int32(max(0, maxPlays - used))))
        let mappedState: ServerAudioPlayState = switch view.state {
        case .PLAYING: .playing
        case .ENDED: .ended
        case .IDLE, nil: .idle
        }
        let mappedEndReason: AudioEndReason? = switch view.endReason {
        case .FINISHED: .finished
        case .CLOSED: .closed
        case nil: nil
        }
        return AudioPlaySnapshot(
            playId: view.playId,
            playsUsed: used,
            playsLeft: left,
            durationMs: max(0, view.durationMs ?? 0),
            positionMs: max(0, view.positionMs ?? 0),
            state: mappedState,
            endReason: mappedEndReason
        )
    }
}

enum AudioPauseReason: Equatable, Sendable {
    case background
    case interrupted
    case routeChange
    case leftScreen
    case remotePause
    case reopened
    case error
}

enum AudioLockReason: Equatable, Sendable {
    case acquiring
    case partClosed
    case timeUp
    case submitted
    case evicted
    case disabled
}

enum AudioPlaybackState: Equatable, Sendable {
    case idle(playsLeft: Int)
    case opening(requestId: String, isResuming: Bool)
    case playing(playId: String, positionMs: Int64)
    case paused(playId: String, serverPositionMs: Int64, reason: AudioPauseReason)
    case waitingNetwork(playId: String)
    case exhausted
    case locked(reason: AudioLockReason)
}

enum AudioPrimaryButtonRole: Equatable, Sendable {
    case play
    case playing
    case resume

    var stringKey: String {
        switch self {
        case .play: "question.audioPlayer.play"
        case .playing: "question.audioPlayer.playing"
        case .resume: "question.audioPlayer.resume"
        }
    }
}

enum AudioBannerTone: Equatable, Sendable {
    case info
    case warning
    case danger
    case neutral
}

struct AudioBanner: Equatable, Sendable {
    let tone: AudioBannerTone
    let stringKey: String
}

struct AudioPlayerUIState: Equatable, Sendable {
    let assetId: String
    let fileName: String
    let playbackState: AudioPlaybackState
    let playsUsed: Int
    let playsLeft: Int
    let maxPlays: Int
    let positionMs: Int64
    let durationMs: Int64
    let isOnline: Bool
    let hasPlayError: Bool

    var effectiveMaxPlays: Int {
        max(maxPlays, playsUsed + playsLeft)
    }

    var clampedPositionMs: Int64 {
        let nonNegative = max(0, positionMs)
        return durationMs > 0 ? min(nonNegative, durationMs) : nonNegative
    }

    var progressFraction: Double {
        guard durationMs > 0 else { return 0 }
        return min(1, max(0, Double(clampedPositionMs) / Double(durationMs)))
    }

    var isExhausted: Bool {
        switch playbackState {
        case .exhausted:
            return true
        case .playing, .paused, .waitingNetwork:
            return false
        case .idle, .opening, .locked:
            return playsLeft <= 0
        }
    }

    var isBusyOpening: Bool {
        if case .opening = playbackState { return true }
        return false
    }

    var primaryButtonRole: AudioPrimaryButtonRole {
        switch playbackState {
        case .playing:
            return .playing
        case .paused, .waitingNetwork:
            return .resume
        case let .opening(_, isResuming):
            return isResuming ? .resume : .play
        case .idle, .exhausted, .locked:
            return .play
        }
    }

    var isPrimaryButtonEnabled: Bool {
        guard isOnline else { return false }
        switch playbackState {
        case .idle:
            return playsLeft > 0
        case .paused:
            return true
        case .opening, .playing, .waitingNetwork, .exhausted, .locked:
            return false
        }
    }

    var banner: AudioBanner? {
        if case let .locked(reason) = playbackState {
            switch reason {
            case .acquiring:
                return nil
            case .partClosed:
                return AudioBanner(tone: .warning, stringKey: "question.audioPlayer.partClosedReason")
            case .timeUp:
                return AudioBanner(tone: .danger, stringKey: "question.audioPlayer.timeUpReason")
            case .submitted:
                return AudioBanner(tone: .warning, stringKey: "question.audioPlayer.submittedReason")
            case .evicted:
                return AudioBanner(tone: .danger, stringKey: "question.audioPlayer.evictedReason")
            case .disabled:
                return AudioBanner(tone: .neutral, stringKey: "question.audioPlayer.disabledReason")
            }
        }
        if isExhausted {
            return AudioBanner(tone: .neutral, stringKey: "question.audioPlayer.exhaustedReason")
        }
        if case .waitingNetwork = playbackState {
            return AudioBanner(tone: .warning, stringKey: "question.audioPlayer.offlineWaiting")
        }
        if !isOnline {
            return AudioBanner(tone: .warning, stringKey: "question.audioPlayer.offlineWaiting")
        }
        if case let .paused(_, _, reason) = playbackState {
            switch reason {
            case .error:
                return AudioBanner(tone: .danger, stringKey: "question.audioPlayer.playErrorRetry")
            case .background, .interrupted, .routeChange, .leftScreen, .remotePause, .reopened:
                return AudioBanner(tone: .info, stringKey: "question.audioPlayer.pausedResumable")
            }
        }
        if hasPlayError {
            return AudioBanner(tone: .danger, stringKey: "question.audioPlayer.playErrorRetry")
        }
        return nil
    }
}

enum AudioError: Error, Equatable, Sendable {
    case inProgress(playId: String?)
    case exhausted
    case seekRejected(positionMs: Int64?)
    case stale
    case partClosed
    case timeUp
    case sessionRevoked
    case unauthorized
    case network(URLError.Code)
    case other(statusCode: Int?, code: String?, message: String?)
}

enum AudioTimeFormatter {
    static func format(milliseconds: Int64) -> String {
        let safeSeconds = max(0, milliseconds) / 1_000
        let minutes = safeSeconds / 60
        let seconds = safeSeconds % 60
        let minText = minutes < 10 ? "0\(minutes)" : "\(minutes)"
        let secText = seconds < 10 ? "0\(seconds)" : "\(seconds)"
        return "\(minText):\(secText)"
    }
}
