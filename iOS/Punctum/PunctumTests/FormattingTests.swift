import XCTest
@testable import Punctum

final class FormattingTests: XCTestCase {
    func testCompactSameYearSpan() {
        XCTAssertEqual(PunctumFormatting.compactSpan("2026年1-8月"), "2026.01-2026.08")
    }

    func testReversalFilmSpanKeepsBothYears() {
        XCTAssertEqual(PunctumFormatting.reversalFilmSpan("2026年1-8月"), "2026.01 — 2026.08")
    }

    func testExposureFormatting() {
        XCTAssertEqual(PunctumFormatting.exposure(seconds: 1.0 / 500.0), "1/500 s")
        XCTAssertEqual(PunctumFormatting.exposure(seconds: 2), "2 s")
    }

    func testFileSizeFormatting() {
        XCTAssertEqual(PunctumFormatting.fileSize(5_662_310), "5.4 MB")
    }
}
