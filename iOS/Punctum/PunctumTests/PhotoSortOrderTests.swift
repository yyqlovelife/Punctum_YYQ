import XCTest
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
