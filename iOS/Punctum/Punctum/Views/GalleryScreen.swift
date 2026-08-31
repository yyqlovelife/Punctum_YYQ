import SwiftUI
import UIKit

struct GalleryScreen: View {
    let gallery: PunctumGallery
    let photos: [PhotoItem]
    let overview: GalleryOverview?
    let isLoading: Bool
    let onOpenSwitcher: () -> Void
    let onRename: () -> Void
    let onSelectPhoto: (Int) -> Void
    let onDeletePhoto: (PhotoItem) -> Void
    var onLoadMore: () -> Void = {}

    var body: some View {
        ScrollViewReader { reader in
            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 0) {
                    GalleryHeader(
                        gallery: gallery,
                        count: overview?.count ?? photos.count,
                        timeSpan: overview?.timeSpan ?? "",
                        onOpenSwitcher: onOpenSwitcher,
                        onRename: onRename
                    )
                    .id("gallery-header")
                    .onTapGesture(count: 2) {
                        withAnimation { reader.scrollTo("gallery-header", anchor: .top) }
                    }

                    if isLoading {
                        ProgressView()
                            .tint(PunctumTheme.gold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 80)
                    } else if photos.isEmpty {
                        Text("这个图集里还没有照片")
                            .font(PunctumTheme.serifSC(15))
                            .foregroundStyle(PunctumTheme.muted)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 80)
                    } else {
                        ForEach(Array(stride(from: 0, to: photos.count, by: 2)), id: \.self) { index in
                            OriginalRatioRow(
                                photos: Array(photos[index..<min(index + 2, photos.count)]),
                                startIndex: index,
                                onSelect: onSelectPhoto,
                                onDelete: onDeletePhoto
                            )
                            .onAppear {
                                if index + 4 >= photos.count {
                                    onLoadMore()
                                }
                            }
                        }
                    }

                    Spacer().frame(height: 24)
                }
            }
            .background(PunctumTheme.ink)
        }
    }
}

private struct GalleryHeader: View {
    let gallery: PunctumGallery
    let count: Int
    let timeSpan: String
    let onOpenSwitcher: () -> Void
    let onRename: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onOpenSwitcher) {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.left")
                            .font(.system(size: 14, weight: .medium))
                        Text("Your Punctums")
                            .font(PunctumTheme.georgia(12, bold: true))
                            .tracking(1.2)
                    }
                    .foregroundStyle(PunctumTheme.muted)
                    .padding(.vertical, 6)
                }
                .buttonStyle(.plain)
                Spacer()
                Button(action: onRename) {
                    Image(systemName: "pencil")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(PunctumTheme.muted)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("重命名")
            }

            Text(gallery.displayName)
                .font(PunctumTheme.galleryTitle(gallery.displayName, size: 44))
                .foregroundStyle(PunctumTheme.bone)
                .padding(.top, 14)

            if !timeSpan.isEmpty {
                Text(timeSpan)
                    .font(PunctumTheme.serifSC(15))
                    .foregroundStyle(PunctumTheme.muted)
                    .padding(.top, 14)
            }

            HStack(alignment: .center) {
                Text("关于 \(count) 幅作品的故事")
                    .font(PunctumTheme.serifSC(15))
                    .foregroundStyle(PunctumTheme.muted)
                Spacer()
                Text("风格 · 原幅")
                    .font(PunctumTheme.georgia(12, bold: true))
                    .tracking(1.2)
                    .foregroundStyle(PunctumTheme.gold.opacity(0.8))
            }
            .padding(.top, timeSpan.isEmpty ? 14 : 6)
            .padding(.trailing, 12)

            HairlineDivider()
                .padding(.top, 16)
                .padding(.trailing, 12)
        }
        .padding(.leading, 24)
        .padding(.trailing, 12)
        .padding(.top, 18)
        .padding(.bottom, 16)
    }
}

private struct OriginalRatioRow: View {
    let photos: [PhotoItem]
    let startIndex: Int
    let onSelect: (Int) -> Void
    let onDelete: (PhotoItem) -> Void

    private var aspects: [CGFloat] {
        photos.map { photo in
            let raw = photo.hasKnownSize ? photo.aspectRatio : 1.5
            return min(max(raw, 0.45), 2.4)
        }
    }

    private var rowAspects: [CGFloat] {
        if photos.count == 1 { return [aspects[0], aspects[0]] }
        return aspects
    }

    var body: some View {
        let sum = max(rowAspects.reduce(0, +), 0.01)
        Color.clear
            .aspectRatio(sum, contentMode: .fit)
            .overlay {
                GeometryReader { geometry in
                    HStack(spacing: 0) {
                        ForEach(Array(photos.enumerated()), id: \.element.id) { offset, photo in
                            PhotoGridCell(
                                photo: photo,
                                onSelect: { onSelect(startIndex + offset) },
                                onDelete: { onDelete(photo) }
                            )
                            .id(photo.id)
                            .accessibilityIdentifier("photo-grid-cell-\(startIndex + offset)")
                            .accessibilityValue(photo.id)
                            .frame(
                                width: geometry.size.width * aspects[offset] / sum,
                                height: geometry.size.height
                            )
                            .clipped()
                        }
                        if photos.count == 1 {
                            Color.clear.frame(
                                width: geometry.size.width * aspects[0] / sum,
                                height: geometry.size.height
                            )
                        }
                    }
                }
            }
    }
}

private struct PhotoGridCell: View {
    let photo: PhotoItem
    let onSelect: () -> Void
    let onDelete: () -> Void

    var body: some View {
        ZStack {
            PhotoAssetImage(
                photo: photo,
                targetSize: photo.thumbnailTargetSize,
                contentMode: .fill,
                skipDegraded: false
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
            GridPressCatcher(
                onTap: onSelect,
                onCommit: {
                    UINotificationFeedbackGenerator().notificationOccurred(.warning)
                    onDelete()
                }
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .contentShape(Rectangle())
    }
}

private struct GridPressCatcher: UIViewRepresentable {
    var onTap: () -> Void
    var onCommit: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> GridPressView {
        let view = GridPressView()
        context.coordinator.attach(to: view)
        return view
    }

    func updateUIView(_ uiView: GridPressView, context: Context) {
        context.coordinator.onTap = onTap
        context.coordinator.onCommit = onCommit
        context.coordinator.view = uiView
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onTap: () -> Void = {}
        var onCommit: () -> Void = {}
        weak var view: GridPressView?
        private var ignoreTap = false
        private var committed = false
        private var tap: UITapGestureRecognizer!
        private var preview: UILongPressGestureRecognizer!
        private var commit: UILongPressGestureRecognizer!
        private var displayLink: CADisplayLink?
        private var pressStartedAt: CFTimeInterval = 0
        private let previewDuration: CFTimeInterval = 0.22
        private let commitDuration: CFTimeInterval = 0.7

        func attach(to view: GridPressView) {
            self.view = view
            tap = UITapGestureRecognizer(target: self, action: #selector(handleTap))
            preview = UILongPressGestureRecognizer(target: self, action: #selector(handlePreview(_:)))
            preview.minimumPressDuration = previewDuration
            preview.allowableMovement = 8
            preview.cancelsTouchesInView = false
            preview.delaysTouchesBegan = false
            commit = UILongPressGestureRecognizer(target: self, action: #selector(handleCommit(_:)))
            commit.minimumPressDuration = commitDuration
            commit.allowableMovement = 8
            commit.cancelsTouchesInView = false
            commit.delaysTouchesBegan = false
            tap.delegate = self
            preview.delegate = self
            commit.delegate = self
            tap.require(toFail: preview)
            view.addGestureRecognizer(tap)
            view.addGestureRecognizer(preview)
            view.addGestureRecognizer(commit)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool {
            if other is UIPanGestureRecognizer { return false }
            return (gestureRecognizer === preview && other === commit)
                || (gestureRecognizer === commit && other === preview)
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldBeRequiredToFailBy other: UIGestureRecognizer
        ) -> Bool {
            other is UIPanGestureRecognizer &&
                (gestureRecognizer === preview || gestureRecognizer === commit)
        }

        @objc private func handleTap() {
            guard !ignoreTap else { return }
            onTap()
        }

        @objc private func handlePreview(_ recognizer: UILongPressGestureRecognizer) {
            switch recognizer.state {
            case .began:
                ignoreTap = true
                committed = false
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                startProgress()
            case .ended, .cancelled, .failed:
                stopProgress(reset: !committed)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [weak self] in
                    self?.ignoreTap = false
                }
            default:
                break
            }
        }

        @objc private func handleCommit(_ recognizer: UILongPressGestureRecognizer) {
            guard recognizer.state == .began else { return }
            ignoreTap = true
            committed = true
            view?.progress = 1
            stopProgress(reset: false)
            onCommit()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
                self?.view?.progress = 0
            }
        }

        private func startProgress() {
            pressStartedAt = CACurrentMediaTime()
            view?.progress = 0.08
            displayLink?.invalidate()
            let link = CADisplayLink(target: self, selector: #selector(tickProgress))
            link.add(to: .main, forMode: .common)
            displayLink = link
        }

        @objc private func tickProgress() {
            let elapsed = CACurrentMediaTime() - pressStartedAt
            let span = commitDuration - previewDuration
            let t = min(max(elapsed / span, 0), 1)
            view?.progress = 0.08 + t * 0.9
        }

        private func stopProgress(reset: Bool) {
            displayLink?.invalidate()
            displayLink = nil
            if reset { view?.progress = 0 }
        }
    }
}

final class GridPressView: UIView {
    var progress: CGFloat = 0 {
        didSet { badge.progress = progress }
    }

    private let badge = DeleteBadgeView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = true
        badge.translatesAutoresizingMaskIntoConstraints = false
        addSubview(badge)
        NSLayoutConstraint.activate([
            badge.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            badge.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            badge.widthAnchor.constraint(equalToConstant: 34),
            badge.heightAnchor.constraint(equalToConstant: 34),
        ])
    }

    required init?(coder: NSCoder) { nil }
}

private final class DeleteBadgeView: UIView {
    var progress: CGFloat = 0 {
        didSet {
            isHidden = progress <= 0
            setNeedsDisplay()
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        isUserInteractionEnabled = false
        isHidden = true
    }

    required init?(coder: NSCoder) { nil }

    override func draw(_ rect: CGRect) {
        guard progress > 0, let ctx = UIGraphicsGetCurrentContext() else { return }
        let bounds = rect.insetBy(dx: 0.5, dy: 0.5)
        ctx.setFillColor(UIColor.black.withAlphaComponent(0.72).cgColor)
        ctx.fillEllipse(in: bounds)

        let ring = bounds.insetBy(dx: 3, dy: 3)
        ctx.setStrokeColor(UIColor.white.withAlphaComponent(0.28).cgColor)
        ctx.setLineWidth(2)
        ctx.strokeEllipse(in: ring)

        let amount = min(max(progress, 0), 1)
        if amount > 0 {
            let center = CGPoint(x: bounds.midX, y: bounds.midY)
            let radius = ring.width / 2
            ctx.setStrokeColor(UIColor(red: 230 / 255, green: 59 / 255, blue: 66 / 255, alpha: 1).cgColor)
            ctx.setLineCap(.round)
            ctx.setLineWidth(2)
            ctx.addArc(
                center: center,
                radius: radius,
                startAngle: -.pi / 2,
                endAngle: -.pi / 2 + .pi * 2 * amount,
                clockwise: false
            )
            ctx.strokePath()
        }

        let trash = UIImage(systemName: "trash")?
            .withConfiguration(UIImage.SymbolConfiguration(pointSize: 15, weight: .medium))
            .withTintColor(.white, renderingMode: .alwaysOriginal)
        let size = trash?.size ?? .zero
        trash?.draw(in: CGRect(
            x: bounds.midX - size.width / 2,
            y: bounds.midY - size.height / 2,
            width: size.width,
            height: size.height
        ))
    }
}
