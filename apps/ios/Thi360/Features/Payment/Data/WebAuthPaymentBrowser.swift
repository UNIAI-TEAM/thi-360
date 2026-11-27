import AuthenticationServices
import Foundation
import SafariServices
import UIKit

@MainActor
protocol PaymentAuthSessionControlling: AnyObject {
    var prefersEphemeralWebBrowserSession: Bool { get set }
    var presentationContextProvider: (any ASWebAuthenticationPresentationContextProviding)? { get set }
    func start() -> Bool
    func cancel()
}

extension ASWebAuthenticationSession: PaymentAuthSessionControlling {}

@MainActor
final class WebAuthPaymentBrowser: NSObject, PaymentBrowser, ASWebAuthenticationPresentationContextProviding, SFSafariViewControllerDelegate {
    typealias AuthSessionFactory = @MainActor (
        URL,
        String,
        String,
        @escaping @Sendable (URL?, (any Error)?) -> Void
    ) -> any PaymentAuthSessionControlling

    typealias SafariFallbackPresenter = @MainActor (URL, @escaping @MainActor () -> Void) -> Bool

    private let callbackHost: String
    private let callbackPath: String
    private let authSessionFactory: AuthSessionFactory
    private let customSafariFallbackPresenter: SafariFallbackPresenter?
    private let onFallbackDismissed: @MainActor () -> Void

    private var activeSession: (any PaymentAuthSessionControlling)?
    private var activeSafariDismissCallback: (@MainActor () -> Void)?

    init(
        callbackHost: String = AppEnvironment.current.webBaseURL.host ?? "",
        callbackPath: String = PaymentReturnLink.returnPath,
        authSessionFactory: AuthSessionFactory? = nil,
        safariFallbackPresenter: SafariFallbackPresenter? = nil,
        onFallbackDismissed: @escaping @MainActor () -> Void = {}
    ) {
        self.callbackHost = callbackHost.trimmingCharacters(in: .whitespacesAndNewlines)
        self.callbackPath = callbackPath
        self.onFallbackDismissed = onFallbackDismissed
        self.authSessionFactory = authSessionFactory ?? { url, host, path, completion in
            ASWebAuthenticationSession(
                url: url,
                callback: .https(host: host, path: path),
                completionHandler: completion
            )
        }
        self.customSafariFallbackPresenter = safariFallbackPresenter
        super.init()
    }

    func open(_ url: URL) async -> PaymentBrowserOutcome {
        guard !callbackHost.isEmpty else {
            return presentSafariFallback(url: url) ? .openedFallback : .failedToOpen
        }

        return await withCheckedContinuation { continuation in
            var resumed = false
            let finish: @MainActor (PaymentBrowserOutcome) -> Void = { [weak self] outcome in
                guard !resumed else { return }
                resumed = true
                self?.activeSession = nil
                continuation.resume(returning: outcome)
            }

            let session = authSessionFactory(url, callbackHost, callbackPath) { [weak self] callbackURL, error in
                Task { @MainActor in
                    if let callbackURL {
                        finish(.callback(callbackURL))
                        return
                    }
                    if let authError = error as? ASWebAuthenticationSessionError,
                       authError.code == .canceledLogin
                    {
                        finish(.dismissed)
                        return
                    }
                    if let self, self.presentSafariFallback(url: url) {
                        finish(.openedFallback)
                    } else {
                        finish(.failedToOpen)
                    }
                }
            }

            session.prefersEphemeralWebBrowserSession = true
            session.presentationContextProvider = self
            activeSession = session

            let started = session.start()
            if !started {
                activeSession = nil
                if presentSafariFallback(url: url) {
                    finish(.openedFallback)
                } else {
                    finish(.failedToOpen)
                }
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        Self.activeKeyWindow() ?? ASPresentationAnchor()
    }

    nonisolated func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        Task { @MainActor in
            let callback = self.activeSafariDismissCallback
            self.activeSafariDismissCallback = nil
            callback?()
        }
    }

    private func presentSafariFallback(url: URL) -> Bool {
        if let customSafariFallbackPresenter {
            return customSafariFallbackPresenter(url, onFallbackDismissed)
        }
        guard let presenter = Self.topViewController() else {
            return false
        }
        let configuration = SFSafariViewController.Configuration()
        configuration.entersReaderIfAvailable = false
        let safariVC = SFSafariViewController(url: url, configuration: configuration)
        safariVC.delegate = self
        safariVC.modalPresentationStyle = .pageSheet
        activeSafariDismissCallback = onFallbackDismissed
        presenter.present(safariVC, animated: true)
        return true
    }

    private static func activeKeyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
            ?? UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .first
    }

    private static func topViewController() -> UIViewController? {
        var controller = activeKeyWindow()?.rootViewController
        while let presented = controller?.presentedViewController {
            controller = presented
        }
        return controller
    }
}
