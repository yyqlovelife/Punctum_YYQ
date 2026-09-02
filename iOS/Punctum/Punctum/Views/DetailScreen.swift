import Photos
import SwiftUI
import UIKit

struct DetailScreen: View {
    let photos: [PhotoItem]
    let startIndex: Int
    let initialMetadata: PhotoMetadata?
    let currentGalleryID: String
    let onClose: ([PhotoItem]) -> Void
    let onMoveInLibrary: (PhotoItem, AlbumOption) async throws -> Void
    let onCommitMove: (PhotoItem, AlbumOption) -> Void
    var onLoadMore: () -> Void = {}

    @State private var currentIndex: Int
    @State private var selectedPhotoID: String?
    @State private var controlsVisible = false
    @State private var deleteProgress: CGFloat = 0
    @State private var deletingGesture = false
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
    @State private var frozenDeleteReplacementID: String?
    @State private var frozenDeleteReplacementDisplayNumber = 1
    @State private var pageViewGeneration = 0
    @State private var preparedMetadata: [String: PhotoMetadata]

    init(
        photos: [PhotoItem],
        startIndex: Int,
        initialMetadata: PhotoMetadata? = nil,
        currentGalleryID: String,
        onClose: @escaping ([PhotoItem]) -> Void,
        onMoveInLibrary: @escaping (PhotoItem, AlbumOption) async throws -> Void,
        onCommitMove: @escaping (PhotoItem, AlbumOption) -> Void,
        onLoadMore: @escaping () -> Void = {}
    ) {
        self.photos = photos
        self.startIndex = startIndex
        self.initialMetadata = initialMetadata
        self.currentGalleryID = currentGalleryID
        self.onClose = onClose
        self.onMoveInLibrary = onMoveInLibrary
        self.onCommitMove = onCommitMove
        self.onLoadMore = onLoadMore
        let initialIndex = min(max(startIndex, 0), max(photos.count - 1, 0))
        _currentIndex = State(initialValue: initialIndex)
        _selectedPhotoID = State(initialValue: photos.indices.contains(initialIndex) ? photos[initialIndex].id : nil)
        if let initialMetadata, photos.indices.contains(initialIndex) {
            _preparedMetadata = State(initialValue: [photos[initialIndex].id: initialMetadata])
        } else {
            _preparedMetadata = State(initialValue: [:])
        }
    }

    private var visiblePhotos: [PhotoItem] {
        photos.filter { !pendingDeletedIDs.contains($0.id) }
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
                    DetailPage(
                        photo: replacement.photo,
                        displayNumber: replacement.displayNumber,
                        screenSize: screenSize,
                        safeAreaTop: geometry.safeAreaInsets.top,
                        livePlaybackActive: .constant(false),
                        pagingEnabled: false,
                        isSelected: false,
                        initialMetadata: preparedMetadata[replacement.photo.id],
                        interactionLocked: false,
                        onPrevious: {},
                        onNext: {},
                        onToggleControls: {}
                    )
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                    .overlay(Color.black.opacity(deletionSettling ? 0 : 0.48 * (1 - revealProgress)))
                    .opacity(deleteProgress > 0 || deletionSettling ? 1 : 0)
                }

                if !visiblePhotos.isEmpty {
                    TabView(selection: $currentIndex) {
                        ForEach(Array(visiblePhotos.enumerated()), id: \.element.id) { index, photo in
                            DetailPage(
                                photo: photo,
                                displayNumber: index + 1,
                                screenSize: screenSize,
                                safeAreaTop: geometry.safeAreaInsets.top,
                                livePlaybackActive: $livePlaybackActive,
                                pagingEnabled: true,
                                isSelected: index == currentIndex,
                                initialMetadata: preparedMetadata[photo.id],
                                interactionLocked: deletingGesture || deleteProgress > 0 || deletionSettling,
                                onPrevious: {
                                    if index > 0 { currentIndex = index - 1 }
                                },
                                onNext: {
                                    if index < visiblePhotos.count - 1 { currentIndex = index + 1 }
                                },
                                onToggleControls: {
                                    withAnimation(.easeOut(duration: 0.16)) { controlsVisible.toggle() }
                                }
                            )
                            .tag(index)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))
                    .id(pageViewGeneration)
                    .background(PagingScrollLock(
                        locked: deletingGesture || deleteProgress > 0 || deletionSettling
                    ))
                    .scaleEffect(pageScale)
                    .offset(y: pageOffset(screenHeight: screenSize.height))
                    .rotationEffect(.degrees(pageRotation))
                    .clipShape(RoundedRectangle(cornerRadius: pageRadius, style: .continuous))
                    .shadow(
                        color: .black.opacity(0.42 * baseDeleteProgress),
                        radius: 18 * baseDeleteProgress,
                        y: 10 * baseDeleteProgress
                    )
                    .opacity(deletionSettling ? 0 : 1)
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

                if controlsVisible {
                    DetailControls(
                        saving: saving,
                        onClose: closeDetail,
                        onEdit: editInLightroom,
                        onSave: { savePage(screenSize: screenSize) },
                        onMove: { showMovePicker = true }
                    )
                    .padding(.top, geometry.safeAreaInsets.top)
                    .frame(maxHeight: .infinity, alignment: .top)
                    .transition(.opacity)
                }
            }
            .contentShape(Rectangle())
            .simultaneousGesture(deleteGesture(safeAreaTop: geometry.safeAreaInsets.top))
            .ignoresSafeArea()
        }
        .statusBarHidden(!controlsVisible)
        .persistentSystemOverlays(.hidden)
        .onChange(of: currentIndex) { _, index in
            livePlaybackActive = false
            if deleteProgress == 0, !deletionSettling {
                frozenDeleteReplacementID = nil
            }
            if visiblePhotos.indices.contains(index) { selectedPhotoID = visiblePhotos[index].id }
            if index >= photos.count - 4 { onLoadMore() }
        }
        .onChange(of: visiblePhotos.map(\.id)) { _, photoIDs in
            guard !photoIDs.isEmpty else {
                closeDetail()
                return
            }
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
        .task { await showTutorialsIfNeeded() }
        .task(id: currentIndex) {
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
        .background(PunctumTheme.ink)
    }

    private func isInsideControls(_ location: CGPoint, safeAreaTop: CGFloat) -> Bool {
        controlsVisible && location.y <= safeAreaTop + DetailControls.rowHeight
    }

    private func prepareMetadata(around index: Int) async {
        guard !visiblePhotos.isEmpty else { return }
        let lowerBound = max(index - 4, 0)
        let upperBound = min(index + 4, visiblePhotos.count - 1)
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

    private func deleteGesture(safeAreaTop: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 6, coordinateSpace: .local)
            .onChanged { value in
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
                    if shouldStart, let replacement = deleteReplacement {
                        frozenDeleteReplacementID = replacement.photo.id
                        frozenDeleteReplacementDisplayNumber = min(replacement.index, currentIndex) + 1
                    }
                    deletingGesture = shouldStart
                }
                if deletingGesture {
                    deleteProgress = min(upward / 180, 1)
                }
            }
            .onEnded { _ in
                guard deletingGesture else {
                    deleteProgress = 0
                    return
                }
                deletingGesture = false
                if armed, let photo = currentPhoto {
                    withAnimation(.timingCurve(0.18, 0.74, 0.25, 1, duration: 0.34)) {
                        deleteProgress = 1.55
                    }
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(350))
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
                        }
                    }
                } else {
                    withAnimation(.interactiveSpring(response: 0.30, dampingFraction: 0.88)) {
                        deleteProgress = 0
                    }
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(310))
                        guard deleteProgress == 0, !deletionSettling else { return }
                        frozenDeleteReplacementID = nil
                    }
                }
            }
    }

    private var baseDeleteProgress: CGFloat { min(max(deleteProgress, 0), 1) }
    private var extraDeleteProgress: CGFloat { min(max(deleteProgress - 1, 0), 0.55) / 0.55 }
    private var pageScale: CGFloat { 1 - baseDeleteProgress * 0.10 - extraDeleteProgress * 0.06 }
    private func pageOffset(screenHeight: CGFloat) -> CGFloat {
        -baseDeleteProgress * 180 - extraDeleteProgress * max(screenHeight * 0.82, 520)
    }
    private var pageRadius: CGFloat { baseDeleteProgress * 20 + extraDeleteProgress * 8 }
    private var pageRotation: Double { Double(-baseDeleteProgress * 0.8 - extraDeleteProgress * 0.8) }
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
        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
        livePlaybackActive = false
    }

    private func closeDetail() {
        guard !closing else { return }
        closing = true
        onClose(pendingDeletedPhotos)
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
                        onFrameChange: { imageFrameInPage = $0 },
                        onTopAreaTap: onToggleControls,
                        onDidBeginPlayback: { hasBegunPlayback = true },
                        onDidEndPlayback: {
                            if playbackMode == .playOnce { haltPlayback() }
                        }
                    )
                    DetailMetadataView(
                        metadata: displayedMetadata,
                        displayNumber: displayNumber,
                        onTap: onToggleControls
                    )
                        .padding(.top, 34)
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
                .padding(.top, -safeAreaTop)
            }
            .ignoresSafeArea(edges: .top)
            .scrollBounceBehavior(.basedOnSize)
            .scrollDisabled(interactionLocked)

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
                    onTapCenter: onToggleControls
                )
                .frame(width: screenSize.width)
                .frame(maxHeight: .infinity)
                .zIndex(1)
            }
        }
        .coordinateSpace(name: "detailPage")
        .background(PunctumTheme.ink)
        .task(id: "\(photo.id)-\(isSelected)") {
            guard pagingEnabled || isSelected else { return }
            let loaded = await MetadataService.shared.metadata(for: photo)
            guard !Task.isCancelled else { return }
            metadata = loaded
            if let location = await MetadataService.shared.locationName(for: photo) {
                guard !Task.isCancelled else { return }
                metadata.location = location
            }
        }
        .onAppear {
            if pagingEnabled { loadLivePhoto() }
        }
        .onChange(of: photo.id) { _, _ in
            resetPlayback()
            if pagingEnabled { loadLivePhoto() }
        }
        .onDisappear {
            haltPlayback()
            liveLoadTask?.cancel()
            liveLoadTask = nil
        }
    }

    private func startPlayback(_ mode: LivePlaybackMode) {
        guard photo.isLivePhoto else { return }
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

private struct DetailPhotoFrame: View {
    let photo: PhotoItem
    let screenSize: CGSize
    let safeAreaTop: CGFloat
    let livePhoto: PHLivePhoto?
    let playbackMode: LivePlaybackMode
    let hasBegunPlayback: Bool
    var onFrameChange: (CGRect) -> Void = { _ in }
    var onTopAreaTap: () -> Void = {}
    var onDidBeginPlayback: () -> Void = {}
    var onDidEndPlayback: () -> Void = {}

    private var fadeIn: Bool {
        playbackMode != .none && livePhoto != nil && hasBegunPlayback
    }

    var body: some View {
        let aspect = max(photo.hasKnownSize ? photo.aspectRatio : 1.5, 0.1)
        let imageHeight = screenSize.width / aspect
        let topPadding = aspect < 1 ? 0 : max((screenSize.height - imageHeight) * 0.5 - 40 + safeAreaTop, 0)

        ZStack(alignment: .bottomTrailing) {
            if photo.isLivePhoto {
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
        .frame(width: screenSize.width, height: imageHeight)
        .onGeometryChange(for: CGRect.self) { geo in
            geo.frame(in: .named("detailPage"))
        } action: { frame in
            onFrameChange(frame)
        }
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
