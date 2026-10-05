# Punctum for iOS

Local update (2026-09-25): returning from detail now keeps the gallery scroll position when the final photo was visible at entry; otherwise it centers that photo's two-column row before detail disappears. Latest local IPA: `iOS/IPA/Punctum-0.5.9-return-center-unsigned.ipa` (from repository root). Version remains 0.5.9/59; device validation is pending.

Earlier local follow-up (2026-09-25): cached cover asset IDs restore home images even when generated cover files are gone; opening a gallery shows its loading state immediately while the initial photo order is fetched in the background. Earlier IPA: `iOS/IPA/Punctum-0.5.9-cover-entry-fix-unsigned.ipa` (from repository root).

Earlier local update (2026-09-25): cold launch displays saved home cards before refreshing Photos. Date-index loading, collection validation, asset enumeration, cover rendering and cache writes run in the background; gallery refreshes proceed one at a time. Earlier IPA: `iOS/IPA/Punctum-0.5.9-fast-launch-unsigned.ipa` (repository root).

Prior handoff (2026-09-20): [session status](../../SESSION_HANDOFF_2026-09-20.md). Release IPA at that time: `iOS/IPA/Punctum-0.5.9-unsigned.ipa` (path from repository root), 41 tests passed; physical-device acceptance remains pending. Earlier package references below are historical.

Native SwiftUI / UIKit edition of Punctum · 观止. Current version: **0.5.9 (build 59)**, updated 2026-10-05. Minimum deployment target: **iOS 17**. Bundle identifier: `com.chessyyq.punctum`.

## Photo sorting update · 2026-10-05

Each gallery remembers Capture / Edited ordering. The text-only switch above the style label uses matching typography/color, with each row aligned by its first text baseline. First tap shows the Chinese sorting hint for 2 seconds, once per app installation. Edited order uses PhotoKit modificationDate, then resolved capture date and stable asset ID. Covers and displayed metadata continue using capture dates. Sorting snapshots runs off the main actor and pagination uses the complete chosen order. IPA is unsigned for AltStore signing.

## Onboarding and source handoff · 2026-10-05

Onboarding uses “Select Exhibition”, “选择一个图集，作为你的第一个画廊” and “让每一次回望，都重新感受影像的重量”, without full stops. The latest onboarding IPA includes sorting; Release previously passed and its archive integrity was rechecked. Source, tests and docs are synchronized alongside Android 0.5.9. The new navigation motion is Android-only; iOS physical-device acceptance remains pending.

## Current experience

- Full-screen photo comparison via the system single-image picker: portrait pairs use columns, other pairs use rows; independent/linked zoom and confirmed deletion with identity-preserving return.

- Three invitation-card home styles, system-album mapping, original-aspect gallery layout, 80-photo pagination, and cached EXIF capture-time ordering.
- Persistent gallery reordering with native drag, edge scrolling and content-first sheet scrolling; already-added albums stay locked in the picker.
- Photo-only 1–5× persistent pinch zoom with one- or two-finger panning; double-tap the enlarged image or pinch back to 1× to reset. Metadata stays fixed. Live Photos support hold playback and one-shot badge playback.
- Native snapshot swipe deletion with resisted drag and a **280ms** shrink toward the trash target. The user confirmed the smoother interaction and final timing. Deletions remain pending until confirmation on leaving detail.
- Full-frame background decoding, shared image requests, per-photo EXIF prefetch, portrait top alignment, and foreground gesture cleanup.

The legacy SwiftUI deletion path remains behind `nativeDeletionEnabled` in `Views/DetailScreen.swift`. Do not restore the abandoned dynamic pager placeholders, portrait safe-area gap, or the old header-icon sorting layout.

## Build and test

Use full Xcode and XcodeGen. The unsigned IPA script pins `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`; no global xcode-select change is required.

```bash
# From this directory
xcodegen generate
open Punctum.xcodeproj

# Choose an available simulator; list devices with xcrun simctl list devices.
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
  -project Punctum.xcodeproj -scheme Punctum -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 14 Pro' \
  CODE_SIGNING_ALLOWED=NO test

# Build and package the current source for AltStore signing
scripts/build-unsigned-ipa.sh
```

The packaging script writes an unsigned IPA under `../IPA/`. The latest local delivery is `Punctum-0.5.9-onboarding-copy-unsigned.ipa`; earlier filenames in the changelog are historical. Do not run concurrent xcodebuild jobs against one DerivedData directory.

## Verification and distribution

The latest regression run passed 47 tests; Release, IPA integrity and bundled 0.5.9/59 checks passed. The user confirmed that the cold-launch home screen can scroll freely. Cover restoration, gallery entry and return positioning still need iPhone validation; see `changelog/ios.md` and `PUNCTUM_HANDOFF.md`. The tests do not establish physical frame rate or gesture acceptance.

Use AltStore / AltServer to sign the unsigned IPA, preserving the existing Apple account/app identity for an overlay install. Simulator builds cannot run on an iPhone. Signing certificates, provisioning profiles and credentials stay outside Git. Android resources referenced by this project are included in the repository; clone the full repository when moving machines.

See [iOS changelog](../../changelog/ios.md), [handoff](../../PUNCTUM_HANDOFF.md), [verification history](../../design-qa.md), and [version index](../../CHANGELOG.md).
