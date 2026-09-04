# Design QA: original reversal-film card restored

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
