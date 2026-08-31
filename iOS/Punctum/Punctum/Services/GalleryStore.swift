import Foundation

final class GalleryStore {
    private let defaults: UserDefaults
    private let galleriesKey = "punctum.galleries"
    private let cardStyleKey = "punctum.invitationStyle"
    private let overviewSnapshotsKey = "punctum.overviewSnapshots.v1"
    private let hiddenAssetsKey = "punctum.hiddenAssets.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func loadGalleries() -> [PunctumGallery] {
        guard let data = defaults.data(forKey: galleriesKey) else { return [] }
        return (try? JSONDecoder().decode([PunctumGallery].self, from: data)) ?? []
    }

    func saveGalleries(_ galleries: [PunctumGallery]) {
        guard let data = try? JSONEncoder().encode(galleries) else { return }
        defaults.set(data, forKey: galleriesKey)
    }

    func loadInvitationStyle() -> InvitationCardStyle {
        InvitationCardStyle(rawValue: defaults.string(forKey: cardStyleKey) ?? "") ?? .postcard
    }

    func saveInvitationStyle(_ style: InvitationCardStyle) {
        defaults.set(style.rawValue, forKey: cardStyleKey)
    }

    func loadOverviewSnapshots() -> [String: GalleryOverviewSnapshot] {
        guard let data = defaults.data(forKey: overviewSnapshotsKey),
              let snapshots = try? JSONDecoder().decode([GalleryOverviewSnapshot].self, from: data) else {
            return [:]
        }
        return Dictionary(uniqueKeysWithValues: snapshots.map { ($0.galleryID, $0) })
    }

    func saveOverviewSnapshots(_ snapshots: [String: GalleryOverviewSnapshot]) {
        guard let data = try? JSONEncoder().encode(Array(snapshots.values)) else { return }
        defaults.set(data, forKey: overviewSnapshotsKey)
    }

    func loadHiddenAssetIDs() -> [String: Set<String>] {
        guard let data = defaults.data(forKey: hiddenAssetsKey),
              let stored = try? JSONDecoder().decode([String: [String]].self, from: data) else {
            return [:]
        }
        return stored.mapValues { Set($0) }
    }

    func saveHiddenAssetIDs(_ hidden: [String: Set<String>]) {
        let stored = hidden.mapValues { Array($0) }
        guard let data = try? JSONEncoder().encode(stored) else { return }
        defaults.set(data, forKey: hiddenAssetsKey)
    }
}
