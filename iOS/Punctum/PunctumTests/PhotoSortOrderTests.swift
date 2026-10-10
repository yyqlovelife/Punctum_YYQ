import XCTest
import Photos
import UIKit
import SwiftUI
@testable import Punctum

final class PhotoSortOrderTests: XCTestCase {
    private struct Item { let id: String; let captured: Date; let modified: Date? }
    private func item(_ id: String, _ shot: Double, _ edit: Double?) -> Item {
        Item(id: id, captured: Date(timeIntervalSince1970: shot), modified: edit.map { Date(timeIntervalSince1970: $0) })
    }
    private func sorted(_ order: PhotoSortOrder, _ items: [Item]) -> [String] {
        items.sorted { order.precedes(capture: $0.captured, modified: $0.modified, id: $0.id,
                                      capture: $1.captured, modified: $1.modified, id: $1.id) }.map(\.id)
    }
    func testEditedOlderPhotoLeadsOnlyInEditedMode() {
        let items = [item("old", 100, 900), item("new", 200, 800)]
        XCTAssertEqual(sorted(.capture, items), ["new", "old"])
        XCTAssertEqual(sorted(.modified, items), ["old", "new"])
    }
    func testTiesAndMissingModifiedTimeAreStable() {
        let items = [item("b", 100, 500), item("a", 100, 500), item("c", 200, 500), item("missing", 300, nil)]
        XCTAssertEqual(sorted(.modified, items), ["c", "a", "b", "missing"])
        XCTAssertEqual(sorted(.modified, items), sorted(.modified, items.reversed()))
    }
    func testLegacyGalleryDecodesWithoutLosingAlbums() throws {
        let data = Data("[{\"id\":\"album\",\"displayName\":\"画廊\",\"styleID\":\"original\"}]".utf8)
        let gallery = try JSONDecoder().decode([PunctumGallery].self, from: data)[0]
        XCTAssertEqual(gallery.sortOrder, .capture)
    }
    func testIndependentModesAndHintSurviveStoreRecreation() throws {
        let name = "PhotoSortOrderTests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let store = GalleryStore(defaults: defaults)
        store.saveGalleries([PunctumGallery(id: "a", displayName: "A", sortOrder: .modified), PunctumGallery(id: "b", displayName: "B")])
        XCTAssertTrue(store.consumePhotoSortHint())
        let restored = GalleryStore(defaults: defaults)
        XCTAssertEqual(restored.loadGalleries().map(\.sortOrder), [.modified, .capture])
        XCTAssertFalse(restored.consumePhotoSortHint())
    }
}

extension PhotoSortOrderTests {
    func testThreeModesCycleAndSyncPersistence() throws {
        XCTAssertEqual(PhotoSortOrder.capture.next, .modified)
        XCTAssertEqual(PhotoSortOrder.modified.next, .sync)
        XCTAssertEqual(PhotoSortOrder.sync.next, .capture)
        let gallery = PunctumGallery(id: "custom", displayName: "自定义图集", sortOrder: .sync)
        XCTAssertEqual(try JSONDecoder().decode(PunctumGallery.self, from: JSONEncoder().encode(gallery)), gallery)
    }

    func testNewHintIsShownOnceForExistingTwoModeUsers() throws {
        let name = "PhotoSortHintUpgrade.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        defaults.set(true, forKey: "punctum.photoSortHintSeen")
        XCTAssertTrue(GalleryStore(defaults: defaults).consumePhotoSortHint())
        XCTAssertFalse(GalleryStore(defaults: defaults).consumePhotoSortHint())
    }
}

// Exercises the real PhotoKit album order, including a missed background notification.
// All fixtures live in the simulator's library, never in a connected user's library.
extension PhotoSortOrderTests {
    @MainActor
    func testCustomAlbumOrderAndForegroundRefreshWithoutObserver() async throws {
        #if targetEnvironment(simulator)
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .authorized else {
            throw XCTSkip("Grant full Photos access to the simulator test host to run PhotoKit integration (status=\(PHPhotoLibrary.authorizationStatus(for: .readWrite).rawValue), bundle=\(Bundle.main.bundleIdentifier ?? "nil"))")
        }
        let name = "PhotoSyncIntegration.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        var assetIDs: [String] = []
        var albumID = ""
        let image = UIGraphicsImageRenderer(size: CGSize(width: 60, height: 40)).image { context in
            UIColor.orange.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 60, height: 40))
        }
        try await PHPhotoLibrary.shared().performChanges {
            let placeholders = (0..<3).map { index -> PHObjectPlaceholder in
                let request = PHAssetChangeRequest.creationRequestForAsset(from: image)
                request.creationDate = Date(timeIntervalSince1970: Double(index + 1) * 86400)
                return request.placeholderForCreatedAsset!
            }
            assetIDs = placeholders.map(\.localIdentifier)
            let album = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
            albumID = album.placeholderForCreatedAssetCollection.localIdentifier
            album.addAssets([placeholders[1], placeholders[0], placeholders[2]] as NSArray)
        }
        let gallery = PunctumGallery(id: albumID, displayName: name, sortOrder: .sync)
        let initialIDs = [assetIDs[1], assetIDs[0], assetIDs[2]]
        let library = PhotoLibraryService()
        // Warm the time-order cache first so the test detects an accidental cache override.
        await library.prepareCaptureOrder(for: gallery)
        await library.pinCaptureOrderAsync(for: gallery)
        XCTAssertEqual(library.fetchPhotos(in: gallery).map(\.id), initialIDs)
        let page1 = library.fetchPhotoPage(in: gallery, startIndex: 0, limit: 2)
        let page2 = library.fetchPhotoPage(in: gallery, startIndex: page1.nextIndex, limit: 2)
        XCTAssertEqual((page1.photos + page2.photos).map(\.id), initialIDs)

        let store = GalleryStore(defaults: defaults)
        store.saveGalleries([gallery])
        let model = GalleryViewModel(store: store, library: library, imageCache: .shared)
        PHPhotoLibrary.shared().unregisterChangeObserver(model)
        model.selectGallery(gallery.id)
        for _ in 0..<100 {
            if model.photos.map(\.id) == initialIDs { break }
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertEqual(model.photos.map(\.id), initialIDs)
        try await Task.sleep(for: .milliseconds(600))
        let collection = try XCTUnwrap(PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [albumID], options: nil).firstObject)
        let original = PHAsset.fetchAssets(in: collection, options: nil)
        try await PHPhotoLibrary.shared().performChanges {
            PHAssetCollectionChangeRequest(for: collection, assets: original)?.moveAssets(at: IndexSet(integer: 2), to: 0)
        }
        let reorderedIDs = [assetIDs[2], assetIDs[1], assetIDs[0]]
        XCTAssertEqual(model.photos.map(\.id), initialIDs, "No observer: visible snapshot stays unchanged until foreground")
        model.appDidBecomeActive()
        model.appDidBecomeActive()
        for _ in 0..<100 {
            if model.photos.map(\.id) == reorderedIDs { break }
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertEqual(model.photos.map(\.id), reorderedIDs, "Foreground must reload this open gallery without navigating away")
        XCTAssertEqual(model.currentGalleryID, albumID)

        let host = UIHostingController(rootView: RootView().environmentObject(model))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        try await Task.sleep(for: .milliseconds(100))
        model.transientMessage = GalleryViewModel.photoSortHint
        try await Task.sleep(for: .milliseconds(150))
        host.view.layoutIfNeeded()
        let screenshot = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        let attachment = XCTAttachment(image: screenshot)
        attachment.name = "Three-mode sorting hint"
        attachment.lifetime = .keepAlways
        add(attachment)
        try await Task.sleep(for: .milliseconds(3050))
        XCTAssertEqual(model.transientMessage, GalleryViewModel.photoSortHint, "Hint stays visible past the old two-second duration")
        try await Task.sleep(for: .milliseconds(1100))
        XCTAssertNil(model.transientMessage, "Hint expires after four seconds")

        model.transientMessage = GalleryViewModel.photoSortHint
        model.openDetail(at: 0, metadata: PhotoMetadata())
        XCTAssertNil(model.transientMessage, "Opening detail must immediately dismiss the hint")
        model.detailIndex = nil
        model.transientMessage = GalleryViewModel.photoSortHint
        model.openSwitcher()
        XCTAssertNil(model.transientMessage, "Leaving the gallery must immediately dismiss the hint")
        #else
        throw XCTSkip("Synthetic album mutations are restricted to the simulator")
        #endif
    }
}

extension PhotoSortOrderTests {
    @MainActor
    func testFourSecondHintLayoutAndNavigationDismissal() async throws {
        let name = "PhotoSortToast.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        let store = GalleryStore(defaults: defaults)
        store.saveGalleries([PunctumGallery(id: "toast-fixture", displayName: "排序测试")])
        let model = GalleryViewModel(store: store, library: PhotoLibraryService(), imageCache: .shared, startLibraryUpdates: false)
        model.currentGalleryID = "toast-fixture"
        model.galleryReadyID = "toast-fixture"
        let host = UIHostingController(rootView: RootView().environmentObject(model))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }
        try await Task.sleep(for: .milliseconds(100))
        model.transientMessage = GalleryViewModel.photoSortHint
        try await Task.sleep(for: .milliseconds(150))
        host.view.layoutIfNeeded()
        let screenshot = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
            host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        let attachment = XCTAttachment(image: screenshot)
        attachment.name = "Three-mode sorting hint"
        attachment.lifetime = .keepAlways
        add(attachment)
        try await Task.sleep(for: .milliseconds(3050))
        XCTAssertEqual(model.transientMessage, GalleryViewModel.photoSortHint)
        try await Task.sleep(for: .milliseconds(1100))
        XCTAssertNil(model.transientMessage)
        model.transientMessage = GalleryViewModel.photoSortHint
        model.detailIndex = 0
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertNil(model.transientMessage)
        model.detailIndex = nil
        model.transientMessage = GalleryViewModel.photoSortHint
        model.openSwitcher()
        XCTAssertNil(model.transientMessage)
    }
}


extension PhotoSortOrderTests {
    @MainActor func testCaptureAndEditedForegroundRefreshRemoveStaleAlbumEntriesWithoutObserver() async throws {
        #if targetEnvironment(simulator)
        if PHPhotoLibrary.authorizationStatus(for: .readWrite) == .notDetermined {
            _ = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        }
        guard PHPhotoLibrary.authorizationStatus(for: .readWrite) == .authorized else {
            throw XCTSkip("PhotoKit foreground integration requires full simulator access")
        }
        for order: PhotoSortOrder in [.capture, .modified] {
            let name = "ForegroundRefresh.\(UUID().uuidString)"
            let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
            defer { defaults.removePersistentDomain(forName: name) }
            let image = UIGraphicsImageRenderer(size: CGSize(width: 30, height: 20)).image { context in
                UIColor.blue.setFill(); context.fill(CGRect(x: 0, y: 0, width: 30, height: 20))
            }
            var ids: [String] = [], albumID = ""
            try await PHPhotoLibrary.shared().performChanges {
                let placeholders = (0..<3).map { _ in
                    PHAssetChangeRequest.creationRequestForAsset(from: image).placeholderForCreatedAsset!
                }
                ids = placeholders.map(\.localIdentifier)
                let album = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
                albumID = album.placeholderForCreatedAssetCollection.localIdentifier
                album.addAssets(placeholders as NSArray)
            }
            let gallery = PunctumGallery(id: albumID, displayName: name, sortOrder: order)
            let library = PhotoLibraryService()
            await library.prepareCaptureOrder(for: gallery)
            let store = GalleryStore(defaults: defaults); store.saveGalleries([gallery])
            let model = GalleryViewModel(store: store, library: library, imageCache: .shared, startLibraryUpdates: false)
            model.selectGallery(albumID)
            for _ in 0..<100 {
                if model.photos.count == 3 { break }
                try await Task.sleep(for: .milliseconds(20))
            }
            XCTAssertEqual(Set(model.photos.map(\.id)), Set(ids))
            let collection = try XCTUnwrap(PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [albumID], options: nil).firstObject)
            let removed = PHAsset.fetchAssets(withLocalIdentifiers: [ids[1]], options: nil)
            model.appDidEnterBackground()
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetCollectionChangeRequest(for: collection)?.removeAssets(removed)
            }
            XCTAssertEqual(model.photos.count, 3, "Suspended visit remains coherent until resume")
            model.appDidBecomeActive()
            model.appDidBecomeActive()
            for _ in 0..<100 {
                if model.photos.count == 2 { break }
                try await Task.sleep(for: .milliseconds(20))
            }
            XCTAssertEqual(Set(model.photos.map(\.id)), Set([ids[0], ids[2]]))
            XCTAssertFalse(model.isLoading)
            let target = try XCTUnwrap(model.photos.first)
            model.openDetail(photoID: target.id, in: albumID, metadata: PhotoMetadata(), entryVisibleIDs: [])
            XCTAssertEqual(model.photos[try XCTUnwrap(model.detailIndex)].id, target.id)
        }
        #else
        throw XCTSkip("PhotoKit fixture mutation is simulator-only")
        #endif
    }
}
