import Photos
import SwiftUI
import UIKit

struct DetailScreen: View {
    let photos: [PhotoItem]
    let startIndex: Int
    let initialMetadata: PhotoMetadata?
    let currentGalleryID: String
    let onClose: ([PhotoItem], String?, Int) -> Void
    let onMoveInLibrary: (PhotoItem, AlbumOption) async throws -> Void
    let onCommitMove: (PhotoItem, AlbumOption) -> Void
    let hasMorePhotos: Bool
    var onLoadMore: () -> Void = {}
    var onCommitComparisonDelete: (PhotoItem) -> Void = { _ in }

    @State private var currentIndex: Int
    @State private var selectedPhotoID: String?
    @State private var showComparisonPicker = false
    @State private var comparisonOriginal: PhotoItem?
    @State private var selectedComparisonPhoto: ComparisonPhoto?
    @State private var comparisonSession: ComparisonSession?
    @State private var comparisonError: String?
    @State private var committedComparisonIDs: Set<String> = []
    @State private var comparisonDeleteInFlight = false
    @State private var pinchActive = false
    @State private var controlsVisible = false
    @State private var deleteProgress: CGFloat = 0
    @State private var deletingGesture = false
    @State private var deleteDragOrigin: CGFloat = 0
    @State private var visiblePhotos: [PhotoItem]
    @State private var centerMessage: String?
    @State private var showDeleteHint = false
    @State private var saving = false
    @State private var sharePayload: SharePayload?
    @State private var pendingDeletedIDs: Set<String> = []
    @State private var pendingDeletedPhotos: [PhotoItem] = []
    @State private var closing = false
    @State private var livePlaybackActive = false
    @State private var showMovePicker = false
    @State private var moving = false
    @State private var deletionSettling = false
    @State private var deletionCard: FrozenDeletionCard?
    @State private var deletionFinishing = false
    @State private var frozenDeleteReplacementID: String?
    @State private var frozenDeleteReplacementDisplayNumber = 1
    @State private var pageViewGeneration = 0
    @State private var preparedMetadata: [String: PhotoMetadata]

    init(
        photos: [PhotoItem],
        startIndex: Int,
        initialMetadata: PhotoMetadata? = nil,
        currentGalleryID: String,
        onClose: @escaping ([PhotoItem], String?, Int) -> Void,
        onMoveInLibrary: @escaping (PhotoItem, AlbumOption) async throws -> Void,
        onCommitMove: @escaping (PhotoItem, AlbumOption) -> Void,
        hasMorePhotos: Bool = false,
        onLoadMore: @escaping () -> Void = {},
        onCommitComparisonDelete: @escaping (PhotoItem) -> Void = { _ in }
    ) {
        self.photos = photos
        self.startIndex = startIndex
        self.initialMetadata = initialMetadata
        self.currentGalleryID = currentGalleryID
        self.onClose = onClose
        self.onMoveInLibrary = onMoveInLibrary
        self.onCommitMove = onCommitMove
        self.hasMorePhotos = hasMorePhotos
        self.onLoadMore = onLoadMore
        self.onCommitComparisonDelete = onCommitComparisonDelete
        let initialIndex = min(max(startIndex, 0), max(photos.count - 1, 0))
        _visiblePhotos = State(initialValue: photos)
        _currentIndex = State(initialValue: initialIndex)
        _selectedPhotoID = State(initialValue: photos.indices.contains(initialIndex) ? photos[initialIndex].id : nil)
        if let initialMetadata, photos.indices.contains(initialIndex) {
            _preparedMetadata = State(initialValue: [photos[initialIndex].id: initialMetadata])
        } else {
            _preparedMetadata = State(initialValue: [:])
        }
    }

    private var currentPhoto: PhotoItem? {
        visiblePhotos.indices.contains(currentIndex) ? visiblePhotos[currentIndex] : nil
    }

    private var nextPhoto: PhotoItem? {
        visiblePhotos.indices.contains(currentIndex + 1) ? visiblePhotos[currentIndex + 1] : nil
    }

    private var deleteReplacement: (photo: PhotoItem, index: Int)? {
        if visiblePhotos.indices.contains(currentIndex + 1) {
            return (visiblePhotos[currentIndex + 1], currentIndex + 1)
        }
        if visiblePhotos.indices.contains(currentIndex - 1) {
            return (visiblePhotos[currentIndex - 1], currentIndex - 1)
        }
        return nil
    }

    private var presentedDeleteReplacement: (photo: PhotoItem, displayNumber: Int)? {
        if let frozenDeleteReplacementID,
           let photo = photos.first(where: { $0.id == frozenDeleteReplacementID }) {
            return (photo, frozenDeleteReplacementDisplayNumber)
        }
        guard let replacement = deleteReplacement else { return nil }
        return (replacement.photo, min(replacement.index, currentIndex) + 1)
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    // Full-screen release travel needs more time than the former 200ms exit.
    // Keep this shared with the commit delay so the fixed card is never removed early.
    private let deletionExitMilliseconds = 400
    private let nativeDeletionEnabled = true // Set false to compare the retained SwiftUI path.
    private var armed: Bool { deleteProgress >= 0.72 }

    var body: some View {
        GeometryReader { geometry in
            let screenSize = CGSize(
                width: geometry.size.width + geometry.safeAreaInsets.leading + geometry.safeAreaInsets.trailing,
                height: geometry.size.height + geometry.safeAreaInsets.top + geometry.safeAreaInsets.bottom
            )
            ZStack {
                PunctumTheme.ink.ignoresSafeArea()

                if let replacement = presentedDeleteReplacement {
                    StaticDeletionPage(
                        card: FrozenDeletionCard(photo: replacement.photo, displayNumber: replacement.displayNumber,
                                                 metadata: preparedMetadata[replacement.photo.id]),
                        screenSize: screenSize, safeAreaTop: geometry.safeAreaInsets.top
                    )
                    .equatable()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                    .overlay(Color.black.opacity(nativeDeletionEnabled || deletionSettling ? 0 : 0.48 * (1 - revealProgress)))
                    .opacity(deletionCard != nil || deleteProgress > 0 || deletionSettling ? 1 : 0)
                }

                if !visiblePhotos.isEmpty {
                    NativeDeletionPager(
                        enabled: nativeDeletionEnabled && !pinchActive && !livePlaybackActive && !deletionFinishing,
                        excludedTop: controlsVisible ? geometry.safeAreaInsets.top + DetailControls.rowHeight : 0,
                        targetY: geometry.safeAreaInsets.top + 82,
                        reduceMotion: reduceMotion,
                        onBegin: beginNativeDeletion,
                        onArm: { deleteProgress = $0 ? 0.75 : 0.1 },
                        onRelease: { deletionFinishing = true },
                        onComplete: completeNativeDeletion,
                        onSettled: settleNativeDeletion
                    ) {
                        DetailPager(
                            photos: visiblePhotos,
                            selectedIndex: currentIndex,
                            selection: Binding(
                                get: { currentIndex },
                                set: { index in
                                    guard deletionCard == nil, !deletionSettling, !pinchActive else { return }
                                    currentIndex = index
                                }
                            ),
                            livePlaybackActive: $livePlaybackActive,
                            pinchActive: $pinchActive,
                            screenSize: screenSize,
                            safeAreaTop: geometry.safeAreaInsets.top,
                            metadata: preparedMetadata,
                            locked: deletionCard != nil || deletionSettling,
                            generation: pageViewGeneration,
                            onToggleControls: {
                                withAnimation(.easeOut(duration: 0.16)) { controlsVisible.toggle() }
                            }
                        )
                        .equatable()
                    }
                    .opacity(!nativeDeletionEnabled && (deletionCard != nil || deletionSettling) ? 0 : 1)
                    .allowsHitTesting(nativeDeletionEnabled || (deletionCard == nil && !deletionSettling))
                    .accessibilityHidden(deletionCard != nil || deletionSettling)
                }

                // Keep the single-photo surface mounted before the gesture begins.
                // Its inputs stay fixed while dragging; progress only changes outer transforms.
                if !nativeDeletionEnabled, let card = deletionCard ?? currentPhoto.map({
                    FrozenDeletionCard(photo: $0, displayNumber: currentIndex + 1, metadata: preparedMetadata[$0.id])
                }) {
                    StaticDeletionPage(card: card, screenSize: screenSize, safeAreaTop: geometry.safeAreaInsets.top)
                    .equatable()
                    .id(card.photo.id)
                    .clipShape(RoundedRectangle(cornerRadius: pageRadius, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: pageRadius, style: .continuous)
                            .stroke(Color.white.opacity(0.09), lineWidth: 0.5)
                    }
                    .scaleEffect(pageScale)
                    .offset(y: pageOffset(screenHeight: screenSize.height))
                    // Avoid rerasterizing a full-page blurred shadow on every drag update.
                    .opacity(deletionCard != nil && !deletionSettling ? 1 : 0)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                }

                if deleteProgress > 0 {
                    VStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(armed ? Color(red: 226 / 255, green: 70 / 255, blue: 70 / 255) : Color(red: 44 / 255, green: 44 / 255, blue: 44 / 255).opacity(0.78))
                                .frame(width: 48, height: 48)
                            Image(systemName: "trash")
                                .font(.system(size: 24, weight: .medium))
                                .foregroundStyle(armed ? .white : PunctumTheme.bone.opacity(0.72))
                        }
                        if armed, showDeleteHint {
                            Text("松开后加入本次删除")
                                .font(PunctumTheme.serifSC(10))
                                .foregroundStyle(PunctumTheme.bone.opacity(0.78))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 7)
                                .background(Color(red: 25 / 255, green: 25 / 255, blue: 25 / 255).opacity(0.8), in: Capsule())
                        }
                    }
                    .position(x: geometry.size.width / 2, y: geometry.safeAreaInsets.top + 82)
                }

                if let centerMessage {
                    ToastView(message: centerMessage, fontSize: 11)
                        .transition(.opacity)
                }

                if controlsVisible, deletionCard == nil {
                    DetailControls(
                        saving: saving,
                        onClose: closeDetail,
                        onEdit: editInLightroom,
                        onSave: { savePage(screenSize: screenSize) },
                        onMove: { showMovePicker = true },
                        onCompare: beginComparison
                    )
                    .padding(.top, geometry.safeAreaInsets.top)
                    .frame(maxHeight: .infinity, alignment: .top)
                    .transition(.opacity)
                }
            }
            .contentShape(Rectangle())
            .modifier(LegacyDeletionGesture(enabled: !nativeDeletionEnabled, gesture: deleteGesture(safeAreaTop: geometry.safeAreaInsets.top)))
            .ignoresSafeArea()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            livePlaybackActive = false
            guard !nativeDeletionEnabled else { return }
            guard !deletionFinishing, !deletionSettling else { return }
            deletingGesture = false
            deleteProgress = 0
            deletionCard = nil
            frozenDeleteReplacementID = nil
        }
        .statusBarHidden(!controlsVisible)
        .persistentSystemOverlays(.hidden)
        .onChange(of: currentIndex) { _, index in
            livePlaybackActive = false
            if deleteProgress == 0, !deletionSettling {
                frozenDeleteReplacementID = nil
            }
            if visiblePhotos.indices.contains(index) { selectedPhotoID = visiblePhotos[index].id }
        }
        .onChange(of: photos) { _, updated in
            guard !comparisonDeleteInFlight else { return }
            visiblePhotos = updated.filter { !pendingDeletedIDs.contains($0.id) && !committedComparisonIDs.contains($0.id) }
        }
        .onChange(of: visiblePhotos) { _, updated in
            let photoIDs = updated.map(\.id)
            guard !photoIDs.isEmpty else { return }
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
                if let selectedPhotoID,
                   let preservedIndex = photoIDs.firstIndex(of: selectedPhotoID) {
                    currentIndex = preservedIndex
                } else {
                    currentIndex = min(currentIndex, photoIDs.count - 1)
                    selectedPhotoID = photoIDs[currentIndex]
                }
            }
        }
        .onChange(of: armed) { _, isArmed in
            guard isArmed else {
                showDeleteHint = false
                return
            }
            let defaults = UserDefaults.standard
            let count = defaults.integer(forKey: "delete_red_toast_count")
            showDeleteHint = count < 2
            if count < 2 { defaults.set(count + 1, forKey: "delete_red_toast_count") }
        }
        .task(id: pagination) {
            // Defer until list/selection updates settle; SwiftUI cancels superseded checks.
            await Task.yield()
            guard !Task.isCancelled else { return }
            if pagination.shouldLoad {
                if visiblePhotos.isEmpty { settleNativeDeletion() }
                onLoadMore()
            } else if pagination.shouldClose { closeDetail() }
        }
        .task { await showTutorialsIfNeeded() }
        .task(id: currentPhoto?.id) {
            await prepareMetadata(around: currentIndex)
        }
        .sheet(item: $sharePayload) { payload in
            ShareSheet(items: [payload.url])
        }
        .overlay {
            if showMovePicker {
                PunctumDialogBackdrop()
            }
        }
        .sheet(isPresented: $showMovePicker) {
            AlbumPickerView(
                existingIDs: [],
                currentGalleryID: currentGalleryID,
                allowsMultipleSelection: false,
                onConfirm: { _ in },
                onSelect: moveCurrentPhoto(to:)
            )
            .punctumDialogPresentation()
        }
        .sheet(isPresented: $showComparisonPicker, onDismiss: {
            if let original = comparisonOriginal, let selected = selectedComparisonPhoto {
                comparisonSession = ComparisonSession(original: original, selected: selected)
            }
            selectedComparisonPhoto = nil
        }) {
            ComparisonPhotoPicker { result in
                switch result {
                case .success(let selected):
                    if let selected, selected.id == comparisonOriginal?.id {
                        comparisonError = "请选择另一张照片进行对比。"
                    } else { selectedComparisonPhoto = selected }
                case .failure(let error): comparisonError = error.localizedDescription
                }
                showComparisonPicker = false
            }
        }
        .fullScreenCover(item: $comparisonSession) { session in
            ComparisonScreen(session: session, onClose: { comparisonSession = nil },
                             onDelete: { try await deleteComparedPhoto($0, original: session.original) })
        }
        .alert("无法对比", isPresented: Binding(get: { comparisonError != nil && !showComparisonPicker },
                                                 set: { if !$0 { comparisonError = nil } })) {
            Button("好", role: .cancel) { comparisonError = nil }
        } message: { Text(comparisonError ?? "") }
        .background(PunctumTheme.ink)
    }

    private var pagination: DetailPagination {
        DetailPagination(index: currentIndex, visibleCount: visiblePhotos.count,
                         loadedCount: photos.count, photoID: currentPhoto?.id,
                         hasMore: hasMorePhotos,
                         blocked: closing || comparisonDeleteInFlight || moving ||
                            (!visiblePhotos.isEmpty && (deletionFinishing || deletionSettling || deletionCard != nil)))
    }

    private func beginComparison() {
        guard let photo = currentPhoto, !moving, deletionCard == nil else { return }
        comparisonOriginal = photo
        selectedComparisonPhoto = nil
        livePlaybackActive = false
        pinchActive = false
        pageViewGeneration += 1 // Stop any retained zoom/Live Photo before presenting the picker.
        showComparisonPicker = true
    }

    @MainActor private func deleteComparedPhoto(_ item: ComparisonPhoto, original: PhotoItem) async throws {
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [item.id], options: nil).firstObject else {
            throw ComparisonError.photoAccessRequired
        }
        let photo = item.photo ?? PhotoItem(asset: asset, name: "")
        comparisonDeleteInFlight = true
        do { try await PhotoLibraryService.shared.delete(photo) }
        catch { comparisonDeleteInFlight = false; throw error }
        let returnID = ComparisonState.returningID(ids: visiblePhotos.map(\.id), originalID: original.id, deletedID: photo.id)
        committedComparisonIDs.insert(photo.id)
        pendingDeletedIDs.remove(photo.id)
        pendingDeletedPhotos.removeAll { $0.id == photo.id }
        selectedPhotoID = returnID
        visiblePhotos.removeAll { $0.id == photo.id }
        currentIndex = returnID.flatMap { id in visiblePhotos.firstIndex { $0.id == id } } ?? 0
        comparisonSession = nil
        comparisonDeleteInFlight = false
        pageViewGeneration += 1
        if visiblePhotos.isEmpty { closeDetail() }
        onCommitComparisonDelete(photo)
    }

    private func isInsideControls(_ location: CGPoint, safeAreaTop: CGFloat) -> Bool {
        controlsVisible && location.y <= safeAreaTop + DetailControls.rowHeight
    }

    private func prepareMetadata(around index: Int) async {
        // Coalesce rapid paging before reading originals for EXIF.
        do { try await Task.sleep(for: .milliseconds(180)) } catch { return }
        guard !visiblePhotos.isEmpty, !Task.isCancelled else { return }
        let lowerBound = max(index - 1, 0)
        let upperBound = min(index + 1, visiblePhotos.count - 1)
        let candidates = visiblePhotos[lowerBound...upperBound].filter {
            preparedMetadata[$0.id] == nil
        }
        guard !candidates.isEmpty else { return }

        await withTaskGroup(of: (String, PhotoMetadata).self) { group in
            for photo in candidates {
                group.addTask {
                    let metadata = await MetadataService.shared.metadata(for: photo)
                    return (photo.id, metadata)
                }
            }
            for await (id, metadata) in group {
                guard !Task.isCancelled else { return }
                preparedMetadata[id] = metadata
            }
        }
    }

    private func beginNativeDeletion() -> Bool {
        guard deletionCard == nil, !deletionSettling, !pinchActive,
              !livePlaybackActive, let photo = currentPhoto else { return false }
        deletionCard = FrozenDeletionCard(photo: photo, displayNumber: currentIndex + 1,
                                          metadata: preparedMetadata[photo.id])
        if let replacement = deleteReplacement {
            frozenDeleteReplacementID = replacement.photo.id
            frozenDeleteReplacementDisplayNumber = min(replacement.index, currentIndex) + 1
        }
        deleteProgress = 0.1
        return true
    }

    private func completeNativeDeletion(_ commit: Bool) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            deletionSettling = true
            if commit, let photo = deletionCard?.photo {
                let replacement = deleteReplacement
                if let replacement {
                    selectedPhotoID = replacement.photo.id
                    currentIndex = replacement.index > currentIndex ? currentIndex : max(currentIndex - 1, 0)
                }
                queueDeletion(photo)
                pageViewGeneration += 1
            }
            deleteProgress = 0
        }
    }

    private func settleNativeDeletion() {
        deletionSettling = false
        frozenDeleteReplacementID = nil
        deletionCard = nil
        deletionFinishing = false
    }

    private func deleteGesture(safeAreaTop: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 6, coordinateSpace: .local)
            .onChanged { value in
                guard !deletionFinishing, !deletionSettling, !pinchActive else { return }
                guard !livePlaybackActive else {
                    deletingGesture = false
                    deleteProgress = 0
                    return
                }
                guard !isInsideControls(value.startLocation, safeAreaTop: safeAreaTop) else {
                    deletingGesture = false
                    deleteProgress = 0
                    return
                }
                let upward = max(-value.translation.height, 0)
                let verticalIntent = abs(value.translation.height) > abs(value.translation.width) * 1.2
                if !deletingGesture {
                    let shouldStart = value.translation.height < 0 && verticalIntent
                    if shouldStart, let photo = currentPhoto {
                        deleteDragOrigin = upward
                        deletionCard = FrozenDeletionCard(photo: photo, displayNumber: currentIndex + 1, metadata: preparedMetadata[photo.id])
                    }
                    if shouldStart, let replacement = deleteReplacement {
                        frozenDeleteReplacementID = replacement.photo.id
                        frozenDeleteReplacementDisplayNumber = min(replacement.index, currentIndex) + 1
                    }
                    deletingGesture = shouldStart
                }
                if deletingGesture {
                    // Gesture recognition can deliver its first sample late. Start at
                    // the current finger position, then follow without inherited animation.
                    var transaction = Transaction()
                    transaction.disablesAnimations = true
                    withTransaction(transaction) {
                        deleteProgress = min(max(upward - deleteDragOrigin, 0) / 140, 1)
                    }
                }
            }
            .onEnded { _ in
                guard !deletionFinishing, !deletionSettling, !pinchActive else { return }
                guard deletingGesture else {
                    deleteProgress = 0
                    return
                }
                deletingGesture = false
                deletionFinishing = true
                if armed, let photo = deletionCard?.photo {
                    withAnimation(.timingCurve(0.18, 0.74, 0.25, 1, duration: reduceMotion ? 0 : Double(deletionExitMilliseconds) / 1_000)) {
                        deleteProgress = 1.55
                    }
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(reduceMotion ? 210 : deletionExitMilliseconds + 10))
                        let replacement = deleteReplacement
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            deletionSettling = true
                            if let replacement {
                                selectedPhotoID = replacement.photo.id
                                currentIndex = replacement.index > currentIndex
                                    ? currentIndex
                                    : max(currentIndex - 1, 0)
                            }
                            queueDeletion(photo)
                            pageViewGeneration += 1
                            deleteProgress = 0
                        }
                        await Task.yield()
                        try? await Task.sleep(for: .milliseconds(50))
                        withTransaction(transaction) {
                            deletionSettling = false
                            frozenDeleteReplacementID = nil
                            deletionCard = nil
                            deletionFinishing = false
                        }
                    }
                } else {
                    withAnimation(reduceMotion ? nil : .interactiveSpring(response: 0.30, dampingFraction: 0.88)) {
                        deleteProgress = 0
                    }
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(310))
                        guard deleteProgress == 0, !deletionSettling else { return }
                        // Rebuild the hidden pager at the unchanged selection before revealing it.
                        var transaction = Transaction(); transaction.disablesAnimations = true
                        withTransaction(transaction) { pageViewGeneration += 1 }
                        await Task.yield()
                        try? await Task.sleep(for: .milliseconds(50))
                        withTransaction(transaction) {
                            frozenDeleteReplacementID = nil
                            deletionCard = nil
                            deletionFinishing = false
                        }
                    }
                }
            }
    }

    private var baseDeleteProgress: CGFloat { min(max(deleteProgress, 0), 1) }
    private var extraDeleteProgress: CGFloat { min(max(deleteProgress - 1, 0), 0.55) / 0.55 }
    private var pageScale: CGFloat { reduceMotion ? 1 : 1 - baseDeleteProgress * 0.10 - extraDeleteProgress * 0.06 }
    private func pageOffset(screenHeight: CGFloat) -> CGFloat {
        -baseDeleteProgress * (reduceMotion ? 40 : 180) - extraDeleteProgress * max(screenHeight * 0.82, 520)
    }
    private var pageRadius: CGFloat { baseDeleteProgress * 20 + extraDeleteProgress * 8 }
    private var revealProgress: CGFloat { min(max((deleteProgress - 0.22) / 1.33, 0), 1) }

    private func moveCurrentPhoto(to album: AlbumOption) {
        guard let photo = currentPhoto, !moving else { return }
        moving = true
        Task { @MainActor in
            do {
                try await onMoveInLibrary(photo, album)
                showMovePicker = false
                let nextID = nextPhoto?.id
                if nextID != nil, currentIndex < visiblePhotos.count - 1 {
                    withAnimation(.easeInOut(duration: 0.28)) {
                        currentIndex += 1
                        selectedPhotoID = nextID
                    }
                    try? await Task.sleep(for: .milliseconds(280))
                }
                onCommitMove(photo, album)
                if visiblePhotos.count > 1 {
                    showMessage("该项目已移动到 \(album.title)")
                }
            } catch {
                showMessage(error.localizedDescription)
            }
            moving = false
        }
    }

    private func queueDeletion(_ photo: PhotoItem) {
        guard pendingDeletedIDs.insert(photo.id).inserted else { return }
        pendingDeletedPhotos.append(photo)
        visiblePhotos.removeAll { $0.id == photo.id }
        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        livePlaybackActive = false
    }

    private func closeDetail() {
        guard !closing else { return }
        closing = true
        onClose(pendingDeletedPhotos, currentPhoto?.id ?? selectedPhotoID, currentIndex)
    }

    private func showMessage(_ text: String, duration: Duration = .seconds(2.4)) {
        centerMessage = text
        Task { @MainActor in
            try? await Task.sleep(for: duration)
            if centerMessage == text { centerMessage = nil }
        }
    }

    private func showTutorialsIfNeeded() async {
        let defaults = UserDefaults.standard
        let tutorialKey = "detail_page_tutorial_shown"
        let alreadyShown = defaults.bool(forKey: tutorialKey)
            || defaults.integer(forKey: "quick_page_toast_count") > 0
            || defaults.integer(forKey: "swipe_delete_toast_count") > 0
        defaults.set(true, forKey: tutorialKey)
        defaults.set(2, forKey: "quick_page_toast_count")
        defaults.set(2, forKey: "swipe_delete_toast_count")
        guard !alreadyShown else { return }

        try? await Task.sleep(for: .milliseconds(500))
        guard !Task.isCancelled else { return }
        showMessage("单击屏幕左/右边缘，支持快速切换前/后图片", duration: .seconds(4))
        try? await Task.sleep(for: .seconds(5))
        guard !Task.isCancelled else { return }
        showMessage("上滑页面，支持快速删除图片", duration: .seconds(4))
    }

    private func editInLightroom() {
        guard let photo = currentPhoto else { return }
        guard LightroomService.shared.isInstalled else {
            showMessage("请下载 Lightroom 后使用编辑")
            return
        }
        Task { @MainActor in
            do {
                sharePayload = SharePayload(url: try await LightroomService.shared.prepareOriginalFile(photo))
            } catch {
                showMessage("照片原图读取失败，请稍后重试")
            }
        }
    }

    private func savePage(screenSize: CGSize) {
        guard let photo = currentPhoto, !saving else { return }
        let displayNumber = currentIndex + 1
        saving = true
        centerMessage = nil
        Task { @MainActor in
            do {
                try await ExportService.shared.renderDetailPageAndSave(
                    photo: photo,
                    displayNumber: displayNumber,
                    screenSize: screenSize
                )
                showMessage("已保存当前页面到系统相册 Punctum")
            } catch {
                showMessage("保存失败，请稍后重试")
            }
            saving = false
        }
    }
}

private struct DetailControls: View {
    static let rowHeight: CGFloat = 60

    let saving: Bool
    let onClose: () -> Void
    let onEdit: () -> Void
    let onSave: () -> Void
    let onMove: () -> Void
    let onCompare: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            controlButton("arrow.left", label: "返回", color: PunctumTheme.bone, action: onClose)
            Spacer()
            controlButton("pencil", label: "使用 Lightroom 编辑", color: PunctumTheme.bone, action: onEdit)
            controlButton("arrow.down.to.line", label: "保存到 Punctum 图集", color: saving ? PunctumTheme.muted : PunctumTheme.bone, action: onSave)
                .disabled(saving)
            Button(action: onMove) {
                MoveToAlbumIcon()
                    .foregroundStyle(PunctumTheme.bone)
                    .frame(width: 23, height: 23)
                    .frame(width: 56, height: 56)
                    .contentShape(Rectangle())
            }
            .buttonStyle(IconPressButtonStyle())
            .accessibilityLabel("移动到其他图集")
            Button(action: onCompare) {
                ComparePhotosIcon().frame(width: 27, height: 27)
                    .frame(width: 56, height: 56).contentShape(Rectangle())
            }
            .buttonStyle(IconPressButtonStyle())
            .accessibilityLabel("对比照片")
            .accessibilityIdentifier("detail-compare")
        }
        .padding(.horizontal, 4)
        .frame(height: Self.rowHeight)
        .contentShape(Rectangle())
    }

    private func controlButton(_ name: String, label: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: 23, weight: .medium))
                .foregroundStyle(color)
                .frame(width: 56, height: 56)
                .contentShape(Rectangle())
        }
        .buttonStyle(IconPressButtonStyle())
        .accessibilityLabel(label)
    }
}

private struct FrozenDeletionCard: Equatable {
    let photo: PhotoItem
    let displayNumber: Int
    let metadata: PhotoMetadata?
}

/// Isolate the expensive page collection from per-frame deletion progress updates.
private struct DetailPager: View, Equatable {
    let photos: [PhotoItem]
    let selectedIndex: Int
    @Binding var selection: Int
    @Binding var livePlaybackActive: Bool
    @Binding var pinchActive: Bool
    let screenSize: CGSize
    let safeAreaTop: CGFloat
    let metadata: [String: PhotoMetadata]
    let locked: Bool
    let generation: Int
    let onToggleControls: () -> Void

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.photos == rhs.photos && lhs.selectedIndex == rhs.selectedIndex
            && lhs.screenSize == rhs.screenSize && lhs.safeAreaTop == rhs.safeAreaTop
            && lhs.metadata == rhs.metadata && lhs.locked == rhs.locked
            && lhs.generation == rhs.generation
    }

    var body: some View {
        TabView(selection: $selection) {
            ForEach(Array(photos.enumerated()), id: \.element.id) { index, photo in
                DetailPage(
                    photo: photo, displayNumber: index + 1,
                    screenSize: screenSize, safeAreaTop: safeAreaTop,
                    livePlaybackActive: $livePlaybackActive,
                    pinchActive: $pinchActive,
                    pagingEnabled: true, isSelected: index == selectedIndex,
                    initialMetadata: metadata[photo.id], interactionLocked: locked,
                    onPrevious: { if !locked, !pinchActive, index > 0 { selection = index - 1 } },
                    onNext: { if !locked, !pinchActive, index < photos.count - 1 { selection = index + 1 } },
                    onToggleControls: onToggleControls
                )
                .tag(index)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .id(generation)
        .background(PagingScrollLock(locked: locked))
    }
}

/// A pre-mounted noninteractive page; drag changes never rebuild its photo or text subtree.
private struct StaticDeletionPage: View, Equatable {
    let card: FrozenDeletionCard
    let screenSize: CGSize
    let safeAreaTop: CGFloat

    var body: some View {
        DetailPage(
            photo: card.photo, displayNumber: card.displayNumber,
            screenSize: screenSize, safeAreaTop: safeAreaTop,
            livePlaybackActive: .constant(false), pagingEnabled: false,
            isSelected: false, initialMetadata: card.metadata, interactionLocked: true
        )
    }
}

private enum DetailLiveBadge {
    static let visualPadding: CGFloat = 6
    static let hotSize: CGFloat = 44
}

private struct DetailPage: View {
    let photo: PhotoItem
    let displayNumber: Int
    let screenSize: CGSize
    let safeAreaTop: CGFloat
    @Binding var livePlaybackActive: Bool
    var pinchActive: Binding<Bool> = .constant(false)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var zoomResetID = 0
    @State private var zoomOwnsInteraction = false
    @State private var zoomScale: CGFloat = 1
    @State private var zoomOffset: CGSize = .zero
    @State private var zoomResetTask: Task<Void, Never>?
    var pagingEnabled: Bool = true
    var isSelected: Bool = true
    var initialMetadata: PhotoMetadata? = nil
    var interactionLocked: Bool = false
    var onPrevious: () -> Void = {}
    var onNext: () -> Void = {}
    var onToggleControls: () -> Void = {}
    @State private var metadata = PhotoMetadata()
    @State private var imageFrameInPage: CGRect = .zero
    @State private var livePhoto: PHLivePhoto?
    @State private var playbackMode: LivePlaybackMode = .none
    @State private var hasBegunPlayback = false
    @State private var liveLoadTask: Task<Void, Never>?
    @State private var playbackFallbackTask: Task<Void, Never>?

    private var badgeHotRect: CGRect {
        guard photo.isLivePhoto, imageFrameInPage.width > 1 else { return .null }
        return CGRect(
            x: imageFrameInPage.maxX - DetailLiveBadge.hotSize,
            y: imageFrameInPage.maxY - DetailLiveBadge.hotSize,
            width: DetailLiveBadge.hotSize,
            height: DetailLiveBadge.hotSize
        )
    }

    private var displayedMetadata: PhotoMetadata {
        var value = metadata == PhotoMetadata() ? (initialMetadata ?? PhotoMetadata()) : metadata
        if value.dateTaken == nil {
            value.dateTaken = PunctumFormatting.detailDate(photo.creationDate)
        }
        if value.coordinate == nil {
            value.coordinate = photo.asset.location.map {
                String(format: "%.5f, %.5f", $0.coordinate.latitude, $0.coordinate.longitude)
            }
        }
        if value.resolution == nil, photo.width > 0, photo.height > 0 {
            value.resolution = "\(photo.width) × \(photo.height)"
        }
        return value
    }

    var body: some View {
        ZStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    DetailPhotoFrame(
                        photo: photo,
                        screenSize: screenSize,
                        safeAreaTop: safeAreaTop,
                        livePhoto: livePhoto,
                        playbackMode: playbackMode,
                        hasBegunPlayback: hasBegunPlayback,
                        zoomScale: zoomScale, zoomOffset: zoomOffset,
                        onFrameChange: { imageFrameInPage = $0 },
                        onTopAreaTap: onToggleControls,
                        onDidBeginPlayback: { hasBegunPlayback = true },
                        onDidEndPlayback: {
                            if playbackMode == .playOnce { haltPlayback() }
                        }
                    )
                    .zIndex(1)
                    DetailMetadataView(
                        metadata: displayedMetadata,
                        displayNumber: displayNumber,
                        onTap: onToggleControls
                    )
                        .padding(.top, 34)
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
            }
            .ignoresSafeArea(edges: .top)
            .scrollBounceBehavior(.basedOnSize)
            .scrollDisabled(interactionLocked || pinchActive.wrappedValue)

            if pagingEnabled {
                LiveHoldCatcher(
                    holdEnabled: photo.isLivePhoto && !interactionLocked,
                    pagingEnabled: !interactionLocked,
                    livePlaybackActive: livePlaybackActive,
                    imageRect: imageFrameInPage,
                    badgeHotRect: badgeHotRect,
                    onHoldStart: { startPlayback(.hold) },
                    onHoldEnd: {
                        if playbackMode == .hold { haltPlayback() }
                    },
                    onBadgeTap: { startPlayback(.playOnce) },
                    onTapLeft: onPrevious,
                    onTapRight: onNext,
                    onTapCenter: { if !pinchActive.wrappedValue { onToggleControls() } },
                    pinchEnabled: isSelected && !interactionLocked,
                    onZoomChange: { zoom, locked in
                        zoomResetTask?.cancel()
                        zoomResetTask = nil
                        if locked { haltPlayback() }
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            zoomScale = zoom.scale
                            zoomOffset = zoom.offset
                            zoomOwnsInteraction = locked
                            pinchActive.wrappedValue = locked
                        }
                    },
                    zoomResetID: zoomResetID,
                    onZoomReset: resetZoom
                )
                .frame(width: screenSize.width)
                .frame(maxHeight: .infinity)
                .zIndex(1)
            }
        }
        .coordinateSpace(name: "detailPage")
        .background(PunctumTheme.ink)
        .task(id: "\(photo.id)-\(isSelected)") {
            guard pagingEnabled && isSelected else { return }
            do { try await Task.sleep(for: .milliseconds(180)) } catch { return }
            guard !Task.isCancelled else { return }
            loadLivePhoto()
            let loaded = await MetadataService.shared.metadata(for: photo)
            guard !Task.isCancelled else { return }
            metadata = loaded
            if let location = await MetadataService.shared.locationName(for: photo) {
                guard !Task.isCancelled else { return }
                metadata.location = location
            }
        }
        .onChange(of: photo.id) { _, _ in
            clearZoom()
            resetPlayback()
        }
        .onChange(of: isSelected) { _, selected in
            if !selected {
                clearZoom()
                resetPlayback()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            clearZoom()
            haltPlayback()
        }
        .onDisappear {
            clearZoom()
            haltPlayback()
            liveLoadTask?.cancel()
            liveLoadTask = nil
        }
    }

    private func resetZoom() {
        zoomResetTask?.cancel()
        withAnimation(reduceMotion ? nil : .interactiveSpring(response: 0.30, dampingFraction: 0.88)) {
            zoomScale = 1
            zoomOffset = .zero
        }
        zoomResetTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(reduceMotion ? 160 : 310))
            guard !Task.isCancelled else { return }
            zoomOwnsInteraction = false
            pinchActive.wrappedValue = false
            zoomResetTask = nil
        }
    }

    private func clearZoom() {
        if zoomOwnsInteraction || zoomResetTask != nil { pinchActive.wrappedValue = false }
        zoomOwnsInteraction = false
        zoomResetID += 1
        zoomResetTask?.cancel()
        zoomResetTask = nil
        zoomScale = 1
        zoomOffset = .zero
    }

    private func startPlayback(_ mode: LivePlaybackMode) {
        guard photo.isLivePhoto, !pinchActive.wrappedValue else { return }
        playbackFallbackTask?.cancel()
        playbackFallbackTask = nil
        if mode == .hold {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
        playbackMode = mode
        livePlaybackActive = true
        if mode == .playOnce {
            playbackFallbackTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(4))
                guard !Task.isCancelled, playbackMode == .playOnce else { return }
                haltPlayback()
            }
        }
    }

    private func haltPlayback() {
        playbackFallbackTask?.cancel()
        playbackFallbackTask = nil
        playbackMode = .none
        livePlaybackActive = false
        hasBegunPlayback = false
    }

    private func resetPlayback() {
        haltPlayback()
        livePhoto = nil
        liveLoadTask?.cancel()
        liveLoadTask = nil
    }

    private func loadLivePhoto() {
        liveLoadTask?.cancel()
        livePhoto = nil
        guard photo.isLivePhoto else { return }
        let aspect = max(photo.hasKnownSize ? photo.aspectRatio : 1.5, 0.1)
        let imageHeight = screenSize.width / aspect
        let scale = UIScreen.main.scale
        let target = CGSize(width: screenSize.width * scale, height: imageHeight * scale)
        liveLoadTask = Task {
            let live = await PhotoLibraryService.shared.requestLivePhoto(for: photo.asset, targetSize: target)
            guard !Task.isCancelled else { return }
            livePhoto = live
        }
    }
}

/// Photo bounds are independent of metadata layout and never begin above the viewport.
struct DetailPhotoLayout {
    let imageSize: CGSize
    let topPadding: CGFloat

    init(aspect: CGFloat, screenSize: CGSize, safeAreaTop: CGFloat) {
        let aspect = max(aspect, 0.1)
        let naturalHeight = screenSize.width / aspect
        if aspect < 1 {
            // Portraits start at the physical screen edge, without shifting content above it.
            topPadding = 0
            let availableHeight = max(screenSize.height - topPadding - 24, 1)
            let height = min(naturalHeight, availableHeight)
            imageSize = CGSize(width: height * aspect, height: height)
        } else {
            imageSize = CGSize(width: screenSize.width, height: naturalHeight)
            // Keep the existing landscape position after removing the parent negative inset.
            topPadding = max((screenSize.height - naturalHeight) * 0.5 - 40, 0)
        }
    }
}

private struct DetailPhotoFrame: View {
    let photo: PhotoItem
    let screenSize: CGSize
    let safeAreaTop: CGFloat
    let livePhoto: PHLivePhoto?
    let playbackMode: LivePlaybackMode
    let hasBegunPlayback: Bool
    var zoomScale: CGFloat = 1
    var zoomAnchor: UnitPoint = .center
    var zoomOffset: CGSize = .zero
    var onFrameChange: (CGRect) -> Void = { _ in }
    var onTopAreaTap: () -> Void = {}
    var onDidBeginPlayback: () -> Void = {}
    var onDidEndPlayback: () -> Void = {}

    private var fadeIn: Bool {
        playbackMode != .none && livePhoto != nil && hasBegunPlayback
    }

    var body: some View {
        let aspect = max(photo.hasKnownSize ? photo.aspectRatio : 1.5, 0.1)
        let layout = DetailPhotoLayout(aspect: aspect, screenSize: screenSize, safeAreaTop: safeAreaTop)
        let imageHeight = layout.imageSize.height
        let topPadding = layout.topPadding

        ZStack(alignment: .bottomTrailing) {
            if photo.isLivePhoto, livePhoto != nil {
                LivePhotoHost(
                    livePhoto: livePhoto,
                    mode: playbackMode,
                    onDidBeginPlayback: onDidBeginPlayback,
                    onDidEndPlayback: onDidEndPlayback
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            PhotoAssetImage(
                photo: photo,
                targetSize: CGSize(width: 2200, height: 2200),
                contentMode: .fit,
                skipDegraded: true
            )
            .opacity(fadeIn ? 0 : 1)
            .animation(fadeIn ? .easeOut(duration: 0.2) : .linear(duration: 0), value: fadeIn)

            if photo.isLivePhoto, playbackMode == .none {
                LivePhotoBadge()
                    .padding(DetailLiveBadge.visualPadding)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .frame(width: layout.imageSize.width, height: imageHeight)
        .scaleEffect(zoomScale, anchor: zoomAnchor)
        .offset(zoomOffset)
        .background {
            Color.clear.onGeometryChange(for: CGRect.self) { geo in
                geo.frame(in: .named("detailPage"))
            } action: { frame in
                onFrameChange(frame)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, topPadding)
        .overlay(alignment: .top) {
            if topPadding > 0 {
                Color.clear
                    .frame(height: topPadding)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onTopAreaTap)
            }
        }
    }
}

private struct DetailMetadataView: View {
    let metadata: PhotoMetadata
    let displayNumber: Int
    var onTap: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("No.\(displayNumber)")
                .font(PunctumTheme.newsreader(22, bold: true))
                .foregroundStyle(PunctumTheme.bone)
                .frame(minHeight: 26, alignment: .leading)
                .padding(.bottom, 18)

            VStack(alignment: .leading, spacing: 0) {
                if let location = metadata.location ?? metadata.coordinate {
                    Text(location).detailTextStyle()
                }
                if let date = metadata.dateTaken {
                    Text(date).detailTextStyle()
                }
            }

            VStack(alignment: .leading, spacing: 0) {
                detailLine("Camera", metadata.camera)
                detailLine("Exposure Time", metadata.exposureTime)
                detailLine("Focal Length", metadata.focalLength)
                detailLine("Aperture", metadata.aperture)
                detailLine("ISO", metadata.iso)
                detailLine("Resolution", metadata.resolution)
                detailLine("File Size", metadata.fileSize)
            }
            .padding(.top, 19)

            Spacer().frame(height: 86)
        }
        .padding(.horizontal, 30)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }

    @ViewBuilder
    private func detailLine(_ label: String, _ value: String?) -> some View {
        if let value, !value.isEmpty {
            Text("\(label): \(value)").detailTextStyle()
        }
    }
}

private struct SharePayload: Identifiable {
    let id = UUID()
    let url: URL
}

private extension Text {
    func detailTextStyle() -> some View {
        font(PunctumTheme.newsreader(14))
            .foregroundStyle(PunctumTheme.bone)
            .frame(maxWidth: .infinity, minHeight: 18, alignment: .leading)
    }
}


private struct LegacyDeletionGesture<G: Gesture>: ViewModifier {
    let enabled: Bool
    let gesture: G
    @ViewBuilder func body(content: Content) -> some View {
        if enabled { content.simultaneousGesture(gesture) }
        else { content }
    }
}
