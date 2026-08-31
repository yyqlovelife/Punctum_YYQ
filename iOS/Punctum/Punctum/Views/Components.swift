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
    @State private var isPressed = false
    @State private var isCompletingTap = false
    @State private var pressedAt: CFTimeInterval = 0

    private let minimumPressedDuration: CFTimeInterval = 0.12
    private let navigationDelay: CFTimeInterval = 0.24

    var body: some View {
        content(isPressed)
            .contentShape(Rectangle())
            .overlay {
                ImmediatePressGestureOverlay(onPhase: handleGesturePhase)
                    .allowsHitTesting(isEnabled)
            }
    }

    private func handleGesturePhase(_ phase: ImmediatePressGestureOverlay.Phase) {
        switch phase {
        case .began:
            guard isEnabled, !isCompletingTap else { return }
            pressedAt = CACurrentMediaTime()
            withAnimation(.easeOut(duration: 0.11)) {
                isPressed = true
            }

        case .ended:
            guard isEnabled, isPressed, !isCompletingTap else { return }
            isCompletingTap = true
            let elapsed = CACurrentMediaTime() - pressedAt
            let releaseDelay = max(0, minimumPressedDuration - elapsed)

            DispatchQueue.main.asyncAfter(deadline: .now() + releaseDelay) {
                withAnimation(.spring(response: 0.20, dampingFraction: 0.72)) {
                    isPressed = false
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + navigationDelay) {
                    action()
                    isCompletingTap = false
                }
            }

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
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.onPhase = onPhase
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var onPhase: (Phase) -> Void

        init(onPhase: @escaping (Phase) -> Void) {
            self.onPhase = onPhase
        }

        @objc func handlePress(_ recognizer: UILongPressGestureRecognizer) {
            switch recognizer.state {
            case .began:
                onPhase(.began)
            case .ended:
                guard let view = recognizer.view,
                      view.bounds.insetBy(dx: -4, dy: -4).contains(recognizer.location(in: view)) else {
                    onPhase(.cancelled)
                    return
                }
                onPhase(.ended)
            case .cancelled, .failed:
                onPhase(.cancelled)
            default:
                break
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

    var body: some View {
        Text(message)
            .font(PunctumTheme.serifSC(fontSize))
            .foregroundStyle(PunctumTheme.bone.opacity(0.92))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                Color(red: 25 / 255, green: 25 / 255, blue: 25 / 255).opacity(0.86),
                in: Capsule()
            )
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
