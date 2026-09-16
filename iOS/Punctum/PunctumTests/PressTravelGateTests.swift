import XCTest
@testable import Punctum

final class PressTravelGateTests: XCTestCase {
    func testSmallFingerJitterStillTaps() {
        var gate = PressTravelGate()
        gate.begin(at: .zero)
        gate.move(to: CGPoint(x: 3, y: 4))
        XCTAssertTrue(gate.isTap)
    }
    func testDragReturningToOriginCannotTap() {
        var gate = PressTravelGate()
        gate.begin(at: .zero)
        gate.move(to: CGPoint(x: 40, y: 0))
        gate.move(to: .zero)
        XCTAssertFalse(gate.isTap)
    }
    func testCancellationAndNextGesture() {
        var gate = PressTravelGate()
        gate.begin(at: .zero)
        gate.cancel()
        XCTAssertFalse(gate.isTap)
        gate.begin(at: CGPoint(x: 100, y: 100))
        XCTAssertTrue(gate.isTap)
    }
}
