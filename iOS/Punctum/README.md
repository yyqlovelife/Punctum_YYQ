# Punctum for iOS

This directory contains the native SwiftUI iOS edition of Punctum. It now mirrors Android 0.5.3: three home invitation-card styles, newest-photo covers, Live Photo playback, and multi-select album adding.

## Local build

1. Install the current full Xcode release. This project pins `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` in the unsigned IPA script; do not switch the global `xcode-select` unless you intend to.
2. Run `xcodegen generate` in this directory if the generated project needs refreshing.
3. Open `Punctum.xcodeproj`, choose an iPhone Simulator, and run the `Punctum` scheme.

The project uses bundle identifier `com.chessyyq.punctum`, version `0.5.3` (build `53`), and iOS 17 as its deployment target. See the top of [`changelog/ios.md`](../../changelog/ios.md) and the handoff section in [`CHANGELOG.md`](../../CHANGELOG.md) for the current formal baseline.

## 0.5.1 parity notes

- Home style cycles postcard → ticket → reversal film. Scroll position is kept per style for the session; the last style is persisted.
- Postcard cards use the kraft footer, `PUNCTUMS` watermark, and `MOMENT · PUNCTUM · STUDIUM` / `TAP TO ENTER EXHIBITION`. Ticket stubs use inward semicircle notches. Reversal film is a two-column 1:1 grid with a 3:2 inset cover.
- Covers use the newest `PHAsset` by `creationDate` (iOS equivalent of EXIF DateTimeOriginal): ticket / reversal film use 1 photo, postcard uses 4.
- Live Photos play with `PHLivePhotoView`: hold 150ms to loop, release to halt immediately, badge plays once, 200ms fade-in after playback begins, no fade-out on stop.
- Adding galleries is a multi-select list of user albums plus Recents / Screenshots / Selfies / Panoramas. Confirm returns to the home cards, toasts「添加完成」, and scrolls to the first newly added gallery.
- Gallery rows size from real `pixelWidth` / `pixelHeight` and skip square degraded thumbnails so the first row does not collapse to 1:1.
- Paper textures are the same Android drawable JPEGs bundled as iOS resources.

## Device distribution

A device archive requires an Apple Developer team, a distribution certificate, and a matching provisioning profile. Simulator builds cannot be installed on an iPhone. An unsigned IPA can still be produced with `scripts/build-unsigned-ipa.sh`.

APK and IPA build outputs are intentionally excluded from Git and should be regenerated on each development machine.
