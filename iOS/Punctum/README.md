# Punctum for iOS

## 双端最新验收与发布 · 2026-10-10

用户已明确将近期 Android 与 iOS 改动全部视为验收通过。版本保持 **Android 0.5.9/59、iOS 0.5.10/60**；本次授权将全部近期源码、资源、测试、构建配置和说明同步到 GitHub `main`。最新功能、安装包和验证范围见 [本次发布汇总](../../docs/RELEASE_VALIDATION_2026-10-10.md)。下方旧“待验收”“未提交／未上传”、空 IPA 目录及旧测试数量均为各自日期的历史记录；用户验收状态以本节为准。未执行的测试或真机操作保留原证据边界。


## Foreground and repeated deletion fix · 2026-10-10

Version remains **0.5.10/60**. A native confirmation coordinator retains pending batches across backgrounding and waits for its dismissal completion before starting Photos deletion. Interrupted deletion motion restores scrolling and settles each released gesture once. All three sort modes refresh the current album asynchronously on foreground entry. Grid and batch deletion share a serialized commit; assets are resolved by current identifiers and checked after PhotoKit success before disappearing from the list.

All **66 simulator tests passed, with 0 failures and 0 skips**, including 8 new regressions. iPhone arm64 Release, ZIP integrity, bundle identity and version checks passed. Latest unsigned package: `../IPA/Punctum-0.5.10-foreground-delete-fix-unsigned.ipa`, SHA256 `eee2d1d08c9092fecb46cabf388fbffc42432227490f7eea500ffc1e5d0cac9b`. Use the existing AltStore signing flow. No connected iPhone was available, so no device install or original-library acceptance is claimed. Repeated system deletion confirmation on the user's phone remains pending. Android and version configuration are unchanged; no GitHub or Drive upload. See [validation](../../docs/IOS_FOREGROUND_DELETE_VALIDATION_2026-10-10.md).

Earlier package availability and test counts below describe their dated historical states.


## Current handoff · 2026-10-08

Start with [the latest handoff](../../SESSION_HANDOFF_2026-10-08.md) and [gallery visual-review prompt](../../docs/GALLERY_VISUAL_REVIEW_PROMPT_2026-10-08.md). Source remains iOS **0.5.10/60**, Android **0.5.9/59**. Sync order, foreground reload of the open gallery, the four-second hint with navigation dismissal, single-line onboarding and deletion/tap identity fixes remain local. iOS still has both sorting and style labels; the latest Android navigation motion and lower sort-only header have not been ported.

All 58 simulator tests passed with no skips on October 6; original-library iPhone acceptance is still pending. October 8 is a documentation audit, with no new build, application test, source/version change, packaging or upload. **The iOS/IPA directory currently contains no IPA file.** Package paths and hashes below are historical deliveries, not available downloads. Existing source and ignored validation evidence are retained. HEAD and remote main were both verified at `aaaa86be14187401312e98e33e80634cc24a2da8`; later changes remain uncommitted. Review the gallery visually first and wait for the user's choice before implementation.

## Latest local fix · deletion and tap identity · 2026-10-06

Version remains 0.5.10/60. Grid taps now resolve the tapped asset in the current gallery, rows refresh both partners together while retaining their scroll anchor, image reuse cannot show a previous asset, and each detail visit has a fresh session. Latest unsigned IPA: `../IPA/Punctum-0.5.10-delete-identity-fix-unsigned.ipa`, SHA256 `4a461f17a07988ff31e6fa3b9d0b94fb9936d6d11d75ae4abdd22d1e9e06ea5e`. All 58 tests passed with no skips, including native grid pixel/tap identity checks, delayed metadata, a real PhotoKit RootView return/reopen test and the previously skipped sync-order integration. iPhone arm64 Release and package checks passed. Original-library deletion still needs physical-iPhone acceptance. See [validation](../../docs/IOS_DELETE_IDENTITY_VALIDATION_2026-10-06.md). Older packages and skip reports below are historical.

## Current version · 0.5.10 / 60 · 2026-10-06

This iOS-only patch adds system-album sync ordering, foreground refresh of the open gallery, the full four-second sorting hint and the single-line onboarding title. Android stays at 0.5.9/59. Latest unsigned IPA: `../IPA/Punctum-0.5.10-unsigned.ipa`; SHA256 `b16c5356800a6bf604a2b072dd73089836f91fb965cafaf6b0b49ba554e2ba59`. iPhone arm64 Release, ZIP integrity and bundled version/identifier checks passed. Use the existing AltStore signing flow. This version-only build did not repeat regression tests or physical-device acceptance; the prior feature regression had 50 passes and one PhotoKit permission skip. See [release validation](../../docs/IOS_RELEASE_VALIDATION_2026-10-06.md). Earlier package references below are historical.

## Latest IPA delivery · 2026-10-06

Current source was rebuilt for iPhone arm64, including sync sorting and the single-line onboarding title. Latest unsigned IPA: `../IPA/Punctum-0.5.9-sync-sort-onboarding-unsigned.ipa`, version 0.5.9/59, SHA256 `820d5769c6b92b444e719088e66f273f27ce926140643db8dd00ea91fd3a8cc0`. Release, ZIP integrity and bundled version checks passed. Use the existing AltStore signing flow. This packaging run did not repeat regression tests or physical-device acceptance. Earlier package references below are historical.

## Latest local follow-up · synchronized album order · 2026-10-06

Sorting now cycles through capture, edited, and sync. Sync preserves PhotoKit's stored collection order and re-fetches the open gallery on every foreground entry, including when no library-change notification arrives. The complete three-mode hint appears for four seconds and disappears immediately on gallery exit or detail entry; existing users see the new explanation once. Version remains 0.5.9/59. Local unsigned IPA: `../IPA/Punctum-0.5.9-sync-sort-unsigned.ipa`. Release/package checks pass; physical-iPhone acceptance is pending. See [validation](../../docs/IOS_SYNC_SORT_VALIDATION_2026-10-06.md). Prior statements below describe earlier phases.


## Current handoff · 2026-10-06

Start with [the current session handoff](../../SESSION_HANDOFF_2026-10-06.md). iOS remains 0.5.9/59 with the onboarding-copy IPA; source is unchanged in this Android follow-up. iOS still shows the sorting label above the style label and does not have the new Android navigation motion or the sort-only lower header. The 47 tests and Release results below are previous checks, not a fresh run today. Physical-device acceptance boundaries remain unchanged.

Local update (2026-09-25): returning from detail now keeps the gallery scroll position when the final photo was visible at entry; otherwise it centers that photo's two-column row before detail disappears. Latest local IPA: `iOS/IPA/Punctum-0.5.9-return-center-unsigned.ipa` (from repository root). Version remains 0.5.9/59; device validation is pending.

Earlier local follow-up (2026-09-25): cached cover asset IDs restore home images even when generated cover files are gone; opening a gallery shows its loading state immediately while the initial photo order is fetched in the background. Earlier IPA: `iOS/IPA/Punctum-0.5.9-cover-entry-fix-unsigned.ipa` (from repository root).

Earlier local update (2026-09-25): cold launch displays saved home cards before refreshing Photos. Date-index loading, collection validation, asset enumeration, cover rendering and cache writes run in the background; gallery refreshes proceed one at a time. Earlier IPA: `iOS/IPA/Punctum-0.5.9-fast-launch-unsigned.ipa` (repository root).

Prior handoff (2026-09-20): [session status](../../SESSION_HANDOFF_2026-09-20.md). Release IPA at that time: `iOS/IPA/Punctum-0.5.9-unsigned.ipa` (path from repository root), 41 tests passed; physical-device acceptance remains pending. Earlier package references below are historical.

Native SwiftUI / UIKit edition of Punctum · 观止. Current version: **0.5.10 (build 60)**, updated 2026-10-06. Minimum deployment target: **iOS 17**. Bundle identifier: `com.chessyyq.punctum`.

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
