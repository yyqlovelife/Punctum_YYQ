import XCTest
import UIKit
@testable import Punctum

final class PhotoZoomTests: XCTestCase {
    private let frame = CGRect(x: 0, y: 150, width: 400, height: 300)

    func testSuccessivePinchesRetainScaleAndRespectFiveTimesLimit() {
        var zoom = PhotoZoomState()
        zoom.pinch(by: 2, at: CGPoint(x: 200, y: 300), in: frame)
        zoom.pinch(by: 2, at: CGPoint(x: 200, y: 300), in: frame)
        XCTAssertEqual(zoom.scale, 4)
        zoom.pinch(by: 2, at: CGPoint(x: 200, y: 300), in: frame)
        XCTAssertEqual(zoom.scale, 5)
        zoom.pinch(by: 0.8, at: CGPoint(x: 200, y: 300), in: frame)
        XCTAssertEqual(zoom.scale, 4) // Reverse immediately at the upper limit.
    }

    func testPinchKeepsPixelUnderNewFocalPointAfterPanning() {
        var zoom = PhotoZoomState()
        zoom.pinch(by: 2, at: CGPoint(x: 200, y: 300), in: frame)
        zoom.pan(by: CGPoint(x: 30, y: -20), in: frame)
        let focal = CGPoint(x: 230, y: 280)
        let pixelX = (focal.x - frame.midX - zoom.offset.width) / zoom.scale
        let pixelY = (focal.y - frame.midY - zoom.offset.height) / zoom.scale
        zoom.pinch(by: 1.5, at: focal, in: frame)
        XCTAssertEqual(frame.midX + pixelX * zoom.scale + zoom.offset.width, focal.x, accuracy: 0.001)
        XCTAssertEqual(frame.midY + pixelY * zoom.scale + zoom.offset.height, focal.y, accuracy: 0.001)
    }

    func testFourDirectionPanningAndBoundsKeepImageReachable() {
        var zoom = PhotoZoomState()
        zoom.pinch(by: 3, at: CGPoint(x: 200, y: 300), in: frame)
        for delta in [CGPoint(x: 40, y: 60), CGPoint(x: -80, y: -120)] {
            zoom.pan(by: delta, in: frame)
        }
        XCTAssertEqual(zoom.offset, CGSize(width: -40, height: -60))
        zoom.pan(by: CGPoint(x: 10000, y: -10000), in: frame)
        XCTAssertEqual(zoom.offset, CGSize(width: 400, height: -300))
        XCTAssertTrue(zoom.transformedRect(frame).contains(frame))
    }

    func testPinchingToOriginalRestoresSizeAndPosition() {
        var zoom = PhotoZoomState()
        zoom.pinch(by: 5, at: CGPoint(x: 110, y: 220), in: frame)
        zoom.pan(by: CGPoint(x: 60, y: 90), in: frame)
        zoom.pinch(by: 0.1, at: CGPoint(x: 340, y: 400), in: frame)
        XCTAssertEqual(zoom, PhotoZoomState())
        zoom.pan(by: CGPoint(x: 80, y: 80), in: frame)
        XCTAssertEqual(zoom, PhotoZoomState())
    }

    @MainActor func testReleaseRetainsZoomAndLocksAncestorUntilDoubleTapReset() {
        let ancestor = UIView()
        let ancestorPan = UIPanGestureRecognizer()
        ancestor.addGestureRecognizer(ancestorPan)
        let catcher = LiveCatcherView(frame: CGRect(x: 0, y: 0, width: 400, height: 800))
        ancestor.addSubview(catcher)
        let coordinator = LiveHoldCatcher.Coordinator()
        coordinator.imageRect = frame
        coordinator.pinchEnabled = true
        coordinator.attach(to: catcher)
        let pinch = TestPinch()
        pinch.testPoint = CGPoint(x: 200, y: 300)
        pinch.testState = .began
        pinch.scale = 2
        coordinator.handlePinch(pinch)
        pinch.testState = .ended
        coordinator.handlePinch(pinch)
        XCTAssertEqual(coordinator.zoom.scale, 2)
        XCTAssertFalse(ancestorPan.isEnabled)
        XCTAssertFalse(coordinator.tapRecognizer!.isEnabled)
        XCTAssertFalse(coordinator.pressRecognizer!.isEnabled)
        // The enlarged part beyond the original photo frame is also interactive.
        XCTAssertTrue(catcher.point(inside: CGPoint(x: 200, y: 30), with: nil))
        var resetCalled = false
        coordinator.onZoomReset = { resetCalled = true }
        coordinator.handleDoubleTap(TestDoubleTap())
        XCTAssertTrue(resetCalled)
        XCTAssertEqual(coordinator.zoom, PhotoZoomState())
        XCTAssertTrue(ancestorPan.isEnabled)
        XCTAssertTrue(coordinator.tapRecognizer!.isEnabled)
    }

    @MainActor func testPinchAlsoTracksTwoFingerMovementWithoutPanRecognizer() {
        let coordinator = LiveHoldCatcher.Coordinator()
        coordinator.imageRect = frame
        let pinch = TestPinch()
        pinch.scale = 3
        coordinator.handlePinch(pinch)
        pinch.testState = .changed
        pinch.testPoint = CGPoint(x: 245, y: 270)
        coordinator.handlePinch(pinch)
        XCTAssertEqual(coordinator.zoom.offset, CGSize(width: 45, height: -30))
        pinch.testState = .ended
        coordinator.handlePinch(pinch)
        XCTAssertEqual(coordinator.zoom.scale, 3)
        XCTAssertEqual(coordinator.zoom.offset, CGSize(width: 45, height: -30))
    }

    @MainActor func testTeardownReleasesLocksWithoutPublishingStaleState() {
        let ancestor = UIView()
        let pan = UIPanGestureRecognizer()
        ancestor.addGestureRecognizer(pan)
        let view = LiveCatcherView()
        ancestor.addSubview(view)
        let coordinator = LiveHoldCatcher.Coordinator()
        coordinator.pinchEnabled = true
        coordinator.imageRect = frame
        coordinator.attach(to: view)
        let pinch = TestPinch()
        pinch.scale = 3
        coordinator.handlePinch(pinch)
        var notified = false
        coordinator.onZoomChange = { _, _ in notified = true }
        coordinator.clearZoom(notify: false)
        XCTAssertFalse(notified)
        XCTAssertTrue(pan.isEnabled)
        XCTAssertFalse(coordinator.zoom.isZoomed)
    }
}

private final class TestPinch: UIPinchGestureRecognizer {
    var testState: UIGestureRecognizer.State = .began
    var testPoint = CGPoint(x: 200, y: 300)
    override var state: UIGestureRecognizer.State { get { testState } set { testState = newValue } }
    override var numberOfTouches: Int { 2 }
    override func location(in view: UIView?) -> CGPoint { testPoint }
}

private final class TestDoubleTap: UITapGestureRecognizer {
    override var state: UIGestureRecognizer.State { get { .ended } set {} }
}
