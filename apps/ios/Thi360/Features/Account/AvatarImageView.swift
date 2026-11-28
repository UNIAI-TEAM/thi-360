import SwiftUI
import UIKit

struct AvatarCircleView: View {
    let avatarUrl: String?
    let initials: String
    let size: CGFloat
    let fontSize: CGFloat
    var locale: AppLocale = .vi
    var exposeAvatarAccessibility: Bool = false
    var loaderOverride: ImageLoader? = nil
    var reloadToken: Int = 0

    @Environment(\.brandPalette) private var palette
    @Environment(\.imageLoader) private var envLoader
    @State private var loadedImage: UIImage?
    @State private var attemptedKey: String?

    private var activeLoader: ImageLoader? {
        loaderOverride ?? envLoader
    }

    private var requestKey: String {
        "\(avatarUrl ?? "")#\(reloadToken)"
    }

    var body: some View {
        let resolvedIdentifier = exposeAvatarAccessibility
            ? (loadedImage != nil ? "profile.avatar.image" : "profile.avatar.initials")
            : "account.card.avatar"
        return ZStack {
            if let loadedImage {
                Image(uiImage: loadedImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size, height: size)
                    .clipShape(Circle())
                    .accessibilityIdentifier("account.avatar.image")
            } else {
                Circle()
                    .fill(palette.primaryContainerStrongColor)
                    .frame(width: size, height: size)
                Text(initials)
                    .font(.system(size: fontSize, weight: .semibold))
                    .foregroundStyle(palette.primaryTextColor)
                    .accessibilityIdentifier("account.avatar.initials")
            }
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityHidden(!exposeAvatarAccessibility)
        .accessibilityLabel(accessibilityLabelText)
        .accessibilityAddTraits(exposeAvatarAccessibility ? .isImage : [])
        .accessibilityIdentifier(resolvedIdentifier)
        .accountProbe(id: resolvedIdentifier, label: accessibilityLabelText)
        .task(id: requestKey) {
            await loadAvatarIfNeeded()
        }
    }

    private var accessibilityLabelText: String {
        guard exposeAvatarAccessibility else { return "" }
        if loadedImage != nil {
            return ProfileStrings.text("account.profile.avatar.current", locale: locale)
        }
        return ProfileStrings.text("account.profile.avatar.currentEmpty", locale: locale)
    }

    private func loadAvatarIfNeeded() async {
        guard let rawURL = avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawURL.isEmpty
        else {
            loadedImage = nil
            attemptedKey = requestKey
            return
        }
        guard let loader = activeLoader else {
            loadedImage = nil
            return
        }
        if let resolved = loader.resolveURL(rawURL),
           let cached = loader.cachedImage(for: resolved)
        {
            loadedImage = cached
            attemptedKey = requestKey
            return
        }
        guard attemptedKey != requestKey else { return }
        attemptedKey = requestKey
        loadedImage = nil
        do {
            let image: UIImage
            if let resolved = loader.resolveURL(rawURL) {
                image = try await AvatarInFlightLoader.load(
                    rawURL: rawURL,
                    resolvedURL: resolved,
                    using: loader
                )
            } else {
                image = try await loader.loadImage(from: rawURL, authenticated: true)
            }
            if !Task.isCancelled {
                loadedImage = image
            }
        } catch {
            if !Task.isCancelled {
                loadedImage = nil
            }
        }
    }
}

@MainActor
enum AvatarInFlightLoader {
    private struct Key: Hashable {
        let loaderID: ObjectIdentifier
        let url: String
    }

    private static var tasks: [Key: Task<UIImage, Error>] = [:]
    private static let observedLoaders = NSHashTable<ImageLoader>.weakObjects()

    static func track(_ loader: ImageLoader) {
        observedLoaders.add(loader)
    }

    static func clearAllCaches() {
        for task in tasks.values {
            task.cancel()
        }
        tasks.removeAll()
        for loader in observedLoaders.allObjects {
            loader.clearMemoryCache()
        }
    }

    static func load(rawURL: String, resolvedURL: URL, using loader: ImageLoader) async throws -> UIImage {
        observedLoaders.add(loader)
        if let cached = loader.cachedImage(for: resolvedURL) {
            return cached
        }
        let key = Key(loaderID: ObjectIdentifier(loader), url: resolvedURL.absoluteString)
        let task: Task<UIImage, Error>
        if let existing = tasks[key] {
            task = existing
        } else {
            let created = Task<UIImage, Error> {
                try await loader.loadImage(from: rawURL, authenticated: true)
            }
            tasks[key] = created
            task = created
        }
        do {
            let image = try await task.value
            if tasks[key] == task {
                tasks.removeValue(forKey: key)
            }
            return image
        } catch {
            if tasks[key] == task {
                tasks.removeValue(forKey: key)
            }
            throw error
        }
    }
}

struct CameraImagePickerView: UIViewControllerRepresentable {
    let onPickedData: (Data) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPickedData: onPickedData, onCancel: onCancel)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        private let onPickedData: (Data) -> Void
        private let onCancel: () -> Void

        init(onPickedData: @escaping (Data) -> Void, onCancel: @escaping () -> Void) {
            self.onPickedData = onPickedData
            self.onCancel = onCancel
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
            onCancel()
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            picker.dismiss(animated: true)
            if let image = info[.originalImage] as? UIImage,
               let data = image.jpegData(compressionQuality: 0.92)
            {
                onPickedData(data)
            } else {
                onCancel()
            }
        }
    }
}
