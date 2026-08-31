import CoreText
import SwiftUI
import UIKit

struct SwitcherScreen: View {
    let galleries: [PunctumGallery]
    let overviews: [String: GalleryOverview]
    let subtitle: String
    let invitationStyle: InvitationCardStyle
    var scrollToGalleryID: Binding<String?> = .constant(nil)
    let onSelect: (String) -> Void
    let onAdd: () -> Void
    let onToggleStyle: () -> Void
    let onMove: (Int, Int) -> Void
    let onDelete: (Int) -> Void

    @State private var showSort = false

    var body: some View {
        GeometryReader { geometry in
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    SectionLabel(text: "SELECT EXHIBITION")
                    Spacer()
                    HStack(spacing: 10) {
                        headerIconButton(
                            "rectangle.3.group",
                            size: CGSize(width: 24, height: 18),
                            label: "切换邀请卡风格",
                            action: onToggleStyle
                        )
                        headerIconButton(
                            "line.3.horizontal.decrease",
                            size: CGSize(width: 22, height: 16),
                            label: "调整排序"
                        ) { showSort = true }
                    }
                }
                .foregroundStyle(PunctumTheme.muted)
                .frame(height: 44)
                .padding(.leading, 24)
                .padding(.trailing, 12)
                .padding(.top, geometry.safeAreaInsets.top + 16)

                Text("Your Punctums")
                    .font(PunctumTheme.georgia(titleSize(for: geometry.size.width), bold: true))
                    .foregroundStyle(PunctumTheme.bone)
                    .lineLimit(1)
                    .padding(.horizontal, 24)
                    .padding(.top, 16)

                Text(subtitle)
                    .font(PunctumTheme.serifSC(15))
                    .tracking(0.1)
                    .foregroundStyle(PunctumTheme.muted)
                    .lineLimit(1)
                    .padding(.horizontal, 24)
                    .padding(.top, 10)

                ZStack {
                    PostcardList(
                        galleries: galleries,
                        overviews: overviews,
                        availableWidth: geometry.size.width,
                        isActive: invitationStyle == .postcard,
                        scrollToGalleryID: scrollToGalleryID,
                        onSelect: onSelect
                    )
                    .opacity(invitationStyle == .postcard ? 1 : 0)
                    .allowsHitTesting(invitationStyle == .postcard)

                    TicketList(
                        galleries: galleries,
                        overviews: overviews,
                        isActive: invitationStyle == .ticket,
                        scrollToGalleryID: scrollToGalleryID,
                        onSelect: onSelect
                    )
                    .opacity(invitationStyle == .ticket ? 1 : 0)
                    .allowsHitTesting(invitationStyle == .ticket)

                    ReversalFilmGrid(
                        galleries: galleries,
                        overviews: overviews,
                        availableWidth: geometry.size.width,
                        isActive: invitationStyle == .reversalFilm,
                        scrollToGalleryID: scrollToGalleryID,
                        onSelect: onSelect
                    )
                    .opacity(invitationStyle == .reversalFilm ? 1 : 0)
                    .allowsHitTesting(invitationStyle == .reversalFilm)
                }
                .padding(.top, 26)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(PunctumTheme.ink)
            .ignoresSafeArea(edges: .top)
        }
        .sheet(isPresented: $showSort) {
            SortGalleriesSheet(
                galleries: galleries,
                onMove: onMove,
                onDelete: onDelete,
                onAdd: {
                    showSort = false
                    onAdd()
                }
            )
            .presentationDetents([.medium, .large])
            .presentationBackground(PunctumTheme.ink)
        }
    }

    private func titleSize(for width: CGFloat) -> CGFloat {
        switch width {
        case ..<330: return 34
        case ..<370: return 37
        case ..<420: return 40
        default: return 44
        }
    }

    private func headerIconButton(
        _ systemName: String,
        size: CGSize,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .resizable()
                .scaledToFit()
                .frame(width: size.width, height: size.height)
                .frame(height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(IconPressButtonStyle())
        .accessibilityLabel(label)
    }
}

private struct PostcardList: View {
    let galleries: [PunctumGallery]
    let overviews: [String: GalleryOverview]
    let availableWidth: CGFloat
    var isActive = true
    var scrollToGalleryID: Binding<String?>
    let onSelect: (String) -> Void

    var body: some View {
        ScrollViewReader { reader in
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 18) {
                    ForEach(galleries) { gallery in
                        PostcardInvitationCard(
                            overview: overviews[gallery.id] ?? emptyOverview(gallery),
                            width: availableWidth * 0.78
                        ) { onSelect(gallery.id) }
                        .id(gallery.id)
                    }
                }
                .padding(.leading, 24)
                .padding(.trailing, 70)
                .padding(.bottom, 28)
            }
            .onTapGesture(count: 2) {
                if let first = galleries.first {
                    withAnimation { reader.scrollTo(first.id, anchor: .leading) }
                }
            }
            .onChange(of: scrollToGalleryID.wrappedValue) { _, id in
                guard isActive, let id else { return }
                DispatchQueue.main.async {
                    withAnimation { reader.scrollTo(id, anchor: .leading) }
                    scrollToGalleryID.wrappedValue = nil
                }
            }
        }
    }
}

private struct TicketList: View {
    let galleries: [PunctumGallery]
    let overviews: [String: GalleryOverview]
    var isActive = true
    var scrollToGalleryID: Binding<String?>
    let onSelect: (String) -> Void

    var body: some View {
        ScrollViewReader { reader in
            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 18) {
                    ForEach(galleries) { gallery in
                        TicketInvitationCard(
                            overview: overviews[gallery.id] ?? emptyOverview(gallery)
                        ) { onSelect(gallery.id) }
                        .id(gallery.id)
                    }
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 28)
            }
            .onTapGesture(count: 2) {
                if let first = galleries.first {
                    withAnimation { reader.scrollTo(first.id, anchor: .top) }
                }
            }
            .onChange(of: scrollToGalleryID.wrappedValue) { _, id in
                guard isActive, let id else { return }
                DispatchQueue.main.async {
                    withAnimation { reader.scrollTo(id, anchor: .top) }
                    scrollToGalleryID.wrappedValue = nil
                }
            }
        }
    }
}

private struct ReversalFilmGrid: View {
    let galleries: [PunctumGallery]
    let overviews: [String: GalleryOverview]
    let availableWidth: CGFloat
    var isActive = true
    var scrollToGalleryID: Binding<String?>
    let onSelect: (String) -> Void

    private var visualScale: CGFloat {
        let baselinePadding: CGFloat = 16
        let baselineGap: CGFloat = 10
        let baselineCardWidth = (availableWidth - baselinePadding * 2 - baselineGap * 2) / 3
        let denominator = baselineCardWidth * 2 + baselineGap + baselinePadding * 2
        guard denominator > 0 else { return 1 }
        return availableWidth / denominator
    }

    var body: some View {
        let padding = 16 * visualScale
        let gap = 10 * visualScale
        let cardWidth = max((availableWidth - padding * 2 - gap) / 2, 0)
        ScrollViewReader { reader in
            ScrollView(showsIndicators: false) {
                LazyVGrid(
                    columns: [
                        GridItem(.flexible(), spacing: gap),
                        GridItem(.flexible(), spacing: gap),
                    ],
                    spacing: 11 * visualScale
                ) {
                    ForEach(galleries) { gallery in
                        ReversalFilmCard(
                            overview: overviews[gallery.id] ?? emptyOverview(gallery),
                            visualScale: visualScale,
                            cardWidth: cardWidth
                        ) { onSelect(gallery.id) }
                        .id(gallery.id)
                    }
                }
                .padding(.horizontal, padding)
                .padding(.bottom, 28 * visualScale)
            }
            .onTapGesture(count: 2) {
                if let first = galleries.first {
                    withAnimation { reader.scrollTo(first.id, anchor: .top) }
                }
            }
            .onChange(of: scrollToGalleryID.wrappedValue) { _, id in
                guard isActive, let id else { return }
                DispatchQueue.main.async {
                    withAnimation { reader.scrollTo(id, anchor: .top) }
                    scrollToGalleryID.wrappedValue = nil
                }
            }
        }
    }
}

private struct ReversalFilmCard: View {
    let overview: GalleryOverview
    let visualScale: CGFloat
    let cardWidth: CGFloat
    let action: () -> Void

    private let ink = Color(red: 41 / 255, green: 39 / 255, blue: 34 / 255)
    private let muted = Color(red: 117 / 255, green: 110 / 255, blue: 98 / 255)
    private var titleIsChinese: Bool { overview.gallery.displayName.containsChinese }
    private var titleSize: CGFloat {
        (titleIsChinese ? 11.5 : 13) * visualScale - 2
    }
    private var coverWidth: CGFloat {
        max(cardWidth - 28 * visualScale, 0)
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                Color.white
                ReversalFilmPaperTexture()
                VStack(spacing: 0) {
                    GalleryTitleText(
                        text: overview.gallery.displayName,
                        size: titleSize,
                        color: ink,
                        alignment: .center,
                        textAlignment: .center
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    .padding(.horizontal, 5 * visualScale)
                    .offset(y: titleIsChinese ? 0 : 3 * visualScale)

                    Color.clear
                        .frame(width: coverWidth, height: coverWidth / 1.5)
                        .overlay {
                            ReversalFilmCover(overview: overview, visualScale: visualScale)
                        }
                        .clipped()
                        .frame(maxWidth: .infinity)

                    VStack(spacing: 0.5 * visualScale) {
                        Text(PunctumFormatting.reversalFilmSpan(overview.timeSpan))
                            .font(PunctumTheme.newsreader(6.5 * visualScale - 1, bold: true))
                            .foregroundStyle(muted)
                            .lineLimit(1)
                        Text("SLIDE · DIAPOSITIVE")
                            .font(PunctumTheme.georgia(5 * visualScale - 1, bold: true))
                            .tracking(0.45 * visualScale)
                            .foregroundStyle(muted)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 2 * visualScale, style: .continuous))
            .clipped()
            .shadow(color: .black.opacity(0.28), radius: 5 * visualScale, y: 2 * visualScale)
            .frame(width: cardWidth, height: cardWidth)
            .clipped()
        }
        .buttonStyle(InvitationCardButtonStyle())
        .accessibilityIdentifier("gallery-card-\(overview.gallery.id)")
        .accessibilityValue(overview.covers.first?.id ?? "")
    }
}

private struct ReversalFilmPaperTexture: View {
    var body: some View {
        PaperTextureFill(name: "reversal_film_paper_texture")
    }
}

private struct ReversalFilmCover: View {
    let overview: GalleryOverview
    let visualScale: CGFloat

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            ZStack {
                Color(red: 21 / 255, green: 20 / 255, blue: 18 / 255)
                coverImage(in: size)
                VStack(spacing: 0) {
                    LinearGradient(
                        colors: [
                            .black.opacity(0.70),
                            .black.opacity(0.32),
                            .black.opacity(0.10),
                            .clear,
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: 8 * visualScale)
                    Spacer(minLength: 0)
                    LinearGradient(
                        colors: [
                            .clear,
                            .black.opacity(0.07),
                            .black.opacity(0.30),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: 6 * visualScale)
                }
                HStack(spacing: 0) {
                    LinearGradient(
                        colors: [
                            .black.opacity(0.62),
                            .black.opacity(0.28),
                            .black.opacity(0.09),
                            .clear,
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: 6 * visualScale)
                    Spacer(minLength: 0)
                    LinearGradient(
                        colors: [
                            .clear,
                            .black.opacity(0.06),
                            .black.opacity(0.26),
                        ],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: 6 * visualScale)
                }
            }
            .frame(width: size.width, height: size.height)
            .clipped()
        }
        .clipShape(RoundedRectangle(cornerRadius: 2 * visualScale, style: .continuous))
    }

    @ViewBuilder
    private func coverImage(in size: CGSize) -> some View {
        if let image = overview.ticketCoverPath.flatMap(UIImage.init(contentsOfFile:)) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: size.width, height: size.height)
                .clipped()
        } else if let latest = overview.covers.first {
            PhotoAssetImage(photo: latest, targetSize: CGSize(width: 900, height: 600), contentMode: .fill)
                .frame(width: size.width, height: size.height)
                .clipped()
        } else {
            Color(red: 54 / 255, green: 52 / 255, blue: 47 / 255)
                .frame(width: size.width, height: size.height)
        }
    }
}

private struct PostcardInvitationCard: View {
    let overview: GalleryOverview
    let width: CGFloat
    let action: () -> Void

    private let paper = Color(red: 232 / 255, green: 222 / 255, blue: 204 / 255)
    private let textColor = Color(red: 36 / 255, green: 33 / 255, blue: 29 / 255)
    private var collageSide: CGFloat { max(width - 44, 0) }

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .top) {
                paper
                PostcardPunctumsWatermark(size: CGSize(width: width, height: width + 128))
                    .frame(width: width, height: width + 128)
                    .allowsHitTesting(false)
                VStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 0) {
                    Color.clear
                        .frame(width: collageSide, height: collageSide)
                        .overlay {
                            CoverCollage(covers: overview.covers, coverPath: overview.postcardCoverPath)
                        }
                        .clipped()
                    Color.clear.frame(height: 22)
                    VStack(alignment: .leading, spacing: 0) {
                        let dateLine = PunctumFormatting.compactSpan(overview.timeSpan)
                        Text("关于 \(overview.count) 幅作品的故事")
                            .font(PunctumTheme.serifSC(12))
                            .fontWeight(.regular)
                            .frame(height: 16, alignment: .center)
                        Text(dateLine.isEmpty ? "时间未知" : dateLine)
                            .font(dateLine.isEmpty ? PunctumTheme.serifSC(12) : PunctumTheme.newsreader(12))
                            .fontWeight(.regular)
                            .frame(height: 16, alignment: .center)
                    }
                    .foregroundStyle(textColor)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(height: 40, alignment: .topLeading)
                    Color.clear.frame(height: 22)
                    Color.clear
                        .frame(height: 50)
                        .overlay(alignment: .leading) {
                            GalleryTitleText(
                                text: overview.gallery.displayName,
                                size: 32,
                                color: textColor
                            )
                        }
                    Color.clear.frame(height: 10)
                }
                .padding(.horizontal, 22)
                .padding(.top, 28)

                    PostcardFooter()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .frame(width: width, height: width * 1.70, alignment: .top)
            .overlay(Rectangle().stroke(Color.black.opacity(0.12), lineWidth: 1))
            .clipped()
        }
        .buttonStyle(InvitationCardButtonStyle())
        .accessibilityIdentifier("gallery-card-\(overview.gallery.id)")
        .accessibilityValue(overview.covers.first?.id ?? "")
    }
}

private struct PostcardFooter: View {
    var body: some View {
        ZStack {
            PaperTextureFill(
                name: "postcard_footer_paper_texture",
                fallback: Color(red: 196 / 255, green: 168 / 255, blue: 132 / 255)
            )
            VStack(alignment: .leading, spacing: 2) {
                Text("MOMENT · PUNCTUM · STUDIUM")
                    .font(PunctumTheme.georgia(9, bold: true))
                    .tracking(1.5)
                    .foregroundStyle(Color.black.opacity(0.52))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text("TAP TO ENTER EXHIBITION")
                    .font(PunctumTheme.georgia(9, bold: true))
                    .tracking(1.5)
                    .foregroundStyle(Color.black.opacity(0.34))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .padding(.horizontal, 22)
            .padding(.vertical, 7)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
    }
}

private struct PostcardPunctumsWatermark: View {
    let size: CGSize

    var body: some View {
        PostcardPunctumsInk()
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)
    }
}

/// 按安卓 Paint.drawText 的基线来画：右边距 = P 到牛皮纸顶的距离。
private struct PostcardPunctumsInk: View {
    var body: some View {
        Canvas { context, canvasSize in
            context.withCGContext { cg in
                drawPostcardPunctums(in: cg, size: canvasSize)
            }
        }
        .allowsHitTesting(false)
    }
}

private func drawPostcardPunctums(in ctx: CGContext, size: CGSize) {
    let characters = Array("PUNCTUMS")
    let font = UIFont.systemFont(ofSize: 68, weight: .black)
    let color = UIColor(red: 111 / 255, green: 98 / 255, blue: 81 / 255, alpha: 0.065)
    let attributes: [NSAttributedString.Key: Any] = [
        .font: font,
        .foregroundColor: color,
    ]
    let unscaled = characters.map {
        CTLineGetTypographicBounds(
            CTLineCreateWithAttributedString(NSAttributedString(string: String($0), attributes: attributes)),
            nil, nil, nil
        )
    }.map { CGFloat($0) }
    var scaleX: CGFloat = 1.18
    var natural = unscaled.reduce(0, +) * scaleX
    // 系统黑体基线外侧还有空白：把字形光学边缘钉在 4pt 边距上。
    let visualInset: CGFloat = 4
    let rightOpticalPad: CGFloat = 0.4
    let startOpticalPad: CGFloat = 3.7
    let target = max(size.height - visualInset * 2, 1)
    if natural > target, natural > 0 {
        scaleX *= target / natural
        natural = unscaled.reduce(0, +) * scaleX
    }
    let gap = characters.count > 1 ? max((target - natural) / CGFloat(characters.count - 1), 0) : 0

    ctx.saveGState()
    ctx.setShouldAntialias(true)
    ctx.translateBy(
        x: size.width - visualInset + rightOpticalPad,
        y: size.height - visualInset + startOpticalPad
    )
    ctx.rotate(by: -.pi / 2)
    var cursor: CGFloat = 0
    for (index, character) in characters.enumerated() {
        let line = CTLineCreateWithAttributedString(
            NSAttributedString(string: String(character), attributes: attributes)
        )
        ctx.saveGState()
        ctx.translateBy(x: cursor, y: 0)
        ctx.scaleBy(x: scaleX, y: -1)
        ctx.textMatrix = .identity
        ctx.textPosition = .zero
        CTLineDraw(line, ctx)
        ctx.restoreGState()
        cursor += unscaled[index] * scaleX + gap
    }
    ctx.restoreGState()
}

private struct CoverCollage: View {
    let covers: [PhotoItem]
    let coverPath: String?

    var body: some View {
        ZStack {
            Color(red: 21 / 255, green: 17 / 255, blue: 14 / 255)
            Group {
                if let image = coverPath.flatMap(UIImage.init(contentsOfFile:)) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                } else if covers.count >= 4 {
                    GeometryReader { geometry in
                        let cellSize = max((min(geometry.size.width, geometry.size.height) - 10) / 2, 0)
                        VStack(spacing: 10) {
                            HStack(spacing: 10) {
                                image(covers[0], size: cellSize)
                                image(covers[1], size: cellSize)
                            }
                            HStack(spacing: 10) {
                                image(covers[2], size: cellSize)
                                image(covers[3], size: cellSize)
                            }
                        }
                        .frame(width: geometry.size.width, height: geometry.size.height, alignment: .center)
                    }
                } else if let first = covers.first {
                    image(first)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    Color.clear
                }
            }
            .padding(12)
        }
        .clipped()
    }

    @ViewBuilder
    private func image(_ photo: PhotoItem, size: CGFloat? = nil) -> some View {
        PhotoAssetImage(photo: photo, targetSize: CGSize(width: 700, height: 700), contentMode: .fill)
            .frame(
                minWidth: size,
                maxWidth: size ?? .infinity,
                minHeight: size,
                maxHeight: size ?? .infinity
            )
            .clipped()
    }
}

private struct TicketInvitationCard: View {
    let overview: GalleryOverview
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            GeometryReader { geometry in
                let ticketWidth = geometry.size.width
                let rightWidth: CGFloat = ticketWidth < 340 ? 72 : 78
                let cutLineOffset: CGFloat = 4
                let imageWidth = geometry.size.width * 0.39
                ZStack(alignment: .leading) {
                    Color(red: 1, green: 245 / 255, blue: 230 / 255)
                    PaperTextureFill(name: "ticket_paper_texture")
                        .frame(
                            width: ticketWidth - rightWidth + cutLineOffset,
                            height: geometry.size.height
                        )
                        .clipped()

                    HStack(spacing: 0) {
                        VStack(alignment: .leading, spacing: 0) {
                            GalleryTitleText(
                                text: overview.gallery.displayName,
                                size: overview.gallery.displayName.containsChinese ? 22 : 20,
                                color: Color(red: 5 / 255, green: 5 / 255, blue: 5 / 255),
                                maxLines: 2,
                                minimumScaleFactor: 0.82,
                                lineSpacing: 3
                            )
                            Spacer(minLength: 0)
                            TicketInfoBand(
                                story: "关于 \(overview.count) 幅作品的故事",
                                time: PunctumFormatting.compactSpan(overview.timeSpan).isEmpty
                                    ? "Time unknown"
                                    : PunctumFormatting.compactSpan(overview.timeSpan)
                            )
                        }
                        .padding(.leading, 12)
                        .padding(.vertical, 14)
                        .padding(.trailing, 8)
                        .frame(width: ticketWidth - imageWidth - rightWidth, alignment: .leading)

                        ZStack(alignment: .leading) {
                            TicketImageStrip(
                                covers: overview.covers,
                                coverPath: overview.ticketCoverPath
                            )
                            .frame(width: max(imageWidth - 6, 0), height: 92)
                            .offset(x: -2)
                        }
                        .frame(width: imageWidth, height: geometry.size.height)

                        TicketStub(
                            seed: ticketNumber,
                            marker: ticketMarker,
                            color: overview.ticketDominantColorARGB.map(Color.init(argb:))
                                ?? Color(red: 87 / 255, green: 83 / 255, blue: 78 / 255),
                            cutLineOffset: cutLineOffset
                        )
                        .frame(width: rightWidth, height: geometry.size.height)
                    }
                }
                .clipped()
            }
            .frame(height: 116)
        }
        .buttonStyle(InvitationCardButtonStyle())
        .accessibilityIdentifier("gallery-card-\(overview.gallery.id)")
        .accessibilityValue(overview.covers.first?.id ?? "")
    }

    private var ticketNumber: String {
        var hash: UInt32 = 2_166_136_261
        for byte in overview.gallery.id.utf8 {
            hash ^= UInt32(byte)
            hash = hash &* 16_777_619
        }
        return String(format: "%07d - 0101110", Int(hash % 10_000_000))
    }

    private var ticketMarker: String {
        var hash: UInt32 = 2_166_136_261
        for byte in overview.gallery.id.utf8 {
            hash ^= UInt32(byte)
            hash = hash &* 16_777_619
        }
        return String((hash % 90) + 10)
    }
}

private struct TicketInfoBand: View {
    let story: String
    let time: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle()
                .fill(Color.black.opacity(0.48))
                .frame(height: 0.7)
                .frame(maxWidth: .infinity)
                .scaleEffect(x: 0.92, anchor: .leading)
            Spacer().frame(height: 1)
            Text(story)
                .font(PunctumTheme.serifSC(9))
                .fontWeight(.regular)
                .foregroundStyle(Color(red: 5 / 255, green: 5 / 255, blue: 5 / 255))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .frame(height: 10, alignment: .center)
            Spacer().frame(height: 3)
            Text(time)
                .font(PunctumTheme.newsreader(9))
                .fontWeight(.regular)
                .foregroundStyle(Color(red: 5 / 255, green: 5 / 255, blue: 5 / 255))
                .lineLimit(1)
                .minimumScaleFactor(0.75)
                .frame(height: 9, alignment: .center)
            Spacer().frame(height: 1)
            Rectangle()
                .fill(Color.black.opacity(0.48))
                .frame(height: 0.7)
                .frame(maxWidth: .infinity)
                .scaleEffect(x: 0.92, anchor: .leading)
        }
    }
}

private struct TicketImageStrip: View {
    let covers: [PhotoItem]
    let coverPath: String?

    var body: some View {
        GeometryReader { geometry in
            let innerWidth = max(geometry.size.width - 16, 0)
            let innerHeight = max(geometry.size.height - 16, 0)
            let photoWidth = max(innerWidth - 2, 0)
            let photoHeight = max(innerHeight - 2, 0)

            ZStack {
                Color.black
                ZStack {
                    Color(red: 5 / 255, green: 5 / 255, blue: 5 / 255)
                    Group {
                        if let image = coverPath.flatMap(UIImage.init(contentsOfFile:)) {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFill()
                        } else if let latestPhoto = covers.first {
                            PhotoAssetImage(
                                photo: latestPhoto,
                                targetSize: CGSize(width: 700, height: 700),
                                contentMode: .fill
                            )
                        }
                    }
                    .frame(width: photoWidth, height: photoHeight)
                    .clipped()

                    VStack(spacing: 0) {
                        LinearGradient(
                            colors: [.black.opacity(0.52), .clear],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 5)
                        Spacer(minLength: 0)
                        LinearGradient(
                            colors: [.clear, .white.opacity(0.14)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 2)
                    }
                    HStack(spacing: 0) {
                        LinearGradient(
                            colors: [.black.opacity(0.38), .clear],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                        .frame(width: 4)
                        Spacer(minLength: 0)
                        LinearGradient(
                            colors: [.clear, .white.opacity(0.10)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                        .frame(width: 2)
                    }
                }
                .frame(width: innerWidth, height: innerHeight)
                .clipped()
            }
        }
        .clipped()
    }
}

private struct TicketStub: View {
    let seed: String
    let marker: String
    let color: Color
    let cutLineOffset: CGFloat

    var body: some View {
        GeometryReader { geometry in
            let horizontalScale = min(max(geometry.size.width / 78, 0.88), 1)
            let labelWidth = 14 * horizontalScale
            let barcodeWidth = 28 * horizontalScale
            ZStack(alignment: .topLeading) {
                HStack(spacing: 0) {
                    Color.clear.frame(width: cutLineOffset)
                    color
                }
                DashedCutLine()
                    .frame(width: 2, height: 96)
                    .position(x: cutLineOffset, y: geometry.size.height / 2)
                TicketNotch()
                    .frame(width: 10, height: 10)
                    .offset(x: -1)
                TicketNotch()
                    .frame(width: 10, height: 10)
                    .rotationEffect(.degrees(180))
                    .offset(x: -1, y: geometry.size.height - 10)
                TicketStubLabels(marker: marker)
                    .frame(width: labelWidth, height: 96)
                    .position(x: 16 * horizontalScale, y: geometry.size.height / 2)
                Barcode(seed: seed)
                    .frame(width: barcodeWidth, height: 96)
                    .position(x: 41 * horizontalScale, y: geometry.size.height / 2)
                TicketSerialText(text: seed)
                    .frame(width: labelWidth, height: 96)
                    .position(
                        x: geometry.size.width - 11 * horizontalScale,
                        y: geometry.size.height / 2
                    )
                    .offset(y: -1)
            }
        }
    }
}

private struct TicketStubLabels: View {
    let marker: String

    var body: some View {
        GeometryReader { geometry in
            Text(marker)
                .font(PunctumTheme.georgia(7, bold: true))
                .tracking(0.4)
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize()
                .rotationEffect(.degrees(-90))
                .position(x: geometry.size.width / 2, y: 6)
            Text("CAPTURE")
                .font(PunctumTheme.georgia(6.5, bold: true))
                .tracking(0.2)
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize()
                .rotationEffect(.degrees(-90))
                .position(x: geometry.size.width / 2, y: geometry.size.height - 23)
        }
    }
}

private struct DashedCutLine: View {
    var body: some View {
        Canvas { context, size in
            var y: CGFloat = 0
            while y < size.height {
                let end = min(y + 8, size.height)
                var path = Path()
                path.move(to: CGPoint(x: size.width / 2, y: y))
                path.addLine(to: CGPoint(x: size.width / 2, y: end))
                context.stroke(
                    path,
                    with: .color(.white.opacity(0.82)),
                    style: StrokeStyle(lineWidth: 1.3, lineCap: .square)
                )
                y += 14
            }
        }
    }
}

private struct Barcode: View {
    let seed: String

    var body: some View {
        Canvas { context, size in
            let digits = seed.compactMap(\.wholeNumberValue)
            let fallback = [0, 1, 0, 1, 1, 1, 0]
            let values = digits.isEmpty ? fallback : digits
            let module: CGFloat = 0.72
            let quietZone: CGFloat = 2
            let maxY = size.height - quietZone
            var y = quietZone
            var index = 0
            while y < maxY {
                let digit = values[index % values.count]
                let barModules = 1 + ((digit + index) % 4)
                let spaceModules = 1 + ((digit * 3 + index) % 3)
                let barHeight = min(CGFloat(barModules) * module, maxY - y)
                context.fill(
                    Path(CGRect(x: 0, y: y, width: size.width, height: barHeight)),
                    with: .color(.white)
                )
                y += barHeight + CGFloat(spaceModules) * module
                index += 1
            }
        }
    }
}

private struct TicketSerialText: View {
    let text: String
    private var digits: [Character] { Array(text.filter(\.isNumber).prefix(12)) }

    var body: some View {
        VStack(spacing: 0) {
            ForEach(digits.indices, id: \.self) { index in
                Text(String(digits[index]))
                    .font(PunctumTheme.georgia(6.5))
                    .foregroundStyle(.white)
                    .rotationEffect(.degrees(-90))
                if index < digits.count - 1 {
                    Spacer(minLength: 0)
                }
            }
        }
        .clipped()
    }
}

private struct TicketNotch: View {
    var body: some View {
        Canvas { context, size in
            let radius = size.width / 2
            let circle = Path(ellipseIn: CGRect(x: 0, y: -radius, width: size.width, height: size.width))
            context.fill(circle, with: .color(PunctumTheme.ink))
        }
    }
}

private struct SortGalleriesSheet: View {
    @Environment(\.dismiss) private var dismiss
    let galleries: [PunctumGallery]
    let onMove: (Int, Int) -> Void
    let onDelete: (Int) -> Void
    let onAdd: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("调整图集画廊")
                    .font(PunctumTheme.serifSC(20, weight: .medium))
                    .foregroundStyle(PunctumTheme.bone)
                Spacer()
                Button("完成", action: dismiss.callAsFunction)
                    .font(PunctumTheme.serifSC(15))
                    .foregroundStyle(PunctumTheme.gold)
            }
            .padding(.horizontal, 20)
            .padding(.top, 24)

            ScrollView {
                VStack(spacing: 0) {
                    ForEach(Array(galleries.enumerated()), id: \.element.id) { index, gallery in
                        HStack {
                            Text(gallery.displayName)
                                .font(PunctumTheme.galleryListName(gallery.displayName, size: 15))
                                .fontWeight(.regular)
                                .foregroundStyle(PunctumTheme.bone)
                                .lineLimit(1)
                            Spacer()
                            iconButton("chevron.up", enabled: index > 0) { onMove(index, index - 1) }
                            iconButton("chevron.down", enabled: index < galleries.count - 1) { onMove(index, index + 1) }
                            iconButton("trash", enabled: true) { onDelete(index) }
                        }
                        .padding(.horizontal, 20)
                        .frame(height: 54)
                        HairlineDivider().padding(.leading, 20)
                    }
                    Button(action: onAdd) {
                        Label("添加图集画廊", systemImage: "plus")
                            .font(PunctumTheme.serifSC(15))
                            .foregroundStyle(PunctumTheme.gold)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(20)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .background(PunctumTheme.ink.ignoresSafeArea())
    }

    private func iconButton(_ name: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .frame(width: 36, height: 36)
                .foregroundStyle(enabled ? PunctumTheme.muted : PunctumTheme.muted.opacity(0.25))
        }
        .disabled(!enabled)
        .buttonStyle(.plain)
    }
}

private func emptyOverview(_ gallery: PunctumGallery) -> GalleryOverview {
    GalleryOverview(gallery: gallery, count: 0, timeSpan: "", covers: [])
}
