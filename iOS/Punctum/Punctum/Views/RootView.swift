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
                    sortingEnabled: !model.isLoading && !model.isSortingPhotos,
                    onToggleSort: model.togglePhotoSort,
                    onSelectPhoto: { photo, metadata, visibleIDs in
                        model.openDetail(photoID: photo.id, in: gallery.id, metadata: metadata, entryVisibleIDs: visibleIDs)
                    },
                    onDeletePhoto: model.deletePhoto,
                    returnTargetID: model.galleryReturnTargetID,
                    onReturnPositioned: model.finishGalleryReturnPositioning,
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
                    hasMorePhotos: !model.galleryFetchExhausted,
                    onLoadMore: model.loadMorePhotos,
                    onCommitComparisonDelete: model.commitComparisonDeletion
                )
                .id(model.detailSession)
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
        .background {
            PhotoDeletionConfirmation(
                request: model.pendingDeletionRequest,
                isActive: scenePhase == .active,
                onConfirm: model.confirmPendingDeletion,
                onCancel: model.cancelPendingDeletion
            ).allowsHitTesting(false)
        }
        .overlay(alignment: .center) {
            if let message = model.transientMessage,
               message != GalleryViewModel.photoSortHint ||
                (model.currentGallery != nil && !model.showSwitcher && model.detailIndex == nil) {
                ToastView(message: message, multiline: message == GalleryViewModel.photoSortHint)
                    .padding(.horizontal, 24)
                    .allowsHitTesting(false)
                    .task(id: message) {
                        try? await Task.sleep(for: .seconds(message == GalleryViewModel.photoSortHint ? 4 : 2.4))
                        guard !Task.isCancelled else { return }
                        if model.transientMessage == message { model.transientMessage = nil }
                    }
            }
        }
        .tint(PunctumTheme.gold)
        .onChange(of: model.currentGalleryID) { _, _ in model.dismissPhotoSortHint() }
        .onChange(of: model.showSwitcher) { _, visible in
            if visible { model.dismissPhotoSortHint() }
        }
        .onChange(of: model.detailIndex) { _, index in
            if index != nil { model.dismissPhotoSortHint() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.appDidBecomeActive() }
            else if phase == .background { model.appDidEnterBackground() }
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

// Own the native confirmation independently of SwiftUI's alert binding. Photos
// may present its own system dialog only after our presenter has fully dismissed.
struct PhotoDeletionConfirmation: UIViewControllerRepresentable {
    let request: PendingPhotoDeletion?
    let isActive: Bool
    let onConfirm: (PendingPhotoDeletion) -> Void
    let onCancel: (PendingPhotoDeletion) -> Void

    func makeUIViewController(context: Context) -> Controller { Controller() }
    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.onConfirm = onConfirm
        controller.onCancel = onCancel
        controller.update(request: request, isActive: isActive)
    }
    static func dismantleUIViewController(_ controller: Controller, coordinator: ()) {
        controller.stop()
    }

    final class Controller: UIViewController {
        var onConfirm: (PendingPhotoDeletion) -> Void = { _ in }
        var onCancel: (PendingPhotoDeletion) -> Void = { _ in }
        private(set) var request: PendingPhotoDeletion?
        private var isActive = false
        private var alert: UIAlertController?
        private var dismissing = false
        private var decision: (request: PendingPhotoDeletion, confirmed: Bool)?

        override func loadView() {
            let anchor = WindowAnchorView()
            anchor.onAttach = { [weak self] in
                DispatchQueue.main.async { self?.presentIfNeeded() }
            }
            view = anchor
        }
        override func viewDidLoad() {
            super.viewDidLoad()
            view.isUserInteractionEnabled = false
            NotificationCenter.default.addObserver(self, selector: #selector(enterBackground),
                name: UIApplication.didEnterBackgroundNotification, object: nil)
        }
        deinit { NotificationCenter.default.removeObserver(self) }
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            presentIfNeeded()
        }
        func update(request: PendingPhotoDeletion?, isActive: Bool) {
            self.request = request
            self.isActive = isActive
            if request == nil, alert != nil { dismissAlert() }
            presentIfNeeded()
        }
        private func presentIfNeeded() {
            guard isActive, !dismissing, isViewLoaded, view.window != nil else { return }
            if let decision {
                self.decision = nil
                guard request?.id == decision.request.id else { return }
                if decision.confirmed { onConfirm(decision.request) }
                else { onCancel(decision.request) }
                return
            }
            guard alert == nil, presentedViewController == nil, let request else { return }
            // Wait for other root presentations instead of stacking modal dialogs.
            var ancestor = parent
            while let controller = ancestor {
                guard controller.presentedViewController == nil else { return }
                ancestor = controller.parent
            }
            let confirmation = UIAlertController(title: "本次删除 \(request.photos.count) 项",
                message: "确定删除后该照片将移入回收站", preferredStyle: .alert)
            confirmation.view.tintColor = UIColor(PunctumTheme.gold)
            confirmation.addAction(UIAlertAction(title: "确定删除", style: .destructive) { [weak self] _ in
                self?.complete(request, confirmed: true)
            })
            confirmation.addAction(UIAlertAction(title: "取消", style: .cancel) { [weak self] _ in
                self?.complete(request, confirmed: false)
            })
            alert = confirmation
            present(confirmation, animated: true)
        }
        func complete(_ request: PendingPhotoDeletion, confirmed: Bool) {
            guard self.request?.id == request.id, decision == nil, !dismissing else { return }
            decision = (request, confirmed)
            dismissAlert()
        }
        private func dismissAlert() {
            guard !dismissing else { return }
            guard let alert else { presentIfNeeded(); return }
            dismissing = true
            alert.dismiss(animated: isActive) { [weak self] in
                guard let self else { return }
                self.alert = nil
                self.dismissing = false
                self.presentIfNeeded()
            }
        }
        @objc func enterBackground() {
            isActive = false
            // Keep the batch; a suspended native alert must not leave a modal
            // shield on the list or silently clear the pending deletion request.
            dismissAlert()
        }
        func stop() {
            isActive = false
            request = nil
            decision = nil
            dismissAlert()
        }
    }
}


private final class WindowAnchorView: UIView {
    var onAttach: () -> Void = {}
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil { onAttach() }
    }
}
