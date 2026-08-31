import Photos
import SwiftUI
import UIKit

@MainActor
final class ExportService {
    static let shared = ExportService()
    static let exportLongEdge: CGFloat = 1920

    func renderDetailPageAndSave(
        photo: PhotoItem,
        displayNumber: Int,
        screenSize: CGSize
    ) async throws {
        guard screenSize.width > 0, screenSize.height > 0 else {
            throw PhotoLibraryError.saveFailed
        }

        async let loadedImage = MetadataService.shared.image(for: photo, targetLongEdge: 2400)
        async let loadedMetadata = MetadataService.shared.metadata(for: photo)
        let (image, metadata) = try await (loadedImage, loadedMetadata)

        let page = ExportDetailPage(
            image: image,
            aspectRatio: photo.hasKnownSize ? photo.aspectRatio : 1.5,
            metadata: metadata,
            displayNumber: displayNumber,
            screenSize: screenSize
        )
        let renderer = ImageRenderer(content: page)
        renderer.proposedSize = ProposedViewSize(width: screenSize.width, height: screenSize.height)
        renderer.scale = Self.exportLongEdge / max(screenSize.width, screenSize.height)
        renderer.isOpaque = true
        guard let screenshot = renderer.uiImage else {
            throw PhotoLibraryError.saveFailed
        }
        try await saveToPunctumAlbum(screenshot)
    }

    private func saveToPunctumAlbum(_ image: UIImage) async throws {
        let status = await requestAddAuthorizationIfNeeded()
        guard status == .authorized || status == .limited else {
            throw PhotoLibraryError.saveFailed
        }
        let album = fetchPunctumAlbum()
        try await withCheckedThrowingContinuation { continuation in
            PHPhotoLibrary.shared().performChanges {
                let assetRequest = PHAssetChangeRequest.creationRequestForAsset(from: image)
                guard let asset = assetRequest.placeholderForCreatedAsset else { return }
                if let album,
                   let albumRequest = PHAssetCollectionChangeRequest(for: album) {
                    albumRequest.addAssets([asset] as NSArray)
                } else {
                    let albumRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: "Punctum")
                    albumRequest.addAssets([asset] as NSArray)
                }
            } completionHandler: { success, error in
                if success {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: error ?? PhotoLibraryError.saveFailed)
                }
            }
        }
    }

    private func requestAddAuthorizationIfNeeded() async -> PHAuthorizationStatus {
        let status = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        guard status == .notDetermined else { return status }
        return await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { continuation.resume(returning: $0) }
        }
    }

    private func fetchPunctumAlbum() -> PHAssetCollection? {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "title == %@", "Punctum")
        return PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: options).firstObject
    }
}

private struct ExportDetailPage: View {
    let image: UIImage
    let aspectRatio: CGFloat
    let metadata: PhotoMetadata
    let displayNumber: Int
    let screenSize: CGSize

    var body: some View {
        let aspect = max(aspectRatio, 0.1)
        let imageHeight = screenSize.width / aspect
        let topPadding = aspect < 1 ? 0 : max((screenSize.height - imageHeight) * 0.5 - 40, 0)

        ZStack(alignment: .topLeading) {
            PunctumTheme.ink
            VStack(alignment: .leading, spacing: 0) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: screenSize.width, height: imageHeight)
                    .padding(.top, topPadding)

                exportMetadata
                    .padding(.top, 30)
            }
        }
        .frame(width: screenSize.width, height: screenSize.height, alignment: .topLeading)
        .clipped()
    }

    private var exportMetadata: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("No.\(displayNumber)")
                .font(PunctumTheme.georgia(21, bold: true))
                .foregroundStyle(PunctumTheme.bone)
                .padding(.bottom, 18)

            if let date = metadata.dateTaken {
                exportText(date)
            }
            if let location = metadata.location ?? metadata.coordinate {
                exportText(location)
            }

            VStack(alignment: .leading, spacing: 0) {
                exportLine("Camera", metadata.camera)
                exportLine("Exposure Time", metadata.exposureTime)
                exportLine("Focal Length", metadata.focalLength)
                exportLine("Aperture", metadata.aperture)
                exportLine("ISO", metadata.iso)
                exportLine("Resolution", metadata.resolution)
                exportLine("File Size", metadata.fileSize)
            }
            .padding(.top, 24)
        }
        .padding(.horizontal, 30)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func exportLine(_ label: String, _ value: String?) -> some View {
        if let value, !value.isEmpty {
            exportText("\(label): \(value)")
        }
    }

    private func exportText(_ text: String) -> some View {
        Text(text)
            .font(PunctumTheme.georgia(14))
            .foregroundStyle(PunctumTheme.bone)
            .lineSpacing(4)
    }
}
