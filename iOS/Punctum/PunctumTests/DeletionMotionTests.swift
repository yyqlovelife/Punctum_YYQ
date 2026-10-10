import XCTest
import SwiftUI
import UIKit
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


extension DeletionMotionTests {
    @MainActor func testBackgroundDuringConfirmedAnimationCommitsOnceAndUnlocksScrolling() async throws {
        try await checkInterruptedDeletion(released: true, commit: true)
    }
    @MainActor func testBackgroundDuringCancelledAnimationDoesNotDeleteAndUnlocksScrolling() async throws {
        try await checkInterruptedDeletion(released: true, commit: false)
    }
    @MainActor func testBackgroundWhileFingerDownCancelsAndUnlocksScrolling() async throws {
        try await checkInterruptedDeletion(released: false, commit: false)
    }
    @MainActor private func checkInterruptedDeletion(released: Bool, commit: Bool) async throws {
        let controller = NativeDeletionPager<EmptyView>.Controller(content: AnyView(Color.orange))
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
        window.rootViewController = controller
        window.makeKeyAndVisible()
        defer { controller.tearDown(); window.isHidden = true }
        try await Task.sleep(for: .milliseconds(100))
        controller.view.layoutIfNeeded()
        let scroll = UIScrollView(frame: controller.view.bounds)
        controller.host.view.addSubview(scroll)
        var completions: [Bool] = [], settled = 0
        controller.onBegin = { true }
        controller.onComplete = { completions.append($0) }
        controller.onSettled = { settled += 1 }
        XCTAssertTrue(controller.beginSnapshot(UIView(frame: controller.view.bounds)))
        XCTAssertFalse(scroll.panGestureRecognizer.isEnabled)
        XCTAssertEqual(controller.host.view.layer.opacity, 0)
        if released { controller.settle(commit: commit) }
        NotificationCenter.default.post(name: UIApplication.willResignActiveNotification, object: nil)
        // The SwiftUI content update is deliberately omitted: recovery must not
        // rely on an animation completion or an update delivered while suspended.
        try await Task.sleep(for: .milliseconds(450))
        XCTAssertEqual(completions, [released && commit])
        XCTAssertEqual(settled, 1)
        XCTAssertTrue(scroll.panGestureRecognizer.isEnabled)
        XCTAssertEqual(controller.host.view.layer.opacity, 1)
        controller.suspend()
        XCTAssertEqual(completions.count, 1)
        XCTAssertTrue(controller.beginSnapshot(UIView(frame: controller.view.bounds)), "The next gesture must be usable")
        controller.suspend()
    }
}
