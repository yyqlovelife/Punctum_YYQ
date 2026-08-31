import Foundation
import Photos
import UIKit

@MainActor
final class PhotoLibraryService {
    static let shared = PhotoLibraryService()

    var authorizationStatus: PHAuthorizationStatus {
        PHPhotoLibrary.authorizationStatus(for: .readWrite)
    }

    func requestAuthorization() async -> PHAuthorizationStatus {
        await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
                continuation.resume(returning: status)
            }
        }
    }

    func fetchAlbums(includingEmpty: Bool = false) -> [AlbumOption] {
        var collections: [PHAssetCollection] = []
        var seen = Set<String>()

        func append(_ result: PHFetchResult<PHAssetCollection>) {
            result.enumerateObjects { collection, _, _ in
                guard seen.insert(collection.localIdentifier).inserted else { return }
                guard Self.shouldOffer(collection) else { return }
                collections.append(collection)
            }
        }

        append(PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: nil))
        append(PHAssetCollection.fetchAssetCollections(with: .smartAlbum, subtype: .any, options: nil))

        return collections.compactMap { collection in
            let result = fetchResult(in: collection)
            guard includingEmpty || result.count > 0 else { return nil }
            return AlbumOption(collection: collection, count: result.count, cover: result.firstObject)
        }
        .sorted { lhs, rhs in
            lhs.title.localizedStandardCompare(rhs.title) == .orderedAscending
        }
    }

    func collection(localIdentifier: String) -> PHAssetCollection? {
        PHAssetCollection.fetchAssetCollections(
            withLocalIdentifiers: [localIdentifier],
            options: nil
        ).firstObject
    }

    func resolvedCollection(for gallery: PunctumGallery) -> PHAssetCollection? {
        let stored = collection(localIdentifier: gallery.id)
        if let stored, fetchResult(in: stored).count > 0 {
            return stored
        }
        let wantsLibrary = gallery.displayName.compare("Recents", options: .caseInsensitive) == .orderedSame
            || stored?.assetCollectionSubtype == .smartAlbumUserLibrary
            || stored?.assetCollectionSubtype == .smartAlbumRecentlyAdded
        if wantsLibrary, let library = userLibraryCollection() {
            return library
        }
        return stored
    }

    private func userLibraryCollection() -> PHAssetCollection? {
        PHAssetCollection.fetchAssetCollections(
            with: .smartAlbum,
            subtype: .smartAlbumUserLibrary,
            options: nil
        ).firstObject
    }

    func galleryExists(_ gallery: PunctumGallery) -> Bool {
        collection(localIdentifier: gallery.id) != nil
    }

    func fetchPhotos(in gallery: PunctumGallery, excluding excludedIDs: Set<String> = [], limit: Int? = nil) -> [PhotoItem] {
        fetchPhotoPage(in: gallery, excluding: excludedIDs, startIndex: 0, limit: limit ?? .max).photos
    }

    func fetchPhotoPage(
        in gallery: PunctumGallery,
        excluding excludedIDs: Set<String> = [],
        startIndex: Int,
        limit: Int
    ) -> PhotoPage {
        let result = photoResult(for: gallery)
        var photos: [PhotoItem] = []
        photos.reserveCapacity(min(max(limit, 0), max(result.count - startIndex, 0)))
        var index = max(startIndex, 0)
        while index < result.count && photos.count < limit {
            let asset = result.object(at: index)
            index += 1
            guard !excludedIDs.contains(asset.localIdentifier) else { continue }
            photos.append(PhotoItem(asset: asset, name: "Photo"))
        }
        return PhotoPage(photos: photos, nextIndex: index, total: result.count)
    }

    func ensureKnownSizes(_ photos: [PhotoItem]) -> [PhotoItem] {
        photos.map { photo in
            guard !photo.hasKnownSize else { return photo }
            var copy = photo
            copy.resolvedWidth = max(photo.asset.pixelWidth, 0)
            copy.resolvedHeight = max(photo.asset.pixelHeight, 0)
            return copy
        }
    }

    func resolveMissingSizes(_ photos: [PhotoItem]) async -> [PhotoItem] {
        var result = photos
        for index in result.indices where !result[index].hasKnownSize {
            if let size = await requestPixelSize(for: result[index].asset) {
                result[index].resolvedWidth = Int(size.width.rounded())
                result[index].resolvedHeight = Int(size.height.rounded())
            }
        }
        return result
    }

    func latestPhotos(
        in gallery: PunctumGallery,
        limit: Int = 4,
        excluding excludedIDs: Set<String> = []
    ) -> [PhotoItem] {
        let result = photoResult(for: gallery, limit: max(limit + excludedIDs.count, limit))
        var photos: [PhotoItem] = []
        for index in 0..<result.count {
            let asset = result.object(at: index)
            guard !excludedIDs.contains(asset.localIdentifier) else { continue }
            photos.append(PhotoItem(asset: asset, name: "Photo"))
            if photos.count == limit { break }
        }
        return photos.sorted(by: newestFirst).prefix(limit).map { $0 }
    }

    func photoItems(localIdentifiers: [String]) -> [PhotoItem] {
        guard !localIdentifiers.isEmpty else { return [] }
        let result = PHAsset.fetchAssets(withLocalIdentifiers: localIdentifiers, options: nil)
        var byID: [String: PhotoItem] = [:]
        result.enumerateObjects { asset, _, _ in
            byID[asset.localIdentifier] = PhotoItem(asset: asset, name: "Photo")
        }
        return localIdentifiers.compactMap { byID[$0] }
    }

    func overview(
        for gallery: PunctumGallery,
        excluding excludedIDs: Set<String> = []
    ) -> GalleryOverview? {
        let result = photoResult(for: gallery)
        var count = result.count
        var newest: Date? = result.firstObject?.creationDate
        var oldest: Date? = result.lastObject?.creationDate

        if !excludedIDs.isEmpty {
            count = 0
            newest = nil
            oldest = nil
            result.enumerateObjects { asset, _, _ in
                guard !excludedIDs.contains(asset.localIdentifier) else { return }
                count += 1
                if newest == nil { newest = asset.creationDate }
                oldest = asset.creationDate
            }
        }

        return GalleryOverview(
            gallery: gallery,
            count: count,
            timeSpan: PunctumFormatting.timeSpan(oldest: oldest, newest: newest),
            covers: latestPhotos(in: gallery, limit: 4, excluding: excludedIDs)
        )
    }

    func delete(_ photo: PhotoItem) async throws {
        try await delete([photo])
    }

    func delete(_ photos: [PhotoItem]) async throws {
        guard !photos.isEmpty else { return }
        let assets = photos.map(\.asset)
        try await performChanges(fallback: .deleteFailed) {
            PHAssetChangeRequest.deleteAssets(assets as NSArray)
        }
    }

    func move(_ photo: PhotoItem, from source: PunctumGallery, to destination: PHAssetCollection) async throws {
        try await performChanges(fallback: .moveFailed) {
            PHAssetCollectionChangeRequest(for: destination)?
                .addAssets([photo.asset] as NSArray)
        }
        guard let sourceCollection = resolvedCollection(for: source),
              sourceCollection.localIdentifier != destination.localIdentifier,
              sourceCollection.assetCollectionType == .album else { return }
        try? await performChanges(fallback: .moveFailed) {
            PHAssetCollectionChangeRequest(for: sourceCollection)?
                .removeAssets([photo.asset] as NSArray)
        }
    }

    private func performChanges(
        fallback: PhotoLibraryError,
        _ changes: @escaping () -> Void
    ) async throws {
        try await withCheckedThrowingContinuation { continuation in
            PHPhotoLibrary.shared().performChanges(changes) { success, error in
                if success {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: error ?? fallback)
                }
            }
        }
    }

    func requestLivePhoto(for asset: PHAsset, targetSize: CGSize) async -> PHLivePhoto? {
        guard asset.mediaSubtypes.contains(.photoLive) else { return nil }
        if let livePhoto = await requestLivePhotoFromManager(asset, targetSize: targetSize, version: .current) {
            return livePhoto
        }
        if let livePhoto = await requestLivePhotoFromManager(asset, targetSize: targetSize, version: .original) {
            return livePhoto
        }
        return await requestLivePhotoFromPairedFiles(asset, targetSize: targetSize)
    }

    private func requestLivePhotoFromManager(
        _ asset: PHAsset,
        targetSize: CGSize,
        version: PHImageRequestOptionsVersion
    ) async -> PHLivePhoto? {
        await withCheckedContinuation { continuation in
            let options = PHLivePhotoRequestOptions()
            options.deliveryMode = .highQualityFormat
            options.version = version
            options.isNetworkAccessAllowed = true
            let gate = LivePhotoContinuationGate(continuation)
            PHImageManager.default().requestLivePhoto(
                for: asset,
                targetSize: targetSize,
                contentMode: .aspectFit,
                options: options
            ) { livePhoto, info in
                let degraded = info?[PHImageResultIsDegradedKey] as? Bool == true
                if info?[PHImageCancelledKey] as? Bool == true {
                    gate.resume(nil)
                    return
                }
                // A degraded PHLivePhoto is only a still-image preview. It has
                // no playable motion/audio payload, so wait for the final result.
                if degraded { return }
                if let livePhoto {
                    gate.resume(livePhoto)
                    return
                }
                gate.resume(nil)
            }
        }
    }

    private func requestLivePhotoFromPairedFiles(_ asset: PHAsset, targetSize: CGSize) async -> PHLivePhoto? {
        let resources = PHAssetResource.assetResources(for: asset)
        guard let photoResource = resources.first(where: { $0.type == .fullSizePhoto })
                ?? resources.first(where: { $0.type == .photo }),
              let videoResource = resources.first(where: { $0.type == .fullSizePairedVideo })
                ?? resources.first(where: { $0.type == .pairedVideo }) else {
            return nil
        }
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("punctum-live-\(UUID().uuidString)")
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let photoURL = folder.appendingPathComponent("still.JPG")
            let videoURL = folder.appendingPathComponent("video.MOV")
            try await writeResource(photoResource, to: photoURL)
            try await writeResource(videoResource, to: videoURL)
            return await withCheckedContinuation { continuation in
                let gate = LivePhotoContinuationGate(continuation)
                PHLivePhoto.request(
                    withResourceFileURLs: [photoURL, videoURL],
                    placeholderImage: nil,
                    targetSize: targetSize,
                    contentMode: .aspectFit
                ) { livePhoto, info in
                    if info[PHLivePhotoInfoCancelledKey] as? Bool == true ||
                        info[PHLivePhotoInfoErrorKey] != nil {
                        gate.resume(nil)
                        return
                    }
                    if info[PHLivePhotoInfoIsDegradedKey] as? Bool == true { return }
                    if let livePhoto {
                        gate.resume(livePhoto)
                        return
                    }
                    gate.resume(nil)
                }
            }
        } catch {
            return nil
        }
    }

    private func writeResource(_ resource: PHAssetResource, to url: URL) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let options = PHAssetResourceRequestOptions()
            options.isNetworkAccessAllowed = true
            PHAssetResourceManager.default().writeData(for: resource, toFile: url, options: options) { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }

    private func requestPixelSize(for asset: PHAsset) async -> CGSize? {
        if asset.pixelWidth > 0, asset.pixelHeight > 0 {
            return CGSize(width: asset.pixelWidth, height: asset.pixelHeight)
        }
        return await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.deliveryMode = .fastFormat
            options.resizeMode = .fast
            options.isNetworkAccessAllowed = true
            let gate = SizeContinuationGate(continuation)
            PHImageManager.default().requestImage(
                for: asset,
                targetSize: CGSize(width: 64, height: 64),
                contentMode: .aspectFit,
                options: options
            ) { image, info in
                if info?[PHImageCancelledKey] as? Bool == true {
                    gate.resume(nil)
                    return
                }
                guard let image else {
                    if info?[PHImageResultIsDegradedKey] as? Bool != true {
                        gate.resume(nil)
                    }
                    return
                }
                let size = CGSize(
                    width: image.size.width * image.scale,
                    height: image.size.height * image.scale
                )
                gate.resume(size)
            }
        }
    }

    private static func shouldOffer(_ collection: PHAssetCollection) -> Bool {
        if collection.assetCollectionType == .album { return true }
        switch collection.assetCollectionSubtype {
        case .smartAlbumUserLibrary, .smartAlbumScreenshots, .smartAlbumSelfPortraits, .smartAlbumPanoramas:
            return true
        default:
            return false
        }
    }

    private func photoResult(for gallery: PunctumGallery, limit: Int? = nil) -> PHFetchResult<PHAsset> {
        if let collection = resolvedCollection(for: gallery) {
            let result = fetchResult(in: collection, limit: limit)
            if result.count > 0 { return result }
            if isLibraryGallery(gallery, collection) {
                return fetchAllImages(limit: limit)
            }
            return result
        }
        if gallery.displayName.compare("Recents", options: .caseInsensitive) == .orderedSame {
            return fetchAllImages(limit: limit)
        }
        return PHAsset.fetchAssets(withLocalIdentifiers: [], options: nil)
    }

    private func isLibraryGallery(_ gallery: PunctumGallery, _ collection: PHAssetCollection) -> Bool {
        gallery.displayName.compare("Recents", options: .caseInsensitive) == .orderedSame
            || collection.assetCollectionSubtype == .smartAlbumUserLibrary
            || collection.assetCollectionSubtype == .smartAlbumRecentlyAdded
    }

    private func fetchAllImages(limit: Int?) -> PHFetchResult<PHAsset> {
        PHAsset.fetchAssets(with: .image, options: imageFetchOptions(limit: limit))
    }

    private func fetchResult(in collection: PHAssetCollection, limit: Int? = nil) -> PHFetchResult<PHAsset> {
        PHAsset.fetchAssets(in: collection, options: imageFetchOptions(limit: limit))
    }

    private func imageFetchOptions(limit: Int?) -> PHFetchOptions {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "mediaType == %d", PHAssetMediaType.image.rawValue)
        options.includeHiddenAssets = false
        options.sortDescriptors = [
            NSSortDescriptor(key: "creationDate", ascending: false),
            NSSortDescriptor(key: "modificationDate", ascending: false),
        ]
        if let limit { options.fetchLimit = max(limit, 0) }
        return options
    }

    private func newestFirst(_ lhs: PhotoItem, _ rhs: PhotoItem) -> Bool {
        let lhsCreated = lhs.asset.creationDate ?? .distantPast
        let rhsCreated = rhs.asset.creationDate ?? .distantPast
        if lhsCreated != rhsCreated { return lhsCreated > rhsCreated }

        let lhsModified = lhs.asset.modificationDate ?? .distantPast
        let rhsModified = rhs.asset.modificationDate ?? .distantPast
        if lhsModified != rhsModified { return lhsModified > rhsModified }
        return lhs.id > rhs.id
    }
}

private final class SizeContinuationGate: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<CGSize?, Never>?

    init(_ continuation: CheckedContinuation<CGSize?, Never>) {
        self.continuation = continuation
    }

    func resume(_ value: CGSize?) {
        lock.lock()
        let continuation = continuation
        self.continuation = nil
        lock.unlock()
        continuation?.resume(returning: value)
    }
}

private final class LivePhotoContinuationGate: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<PHLivePhoto?, Never>?

    init(_ continuation: CheckedContinuation<PHLivePhoto?, Never>) {
        self.continuation = continuation
    }

    func resume(_ value: PHLivePhoto?) {
        lock.lock()
        let continuation = continuation
        self.continuation = nil
        lock.unlock()
        continuation?.resume(returning: value)
    }
}

struct PhotoPage {
    let photos: [PhotoItem]
    let nextIndex: Int
    let total: Int
}

enum PhotoLibraryError: LocalizedError {
    case deleteFailed
    case moveFailed
    case dataUnavailable
    case imageUnavailable
    case saveFailed

    var errorDescription: String? {
        switch self {
        case .deleteFailed: return "照片未能移入系统相册的最近删除"
        case .moveFailed: return "照片未能移动到目标图集"
        case .dataUnavailable: return "无法读取照片原始文件"
        case .imageUnavailable: return "无法读取照片画面"
        case .saveFailed: return "无法保存到系统相册"
        }
    }
}
