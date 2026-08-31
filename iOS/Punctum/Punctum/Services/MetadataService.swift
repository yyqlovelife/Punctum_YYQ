@preconcurrency import CoreLocation
import Foundation
import ImageIO
import Photos
import UIKit

actor MetadataService {
    static let shared = MetadataService()

    private var cache: [String: PhotoMetadata] = [:]
    private var metadataWaiters: [String: [CheckedContinuation<PhotoMetadata, Never>]] = [:]

    func metadata(for photo: PhotoItem) async -> PhotoMetadata {
        if let cached = cache[photo.id] { return cached }
        if metadataWaiters[photo.id] != nil {
            return await withCheckedContinuation { continuation in
                metadataWaiters[photo.id, default: []].append(continuation)
            }
        }
        metadataWaiters[photo.id] = []

        let original = try? await originalData(for: photo)
        let properties: [CFString: Any]
        if let data = original?.data,
           let source = CGImageSourceCreateWithData(data as CFData, nil),
           let raw = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] {
            properties = raw
        } else {
            properties = [:]
        }

        let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any] ?? [:]
        let tiff = properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any] ?? [:]
        let camera = cleanText(tiff[kCGImagePropertyTIFFModel] as? String)
            ?? cleanText(tiff[kCGImagePropertyTIFFMake] as? String)
        let exposure = number(exif[kCGImagePropertyExifExposureTime])
        let focal = number(exif[kCGImagePropertyExifFocalLength])
        let aperture = number(exif[kCGImagePropertyExifFNumber])
        let isoValues = exif[kCGImagePropertyExifISOSpeedRatings] as? [NSNumber]
        let exifTaken = (exif[kCGImagePropertyExifDateTimeOriginal] as? String)
            .flatMap(PunctumFormatting.detailDate(fromExif:))

        let coordinate = photo.asset.location.map {
            String(format: "%.5f, %.5f", $0.coordinate.latitude, $0.coordinate.longitude)
        }

        let value = PhotoMetadata(
            dateTaken: exifTaken ?? PunctumFormatting.detailDate(photo.creationDate),
            location: nil,
            coordinate: coordinate,
            camera: camera,
            exposureTime: PunctumFormatting.exposure(seconds: exposure),
            focalLength: PunctumFormatting.focalLength(focal),
            aperture: PunctumFormatting.aperture(aperture),
            iso: isoValues?.first.map { "ISO \($0.intValue)" },
            resolution: photo.width > 0 && photo.height > 0 ? "\(photo.width) × \(photo.height)" : nil,
            fileSize: PunctumFormatting.fileSize(original?.data.count ?? 0)
        )
        cache[photo.id] = value
        let waiters = metadataWaiters.removeValue(forKey: photo.id) ?? []
        waiters.forEach { $0.resume(returning: value) }
        return value
    }

    func locationName(for photo: PhotoItem) async -> String? {
        if let location = cache[photo.id]?.location { return location }
        guard let location = photo.asset.location else { return nil }
        guard let name = await reverseGeocode(location) else { return nil }
        if var value = cache[photo.id] {
            value.location = name
            cache[photo.id] = value
        }
        return name
    }

    func originalData(for photo: PhotoItem) async throws -> (data: Data, filename: String, uniformType: String?) {
        try await withCheckedThrowingContinuation { continuation in
            let options = PHImageRequestOptions()
            options.version = .original
            options.deliveryMode = .highQualityFormat
            options.resizeMode = .none
            options.isNetworkAccessAllowed = true
            PHImageManager.default().requestImageDataAndOrientation(for: photo.asset, options: options) { data, uti, _, _ in
                guard let data else {
                    continuation.resume(throwing: PhotoLibraryError.dataUnavailable)
                    return
                }
                let filename = PHAssetResource.assetResources(for: photo.asset).first?.originalFilename ?? photo.name
                continuation.resume(returning: (data, filename, uti))
            }
        }
    }

    func image(for photo: PhotoItem, targetLongEdge: CGFloat = 2200) async throws -> UIImage {
        try await withCheckedThrowingContinuation { continuation in
            let options = PHImageRequestOptions()
            options.version = .current
            options.deliveryMode = .highQualityFormat
            options.resizeMode = .exact
            options.isNetworkAccessAllowed = true
            let aspect = max(photo.hasKnownSize ? photo.aspectRatio : 1.5, 0.1)
            let size = aspect >= 1
                ? CGSize(width: targetLongEdge, height: targetLongEdge / aspect)
                : CGSize(width: targetLongEdge * aspect, height: targetLongEdge)
            PHImageManager.default().requestImage(
                for: photo.asset,
                targetSize: size,
                contentMode: .aspectFit,
                options: options
            ) { image, info in
                if (info?[PHImageCancelledKey] as? Bool) == true { return }
                if (info?[PHImageResultIsDegradedKey] as? Bool) == true { return }
                guard let image else {
                    continuation.resume(throwing: PhotoLibraryError.imageUnavailable)
                    return
                }
                continuation.resume(returning: image)
            }
        }
    }

    private func reverseGeocode(_ location: CLLocation) async -> String? {
        await withCheckedContinuation { continuation in
            let geocoder = CLGeocoder()
            geocoder.reverseGeocodeLocation(location, preferredLocale: Locale(identifier: "zh_CN")) { placemarks, _ in
                guard let mark = placemarks?.first else {
                    continuation.resume(returning: nil)
                    return
                }
                var parts: [String] = []
                for candidate in [mark.name, mark.locality, mark.administrativeArea, mark.country] {
                    guard let candidate, !candidate.isEmpty, !parts.contains(candidate) else { continue }
                    parts.append(candidate)
                }
                continuation.resume(returning: parts.prefix(3).joined(separator: " · ").nilIfEmpty)
                _ = geocoder
            }
        }
    }

    private func number(_ value: Any?) -> Double? {
        if let number = value as? NSNumber { return number.doubleValue }
        if let string = value as? String { return Double(string) }
        return nil
    }

    private func cleanText(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        let words = text.split(separator: " ")
        if words.count > 1,
           String(words[0]).caseInsensitiveCompare(String(words[1])) == .orderedSame {
            return words.dropFirst().joined(separator: " ")
        }
        return text.nilIfEmpty
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
