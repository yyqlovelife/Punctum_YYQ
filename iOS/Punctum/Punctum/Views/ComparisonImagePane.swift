import SwiftUI
import UIKit

/// A clipped cell keeps its image and gestures independent from the other cell.
struct ComparisonImagePane: UIViewRepresentable {
    var image: UIImage
    var pose: PhotoZoomState
    var motionID: Int
    var reduceMotion: Bool
    var onBegin: (CGFloat, CGSize) -> Void
    var onFrame: (CGRect) -> Void
    var onPinch: (CGFloat, CGPoint, CGPoint) -> Void
    var onPan: (CGPoint) -> Void
    var onTap: (CGPoint, Bool) -> Void

    func makeUIView(context: Context) -> Pane {
        let view = Pane()
        view.configure(self)
        return view
    }
    func updateUIView(_ view: Pane, context: Context) { view.configure(self) }

    final class Pane: UIView, UIGestureRecognizerDelegate {
        private let imageView = UIImageView()
        private var input: ComparisonImagePane?
        private var imageFrame = CGRect.zero
        private var pinchPoint: CGPoint?
        private var touchCount = 0
        private lazy var pinch = UIPinchGestureRecognizer(target: self, action: #selector(pinched(_:)))
        private lazy var pan = UIPanGestureRecognizer(target: self, action: #selector(panned(_:)))
        override init(frame: CGRect) {
            super.init(frame: frame)
            clipsToBounds = true
            backgroundColor = .black
            isMultipleTouchEnabled = true
            imageView.contentMode = .scaleAspectFit
            addSubview(imageView)
            pinch.delegate = self
            pan.delegate = self
            pan.maximumNumberOfTouches = 2
            addGestureRecognizer(pinch)
            addGestureRecognizer(pan)
            let single = UITapGestureRecognizer(target: self, action: #selector(tapped(_:)))
            let double = UITapGestureRecognizer(target: self, action: #selector(doubleTapped(_:)))
            double.numberOfTapsRequired = 2
            single.require(toFail: double)
            addGestureRecognizer(single)
            addGestureRecognizer(double)
        }
        required init?(coder: NSCoder) { nil }
        func configure(_ input: ComparisonImagePane) {
            let animate = self.input != nil && self.input?.motionID != input.motionID && !input.reduceMotion
            let changed = self.input?.pose != input.pose || imageView.image !== input.image
            self.input = input
            guard changed else { return }
            imageView.image = input.image
            setNeedsLayout()
            if animate {
                UIView.animate(withDuration: 0.30, delay: 0, usingSpringWithDamping: 0.88,
                               initialSpringVelocity: 0, options: [.beginFromCurrentState, .allowUserInteraction]) {
                    self.layoutIfNeeded()
                }
            } else {
                UIView.performWithoutAnimation { self.layoutIfNeeded() }
            }
        }
        override func layoutSubviews() {
            super.layoutSubviews()
            guard let input else { return }
            let size = input.image.size
            let fit = min(bounds.width / max(size.width, 1), bounds.height / max(size.height, 1))
            let fitted = CGSize(width: size.width * fit, height: size.height * fit)
            let frame = CGRect(x: (bounds.width - fitted.width) / 2, y: (bounds.height - fitted.height) / 2,
                               width: fitted.width, height: fitted.height)
            imageView.bounds = CGRect(origin: .zero, size: fitted)
            imageView.center = CGPoint(x: bounds.midX + input.pose.offset.width,
                                       y: bounds.midY + input.pose.offset.height)
            imageView.transform = CGAffineTransform(scaleX: input.pose.scale, y: input.pose.scale)
            if imageFrame != frame {
                imageFrame = frame
                DispatchQueue.main.async { [weak self] in self?.input?.onFrame(frame) }
            }
        }
        private func interruptReturn() {
            guard let layer = imageView.layer.presentation(),
                  imageView.layer.animationKeys()?.isEmpty == false else { return }
            let scale = CGFloat(layer.transform.m11)
            let offset = CGSize(width: layer.position.x - bounds.midX, height: layer.position.y - bounds.midY)
            imageView.layer.removeAllAnimations()
            imageView.center = layer.position
            imageView.transform = CGAffineTransform(scaleX: scale, y: scale)
            input?.onBegin(scale, offset)
        }
        @objc private func pinched(_ sender: UIPinchGestureRecognizer) {
            if sender.state == .began { interruptReturn() }
            switch sender.state {
            case .began, .changed:
                guard sender.numberOfTouches == 2 else { return }
                let point = sender.location(in: self)
                let previous = pinchPoint ?? point
                input?.onPinch(sender.scale, point, CGPoint(x: point.x - previous.x, y: point.y - previous.y))
                pinchPoint = point
                sender.scale = 1
                pan.setTranslation(.zero, in: self)
            case .ended, .cancelled, .failed: pinchPoint = nil
            default: break
            }
        }
        @objc private func panned(_ sender: UIPanGestureRecognizer) {
            if sender.state == .began { interruptReturn(); touchCount = sender.numberOfTouches }
            if sender.state == .changed, pinchPoint == nil, touchCount == sender.numberOfTouches {
                input?.onPan(sender.translation(in: self))
            }
            touchCount = sender.numberOfTouches
            sender.setTranslation(.zero, in: self)
        }
        @objc private func tapped(_ sender: UITapGestureRecognizer) { interruptReturn(); input?.onTap(sender.location(in: self), false) }
        @objc private func doubleTapped(_ sender: UITapGestureRecognizer) { interruptReturn(); input?.onTap(sender.location(in: self), true) }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            (gestureRecognizer === pinch && other === pan) || (gestureRecognizer === pan && other === pinch)
        }
    }
}
