import AVFoundation
import Photos
import PhotosUI
import SwiftUI
import UIKit

enum LiveAudioSession {
    static func activate() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .moviePlayback)
            try session.setActive(true)
        } catch { }
    }
}

enum LivePlaybackMode: Equatable {
    case none
    case hold
    case playOnce
}

struct LivePhotoHost: UIViewRepresentable {
    let livePhoto: PHLivePhoto?
    var mode: LivePlaybackMode
    var onDidBeginPlayback: () -> Void
    var onDidEndPlayback: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onDidBeginPlayback: onDidBeginPlayback, onDidEndPlayback: onDidEndPlayback)
    }

    func makeUIView(context: Context) -> PHLivePhotoView {
        let view = PHLivePhotoView()
        view.delegate = context.coordinator
        view.contentMode = .scaleAspectFit
        view.backgroundColor = .clear
        view.isMuted = false
        view.isUserInteractionEnabled = true
        view.playbackGestureRecognizer.isEnabled = false
        view.livePhoto = livePhoto
        return view
    }

    func updateUIView(_ view: PHLivePhotoView, context: Context) {
        context.coordinator.onDidBeginPlayback = onDidBeginPlayback
        context.coordinator.onDidEndPlayback = onDidEndPlayback
        context.coordinator.mode = mode
        view.isUserInteractionEnabled = true
        view.playbackGestureRecognizer.isEnabled = false
        let photoChanged = view.livePhoto !== livePhoto
        if photoChanged {
            view.livePhoto = livePhoto
            context.coordinator.appliedMode = .none
        }
        switch mode {
        case .none:
            context.coordinator.cancelPendingPlayback()
            if context.coordinator.appliedMode != .none {
                view.isMuted = true
                view.stopPlayback()
                context.coordinator.appliedMode = .none
            }
        case .hold, .playOnce:
            guard view.livePhoto != nil else { return }
            guard photoChanged || context.coordinator.appliedMode != mode else { return }
            context.coordinator.beginPlayback(on: view, mode: mode)
        }
    }

    final class Coordinator: NSObject, PHLivePhotoViewDelegate {
        var mode: LivePlaybackMode = .none
        var appliedMode: LivePlaybackMode = .none
        var onDidBeginPlayback: () -> Void
        var onDidEndPlayback: () -> Void
        private var playbackGeneration = 0
        private var pendingPlaybackItems: [DispatchWorkItem] = []
        private var playbackDidBegin = false

        init(onDidBeginPlayback: @escaping () -> Void, onDidEndPlayback: @escaping () -> Void) {
            self.onDidBeginPlayback = onDidBeginPlayback
            self.onDidEndPlayback = onDidEndPlayback
        }

        func beginPlayback(on livePhotoView: PHLivePhotoView, mode requestedMode: LivePlaybackMode) {
            cancelPendingPlayback()
            appliedMode = requestedMode
            playbackDidBegin = false
            let generation = playbackGeneration
            LiveAudioSession.activate()

            // PHLivePhotoView may need one run-loop turn after receiving a new
            // PHLivePhoto. A guarded retry covers slower iCloud-backed assets.
            schedulePlayback(on: livePhotoView, mode: requestedMode, generation: generation, delay: 0.04)
            schedulePlayback(on: livePhotoView, mode: requestedMode, generation: generation, delay: 0.28)
        }

        func cancelPendingPlayback() {
            playbackGeneration += 1
            pendingPlaybackItems.forEach { $0.cancel() }
            pendingPlaybackItems.removeAll()
            playbackDidBegin = false
        }

        private func schedulePlayback(
            on livePhotoView: PHLivePhotoView,
            mode requestedMode: LivePlaybackMode,
            generation: Int,
            delay: TimeInterval
        ) {
            let item = DispatchWorkItem { [weak self, weak livePhotoView] in
                guard let self, let livePhotoView,
                      !self.playbackDidBegin,
                      self.playbackGeneration == generation,
                      self.mode == requestedMode,
                      self.appliedMode == requestedMode,
                      livePhotoView.livePhoto != nil else { return }
                livePhotoView.isMuted = false
                livePhotoView.startPlayback(with: .full)
            }
            pendingPlaybackItems.append(item)
            DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: item)
        }

        func livePhotoView(
            _ livePhotoView: PHLivePhotoView,
            willBeginPlaybackWith playbackStyle: PHLivePhotoViewPlaybackStyle
        ) {
            playbackDidBegin = true
            pendingPlaybackItems.forEach { $0.cancel() }
            pendingPlaybackItems.removeAll()
            onDidBeginPlayback()
        }

        func livePhotoView(
            _ livePhotoView: PHLivePhotoView,
            didEndPlaybackWith playbackStyle: PHLivePhotoViewPlaybackStyle
        ) {
            if mode == .hold {
                beginPlayback(on: livePhotoView, mode: .hold)
                return
            }
            onDidEndPlayback()
        }
    }
}

struct LiveHoldCatcher: UIViewRepresentable {
    var holdEnabled: Bool
    var pagingEnabled: Bool
    var livePlaybackActive: Bool
    var imageRect: CGRect
    var badgeHotRect: CGRect
    var onHoldStart: () -> Void
    var onHoldEnd: () -> Void
    var onBadgeTap: () -> Void
    var onTapLeft: () -> Void
    var onTapRight: () -> Void
    var onTapCenter: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> LiveCatcherView {
        let view = LiveCatcherView()
        context.coordinator.attach(to: view)
        return view
    }

    func updateUIView(_ uiView: LiveCatcherView, context: Context) {
        let coordinator = context.coordinator
        uiView.imageRect = imageRect
        uiView.badgeHotRect = badgeHotRect
        coordinator.holdEnabled = holdEnabled
        coordinator.pagingEnabled = pagingEnabled
        coordinator.livePlaybackActive = livePlaybackActive
        coordinator.imageRect = imageRect
        coordinator.badgeHotRect = badgeHotRect
        coordinator.onHoldStart = onHoldStart
        coordinator.onHoldEnd = onHoldEnd
        coordinator.onBadgeTap = onBadgeTap
        coordinator.onTapLeft = onTapLeft
        coordinator.onTapRight = onTapRight
        coordinator.onTapCenter = onTapCenter
        coordinator.pressRecognizer?.isEnabled = holdEnabled
        coordinator.tapRecognizer?.isEnabled = pagingEnabled
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var holdEnabled = true
        var pagingEnabled = true
        var livePlaybackActive = false
        var imageRect: CGRect = .null
        var badgeHotRect: CGRect = .null
        var onHoldStart: () -> Void = {}
        var onHoldEnd: () -> Void = {}
        var onBadgeTap: () -> Void = {}
        var onTapLeft: () -> Void = {}
        var onTapRight: () -> Void = {}
        var onTapCenter: () -> Void = {}
        var pressRecognizer: UILongPressGestureRecognizer?
        var tapRecognizer: UITapGestureRecognizer?
        private var didStart = false

        func attach(to view: LiveCatcherView) {
            let press = UILongPressGestureRecognizer(target: self, action: #selector(handlePress(_:)))
            press.minimumPressDuration = 0.15
            press.allowableMovement = 28
            press.cancelsTouchesInView = false
            press.delegate = self
            let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
            tap.cancelsTouchesInView = true
            tap.delegate = self
            tap.require(toFail: press)
            view.addGestureRecognizer(press)
            view.addGestureRecognizer(tap)
            pressRecognizer = press
            tapRecognizer = tap
        }

        @objc func handlePress(_ recognizer: UILongPressGestureRecognizer) {
            guard holdEnabled else { return }
            switch recognizer.state {
            case .began:
                didStart = true
                onHoldStart()
            case .ended, .cancelled, .failed:
                if didStart { onHoldEnd() }
                didStart = false
            default:
                break
            }
        }

        @objc func handleTap(_ recognizer: UITapGestureRecognizer) {
            guard pagingEnabled, let view = recognizer.view else { return }
            let point = recognizer.location(in: view)
            if badgeHotRect.contains(point) {
                onBadgeTap()
                return
            }
            if livePlaybackActive { return }
            let width = view.bounds.width
            if point.x < width * 0.25 {
                onTapLeft()
            } else if point.x > width * 0.75 {
                onTapRight()
            } else {
                onTapCenter()
            }
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            let point = gestureRecognizer.location(in: gestureRecognizer.view)
            if gestureRecognizer === pressRecognizer {
                return holdEnabled && imageRect.contains(point) && !badgeHotRect.contains(point)
            }
            return pagingEnabled
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldReceive touch: UITouch
        ) -> Bool {
            if gestureRecognizer === pressRecognizer { return holdEnabled }
            return pagingEnabled
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            otherGestureRecognizer is UIPanGestureRecognizer && gestureRecognizer === pressRecognizer
        }
    }
}

final class LiveCatcherView: UIView {
    var imageRect: CGRect = .null
    var badgeHotRect: CGRect = .null

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        isUserInteractionEnabled = true
        backgroundColor = .clear
        autoresizingMask = [.flexibleWidth, .flexibleHeight]
    }

    required init?(coder: NSCoder) { nil }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if imageRect.contains(point) { return true }
        return point.x < bounds.width * 0.25 || point.x > bounds.width * 0.75
    }
}

struct LivePhotoBadge: View {
    var body: some View {
        ZStack {
            Circle().fill(Color.black.opacity(0.38))
            Canvas { context, size in
                let ink = Color.white.opacity(0.82)
                let minSide = min(size.width, size.height)
                let stroke: CGFloat = 1.1
                let dashedStroke: CGFloat = 1
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                context.fill(Path(ellipseIn: CGRect(
                    x: center.x - minSide * 0.11,
                    y: center.y - minSide * 0.11,
                    width: minSide * 0.22,
                    height: minSide * 0.22
                )), with: .color(ink))
                context.stroke(
                    Path(ellipseIn: CGRect(
                        x: center.x - minSide * 0.30,
                        y: center.y - minSide * 0.30,
                        width: minSide * 0.60,
                        height: minSide * 0.60
                    )),
                    with: .color(ink),
                    lineWidth: stroke
                )
                context.stroke(
                    Path(ellipseIn: CGRect(
                        x: center.x - minSide * 0.44,
                        y: center.y - minSide * 0.44,
                        width: minSide * 0.88,
                        height: minSide * 0.88
                    )),
                    with: .color(ink),
                    style: StrokeStyle(lineWidth: dashedStroke, dash: [1.7, 1.05])
                )
            }
            .frame(width: 16, height: 16)
        }
        .frame(width: 26, height: 26)
    }
}

struct PagingScrollLock: UIViewRepresentable {
    var locked: Bool

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            var current: UIView? = uiView.superview
            while let candidate = current {
                if let scrollView = candidate as? UIScrollView, scrollView.isPagingEnabled {
                    scrollView.isScrollEnabled = !locked
                }
                current = candidate.superview
            }
        }
    }
}
