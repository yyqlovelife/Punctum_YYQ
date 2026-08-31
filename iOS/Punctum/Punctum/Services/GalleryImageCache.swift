import Photos
import UIKit

struct GalleryCoverAssets {
    let postcardCoverPath: String?
    let ticketCoverPath: String?
    let ticketDominantColorARGB: UInt32?
    let ticketColorVersion: Int
}

@MainActor
final class GalleryImageCache {
    static let shared = GalleryImageCache()
    static let ticketColorVersion = 5

    private let manager = PHCachingImageManager()
    private let fileManager = FileManager.default
    private let coverSize = CGSize(width: 900, height: 900)
    private let ticketSize = CGSize(width: 1_400, height: 1_400)

    private lazy var cacheDirectory: URL = {
        let root = fileManager.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let directory = root.appendingPathComponent("punctum_covers_v1", isDirectory: true)
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }()

    func buildCovers(galleryID: String, covers: [PhotoItem]) async -> GalleryCoverAssets {
        guard let first = covers.first else {
            return GalleryCoverAssets(
                postcardCoverPath: nil,
                ticketCoverPath: nil,
                ticketDominantColorARGB: nil,
                ticketColorVersion: Self.ticketColorVersion
            )
        }

        let key = cacheKey(galleryID: galleryID, covers: covers)
        let postcardURL = cacheDirectory.appendingPathComponent("\(key)-postcard.jpg")
        let ticketURL = cacheDirectory.appendingPathComponent("\(key)-ticket.jpg")

        let ticketImage: UIImage?
        if fileManager.fileExists(atPath: ticketURL.path) {
            ticketImage = UIImage(contentsOfFile: ticketURL.path)
        } else {
            ticketImage = await requestImage(for: first.asset, targetSize: ticketSize)
            if let data = ticketImage?.jpegData(compressionQuality: 0.91) {
                try? data.write(to: ticketURL, options: .atomic)
            }
        }

        if !fileManager.fileExists(atPath: postcardURL.path) {
            var images: [UIImage] = []
            for cover in covers.prefix(4) {
                if let image = await requestImage(
                    for: cover.asset,
                    targetSize: CGSize(width: 520, height: 520)
                ) {
                    images.append(image)
                }
            }
            if let collage = makePostcardCollage(images: images),
               let data = collage.jpegData(compressionQuality: 0.92) {
                try? data.write(to: postcardURL, options: .atomic)
            }
        }

        return GalleryCoverAssets(
            postcardCoverPath: validPath(postcardURL),
            ticketCoverPath: validPath(ticketURL),
            ticketDominantColorARGB: ticketImage.flatMap(dominantColorARGB),
            ticketColorVersion: Self.ticketColorVersion
        )
    }

    func startCaching(_ photos: [PhotoItem], targetSize: CGSize) {
        manager.startCachingImages(
            for: photos.map(\.asset),
            targetSize: targetSize,
            contentMode: .aspectFit,
            options: imageRequestOptions()
        )
    }

    func isValidCacheFile(_ path: String?) -> Bool {
        guard let path else { return false }
        return fileManager.fileExists(atPath: path)
    }

    private func requestImage(for asset: PHAsset, targetSize: CGSize) async -> UIImage? {
        await withCheckedContinuation { continuation in
            let gate = ContinuationGate(continuation)
            manager.requestImage(
                for: asset,
                targetSize: targetSize,
                contentMode: .aspectFill,
                options: imageRequestOptions()
            ) { image, info in
                if info?[PHImageResultIsDegradedKey] as? Bool == true { return }
                gate.resume(image)
            }
        }
    }

    private func imageRequestOptions() -> PHImageRequestOptions {
        let options = PHImageRequestOptions()
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .exact
        options.isNetworkAccessAllowed = true
        return options
    }

    private func makePostcardCollage(images: [UIImage]) -> UIImage? {
        guard let first = images.first else { return nil }
        let renderer = UIGraphicsImageRenderer(size: coverSize)
        return renderer.image { context in
            UIColor(red: 21 / 255, green: 17 / 255, blue: 14 / 255, alpha: 1).setFill()
            context.fill(CGRect(origin: .zero, size: coverSize))

            let pad: CGFloat = 34
            let gap: CGFloat = 28
            if images.count < 4 {
                drawCenterCrop(
                    first,
                    in: CGRect(
                        x: pad,
                        y: pad,
                        width: coverSize.width - pad * 2,
                        height: coverSize.height - pad * 2
                    ),
                    context: context.cgContext
                )
            } else {
                let cell = (coverSize.width - pad * 2 - gap) / 2
                let rects = [
                    CGRect(x: pad, y: pad, width: cell, height: cell),
                    CGRect(x: pad + cell + gap, y: pad, width: cell, height: cell),
                    CGRect(x: pad, y: pad + cell + gap, width: cell, height: cell),
                    CGRect(x: pad + cell + gap, y: pad + cell + gap, width: cell, height: cell),
                ]
                for (image, rect) in zip(images.prefix(4), rects) {
                    drawCenterCrop(image, in: rect, context: context.cgContext)
                }
            }
        }
    }

    private func drawCenterCrop(_ image: UIImage, in rect: CGRect, context: CGContext) {
        guard image.size.width > 0, image.size.height > 0 else { return }
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let drawRect = CGRect(
            x: rect.midX - drawSize.width / 2,
            y: rect.midY - drawSize.height / 2,
            width: drawSize.width,
            height: drawSize.height
        )
        context.saveGState()
        context.clip(to: rect)
        image.draw(in: drawRect)
        context.restoreGState()
    }

    private func dominantColorARGB(_ image: UIImage) -> UInt32? {
        guard let cgImage = image.cgImage else { return nil }
        let width = 128
        let height = 128
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }

        context.interpolationQuality = .medium
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        var buckets: [Int: Int] = [:]
        for index in stride(from: 0, to: pixels.count, by: 4) {
            guard pixels[index + 3] > 127 else { continue }
            let red = Int(pixels[index])
            let green = Int(pixels[index + 1])
            let blue = Int(pixels[index + 2])
            let key = ((red >> 4) << 8) | ((green >> 4) << 4) | (blue >> 4)
            buckets[key, default: 0] += 1
        }

        let candidates = buckets.sorted { $0.value > $1.value }
        var fallback: UIColor?
        var selected: UIColor?
        for (key, _) in candidates.prefix(24) {
            let color = UIColor(
                red: CGFloat(((key >> 8) & 0xF) * 17) / 255,
                green: CGFloat(((key >> 4) & 0xF) * 17) / 255,
                blue: CGFloat((key & 0xF) * 17) / 255,
                alpha: 1
            )
            var hue: CGFloat = 0
            var saturation: CGFloat = 0
            var brightness: CGFloat = 0
            color.getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: nil)
            let nearBlack = brightness <= 0.08
            let nearWhite = brightness >= 0.94 && saturation <= 0.12
            guard !nearBlack, !nearWhite else { continue }
            if fallback == nil { fallback = color }
            if saturation >= 0.12 {
                selected = color
                break
            }
        }

        guard let color = selected ?? fallback else { return nil }
        var hue: CGFloat = 0
        var saturation: CGFloat = 0
        var brightness: CGFloat = 0
        color.getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: nil)
        if saturation < 0.12 {
            saturation = 0
            brightness = min(max(brightness, 0.32), 0.44)
        } else {
            saturation = min(max(saturation, 0.36), 0.68)
            brightness = min(max(brightness, 0.30), 0.48)
        }

        let normalized = UIColor(hue: hue, saturation: saturation, brightness: brightness, alpha: 1)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        normalized.getRed(&red, green: &green, blue: &blue, alpha: nil)
        return (0xFF << 24)
            | (UInt32((red * 255).rounded()) << 16)
            | (UInt32((green * 255).rounded()) << 8)
            | UInt32((blue * 255).rounded())
    }

    private func cacheKey(galleryID: String, covers: [PhotoItem]) -> String {
        let identity = ([galleryID] + covers.map {
            "\($0.id)|\($0.asset.modificationDate?.timeIntervalSince1970 ?? 0)"
        }).joined(separator: "|")
        var hash: UInt64 = 14_695_981_039_346_656_037
        for byte in identity.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 1_099_511_628_211
        }
        return String(hash, radix: 16)
    }

    private func validPath(_ url: URL) -> String? {
        fileManager.fileExists(atPath: url.path) ? url.path : nil
    }
}

private final class ContinuationGate<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<Value, Never>?

    init(_ continuation: CheckedContinuation<Value, Never>) {
        self.continuation = continuation
    }

    func resume(_ value: Value) {
        lock.lock()
        let continuation = continuation
        self.continuation = nil
        lock.unlock()
        continuation?.resume(returning: value)
    }
}
