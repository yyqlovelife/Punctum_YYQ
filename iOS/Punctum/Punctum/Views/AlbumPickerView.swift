import Photos
import SwiftUI

struct AlbumPickerView: View {
    @Environment(\.dismiss) private var dismiss
    let existingIDs: Set<String>
    var currentGalleryID: String? = nil
    var allowsMultipleSelection = true
    let onConfirm: ([AlbumOption]) -> Void
    var onSelect: ((AlbumOption) -> Void)? = nil
    @State private var albums: [AlbumOption]?
    @State private var selectedIDs: Set<String> = []

    private var isMovePicker: Bool { !allowsMultipleSelection }

    private var selectableIDs: Set<String> {
        Set((albums ?? []).map(\.id).filter { !existingIDs.contains($0) })
    }

    private var confirmEnabled: Bool {
        selectedIDs.contains { selectableIDs.contains($0) }
    }

    var body: some View {
        NavigationStack {
            Group {
                switch albums {
                case nil:
                    ProgressView()
                        .tint(PunctumTheme.gold)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                case let loaded? where loaded.isEmpty:
                    Text("没有找到可用图集。")
                        .font(PunctumTheme.serifSC(15))
                        .foregroundStyle(PunctumTheme.muted)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                case let loaded?:
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(loaded) { album in
                                if isMovePicker {
                                    let isCurrent = album.id == currentGalleryID
                                    let canReceive = album.collection.assetCollectionType == .album
                                    AlbumPickerRow(
                                        album: album,
                                        checked: false,
                                        alreadyAdded: false,
                                        showsCheckbox: false,
                                        disabled: isCurrent || !canReceive,
                                        statusText: isCurrent
                                            ? "当前图集"
                                            : (canReceive ? nil : "无法移入")
                                    ) {
                                        onSelect?(album)
                                        dismiss()
                                    }
                                } else {
                                    let alreadyAdded = existingIDs.contains(album.id)
                                    let checked = alreadyAdded || selectedIDs.contains(album.id)
                                    AlbumPickerRow(
                                        album: album,
                                        checked: checked,
                                        alreadyAdded: alreadyAdded
                                    ) {
                                        if selectedIDs.contains(album.id) {
                                            selectedIDs.remove(album.id)
                                        } else {
                                            selectedIDs.insert(album.id)
                                        }
                                    }
                                }
                                HairlineDivider().padding(.leading, isMovePicker ? 20 : 52)
                            }
                        }
                    }
                }
            }
            .background(PunctumTheme.ink.ignoresSafeArea())
            .navigationTitle(isMovePicker ? "移动到图集" : "选择图集")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(PunctumTheme.ink, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                        .font(PunctumTheme.serifSC(15))
                        .foregroundStyle(PunctumTheme.muted)
                }
                if !isMovePicker {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("确定") {
                            let chosen = (albums ?? []).filter { selectedIDs.contains($0.id) }
                            onConfirm(chosen)
                        }
                        .font(PunctumTheme.serifSC(15))
                        .foregroundStyle(confirmEnabled ? PunctumTheme.gold : PunctumTheme.muted)
                        .disabled(!confirmEnabled)
                    }
                }
            }
        }
        .task {
            albums = PhotoLibraryService.shared.fetchAlbums(includingEmpty: isMovePicker)
        }
    }
}

private struct AlbumPickerRow: View {
    let album: AlbumOption
    let checked: Bool
    let alreadyAdded: Bool
    var showsCheckbox = true
    var disabled = false
    var statusText: String? = nil
    let onToggle: () -> Void

    private var isDisabled: Bool { alreadyAdded || disabled }

    var body: some View {
        HStack(spacing: 12) {
            if showsCheckbox {
                Image(systemName: checked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(checked ? PunctumTheme.gold.opacity(alreadyAdded ? 0.38 : 1) : PunctumTheme.muted)
                    .frame(width: 28)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(album.title)
                    .font(PunctumTheme.galleryListName(album.title, size: 15))
                    .foregroundStyle(isDisabled ? PunctumTheme.muted : PunctumTheme.bone)
                    .lineLimit(1)
                mixedCaption(statusText ?? (alreadyAdded ? "已添加 · \(album.count) 项" : "\(album.count) 项"))
                    .foregroundStyle(PunctumTheme.muted)
                    .lineLimit(1)
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture {
            if !isDisabled { onToggle() }
        }
    }

    private func mixedCaption(_ string: String) -> Text {
        var result: Text?
        var buffer = ""
        var bufferLatin: Bool?

        func flush() {
            guard !buffer.isEmpty else { return }
            let piece = Text(buffer).font(
                (bufferLatin ?? false) ? PunctumTheme.newsreader(12) : PunctumTheme.serifSC(12)
            )
            result = result.map { $0 + piece } ?? piece
            buffer = ""
            bufferLatin = nil
        }

        for character in string {
            if character.isWhitespace || character == "·" {
                buffer.append(character)
                continue
            }
            let latin = character.isNumber || (character.isASCII && character.isLetter) || character == "."
            if let current = bufferLatin, current != latin {
                flush()
            }
            bufferLatin = latin
            buffer.append(character)
        }
        flush()
        return result ?? Text(string)
    }
}
