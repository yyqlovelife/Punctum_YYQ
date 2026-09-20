import Photos
import SwiftUI

struct ComparisonScreen: View {
    let session: ComparisonSession
    let onClose: () -> Void
    let onDelete: (ComparisonPhoto) async throws -> Void
    @State private var comparison = ComparisonState()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var deleteIndex: Int?
    @State private var deleting = false
    @State private var errorMessage: String?
    @State private var accessTarget: Int?
    @State private var showAccessPicker = false

    private var items: [ComparisonPhoto] { [ComparisonPhoto(photo: session.original), session.selected] }
    private var layout: ComparisonState.Layout { ComparisonState.layout(first: items[0].size, second: items[1].size) }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onClose) {
                    Image(systemName: "arrow.left").font(.system(size: 23, weight: .medium))
                        .frame(width: 56, height: 56).contentShape(Rectangle())
                }
                .accessibilityLabel("返回大图")
                Spacer()
                Text("对比").font(PunctumTheme.serifSC(17))
                Spacer()
                Button { comparison.toggleLink() } label: {
                    Image(systemName: comparison.linked ? "link.circle.fill" : "link")
                        .font(.system(size: 24, weight: .medium))
                        .foregroundStyle(comparison.linked ? PunctumTheme.gold : PunctumTheme.bone)
                        .frame(width: 56, height: 56).contentShape(Rectangle())
                }
                .accessibilityLabel(comparison.linked ? "关闭联动缩放" : "开启联动缩放")
                .accessibilityValue(comparison.linked ? "已开启" : "已关闭")
                .accessibilityIdentifier("comparison-link")
            }
            .padding(.horizontal, 4)
            .buttonStyle(IconPressButtonStyle())
            .disabled(deleting)

            GeometryReader { geometry in
                if layout == .columns {
                    HStack(spacing: 1) {
                        tile(0).frame(width: (geometry.size.width - 1) / 2)
                        tile(1).frame(width: (geometry.size.width - 1) / 2)
                    }
                } else {
                    VStack(spacing: 1) {
                        tile(0).frame(height: (geometry.size.height - 1) / 2)
                        tile(1).frame(height: (geometry.size.height - 1) / 2)
                    }
                }
            }
            .background(PunctumTheme.bone.opacity(0.16))
            Text(comparison.linked ? "联动缩放" : "独立缩放")
                .font(PunctumTheme.serifSC(11))
                .foregroundStyle(comparison.linked ? PunctumTheme.gold : PunctumTheme.bone.opacity(0.5))
                .frame(height: 30)
        }
        .foregroundStyle(PunctumTheme.bone)
        .background(Color.black.ignoresSafeArea())
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .interactiveDismissDisabled()
        .overlay {
            if let index = deleteIndex {
                PunctumDialogBackdrop()
                VStack(spacing: 20) {
                    Text("删除这张照片？").font(PunctumTheme.serifSC(19))
                    Text("照片将移入系统相册的最近删除。")
                        .font(PunctumTheme.serifSC(13)).foregroundStyle(PunctumTheme.bone.opacity(0.65))
                    HStack(spacing: 16) {
                        Button { deleteIndex = nil } label: {
                            Text("取消").frame(maxWidth: .infinity, minHeight: 48)
                        }
                        Button {
                            delete(index)
                        } label: {
                            Group {
                                if deleting { ProgressView().tint(PunctumTheme.bone) }
                                else { Text("删除").foregroundStyle(.red) }
                            }.frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .accessibilityIdentifier("comparison-confirm-delete")
                    }
                    .buttonStyle(IconPressButtonStyle())
                    .disabled(deleting)
                }
                .padding(24)
                .background(Color(red: 0.15, green: 0.145, blue: 0.135), in: RoundedRectangle(cornerRadius: 28))
                .overlay(RoundedRectangle(cornerRadius: 28).stroke(PunctumTheme.bone.opacity(0.12), lineWidth: 0.5))
                .padding(28)
                .foregroundStyle(PunctumTheme.bone)
            }
        }
        .sheet(isPresented: $showAccessPicker, onDismiss: {
            guard let index = accessTarget else { return }
            accessTarget = nil
            if PHAsset.fetchAssets(withLocalIdentifiers: [items[index].id], options: nil).firstObject != nil {
                deleteIndex = index
            } else { errorMessage = ComparisonError.photoAccessRequired.localizedDescription }
        }) {
            LimitedComparisonAccess { showAccessPicker = false }
        }
        .alert("未能删除", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("好", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func tile(_ index: Int) -> some View {
        ComparisonTile(photo: items[index], pose: comparison.poses[index],
                       motionID: comparison.motionID, reduceMotion: reduceMotion,
                       onBegin: { comparison.begin(index: index, scale: $0, offset: $1) },
                       onFrame: { comparison.updateFrame(index: index, frame: $0) },
                       onPinch: { comparison.pinch(index: index, factor: $0, point: $1, translation: $2) },
                       onPan: { comparison.pan(index: index, delta: $0) },
                       onTap: { comparison.tap(index: index, point: $0, double: $1) })
            .overlay(alignment: .topTrailing) {
                Button { deleteIndex = index } label: {
                    Image(systemName: "trash").font(.system(size: 17, weight: .medium))
                        .frame(width: 38, height: 38)
                        .background(.black.opacity(0.58), in: Circle())
                        .frame(width: 48, height: 48).contentShape(Rectangle())
                }
                .buttonStyle(IconPressButtonStyle())
                .accessibilityLabel(index == 0 ? "删除原图" : "删除对比图")
                .padding(6)
            }
            .allowsHitTesting(deleteIndex == nil && !deleting)
            .clipped()
            .accessibilityIdentifier("comparison-cell-\(index)")
    }

    private func delete(_ index: Int) {
        guard !deleting else { return }
        if PHAsset.fetchAssets(withLocalIdentifiers: [items[index].id], options: nil).firstObject == nil,
           PHPhotoLibrary.authorizationStatus(for: .readWrite) == .limited {
            deleteIndex = nil
            accessTarget = index
            showAccessPicker = true
            return
        }
        deleting = true
        Task { @MainActor in
            defer { deleting = false }
            do { try await onDelete(items[index]) }
            catch {
                deleteIndex = nil
                let value = error as NSError
                if value.domain != PHPhotosErrorDomain || value.code != PHPhotosError.userCancelled.rawValue {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

private struct ComparisonTile: View {
    let photo: ComparisonPhoto
    let pose: PhotoZoomState
    let motionID: Int
    let reduceMotion: Bool
    var onBegin: (CGFloat, CGSize) -> Void
    var onFrame: (CGRect) -> Void
    var onPinch: (CGFloat, CGPoint, CGPoint) -> Void
    var onPan: (CGPoint) -> Void
    var onTap: (CGPoint, Bool) -> Void
    @StateObject private var loader = ComparisonPhotoLoader()

    var body: some View {
        ZStack {
            Color.black
            if let image = loader.image ?? photo.preview {
                ComparisonImagePane(image: image, pose: pose, motionID: motionID, reduceMotion: reduceMotion,
                                    onBegin: onBegin, onFrame: onFrame,
                                    onPinch: onPinch, onPan: onPan, onTap: onTap)
            } else if loader.failed {
                VStack(spacing: 12) {
                    Text("照片暂时无法载入").font(PunctumTheme.serifSC(13))
                    Button("重试") { loader.load(photo) }
                        .buttonStyle(IconPressButtonStyle())
                        .frame(minHeight: 44)
                }
            } else { ProgressView().tint(PunctumTheme.bone) }
        }
        .task(id: photo.id) { loader.load(photo) }
        .onDisappear { loader.cancel() }
    }
}

@MainActor
private final class ComparisonPhotoLoader: ObservableObject {
    @Published var image: UIImage?
    @Published var failed = false
    private var request: PHImageRequestID = PHInvalidImageRequestID
    private var generation = 0
    func load(_ photo: ComparisonPhoto) {
        cancel()
        failed = false
        image = photo.preview
        guard let asset = photo.photo?.asset else { return }
        let expected = generation
        let options = PHImageRequestOptions()
        options.isNetworkAccessAllowed = true
        options.deliveryMode = .highQualityFormat
        options.resizeMode = .exact
        request = PHImageManager.default().requestImage(for: asset, targetSize: CGSize(width: 2200, height: 2200),
                                                        contentMode: .aspectFit, options: options) { [weak self] image, info in
            guard info?[PHImageResultIsDegradedKey] as? Bool != true else { return }
            DispatchQueue.main.async {
                guard let self, self.generation == expected else { return }
                self.image = image
                self.failed = image == nil
            }
        }
    }
    func cancel() {
        generation += 1
        if request != PHInvalidImageRequestID { PHImageManager.default().cancelImageRequest(request) }
        request = PHInvalidImageRequestID
    }
}

struct ComparePhotosIcon: View {
    var body: some View {
        Image("compare_photos", bundle: .main)
            .renderingMode(.original)
            .resizable().scaledToFit()
    }
}
