import SwiftUI
import UIKit

// The legacy arrow-button sheet remains in SwitcherScreen.swift for comparison.
struct SortGalleriesSheet: View {
    @Environment(\.dismiss) private var dismiss
    let galleries: [PunctumGallery]
    let onMove: (Int, Int) -> Void
    let onDelete: (Int) -> Void
    let onAdd: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text("调整图集画廊").font(PunctumTheme.serifSC(20, weight: .medium))
                Spacer()
                Button("完成", action: dismiss.callAsFunction)
            }.padding(.horizontal, 20).padding(.top, 24)
            GalleryReorderList(galleries: galleries, onMove: onMove, onDelete: onDelete)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            Button(action: onAdd) {
                Label("添加图集画廊", systemImage: "plus")
                    .frame(maxWidth: .infinity, alignment: .leading).padding(20)
            }
        }
        .foregroundStyle(PunctumTheme.bone)
        .tint(PunctumTheme.gold)
        .punctumDialogSurface()
    }
}

private struct GalleryReorderList: UIViewRepresentable {
    let galleries: [PunctumGallery]
    let onMove: (Int, Int) -> Void
    let onDelete: (Int) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(self) }
    func makeUIView(context: Context) -> UICollectionView {
        let layout = UICollectionViewFlowLayout()
        layout.minimumLineSpacing = 0
        let view = UICollectionView(frame: .zero, collectionViewLayout: layout)
        view.backgroundColor = .clear
        view.showsVerticalScrollIndicator = true
        view.indicatorStyle = .white
        view.alwaysBounceVertical = true
        view.contentInsetAdjustmentBehavior = .never
        view.register(UICollectionViewCell.self, forCellWithReuseIdentifier: "gallery")
        view.dataSource = context.coordinator
        view.delegate = context.coordinator
        view.addGestureRecognizer(UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.drag(_:))))
        context.coordinator.view = view
        return view
    }
    func updateUIView(_ view: UICollectionView, context: Context) {
        let coordinator = context.coordinator
        coordinator.parent = self
        // Preserve stable IDs and order while interactive movement owns its snapshot.
        guard coordinator.sourceID == nil else { return }
        if coordinator.items != galleries {
            coordinator.items = galleries
            view.reloadData()
        }
    }
    final class Coordinator: NSObject, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
        var parent: GalleryReorderList
        var items: [PunctumGallery]
        var sourceID: String?
        var lastTarget: IndexPath?
        var beforeDrag: [PunctumGallery] = []
        weak var view: UICollectionView?
        init(_ parent: GalleryReorderList) { self.parent = parent; items = parent.galleries }
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int { items.count }
        func collectionView(_ collectionView: UICollectionView, layout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
            CGSize(width: collectionView.bounds.width, height: 58)
        }
        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "gallery", for: indexPath)
            let gallery = items[indexPath.item]
            cell.contentConfiguration = UIHostingConfiguration {
                HStack {
                    Text(gallery.displayName).font(PunctumTheme.galleryListName(gallery.displayName, size: 15)).lineLimit(1)
                    Spacer()
                    Image(systemName: "line.3.horizontal").accessibilityLabel("长按拖动排序")
                    Button {
                        guard self.sourceID == nil,
                              let index = self.parent.galleries.firstIndex(where: { $0.id == gallery.id }) else { return }
                        self.parent.onDelete(index)
                    } label: { Image(systemName: "trash").frame(width: 44, height: 44) }
                }.foregroundStyle(PunctumTheme.bone)
            }.margins(.horizontal, 20).margins(.vertical, 0)
            cell.isAccessibilityElement = true
            cell.accessibilityLabel = gallery.displayName
            cell.accessibilityCustomActions = [
                UIAccessibilityCustomAction(name: "向上移动") { [weak self] _ in self?.moveAccessible(gallery.id, by: -1) ?? false },
                UIAccessibilityCustomAction(name: "向下移动") { [weak self] _ in self?.moveAccessible(gallery.id, by: 1) ?? false },
                UIAccessibilityCustomAction(name: "移除图集") { [weak self] _ in
                    guard let self, self.sourceID == nil, let index = self.parent.galleries.firstIndex(where: { $0.id == gallery.id }) else { return false }
                    self.parent.onDelete(index); return true
                }
            ]
            return cell
        }
        func moveAccessible(_ id: String, by delta: Int) -> Bool {
            guard sourceID == nil, let index = parent.galleries.firstIndex(where: { $0.id == id }),
                  parent.galleries.indices.contains(index + delta) else { return false }
            parent.onMove(index, index + delta); return true
        }
        func collectionView(_ collectionView: UICollectionView, canMoveItemAt indexPath: IndexPath) -> Bool { true }
        func collectionView(_ collectionView: UICollectionView, moveItemAt sourceIndexPath: IndexPath, to destinationIndexPath: IndexPath) {
            let moved = items.remove(at: sourceIndexPath.item)
            items.insert(moved, at: destinationIndexPath.item)
            if let source = parent.galleries.firstIndex(where: { $0.id == moved.id }) {
                parent.onMove(source, destinationIndexPath.item)
            }
        }
        func collectionView(_ collectionView: UICollectionView, targetIndexPathForMoveFromItemAt originalIndexPath: IndexPath, toProposedIndexPath proposedIndexPath: IndexPath) -> IndexPath {
            if lastTarget != proposedIndexPath { UISelectionFeedbackGenerator().selectionChanged(); lastTarget = proposedIndexPath }
            return proposedIndexPath
        }
        @objc func drag(_ gesture: UILongPressGestureRecognizer) {
            guard let view else { return }
            switch gesture.state {
            case .began:
                guard sourceID == nil else { return }
                guard let index = view.indexPathForItem(at: gesture.location(in: view)) else { return }
                lastTarget = index
                beforeDrag = items
                sourceID = items[index.item].id
                if !view.beginInteractiveMovementForItem(at: index) { sourceID = nil; return }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            case .changed:
                view.updateInteractiveMovementTargetPosition(gesture.location(in: view))
            case .ended:
                view.endInteractiveMovement()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
                    guard let self else { return }
                    self.sourceID = nil
                    if self.items != self.parent.galleries { self.items = self.parent.galleries; self.view?.reloadData() }
                }
            case .cancelled, .failed:
                view.cancelInteractiveMovement()
                items = beforeDrag
                sourceID = nil
                view.reloadData()
            default: break
            }
        }
    }
}
