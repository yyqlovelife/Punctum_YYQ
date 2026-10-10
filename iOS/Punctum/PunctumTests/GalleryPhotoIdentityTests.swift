import XCTest
import Photos
import SwiftUI
import UIKit
@testable import Punctum

private final class IdentityAsset: PHAsset, @unchecked Sendable {
    private let identifier: String
    init(_ identifier: String) { self.identifier = identifier; super.init() }
    override var localIdentifier: String { identifier }
    override var pixelWidth: Int { 120 }
    override var pixelHeight: Int { 80 }
}

@MainActor private final class IdentityFixture: ObservableObject {
    @Published var photos: [PhotoItem]
    @Published var returnTargetID: String?
    var galleryID = "identity-gallery"
    var selected: [(id: String, index: Int, metadataID: String?)] = []
    var waiting: [String: CheckedContinuation<PhotoMetadata, Never>] = [:]
    var delaysMetadata = false
    init(_ photos: [PhotoItem]) { self.photos = photos }
    func metadata(_ photo: PhotoItem) async -> PhotoMetadata {
        if delaysMetadata { return await withCheckedContinuation { waiting[photo.id] = $0 } }
        return PhotoMetadata(camera: photo.id)
    }
    func complete(_ id: String) { waiting.removeValue(forKey: id)?.resume(returning: PhotoMetadata(camera: id)) }
    func select(_ photo: PhotoItem, metadata: PhotoMetadata) {
        guard let index = GalleryPhotoSelection.index(for: photo.id, in: photos,
            expectedGalleryID: "identity-gallery", currentGalleryID: galleryID) else { return }
        selected.append((photos[index].id, index, metadata.camera))
    }
}

private struct IdentityHost: View {
    @ObservedObject var fixture: IdentityFixture
    var body: some View {
        GalleryScreen(gallery: PunctumGallery(id: "identity-gallery", displayName: "照片身份回归"),
            photos: fixture.photos, overview: nil, isLoading: false,
            onOpenSwitcher: {}, onRename: {}, sortingEnabled: true, onToggleSort: {},
            onSelectPhoto: { photo, metadata, _ in fixture.select(photo, metadata: metadata) },
            onDeletePhoto: { _ in }, returnTargetID: fixture.returnTargetID, onReturnPositioned: { _ in },
            metadataForPhoto: fixture.metadata)
    }
}

final class GalleryPhotoIdentityTests: XCTestCase {
    @MainActor private func photos(_ count: Int) -> [PhotoItem] {
        let prefix = UUID().uuidString
        return (0..<count).map { PhotoItem(asset: IdentityAsset("\(prefix)-\($0)"), name: "Photo \($0)") }
    }
    @MainActor func testSelectionFollowsAssetAcrossDeletionAndPageReordering() {
        let original = photos(240), target = original[169]
        let remaining = Array(original[80...]).filter { $0.id != original[100].id }
        XCTAssertEqual(GalleryPhotoSelection.index(for: target.id, in: remaining,
            expectedGalleryID: "album", currentGalleryID: "album"), 88)
        let reordered = Array(remaining.reversed())
        let index = GalleryPhotoSelection.index(for: target.id, in: reordered,
            expectedGalleryID: "album", currentGalleryID: "album")!
        XCTAssertEqual(reordered[index].id, target.id)
        XCTAssertNil(GalleryPhotoSelection.index(for: original[100].id, in: remaining,
            expectedGalleryID: "album", currentGalleryID: "album"))
        XCTAssertNil(GalleryPhotoSelection.index(for: target.id, in: remaining,
            expectedGalleryID: "album", currentGalleryID: "other-album"))
    }
    @MainActor func testRowIdentityChangesWhenDeletionChangesEitherPartner() {
        let original = photos(6), rows = GalleryPhotoRow.rows(from: original)
        var remaining = original; remaining.remove(at: 1)
        let changed = GalleryPhotoRow.rows(from: remaining)
        XCTAssertEqual(rows[0].id, changed[0].id) // Keep A as the scroll anchor.
        XCTAssertNotEqual(rows[0].contentID, changed[0].contentID) // Reset the changed A/C content.
        XCTAssertEqual(changed.map(\.contentID), [[original[0].id, original[2].id],
            [original[3].id, original[4].id], [original[5].id]])
        XCTAssertEqual(changed.map(\.startIndex), [0, 2, 4])
    }
    @MainActor func testImageLoaderCannotShowPreviousAssetDuringReuse() {
        let items = photos(2), red = solidImage(.red)
        PhotoThumbnailCache.shared.store(red, for: items[0].id, size: items[0].thumbnailTargetSize)
        let loader = PhotoImageLoader()
        loader.load(asset: items[0].asset, targetSize: items[0].thumbnailTargetSize)
        XCTAssertTrue(loader.image(for: items[0].id) === red)
        XCTAssertNil(loader.image(for: items[1].id))
        loader.load(asset: items[1].asset, targetSize: items[1].thumbnailTargetSize)
        XCTAssertNil(loader.image)
        XCTAssertNil(loader.image(for: items[0].id))
        loader.cancel()
    }
    @MainActor func testNativeGridDisplaysAndOpensSamePhotosAfterRepeatedOddDeletions() async throws {
        let items = photos(8)
        let colors: [UIColor] = [.red, .green, .blue, .yellow, .magenta, .cyan, .orange, .purple]
        for (photo, color) in zip(items, colors) {
            PhotoThumbnailCache.shared.store(solidImage(color), for: photo.id, size: photo.thumbnailTargetSize)
        }
        let fixture = IdentityFixture(Array(items.prefix(6)))
        let (host, window) = host(fixture); defer { window.isHidden = true }
        var deleted = Set<String>()
        for deletedID in [items[0].id, items[2].id, items[5].id] {
            deleted.insert(deletedID)
            fixture.photos.removeAll { $0.id == deletedID }
            if let next = items.first(where: { !fixture.photos.contains($0) && !deleted.contains($0.id) }) {
                fixture.photos.append(next)
            }
            try await settle(host)
            var checked = 0
            for photo in fixture.photos {
                // Exercise the actual return-positioning path to bring each lazy row
                // into view; offscreen rows are not guaranteed to be instantiated.
                fixture.returnTargetID = photo.id
                try await settle(host)
                let touchViews = descendants(host.view).compactMap { $0 as? GridPressView }
                let touch = try XCTUnwrap(touchViews.first { $0.accessibilityValue == photo.id }, "Missing \(photo.name), rendered: \(touchViews.map { $0.accessibilityValue ?? "nil" })")
                let frame = touch.convert(touch.bounds, to: host.view)
                XCTAssertTrue(host.view.bounds.contains(CGPoint(x: frame.midX, y: frame.midY)))
                checked += 1
                let image = screenshot(host)
                let colorIndex = items.firstIndex { $0.id == photo.id }!
                assertPixel(image, point: CGPoint(x: frame.midX, y: frame.midY), color: colors[colorIndex])
                touch.onTap()
                try await Task.sleep(for: .milliseconds(30))
                XCTAssertEqual(fixture.selected.last?.id, photo.id)
                XCTAssertEqual(fixture.selected.last?.metadataID, photo.id)
            }
            XCTAssertEqual(checked, fixture.photos.count, "Every surviving photo is shown and tapped")
        }
        let attachment = XCTAttachment(image: screenshot(host))
        attachment.name = "Native grid after odd deletions - image and tap identity"
        attachment.lifetime = .keepAlways; add(attachment)
    }
    @MainActor func testDelayedTapKeepsPhotoIdentityWhenDeletionShiftsItsIndex() async throws {
        let items = photos(6), fixture = IdentityFixture([])
        fixture.photos = items; fixture.delaysMetadata = true
        let (host, window) = host(fixture); defer { window.isHidden = true }
        try await settle(host)
        let touch = try XCTUnwrap(descendants(host.view).compactMap { $0 as? GridPressView }
            .first { $0.accessibilityValue == items[1].id })
        touch.onTap(); try await Task.sleep(for: .milliseconds(50))
        XCTAssertNotNil(fixture.waiting[items[1].id])
        fixture.photos.removeFirst() // Tapped B moves from index 1 to index 0.
        try await settle(host); fixture.complete(items[1].id)
        try await Task.sleep(for: .milliseconds(50))
        XCTAssertEqual(fixture.selected.last?.id, items[1].id)
        XCTAssertEqual(fixture.selected.last?.index, 0)
        XCTAssertEqual(fixture.selected.last?.metadataID, items[1].id)
    }
    @MainActor func testDelayedTapIsDiscardedIfTappedPhotoWasDeleted() async throws {
        let items = photos(4), fixture = IdentityFixture([])
        fixture.photos = items; fixture.delaysMetadata = true
        let (host, window) = host(fixture); defer { window.isHidden = true }
        try await settle(host)
        let touch = try XCTUnwrap(descendants(host.view).compactMap { $0 as? GridPressView }
            .first { $0.accessibilityValue == items[1].id })
        touch.onTap(); try await Task.sleep(for: .milliseconds(50))
        fixture.photos.removeAll { $0.id == items[1].id }
        try await settle(host); fixture.complete(items[1].id)
        try await Task.sleep(for: .milliseconds(50))
        XCTAssertTrue(fixture.selected.isEmpty)
    }
    @MainActor func testRootViewOpensTappedAssetAfterDeletionCommitAndReturn() async throws {
        #if targetEnvironment(simulator)
        guard PHPhotoLibrary.authorizationStatus(for: .readWrite) == .authorized else {
            throw XCTSkip("PhotoKit fixture integration requires simulator full Photos access")
        }
        let name = "DeletionIdentity.\(UUID().uuidString)"
        var ids: [String] = [], albumID = ""
        let colors: [UIColor] = [.red, .green, .blue, .yellow, .magenta, .cyan]
        let images = colors.map { solidImage($0) }
        try await PHPhotoLibrary.shared().performChanges {
            let placeholders = images.map { PHAssetChangeRequest.creationRequestForAsset(from: $0).placeholderForCreatedAsset! }
            ids = placeholders.map(\.localIdentifier)
            let album = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
            albumID = album.placeholderForCreatedAssetCollection.localIdentifier
            album.addAssets(placeholders as NSArray)
        }
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let store = GalleryStore(defaults: defaults)
        store.saveGalleries([PunctumGallery(id: albumID, displayName: name, sortOrder: .sync)])
        let model = GalleryViewModel(store: store, library: PhotoLibraryService(), imageCache: .shared, startLibraryUpdates: false)
        model.selectGallery(albumID)
        for _ in 0..<100 {
            if model.photos.map(\.id) == ids { break }
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertEqual(model.photos.map(\.id), ids)
        let host = UIHostingController(rootView: RootView().environmentObject(model))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = host; window.makeKeyAndVisible()
        defer { window.isHidden = true }
        for round in 0..<2 {
            let tappedID = model.photos[1].id
            try await settle(host)
            let touch = try XCTUnwrap(descendants(host.view).compactMap { $0 as? GridPressView }
                .first { $0.accessibilityValue == tappedID })
            let session = model.detailSession
            touch.onTap()
            for _ in 0..<100 {
                if model.detailIndex != nil { break }
                try await Task.sleep(for: .milliseconds(20))
            }
            let index = try XCTUnwrap(model.detailIndex)
            XCTAssertEqual(model.photos[index].id, tappedID)
            XCTAssertNotEqual(model.detailSession, session)
            try await Task.sleep(for: .milliseconds(250))
            model.revealDetail()
            model.closeDetail(pendingPhotos: [], viewedID: tappedID, fallbackIndex: index)
            for _ in 0..<100 {
                if model.detailIndex == nil { break }
                try await Task.sleep(for: .milliseconds(20))
            }
            XCTAssertNil(model.detailIndex)
            if round == 0 {
                // These are callbacks after deletion has succeeded. No system deletion
                // is requested; only our private synthetic visit snapshot is changed.
                let first = model.photos[0]
                model.commitComparisonDeletion(first)
                let partner = model.photos[2]
                model.commitComparisonDeletion(partner)
                XCTAssertFalse(model.photos.contains { $0.id == first.id || $0.id == partner.id })
            }
        }
        #else
        throw XCTSkip("Synthetic PhotoKit fixtures are restricted to the simulator")
        #endif
    }

    @MainActor private func host(_ fixture: IdentityFixture) -> (UIHostingController<IdentityHost>, UIWindow) {
        let host = UIHostingController(rootView: IdentityHost(fixture: fixture))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = host; window.makeKeyAndVisible(); return (host, window)
    }
    @MainActor private func settle(_ host: UIViewController) async throws {
        try await Task.sleep(for: .milliseconds(160)); host.view.layoutIfNeeded()
    }
    @MainActor private func descendants(_ view: UIView) -> [UIView] {
        [view] + view.subviews.flatMap { descendants($0) }
    }
    @MainActor private func screenshot(_ host: UIViewController) -> UIImage {
        UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
    }
    @MainActor private func solidImage(_ color: UIColor) -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 120, height: 80)).image { context in
            color.setFill(); context.fill(CGRect(x: 0, y: 0, width: 120, height: 80))
        }
    }
    private func assertPixel(_ image: UIImage, point: CGPoint, color: UIColor, file: StaticString = #filePath, line: UInt = #line) {
        guard let pixel = image.cgImage?.cropping(to: CGRect(x: point.x * image.scale,
            y: point.y * image.scale, width: 1, height: 1)) else { XCTFail("No pixel", file: file, line: line); return }
        var rgba = [UInt8](repeating: 0, count: 4)
        rgba.withUnsafeMutableBytes { bytes in
            let context = CGContext(data: bytes.baseAddress, width: 1, height: 1, bitsPerComponent: 8,
                bytesPerRow: 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)!
            context.draw(pixel, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        }
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        XCTAssertEqual(CGFloat(rgba[0]) / 255, r, accuracy: 0.05, file: file, line: line)
        XCTAssertEqual(CGFloat(rgba[1]) / 255, g, accuracy: 0.05, file: file, line: line)
        XCTAssertEqual(CGFloat(rgba[2]) / 255, b, accuracy: 0.05, file: file, line: line)
    }
}


extension GalleryPhotoIdentityTests {
    @MainActor func testDeletionConfirmationDismissesBeforeCommittingAndCanRepeatAfterBackground() async throws {
        let controller = PhotoDeletionConfirmation.Controller()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = controller
        window.makeKeyAndVisible()
        defer { controller.stop(); window.isHidden = true }
        var committed: [UUID] = []
        controller.onConfirm = { request in
            XCTAssertNil(controller.presentedViewController, "Photos must start after our alert fully exits")
            committed.append(request.id)
            controller.update(request: nil, isActive: true)
        }
        for round in 0..<3 {
            let request = PendingPhotoDeletion(photos: photos(2))
            controller.update(request: request, isActive: true)
            try await Task.sleep(for: .milliseconds(400))
            XCTAssertTrue(controller.presentedViewController is UIAlertController)
            if round > 0 {
                controller.enterBackground()
                try await Task.sleep(for: .milliseconds(100))
                XCTAssertNil(controller.presentedViewController)
                XCTAssertEqual(controller.request?.id, request.id, "Background must preserve the pending batch")
                controller.update(request: request, isActive: true)
                try await Task.sleep(for: .milliseconds(400))
                XCTAssertTrue(controller.presentedViewController is UIAlertController)
            }
            controller.complete(request, confirmed: true)
            controller.complete(request, confirmed: true)
            try await Task.sleep(for: .milliseconds(450))
            XCTAssertEqual(committed.count, round + 1, "Each batch commits exactly once")
            XCTAssertEqual(committed.last, request.id)
            XCTAssertNil(controller.presentedViewController)
        }
    }

    @MainActor func testConfirmationInterruptedAfterTapWaitsForActiveScene() async throws {
        let controller = PhotoDeletionConfirmation.Controller()
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = controller
        window.makeKeyAndVisible()
        defer { controller.stop(); window.isHidden = true }
        let request = PendingPhotoDeletion(photos: photos(1))
        var count = 0
        controller.onConfirm = { _ in count += 1; controller.update(request: nil, isActive: true) }
        controller.update(request: request, isActive: true)
        try await Task.sleep(for: .milliseconds(400))
        controller.complete(request, confirmed: true)
        controller.enterBackground()
        try await Task.sleep(for: .milliseconds(450))
        XCTAssertEqual(count, 0)
        controller.update(request: request, isActive: true)
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertEqual(count, 1)
        XCTAssertNil(controller.presentedViewController)
    }

    @MainActor func testCancellingConfirmationDoesNotCommitAndStaleBatchCannotCancelNewBatch() async throws {
        let name = "DeletionConfirmation.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let model = GalleryViewModel(store: GalleryStore(defaults: defaults), library: PhotoLibraryService(), imageCache: .shared, startLibraryUpdates: false)
        let old = PendingPhotoDeletion(photos: photos(1)), current = PendingPhotoDeletion(photos: photos(1))
        model.pendingDeletionRequest = current
        model.cancelPendingDeletion(old)
        model.confirmPendingDeletion(old)
        XCTAssertEqual(model.pendingDeletionRequest?.id, current.id)
        XCTAssertFalse(model.isDeletingPhotos)
        model.cancelPendingDeletion(current)
        XCTAssertNil(model.pendingDeletionRequest)
    }
}


extension GalleryPhotoIdentityTests {
    @MainActor func testRootUsesNativeConfirmationAndCancelLeavesListInteractive() async throws {
        let name = "RootConfirmation.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let model = GalleryViewModel(store: GalleryStore(defaults: defaults), library: PhotoLibraryService(), imageCache: .shared, startLibraryUpdates: false)
        let host = UIHostingController(rootView: RootView().environmentObject(model).environment(\.scenePhase, .active))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = host; window.makeKeyAndVisible()
        defer { window.isHidden = true }
        func presenters(_ controller: UIViewController) -> [PhotoDeletionConfirmation.Controller] {
            (controller as? PhotoDeletionConfirmation.Controller).map { [$0] } ?? controller.children.flatMap { presenters($0) }
        }
        for _ in 0..<2 {
            let request = PendingPhotoDeletion(photos: photos(2))
            model.pendingDeletionRequest = request
            try await settle(host)
            try await Task.sleep(for: .milliseconds(400))
            let bridge = descendants(host.view).compactMap { $0.next as? PhotoDeletionConfirmation.Controller }.first
            let presenter = try XCTUnwrap(presenters(host).first ?? bridge)
            let alert = try XCTUnwrap((presenter.presentedViewController ?? host.presentedViewController) as? UIAlertController)
            XCTAssertEqual(alert.title, "本次删除 2 项")
            presenter.complete(request, confirmed: false)
            try await Task.sleep(for: .milliseconds(450))
            XCTAssertNil(model.pendingDeletionRequest)
            XCTAssertNil(presenter.presentedViewController, "No invisible modal may block the list")
            XCTAssertFalse(model.isDeletingPhotos)
        }
    }
}
