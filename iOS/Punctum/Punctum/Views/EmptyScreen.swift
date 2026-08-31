import SwiftUI

struct EmptyScreen: View {
    let onAdd: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            SectionLabel(text: "P U N C T U M")
            Spacer().frame(height: 22)
            Text("Punctum")
                .font(PunctumTheme.georgia(44, bold: true))
                .foregroundStyle(PunctumTheme.bone)
            Spacer().frame(height: 18)
            Text("为情绪而生的画廊。\n选择一个系统图集，映射为你的第一个展厅。")
                .font(PunctumTheme.serifSC(15))
                .lineSpacing(7)
                .multilineTextAlignment(.center)
                .foregroundStyle(PunctumTheme.muted)
            Spacer().frame(height: 52)
            FrameButton(text: "选 择 系 统 图 集", action: onAdd)
            Spacer()
        }
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PunctumTheme.ink)
    }
}
