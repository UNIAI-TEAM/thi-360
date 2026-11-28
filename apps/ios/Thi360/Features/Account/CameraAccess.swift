import AVFoundation
import Foundation
import UIKit

enum CameraAuthorizationStatus: Equatable, Sendable {
    case granted
    case denied
    case notDetermined
}

@MainActor
protocol CameraAccessProviding: AnyObject {
    var isAvailable: Bool { get }
    var authorization: CameraAuthorizationStatus { get }
    func request() async -> Bool
}

@MainActor
final class SystemCameraAccess: CameraAccessProviding {
    var isAvailable: Bool {
        UIImagePickerController.isSourceTypeAvailable(.camera)
    }

    var authorization: CameraAuthorizationStatus {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            return .granted
        case .notDetermined:
            return .notDetermined
        case .denied, .restricted:
            return .denied
        @unknown default:
            return .denied
        }
    }

    func request() async -> Bool {
        await AVCaptureDevice.requestAccess(for: .video)
    }
}
