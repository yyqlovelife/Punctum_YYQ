import XCTest
@testable import Punctum

final class DeletionMotionTests: XCTestCase {
    func testOverdragHasResistanceWithoutJumpAtBoundary() {
        XCTAssertEqual(DeletionMotion.dragProgress(distance: -10), 0)
        XCTAssertEqual(DeletionMotion.dragProgress(distance: 140), 1)
        let over = DeletionMotion.dragProgress(distance: 150)
        XCTAssertGreaterThan(over, 1)
        XCTAssertLessThan(over, 150.0 / 140)
        XCTAssertEqual(DeletionMotion.dragProgress(distance: 2000), 1.12)
    }

    func testReleaseStartsAtFingerPoseAndEndsAtTrashWithoutReversal() {
        for progress: CGFloat in [0.72, 0.9, 1, 1.12] {
            let start = DeletionMotion.dragPose(progress: progress)
            let first = DeletionMotion.commitPose(start: start, targetY: -340, progress: 0)
            XCTAssertEqual(first.y, start.y)
            XCTAssertEqual(first.scale, start.scale)
            XCTAssertEqual(first.radius, start.radius)
            var previous = first
            for step in 1...100 {
                let pose = DeletionMotion.commitPose(start: start, targetY: -340, progress: CGFloat(step) / 100)
                XCTAssertLessThanOrEqual(pose.y, previous.y)
                XCTAssertLessThanOrEqual(pose.scale, previous.scale)
                XCTAssertLessThanOrEqual(pose.alpha, previous.alpha)
                XCTAssertGreaterThan(pose.scale, 0)
                previous = pose
            }
            XCTAssertEqual(previous.y, -340, accuracy: 0.0001)
            XCTAssertEqual(previous.scale, 0.035, accuracy: 0.0001)
            XCTAssertEqual(previous.alpha, 0, accuracy: 0.0001)
        }
    }
}
