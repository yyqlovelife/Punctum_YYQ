# Design QA: original reversal-film card restored

## 当前验收结论 · 2026-09-16

用户先确认 iOS 原生快照删除“确实流畅了很多”，在收缩时长从 400ms 改为 280ms 后明确确认“可以了这个状态”，并授权同步近期源码和文档至 GitHub。因此 **原生快照删除流畅度及 280ms 节奏已获真机确认**。Android 照片缩放、双指平移与回弹此前已确认。两端保持 0.5.8 / 58。

以下各节保留当时的试验、待验证状态和证据；早期“280ms 待确认”已经被本结论取代。当前实现和安装包入口见 [PUNCTUM_HANDOFF.md](PUNCTUM_HANDOFF.md)。本次删除确认不扩写为首页、后台恢复、所有组合手势或量化帧率均已验收。


## Evidence

- Source of truth: the retained `LegacyReversalFilmCard` implementation.
- Final implementation screenshot: `/tmp/punctum_reversal_legacy_restored.png`.
- Implementation dimensions: `1440 x 3168`; native Android viewport `360 x 792dp` at density `4.0` (`640dpi`).
- State: Android home, reversal-film style, first six galleries in a two-column grid after a fresh app launch.

## Visual verification

- The original warm gray paper texture is restored.
- The original gallery-title typography, size and position are restored.
- The original 3:2 cover opening and four dark inner edges are restored.
- The original date and `SLIDE · DIAPOSITIVE` footer are restored.
- All Kodak-inspired colored bars, processing marks and custom Punctum badges are hidden.
- The experimental implementations remain in source and are disabled by `KodakInspiredReversalFilmEnabled = false` for a possible future revisit.

## Interaction and behavior checks

- Tapping the first card opens `徕卡影像` with its metadata and thumbnails intact.
- Android back returns to the home grid normally.
- Release build and Release Lint pass.
- The release APK is installed on OPPO PMA110.

final result: passed

---

# Design QA: home label and added-album state

## Evidence

- Home screenshot: `/tmp/punctum_studium_aligned.png`.
- Album picker screenshot: `/tmp/punctum_album_picker_added.png`.
- Device viewport: `1440 x 3168` on OPPO PMA110.

## Verification

- The home eyebrow reads `- PUNCTUM · STUDIUM -`; its `48dp` centering container matches both action buttons, with a `1dp` optical compensation for the font's visible glyph center.
- Albums already present in Punctum render with a checked checkbox, muted row treatment and the exact status text `已添加`.
- The accessibility tree reports each existing-album row as `enabled=false`.
- Tapping an existing-album row leaves the selection unchanged and the confirm action disabled when no new album is selected.
- The shared home header top inset is increased from `16dp` to `22dp`; postcard, ticket and reversal-film modes inherit the same `6dp` downward shift without changing their internal card geometry.
- Device screenshots for the three modes are `/tmp/punctum_home_weight_1.png`, `/tmp/punctum_home_weight_2.png` and `/tmp/punctum_home_weight_3.png`.
- Release build, Release Lint and APK installation pass.

final result: passed

---

# iOS Android alignment — 2026-09-15

## Scope and baseline

- Android reference: local `3b714ba`, file tree equivalent to remote `b1d0ba5` (verified before editing).
- iOS updated from 0.5.5 / 55 to 0.5.7 / 57. Android source is unchanged.
- Full implementation notes: `changelog/ios.md`, 2026-09-15 section.
- The abandoned modified-time sorting toggle remains absent.

## Verified evidence

- Final Debug simulator test run: `/tmp/punctum-ios-verified-tests.log`; **TEST SUCCEEDED**, 10 tests, 0 failures.
- Final Release iphoneos build: `/tmp/punctum-ios-verified-release.log`; **BUILD SUCCEEDED**.
- Tests cover EXIF timezone/invalid values plus existing deletion tombstone and formatting rules; they do not establish end-to-end deletion UI correctness.
- Final IPA ZIP integrity and Info.plist verified: 0.5.7 / 57, `com.chessyyq.punctum`.
- IPA: `iOS/IPA/Punctum-0.5.7-unsigned.ipa`.
- SHA256: `20b5f64c9f662f94d405bf37721b730577391b0ffc1a37ed0958bc0d61bce859`.
- Simulator: Punctum iPhone 14 Pro, iOS 18.2, `FAE0B8A7-B631-4AEA-AE84-133224A0A1E2`.
- Visible UI checked: all three home styles; new header label and subtitle; postcard spacing; ticket text; reorder sheet with fixed add action; moving a gallery via accessibility actions, dismissing and reopening with the changed order intact; original order restored afterwards.
- Gallery open, thumbnails, detail metadata and right-edge next-photo action checked on the simulator.
- Final build also checked for sort-sheet → album-picker presentation, selected/locked existing albums and exact `已添加` copy; cancellation returned home. Original reversal-film style restored.

## Motion review (animate / review-animations)

| Before | After | Why |
|---|---|---|
| Delayed navigation after button rebound | Release dispatches action immediately; rebound runs independently | Removes avoidable response delay |
| A canceled style transition could leave cards transparent | Retargeting back to the active style explicitly restores opacity | Rapid repeated switching remains interruptible |
| Removing detail as exit begins | Keep detail mounted for 140ms and disable underlying hit testing | Makes the exit visible without click-through |
| Capture index can change while paging | Pin the active gallery's asset order for the detail session | Preserve pagination identity during background refresh |

Source-level motion verdict: **Approve** for the implemented fade/transform timings and cancellation handling. Home and press transforms honor reduced motion; native interactive movement uses UIKit behavior. Native drag feel is not claimed as visually accepted from source review alone.

## Outstanding device checks and limits

- Manual long-press movement, both directions of edge auto-scroll, interrupted drag, Live Photo audio/video, deletion confirmation/cancellation and large-library frame/memory behavior still require iPhone verification.
- The computer-use drag attempt did not establish a successful long-press reorder or swipe-delete; only the explicitly described accessibility movement and click paths were verified.
- Physical iPhone 14 Pro is paired, with AltStore-installed `com.chessyyq.punctum.YJTQFDXG3N`, 0.5.5 / 55. It was not replaced.
- Native signed build is blocked by missing provisioning profile for `com.chessyyq.punctum`. Deliver the unsigned IPA through the user's existing AltStore identity for an in-place update.
- EXIF indexing only reads locally available originals; cloud-only assets use Photos dates until their originals are local. First indexing cost scales with library size and was not benchmarked on a large physical library.
- An intermediate overlapping build hit an Xcode build-database lock; the final test run was rerun serially and passed.

Result: implementation and build verification complete; physical-device acceptance pending.

## Follow-up: gallery entry gate fix

User reports that a three-photo gallery opens, while a larger gallery remains at “正在准备图集”. Source confirms entry awaited both a complete EXIF index and two unbounded high-quality image requests. Entry now loads the first page without awaiting either; image rendering and time indexing continue independently. The gallery visit pins its order across detail navigation and pagination; returning home releases that snapshot.

- Version remains 0.5.7 / 57; replacement: `iOS/IPA/Punctum-0.5.7-gallery-entry-fix-unsigned.ipa`.
- `/tmp/punctum-gallery-entry-tests.log`: 10 tests, 0 failures, TEST SUCCEEDED.
- `/tmp/punctum-gallery-entry-release.log`: BUILD SUCCEEDED.
- IPA ZIP and version verified.
- Simulator launched with `-openFirstGallery`: Recents (33 photos) and image grid visible; preparation overlay absent. This small simulator library does not establish real-device large-library performance.
- User to install via existing AltStore identity and retest the affected physical-device gallery.

## Follow-up: isolate the swipe-delete card

User reports horizontal paging inside the lifted deletion card. The transformed view was the entire TabView, and its ancestor scroll lock did not reliably cover the internal pager. The moving layer now contains one fixed, noninteractive DetailPage; its photo identity, display number and prepared metadata are captured when vertical intent begins. The real pager is hidden, rejects selection changes and is rebuilt before revealing the unchanged selection after cancellation. Confirmation queues the captured photo identity. Finishing blocks repeated deletion gestures until cleanup completes.

- Motion source review (`animate` / `review-animations`): existing 200ms confirmation and cancellation spring retained; transforms only affect the single-photo card. Card has no paging catcher, scrolling, live playback or hit testing. No additional decorative motion.
- `/tmp/punctum-delete-card-tests.log`: TEST SUCCEEDED, 10 tests, 0 failures. These existing unit tests do not exercise physical swipe gestures.
- `/tmp/punctum-delete-card-release.log`: BUILD SUCCEEDED.
- Delivery: `iOS/IPA/Punctum-0.5.7-delete-card-fix-unsigned.ipa`, version 0.5.7 / 57, includes the gallery-entry fix.
- Device acceptance pending: move horizontally during an upward drag; cancel then page normally; commit repeatedly; delete the final photo; check Live Photo behavior outside deletion. No successful physical-device gesture validation or installation is claimed for this build.

## Follow-up: swipe-delete rendering cost

The user reports a dropped-frame feel after the single-card fix. Source review found gesture-start page creation, per-progress reconstruction of the pager's photo collection, and a changing full-page blur shadow. These are performance risks; no device frame-time trace was captured.

### Motion review (animate / review-animations)

| Before | After | Why |
| --- | --- | --- |
| Create the single-photo page when dragging starts | Keep one current-photo card mounted invisibly before dragging | Avoid initial page construction and image-loader startup at gesture onset |
| Inline pager and photo subtrees depend on every parent progress update | Equatable boundaries around the pager, current card and replacement page | Stable inputs skip subtree updates; selection, lock, metadata and geometry changes still propagate |
| Animate full-page shadow blur and slight rotation | Remove both; retain scale and upward translation | Reduce offscreen rendering and visual motion complexity |
| Reduced motion still scales and springs back | Disable scaling and cancel spring, reduce direct drag distance | Gentler feedback for the accessibility preference |

Source review: **Approve** for the scoped simplification. Existing confirmation duration (200ms), gesture threshold, frozen deletion target and hidden pager safeguards are retained. The existing cancel cleanup rebuild remains covered by the stationary card. No motion timing is claimed to establish a measured frame-rate improvement.

Device checks pending: gesture onset on portrait / landscape / Live Photo, slow reversal, cancellation, repeated deletion, last-photo deletion, horizontal movement during lift, and normal paging after cancellation. Check both warm and newly loaded photos. The invisible current card adds one resident photo view; large-library memory behavior still requires physical-device observation.

Verification: final Debug simulator test run `/tmp/punctum-swipe-performance-tests.log` passed (10 tests, 0 failures); serial Release build `/tmp/punctum-swipe-performance-release.log` passed. Existing unit tests cover data/formatting behavior, not gesture frame rate. Version remains 0.5.7 / 57. Android is unchanged. Delivery uses the existing AltStore installation route; no physical-device install or frame-rate measurement is claimed.

## Follow-up: temporary photo-only pinch zoom

User requests lifting/enlarging the image layer with two fingers and springing back on release, while the metadata black area remains stationary. Added a UIPinchGestureRecognizer to the existing image input catcher. Both initial touch points must lie in the photo. A local DetailPage scale (1–3) and initial pinch anchor only transform DetailPhotoFrame's image stack, before its surrounding padding; metadata layout is unchanged. The photo is drawn above metadata while expanded. Static deletion cards have no pinch catcher.

### Motion review (animate / review-animations)

| Before | After | Why |
| --- | --- | --- |
| No temporary magnification | Finger-driven photo-only scale around the initial two-finger center | Provides intentional visual inspection feedback without shifting metadata |
| Paging / deletion / Live Photo can contend for input | Pinch locks selection and deletion, stops playback, suspends ancestor pans and restores them on end/cancel | Avoid accidental page navigation or deletion while inspecting an image |
| A new pinch can arrive during return animation | Cancel the pending unlock and retarget the local scale | Prevent an old callback from unlocking the current gesture |
| Automatic elastic return for all users | Existing 0.30 response / 0.88 damping spring; reduced motion uses 160ms ease-out | Reuse established motion; provide a no-bounce accessibility variant |

Source review: **Approve**. Scale is local to the photo page, with no frame-size or metadata-layout animation. Reuses existing image resources and existing spring values. This is source-level approval; two-finger feel, pinch-from-Live-hold, cancellation, backgrounding, portrait/landscape photos, reduced motion, and paging/deletion immediately after release need physical-device verification. Existing unit tests do not exercise multi-touch input. No physical-device install is claimed.

Final verification: `/tmp/punctum-pinch-tests.log` reports TEST SUCCEEDED (10 tests, 0 failures); `/tmp/punctum-pinch-release.log` reports BUILD SUCCEEDED. Delivered as `iOS/IPA/Punctum-0.5.7-photo-pinch-unsigned.ipa`, retaining version 0.5.7 / 57 and prior gallery-entry / single-card / swipe-performance changes. Android remains unchanged.

## Follow-up: horizontal detail paging performance

User reports visible stutter during horizontal swipes. Source inspection found duplicate full-frame requests across paging/deletion copies, image decoding directly in the Photos callback without an explicit background queue, metadata and Live Photo work for unselected pages, and nine-photo EXIF fan-out per selection. No physical frame-time capture is available.

Changes: coalesce full-frame requests by asset/size with independent cancellation subscribers; decode full-frame images on one background queue; drop stale deliveries; retain only current and adjacent full DetailPages with stable tags for all others; defer selected-page Live Photo and metadata work by 180ms, reduce metadata neighborhood to three and publish one batch. Empty PHLivePhotoViews are omitted and cancelled Live Photo tasks cannot update a page. Existing still-photo resolution (2200px), aspect handling, EXIF orientation, pinch and deletion motion remain.

### Motion/performance review (review-animations)

| Before | After | Why |
| --- | --- | --- |
| Duplicate original-data requests and decodes for one photo | One request/decode shared by active consumers | Reduce CPU, memory and original-file access during paging |
| Decode inside an unspecified Photos callback context | Explicit serial background decoding and main-thread publication | Keep synchronous ImageIO work away from UI updates |
| Complete page resources for distant pages | Current/previous/next full pages only, stable lightweight tags elsewhere | Bound expensive page count without changing gallery order |
| Unselected pages load Live Photo and metadata; nine-photo metadata fan-out | Selected-page resources after 180ms, three-photo metadata prefetch and batched publication | Reduce work during rapid repeated swipes |

Source review: **Approve** for the bounded work and unchanged gesture animation. New decoder regression tests cover landscape proportions and both colored edges, EXIF rotation and corrupt data. Real-device frame rate, rapid repeated paging in both directions, cold/iCloud photos, watermarked images, Live Photo, pinch return and swipe deletion remain pending. Do not equate compilation or simulator tests with physical paging smoothness.

Final verification: `/tmp/punctum-paging-tests.log` reports TEST SUCCEEDED, 13 tests / 0 failures; `/tmp/punctum-paging-release.log` reports BUILD SUCCEEDED. The corrupt-image test intentionally exercises ImageIO's failure path. Xcode project regeneration changed only the four references needed for `FullFrameImageDecoderTests.swift`. Delivery: `iOS/IPA/Punctum-0.5.7-paging-performance-unsigned.ipa`, version 0.5.7 / 57. Existing local work is retained, Android untouched, no Git commit or upload.

## Regression fix: horizontal swipes rebound

User reports the paging-performance IPA cannot finish horizontal swipes. Reverted the conditional Group/placeholder page structure in DetailPager to the exact direct DetailPage-plus-tag structure from before the paging optimization. All page identities now remain structurally fixed across index changes. The earlier three-full-page optimization is withdrawn; shared image requests, background decoding and delayed metadata/Live Photo work remain.

Actual simulator gesture verification on the rebuilt app (iPhone 14 Pro / iOS 18.2, Recents 33 photos): CUA horizontal drags produced No.1 → No.2 → No.3 → No.2 → No.1, with each completed page number verified from the accessibility tree. No edge-tap substitution was used. This validates completion in both directions, not physical-device frame rate.

13 unit tests passed in `/tmp/punctum-paging-rebound-tests.log`. Release log: `/tmp/punctum-paging-rebound-release.log`. Version remains 0.5.7 / 57; replacement IPA: `iOS/IPA/Punctum-0.5.7-paging-rebound-fix-unsigned.ipa`. Android and existing uncommitted work retained; no commit or upload.

## Fix: portrait image top clipped in detail

The detail scroll content applied `-safeAreaTop` to the entire stack while portrait images had no compensating top padding. Removed the parent negative inset, positioned portraits below the top safe area and constrained exceptionally tall portraits to available viewport height while preserving aspect ratio. Landscape positioning keeps the previous net offset. Actual image bounds continue to drive pinch anchoring and the Live Photo badge. Metadata remains below the image.

Added three layout regression tests: normal portrait safe-area placement, tall-photo full-frame aspect fit, and unchanged landscape position. Final Debug test run `/tmp/punctum-portrait-tests.log` passed 16 tests with zero failures.

Simulator visual check (iPhone 14 Pro / iOS 18.2): imported a synthetic 600x1600 test image with red TOP and blue BOTTOM strips; both strips and text are fully visible in initial detail display below the top cutout. Horizontal drag to the next landscape photo preserved its established position; reverse drag returned to the portrait (No.1 → No.2 → No.1). This verifies the synthetic image and navigation, not every user photo. Test fixture remains in simulator Recents only.

Release log: `/tmp/punctum-portrait-release.log`. Delivery: `iOS/IPA/Punctum-0.5.7-portrait-fit-unsigned.ipa`, version 0.5.7 / 57. No physical installation, commit or upload is claimed.

## Follow-up: portrait must meet the top screen edge

User clarifies the added portrait safe-area gap is unwanted. Portrait topPadding is now exactly zero; the removed parent negative inset stays removed. Thus the image begins at the screen edge without being shifted above it. Native camera cutout coverage is accepted by the user's edge-to-edge layout request. Tall-image aspect-fit and landscape layout remain unchanged. Updated the portrait layout regression test to require zero top padding even with a 59pt safe area.

Package: `iOS/IPA/Punctum-0.5.7-portrait-edge-unsigned.ipa`, version 0.5.7 / 57. This supersedes the preceding safe-area placement decision.

Verified rebuilt simulator with the same 600x1600 fixture: red image area now reaches the physical top screen boundary, with no added safe-area gap; blue bottom strip remains visible. The native camera cutout overlays the image as expected for edge-to-edge content. Final tests: 16 passed / 0 failures in `/tmp/punctum-portrait-edge-tests.log`; Release log `/tmp/punctum-portrait-edge-release.log`.

# iOS 0.5.8 release and Android photo pinch — 2026-09-15

## Audit and scope

Reviewed the previous handoff dated 2026-09-09, its historical baseline `4a4cc01 / f7cab89`, subsequent Android release commit `3b714ba`, all current iOS changes and the intervening QA records. Fresh fetch reports `origin/main=b1d0ba5`; `git diff origin/main HEAD` is empty. Current uncommitted changes are retained and are not on GitHub.

- iOS alone advances from 0.5.7 / 57 to **0.5.8 / 58** in project.yml and the generated project. The new top changelog section describes final behavior, including the rejected paging placeholders and rejected portrait safe-area gap. README, root handoff summary and the external PUNCTUM_HANDOFF.md have been updated; the former handoff is archived next to it.
- Android stays **0.5.7 / 57**. Only Android production source changed: `ui/DetailScreen.kt`. No Android layout, version, resources, repository or other screen is edited.

## Android motion review (animate / review-animations)

| Before | After | Why |
| --- | --- | --- |
| Photos cannot be temporarily enlarged | Two fingers within the photo scale its graphics layer 1–3x around their initial center | Inspect photo detail without moving the metadata layout |
| Existing single-finger actions can receive multi-touch movement | Initial-pass pinch claims multi-touch; pager, delete and Live Photo paths defer for that gesture | Avoid accidental navigation, deletion or playback |
| Release needs a stable original view | Reuse dampingRatio 0.82 / stiffness 700 spring; cancel old return on a new pinch | Keep an interruptible return consistent with Android motion |
| System animations disabled | Restore immediately after direct manipulation | Respect the user's system animation preference |

Source-level verdict: **Approve**. Per-frame scale uses primitive float state read in graphicsLayer; no size/metadata-layout animation or additional image request. Existing deletion and move-transition copies leave pinch disabled. `PHOTO_PINCH_ENABLED` provides one rollback switch. Device multi-touch feel and the full combination with paging, live playback and deletion remain user acceptance checks.

## Verification

- Android Release + Release Lint: `/tmp/punctum-android-pinch-build.log`; no Lint errors. Report: `app/build/reports/lint-results-release.html`. Existing non-error diagnostics remain outside this feature's scope.
- OPPO PMX110, serial `3B167100EXP00000`: `adb install -r` succeeded, package `com.punctum.gallery` reports 0.5.7 / 57. MainActivity started, screenshot `/tmp/punctum-android-pinch-installed.png` shows original galleries and counts. No uninstall or data clearing. Installation/startup do not prove multi-touch feel.
- iOS final tests `/tmp/punctum-ios-058-tests.log`: 16 tests, 0 failures; Release `/tmp/punctum-ios-058-release.log`: BUILD SUCCEEDED.
- `iOS/IPA/Punctum-0.5.8-unsigned.ipa`: ZIP integrity and Info.plist version verified; SHA256 `21ed0a5c2700acfbef658bda2058d271112095b3b7db2b02cc430d7fdf0a4ca1`. User to install using the existing AltStore identity.
- Android artifact: `APK/Punctum-0.5.7-photo-pinch-release.apk`. All previous source and rollback switches retained; no Git commit or upload.

Final Android rebuild after replacing boxed per-frame scale state with primitive float state: Release + Lint passed (0 errors, 17 warnings, 8 informational diagnostics). Final APK SHA256 `5f9cda82351a66f5de4d2962cd70fdbd9cdbe2495a4c0287bc4bb77179b8e262`; final package was again installed with `-r` successfully and launched. Android version remains 0.5.7 / 57.

## Android follow-up: two-finger pan while magnified

User requests moving the raised photo horizontally and vertically while both fingers remain down. Scoped to the current Android implementation; iOS source/version unchanged. Added x/y graphics-layer translation driven by the two-finger centroid. Combined zoom/pan updates preserve the point beneath the fingers, including the clamped zoom range. Re-anchoring an interrupted return compensates the existing translation instead of changing the visible image position.

| Before | After | Why |
| --- | --- | --- |
| Two fingers only change photo scale | Two fingers also move the magnified image in both axes | Inspect different photo regions without moving metadata |
| Scale-only return | One spring progress restores scale and both translations together | Avoid separate return timings or a residual offset |
| New pinch can change the transform anchor | Compensate translation on anchor change | Preserve position while interrupting the return |

Motion source review (`animate` / `review-animations`): **Approve**. Only transform properties change; retained existing spring, gesture locks, reduced-motion behavior and rollback switch. 10,000 randomized geometry checks passed the combined zoom/pan focal-point invariant; this checks math, not device multi-touch feel.

Release + Lint passed: `/tmp/punctum-android-pinch-pan-build.log`. Artifact: `APK/Punctum-0.5.7-pinch-pan-release.apk`. Android stays 0.5.7 / 57. Multi-touch feel and return need user verification on the connected PMX110.

Final install evidence: `adb install -r` returned Success on PMX110 `3B167100EXP00000`; MainActivity launched and package version verified as 0.5.7 / 57. Data retained. APK SHA256 `f2b819cc46e8688deca3a30506eaa321300e198280d2c0d50788f9752eec15eb`. No Git commit or upload.

# Android 0.5.8 and iOS pinch-pan completion — 2026-09-15

User explicitly confirms the Android combined zoom/pan/return experience and requests the next patch version. Android is advanced from 0.5.7 / 57 to **0.5.8 / 58**, with the already-accepted gesture code unchanged this turn. Changelog, root current-state summary, README and handoff now distinguish accepted Android behavior from pending iOS feel acceptance.

Android Release and Lint succeeded: `/tmp/punctum-android-058-build.log`. Final APK `APK/Punctum-0.5.8-release.apk`, SHA256 `da9f586920c28cf8e2eac85a8a13089353525091e7384719bac2da60e2fb5ea8`. Covered existing PMX110 installation using `-r`, returned Success, launched MainActivity and verified versionName 0.5.8 / versionCode 58. No uninstall or data clearing.

## iOS motion review (animate / review-animations)

| Before | After | Why |
| --- | --- | --- |
| Pinch scale follows fingers but their center movement is ignored | Pinch recognizer sends centroid displacement from gesture start; only photo layer offsets | Match Android's accepted two-finger movement while leaving metadata fixed |
| Return resets scale alone | Same animation transaction resets scale and x/y displacement | Restore original size and position together |
| Photo can leave the screen during a gesture | Cancellation and page cleanup reset displacement as well as scale | Avoid persisting offsets into another photo |

Source review: **Approve** for the scoped extension. Reuses existing spring/reduced-motion return and paging/deletion/Live Photo locks. Offset is a visual transform after photo scale and before layout padding; portrait top alignment and metadata placement are unchanged. iOS stays **0.5.8 / 58**. Actual two-finger feel remains pending user verification through AltStore; no iPhone installation is claimed.

Latest iOS artifact: `iOS/IPA/Punctum-0.5.8-pinch-pan-unsigned.ipa`. Earlier 0.5.8 IPA without the filename suffix predates iOS panning.

Final iOS verification: `/tmp/punctum-ios-pinch-pan-tests.log` reports TEST SUCCEEDED (16 tests, 0 failures), and `/tmp/punctum-ios-pinch-pan-release.log` reports BUILD SUCCEEDED. These existing tests cover model/decoder/layout regressions, not multi-touch feel. Version configuration remains 0.5.8 / 58. No Git commit or upload.


## iOS 0.5.8 interaction fixes — 2026-09-16

- Scope: invitation-card drag/tap arbitration, foreground refresh/index reuse and interrupted-gesture cleanup, gallery-management sheet scrolling. Android files not edited in this round.
- Release build passed: `/tmp/punctum-ios-interaction-release.log`.
- Debug application and XCTest target compiled; simulator remained on the system startup spinner and simulator control calls timed out. Test execution was interrupted; do not report the full XCTest suite or UI scenarios as passed.
- Extracted production `PressTravelGate` executed on the host with five passing regression assertions: small jitter, drag-return-to-origin, vertical movement, cancellation, new gesture. XCTest equivalents added in `PressTravelGateTests.swift`.
- IPA archive validated, bundle `com.chessyyq.punctum`, version/build `0.5.8 / 58`: `iOS/IPA/Punctum-0.5.8-interaction-fixes-unsigned.ipa`.
- SHA256: `a2f741ea6c14cab844019eaf93e8291ca1ecfdcea27619041e386a9164fb6731`.
- Physical-device checks pending: repeated left/right invitation-card swipes without album entry; short taps still enter; app-switcher return remains responsive from home/gallery/detail; list scrolls immediately with many galleries; pinch and interrupted delete drag recover correctly. Use the same AltStore identity for an overlay installation.

### review-animations scope review

| Before | After | Why |
| --- | --- | --- |
| Card press could finish after horizontal travel | Window-coordinate travel over 10pt permanently cancels this press | Scroll release cannot become navigation |
| Interrupted pinch could retain suspended ancestor pans | Resigning active finishes pinch and restores recognizers | Gesture ownership must end on lifecycle interruption |
| Sheet expansion preceded list scrolling; fixed 290pt list | Content scrolling prioritized; list fills available height | Direct manipulation reaches lower rows immediately |

Code review: Approve for packaging; no new animation added, existing reduced-motion behavior retained. Device acceptance remains pending, especially foreground responsiveness with a large photo library.


## iOS delete release timing — 2026-09-16

Purpose: spatial continuity for the full-screen card after release. User explicitly requested a slower second phase. Native SwiftUI transform animation and existing `(0.18, 0.74, 0.25, 1)` curve retained; duration extended from 200ms to 400ms. This deliberate, long-distance exit justifies exceeding the usual 300ms budget. First-phase dragging, arming threshold, cancel spring and reduced-motion branch are unchanged.

### review-animations

| Before | After | Why |
| --- | --- | --- |
| Automatic flight covers most of a screen in 200ms | Same path and curve over 400ms | User found release too abrupt; give travel more visible time |
| Photo commit waits a fixed 210ms | Normal-motion wait derives from exit duration + 10ms | Avoid removing the fixed card halfway through its longer animation |

Verdict: Approve scoped timing change for device trial. No new layout animation or image work; existing fixed noninteractive deletion card retained. True device smoothness and preferred pace remain unverified.

- Release build passed: `/tmp/punctum-ios-delete-exit-release.log`.
- `git diff --check` passed. No new tests for this timing-only adjustment.
- IPA: `iOS/IPA/Punctum-0.5.8-delete-exit-unsigned.ipa`; version/build unchanged at `0.5.8 / 58`; packaging verifies bundle metadata and ZIP integrity.
- Previous simulator system-startup failure still limits interactive verification; no claim of device validation. User should compare release at the upper limit and just past the deletion threshold, then repeat consecutive deletions.


## iOS first-stage drag and EXIF — 2026-09-16

| Before | After | Why |
| --- | --- | --- |
| First drag sample applies accumulated translation | Rebase to first recognized upward sample; subsequent updates disable inherited animations | Prevent recognition delay from producing a large initial jump |
| Every access to visiblePhotos filters all photos during body updates | Visible snapshot updates only on source changes or queued deletion | Remove repeated per-frame O(n) filtering |
| Metadata waits for full original Data and entire neighbor batch | Read ImageIO properties from original URL when available; publish each result immediately | Avoid large image copies and slow-neighbor blocking |
| Prefetch task keyed by numeric index | Keyed by current photo ID | Consecutive deletion changes identity even when index stays the same |

review-animations: Approve scoped code changes for device trial. Direct manipulation remains unanimated, existing 400ms release and cancellation spring retained, fixed noninteractive card preserved. Rebased 140pt drag range means the deletion threshold is now relative to the first recognized upward sample. No new animation or loading blocker added.

Release build passed: `/tmp/punctum-ios-drag-metadata-release.log`. `git diff --check` passed. IPA packaging checks archive integrity and version `0.5.8 / 58`. No physical-frame-rate or UI-test pass claimed; previous simulator system-startup failures remain a verification limitation. Test on device with slow initial upward drags, fast upward drags, reversal/cancel, several consecutive deletions at the same index, and a large next image with EXIF. Cloud-only originals may still require download; missing source EXIF is not synthesized.

IPA: `iOS/IPA/Punctum-0.5.8-drag-metadata-unsigned.ipa`. Android unchanged this round; no commit or push.


## iOS native deletion redesign — 2026-09-16

Scope: redesign iOS deletion using the accepted Android motion as reference. Android source remains unchanged. Version remains 0.5.8 / 58.

### review-animations

| Before | After | Why |
| --- | --- | --- |
| Drag updates SwiftUI state and changes a separately rendered page | UIKit captures the visible pager once; per-frame changes touch only the inert snapshot | Keep photo/EXIF/pager rendering outside the direct manipulation loop |
| Drag hits a hard cap then flies a full page offscreen | Android drag resistance (0.14, max progress 1.12), then shrink toward trash | Continuous physical resistance and a clear destination |
| Single exit curve drives the full page | Android quadratic travel and smoothstep shape tracks sampled into Core Animation keyframes | Shape and travel stay continuous from any armed release position |
| Fixed delays govern handoff | Animation completion, then host content layout, then restore | Avoid deleting the visual surface before motion completes |
| Active view could disappear during ownership transfer | Hide only its rendering layer, retain hit-tested hierarchy; restore suspended scroll pans on settle/teardown | Preserve touch sequence and prevent residual interaction locks |

Verdict: Approve scoped implementation for physical-device trial. 400ms is retained at the user's request for this long-distance committed motion. Cancellation uses a 300ms damped return; reduced motion uses a 160ms fade without card travel. No heavyweight shadow or interactive pager inside the moving card. Old SwiftUI path retained behind `nativeDeletionEnabled = false`.

Validation: 21 regression tests including overdrag continuity and release-position continuity; host checks cover 400 monotonic motion samples. Final logs: `/tmp/punctum-ios-native-delete-final-tests.log`, `/tmp/punctum-ios-native-delete-release.log`. Packaging checks bundle version and ZIP integrity.

UI limitation: Simulator launched and displayed correct full-frame content and page numbers. Automated CUA drags only generated touch-down/touch-up in the diagnostic recognizer (dy=0, no touchesMoved). Therefore this round does NOT claim verified dragging, cancelling, deletion, or physical frame-rate. Earlier apparent horizontal page changes may be edge taps. Temporary recognizer logging was removed. Physical checks needed: short drag cancellation, slow/fast upward drag, sequential deletes, horizontal paging, Live Photo hold, two-finger zoom, and app-switcher interruption. Do not mark motion accepted until the user confirms.

Delivery: `iOS/IPA/Punctum-0.5.8-native-delete-unsigned.ipa`. No commit/push.

Native deletion IPA SHA256: `1040b4bd5c1cf5700c9f815548b802eaec7baba027f2e7e85a76e9c1f501c9ca`.


## iOS native deletion 280ms — 2026-09-16

User confirmed the native snapshot version was substantially smoother, but requested a shorter release. Only the active native commit duration changes; legacy fallback remains 400ms.

| Before | After | Why |
| --- | --- | --- |
| Native release 400ms | 280ms | User-requested shorter completion, 30% less time; same path and easing |

review-animations: Approve scoped duration adjustment. Drag resistance, cancellation spring, reduced-motion branch and completion-based handoff are unchanged. Release build passed (`/tmp/punctum-ios-native-delete-280-release.log`); archive integrity and 0.5.8 / 58 metadata verified. `git diff --check` passed. Existing motion tests were not rerun for a duration-only change. 280ms device preference remains pending.

IPA: `iOS/IPA/Punctum-0.5.8-native-delete-280ms-unsigned.ipa`.
SHA256: `d0f3d143d550f7bef6a92fe18b26fe04029a8880b6752f53092f31c3e58d12e8`.


## 2026-09-16 GitHub synchronization audit

Scope: full uncommitted Android 0.5.8 photo gestures and iOS 0.5.8 parity/browsing/interaction/native-deletion changes, new services and regression tests, generated project, version configuration and documentation. User approved the final 280ms iOS deletion state and explicitly requested upload.

- Android Release + Release Lint rechecked successfully: `/tmp/punctum-publish-android.log`.
- iOS latest regression evidence: 21 tests passed (`/tmp/punctum-ios-native-delete-final-tests.log`); final 280ms Release passed (`/tmp/punctum-ios-native-delete-280-release.log`). No code changes during this publishing pass.
- README, iOS README, platform changelogs, changelog guidance, root index and repository handoff updated together. Earlier local handoff retained outside the repository as an archive; current copy synchronized to repository PUNCTUM_HANDOFF.md.
- Prior local/remote baseline trees verified equal (`e174ef2a0ac161bf17721eb713811b8a9c44286f`). Their parallel Git history was reconciled without changing baseline content before collecting this upload.
- APK/IPA and signing material remain excluded. User confirmation is scoped to Android pinch/pan and iOS native deletion feel/280ms timing; it does not imply all earlier reported issues or gesture combinations were independently reaccepted.
