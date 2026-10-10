import ImageIO
import Photos
import SwiftUI
import UIKit

@MainActor
final class PhotoThumbnailCache {
    static let shared = PhotoThumbnailCache()
    private let cache = NSCache<NSString, UIImage>()
    private let previewCache = NSCache<NSString, UIImage>()
    private var previewPixelCounts: [String: Int] = [:]

    private init() {
        cache.countLimit = 180
        cache.totalCostLimit = 64 * 1024 * 1024
        previewCache.countLimit = 120
        previewCache.totalCostLimit = 96 * 1024 * 1024
    }

    func image(for id: String, size: CGSize) -> UIImage? {
        cache.object(forKey: Self.key(id, size) as NSString)
    }

    func store(_ image: UIImage, for id: String, size: CGSize) {
        let cost = Int(image.size.width * image.size.height * image.scale * image.scale * 4)
        cache.setObject(image, forKey: Self.key(id, size) as NSString, cost: max(cost, 1))
        let pixels = max(cost / 4, 1)
        if pixels >= previewPixelCounts[id, default: 0] {
            previewPixelCounts[id] = pixels
            previewCache.setObject(image, forKey: id as NSString, cost: max(cost, 1))
        }
    }

    func previewImage(for id: String) -> UIImage? {
        previewCache.object(forKey: id as NSString)
    }

    private static func key(_ id: String, _ size: CGSize) -> String {
        "\(id)-\(Int(size.width))x\(Int(size.height))"
    }
}

@MainActor
final class PhotoImageLoader: ObservableObject {
    @Published var image: UIImage?
    private var requestID: PHImageRequestID = PHInvalidImageRequestID
    private var loadingID: String?
    private var fullFrameToken: FullFrameImageRequests.Token?
    private var generation = 0

    func load(
        asset: PHAsset,
        targetSize: CGSize,
        contentMode: PHImageContentMode = .aspectFill,
        skipDegraded: Bool = false
    ) {
        let id = asset.localIdentifier
        if loadingID == id, image != nil, skipDegraded {
            return
        }
        cancelRequest()
        loadingID = id
        // A reused view must never display the previous asset while a new request runs.
        image = PhotoThumbnailCache.shared.image(for: id, size: targetSize)
            ?? PhotoThumbnailCache.shared.previewImage(for: id)
        let expectedGeneration = generation
        if let cached = PhotoThumbnailCache.shared.image(for: id, size: targetSize) {
            image = cached
            if skipDegraded { return }
        }
        if skipDegraded {
            loadFullFrame(asset: asset, targetSize: targetSize)
            return
        }

        let options = PHImageRequestOptions()
        options.deliveryMode = .opportunistic
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = true
        let expectedAspect = asset.pixelWidth > 0 && asset.pixelHeight > 0
            ? CGFloat(asset.pixelWidth) / CGFloat(asset.pixelHeight)
            : 0
        requestID = PHImageManager.default().requestImage(
            for: asset,
            targetSize: targetSize,
            contentMode: contentMode,
            options: options
        ) { [weak self] image, info in
            guard info?[PHImageCancelledKey] as? Bool != true else { return }
            let degraded = info?[PHImageResultIsDegradedKey] as? Bool == true
            if let image, degraded, expectedAspect > 0 {
                let imageAspect = image.size.width / max(image.size.height, 0.01)
                let looksSquare = abs(imageAspect - 1) < 0.08
                let assetNotSquare = abs(expectedAspect - 1) > 0.12
                if looksSquare && assetNotSquare { return }
            }
            if let image {
                Task { @MainActor in
                    guard let self, self.loadingID == id, self.generation == expectedGeneration else { return }
                    PhotoThumbnailCache.shared.store(image, for: id, size: targetSize)
                    self.image = image
                }
            }
        }
    }

    func image(for photoID: String) -> UIImage? {
        loadingID == photoID ? image : nil
    }

    func cancel() {
        cancelRequest()
        loadingID = nil
    }

    private func cancelRequest() {
        generation += 1
        if let fullFrameToken { FullFrameImageRequests.shared.cancel(fullFrameToken) }
        fullFrameToken = nil
        guard requestID != PHInvalidImageRequestID else { return }
        PHImageManager.default().cancelImageRequest(requestID)
        requestID = PHInvalidImageRequestID
    }

    /// Decode the still JPEG/HEIC itself. Photos' opportunistic thumbnails are often
    /// square center-crops, which chops VIVO-style white-bar watermarks.
    private func loadFullFrame(asset: PHAsset, targetSize: CGSize) {
        let id = asset.localIdentifier
        let expectedGeneration = generation
        fullFrameToken = FullFrameImageRequests.shared.load(asset: asset, targetSize: targetSize) { [weak self] image in
            guard let self, self.loadingID == id, self.generation == expectedGeneration else { return }
            if let image { self.image = image }
        }
    }
}

/// One Photos request and one bounded background decode for all consumers of an image.
@MainActor
private final class FullFrameImageRequests {
    static let shared = FullFrameImageRequests()
    struct Token { let key: String; let subscriber: UUID }
    private final class Request {
        let identity = UUID()
        var photoRequest = PHInvalidImageRequestID
        var callbacks: [UUID: (UIImage?) -> Void] = [:]
    }
    private var requests: [String: Request] = [:]
    private static let decodeQueue = DispatchQueue(label: "punctum.full-frame-decode", qos: .userInitiated)

    func load(asset: PHAsset, targetSize: CGSize, completion: @escaping (UIImage?) -> Void) -> Token {
        let id = asset.localIdentifier
        let key = "\(id)-\(Int(targetSize.width))x\(Int(targetSize.height))"
        let token = Token(key: key, subscriber: UUID())
        if let existing = requests[key] {
            existing.callbacks[token.subscriber] = completion
            return token
        }
        let request = Request()
        request.callbacks[token.subscriber] = completion
        requests[key] = request
        let identity = request.identity
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.version = .current
        options.resizeMode = .none
        options.isNetworkAccessAllowed = true
        let maxPixel = max(targetSize.width, targetSize.height, 1)
        request.photoRequest = PHImageManager.default().requestImageDataAndOrientation(for: asset, options: options) { data, _, _, info in
            let cancelled = info?[PHImageCancelledKey] as? Bool == true
            Task { @MainActor in
                guard self.requests[key]?.identity == identity else { return }
                guard !cancelled, let data else {
                    self.finish(key: key, identity: identity, image: nil, photoID: id, size: targetSize)
                    return
                }
                Self.decodeQueue.async {
                    let image = autoreleasepool { FullFrameImageDecoder.downsample(data, maxPixel: maxPixel) }
                    Task { @MainActor in
                        self.finish(key: key, identity: identity, image: image, photoID: id, size: targetSize)
                    }
                }
            }
        }
        return token
    }

    func cancel(_ token: Token) {
        guard let request = requests[token.key] else { return }
        request.callbacks.removeValue(forKey: token.subscriber)
        guard request.callbacks.isEmpty else { return }
        requests.removeValue(forKey: token.key)
        PHImageManager.default().cancelImageRequest(request.photoRequest)
    }

    private func finish(key: String, identity: UUID, image: UIImage?, photoID: String, size: CGSize) {
        guard let request = requests[key], request.identity == identity else { return }
        requests.removeValue(forKey: key)
        if let image { PhotoThumbnailCache.shared.store(image, for: photoID, size: size) }
        request.callbacks.values.forEach { $0(image) }
    }
}

enum FullFrameImageDecoder {
    nonisolated static func downsample(_ data: Data, maxPixel: CGFloat) -> UIImage? {
        let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

struct PhotoAssetImage: View {
    let photo: PhotoItem
    var targetSize: CGSize = CGSize(width: 900, height: 900)
    var contentMode: ContentMode = .fill
    var skipDegraded: Bool = false
    var onReady: () -> Void = {}

    @StateObject private var loader = PhotoImageLoader()

    var body: some View {
        let displayed = loader.image(for: photo.id)
            ?? PhotoThumbnailCache.shared.image(for: photo.id, size: targetSize)
            ?? PhotoThumbnailCache.shared.previewImage(for: photo.id)
        ZStack {
            if let image = displayed {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
                    .onAppear(perform: onReady)
            } else {
                PunctumTheme.surface
            }
        }
        .clipped()
        .onAppear(perform: startLoad)
        .onChange(of: photo.id) { _, _ in startLoad() }
        .onDisappear { loader.cancel() }
        .transaction { $0.animation = nil }
    }

    private func startLoad() {
        loader.load(
            asset: photo.asset,
            targetSize: targetSize,
            contentMode: contentMode == .fill ? .aspectFill : .aspectFit,
            skipDegraded: skipDegraded
        )
    }
}
