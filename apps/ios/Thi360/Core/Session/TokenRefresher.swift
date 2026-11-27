import Foundation

enum TokenRefreshEvent: Equatable, Sendable {
    case refreshed(SessionTokens)
    case expired
    case studentOnly(username: String)
}

final class RefreshEventSink: @unchecked Sendable {
    typealias Handler = @Sendable (TokenRefreshEvent) async -> Void

    private let lock = NSLock()
    private var handler: Handler?

    func setHandler(_ handler: @escaping Handler) {
        lock.lock()
        self.handler = handler
        lock.unlock()
    }

    func emit(_ event: TokenRefreshEvent) async {
        let activeHandler: Handler? = {
            lock.lock()
            defer { lock.unlock() }
            return handler
        }()
        await activeHandler?(event)
    }
}

actor TokenRefresher {
    typealias RefreshExecutor = @Sendable (String) async throws -> SessionTokens

    private let tokenStore: any TokenStore
    private let refreshExecutor: RefreshExecutor
    let eventSink: RefreshEventSink
    private var sessionEpoch: UInt64 = 0
    private var inFlightTask: Task<SessionTokens, Error>?

    init(
        tokenStore: any TokenStore,
        refreshExecutor: @escaping RefreshExecutor,
        eventSink: RefreshEventSink = RefreshEventSink()
    ) {
        self.tokenStore = tokenStore
        self.refreshExecutor = refreshExecutor
        self.eventSink = eventSink
    }

    func invalidate() {
        sessionEpoch &+= 1
        inFlightTask?.cancel()
        inFlightTask = nil
    }

    func refreshIfNeeded(failedAccessToken: String? = nil) async throws -> SessionTokens {
        if let existingTask = inFlightTask {
            return try await existingTask.value
        }

        guard let currentTokens = try tokenStore.read() else {
            await eventSink.emit(.expired)
            throw MobileApiError.api(statusCode: 401, error: ApiError(code: "SESSION_REVOKED"))
        }

        if let failedAccessToken, currentTokens.accessToken != failedAccessToken {
            return currentTokens
        }

        let startEpoch = sessionEpoch
        let refreshToken = currentTokens.refreshToken
        let storedUsername = currentTokens.username
        let storedAccountId = currentTokens.accountId

        // Wrap the full actor-isolated commit so coalesced followers receive mergedTokens after persistence and event delivery.
        let task = Task<SessionTokens, Error> {
            try await self.performRefresh(
                startEpoch: startEpoch,
                refreshToken: refreshToken,
                storedUsername: storedUsername,
                storedAccountId: storedAccountId
            )
        }
        inFlightTask = task
        return try await task.value
    }

    func handleUnrecoverableUnauthorized(accessToken: String) async {
        guard let current = try? tokenStore.read(), current.accessToken == accessToken else {
            return
        }
        invalidate()
        try? tokenStore.clear()
        await eventSink.emit(.expired)
    }

    private func performRefresh(
        startEpoch: UInt64,
        refreshToken: String,
        storedUsername: String?,
        storedAccountId: String?
    ) async throws -> SessionTokens {
        do {
            let newTokens = try await refreshExecutor(refreshToken)
            if sessionEpoch == startEpoch {
                inFlightTask = nil
            }

            guard sessionEpoch == startEpoch,
                  (try? tokenStore.read())?.refreshToken == refreshToken
            else {
                throw MobileApiError.api(statusCode: 401, error: ApiError(code: "SESSION_REVOKED"))
            }

            let mergedTokens = SessionTokens(
                accessToken: newTokens.accessToken,
                refreshToken: newTokens.refreshToken,
                accessExpiresIn: newTokens.accessExpiresIn,
                refreshExpiresIn: newTokens.refreshExpiresIn,
                mustChangePassword: newTokens.mustChangePassword,
                username: newTokens.username ?? storedUsername,
                accountId: newTokens.accountId ?? storedAccountId,
                issuedAt: newTokens.issuedAt
            )
            try tokenStore.write(mergedTokens)
            await eventSink.emit(.refreshed(mergedTokens))
            return mergedTokens
        } catch {
            if sessionEpoch == startEpoch {
                inFlightTask = nil
            }

            let classified = MobileApiError.classify(error)

            guard sessionEpoch == startEpoch,
                  (try? tokenStore.read())?.refreshToken == refreshToken
            else {
                throw classified
            }

            if classified.statusCode == 403, classified.apiCode == "APP_STUDENT_ONLY" {
                try? tokenStore.clear()
                await eventSink.emit(.studentOnly(username: storedUsername ?? ""))
                throw classified
            }

            if classified.statusCode == 401 {
                try? tokenStore.clear()
                await eventSink.emit(.expired)
                throw classified
            }

            throw classified
        }
    }
}
