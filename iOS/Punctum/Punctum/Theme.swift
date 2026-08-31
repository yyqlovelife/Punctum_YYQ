import SwiftUI
import UIKit

enum PunctumTheme {
    static let ink = Color(red: 10 / 255, green: 10 / 255, blue: 10 / 255)
    static let surface = Color(red: 20 / 255, green: 20 / 255, blue: 20 / 255)
    static let bone = Color(red: 237 / 255, green: 232 / 255, blue: 221 / 255)
    static let muted = Color(red: 138 / 255, green: 129 / 255, blue: 112 / 255)
    static let gold = Color(red: 200 / 255, green: 162 / 255, blue: 75 / 255)
    static let hairline = Color(red: 42 / 255, green: 42 / 255, blue: 40 / 255)

    static func newsreader(_ size: CGFloat, bold: Bool = false) -> Font {
        Font(newsreaderUIFont(size, bold: bold))
    }

    static func georgia(_ size: CGFloat, bold: Bool = false) -> Font {
        newsreader(size, bold: bold)
    }

    static func serifSC(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        Font(serifSCUIFont(size, weight: weight))
    }

    static func galleryTitle(_ text: String, size: CGFloat) -> Font {
        Font(galleryTitleUIFont(text, size: size))
    }

    /// Cover titles: English Newsreader SemiBold. Chinese Noto SemiBold
    /// (two steps up from Regular) without extra stroke or synthetic bold.
    static func galleryTitleUIFont(_ text: String, size: CGFloat) -> UIFont {
        let name = text.containsChinese ? "NotoSerifSC-SemiBold" : "Newsreader16pt-SemiBold"
        return uiFont(named: name, size: size)
    }

    static func newsreaderUIFont(_ size: CGFloat, bold: Bool = false) -> UIFont {
        uiFont(named: bold ? "Newsreader16pt-SemiBold" : "Newsreader16pt-Regular", size: size)
    }

    static func serifSCUIFont(_ size: CGFloat, weight: Font.Weight = .regular) -> UIFont {
        let name: String
        switch weight {
        case .semibold, .bold, .heavy, .black:
            name = "NotoSerifSC-SemiBold"
        case .medium:
            name = "NotoSerifSC-Medium"
        default:
            name = "NotoSerifSC-Regular"
        }
        return uiFont(named: name, size: size)
    }

    static func uiFont(named name: String, size: CGFloat) -> UIFont {
        UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
    }

    /// 选择图集等列表：只换中英字体，保持 Regular，对齐安卓 bodyMedium。
    static func galleryListName(_ text: String, size: CGFloat = 15) -> Font {
        text.containsChinese ? serifSC(size) : newsreader(size)
    }
}

extension String {
    var containsChinese: Bool {
        unicodeScalars.contains { scalar in
            (0x4E00...0x9FFF).contains(Int(scalar.value))
        }
    }
}

extension Color {
    init(argb: UInt32) {
        self.init(
            .sRGB,
            red: Double((argb >> 16) & 0xFF) / 255,
            green: Double((argb >> 8) & 0xFF) / 255,
            blue: Double(argb & 0xFF) / 255,
            opacity: Double((argb >> 24) & 0xFF) / 255
        )
    }
}

enum PaperTexture {
    static func image(_ name: String) -> UIImage? {
        if let image = UIImage(named: name) { return image }
        let url = Bundle.main.url(forResource: name, withExtension: "jpg")
            ?? Bundle.main.url(forResource: name, withExtension: "jpeg")
        guard let url else { return nil }
        return UIImage(contentsOfFile: url.path)
    }
}

struct PaperTextureFill: View {
    let name: String
    var fallback: Color? = nil

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let fallback {
                    fallback
                }
                if let texture = PaperTexture.image(name) {
                    Image(uiImage: texture)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                }
            }
        }
        .allowsHitTesting(false)
    }
}
