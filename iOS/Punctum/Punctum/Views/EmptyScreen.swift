import SwiftUI

struct EmptyScreen: View {
    let onAdd: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            SectionLabel(text: "P U N C T U M")
            Spacer().frame(height: 22)
            Text("Select Exhibition")
                .font(PunctumTheme.georgia(44, bold: true))
                .multilineTextAlignment(.center)
                .foregroundStyle(PunctumTheme.bone)
            Spacer().frame(height: 18)
            Text("选择一个图集，作为你的第一个画廊\n让每一次回望，都重新感受影像的重量")
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
