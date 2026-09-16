import XCTest
@testable import Punctum

final class DetailPhotoLayoutTests: XCTestCase {
    func testPortraitStartsAtScreenTopWithoutNegativeOffset() {
        let layout = DetailPhotoLayout(aspect: 0.75, screenSize: CGSize(width: 393, height: 852), safeAreaTop: 59)
        XCTAssertEqual(layout.topPadding, 0)
        XCTAssertEqual(layout.imageSize.width, 393, accuracy: 0.01)
        XCTAssertEqual(layout.imageSize.height, 524, accuracy: 0.01)
    }

    func testTallPortraitFitsEntirelyInViewportWithoutCropping() {
        let layout = DetailPhotoLayout(aspect: 0.3, screenSize: CGSize(width: 393, height: 852), safeAreaTop: 59)
        XCTAssertLessThanOrEqual(layout.topPadding + layout.imageSize.height, 852 - 24)
        XCTAssertEqual(layout.imageSize.width / layout.imageSize.height, 0.3, accuracy: 0.001)
        XCTAssertLessThan(layout.imageSize.width, 393)
    }

    func testLandscapeRetainsItsExistingVisualPosition() {
        let layout = DetailPhotoLayout(aspect: 1.5, screenSize: CGSize(width: 393, height: 852), safeAreaTop: 59)
        XCTAssertEqual(layout.imageSize, CGSize(width: 393, height: 262))
        XCTAssertEqual(layout.topPadding, 255)
    }
}
