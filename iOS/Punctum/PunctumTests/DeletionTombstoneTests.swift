import XCTest
@testable import Punctum

final class DeletionTombstoneTests: XCTestCase {
    func testMarkedPhotosRemainHiddenUntilExpiry() {
        let now = Date(timeIntervalSince1970: 1_000)
        var tombstones = PhotoDeletionTombstones()
        tombstones.mark(["photo-a", "photo-b"], expiresAt: now.addingTimeInterval(120))

        XCTAssertEqual(tombstones.activeIDs(at: now), ["photo-a", "photo-b"])
        XCTAssertEqual(tombstones.activeIDs(at: now.addingTimeInterval(119)), ["photo-a", "photo-b"])
        XCTAssertTrue(tombstones.activeIDs(at: now.addingTimeInterval(120)).isEmpty)
    }

    func testCancellationClearsOnlyRequestedPhoto() {
        let now = Date(timeIntervalSince1970: 2_000)
        var tombstones = PhotoDeletionTombstones()
        tombstones.mark(["photo-a", "photo-b"], expiresAt: now.addingTimeInterval(120))
        tombstones.clear(["photo-a"])

        XCTAssertEqual(tombstones.activeIDs(at: now), ["photo-b"])
    }

    func testPruneRemovesExpiredEntries() {
        let now = Date(timeIntervalSince1970: 3_000)
        var tombstones = PhotoDeletionTombstones()
        tombstones.mark(["expired"], expiresAt: now)
        tombstones.mark(["active"], expiresAt: now.addingTimeInterval(1))
        tombstones.prune(at: now)

        XCTAssertEqual(tombstones.activeIDs(at: now), ["active"])
    }
}
