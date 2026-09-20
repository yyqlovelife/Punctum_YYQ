import CoreGraphics

/// Image-only transform about the unscaled frame's center. Recognizers supply
/// incremental changes, so another gesture continues from the retained pose.
struct PhotoZoomState: Equatable {
    static let maximumScale: CGFloat = 5
    private(set) var scale: CGFloat = 1
    private(set) var offset: CGSize = .zero
    var isZoomed: Bool { scale > 1 }

    mutating func pinch(by factor: CGFloat, at point: CGPoint, in frame: CGRect) {
        guard factor.isFinite, factor > 0, frame.width > 0, frame.height > 0 else { return }
        let next = min(max(scale * factor, 1), Self.maximumScale)
        let ratio = next / scale
        // Preserve the image pixel under the fingers when changing the scale.
        offset.width = (point.x - frame.midX) * (1 - ratio) + offset.width * ratio
        offset.height = (point.y - frame.midY) * (1 - ratio) + offset.height * ratio
        scale = next
        constrain(in: frame)
    }

    mutating func pan(by delta: CGPoint, in frame: CGRect) {
        guard isZoomed, delta.x.isFinite, delta.y.isFinite else { return }
        offset.width += delta.x
        offset.height += delta.y
        constrain(in: frame)
    }

    func transformedRect(_ frame: CGRect) -> CGRect {
        CGRect(x: frame.midX + offset.width - frame.width * scale / 2,
               y: frame.midY + offset.height - frame.height * scale / 2,
               width: frame.width * scale, height: frame.height * scale)
    }

    mutating func adopt(scale: CGFloat, offset: CGSize, in frame: CGRect) {
        guard scale.isFinite, offset.width.isFinite, offset.height.isFinite else { return }
        self.scale = min(max(scale, 1), Self.maximumScale)
        self.offset = offset
        constrain(in: frame)
    }

    mutating func match(_ other: PhotoZoomState, from source: CGSize, to target: CGSize) {
        guard source.width > 0, source.height > 0, target.width > 0, target.height > 0 else { return }
        scale = other.scale
        offset = CGSize(width: other.offset.width / source.width * target.width,
                        height: other.offset.height / source.height * target.height)
        constrain(in: CGRect(origin: .zero, size: target))
    }

    private mutating func constrain(in frame: CGRect) {
        let maxX = max(0, (scale - 1) * frame.width / 2)
        let maxY = max(0, (scale - 1) * frame.height / 2)
        offset.width = min(max(offset.width, -maxX), maxX)
        offset.height = min(max(offset.height, -maxY), maxY)
    }
}
