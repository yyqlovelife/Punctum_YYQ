import XCTest
@testable import Punctum

final class CaptureDateIndexTests: XCTestCase {
    func testEXIFTimezoneRepresentsSameInstant() {
        let local = CaptureDateIndex.parse("2026:09:15 16:30:00", offset: "+08:00")
        let utc = CaptureDateIndex.parse("2026:09:15 08:30:00", offset: "+00:00")
        XCTAssertNotNil(local)
        XCTAssertEqual(local, utc)
    }
    func testInvalidEXIFFallsBackRatherThanInventingADate() {
        XCTAssertNil(CaptureDateIndex.parse("0000:00:00 00:00:00", offset: nil))
        XCTAssertNil(CaptureDateIndex.parse("not a date", offset: "+08:00"))
    }
    func testDatesWithoutAnOffsetAreSupported() {
        XCTAssertNotNil(CaptureDateIndex.parse("2026:09:15 16:30:00", offset: nil))
    }
}
