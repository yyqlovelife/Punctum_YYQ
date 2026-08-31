import SwiftUI

@main
struct PunctumApp: App {
    @StateObject private var model = GalleryViewModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
        }
    }
}
