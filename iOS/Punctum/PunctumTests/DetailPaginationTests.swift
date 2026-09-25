import XCTest
@testable import Punctum

final class DetailPaginationTests: XCTestCase {
    private func state(_ index: Int, _ visible: Int, loaded: Int = 80,
                       more: Bool = true, blocked: Bool = false) -> DetailPagination {
        DetailPagination(index: index, visibleCount: visible, loadedCount: loaded,
                         photoID: "photo-\(index)", hasMore: more, blocked: blocked)
    }
    func testEntryAtLastLoadedPhotoRequestsNextPage() {
        XCTAssertTrue(state(79, 80).shouldLoad)
        XCTAssertFalse(state(10, 80).shouldLoad)
    }
    func testPendingDeletionsUseVisibleBoundary() {
        for removed in [4, 20, 50, 79] {
            XCTAssertTrue(state(79 - removed, 80 - removed).shouldLoad)
        }
    }
    func testDeletionAtUnchangedIndexRestartsCheck() {
        let before = state(60, 80)
        let after = state(60, 64)
        XCTAssertNotEqual(before, after)
        XCTAssertFalse(before.shouldLoad)
        XCTAssertTrue(after.shouldLoad)
    }
    func testContinuousBrowsingAcrossFourBatchesPreservesSelection() {
        var loaded = 80
        var requests = 0
        for index in 0..<300 {
            let check = state(index, loaded, loaded: loaded, more: loaded < 300)
            if check.shouldLoad {
                loaded = min(loaded + 80, 300)
                requests += 1
                XCTAssertFalse(state(index, loaded, loaded: loaded, more: loaded < 300).shouldLoad)
            }
            XCTAssertLessThan(index, loaded)
        }
        XCTAssertEqual(requests, 3)
        XCTAssertEqual(loaded, 300)
    }
    func testEmptyBatchRefillsUntilLibraryIsExhausted() {
        XCTAssertTrue(state(0, 0).shouldLoad)
        XCTAssertFalse(state(0, 0).shouldClose)
        XCTAssertTrue(state(0, 0, more: false).shouldClose)
        XCTAssertFalse(state(0, 0, more: false).shouldLoad)
    }
    func testGestureCompletionResumesAndExhaustionStopsRequests() {
        let blocked = state(75, 76, blocked: true)
        let settled = state(75, 76)
        XCTAssertFalse(blocked.shouldLoad)
        XCTAssertNotEqual(blocked, settled)
        XCTAssertTrue(settled.shouldLoad)
        XCTAssertFalse(state(79, 80, more: false).shouldLoad)
    }

    func testReturnKeepsPositionForPhotosVisibleAtEntry() {
        let ids = (0..<12).map { "photo-\($0)" }
        let viewport = CGRect(x: 0, y: 0, width: 390, height: 700)
        let frames = [
            0: CGRect(x: 0, y: -180, width: 390, height: 200),
            2: CGRect(x: 0, y: 20, width: 390, height: 300),
            4: CGRect(x: 0, y: 320, width: 390, height: 400),
            6: CGRect(x: 0, y: 720, width: 390, height: 300),
        ]
        let indices = GalleryReturnPosition.visibleIndices(
            rowFrames: frames, viewport: viewport, photoCount: ids.count
        )
        let entryIDs = Set(indices.map { ids[$0] })
        XCTAssertEqual(entryIDs, Set((0..<6).map { "photo-\($0)" }))
        XCTAssertFalse(GalleryReturnPosition.shouldCenter("photo-5", entryVisibleIDs: entryIDs))
        XCTAssertTrue(GalleryReturnPosition.shouldCenter("photo-6", entryVisibleIDs: entryIDs))
    }

    func testReturnTargetSkipsPendingDeletionsAndUsesNextThenPreviousPhoto() {
        let ids = (0..<8).map { "photo-\($0)" }
        XCTAssertEqual(GalleryReturnPosition.targetID(
            ids: ids, viewedID: "photo-6", fallbackIndex: 6, excluding: ["photo-6"]
        ), "photo-7")
        XCTAssertEqual(GalleryReturnPosition.targetID(
            ids: ids, viewedID: "photo-7", fallbackIndex: 7, excluding: ["photo-6", "photo-7"]
        ), "photo-5")
        XCTAssertNil(GalleryReturnPosition.targetID(
            ids: ids, viewedID: "photo-7", fallbackIndex: 7, excluding: Set(ids)
        ))
    }
}
