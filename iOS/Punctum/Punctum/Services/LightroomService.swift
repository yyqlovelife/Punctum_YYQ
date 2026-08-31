import Foundation
import UIKit

@MainActor
final class LightroomService {
    static let shared = LightroomService()

    var isInstalled: Bool {
        let schemes = ["lightroom://", "adobelightroom://"]
        return schemes.compactMap(URL.init(string:)).contains { UIApplication.shared.canOpenURL($0) }
    }

    func prepareOriginalFile(_ photo: PhotoItem) async throws -> URL {
        let original = try await MetadataService.shared.originalData(for: photo)
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("Punctum-Lightroom", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let safeName = original.filename.replacingOccurrences(of: "/", with: "-")
        let url = folder.appendingPathComponent(safeName)
        try original.data.write(to: url, options: .atomic)
        return url
    }
}
