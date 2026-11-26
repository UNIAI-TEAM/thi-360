import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit

enum ImageLoaderError: Error, Equatable {
    case invalidURL
    case httpStatus(Int)
    case invalidImageData
}

protocol ImageDataFetching: Sendable {
    func fetch(_ request: URLRequest) async throws -> (Data, Int)
}

final class URLSessionImageDataFetcher: ImageDataFetching, @unchecked Sendable {
    private let session: URLSession

    init(session: URLSession? = nil) {
        if let session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.ephemeral
            config.urlCache = URLCache(memoryCapacity: 0, diskCapacity: 0)
            config.requestCachePolicy = .reloadIgnoringLocalCacheData
            config.httpCookieStorage = nil
            config.httpShouldSetCookies = false
            self.session = URLSession(configuration: config)
        }
    }

    func fetch(_ request: URLRequest) async throws -> (Data, Int) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ImageLoaderError.httpStatus(0)
        }
        return (data, http.statusCode)
    }
}

struct ClientTransportImageDataFetcher: ImageDataFetching {
    private let transport: any ClientTransport

    init(transport: any ClientTransport) {
        self.transport = transport
    }

    func fetch(_ request: URLRequest) async throws -> (Data, Int) {
        guard let url = request.url,
              let scheme = url.scheme,
              let host = url.host
        else {
            throw ImageLoaderError.invalidURL
        }
        var baseComponents = URLComponents()
        baseComponents.scheme = scheme
        baseComponents.host = host
        baseComponents.port = url.port
        guard let baseURL = baseComponents.url else {
            throw ImageLoaderError.invalidURL
        }

        let pathWithQuery: String
        if let query = url.query, !query.isEmpty {
            pathWithQuery = "\(url.path)?\(query)"
        } else {
            pathWithQuery = url.path
        }

        var fields = HTTPFields()
        for (key, value) in request.allHTTPHeaderFields ?? [:] {
            if let name = HTTPField.Name(key) {
                fields[name] = value
            }
        }
        let httpRequest = HTTPRequest(
            method: .get,
            scheme: scheme,
            authority: host,
            path: pathWithQuery,
            headerFields: fields
        )
        let (response, body) = try await transport.send(
            httpRequest,
            body: nil,
            baseURL: baseURL,
            operationID: "imageLoaderFetch"
        )
        let data: Data
        if let body {
            data = try await Data(collecting: body, upTo: 10 * 1024 * 1024)
        } else {
            data = Data()
        }
        return (data, response.status.code)
    }
}

final class ImageLoader: SessionCleanupParticipant, @unchecked Sendable {
    let id = "core.imageLoader"
    let apiBaseURL: URL

    private let tokenStore: any TokenStore
    private let refresher: TokenRefresher?
    private let fetcher: any ImageDataFetching
    private let cache = NSCache<NSString, UIImage>()

    init(
        apiBaseURL: URL,
        tokenStore: any TokenStore,
        refresher: TokenRefresher? = nil,
        fetcher: any ImageDataFetching = URLSessionImageDataFetcher(),
        maxCachedImages: Int = 100
    ) {
        self.apiBaseURL = apiBaseURL
        self.tokenStore = tokenStore
        self.refresher = refresher
        self.fetcher = fetcher
        self.cache.countLimit = maxCachedImages
    }

    func resolveURL(_ rawURL: String) -> URL? {
        let trimmed = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if trimmed.hasPrefix("/") {
            return URL(string: trimmed, relativeTo: apiBaseURL)?.absoluteURL
        }
        return URL(string: trimmed)
    }

    func shouldAttachBearer(for url: URL) -> Bool {
        guard let targetHost = url.host?.lowercased(),
              let apiHost = apiBaseURL.host?.lowercased(),
              targetHost == apiHost
        else {
            return false
        }
        let targetPort = url.port ?? Self.defaultPort(for: url.scheme)
        let apiPort = apiBaseURL.port ?? Self.defaultPort(for: apiBaseURL.scheme)
        return targetPort == apiPort
    }

    func cachedImage(for url: URL) -> UIImage? {
        cache.object(forKey: url.absoluteString as NSString)
    }

    func loadImage(from rawURL: String) async throws -> UIImage {
        guard let url = resolveURL(rawURL) else {
            throw ImageLoaderError.invalidURL
        }
        return try await loadImage(from: url)
    }

    func loadImage(from url: URL) async throws -> UIImage {
        let cacheKey = url.absoluteString as NSString
        if let cached = cache.object(forKey: cacheKey) {
            return cached
        }

        let attachBearer = shouldAttachBearer(for: url)
        let initialToken = attachBearer ? (try? tokenStore.read())?.accessToken : nil

        let (data, statusCode) = try await fetchData(url: url, bearerToken: initialToken)
        if statusCode == 401, attachBearer, let refresher, let failedToken = initialToken {
            let refreshed = try await refresher.refreshIfNeeded(failedAccessToken: failedToken)
            let (retryData, retryStatus) = try await fetchData(url: url, bearerToken: refreshed.accessToken)
            guard retryStatus == 200 else {
                if retryStatus == 401 {
                    await refresher.handleUnrecoverableUnauthorized(accessToken: refreshed.accessToken)
                }
                throw ImageLoaderError.httpStatus(retryStatus)
            }
            guard let image = UIImage(data: retryData) else {
                throw ImageLoaderError.invalidImageData
            }
            cache.setObject(image, forKey: cacheKey)
            return image
        }

        guard statusCode == 200 else {
            throw ImageLoaderError.httpStatus(statusCode)
        }
        guard let image = UIImage(data: data) else {
            throw ImageLoaderError.invalidImageData
        }
        cache.setObject(image, forKey: cacheKey)
        return image
    }

    func clearMemoryCache() {
        cache.removeAllObjects()
    }

    func onLogout(accountId: String) async throws {
        clearMemoryCache()
    }

    private func fetchData(url: URL, bearerToken: String?) async throws -> (Data, Int) {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        if let bearerToken, !bearerToken.isEmpty {
            request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        }
        return try await fetcher.fetch(request)
    }

    private static func defaultPort(for scheme: String?) -> Int {
        switch scheme?.lowercased() {
        case "https": 443
        case "http": 80
        default: -1
        }
    }
}

private struct ImageLoaderEnvironmentKey: EnvironmentKey {
    static let defaultValue: ImageLoader? = nil
}

extension EnvironmentValues {
    var imageLoader: ImageLoader? {
        get { self[ImageLoaderEnvironmentKey.self] }
        set { self[ImageLoaderEnvironmentKey.self] = newValue }
    }
}

struct RemoteImage<Placeholder: View>: View {
    let urlString: String?
    let contentMode: ContentMode
    var loader: ImageLoader? = nil
    @ViewBuilder let placeholder: () -> Placeholder

    @Environment(\.imageLoader) private var envLoader
    @State private var loadedImage: UIImage?

    private var resolvedLoader: ImageLoader? {
        loader ?? envLoader
    }

    var body: some View {
        Group {
            if let loadedImage {
                Image(uiImage: loadedImage)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                placeholder()
            }
        }
        .task(id: urlString) {
            loadedImage = nil
            guard let urlString, !urlString.isEmpty, let activeLoader = resolvedLoader else { return }
            if let resolved = activeLoader.resolveURL(urlString),
               let cached = activeLoader.cachedImage(for: resolved)
            {
                loadedImage = cached
                return
            }
            if let image = try? await activeLoader.loadImage(from: urlString) {
                loadedImage = image
            }
        }
    }
}
