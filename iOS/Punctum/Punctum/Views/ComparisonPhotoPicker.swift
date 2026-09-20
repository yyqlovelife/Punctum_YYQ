import Photos
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

struct ComparisonPhoto: Identifiable {
    let id: String
    let photo: PhotoItem?
    let preview: UIImage?
    var size: CGSize {
        if let photo { return CGSize(width: max(photo.width, 1), height: max(photo.height, 1)) }
        return preview?.size ?? CGSize(width: 1, height: 1)
    }
    init(photo: PhotoItem) { id = photo.id; self.photo = photo; preview = nil }
    init(id: String, preview: UIImage) { self.id = id; photo = nil; self.preview = preview }
}

struct ComparisonSession: Identifiable {
    let id = UUID()
    let original: PhotoItem
    let selected: ComparisonPhoto
}

struct ComparisonPhotoPicker: UIViewControllerRepresentable {
    var onResult: (Result<ComparisonPhoto?, Error>) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onResult: onResult) }
    func makeUIViewController(context: Context) -> PHPickerViewController {
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = 1
        configuration.selection = .ordered
        configuration.preferredAssetRepresentationMode = .current
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = context.coordinator
        return picker
    }
    func updateUIViewController(_ view: PHPickerViewController, context: Context) {}

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        let onResult: (Result<ComparisonPhoto?, Error>) -> Void
        private var loading = false
        init(onResult: @escaping (Result<ComparisonPhoto?, Error>) -> Void) { self.onResult = onResult }
        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            guard !loading else { return }
            guard let result = results.first else { onResult(.success(nil)); return }
            loading = true
            if let id = result.assetIdentifier,
               let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject {
                onResult(.success(ComparisonPhoto(photo: PhotoItem(asset: asset, name: ""))))
                return
            }
            // PHPicker may expose a photo outside the app's limited-library grant.
            // Its exported image still permits comparison without broadening access.
            let id = result.assetIdentifier ?? UUID().uuidString
            guard let type = result.itemProvider.registeredTypeIdentifiers.first(where: {
                UTType($0)?.conforms(to: .image) == true
            }) else { onResult(.failure(ComparisonError.loadFailed)); return }
            result.itemProvider.loadFileRepresentation(forTypeIdentifier: type) { [weak self] url, error in
                let image = url.flatMap { try? Data(contentsOf: $0) }
                    .flatMap { FullFrameImageDecoder.downsample($0, maxPixel: 2200) }
                DispatchQueue.main.async {
                    guard let self else { return }
                    if let image { self.onResult(.success(ComparisonPhoto(id: id, preview: image))) }
                    else { self.onResult(.failure(error ?? ComparisonError.loadFailed)) }
                }
            }
        }
    }
}

enum ComparisonError: LocalizedError {
    case loadFailed, photoAccessRequired
    var errorDescription: String? {
        switch self {
        case .loadFailed: return "照片暂时无法载入，请检查网络后重试。"
        case .photoAccessRequired: return "请在系统设置的照片权限中允许观止访问这张照片，再进行删除。"
        }
    }
}

/// The picker grants read access only to the photos the user explicitly adds.
struct LimitedComparisonAccess: UIViewControllerRepresentable {
    var onFinish: () -> Void
    func makeUIViewController(context: Context) -> Controller {
        let controller = Controller()
        controller.onFinish = onFinish
        return controller
    }
    func updateUIViewController(_ controller: Controller, context: Context) {}
    final class Controller: UIViewController {
        var onFinish: () -> Void = {}
        private var presented = false
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard !presented else { return }
            presented = true
            PHPhotoLibrary.shared().presentLimitedLibraryPicker(from: self) { [weak self] _ in
                DispatchQueue.main.async { self?.onFinish() }
            }
        }
    }
}
