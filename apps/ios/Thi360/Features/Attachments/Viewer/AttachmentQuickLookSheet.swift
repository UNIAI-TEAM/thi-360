import QuickLook
import SwiftUI
import UIKit

fileprivate final class ReadOnlyPreviewItem: NSObject, QLPreviewItem {
    let previewItemURL: URL?
    let previewItemTitle: String?

    init(url: URL, title: String) {
        self.previewItemURL = url
        self.previewItemTitle = title
    }
}

struct AttachmentQuickLookSheet: UIViewControllerRepresentable {
    let fileURL: URL
    let title: String
    let locale: AppLocale
    let onDismiss: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            item: ReadOnlyPreviewItem(url: fileURL, title: title),
            onDismiss: onDismiss
        )
    }

    func makeUIViewController(context: Context) -> UINavigationController {
        let previewController = QLPreviewController()
        previewController.dataSource = context.coordinator
        previewController.delegate = context.coordinator
        previewController.title = title

        let closeTitle = AppStrings.common("common.close", locale: locale)
        let closeButton = UIBarButtonItem(
            title: closeTitle,
            style: .done,
            target: context.coordinator,
            action: #selector(Coordinator.handleCloseTapped)
        )
        closeButton.accessibilityIdentifier = "attachments.viewer.closeButton"
        previewController.navigationItem.leftBarButtonItem = closeButton
        // App adds no save or share actions of its own (R-177).
        previewController.navigationItem.rightBarButtonItems = nil

        let nav = UINavigationController(rootViewController: previewController)
        nav.view.accessibilityIdentifier = "attachments.viewer.container"
        return nav
    }

    func updateUIViewController(_ uiViewController: UINavigationController, context: Context) {
        context.coordinator.update(url: fileURL, title: title)
        if let preview = uiViewController.topViewController as? QLPreviewController {
            preview.reloadData()
        }
    }

    final class Coordinator: NSObject, QLPreviewControllerDataSource, QLPreviewControllerDelegate {
        private var item: ReadOnlyPreviewItem
        private let onDismiss: () -> Void

        fileprivate init(item: ReadOnlyPreviewItem, onDismiss: @escaping () -> Void) {
            self.item = item
            self.onDismiss = onDismiss
        }

        func update(url: URL, title: String) {
            if item.previewItemURL != url || item.previewItemTitle != title {
                item = ReadOnlyPreviewItem(url: url, title: title)
            }
        }

        func numberOfPreviewItems(in controller: QLPreviewController) -> Int {
            1
        }

        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> any QLPreviewItem {
            item
        }

        func previewController(_ controller: QLPreviewController, editingModeFor previewItem: any QLPreviewItem) -> QLPreviewItemEditingMode {
            .disabled
        }

        func previewControllerDidDismiss(_ controller: QLPreviewController) {
            onDismiss()
        }

        @objc func handleCloseTapped() {
            onDismiss()
        }
    }
}
