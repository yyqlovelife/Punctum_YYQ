import Foundation
import ImageIO
import Photos

// Local EXIF only: cloud-only originals keep their Photos date until available locally.
// Cache invalidates when Photos modificationDate changes, including edited assets.
@MainActor
final class CaptureDateIndex {
    static let shared = CaptureDateIndex()
    struct Entry: Codable { let modification: Date?; let capture: Date }
    private var saveTask: Task<Void, Never>?
    private var loadTask: Task<[String: Entry], Never>?
    private var didLoad = false
    private var entries: [String: Entry] = [:]
    private let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("capture-dates-v1.json")
    private init() {}
    func loadIfNeeded() async {
        if didLoad { return }
        if loadTask == nil {
            let source = url
            loadTask = Task.detached(priority: .utility) {
                guard let data = try? Data(contentsOf: source),
                      let saved = try? JSONDecoder().decode([String: Entry].self, from: data) else { return [:] }
                return saved
            }
        }
        guard let loadTask else { return }
        let saved = await loadTask.value
        if !didLoad {
            entries = saved.merging(entries) { _, current in current }
            didLoad = true
        }
        self.loadTask = nil
    }
    func date(for asset: PHAsset) -> Date {
        if let cached = entries[asset.localIdentifier], cached.modification == asset.modificationDate { return cached.capture }
        return asset.creationDate ?? asset.modificationDate ?? .distantPast
    }
    func prepare(_ asset: PHAsset) async {
        await loadIfNeeded()
        if let cached = entries[asset.localIdentifier], cached.modification == asset.modificationDate { return }
        let input: PHContentEditingInput? = await withCheckedContinuation { continuation in
            let options = PHContentEditingInputRequestOptions()
            options.isNetworkAccessAllowed = false
            // Request original input rather than an app-flattened adjustment rendition.
            options.canHandleAdjustmentData = { _ in true }
            asset.requestContentEditingInput(with: options) { input, _ in continuation.resume(returning: input) }
        }
        guard let imageURL = input?.fullSizeImageURL else { return }
        let captured = await Task.detached(priority: .utility) {
            guard let source = CGImageSourceCreateWithURL(imageURL as CFURL, nil),
                  let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
                  let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any],
                  let raw = exif[kCGImagePropertyExifDateTimeOriginal] as? String else { return Optional<Date>.none }
            return Self.parse(raw, offset: exif[kCGImagePropertyExifOffsetTimeOriginal] as? String)
        }.value
        entries[asset.localIdentifier] = Entry(modification: asset.modificationDate, capture: captured ?? asset.creationDate ?? asset.modificationDate ?? .distantPast)
    }
    nonisolated static func parse(_ raw: String, offset: String?) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.isLenient = false
        formatter.dateFormat = offset == nil ? "yyyy:MM:dd HH:mm:ss" : "yyyy:MM:dd HH:mm:ssXXX"
        return formatter.date(from: raw + (offset ?? ""))
    }
    func save() {
        let snapshot = entries
        let destination = url
        let previous = saveTask
        saveTask = Task.detached(priority: .utility) {
            // Serialize writes so an older snapshot cannot overwrite a newer one.
            await previous?.value
            if let data = try? JSONEncoder().encode(snapshot) {
                try? data.write(to: destination, options: .atomic)
            }
        }
    }
}
