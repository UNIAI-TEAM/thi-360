import SwiftUI

struct AccountInspectedNode: Equatable {
    let identifier: String
    let label: String
    let frame: CGRect
}

@MainActor
final class AccountInspectionSink {
    private var nodesByInstance: [UUID: AccountInspectedNode] = [:]

    func record(instanceID: UUID, node: AccountInspectedNode) {
        nodesByInstance[instanceID] = node
    }

    func remove(instanceID: UUID) {
        nodesByInstance.removeValue(forKey: instanceID)
    }

    var identifiers: Set<String> {
        Set(nodesByInstance.values.map(\.identifier).filter { !$0.isEmpty })
    }

    var labels: [String] {
        nodesByInstance.values.map(\.label).filter { !$0.isEmpty }
    }

    func frame(for identifier: String) -> CGRect? {
        nodesByInstance.values.first { $0.identifier == identifier && !$0.frame.isEmpty }?.frame
    }
}

private struct AccountInspectorEnvironmentKey: EnvironmentKey {
    static let defaultValue: AccountInspectionSink? = nil
}

extension EnvironmentValues {
    var accountInspector: AccountInspectionSink? {
        get { self[AccountInspectorEnvironmentKey.self] }
        set { self[AccountInspectorEnvironmentKey.self] = newValue }
    }
}

private struct AccountProbeModifier: ViewModifier {
    let identifier: String
    let label: String

    @Environment(\.accountInspector) private var inspector
    @State private var instanceID = UUID()

    func body(content: Content) -> some View {
        if let inspector {
            content.background {
                GeometryReader { proxy in
                    let frame = proxy.frame(in: .global)
                    Color.clear
                        .onAppear {
                            record(inspector: inspector, frame: frame)
                        }
                        .onChange(of: identifier) { _, _ in
                            record(inspector: inspector, frame: proxy.frame(in: .global))
                        }
                        .onChange(of: label) { _, _ in
                            record(inspector: inspector, frame: proxy.frame(in: .global))
                        }
                        .onChange(of: frame) { _, newFrame in
                            record(inspector: inspector, frame: newFrame)
                        }
                        .onDisappear {
                            inspector.remove(instanceID: instanceID)
                        }
                }
            }
        } else {
            content
        }
    }

    private func record(inspector: AccountInspectionSink, frame: CGRect) {
        inspector.record(
            instanceID: instanceID,
            node: AccountInspectedNode(identifier: identifier, label: label, frame: frame)
        )
    }
}

extension View {
    func accountProbe(id: String, label: String = "") -> some View {
        modifier(AccountProbeModifier(identifier: id, label: label))
    }
}
