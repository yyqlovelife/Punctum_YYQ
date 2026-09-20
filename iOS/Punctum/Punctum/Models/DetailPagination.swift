import Foundation

/// A task key covering entry, page movement, removals, appends and gesture completion.
struct DetailPagination: Equatable {
    let index: Int
    let visibleCount: Int
    let loadedCount: Int
    let photoID: String?
    let hasMore: Bool
    let blocked: Bool

    var shouldLoad: Bool {
        !blocked && hasMore && (visibleCount == 0 || index >= max(visibleCount - 4, 0))
    }
    var shouldClose: Bool { !blocked && !hasMore && visibleCount == 0 }
}
