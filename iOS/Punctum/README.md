# Punctum for iOS

Latest handoff (2026-09-20): [session status](../../SESSION_HANDOFF_2026-09-20.md). Latest IPA: `iOS/IPA/Punctum-0.5.9-unsigned.ipa` (path from repository root), 41 tests passed; physical-device acceptance remains pending. Earlier package references below are historical.

Native SwiftUI / UIKit edition of Punctum · 观止. Current version: **0.5.9 (build 59)**, updated 2026-09-20. Minimum deployment target: **iOS 17**. Bundle identifier: `com.chessyyq.punctum`.

## Current experience

- Full-screen photo comparison via the system single-image picker: portrait pairs use columns, other pairs use rows; independent/linked zoom and confirmed deletion with identity-preserving return.

- Three invitation-card home styles, system-album mapping, original-aspect gallery layout, 80-photo pagination, and cached EXIF capture-time ordering.
- Persistent gallery reordering with native drag, edge scrolling and content-first sheet scrolling; already-added albums stay locked in the picker.
- Photo-only 1–5× persistent pinch zoom with one- or two-finger panning; double-tap the enlarged image or pinch back to 1× to reset. Metadata stays fixed. Live Photos support hold playback and one-shot badge playback.
- Native snapshot swipe deletion with resisted drag and a **280ms** shrink toward the trash target. The user confirmed the smoother interaction and final timing. Deletions remain pending until confirmation on leaving detail.
- Full-frame background decoding, shared image requests, per-photo EXIF prefetch, portrait top alignment, and foreground gesture cleanup.

The legacy SwiftUI deletion path remains behind `nativeDeletionEnabled` in `Views/DetailScreen.swift`. Do not restore the abandoned dynamic pager placeholders, portrait safe-area gap, or modified-time sorting toggle.

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

The packaging script writes `../IPA/Punctum-0.5.8-unsigned.ipa`. The current local trial delivery is `Punctum-0.5.8-comparison-unsigned.ipa`; the earlier `native-delete-280ms` package predates persistent zoom. Do not run concurrent xcodebuild jobs against one DerivedData directory.

## Verification and distribution

The current regression run passed 35 tests, including 7 comparison tests; the Release build and IPA integrity/version checks passed. Simulator checks confirmed portrait/landscape layout and original-size edge paging. Comparison UI verification was blocked by the locked Mac; physical multi-touch and system deletion acceptance are pending; see `design-qa.md`. Tests cover models, EXIF dates, decoding, layout, tap travel and motion continuity, not physical frame rate. User confirmation covers native deletion feel and timing; other interaction combinations retain their own verification status.

Use AltStore / AltServer to sign the unsigned IPA, preserving the existing Apple account/app identity for an overlay install. Simulator builds cannot run on an iPhone. Signing certificates, provisioning profiles and credentials stay outside Git. Android resources referenced by this project are included in the repository; clone the full repository when moving machines.

See [iOS changelog](../../changelog/ios.md), [handoff](../../PUNCTUM_HANDOFF.md), [verification history](../../design-qa.md), and [version index](../../CHANGELOG.md).
