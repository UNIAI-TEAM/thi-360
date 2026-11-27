import SwiftUI

struct QuestionInspectedNode: Equatable {
    let identifier: String
    let label: String
    let value: String?
    let frame: CGRect
    let isButton: Bool
    let isEnabled: Bool
    let isSelected: Bool
}

@MainActor
final class QuestionInspectionSink {
    private(set) var nodes: [String: QuestionInspectedNode] = [:]
    private(set) var texts: [String: String] = [:]
    private var actions: [String: () -> Void] = [:]

    func record(_ node: QuestionInspectedNode, action: (() -> Void)? = nil) {
        nodes[node.identifier] = node
        if !node.label.isEmpty {
            texts[node.identifier] = node.label
        }
        if let action {
            actions[node.identifier] = action
        }
    }

    func remove(identifier: String) {
        nodes.removeValue(forKey: identifier)
        texts.removeValue(forKey: identifier)
        actions.removeValue(forKey: identifier)
    }

    func performAction(for identifier: String) -> Bool {
        guard let node = nodes[identifier], node.isEnabled, let action = actions[identifier] else {
            return false
        }
        action()
        return true
    }

    var allRecordedStrings: [String] {
        Array(texts.values)
    }
}

private struct QuestionInspectorEnvironmentKey: EnvironmentKey {
    static let defaultValue: QuestionInspectionSink? = nil
}

extension EnvironmentValues {
    var questionInspector: QuestionInspectionSink? {
        get { self[QuestionInspectorEnvironmentKey.self] }
        set { self[QuestionInspectorEnvironmentKey.self] = newValue }
    }
}

private struct QuestionProbeModifier: ViewModifier {
    let identifier: String
    let label: String
    let value: String?
    let isButton: Bool
    let isEnabled: Bool
    let isSelected: Bool
    let action: (() -> Void)?

    @Environment(\.questionInspector) private var inspector

    func body(content: Content) -> some View {
        if let inspector {
            content.background {
                GeometryReader { proxy in
                    let frame = proxy.frame(in: .global)
                    Color.clear
                        .onAppear {
                            record(inspector: inspector, frame: frame)
                        }
                        .onChange(of: frame) { _, newFrame in
                            record(inspector: inspector, frame: newFrame)
                        }
                        .onChange(of: label) { _, _ in
                            record(inspector: inspector, frame: proxy.frame(in: .global))
                        }
                        .onChange(of: value) { _, _ in
                            record(inspector: inspector, frame: proxy.frame(in: .global))
                        }
                        .onChange(of: isEnabled) { _, _ in
                            record(inspector: inspector, frame: proxy.frame(in: .global))
                        }
                        .onChange(of: isSelected) { _, _ in
                            record(inspector: inspector, frame: proxy.frame(in: .global))
                        }
                        .onDisappear {
                            inspector.remove(identifier: identifier)
                        }
                }
            }
        } else {
            content
        }
    }

    private func record(inspector: QuestionInspectionSink, frame: CGRect) {
        inspector.record(
            QuestionInspectedNode(
                identifier: identifier,
                label: label,
                value: value,
                frame: frame,
                isButton: isButton,
                isEnabled: isEnabled,
                isSelected: isSelected
            ),
            action: action
        )
    }
}

extension View {
    func questionProbe(
        id: String,
        label: String = "",
        value: String? = nil,
        isButton: Bool = false,
        isEnabled: Bool = true,
        isSelected: Bool = false,
        action: (() -> Void)? = nil
    ) -> some View {
        modifier(
            QuestionProbeModifier(
                identifier: id,
                label: label,
                value: value,
                isButton: isButton,
                isEnabled: isEnabled,
                isSelected: isSelected,
                action: action
            )
        )
    }
}
