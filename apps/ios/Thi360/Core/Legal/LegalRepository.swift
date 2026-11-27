import Foundation

enum LegalLoadError: Error, Equatable, Sendable {
    case missing
    case malformed
}

struct LegalRepository: Sendable {
    static let defaultSubdirectory = "legal"
    static let fallbackTermsVersion = "unknown"

    let bundle: Bundle
    let subdirectory: String
    let directoryOverride: URL?

    init(
        bundle: Bundle = .main,
        subdirectory: String = LegalRepository.defaultSubdirectory,
        directoryOverride: URL? = nil
    ) {
        self.bundle = bundle
        self.subdirectory = subdirectory
        self.directoryOverride = directoryOverride
    }

    func fileURL(for id: LegalId, locale: AppLocale) -> URL? {
        let filename = "\(id.rawValue).\(locale.rawValue).json"
        if let directoryOverride {
            let candidate = directoryOverride.appendingPathComponent(filename, isDirectory: false)
            return FileManager.default.fileExists(atPath: candidate.path) ? candidate : nil
        }
        let sub = subdirectory.isEmpty ? nil : subdirectory
        return bundle.url(
            forResource: "\(id.rawValue).\(locale.rawValue)",
            withExtension: "json",
            subdirectory: sub
        )
    }

    func rawData(for id: LegalId, locale: AppLocale) -> Data? {
        guard let url = fileURL(for: id, locale: locale) else { return nil }
        return try? Data(contentsOf: url)
    }

    func load(_ id: LegalId, locale: AppLocale) -> Result<LegalDocument, LegalLoadError> {
        guard let url = fileURL(for: id, locale: locale) else {
            return .failure(.missing)
        }
        guard let data = try? Data(contentsOf: url) else {
            return .failure(.missing)
        }
        guard !data.isEmpty else {
            return .failure(.malformed)
        }

        do {
            let document = try JSONDecoder().decode(LegalDocument.self, from: data)
            guard document.id == id,
                  document.locale == locale,
                  !document.version.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  !document.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            else {
                return .failure(.malformed)
            }
            return .success(document)
        } catch {
            return .failure(.malformed)
        }
    }

    func termsVersion() -> String {
        if case let .success(viDoc) = load(.terms, locale: .vi) {
            let trimmed = viDoc.version.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                return trimmed
            }
        }
        if case let .success(enDoc) = load(.terms, locale: .en) {
            let trimmed = enDoc.version.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                return trimmed
            }
        }
        return Self.fallbackTermsVersion
    }
}
