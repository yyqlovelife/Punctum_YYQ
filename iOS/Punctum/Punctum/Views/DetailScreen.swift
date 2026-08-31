import Photos
import SwiftUI
import UIKit

struct DetailScreen: View {
    let photos: [PhotoItem]
    let startIndex: Int
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
    @State private var deleteSnapshot: UIImage?

    init(
        photos: [PhotoItem],
        startIndex: Int,
        currentGalleryID: String,
        onClose: @escaping ([PhotoItem]) -> Void,
        onMoveInLibrary: @escaping (PhotoItem, AlbumOption) async throws -> Void,
        onCommitMove: @escaping (PhotoItem, AlbumOption) -> Void,
        onLoadMore: @escaping () -> Void = {}
    ) {
        self.photos = photos
        self.startIndex = startIndex
        self.currentGalleryID = currentGalleryID
        self.onClose = onClose
        self.onMoveInLibrary = onMoveInLibrary
        self.onCommitMove = onCommitMove
        self.onLoadMore = onLoadMore
        let initialIndex = min(max(startIndex, 0), max(photos.count - 1, 0))
        _currentIndex = State(initialValue: initialIndex)
        _selectedPhotoID = State(initialValue: photos.indices.contains(initialIndex) ? photos[initialIndex].id : nil)
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

    private var armed: Bool { deleteProgress >= 0.72 }

    var body: some View {
        GeometryReader { geometry in
            let screenSize = CGSize(
                width: geometry.size.width + geometry.safeAreaInsets.leading + geometry.safeAreaInsets.trailing,
                height: geometry.size.height + geometry.safeAreaInsets.top + geometry.safeAreaInsets.bottom
            )
            ZStack {
                PunctumTheme.ink.ignoresSafeArea()

                if deleteProgress > 0, let nextPhoto {
                    DetailPage(
                        photo: nextPhoto,
                        displayNumber: min(currentIndex + 2, visiblePhotos.count),
                        screenSize: screenSize,
                        safeAreaTop: geometry.safeAreaInsets.top,
                        livePlaybackActive: .constant(false),
                        pagingEnabled: false,
                        onPrevious: {},
                        onNext: {},
                        onToggleControls: {}
                    )
                    .allowsHitTesting(false)
                    .overlay(Color.black.opacity(0.48 * (1 - revealProgress)))
                }

                if deleteProgress == 0, !visiblePhotos.isEmpty {
                    TabView(selection: $currentIndex) {
                        ForEach(Array(visiblePhotos.enumerated()), id: \.element.id) { index, photo in
                            DetailPage(
                                photo: photo,
                                displayNumber: index + 1,
                                screenSize: screenSize,
                                safeAreaTop: geometry.safeAreaInsets.top,
                                livePlaybackActive: $livePlaybackActive,
                                pagingEnabled: true,
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
                    .background(PagingScrollLock(locked: livePlaybackActive))
                } else if deleteProgress > 0 {
                    deleteLiftView(screenSize: screenSize)
                        .scaleEffect(pageScale)
                        .offset(y: pageOffset)
                        .rotationEffect(.degrees(pageRotation))
                        .clipShape(RoundedRectangle(cornerRadius: pageRadius, style: .continuous))
                        .shadow(color: .black.opacity(0.5), radius: 22, y: 12)
                        .allowsHitTesting(false)
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
        .onChange(of: deleteProgress) { _, progress in
            if progress == 0 { deleteSnapshot = nil }
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
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
        }
        .task { await showTutorialsIfNeeded() }
        .sheet(item: $sharePayload) { payload in
            ShareSheet(items: [payload.url])
        }
        .sheet(isPresented: $showMovePicker) {
            AlbumPickerView(
                existingIDs: [],
                currentGalleryID: currentGalleryID,
                allowsMultipleSelection: false,
                onConfirm: { _ in },
                onSelect: moveCurrentPhoto(to:)
            )
            .presentationDragIndicator(.hidden)
        }
        .background(PunctumTheme.ink)
    }

    private func isInsideControls(_ location: CGPoint, safeAreaTop: CGFloat) -> Bool {
        controlsVisible && location.y <= safeAreaTop + DetailControls.rowHeight
    }

    private func deleteGesture(safeAreaTop: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .local)
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
                    if shouldStart, deleteSnapshot == nil {
                        deleteSnapshot = captureInterfaceSnapshot()
                    }
                    deletingGesture = shouldStart
                }
                if deletingGesture {
                    deleteProgress = min(upward / 180, 1.55)
                }
            }
            .onEnded { _ in
                guard deletingGesture else {
                    deleteProgress = 0
                    return
                }
                deletingGesture = false
                if armed, let photo = currentPhoto {
                    let nextID = nextPhoto?.id
                    withAnimation(.easeIn(duration: 0.26)) { deleteProgress = 1.55 }
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(270))
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            if let nextID { selectedPhotoID = nextID }
                            queueDeletion(photo)
                            deleteProgress = 0
                            deleteSnapshot = nil
                        }
                    }
                } else {
                    withAnimation(.easeOut(duration: 0.235)) { deleteProgress = 0 }
                }
            }
    }

    private var baseDeleteProgress: CGFloat { min(max(deleteProgress, 0), 1) }
    private var extraDeleteProgress: CGFloat { min(max(deleteProgress - 1, 0), 0.55) / 0.55 }
    private var pageScale: CGFloat { 1 - baseDeleteProgress * 0.28 - extraDeleteProgress * 0.26 }
    private var pageOffset: CGFloat { -baseDeleteProgress * 132 - extraDeleteProgress * 58 }
    private var pageRadius: CGFloat { baseDeleteProgress * 24 + extraDeleteProgress * 10 }
    private var pageRotation: Double { Double(-baseDeleteProgress * 1.2 - extraDeleteProgress * 1.4) }
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

    @ViewBuilder
    private func deleteLiftView(screenSize: CGSize) -> some View {
        if let deleteSnapshot {
            Image(uiImage: deleteSnapshot)
                .resizable()
                .scaledToFill()
                .frame(width: screenSize.width, height: screenSize.height)
                .clipped()
        } else if let currentPhoto {
            Color.clear
                .overlay {
                    DetailPage(
                        photo: currentPhoto,
                        displayNumber: currentIndex + 1,
                        screenSize: screenSize,
                        safeAreaTop: 0,
                        livePlaybackActive: .constant(false),
                        pagingEnabled: false
                    )
                }
                .frame(width: screenSize.width, height: screenSize.height)
                .clipped()
        }
    }

    private func captureInterfaceSnapshot() -> UIImage? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        guard let window else { return nil }
        let format = UIGraphicsImageRendererFormat()
        format.scale = window.screen.scale
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(bounds: window.bounds, format: format)
        return renderer.image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: false)
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
        if defaults.integer(forKey: "quick_page_toast_count") < 2 {
            try? await Task.sleep(for: .milliseconds(500))
            showMessage("单击屏幕左/右边缘，支持快速切换前/后图片", duration: .seconds(4))
            defaults.set(defaults.integer(forKey: "quick_page_toast_count") + 1, forKey: "quick_page_toast_count")
            try? await Task.sleep(for: .seconds(5))
        }
        if defaults.integer(forKey: "swipe_delete_toast_count") < 2 {
            showMessage("上滑页面，支持快速删除图片", duration: .seconds(4))
            defaults.set(defaults.integer(forKey: "swipe_delete_toast_count") + 1, forKey: "swipe_delete_toast_count")
        }
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
            controlButton("xmark", label: "返回", color: PunctumTheme.bone, action: onClose)
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
            .buttonStyle(.plain)
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
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private enum DetailLiveBadge {
    static let visualPadding: CGFloat = 6
    static let hotSize: CGFloat = 44
}

private struct DetailImageFrameKey: PreferenceKey {
    static var defaultValue: CGRect = .zero
    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}

private struct DetailPage: View {
    let photo: PhotoItem
    let displayNumber: Int
    let screenSize: CGSize
    let safeAreaTop: CGFloat
    @Binding var livePlaybackActive: Bool
    var pagingEnabled: Bool = true
    var onPrevious: () -> Void = {}
    var onNext: () -> Void = {}
    var onToggleControls: () -> Void = {}
    @State private var metadata = PhotoMetadata()
    @State private var imageFrameInPage: CGRect = .zero
    @State private var livePhoto: PHLivePhoto?
    @State private var playbackMode: LivePlaybackMode = .none
    @State private var hasBegunPlayback = false
    @State private var liveLoadTask: Task<Void, Never>?

    private var badgeHotRect: CGRect {
        guard photo.isLivePhoto, imageFrameInPage.width > 1 else { return .null }
        return CGRect(
            x: imageFrameInPage.maxX - DetailLiveBadge.hotSize,
            y: imageFrameInPage.maxY - DetailLiveBadge.hotSize,
            width: DetailLiveBadge.hotSize,
            height: DetailLiveBadge.hotSize
        )
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
                        onDidBeginPlayback: { hasBegunPlayback = true },
                        onDidEndPlayback: {
                            if playbackMode == .playOnce { haltPlayback() }
                        }
                    )
                    DetailMetadataView(metadata: metadata, displayNumber: displayNumber)
                        .padding(.top, 34)
                }
                .frame(maxWidth: .infinity, alignment: .topLeading)
                .padding(.top, -safeAreaTop)
            }
            .ignoresSafeArea(edges: .top)
            .scrollBounceBehavior(.basedOnSize)
            .scrollDisabled(livePlaybackActive)

            if pagingEnabled {
                LiveHoldCatcher(
                    holdEnabled: photo.isLivePhoto,
                    pagingEnabled: true,
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
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .coordinateSpace(name: "detailPage")
        .background(PunctumTheme.ink)
        .onPreferenceChange(DetailImageFrameKey.self) { imageFrameInPage = $0 }
        .task(id: photo.id) {
            metadata = await MetadataService.shared.metadata(for: photo)
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
        if mode == .hold {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
        playbackMode = mode
        livePlaybackActive = true
    }

    private func haltPlayback() {
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
        .background {
            GeometryReader { geo in
                Color.clear.preference(key: DetailImageFrameKey.self, value: geo.frame(in: .named("detailPage")))
            }
        }
        .padding(.top, topPadding)
    }
}

private struct DetailMetadataView: View {
    let metadata: PhotoMetadata
    let displayNumber: Int

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
