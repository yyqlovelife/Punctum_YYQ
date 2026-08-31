import ImageIO
import Photos
import SwiftUI
import UIKit

@MainActor
final class PhotoThumbnailCache {
    static let shared = PhotoThumbnailCache()
    private let cache = NSCache<NSString, UIImage>()

    private init() {
        cache.countLimit = 180
        cache.totalCostLimit = 64 * 1024 * 1024
    }

    func image(for id: String, size: CGSize) -> UIImage? {
        cache.object(forKey: Self.key(id, size) as NSString)
    }

    func store(_ image: UIImage, for id: String, size: CGSize) {
        let cost = Int(image.size.width * image.size.height * image.scale * image.scale * 4)
        cache.setObject(image, forKey: Self.key(id, size) as NSString, cost: max(cost, 1))
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
                    guard let self, self.loadingID == id else { return }
                    PhotoThumbnailCache.shared.store(image, for: id, size: targetSize)
                    self.image = image
                }
            }
        }
    }

    func cancel() {
        cancelRequest()
        loadingID = nil
    }

    private func cancelRequest() {
        guard requestID != PHInvalidImageRequestID else { return }
        PHImageManager.default().cancelImageRequest(requestID)
        requestID = PHInvalidImageRequestID
    }

    /// Decode the still JPEG/HEIC itself. Photos' opportunistic thumbnails are often
    /// square center-crops, which chops VIVO-style white-bar watermarks.
    private func loadFullFrame(asset: PHAsset, targetSize: CGSize) {
        let id = asset.localIdentifier
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.version = .current
        options.resizeMode = .none
        options.isNetworkAccessAllowed = true
        let maxPixel = max(max(targetSize.width, targetSize.height), 1)
        requestID = PHImageManager.default().requestImageDataAndOrientation(
            for: asset,
            options: options
        ) { [weak self] data, _, _, info in
            guard info?[PHImageCancelledKey] as? Bool != true else { return }
            guard let data, let image = Self.downsample(data, maxPixel: maxPixel) else { return }
            Task { @MainActor in
                guard let self, self.loadingID == id else { return }
                PhotoThumbnailCache.shared.store(image, for: id, size: targetSize)
                self.image = image
            }
        }
    }

    private static func downsample(_ data: Data, maxPixel: CGFloat) -> UIImage? {
        let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions) else {
            return UIImage(data: data)
        }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return UIImage(data: data)
        }
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
        let displayed = loader.image ?? PhotoThumbnailCache.shared.image(for: photo.id, size: targetSize)
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
        .onChange(of: photo.id) { _, newID in
            loader.image = PhotoThumbnailCache.shared.image(for: newID, size: targetSize)
            startLoad()
        }
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
