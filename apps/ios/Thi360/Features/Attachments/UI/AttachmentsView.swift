import SwiftUI

struct AttachmentsView<AudioPlayerContent: View>: View {
    let attachments: [QuestionAttachment]
    @Bindable var opener: AttachmentOpener
    let locale: AppLocale
    @ViewBuilder let audioPlayer: (QuestionAttachment) -> AudioPlayerContent

    @Environment(\.brandPalette) private var brandPalette

    init(
        parentAttachments: [Components.Schemas.AttemptAttachmentView]?,
        childItems: [Components.Schemas.AttemptItemView]? = nil,
        opener: AttachmentOpener,
        locale: AppLocale = .vi,
        @ViewBuilder audioPlayer: @escaping (QuestionAttachment) -> AudioPlayerContent
    ) {
        self.attachments = AttachmentMerge.mergeQuestionAttachments(
            parentAttachments: parentAttachments,
            childItems: childItems
        )
        self.opener = opener
        self.locale = locale
        self.audioPlayer = audioPlayer
    }

    init(
        attachments: [QuestionAttachment],
        opener: AttachmentOpener,
        locale: AppLocale = .vi,
        @ViewBuilder audioPlayer: @escaping (QuestionAttachment) -> AudioPlayerContent
    ) {
        self.attachments = attachments
        self.opener = opener
        self.locale = locale
        self.audioPlayer = audioPlayer
    }

    var body: some View {
        if !attachments.isEmpty {
            let sectionLabel = AttachmentsStrings.attachmentsLabel(locale: locale)

            VStack(alignment: .leading, spacing: 8) {
                Text(sectionLabel)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(brandPalette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("attachments.label")
                    .questionProbe(id: "attachments.label", label: sectionLabel)

                ForEach(attachments) { attachment in
                    if attachment.isAudio {
                        audioPlayer(attachment)
                    } else {
                        attachmentFileRow(attachment)
                    }
                }

                if case .openFailed = opener.state {
                    let errorText = AttachmentsStrings.openError(locale: locale)
                    HStack(alignment: .top, spacing: 6) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(brandPalette.dangerTextColor)
                            .padding(.top, 2)
                            .accessibilityHidden(true)

                        Text(errorText)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(brandPalette.dangerTextColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(brandPalette.dangerBgColor)
                    )
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("attachments.openError")
                    .questionProbe(id: "attachments.openError", label: errorText)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(brandPalette.bgSubtleColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(brandPalette.borderColor, lineWidth: 1)
            )
            .accessibilityElement(children: .contain)
            .accessibilityLabel(sectionLabel)
            .accessibilityIdentifier("attachments.container")
            .questionProbe(id: "attachments.container", label: sectionLabel)
            .sheet(
                item: $opener.activePreview,
                onDismiss: {
                    opener.dismissViewer()
                }
            ) { preview in
                AttachmentQuickLookSheet(
                    fileURL: preview.tempFile.url,
                    title: preview.attachment.fileName,
                    locale: locale,
                    onDismiss: {
                        opener.dismissViewer()
                    }
                )
            }
        }
    }

    @ViewBuilder
    private func attachmentFileRow(_ attachment: QuestionAttachment) -> some View {
        let isLoading: Bool = {
            if case let .loading(loadingId) = opener.state {
                return loadingId == attachment.assetId
            }
            return false
        }()

        Button {
            Task { @MainActor in
                _ = await opener.open(attachment)
            }
        } label: {
            HStack(spacing: 10) {
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                        .tint(brandPalette.primaryFillColor)
                } else {
                    Image(systemName: "doc.text")
                        .font(.body.weight(.medium))
                        .foregroundStyle(brandPalette.primaryTextColor)
                        .accessibilityHidden(true)
                }

                Text(attachment.fileName)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(brandPalette.primaryTextColor)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(brandPalette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(brandPalette.borderColor, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
        .accessibilityIdentifier("attachments.file.\(attachment.assetId)")
        .accessibilityLabel(attachment.fileName)
        .questionProbe(
            id: "attachments.file.\(attachment.assetId)",
            label: attachment.fileName,
            isButton: true,
            isEnabled: !isLoading,
            action: {
                Task { @MainActor in
                    _ = await opener.open(attachment)
                }
            }
        )
    }
}
