import Foundation
import Photos
import SwiftUI

@MainActor
final class GalleryViewModel: NSObject, ObservableObject, PHPhotoLibraryChangeObserver {
    @Published private(set) var galleries: [PunctumGallery]
    @Published private(set) var overviews: [String: GalleryOverview] = [:]
    @Published private(set) var photos: [PhotoItem] = []
    @Published private(set) var isLoading = false
    @Published var currentGalleryID: String?
    @Published var showSwitcher = false
    @Published var showAlbumPicker = false
    @Published var detailIndex: Int?
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
    private var coverBuildTasks: [String: Task<Void, Never>] = [:]
    private var expectedCoverIDs: [String: [String]] = [:]
    private var deleteTombstones = PhotoDeletionTombstones()
    private var hiddenAssetIDs: [String: Set<String>]
    private var refreshTask: Task<Void, Never>?
    private var galleryLoadGeneration = 0
    private var galleryFetchNextIndex = 0
    private var galleryFetchExhausted = true
    private var isLoadingMorePhotos = false

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
        imageCache: GalleryImageCache
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
        PHPhotoLibrary.shared().register(self)
        hydrateOverviewSnapshots()
        refreshAll()
        Task { [weak self] in
            guard let self else { return }
            if self.library.authorizationStatus == .notDetermined {
                _ = await self.library.requestAuthorization()
            }
            self.hydrateOverviewSnapshots()
            self.refreshAll()
            await LivePhotoSeeder.seedIfNeeded()
            self.refreshAll()
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
                await MainActor.run {
                    self?.openDetail(at: 0)
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

    func selectGallery(_ id: String) {
        guard galleries.contains(where: { $0.id == id }) else { return }
        let switching = currentGalleryID != id
        currentGalleryID = id
        showSwitcher = false
        detailIndex = nil
        if switching {
            photos = []
            isLoading = true
        }
        loadCurrentGallery()
    }

    func openSwitcher() {
        detailIndex = nil
        showSwitcher = true
    }

    func closeSwitcher() {
        showSwitcher = false
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
            currentGalleryID = nil
            photos = []
            detailIndex = nil
            showSwitcher = false
        }
        persistGalleries()
        store.saveOverviewSnapshots(overviewSnapshots)
    }

    func openDetail(at index: Int) {
        guard photos.indices.contains(index) else { return }
        detailIndex = index
    }

    func closeDetail(pendingPhotos: [PhotoItem] = []) {
        detailIndex = nil
        guard !pendingPhotos.isEmpty else { return }

        var seen = Set<String>()
        let uniquePhotos = pendingPhotos.filter { seen.insert($0.id).inserted }
        let pendingIDs = Set(uniquePhotos.map(\.id))
        markDeleting(uniquePhotos)
        pendingDeletionRequest = PendingPhotoDeletion(photos: uniquePhotos)
        photos.removeAll { pendingIDs.contains($0.id) }
        updateCurrentOverview()
    }

    func cancelPendingDeletion(_ request: PendingPhotoDeletion) {
        pendingDeletionRequest = nil
        clearTombstones(for: request.photos)
        loadCurrentGallery()
    }

    func confirmPendingDeletion(_ request: PendingPhotoDeletion) {
        pendingDeletionRequest = nil
        Task {
            do {
                try await library.delete(request.photos)
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

    func deletePhoto(_ photo: PhotoItem) {
        Task {
            do {
                try await library.delete(photo)
                markDeleting([photo])
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) {
                    photos.removeAll { $0.id == photo.id }
                    if photos.isEmpty {
                        detailIndex = nil
                    } else if let detailIndex {
                        self.detailIndex = min(detailIndex, photos.count - 1)
                    }
                }
                updateCurrentOverview()
            } catch {
                let photosError = error as NSError
                if photosError.domain != PHPhotosErrorDomain ||
                    photosError.code != PHPhotosError.userCancelled.rawValue {
                    transientMessage = error.localizedDescription
                }
            }
        }
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
        guard library.authorizationStatus == .authorized || library.authorizationStatus == .limited else { return }
        pruneExpiredTombstones()
        let valid = galleries.filter(library.galleryExists)
        if valid != galleries {
            galleries = valid
            persistGalleries()
        }
        for gallery in galleries { refreshOverview(for: gallery) }
        if currentGallery != nil { loadCurrentGallery() }
    }

    func appDidBecomeActive() {
        refreshAll()
    }

    nonisolated func photoLibraryDidChange(_ changeInstance: PHChange) {
        Task { @MainActor [weak self] in
            self?.scheduleRefresh()
        }
    }

    private func scheduleRefresh() {
        refreshTask?.cancel()
        refreshTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled, let self else { return }
            self.refreshAll()
        }
    }

    private func loadCurrentGallery() {
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
            targetSize: CGSize(width: 360, height: 360)
        )
        refreshOverview(for: gallery)
        isLoading = false
    }

    func loadMorePhotos() {
        guard let gallery = currentGallery,
              !isLoading,
              !isLoadingMorePhotos,
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
                targetSize: CGSize(width: 360, height: 360)
            )
        }
        isLoadingMorePhotos = false
    }

    private func updateCurrentOverview() {
        guard let gallery = currentGallery else { return }
        refreshOverview(for: gallery)
    }

    private func refreshOverview(for gallery: PunctumGallery) {
        guard var overview = library.overview(for: gallery, excluding: excludedPhotoIDs(for: gallery)) else {
            coverBuildTasks.removeValue(forKey: gallery.id)?.cancel()
            expectedCoverIDs.removeValue(forKey: gallery.id)
            overviews.removeValue(forKey: gallery.id)
            return
        }

        let coverIDs = overview.covers.map(\.id)
        let previous = overviews[gallery.id]
        let canReuseAssets = previous?.covers.map(\.id) == coverIDs
            && imageCache.isValidCacheFile(previous?.postcardCoverPath)
            && imageCache.isValidCacheFile(previous?.ticketCoverPath)
            && previous?.ticketDominantColorARGB != nil
            && previous?.ticketColorVersion == GalleryImageCache.ticketColorVersion

        if canReuseAssets, let previous {
            overview.postcardCoverPath = previous.postcardCoverPath
            overview.ticketCoverPath = previous.ticketCoverPath
            overview.ticketDominantColorARGB = previous.ticketDominantColorARGB
            overview.ticketColorVersion = previous.ticketColorVersion
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
                oldest: photos.last?.creationDate,
                newest: photos.first?.creationDate
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
            let covers = library.photoItems(localIdentifiers: snapshot.coverAssetIDs)
            guard covers.map(\.id) == snapshot.coverAssetIDs else { continue }
            let validColor = snapshot.ticketColorVersion == GalleryImageCache.ticketColorVersion
            overviews[gallery.id] = GalleryOverview(
                gallery: gallery,
                count: snapshot.count,
                timeSpan: snapshot.timeSpan,
                covers: covers,
                postcardCoverPath: imageCache.isValidCacheFile(snapshot.postcardCoverPath)
                    ? snapshot.postcardCoverPath : nil,
                ticketCoverPath: imageCache.isValidCacheFile(snapshot.ticketCoverPath)
                    ? snapshot.ticketCoverPath : nil,
                ticketDominantColorARGB: validColor ? snapshot.ticketDominantColorARGB : nil,
                ticketColorVersion: validColor ? snapshot.ticketColorVersion : 0
            )
        }
    }

    private func saveSnapshot(for overview: GalleryOverview) {
        let snapshot = GalleryOverviewSnapshot(
            galleryID: overview.gallery.id,
            count: overview.count,
            timeSpan: overview.timeSpan,
            coverAssetIDs: overview.covers.map(\.id),
            postcardCoverPath: overview.postcardCoverPath,
            ticketCoverPath: overview.ticketCoverPath,
            ticketDominantColorARGB: overview.ticketDominantColorARGB,
            ticketColorVersion: overview.ticketColorVersion,
            updatedAt: Date()
        )
        overviewSnapshots[overview.gallery.id] = snapshot
        store.saveOverviewSnapshots(overviewSnapshots)
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
