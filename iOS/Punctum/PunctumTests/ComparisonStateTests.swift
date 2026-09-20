import XCTest
@testable import Punctum

final class ComparisonStateTests: XCTestCase {
    private func state() -> ComparisonState {
        var state = ComparisonState()
        state.updateFrame(index: 0, frame: CGRect(x: 0, y: 50, width: 300, height: 200))
        state.updateFrame(index: 1, frame: CGRect(x: 90, y: 0, width: 120, height: 300))
        return state
    }
    func testLayoutsAndSquareFallback() {
        let portrait = CGSize(width: 600, height: 900), landscape = CGSize(width: 900, height: 600)
        XCTAssertEqual(ComparisonState.layout(first: portrait, second: portrait), .columns)
        XCTAssertEqual(ComparisonState.layout(first: landscape, second: landscape), .rows)
        XCTAssertEqual(ComparisonState.layout(first: portrait, second: landscape), .rows)
        XCTAssertEqual(ComparisonState.layout(first: landscape, second: portrait), .rows)
        XCTAssertEqual(ComparisonState.layout(first: CGSize(width: 600, height: 600), second: portrait), .rows)
    }
    func testIndependentZoomPanAndResetLeaveOtherPhotoUntouched() {
        var value = state()
        value.pinch(index: 0, factor: 3, point: CGPoint(x: 150, y: 150), translation: .zero)
        value.pan(index: 0, delta: CGPoint(x: 40, y: 30))
        XCTAssertEqual(value.poses[1], PhotoZoomState())
        value.tap(index: 0, point: .zero, double: true)
        XCTAssertEqual(value.poses[0], PhotoZoomState())
    }
    func testLinkAdoptsLastManipulatedPoseAndUnlinkPreservesBoth() {
        var value = state()
        value.pinch(index: 1, factor: 2, point: CGPoint(x: 150, y: 150), translation: .zero)
        value.pan(index: 1, delta: CGPoint(x: 20, y: -30))
        value.toggleLink()
        XCTAssertEqual(value.poses[0].scale, 2)
        XCTAssertEqual(value.poses[0].offset.width, 50, accuracy: 0.001)
        XCTAssertEqual(value.poses[0].offset.height, -20, accuracy: 0.001)
        value.toggleLink()
        let other = value.poses[0]
        value.pan(index: 1, delta: CGPoint(x: -10, y: 0))
        XCTAssertEqual(value.poses[0], other)
    }
    func testLinkedOperationsWorkFromEitherCellAndClampToFive() {
        var value = state()
        value.toggleLink()
        value.pinch(index: 0, factor: 9, point: CGPoint(x: 150, y: 150), translation: .zero)
        XCTAssertEqual(value.poses.map(\.scale), [5, 5])
        value.pan(index: 1, delta: CGPoint(x: -20, y: 30))
        XCTAssertEqual(value.poses[0].offset.width, -50, accuracy: 0.001)
        XCTAssertEqual(value.poses[0].offset.height, 20, accuracy: 0.001)
        value.pinch(index: 1, factor: 0.1, point: CGPoint(x: 150, y: 150), translation: .zero)
        XCTAssertEqual(value.poses, [PhotoZoomState(), PhotoZoomState()])
        value.tap(index: 0, point: CGPoint(x: 150, y: 150), double: false)
        XCTAssertEqual(value.poses.map(\.scale), [2, 2])
        value.tap(index: 1, point: .zero, double: true)
        XCTAssertEqual(value.poses, [PhotoZoomState(), PhotoZoomState()])
    }
    func testLateImageLayoutSynchronizesAndResizingKeepsRelativePose() {
        var value = ComparisonState()
        value.updateFrame(index: 0, frame: CGRect(x: 0, y: 0, width: 300, height: 200))
        value.pinch(index: 0, factor: 3, point: CGPoint(x: 150, y: 100), translation: .zero)
        value.pan(index: 0, delta: CGPoint(x: 30, y: 20))
        value.toggleLink()
        value.updateFrame(index: 1, frame: CGRect(x: 0, y: 0, width: 150, height: 400))
        XCTAssertEqual(value.poses[1].scale, 3)
        XCTAssertEqual(value.poses[1].offset, CGSize(width: 15, height: 40))
        value.updateFrame(index: 0, frame: CGRect(x: 0, y: 0, width: 600, height: 400))
        XCTAssertEqual(value.poses[0].offset, CGSize(width: 60, height: 40))
        XCTAssertEqual(value.poses[1].offset, CGSize(width: 15, height: 40))
    }
    func testInterruptedReturnContinuesFromVisiblePose() {
        var value = state()
        value.toggleLink()
        value.begin(index: 1, scale: 1.6, offset: CGSize(width: 12, height: -15))
        value.pinch(index: 1, factor: 1.25, point: CGPoint(x: 150, y: 150), translation: .zero)
        XCTAssertEqual(value.poses.map(\.scale), [2, 2])
        XCTAssertEqual(value.poses[1].offset.width, 15, accuracy: 0.001)
        XCTAssertEqual(value.poses[0].offset.width, 37.5, accuracy: 0.001)
    }
    func testDeletionReturnIdentity() {
        let ids = ["a", "b", "c"]
        XCTAssertEqual(ComparisonState.returningID(ids: ids, originalID: "b", deletedID: "outside"), "b")
        XCTAssertEqual(ComparisonState.returningID(ids: ids, originalID: "b", deletedID: "a"), "b")
        XCTAssertEqual(ComparisonState.returningID(ids: ids, originalID: "b", deletedID: "b"), "c")
        XCTAssertEqual(ComparisonState.returningID(ids: ids, originalID: "c", deletedID: "c"), "b")
        XCTAssertNil(ComparisonState.returningID(ids: ["a"], originalID: "a", deletedID: "a"))
    }
}
