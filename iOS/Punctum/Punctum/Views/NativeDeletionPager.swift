import SwiftUI
import UIKit

/// A single inert snapshot owns the gesture. Photo decoding, EXIF and the pager
/// stay outside the per-frame path. The legacy SwiftUI gesture remains available.
struct NativeDeletionPager<Content: View>: UIViewControllerRepresentable {
    var enabled: Bool
    var excludedTop: CGFloat
    var targetY: CGFloat
    var reduceMotion: Bool
    var onBegin: () -> Bool
    var onArm: (Bool) -> Void
    var onRelease: () -> Void
    var onComplete: (Bool) -> Void
    var onSettled: () -> Void
    @ViewBuilder var content: () -> Content

    func makeUIViewController(context: Context) -> Controller {
        Controller(content: AnyView(content()))
    }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.enabled = enabled
        controller.excludedTop = excludedTop
        controller.targetY = targetY
        controller.reduceMotion = reduceMotion
        controller.onBegin = onBegin
        controller.onArm = onArm
        controller.onRelease = onRelease
        controller.onComplete = onComplete
        controller.onSettled = onSettled
        controller.host.rootView = AnyView(content())
        controller.restoreIfReady()
    }

    static func dismantleUIViewController(_ controller: Controller, coordinator: ()) {
        controller.tearDown()
    }

    final class Controller: UIViewController, UIGestureRecognizerDelegate {
        let host: UIHostingController<AnyView>
        var enabled = true
        var excludedTop: CGFloat = 0
        var targetY: CGFloat = 82
        var reduceMotion = false
        var onBegin: () -> Bool = { false }
        var onArm: (Bool) -> Void = { _ in }
        var onRelease: () -> Void = {}
        var onComplete: (Bool) -> Void = { _ in }
        var onSettled: () -> Void = {}
        private var card: UIView?
        private var suspendedPans: [UIPanGestureRecognizer] = []
        private let shade = UIView()
        private var progress: CGFloat = 0
        private var armed = false
        private var finishing = false
        private var releasedCommit = false
        private var waitingForContent = false
        private var restoring = false
        private var animationGeneration = 0
        private lazy var pan = UIPanGestureRecognizer(target: self, action: #selector(drag(_:)))

        init(content: AnyView) {
            host = UIHostingController(rootView: content)
            super.init(nibName: nil, bundle: nil)
        }
        required init?(coder: NSCoder) { nil }
        deinit { NotificationCenter.default.removeObserver(self) }

        override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .clear
            host.safeAreaRegions = []
            addChild(host)
            host.view.backgroundColor = .clear
            host.view.frame = view.bounds
            host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(host.view)
            host.didMove(toParent: self)
            shade.backgroundColor = .black
            shade.isUserInteractionEnabled = false
            pan.maximumNumberOfTouches = 1
            pan.delegate = self
            view.addGestureRecognizer(pan)
            NotificationCenter.default.addObserver(self, selector: #selector(suspend),
                name: UIApplication.willResignActiveNotification, object: nil)
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            let velocity = pan.velocity(in: view)
            return enabled && card == nil && !finishing && !waitingForContent
                && pan.numberOfTouches == 1 && pan.location(in: view).y > excludedTop
                && velocity.y < 0 && abs(velocity.y) > abs(velocity.x) * 1.2
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            other is UIPanGestureRecognizer || other is UILongPressGestureRecognizer
        }

        @objc private func drag(_ recognizer: UIPanGestureRecognizer) {
            switch recognizer.state {
            case .began:
                guard let snapshot = host.view.snapshotView(afterScreenUpdates: false),
                      beginSnapshot(snapshot) else { return }
                recognizer.setTranslation(.zero, in: view)
            case .changed:
                guard card != nil, !finishing else { return }
                progress = DeletionMotion.dragProgress(distance: -recognizer.translation(in: view).y)
                applyDrag()
                let nextArmed = progress >= 0.72
                if armed != nextArmed {
                    armed = nextArmed
                    UISelectionFeedbackGenerator().selectionChanged()
                    onArm(armed)
                }
            case .ended:
                guard card != nil, !finishing else { return }
                settle(commit: armed)
            case .cancelled, .failed:
                if card != nil && !finishing { settle(commit: false) }
            default: break
            }
        }

        @discardableResult
        func beginSnapshot(_ snapshot: UIView) -> Bool {
            guard card == nil, !finishing, !waitingForContent else { return false }
            snapshot.frame = view.bounds
            snapshot.isUserInteractionEnabled = false
            snapshot.layer.masksToBounds = true
            card = snapshot
            shade.frame = view.bounds
            shade.alpha = 0.48
            view.addSubview(shade)
            view.addSubview(snapshot)
            guard onBegin() else { discard(); return false }
            suspendScrolling(in: host.view)
            host.view.layer.opacity = 0
            progress = 0
            armed = false
            return true
        }

        private func suspendScrolling(in view: UIView) {
            if let scroll = view as? UIScrollView, scroll.panGestureRecognizer.isEnabled {
                suspendedPans.append(scroll.panGestureRecognizer)
                scroll.panGestureRecognizer.isEnabled = false
            }
            for child in view.subviews { suspendScrolling(in: child) }
        }

        private func applyDrag() {
            guard let card else { return }
            let pose = DeletionMotion.dragPose(progress: progress)
            UIView.performWithoutAnimation {
                card.transform = reduceMotion ? .identity : pose.transform
                card.layer.cornerRadius = reduceMotion ? 0 : pose.radius
                shade.alpha = 0.48 * (1 - min(progress / 1.12, 1) * 0.30)
            }
        }

        func settle(commit: Bool) {
            guard let card else { return }
            finishing = true
            releasedCommit = commit
            onRelease()
            animationGeneration += 1
            let generation = animationGeneration
            if commit && !reduceMotion {
                let start = DeletionMotion.dragPose(progress: progress)
                let destination = targetY - view.bounds.midY
                UIView.animateKeyframes(withDuration: 0.28, delay: 0,
                                        options: [.calculationModeLinear, .beginFromCurrentState]) {
                    // Android's independent travel and shape curves, baked into
                    // Core Animation keyframes; no SwiftUI state writes per frame.
                    for step in 1...30 {
                        let t = CGFloat(step) / 30
                        let pose = DeletionMotion.commitPose(start: start, targetY: destination, progress: t)
                        UIView.addKeyframe(withRelativeStartTime: Double(step - 1) / 30,
                                           relativeDuration: 1.0 / 30) {
                            card.transform = pose.transform
                            card.layer.cornerRadius = pose.radius
                            card.alpha = pose.alpha
                            self.shade.alpha = 0.48 * (1 - t) * 0.7
                        }
                    }
                } completion: { [weak self] _ in
                    guard let self, generation == self.animationGeneration else { return }
                    self.finish(commit: true)
                }
            } else {
                UIView.animate(withDuration: reduceMotion ? 0.16 : 0.30,
                               delay: 0, usingSpringWithDamping: 0.88, initialSpringVelocity: 0,
                               options: [.beginFromCurrentState]) {
                    card.transform = .identity
                    card.layer.cornerRadius = 0
                    card.alpha = commit ? 0 : 1
                    self.shade.alpha = commit ? 0 : 0.48
                } completion: { [weak self] _ in
                    guard let self, generation == self.animationGeneration else { return }
                    self.finish(commit: commit)
                }
            }
        }

        private func finish(commit: Bool) {
            guard !waitingForContent else { return }
            waitingForContent = true
            onComplete(commit)
        }

        func restoreIfReady() {
            guard waitingForContent, !restoring else { return }
            restoring = true
            DispatchQueue.main.async { [weak self] in
                guard let self, self.waitingForContent else { return }
                self.host.view.setNeedsLayout()
                self.host.view.layoutIfNeeded()
                self.discard()
                self.onSettled()
            }
        }

        private func discard() {
            card?.removeFromSuperview()
            card = nil
            shade.removeFromSuperview()
            host.view.layer.opacity = 1
            suspendedPans.forEach { $0.isEnabled = true }
            suspendedPans.removeAll()
            finishing = false
            releasedCommit = false
            waitingForContent = false
            restoring = false
            progress = 0
            armed = false
        }

        func tearDown() {
            animationGeneration += 1
            card?.layer.removeAllAnimations()
            shade.layer.removeAllAnimations()
            discard()
        }

        @objc func suspend() {
            // Finish a released deletion once, cancel a finger-down drag, and
            // restore scrolling even if UIKit never delivers animation completion.
            guard card != nil else { return }
            animationGeneration += 1
            card?.layer.removeAllAnimations()
            shade.layer.removeAllAnimations()
            if !waitingForContent { finish(commit: finishing && releasedCommit) }
            restoreIfReady()
            pan.isEnabled = false
            pan.isEnabled = true
        }

    }
}

/// Pure motion math shared with the regression checks. Values match Android's
/// drag resistance and separate travel/shape curves; iOS uses the user-requested 280ms exit.
enum DeletionMotion {
    struct Pose {
        var y: CGFloat
        var scale: CGFloat
        var radius: CGFloat
        var alpha: CGFloat = 1
        var transform: CGAffineTransform {
            CGAffineTransform(translationX: 0, y: y).scaledBy(x: scale, y: scale)
        }
    }
    static func dragProgress(distance: CGFloat) -> CGFloat {
        let raw = max(distance, 0) / 140
        return raw <= 1 ? raw : min(1 + (raw - 1) * 0.14, 1.12)
    }
    static func dragPose(progress: CGFloat) -> Pose {
        let base = min(max(progress, 0), 1)
        let extra = min(max((progress - 1) / 0.12, 0), 1)
        return Pose(y: -base * 132 - extra * 18,
                    scale: 1 - base * 0.08 - extra * 0.01,
                    radius: base * 18 + extra * 2)
    }
    static func commitPose(start: Pose, targetY: CGFloat, progress: CGFloat) -> Pose {
        let t = min(max(progress, 0), 1)
        let travel = 1 - (1 - t) * (1 - t)
        let shape = t * t * (3 - 2 * t)
        return Pose(y: start.y + (targetY - start.y) * travel,
                    scale: start.scale + (0.035 - start.scale) * shape,
                    radius: start.radius + (180 - start.radius) * shape,
                    alpha: 1 - min(max((t - 0.88) / 0.12, 0), 1))
    }
}
