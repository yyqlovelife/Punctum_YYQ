import Foundation
import Photos
import UIKit

struct PunctumGallery: Identifiable, Codable, Equatable, Hashable {
    let id: String
    var displayName: String
    var styleID: String = "original"
    var sortOrder: PhotoSortOrder = .capture

    init(id: String, displayName: String, styleID: String = "original", sortOrder: PhotoSortOrder = .capture) {
        self.id = id; self.displayName = displayName; self.styleID = styleID; self.sortOrder = sortOrder
    }
    private enum CodingKeys: String, CodingKey { case id, displayName, styleID, sortOrder }
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(String.self, forKey: .id)
        displayName = try values.decode(String.self, forKey: .displayName)
        styleID = try values.decodeIfPresent(String.self, forKey: .styleID) ?? "original"
        sortOrder = PhotoSortOrder(rawValue: try values.decodeIfPresent(String.self, forKey: .sortOrder) ?? "") ?? .capture
    }
}

enum InvitationCardStyle: String, Codable, CaseIterable {
    case postcard
    case ticket
    case reversalFilm = "reversal_film"

    mutating func toggle() {
        switch self {
        case .postcard: self = .ticket
        case .ticket: self = .reversalFilm
        case .reversalFilm: self = .postcard
        }
    }
}

struct AlbumOption: Identifiable {
    let collection: PHAssetCollection
    let count: Int
    let cover: PHAsset?

    var id: String { collection.localIdentifier }
    var title: String { collection.localizedTitle ?? "未命名图集" }
}

struct GalleryOverview: Identifiable {
    let gallery: PunctumGallery
    let count: Int
    let timeSpan: String
    var covers: [PhotoItem]
    var postcardCoverPath: String? = nil
    var ticketCoverPath: String? = nil
    var ticketDominantColorARGB: UInt32? = nil
    var ticketColorVersion: Int = 0
    var id: String { gallery.id }
}

struct GalleryOverviewSnapshot: Codable {
    let galleryID: String
    let count: Int
    let timeSpan: String
    let coverAssetIDs: [String]
    let postcardCoverPath: String?
    let ticketCoverPath: String?
    let ticketDominantColorARGB: UInt32?
    let ticketColorVersion: Int
    let updatedAt: Date
}

struct PhotoItem: Identifiable, Hashable {
    let asset: PHAsset
    let name: String
    var resolvedWidth: Int?
    var resolvedHeight: Int?

    var id: String { asset.localIdentifier }
    var width: Int {
        let value = resolvedWidth ?? asset.pixelWidth
        return value > 0 ? value : 0
    }
    var height: Int {
        let value = resolvedHeight ?? asset.pixelHeight
        return value > 0 ? value : 0
    }
    var creationDate: Date? { asset.creationDate }
    var hasKnownSize: Bool { width > 0 && height > 0 }
    var isLivePhoto: Bool { asset.mediaSubtypes.contains(.photoLive) }
    var aspectRatio: CGFloat {
        guard hasKnownSize else { return 0 }
        return CGFloat(width) / CGFloat(height)
    }

    var thumbnailTargetSize: CGSize {
        let maxEdge: CGFloat = min(900, max(360, UIScreen.main.bounds.width * UIScreen.main.scale / 2))
        guard hasKnownSize else { return CGSize(width: maxEdge, height: maxEdge) }
        if aspectRatio >= 1 {
            return CGSize(width: maxEdge, height: (maxEdge / aspectRatio).rounded())
        }
        return CGSize(width: (maxEdge * aspectRatio).rounded(), height: maxEdge)
    }

    static func == (lhs: PhotoItem, rhs: PhotoItem) -> Bool {
        lhs.id == rhs.id && lhs.width == rhs.width && lhs.height == rhs.height
    }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

enum GalleryReturnPosition {
    static func targetID(
        ids: [String], viewedID: String?, fallbackIndex: Int, excluding: Set<String>
    ) -> String? {
        if let viewedID, ids.contains(viewedID), !excluding.contains(viewedID) { return viewedID }
        let origin = viewedID.flatMap { ids.firstIndex(of: $0) } ?? max(fallbackIndex, 0)
        if origin < ids.count, let next = ids[origin...].first(where: { !excluding.contains($0) }) {
            return next
        }
        return ids.prefix(origin).last(where: { !excluding.contains($0) })
    }

    static func shouldCenter(_ photoID: String?, entryVisibleIDs: Set<String>) -> Bool {
        guard let photoID else { return false }
        return !entryVisibleIDs.contains(photoID)
    }

    static func visibleIndices(
        rowFrames: [Int: CGRect], viewport: CGRect, photoCount: Int
    ) -> Set<Int> {
        Set(rowFrames.flatMap { start, frame -> [Int] in
            guard frame.intersects(viewport), start >= 0, start < photoCount else { return [] }
            return Array(start..<min(start + 2, photoCount))
        })
    }
}

enum GalleryPhotoSelection {
    // Metadata can finish after deletion or a foreground refresh changes positions.
    // Resolve the tapped asset in the current snapshot, never reuse its old offset.
    static func index(
        for photoID: String, in photos: [PhotoItem], expectedGalleryID: String, currentGalleryID: String?
    ) -> Int? {
        guard currentGalleryID == expectedGalleryID else { return nil }
        return photos.firstIndex { $0.id == photoID }
    }
}

struct PendingPhotoDeletion: Identifiable {
    let id = UUID()
    let photos: [PhotoItem]
}

struct PhotoMetadata: Equatable {
    var dateTaken: String?
    var location: String?
    var coordinate: String?
    var camera: String?
    var exposureTime: String?
    var focalLength: String?
    var aperture: String?
    var iso: String?
    var resolution: String?
    var fileSize: String?
}

// Keys are captured before background sorting so PhotoKit/EXIF lookups stay outside the comparator.
enum PhotoSortOrder: String, Codable {
    case capture, modified, sync
    var label: String {
        switch self {
        case .capture: return "拍摄"
        case .modified: return "编辑"
        case .sync: return "同步"
        }
    }
    var next: Self {
        switch self {
        case .capture: return .modified
        case .modified: return .sync
        case .sync: return .capture
        }
    }
    var accessibilityDescription: String {
        self == .sync ? "按系统相册自定义的位置排序" : "按\(label)时间排序，最新在前"
    }
    func precedes(capture lhsCapture: Date, modified lhsModified: Date?, id lhsID: String,
                  capture rhsCapture: Date, modified rhsModified: Date?, id rhsID: String) -> Bool {
        // Sync keeps PhotoKit's collection order and must never use a time comparator.
        guard self != .sync else { return false }
        let lhs = self == .capture ? lhsCapture : (lhsModified ?? lhsCapture)
        let rhs = self == .capture ? rhsCapture : (rhsModified ?? rhsCapture)
        if lhs != rhs { return lhs > rhs }
        if self == .modified, lhsCapture != rhsCapture { return lhsCapture > rhsCapture }
        if self == .capture, lhsModified != rhsModified {
            return (lhsModified ?? .distantPast) > (rhsModified ?? .distantPast)
        }
        return lhsID < rhsID
    }
}
