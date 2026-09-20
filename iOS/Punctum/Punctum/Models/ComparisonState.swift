import CoreGraphics

struct ComparisonState {
    enum Layout { case rows, columns }
    private(set) var poses = [PhotoZoomState(), PhotoZoomState()]
    private(set) var linked = false
    private(set) var motionID = 0
    private(set) var activeIndex = 0
    var frames = [CGRect.zero, CGRect.zero]

    static func layout(first: CGSize, second: CGSize) -> Layout {
        first.height > first.width && second.height > second.width ? .columns : .rows
    }

    mutating func updateFrame(index: Int, frame: CGRect) {
        let previous = frames[index]
        frames[index] = frame
        if previous.width > 0 && previous.height > 0 {
            let old = poses[index]
            poses[index].match(old, from: previous.size, to: frame.size)
        }
        if linked { synchronize() }
    }

    mutating func toggleLink() {
        linked.toggle()
        if linked { synchronize(); motionID += 1 }
    }

    mutating func begin(index: Int, scale: CGFloat, offset: CGSize) {
        activeIndex = index
        poses[index].adopt(scale: scale, offset: offset, in: frames[index])
        if linked { synchronize() }
    }

    mutating func pinch(index: Int, factor: CGFloat, point: CGPoint, translation: CGPoint) {
        activeIndex = index
        poses[index].pan(by: translation, in: frames[index])
        poses[index].pinch(by: factor, at: point, in: frames[index])
        if linked { synchronize() }
    }

    mutating func pan(index: Int, delta: CGPoint) {
        activeIndex = index
        poses[index].pan(by: delta, in: frames[index])
        if linked { synchronize() }
    }

    mutating func tap(index: Int, point: CGPoint, double: Bool) {
        motionID += 1
        activeIndex = index
        if poses[index].isZoomed {
            if double { poses[index] = PhotoZoomState() }
        } else {
            poses[index].pinch(by: 2, at: point, in: frames[index])
        }
        if linked { synchronize() }
    }

    private mutating func synchronize() {
        let other = 1 - activeIndex
        poses[other].match(poses[activeIndex], from: frames[activeIndex].size, to: frames[other].size)
    }

    static func returningID(ids: [String], originalID: String, deletedID: String) -> String? {
        let remaining = ids.filter { $0 != deletedID }
        guard deletedID == originalID else { return remaining.contains(originalID) ? originalID : remaining.first }
        guard let index = ids.firstIndex(of: originalID), !remaining.isEmpty else { return remaining.first }
        return remaining[min(index, remaining.count - 1)]
    }
}
