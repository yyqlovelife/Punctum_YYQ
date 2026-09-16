import SwiftUI
import UIKit

struct RootView: View {
    @EnvironmentObject private var model: GalleryViewModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var renameTarget: PunctumGallery?
    @State private var renameText = ""
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var showingHome: Bool { model.showSwitcher || model.currentGallery == nil || model.galleryReadyID != model.currentGalleryID }

    var body: some View {
        ZStack {
            PunctumTheme.ink.ignoresSafeArea()

            if model.galleries.isEmpty {
                EmptyScreen(onAdd: model.requestAlbumPicker)
            }
            if let gallery = model.currentGallery {
                GalleryScreen(
                    gallery: gallery,
                    photos: model.photos,
                    overview: model.overviews[gallery.id],
                    isLoading: model.isLoading,
                    onOpenSwitcher: model.openSwitcher,
                    onRename: { beginRename(gallery) },
                    onSelectPhoto: model.openDetail,
                    onDeletePhoto: model.deletePhoto,
                    onLoadMore: model.loadMorePhotos
                )
                .opacity(showingHome ? 0 : 1)
                .offset(y: reduceMotion || !showingHome ? 0 : 8)
                .allowsHitTesting(!showingHome && model.detailIndex == nil)
                .accessibilityHidden(showingHome)
            }
            if !model.galleries.isEmpty {
                SwitcherScreen(
                    galleries: model.galleries,
                    overviews: model.overviews,
                    subtitle: model.homeSubtitle,
                    invitationStyle: model.invitationStyle,
                    scrollToGalleryID: $model.pendingHomeScrollID,
                    onSelect: model.selectGallery,
                    onAdd: model.requestAlbumPicker,
                    onToggleStyle: model.toggleInvitationStyle,
                    onMove: model.moveGallery,
                    onDelete: model.removeGallery
                )
                .opacity(showingHome ? 1 : 0)
                .allowsHitTesting(showingHome && model.detailIndex == nil)
                .accessibilityHidden(!showingHome)
            }

            if !model.showSwitcher, model.currentGallery != nil, model.galleryReadyID != model.currentGalleryID {
                VStack { Spacer(); ProgressView("正在准备图集…").tint(PunctumTheme.gold).padding().background(PunctumTheme.ink.opacity(0.9)); Spacer().frame(height: 24) }
                    .allowsHitTesting(false)
            }

            if let detailIndex = model.detailIndex, !model.photos.isEmpty {
                DetailScreen(
                    photos: model.photos,
                    startIndex: detailIndex,
                    initialMetadata: model.detailInitialMetadata,
                    currentGalleryID: model.currentGalleryID ?? "",
                    onClose: model.closeDetail,
                    onMoveInLibrary: model.movePhotoInLibrary,
                    onCommitMove: model.commitMovedPhoto,
                    onLoadMore: model.loadMorePhotos
                )
                .opacity(model.detailVisible ? 1 : 0)
                .allowsHitTesting(model.detailVisible)
                .animation(.easeOut(duration: model.detailVisible ? 0.18 : 0.14), value: model.detailVisible)
                .task { await Task.yield(); model.revealDetail() }
                .zIndex(20)
            }

            if model.showAlbumPicker {
                PunctumDialogBackdrop()
                    .zIndex(30)
            }
        }
        .animation(.easeOut(duration: showingHome ? 0.16 : 0.18), value: showingHome)
        .sheet(isPresented: $model.showAlbumPicker) {
            AlbumPickerView(
                existingIDs: Set(model.galleries.map(\.id)),
                onConfirm: model.addAlbums
            )
                .punctumDialogPresentation()
        }
        .alert("重命名画廊", isPresented: renamePresented) {
            TextField("画廊名称", text: $renameText)
            Button("取消", role: .cancel) { renameTarget = nil }
            Button("保存") {
                if let target = renameTarget { model.renameGallery(target, to: renameText) }
                renameTarget = nil
            }
        }
        .alert("照片访问权限", isPresented: permissionPresented) {
            Button("取消", role: .cancel) { model.permissionMessage = nil }
            Button("打开设置") {
                model.permissionMessage = nil
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
        } message: {
            Text(model.permissionMessage ?? "")
        }
        .alert(item: $model.pendingDeletionRequest) { request in
            Alert(
                title: Text("本次删除 \(request.photos.count) 项"),
                message: Text("确定删除后该照片将移入回收站"),
                primaryButton: .destructive(Text("确定删除")) {
                    model.confirmPendingDeletion(request)
                },
                secondaryButton: .cancel(Text("取消")) {
                    model.cancelPendingDeletion(request)
                }
            )
        }
        .overlay(alignment: .center) {
            if let message = model.transientMessage {
                ToastView(message: message)
                    .onAppear {
                        Task {
                            try? await Task.sleep(for: .seconds(2.4))
                            if model.transientMessage == message { model.transientMessage = nil }
                        }
                    }
            }
        }
        .tint(PunctumTheme.gold)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.appDidBecomeActive() }
        }
    }

    private var renamePresented: Binding<Bool> {
        Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )
    }

    private var permissionPresented: Binding<Bool> {
        Binding(
            get: { model.permissionMessage != nil },
            set: { if !$0 { model.permissionMessage = nil } }
        )
    }

    private func beginRename(_ gallery: PunctumGallery) {
        renameText = gallery.displayName
        renameTarget = gallery
    }
}
