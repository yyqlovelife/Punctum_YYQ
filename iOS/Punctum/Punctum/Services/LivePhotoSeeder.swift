import AVFoundation
import CoreMedia
import ImageIO
import Photos
import UniformTypeIdentifiers
import UIKit

/// Simulator-only helper: Apple Live Photos are a still (JPEG/HEIC) plus a paired
/// MOV, linked by the same content identifier — not an Android Motion Photo container.
enum LivePhotoSeeder {
    private static let seededKey = "punctum.seededSimulatorLivePhotos"

    static func seedIfNeeded() async {
        #if targetEnvironment(simulator)
        guard !UserDefaults.standard.bool(forKey: seededKey) else { return }
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .authorized || status == .limited else { return }
        if livePhotoCount() >= 2 {
            UserDefaults.standard.set(true, forKey: seededKey)
            return
        }
        do {
            try await insertSyntheticLivePhoto(title: "LIVE A", hue: 0.12)
            try await insertSyntheticLivePhoto(title: "LIVE B", hue: 0.58)
            UserDefaults.standard.set(true, forKey: seededKey)
        } catch {
            return
        }
        #endif
    }

    private static func livePhotoCount() -> Int {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(
            format: "(mediaSubtype & %d) != 0",
            PHAssetMediaSubtype.photoLive.rawValue
        )
        return PHAsset.fetchAssets(with: .image, options: options).count
    }

    private static func insertSyntheticLivePhoto(title: String, hue: CGFloat) async throws {
        let identifier = UUID().uuidString
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("punctum-seed-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let photoURL = folder.appendingPathComponent("still.JPG")
        let videoURL = folder.appendingPathComponent("video.MOV")
        let image = makeStill(title: title, hue: hue)
        try writeStill(image, to: photoURL, contentIdentifier: identifier)
        try await writePairedVideo(from: image, to: videoURL, contentIdentifier: identifier)

        try await PHPhotoLibrary.shared().performChanges {
            let request = PHAssetCreationRequest.forAsset()
            request.addResource(with: .photo, fileURL: photoURL, options: nil)
            request.addResource(with: .pairedVideo, fileURL: videoURL, options: nil)
            request.creationDate = Date()
        }
    }

    private static func makeStill(title: String, hue: CGFloat) -> UIImage {
        let size = CGSize(width: 1200, height: 800)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let bounds = CGRect(origin: .zero, size: size)
            let top = UIColor(hue: hue, saturation: 0.35, brightness: 0.92, alpha: 1)
            let bottom = UIColor(hue: hue, saturation: 0.55, brightness: 0.38, alpha: 1)
            let colors = [top.cgColor, bottom.cgColor] as CFArray
            if let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: colors,
                locations: [0, 1]
            ) {
                context.cgContext.drawLinearGradient(
                    gradient,
                    start: CGPoint(x: 0, y: 0),
                    end: CGPoint(x: 0, y: size.height),
                    options: []
                )
            } else {
                top.setFill()
                context.fill(bounds)
            }
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = .center
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 86, weight: .black),
                .foregroundColor: UIColor.white.withAlphaComponent(0.92),
                .paragraphStyle: paragraph,
            ]
            title.draw(
                in: CGRect(x: 40, y: size.height / 2 - 60, width: size.width - 80, height: 120),
                withAttributes: attrs
            )
            let caption: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 28, weight: .medium),
                .foregroundColor: UIColor.white.withAlphaComponent(0.8),
                .paragraphStyle: paragraph,
            ]
            "Apple Live Photo".draw(
                in: CGRect(x: 40, y: size.height / 2 + 50, width: size.width - 80, height: 40),
                withAttributes: caption
            )
        }
    }

    private static func writeStill(_ image: UIImage, to url: URL, contentIdentifier: String) throws {
        guard let cgImage = image.cgImage,
              let destination = CGImageDestinationCreateWithURL(
                url as CFURL,
                UTType.jpeg.identifier as CFString,
                1,
                nil
              ) else {
            throw PhotoLibraryError.saveFailed
        }
        let metadata: [CFString: Any] = [
            kCGImagePropertyMakerAppleDictionary: ["17": contentIdentifier]
        ]
        CGImageDestinationAddImage(destination, cgImage, metadata as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            throw PhotoLibraryError.saveFailed
        }
    }

    private static func writePairedVideo(
        from image: UIImage,
        to url: URL,
        contentIdentifier: String
    ) async throws {
        let width = 1200
        let height = 800
        if FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }

        let writer = try AVAssetWriter(outputURL: url, fileType: .mov)
        writer.metadata = [contentIdentifierItem(contentIdentifier)]

        let input = AVAssetWriterInput(
            mediaType: .video,
            outputSettings: [
                AVVideoCodecKey: AVVideoCodecType.h264,
                AVVideoWidthKey: width,
                AVVideoHeightKey: height,
            ]
        )
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(
            assetWriterInput: input,
            sourcePixelBufferAttributes: [
                kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                kCVPixelBufferWidthKey as String: width,
                kCVPixelBufferHeightKey as String: height,
            ]
        )
        writer.add(input)

        var metadataInput: AVAssetWriterInput?
        var metadataAdaptor: AVAssetWriterInputMetadataAdaptor?
        if let format = stillImageTimeFormat() {
            let meta = AVAssetWriterInput(mediaType: .metadata, outputSettings: nil, sourceFormatHint: format)
            meta.expectsMediaDataInRealTime = false
            if writer.canAdd(meta) {
                writer.add(meta)
                metadataInput = meta
                metadataAdaptor = AVAssetWriterInputMetadataAdaptor(assetWriterInput: meta)
            }
        }

        guard writer.startWriting() else {
            throw writer.error ?? PhotoLibraryError.saveFailed
        }
        writer.startSession(atSourceTime: .zero)
        metadataAdaptor?.append(stillImageTimeGroup())

        guard let buffer = pixelBuffer(from: image, width: width, height: height) else {
            throw PhotoLibraryError.saveFailed
        }
        let fps: Int32 = 30
        let frames = 45
        for index in 0..<frames {
            while !input.isReadyForMoreMediaData {
                try await Task.sleep(for: .milliseconds(4))
            }
            let time = CMTime(value: CMTimeValue(index), timescale: fps)
            guard adaptor.append(buffer, withPresentationTime: time) else {
                throw writer.error ?? PhotoLibraryError.saveFailed
            }
        }
        input.markAsFinished()
        metadataInput?.markAsFinished()
        let end = CMTime(value: CMTimeValue(frames), timescale: fps)
        writer.endSession(atSourceTime: end)
        await writer.finishWriting()
        if writer.status != .completed {
            throw writer.error ?? PhotoLibraryError.saveFailed
        }
    }

    private static func contentIdentifierItem(_ identifier: String) -> AVMutableMetadataItem {
        let item = AVMutableMetadataItem()
        item.identifier = .quickTimeMetadataContentIdentifier
        item.key = AVMetadataKey.quickTimeMetadataKeyContentIdentifier as NSString
        item.keySpace = .quickTimeMetadata
        item.value = identifier as NSString
        item.dataType = kCMMetadataBaseDataType_UTF8 as String
        return item
    }

    private static func stillImageTimeFormat() -> CMFormatDescription? {
        let spec: [String: Any] = [
            kCMMetadataFormatDescriptionMetadataSpecificationKey_Identifier as String:
                "mdta/com.apple.quicktime.still-image-time",
            kCMMetadataFormatDescriptionMetadataSpecificationKey_DataType as String:
                kCMMetadataBaseDataType_SInt8 as String,
        ]
        var format: CMFormatDescription?
        CMMetadataFormatDescriptionCreateWithMetadataSpecifications(
            allocator: kCFAllocatorDefault,
            metadataType: kCMMetadataFormatType_Boxed,
            metadataSpecifications: [spec] as CFArray,
            formatDescriptionOut: &format
        )
        return format
    }

    private static func stillImageTimeGroup() -> AVTimedMetadataGroup {
        let item = AVMutableMetadataItem()
        item.key = "com.apple.quicktime.still-image-time" as NSString
        item.keySpace = .quickTimeMetadata
        item.value = 0 as NSNumber
        item.dataType = kCMMetadataBaseDataType_SInt8 as String
        return AVTimedMetadataGroup(
            items: [item],
            timeRange: CMTimeRange(start: .zero, duration: CMTime(value: 1, timescale: 30))
        )
    }

    private static func pixelBuffer(from image: UIImage, width: Int, height: Int) -> CVPixelBuffer? {
        var buffer: CVPixelBuffer?
        let attrs: [CFString: Any] = [
            kCVPixelBufferCGImageCompatibilityKey: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey: true,
        ]
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            kCVPixelFormatType_32BGRA,
            attrs as CFDictionary,
            &buffer
        )
        guard status == kCVReturnSuccess, let buffer else { return nil }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        let bitmapInfo = CGBitmapInfo.byteOrder32Little.rawValue | CGImageAlphaInfo.premultipliedFirst.rawValue
        guard let context = CGContext(
            data: CVPixelBufferGetBaseAddress(buffer),
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: CVPixelBufferGetBytesPerRow(buffer),
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: bitmapInfo
        ), let cgImage = image.cgImage else {
            return nil
        }
        context.interpolationQuality = .high
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        return buffer
    }
}
