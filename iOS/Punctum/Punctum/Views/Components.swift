import SwiftUI
import UIKit

struct GalleryTitleText: View {
    let text: String
    var size: CGFloat
    var color: Color
    var alignment: Alignment = .leading
    var textAlignment: TextAlignment = .leading
    var maxLines: Int = 1
    var minimumScaleFactor: CGFloat = 0.65
    var lineSpacing: CGFloat = 0

    var body: some View {
        GalleryTitleLabel(
            text: text,
            font: PunctumTheme.galleryTitleUIFont(text, size: size),
            color: UIColor(color),
            alignment: nsAlignment,
            maxLines: maxLines,
            minimumScaleFactor: minimumScaleFactor,
            lineSpacing: lineSpacing
        )
    }

    private var nsAlignment: NSTextAlignment {
        switch textAlignment {
        case .center: return .center
        case .trailing: return .right
        default: return .left
        }
    }
}

/// UILabel so invitation-card Buttons cannot synthesize extra CJK weight.
private struct GalleryTitleLabel: UIViewRepresentable {
    var text: String
    var font: UIFont
    var color: UIColor
    var alignment: NSTextAlignment
    var maxLines: Int
    var minimumScaleFactor: CGFloat
    var lineSpacing: CGFloat

    func makeUIView(context: Context) -> CenteredTitleHost {
        CenteredTitleHost()
    }

    func updateUIView(_ host: CenteredTitleHost, context: Context) {
        apply(to: host)
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: CenteredTitleHost, context: Context) -> CGSize? {
        apply(to: uiView)
        let width = proposal.width ?? 0
        let constraintWidth = width > 0 ? width : CGFloat.greatestFiniteMagnitude
        let textSize = uiView.label.sizeThatFits(
            CGSize(width: constraintWidth, height: .greatestFiniteMagnitude)
        )
        if let height = proposal.height, height.isFinite, height > textSize.height {
            return CGSize(width: width > 0 ? width : textSize.width, height: height)
        }
        return textSize
    }

    private func apply(to host: CenteredTitleHost) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        paragraph.lineBreakMode = .byTruncatingTail
        if lineSpacing > 0 {
            paragraph.lineSpacing = lineSpacing
        }
        let label = host.label
        label.lockedFont = font
        label.setLockedText(NSAttributedString(string: text, attributes: [
            .font: font,
            .foregroundColor: color,
            .paragraphStyle: paragraph
        ]))
        label.numberOfLines = maxLines
        label.adjustsFontSizeToFitWidth = minimumScaleFactor < 1
        label.minimumScaleFactor = minimumScaleFactor
        label.baselineAdjustment = .alignCenters
        host.containsChinese = text.containsChinese
        host.setNeedsLayout()
    }
}

final class CenteredTitleHost: UIView {
    let label = LockedFontLabel()
    var containsChinese = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = false
        backgroundColor = .clear
        label.adjustsFontForContentSizeCategory = false
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        addSubview(label)
    }

    required init?(coder: NSCoder) { nil }

    override func layoutSubviews() {
        super.layoutSubviews()
        let fitted = label.sizeThatFits(
            CGSize(width: bounds.width, height: .greatestFiniteMagnitude)
        )
        let height = max(fitted.height, 1)
        var y = (bounds.height - height) / 2
        if containsChinese, let font = label.lockedFont {
            y -= font.pointSize * 0.1
        }
        label.frame = CGRect(x: 0, y: y, width: bounds.width, height: height)
    }
}

final class LockedFontLabel: UILabel {
    var lockedFont: UIFont? {
        didSet { super.font = lockedFont }
    }

    override var font: UIFont! {
        get { lockedFont ?? super.font }
        set { super.font = lockedFont ?? newValue }
    }

    func setLockedText(_ value: NSAttributedString?) {
        guard let lockedFont, let value, value.length > 0 else {
            super.attributedText = value
            return
        }
        let mutable = NSMutableAttributedString(attributedString: value)
        mutable.addAttribute(
            .font,
            value: lockedFont,
            range: NSRange(location: 0, length: mutable.length)
        )
        super.attributedText = mutable
    }
}

struct InvitationCardButtonStyle: PrimitiveButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        ImmediatePressButtonBody(action: configuration.trigger) { isPressed in
            configuration.label
                .scaleEffect(isPressed ? 0.985 : 1)
                .offset(y: isPressed ? 4 : 0)
                .opacity(isPressed ? 0.96 : 1)
        }
    }
}

struct HeaderBackButtonStyle: PrimitiveButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        ImmediatePressButtonBody(action: configuration.trigger) { isPressed in
            configuration.label
                .environment(\.immediateButtonIsPressed, isPressed)
        }
    }
}

struct IconPressButtonStyle: PrimitiveButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        ImmediatePressButtonBody(action: configuration.trigger) { isPressed in
            configuration.label
                .scaleEffect(isPressed ? 0.88 : 1)
                .offset(y: isPressed ? 2 : 0)
                .opacity(isPressed ? 0.78 : 1)
        }
    }
}

private struct ImmediateButtonPressedKey: EnvironmentKey {
    static let defaultValue = false
}

extension EnvironmentValues {
    var immediateButtonIsPressed: Bool {
        get { self[ImmediateButtonPressedKey.self] }
        set { self[ImmediateButtonPressedKey.self] = newValue }
    }
}

private struct ImmediatePressButtonBody<Content: View>: View {
    let action: () -> Void
    @ViewBuilder let content: (Bool) -> Content

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isPressed = false
    @State private var isCompletingTap = false

    var body: some View {
        content(reduceMotion ? false : isPressed)
            .opacity(reduceMotion && isPressed ? 0.8 : 1)
            .contentShape(Rectangle())
            .overlay {
                ImmediatePressGestureOverlay(onPhase: handleGesturePhase)
                    .allowsHitTesting(isEnabled)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
                isPressed = false
                isCompletingTap = false
            }
    }

    private func handleGesturePhase(_ phase: ImmediatePressGestureOverlay.Phase) {
        switch phase {
        case .began:
            guard isEnabled, !isCompletingTap else { return }
            withAnimation(.easeOut(duration: 0.11)) {
                isPressed = true
            }

        case .ended:
            guard isEnabled, isPressed, !isCompletingTap else { return }
            isCompletingTap = true
            withAnimation(.spring(response: 0.20, dampingFraction: 0.72)) {
                isPressed = false
            }
            action()
            isCompletingTap = false

        case .cancelled:
            guard !isCompletingTap else { return }
            withAnimation(.spring(response: 0.20, dampingFraction: 0.76)) {
                isPressed = false
            }
        }
    }
}

private struct ImmediatePressGestureOverlay: UIViewRepresentable {
    enum Phase {
        case began
        case ended
        case cancelled
    }

    let onPhase: (Phase) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPhase: onPhase)
    }

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear

        let recognizer = UILongPressGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handlePress(_:))
        )
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = 10
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = context.coordinator
        view.addGestureRecognizer(recognizer)
        context.coordinator.recognizer = recognizer
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.onPhase = onPhase
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onPhase: (Phase) -> Void
        weak var recognizer: UILongPressGestureRecognizer?
        private var travel = PressTravelGate()

        init(onPhase: @escaping (Phase) -> Void) {
            self.onPhase = onPhase
            super.init()
            NotificationCenter.default.addObserver(self, selector: #selector(suspendPress),
                name: UIApplication.willResignActiveNotification, object: nil)
        }
        deinit { NotificationCenter.default.removeObserver(self) }

        @objc private func suspendPress() {
            travel.cancel()
            onPhase(.cancelled)
            recognizer?.isEnabled = false
            recognizer?.isEnabled = true
        }

        private func isScrolling(_ view: UIView?) -> Bool {
            var ancestor = view?.superview
            while let candidate = ancestor {
                if let scroll = candidate as? UIScrollView, scroll.isDragging || scroll.isDecelerating { return true }
                ancestor = candidate.superview
            }
            return false
        }

        @objc func handlePress(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: recognizer.view?.window)
            switch recognizer.state {
            case .began:
                travel.begin(at: point)
                if isScrolling(recognizer.view) { travel.cancel() }
                onPhase(travel.isTap ? .began : .cancelled)
            case .changed:
                travel.move(to: point)
                if isScrolling(recognizer.view) { travel.cancel() }
                if !travel.isTap { onPhase(.cancelled) }
            case .ended:
                travel.move(to: point)
                guard travel.isTap, !isScrolling(recognizer.view), let view = recognizer.view,
                      view.bounds.insetBy(dx: -4, dy: -4).contains(recognizer.location(in: view)) else {
                    onPhase(.cancelled)
                    return
                }
                travel.cancel()
                onPhase(.ended)
            case .cancelled, .failed:
                travel.cancel()
                onPhase(.cancelled)
            default: break
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }
}

struct SectionLabel: View {
    let text: String

    var body: some View {
        Text(text)
            .font(PunctumTheme.georgia(12, bold: true))
            .tracking(1.2)
            .foregroundStyle(PunctumTheme.muted)
    }
}

struct HairlineDivider: View {
    var body: some View {
        Rectangle().fill(PunctumTheme.hairline).frame(height: 1)
    }
}

struct PunctumDialogBackdrop: View {
    var body: some View {
        Color.black
            .opacity(0.22)
            .ignoresSafeArea()
            .allowsHitTesting(false)
            .transition(.opacity)
    }
}

extension View {
    func punctumDialogSurface() -> some View {
        background(PunctumTheme.dialogSurface.ignoresSafeArea())
            .overlay {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(PunctumTheme.bone.opacity(0.12), lineWidth: 1)
                    .allowsHitTesting(false)
            }
            .shadow(color: .black.opacity(0.60), radius: 24, y: 8)
    }

    func punctumDialogPresentation() -> some View {
        presentationBackground(PunctumTheme.dialogSurface)
            .presentationCornerRadius(28)
            .presentationDragIndicator(.hidden)
    }
}

struct FrameButton: View {
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(PunctumTheme.georgia(12, bold: true))
                .tracking(1.2)
                .foregroundStyle(PunctumTheme.gold)
                .padding(.horizontal, 32)
                .padding(.vertical, 15)
                .overlay(Rectangle().stroke(PunctumTheme.gold.opacity(0.55), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct MoveToAlbumIcon: View {
    var body: some View {
        Canvas { context, size in
            let line = max(size.width * 0.085, 1.2)
            var back = Path(roundedRect: CGRect(
                x: size.width * 0.08,
                y: size.height * 0.08,
                width: size.width * 0.46,
                height: size.height * 0.58
            ), cornerRadius: size.width * 0.06)
            context.stroke(back, with: .foreground, lineWidth: line)

            var front = Path(roundedRect: CGRect(
                x: size.width * 0.22,
                y: size.height * 0.28,
                width: size.width * 0.46,
                height: size.height * 0.58
            ), cornerRadius: size.width * 0.06)
            context.stroke(front, with: .foreground, lineWidth: line)

            let arrowY = size.height * 0.42
            var arrow = Path()
            arrow.move(to: CGPoint(x: size.width * 0.58, y: arrowY))
            arrow.addLine(to: CGPoint(x: size.width * 0.92, y: arrowY))
            context.stroke(arrow, with: .foreground, style: StrokeStyle(lineWidth: line, lineCap: .round))

            var head = Path()
            head.move(to: CGPoint(x: size.width * 0.78, y: arrowY - size.height * 0.16))
            head.addLine(to: CGPoint(x: size.width * 0.94, y: arrowY))
            head.addLine(to: CGPoint(x: size.width * 0.78, y: arrowY + size.height * 0.16))
            context.stroke(head, with: .foreground, style: StrokeStyle(lineWidth: line, lineCap: .round, lineJoin: .round))
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

struct ToastView: View {
    let message: String
    var fontSize: CGFloat = 12
    var multiline = false

    var body: some View {
        Text(message)
            .font(PunctumTheme.serifSC(fontSize))
            .foregroundStyle(PunctumTheme.bone.opacity(0.92))
            .multilineTextAlignment(multiline ? .leading : .center)
            .fixedSize(horizontal: false, vertical: true)
            .lineSpacing(multiline ? 5 : 0)
            .padding(.horizontal, 16)
            .padding(.vertical, multiline ? 16 : 10)
            .background {
                let color = Color(red: 25 / 255, green: 25 / 255, blue: 25 / 255).opacity(0.86)
                if multiline { RoundedRectangle(cornerRadius: 14).fill(color) }
                else { Capsule().fill(color) }
            }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

/// Once a press travels beyond tap slop, returning to its start never makes it a tap again.
struct PressTravelGate {
    private var start = CGPoint.zero
    private(set) var isTap = false
    mutating func begin(at point: CGPoint) { start = point; isTap = true }
    mutating func move(to point: CGPoint) {
        if hypot(point.x - start.x, point.y - start.y) > 10 { isTap = false }
    }
    mutating func cancel() { isTap = false }
}
