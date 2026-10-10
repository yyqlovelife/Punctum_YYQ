import Foundation
import Photos
import SwiftUI

@MainActor
final class GalleryViewModel: NSObject, ObservableObject, PHPhotoLibraryChangeObserver {
    @Published private(set) var galleries: [PunctumGallery]
    @Published private(set) var overviews: [String: GalleryOverview] = [:]
    @Published private(set) var photos: [PhotoItem] = []
    @Published private(set) var isSortingPhotos = false
    static let photoSortHint = """
    可在以下三种模式之间切换图片顺序

    拍摄：按真实拍摄时间排序
    编辑：按编辑过的保存时间排序
    同步：按系统相册自定义的位置排序
    """
    @Published private(set) var isLoading = false
    @Published var currentGalleryID: String?
    @Published var showSwitcher = false
    @Published var showAlbumPicker = false
    @Published var detailIndex: Int?
    @Published var detailVisible = false
    @Published private(set) var galleryReturnTargetID: String?
    @Published var galleryReadyID: String?
    @Published private(set) var detailSession = UUID()
    private var detailEntryVisibleIDs: Set<String> = []
    private var pendingDetailClosePhotos: [PhotoItem] = []
    @Published private(set) var detailInitialMetadata: PhotoMetadata?
    @Published var invitationStyle: InvitationCardStyle
    @Published var permissionMessage: String?
    @Published var transientMessage: String?
    @Published var pendingDeletionRequest: PendingPhotoDeletion?
    @Published var pendingHomeScrollID: String?

    let homeSubtitle: String

    private let store: GalleryStore
    private let library: PhotoLibraryService
    private let imageCache: GalleryImageCache
    private var overviewSnapshots: [String: GalleryOverviewSnapshot]
    private var snapshotSaveTask: Task<Void, Never>?
    private var coverBuildTasks: [String: Task<Void, Never>] = [:]
    private var expectedCoverIDs: [String: [String]] = [:]
    private var deleteTombstones = PhotoDeletionTombstones()
    private var hiddenAssetIDs: [String: Set<String>]
    private var overviewTasks: [String: Task<Void, Never>] = [:]
    private var refreshGeneration = 0
    private var allRefreshTask: Task<Void, Never>?
    private var refreshTask: Task<Void, Never>?
    private var gallerySelectionTask: Task<Void, Never>?
    private var visibleGalleryRefreshTask: Task<Void, Never>?
    private var visibleGalleryRefreshRequested = false
    private var visibleGalleryRefreshGeneration = 0
    private var lastKnownAuthorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    private var galleryLoadGeneration = 0
    private var galleryFetchNextIndex = 0
    @Published private(set) var galleryFetchExhausted = true
    private var isLoadingMorePhotos = false
    @Published private(set) var isDeletingPhotos = false
    private var isApplicationActive = true

    private static let deleteTombstoneDuration: TimeInterval = 120
    private static let galleryPageCount = 80

    var currentGallery: PunctumGallery? {
        guard let currentGalleryID else { return nil }
        return galleries.first { $0.id == currentGalleryID }
    }

    override convenience init() {
        self.init(store: GalleryStore(), library: .shared, imageCache: .shared)
    }

    init(
        store: GalleryStore,
        library: PhotoLibraryService,
        imageCache: GalleryImageCache,
        startLibraryUpdates: Bool = true
    ) {
        self.store = store
        self.library = library
        self.imageCache = imageCache
        self.galleries = store.loadGalleries()
        self.overviewSnapshots = store.loadOverviewSnapshots()
        self.hiddenAssetIDs = store.loadHiddenAssetIDs()
        self.invitationStyle = store.loadInvitationStyle()
        self.homeSubtitle = Self.homeSubtitles.randomElement() ?? Self.homeSubtitles[0]
        super.init()
        if let override = Self.launchStyleOverride() {
            invitationStyle = override
            store.saveInvitationStyle(override)
        }
        hydrateOverviewSnapshots()
        guard startLibraryUpdates else { return }
        PHPhotoLibrary.shared().register(self)
        Task { [weak self] in
            guard let self else { return }
            // Give the cached home screen a frame before starting Photos work.
            try? await Task.sleep(for: .milliseconds(100))
            if self.library.authorizationStatus == .notDetermined {
                _ = await self.library.requestAuthorization()
                self.hydrateOverviewSnapshots()
            }
            Task { [weak self] in await self?.hydrateCachedCovers() }
            await CaptureDateIndex.shared.loadIfNeeded()
            self.refreshAll()
            await LivePhotoSeeder.seedIfNeeded()
        }
        if ProcessInfo.processInfo.arguments.contains("-openFirstGallery") {
            DispatchQueue.main.async { [weak self] in
                guard let self, let first = self.galleries.first else { return }
                self.selectGallery(first.id)
            }
        }
        if ProcessInfo.processInfo.arguments.contains("-openFirstDetail") {
            Task { [weak self] in
                try? await Task.sleep(nanoseconds: 1_200_000_000)
                await MainActor.run {
                    guard let self, let first = self.galleries.first else { return }
                    self.selectGallery(first.id)
                }
                for _ in 0..<8 {
                    try? await Task.sleep(nanoseconds: 400_000_000)
                    let ready = await MainActor.run { self?.photos.isEmpty == false }
                    if ready == true { break }
                }
                guard let photo = await MainActor.run(body: { self?.photos.first }) else { return }
                let metadata = await MetadataService.shared.metadata(for: photo)
                await MainActor.run {
                    if let galleryID = self?.currentGalleryID {
                        self?.openDetail(photoID: photo.id, in: galleryID, metadata: metadata)
                    }
                }
            }
        }
        if ProcessInfo.processInfo.arguments.contains("-openAlbumPicker") {
            DispatchQueue.main.async { [weak self] in
                self?.requestAlbumPicker()
            }
        }
    }

    private static func launchStyleOverride() -> InvitationCardStyle? {
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "-punctumStyle"), args.indices.contains(index + 1) {
            return InvitationCardStyle(rawValue: args[index + 1])
        }
        if let combined = args.first(where: { $0.hasPrefix("-punctumStyle=") }) {
            return InvitationCardStyle(rawValue: String(combined.dropFirst("-punctumStyle=".count)))
        }
        if let env = ProcessInfo.processInfo.environment["PUNCTUM_STYLE"] {
            return InvitationCardStyle(rawValue: env)
        }
        return nil
    }

    func requestAlbumPicker() {
        Task {
            let status: PHAuthorizationStatus
            if library.authorizationStatus == .notDetermined {
                status = await library.requestAuthorization()
            } else {
                status = library.authorizationStatus
            }
            switch status {
            case .authorized, .limited:
                showAlbumPicker = true
            case .denied, .restricted:
                permissionMessage = "请在系统设置中允许 Punctum 访问照片后使用"
            case .notDetermined:
                break
            @unknown default:
                permissionMessage = "暂时无法访问系统照片"
            }
        }
    }

    func addAlbum(_ option: AlbumOption) {
        addAlbums([option])
    }

    func addAlbums(_ options: [AlbumOption]) {
        let existing = Set(galleries.map(\.id))
        let insertAt = galleries.count
        let added = options.compactMap { option -> PunctumGallery? in
            guard !existing.contains(option.id) else { return nil }
            return PunctumGallery(id: option.id, displayName: option.title)
        }
        if !added.isEmpty {
            galleries.append(contentsOf: added)
            persistGalleries()
            pendingHomeScrollID = galleries[insertAt].id
            for gallery in added { refreshOverview(for: gallery) }
        }
        showAlbumPicker = false
        currentGalleryID = nil
        showSwitcher = true
        detailIndex = nil
        transientMessage = "添加完成"
    }

    func dismissPhotoSortHint() {
        if transientMessage == Self.photoSortHint { transientMessage = nil }
    }

    private func cancelVisibleGalleryRefresh() {
        visibleGalleryRefreshGeneration += 1
        visibleGalleryRefreshTask?.cancel()
        visibleGalleryRefreshTask = nil
        visibleGalleryRefreshRequested = false
    }

    func selectGallery(_ id: String) {
        dismissPhotoSortHint()
        cancelVisibleGalleryRefresh()
        guard let gallery = galleries.first(where: { $0.id == id }) else { return }
        isSortingPhotos = false
        gallerySelectionTask?.cancel()
        library.unpinCaptureOrder()
        let switching = currentGalleryID != id
        currentGalleryID = id
        showSwitcher = false
        galleryReadyID = id
        detailIndex = nil
        if switching {
            photos = []
            isLoading = true
        }
        gallerySelectionTask = Task { [weak self] in
            // Let the pressed card release and the gallery loading state render first.
            try? await Task.sleep(for: .milliseconds(20))
            guard let self, !Task.isCancelled else { return }
            await self.library.pinCaptureOrderAsync(for: gallery)
            guard !Task.isCancelled, self.currentGalleryID == id, !self.showSwitcher else { return }
            self.gallerySelectionTask = nil
            self.loadCurrentGallery()
            self.refreshVisibleGalleryIfNeeded()
        }
    }

    func openSwitcher() {
        dismissPhotoSortHint()
        cancelVisibleGalleryRefresh()
        isSortingPhotos = false
        gallerySelectionTask?.cancel()
        gallerySelectionTask = nil
        library.unpinCaptureOrder()
        detailIndex = nil
        showSwitcher = true
        refreshAll()
    }

    func closeSwitcher() {
        showSwitcher = false
    }

    func togglePhotoSort() {
        guard var gallery = currentGallery, !isLoading, !isSortingPhotos,
              detailIndex == nil, !isDeletingPhotos, pendingDeletionRequest == nil else { return }
        if store.consumePhotoSortHint() { transientMessage = Self.photoSortHint }
        gallery.sortOrder = gallery.sortOrder.next
        cancelVisibleGalleryRefresh()
        gallerySelectionTask?.cancel()
        isSortingPhotos = true
        gallerySelectionTask = Task { [weak self] in
            guard let self else { return }
            await library.pinCaptureOrderAsync(for: gallery)
            guard !Task.isCancelled, currentGalleryID == gallery.id, !showSwitcher,
                  let index = galleries.firstIndex(where: { $0.id == gallery.id }) else { return }
            galleries[index].sortOrder = gallery.sortOrder
            persistGalleries()
            galleryReturnTargetID = nil
            gallerySelectionTask = nil
            loadCurrentGallery()
            isSortingPhotos = false
            refreshVisibleGalleryIfNeeded()
        }
    }

    func toggleInvitationStyle() {
        invitationStyle.toggle()
        store.saveInvitationStyle(invitationStyle)
    }

    func renameGallery(_ gallery: PunctumGallery, to newName: String) {
        let trimmed = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              let index = galleries.firstIndex(where: { $0.id == gallery.id }) else { return }
        galleries[index].displayName = trimmed
        persistGalleries()
        refreshOverview(for: galleries[index])
    }

    func moveGallery(from source: Int, to destination: Int) {
        guard galleries.indices.contains(source), galleries.indices.contains(destination), source != destination else { return }
        let gallery = galleries.remove(at: source)
        galleries.insert(gallery, at: destination)
        persistGalleries()
    }

    func removeGallery(at index: Int) {
        guard galleries.indices.contains(index) else { return }
        let removed = galleries.remove(at: index)
        coverBuildTasks.removeValue(forKey: removed.id)?.cancel()
        expectedCoverIDs.removeValue(forKey: removed.id)
        overviewSnapshots.removeValue(forKey: removed.id)
        overviews.removeValue(forKey: removed.id)
        if currentGalleryID == removed.id {
            isSortingPhotos = false
        gallerySelectionTask?.cancel()
            gallerySelectionTask = nil
            library.unpinCaptureOrder()
            currentGalleryID = nil
            photos = []
            detailIndex = nil
            showSwitcher = false
        }
        persistGalleries()
        store.saveOverviewSnapshots(overviewSnapshots)
    }

    func openDetail(at index: Int, metadata: PhotoMetadata, entryVisibleIDs: Set<String> = []) {
        guard photos.indices.contains(index), let currentGalleryID else { return }
        openDetail(photoID: photos[index].id, in: currentGalleryID, metadata: metadata, entryVisibleIDs: entryVisibleIDs)
    }

    func openDetail(photoID: String, in galleryID: String, metadata: PhotoMetadata, entryVisibleIDs: Set<String> = []) {
        guard !isSortingPhotos, !showSwitcher, detailIndex == nil,
              !isDeletingPhotos, pendingDeletionRequest == nil,
              let index = GalleryPhotoSelection.index(
                for: photoID, in: photos, expectedGalleryID: galleryID, currentGalleryID: currentGalleryID
              ) else { return }
        dismissPhotoSortHint()
        // Freeze the visit snapshot before opening detail. A foreground fetch that
        // finishes later must not replace the pager's order halfway through a visit.
        if visibleGalleryRefreshTask != nil {
            visibleGalleryRefreshGeneration += 1
            visibleGalleryRefreshTask?.cancel()
            visibleGalleryRefreshTask = nil
            visibleGalleryRefreshRequested = true
        }
        if let gallery = currentGallery { library.pinCaptureOrder(for: gallery) }
        detailEntryVisibleIDs = entryVisibleIDs.isEmpty ? [photos[index].id] : entryVisibleIDs
        galleryReturnTargetID = nil
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            detailInitialMetadata = metadata
            detailVisible = false
            detailSession = UUID()
            detailIndex = index
        }
    }

    func closeDetail(pendingPhotos: [PhotoItem], viewedID: String?, fallbackIndex: Int) {
        guard detailVisible else { return }
        let targetID = GalleryReturnPosition.targetID(
            ids: photos.map(\.id), viewedID: viewedID, fallbackIndex: fallbackIndex,
            excluding: Set(pendingPhotos.map(\.id))
        )
        if let targetID,
           GalleryReturnPosition.shouldCenter(targetID, entryVisibleIDs: detailEntryVisibleIDs) {
            pendingDetailClosePhotos = pendingPhotos
            galleryReturnTargetID = targetID
            let session = detailSession
            Task { [weak self] in
                try? await Task.sleep(for: .milliseconds(300))
                guard let self, self.detailSession == session,
                      self.galleryReturnTargetID == targetID else { return }
                self.finishGalleryReturnPositioning(targetID)
            }
            return
        }
        beginDismissingDetail(pendingPhotos: pendingPhotos)
    }

    func finishGalleryReturnPositioning(_ targetID: String) {
        guard galleryReturnTargetID == targetID else { return }
        galleryReturnTargetID = nil
        let pendingPhotos = pendingDetailClosePhotos
        pendingDetailClosePhotos = []
        beginDismissingDetail(pendingPhotos: pendingPhotos)
    }

    private func beginDismissingDetail(pendingPhotos: [PhotoItem]) {
        detailVisible = false
        let session = detailSession
        Task {
            try? await Task.sleep(for: .milliseconds(140))
            guard detailSession == session else { return }
            finishClosingDetail(pendingPhotos: pendingPhotos)
        }
    }

    func revealDetail() { detailVisible = true }

    private func finishClosingDetail(pendingPhotos: [PhotoItem]) {
        // Keep the gallery visit snapshot after returning from detail.
        detailIndex = nil
        detailInitialMetadata = nil
        detailEntryVisibleIDs = []
        guard !pendingPhotos.isEmpty else {
            refreshVisibleGalleryIfNeeded()
            return
        }

        var seen = Set<String>()
        let uniquePhotos = pendingPhotos.filter { seen.insert($0.id).inserted }
        pendingDeletionRequest = PendingPhotoDeletion(photos: uniquePhotos)
    }

    func cancelPendingDeletion(_ request: PendingPhotoDeletion) {
        guard pendingDeletionRequest?.id == request.id else { return }
        pendingDeletionRequest = nil
        refreshVisibleGalleryIfNeeded()
    }

    func confirmPendingDeletion(_ request: PendingPhotoDeletion) {
        guard pendingDeletionRequest?.id == request.id, !isDeletingPhotos else { return }
        isDeletingPhotos = true
        refreshTask?.cancel()
        refreshGeneration += 1
        overviewTasks.values.forEach { $0.cancel() }
        coverBuildTasks.values.forEach { $0.cancel() }
        coverBuildTasks.removeAll()
        pendingDeletionRequest = nil
        Task {
            do {
                defer {
                    isDeletingPhotos = false
                    scheduleRefresh()
                }
                try await library.delete(request.photos)
                markDeleting(request.photos)
                let ids = Set(request.photos.map(\.id))
                var transaction = Transaction(); transaction.disablesAnimations = true
                withTransaction(transaction) { photos.removeAll { ids.contains($0.id) } }
                updateCurrentOverview()
            } catch {
                clearTombstones(for: request.photos)
                let photosError = error as NSError
                if photosError.domain != PHPhotosErrorDomain ||
                    photosError.code != PHPhotosError.userCancelled.rawValue {
                    transientMessage = error.localizedDescription
                }
                loadCurrentGallery()
            }
        }
    }

    func commitComparisonDeletion(_ photo: PhotoItem) {
        markDeleting([photo])
        var transaction = Transaction(); transaction.disablesAnimations = true
        withTransaction(transaction) {
            photos.removeAll { $0.id == photo.id }
            if photos.isEmpty { detailIndex = nil }
            else if let detailIndex { self.detailIndex = min(detailIndex, photos.count - 1) }
        }
        updateCurrentOverview()
        for gallery in galleries where gallery.id != currentGalleryID { refreshOverview(for: gallery) }
    }

    func deletePhoto(_ photo: PhotoItem) {
        guard !isDeletingPhotos, pendingDeletionRequest == nil else { return }
        let request = PendingPhotoDeletion(photos: [photo])
        pendingDeletionRequest = request
        confirmPendingDeletion(request)
    }

    func movePhoto(_ photo: PhotoItem, to destination: AlbumOption) async throws {
        try await movePhotoInLibrary(photo, to: destination)
        commitMovedPhoto(photo, to: destination)
    }

    func movePhotoInLibrary(_ photo: PhotoItem, to destination: AlbumOption) async throws {
        guard let source = currentGallery, source.id != destination.id else {
            throw PhotoLibraryError.moveFailed
        }
        try await library.move(photo, from: source, to: destination.collection)
    }

    func commitMovedPhoto(_ photo: PhotoItem, to destination: AlbumOption) {
        guard let source = currentGallery else { return }
        var sourceHidden = hiddenAssetIDs[source.id] ?? []
        sourceHidden.insert(photo.id)
        hiddenAssetIDs[source.id] = sourceHidden
        hiddenAssetIDs[destination.id]?.remove(photo.id)
        if hiddenAssetIDs[destination.id]?.isEmpty == true {
            hiddenAssetIDs.removeValue(forKey: destination.id)
        }
        store.saveHiddenAssetIDs(hiddenAssetIDs)

        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            photos.removeAll { $0.id == photo.id }
            if photos.isEmpty {
                detailIndex = nil
                transientMessage = "该项目已移动到 \(destination.title)"
            } else if let detailIndex {
                self.detailIndex = min(detailIndex, photos.count - 1)
            }
        }
        updateCurrentOverview()
        if let destGallery = galleries.first(where: { $0.id == destination.id }) {
            refreshOverview(for: destGallery)
        }
    }

    func refreshAll() {
        guard isApplicationActive, !isDeletingPhotos else { return }
        guard library.authorizationStatus == .authorized || library.authorizationStatus == .limited else { return }
        refreshGeneration += 1
        let generation = refreshGeneration
        allRefreshTask?.cancel()
        pruneExpiredTombstones()
        if currentGallery != nil, !showSwitcher, gallerySelectionTask == nil {
            requestVisibleGalleryRefresh()
        }
        let candidates = galleries
        allRefreshTask = Task { [weak self] in
            guard let self else { return }
            await CaptureDateIndex.shared.loadIfNeeded()
            let validIDs = await Task.detached(priority: .utility) {
                Set(candidates.compactMap { gallery in
                    PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [gallery.id], options: nil).firstObject == nil
                        ? nil : gallery.id
                })
            }.value
            guard !Task.isCancelled, generation == refreshGeneration else { return }
            let candidateIDs = Set(candidates.map(\.id))
            let valid = galleries.filter { !candidateIDs.contains($0.id) || validIDs.contains($0.id) }
            if valid != galleries {
                galleries = valid
                persistGalleries()
            }
            for gallery in galleries {
                guard !Task.isCancelled, generation == refreshGeneration, !isDeletingPhotos else { return }
                if overviews[gallery.id]?.postcardCoverPath == nil
                    || overviews[gallery.id]?.ticketCoverPath == nil {
                    refreshOverview(for: gallery)
                }
                await library.prepareCaptureOrder(for: gallery)
                guard !Task.isCancelled, generation == refreshGeneration, !isDeletingPhotos else { return }
                refreshOverview(for: gallery)
                if currentGalleryID == gallery.id, !showSwitcher, gallerySelectionTask == nil, detailIndex == nil,
                   currentGallery?.sortOrder != .sync {
                    requestVisibleGalleryRefresh()
                }
                await Task.yield()
            }
        }
    }

    func appDidEnterBackground() {
        isApplicationActive = false
        refreshTask?.cancel()
        allRefreshTask?.cancel()
    }

    func appDidBecomeActive() {
        isApplicationActive = true
        // Missed Photos notifications affect every order, including deletion.
        // Keep the visible snapshot until the fresh asynchronous fetch is ready.
        library.invalidateCaptureOrder()
        requestVisibleGalleryRefresh()
        let status = library.authorizationStatus
        guard status != lastKnownAuthorization else { return }
        lastKnownAuthorization = status
        scheduleRefresh()
    }

    nonisolated func photoLibraryDidChange(_ changeInstance: PHChange) {
        Task { @MainActor [weak self] in
            self?.library.invalidateCaptureOrder()
            self?.requestVisibleGalleryRefresh()
            self?.scheduleRefresh()
        }
    }

    private func requestVisibleGalleryRefresh() {
        guard currentGallery != nil, !showSwitcher else { return }
        visibleGalleryRefreshRequested = true
        refreshVisibleGalleryIfNeeded()
    }

    private func refreshVisibleGalleryIfNeeded() {
        guard isApplicationActive, visibleGalleryRefreshRequested, let gallery = currentGallery,
              !showSwitcher, detailIndex == nil, pendingDeletionRequest == nil, !isDeletingPhotos,
              !isSortingPhotos, gallerySelectionTask == nil,
              library.authorizationStatus == .authorized || library.authorizationStatus == .limited else { return }
        visibleGalleryRefreshGeneration += 1
        let generation = visibleGalleryRefreshGeneration
        visibleGalleryRefreshTask?.cancel()
        visibleGalleryRefreshTask = Task { [weak self] in
            guard let self else { return }
            await library.pinCaptureOrderAsync(for: gallery)
            guard !Task.isCancelled, generation == visibleGalleryRefreshGeneration else { return }
            visibleGalleryRefreshTask = nil
            guard currentGalleryID == gallery.id, currentGallery?.sortOrder == gallery.sortOrder,
                  isApplicationActive, !showSwitcher, detailIndex == nil, pendingDeletionRequest == nil,
                  !isDeletingPhotos, !isSortingPhotos, gallerySelectionTask == nil else { return }
            visibleGalleryRefreshRequested = false
            loadCurrentGallery()
        }
    }

    private func scheduleRefresh() {
        refreshTask?.cancel()
        refreshTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled, let self else { return }
            guard self.isApplicationActive, !self.isDeletingPhotos else { return }
            self.refreshAll()
        }
    }

    private func loadCurrentGallery() {
        guard detailIndex == nil, pendingDeletionRequest == nil, !isDeletingPhotos else { return }
        guard let gallery = currentGallery else {
            photos = []
            isLoading = false
            galleryFetchNextIndex = 0
            galleryFetchExhausted = true
            return
        }
        galleryLoadGeneration += 1
        let generation = galleryLoadGeneration
        let excluded = excludedPhotoIDs(for: gallery)
        let keepExistingList = !photos.isEmpty
        if !keepExistingList { isLoading = true }

        let pageLimit = keepExistingList ? max(photos.count, Self.galleryPageCount) : Self.galleryPageCount
        let page = library.fetchPhotoPage(
            in: gallery,
            excluding: excluded,
            startIndex: 0,
            limit: pageLimit
        )
        guard generation == galleryLoadGeneration else { return }
        galleryFetchNextIndex = page.nextIndex
        galleryFetchExhausted = page.nextIndex >= page.total
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            photos = keepExistingList ? mergePhotos(current: photos, incoming: page.photos) : page.photos
        }
        imageCache.startCaching(
            Array(photos.prefix(16)),
            targetSize: CGSize(width: 900, height: 900)
        )
        refreshOverview(for: gallery)
        isLoading = false
    }

    func loadMorePhotos() {
        guard let gallery = currentGallery,
              !isLoading,
              !isSortingPhotos,
              !isLoadingMorePhotos,
              !isDeletingPhotos,
              pendingDeletionRequest == nil,
              !galleryFetchExhausted else { return }
        isLoadingMorePhotos = true
        let generation = galleryLoadGeneration
        let excluded = excludedPhotoIDs(for: gallery)
        let page = library.fetchPhotoPage(
            in: gallery,
            excluding: excluded,
            startIndex: galleryFetchNextIndex,
            limit: Self.galleryPageCount
        )
        guard generation == galleryLoadGeneration, currentGalleryID == gallery.id else {
            isLoadingMorePhotos = false
            return
        }
        galleryFetchNextIndex = page.nextIndex
        galleryFetchExhausted = page.nextIndex >= page.total
        let existingIDs = Set(photos.map(\.id))
        let appended = page.photos.filter { existingIDs.contains($0.id) == false }
        if !appended.isEmpty {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) {
                photos.append(contentsOf: appended)
            }
            imageCache.startCaching(
                Array(appended.prefix(16)),
                targetSize: CGSize(width: 900, height: 900)
            )
        }
        isLoadingMorePhotos = false
    }

    private func updateCurrentOverview() {
        guard let gallery = currentGallery else { return }
        refreshOverview(for: gallery)
    }

    private func refreshOverview(for gallery: PunctumGallery) {
        overviewTasks[gallery.id]?.cancel()
        let excluded = excludedPhotoIDs(for: gallery)
        overviewTasks[gallery.id] = Task { [weak self] in
            guard let self else { return }
            let overview = await library.overview(for: gallery, excluding: excluded)
            guard !Task.isCancelled, galleries.contains(where: { $0.id == gallery.id }) else { return }
            applyOverview(overview, for: gallery)
            overviewTasks[gallery.id] = nil
        }
    }

    private func applyOverview(_ result: GalleryOverview?, for gallery: PunctumGallery) {
        guard var overview = result else {
            coverBuildTasks.removeValue(forKey: gallery.id)?.cancel()
            expectedCoverIDs.removeValue(forKey: gallery.id)
            overviews.removeValue(forKey: gallery.id)
            return
        }

        let coverIDs = overview.covers.map(\.id)
        let previous = overviews[gallery.id]
        let cached = overviewSnapshots[gallery.id]
        let usePrevious = previous?.covers.isEmpty == false
        let cachedIDs = usePrevious ? previous?.covers.map(\.id) : cached?.coverAssetIDs
        let cachedPostcard = usePrevious ? previous?.postcardCoverPath : cached?.postcardCoverPath
        let cachedTicket = usePrevious ? previous?.ticketCoverPath : cached?.ticketCoverPath
        let cachedColor = usePrevious ? previous?.ticketDominantColorARGB : cached?.ticketDominantColorARGB
        let cachedVersion = usePrevious ? previous?.ticketColorVersion : cached?.ticketColorVersion
        let canReuseAssets = cachedIDs == coverIDs
            && imageCache.isValidCacheFile(cachedPostcard)
            && imageCache.isValidCacheFile(cachedTicket)
            && cachedColor != nil
            && cachedVersion == GalleryImageCache.ticketColorVersion

        if canReuseAssets {
            overview.postcardCoverPath = cachedPostcard
            overview.ticketCoverPath = cachedTicket
            overview.ticketDominantColorARGB = cachedColor
            overview.ticketColorVersion = GalleryImageCache.ticketColorVersion
        }
        overviews[gallery.id] = overview
        expectedCoverIDs[gallery.id] = coverIDs

        if coverIDs.isEmpty {
            coverBuildTasks.removeValue(forKey: gallery.id)?.cancel()
            saveSnapshot(for: overview)
            return
        }
        if canReuseAssets {
            saveSnapshot(for: overview)
            return
        }

        // Covers are only visible on the home screen. Do not render/encode them
        // while the user is scrolling the gallery after a batch deletion.
        guard currentGalleryID == nil || showSwitcher else { return }
        if previous?.covers.map(\.id) == coverIDs, coverBuildTasks[gallery.id] != nil { return }
        coverBuildTasks.removeValue(forKey: gallery.id)?.cancel()
        coverBuildTasks[gallery.id] = Task { [weak self] in
            guard let self else { return }
            let assets = await imageCache.buildCovers(galleryID: gallery.id, covers: overview.covers)
            guard !Task.isCancelled,
                  expectedCoverIDs[gallery.id] == coverIDs,
                  var current = overviews[gallery.id],
                  current.covers.map(\.id) == coverIDs else { return }
            current.postcardCoverPath = assets.postcardCoverPath
            current.ticketCoverPath = assets.ticketCoverPath
            current.ticketDominantColorARGB = assets.ticketDominantColorARGB
            current.ticketColorVersion = assets.ticketColorVersion
            overviews[gallery.id] = current
            saveSnapshot(for: current)
            coverBuildTasks.removeValue(forKey: gallery.id)
        }
    }

    private func buildOverview(gallery: PunctumGallery, photos: [PhotoItem]) -> GalleryOverview {
        GalleryOverview(
            gallery: gallery,
            count: photos.count,
            timeSpan: PunctumFormatting.timeSpan(
                oldest: photos.map { CaptureDateIndex.shared.date(for: $0.asset) }.min(),
                newest: photos.map { CaptureDateIndex.shared.date(for: $0.asset) }.max()
            ),
            covers: Array(photos.prefix(4))
        )
    }

    private func persistGalleries() {
        store.saveGalleries(galleries)
    }

    private var activeTombstoneIDs: Set<String> {
        deleteTombstones.activeIDs(at: Date())
    }

    private func excludedPhotoIDs(for gallery: PunctumGallery) -> Set<String> {
        activeTombstoneIDs.union(hiddenAssetIDs[gallery.id] ?? [])
    }

    private func mergePhotos(current: [PhotoItem], incoming: [PhotoItem]) -> [PhotoItem] {
        guard !current.isEmpty else { return incoming }
        let existing = Dictionary(uniqueKeysWithValues: current.map { ($0.id, $0) })
        return incoming.map { photo in
            guard var kept = existing[photo.id] else { return photo }
            if !kept.hasKnownSize {
                kept.resolvedWidth = photo.resolvedWidth ?? photo.width
                kept.resolvedHeight = photo.resolvedHeight ?? photo.height
            }
            return kept
        }
    }

    private func markDeleting(_ photos: [PhotoItem]) {
        let expiry = Date().addingTimeInterval(Self.deleteTombstoneDuration)
        deleteTombstones.mark(photos.map(\.id), expiresAt: expiry)
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(Self.deleteTombstoneDuration))
            guard let self else { return }
            pruneExpiredTombstones()
            refreshAll()
        }
    }

    private func clearTombstones(for photos: [PhotoItem]) {
        deleteTombstones.clear(photos.map(\.id))
    }

    private func pruneExpiredTombstones() {
        deleteTombstones.prune(at: Date())
    }

    private func hydrateOverviewSnapshots() {
        guard library.authorizationStatus == .authorized || library.authorizationStatus == .limited else { return }
        for gallery in galleries {
            guard let snapshot = overviewSnapshots[gallery.id] else { continue }
            let validColor = snapshot.ticketColorVersion == GalleryImageCache.ticketColorVersion
            overviews[gallery.id] = GalleryOverview(
                gallery: gallery,
                count: snapshot.count,
                timeSpan: snapshot.timeSpan,
                covers: [],
                postcardCoverPath: imageCache.isValidCacheFile(snapshot.postcardCoverPath)
                    ? snapshot.postcardCoverPath : nil,
                ticketCoverPath: imageCache.isValidCacheFile(snapshot.ticketCoverPath)
                    ? snapshot.ticketCoverPath : nil,
                ticketDominantColorARGB: validColor ? snapshot.ticketDominantColorARGB : nil,
                ticketColorVersion: validColor ? snapshot.ticketColorVersion : 0
            )
        }
    }

    private func hydrateCachedCovers() async {
        guard library.authorizationStatus == .authorized || library.authorizationStatus == .limited else { return }
        for gallery in galleries {
            guard let snapshot = overviewSnapshots[gallery.id], !snapshot.coverAssetIDs.isEmpty else { continue }
            let covers = await library.photoItemsAsync(localIdentifiers: snapshot.coverAssetIDs)
            guard !Task.isCancelled,
                  overviewSnapshots[gallery.id]?.coverAssetIDs == snapshot.coverAssetIDs,
                  var current = overviews[gallery.id], current.covers.isEmpty else { continue }
            guard covers.map(\.id) == snapshot.coverAssetIDs else {
                refreshOverview(for: gallery)
                continue
            }
            current.covers = covers
            overviews[gallery.id] = current
            if current.postcardCoverPath == nil || current.ticketCoverPath == nil {
                applyOverview(current, for: gallery)
            }
            await Task.yield()
        }
    }

    private func saveSnapshot(for overview: GalleryOverview) {
        let previous = overviewSnapshots[overview.gallery.id]
        let coverIDs = overview.covers.map(\.id)
        if previous?.count == overview.count,
           previous?.timeSpan == overview.timeSpan,
           previous?.coverAssetIDs == coverIDs,
           previous?.postcardCoverPath == overview.postcardCoverPath,
           previous?.ticketCoverPath == overview.ticketCoverPath,
           previous?.ticketDominantColorARGB == overview.ticketDominantColorARGB,
           previous?.ticketColorVersion == overview.ticketColorVersion { return }
        let snapshot = GalleryOverviewSnapshot(
            galleryID: overview.gallery.id,
            count: overview.count,
            timeSpan: overview.timeSpan,
            coverAssetIDs: coverIDs,
            postcardCoverPath: overview.postcardCoverPath,
            ticketCoverPath: overview.ticketCoverPath,
            ticketDominantColorARGB: overview.ticketDominantColorARGB,
            ticketColorVersion: overview.ticketColorVersion,
            updatedAt: Date()
        )
        overviewSnapshots[overview.gallery.id] = snapshot
        let snapshots = overviewSnapshots
        let previousSave = snapshotSaveTask
        let store = store
        snapshotSaveTask = Task.detached(priority: .utility) {
            await previousSave?.value
            store.saveOverviewSnapshots(snapshots)
        }
    }

    private static let homeSubtitles = [
        "每一个画廊，都是你来时的路",
        "那些感动你的，那些你凝望的",
        "摄影的第一课：所有平凡都藏着神迹",
        "将流逝的时间，翻译成凝固的瞬间",
        "昧旦启明，蓄势新篇",
        "别想太多，先按快门",
        "影像不是记录，是诠释",
        "相机只是工具，心灵才是真正的镜头",
        "光影交错瞬间，捕捉爱与自由的轮廓",
        "慢门流淌时光，瞬间即是永恒",
        "取景框里的世界，比双眼更温柔",
        "追光者，终成为光",
        "按下快门的勇气，比技巧更珍贵",
        "光轨划过暗房，向流星写下情书",
        "镜头，比情话更擅长说永远",
        "观止，关心每一幅照片被重新看见的时刻",
        "每一次回望，都重新感受影像的重量",
        "光落下的地方，故事开始显影",
        "时间经过镜头，留下自己的形状",
        "有些瞬间，只肯向镜头坦白",
        "把今日的光，留给明日回望",
        "取景，是与世界交换目光",
        "看见之前，先学会凝望",
        "一张照片，一次与时间的重逢",
        "光穿过人间，也穿过你",
        "留住一束光，也留住当时的自己",
        "把平凡看久一点，奇迹就会显影",
        "有些光，只在回望时抵达",
    ]
}

struct PhotoDeletionTombstones {
    private var expirations: [String: Date] = [:]

    mutating func mark(_ photoIDs: [String], expiresAt: Date) {
        for photoID in photoIDs { expirations[photoID] = expiresAt }
    }

    mutating func clear(_ photoIDs: [String]) {
        for photoID in photoIDs { expirations.removeValue(forKey: photoID) }
    }

    func activeIDs(at date: Date) -> Set<String> {
        Set(expirations.compactMap { $0.value > date ? $0.key : nil })
    }

    mutating func prune(at date: Date) {
        expirations = expirations.filter { $0.value > date }
    }
}
