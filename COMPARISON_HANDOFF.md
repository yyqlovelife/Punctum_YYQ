# iOS 大图对比模式交接

## 当前发布与接续 · 2026-09-20

iOS 0.5.9安装包SHA256：`a55562270301e7b8e61acd10890c7a072667d1510ad63a9b1f396f1c9bc27c2e`，Release与包内版本资源核对通过。

- **iOS 0.5.9 / 59**：新增大图对比模式，支持系统单选、横竖布局、独立/联动缩放及删除返回；同时包含1–5倍保留缩放、批量删除性能和连续翻页续载修复。
- **Android 0.5.8 / 58**：包含按进入时可见范围判断的返回定位、返回闪动修复、缓存合并写入、按实际尺寸解码、逐张高清发布及异常预览比例修复。首次进入图集支持可取消加载，超过500ms才显示居中小卡片，完成即关闭。
- 安卓最新本地包：`APK/Punctum-0.5.8-delayed-loading.apk`，已覆盖安装PMX110；iOS本地包：`iOS/IPA/Punctum-0.5.9-unsigned.ipa`。
- 当前实现与待验收项见本节及 `SESSION_HANDOFF_2026-09-20.md`。下方旧日期内容为历史记录，旧“最新包”“未提交上传”及版本号均按当时范围理解。
- 发布源码、资源、测试、构建配置与文档到GitHub；安装包、签名凭据和含用户照片/URI的原始录屏与设备日志仅留本机。公开验证摘要见 `docs/RELEASE_VALIDATION_2026-09-20.md`。


## 当前接续状态 · 2026-09-20

新对话先读 [本轮交接](SESSION_HANDOFF_2026-09-20.md)。两端仍为0.5.8/58，本轮增量未提交上传。

- Android最新包：`APK/Punctum-0.5.8-return-flash-fix.apk`，已覆盖安装PMX110。用户确认返回定位逻辑正确；随后的一帧闪动已调整显示顺序，尚待用户复测。6项位置回归通过。
- iOS最新包：`iOS/IPA/Punctum-0.5.8-pagination-fix-unsigned.ipa`，含保留缩放、对比模式、批删卡顿和连续翻页修复；41项回归通过，剩余真机验收见交接。
- 构建证据归档：`docs/verification/2026-09-20/`。以下旧日期章节保留历史，其“最新包”和验收结论以当时范围为准。


更新：2026-09-18。已按用户“继续对比模式”完成实现与构建，35 项回归通过。解锁后已验证系统选图、混合布局、独立/联动点击放大、双击复原，以及应用/系统取消和删除新选合成测试图后返回原图。后续检查时 Mac 再次锁屏；入口图标最终修复、多指手感、删原图和权限补充流程仍需界面/真机验证。当前不宣称全部交互验收通过。版本保持 0.5.8 / 58，Android 未改，未提交或上传。

## 实现

- 大图操作态右上新增双图图标，系统 PHPicker 限图片、单选、确认后进入全屏对比。取消停留原图；选择原图自身会提示选择另一张。
- 两竖图左右排；两横图、横竖混合及方图上下排，各宫格独立裁切、完整适配原比例。
- 默认独立 1–5 倍缩放，松手保留；单击原尺寸放至2倍，双击放大态复原、原尺寸双击放至2倍，捏小至1倍复原；单指/双指平移。
- 联动开关变为金色激活图标，以最后操作图同步倍率和归一化偏移；从任意一侧操作均可联动，关闭保留两图当前状态。另一张较晚载入也同步。
- 两图各有删除键、自定义确认框；PhotoKit 成功后才退出对比并更新墓碑、列表与概览。删新图保留原图，删原图去下一张，末张回上一张，空图集回画廊。系统取消/失败留在对比，不伪造成功。旧大图待删队列保持。
- 受限照片权限下，picker 导出图仍可比较；删除无访问权限的选图时打开系统 limited-library 管理器，取得访问后重新确认删除。未授权则提示，绝不删错资产。
- 入口、返回、联动、删除、确认、取消和重试复用 IconPressButtonStyle。点击缩放/复原采用0.30秒、0.88阻尼，直接捏合/拖动不附加动画；中断动画时从可见图层状态接手，减少动态效果直接复原。

## 文件入口

- Models/ComparisonState.swift：布局、双图状态、联动、返回ID；Models/PhotoZoomState.swift：沿用上轮缩放并扩展状态同步/接手。
- Views/ComparisonPhotoPicker.swift：系统单选、导出回退、limited-library管理器。
- Views/ComparisonImagePane.swift：UIKit图片层、手势及可中断动画。
- Views/ComparisonScreen.swift：双宫格、工具栏、确认、加载/重试。
- Views/DetailScreen.swift：入口、sheet→cover、实际删除与当前页保持。
- Views/RootView.swift / ViewModels/GalleryViewModel.swift：成功删除回调。已修正暂停草稿误传给GalleryScreen的参数。
- Resources/compare_photos.png：ImageGen双框山线图标，已加入Bundle；原候选design/comparison/compare-icon-candidate.png。使用original渲染，移除导致显示异常的screen混合；最终图标显示待解锁复核。

以上源码相对 iOS/Punctum/Punctum。Xcode工程已重新生成。

## 验证与产物

- 35项XCTest、0失败：包含原28项及7项对比回归，覆盖布局、独立、联动双向/上限/复原、联动切换、迟到图像布局、窗口尺寸变化、动画中断状态、删除返回ID。
- 日志 `/tmp/punctum-ios-compare-tests.log`；Release `/tmp/punctum-ios-comparison-release-final.log`。Release通过，IPA ZIP、0.5.8/58及图标资源检查通过。
- 最新包 `iOS/IPA/Punctum-0.5.8-comparison-unsigned.ipa`，SHA256 `0a558663028884b9901dacc89d20cc0db76b12c82022a735c8239bd51ef8633f`。沿用原AltStore身份签名覆盖安装。
- 上一项已交付 `iOS/IPA/Punctum-0.5.8-persistent-zoom-unsigned.ipa` 保留；不包含对比模式。
- 仅对合成测试图 LIVE B 做了系统删除流程验证：应用取消、系统不允许均留在对比；确认删除后返回原图。未删除用户真实照片。模型测试不能替代真实多指验证。

## 下一步验收

再次解锁后，先复核图标修复；模拟器安装 `/tmp/punctum-compare-derived/Build/Products/Debug-iphonesimulator/Punctum.app`，进入大图，点击中央显示按钮，再用右上对比图标打开系统选图。核对三种布局、独立/联动、连续缩放/双击/平移、图标及所有按键；使用专用测试照片验证删新图/原图/末张/唯一图和系统取消，并检查受限权限与iCloud失败重试。

保护原生280ms删除、列表稳定、原图比例/竖图贴顶、Live Photo及元数据；同一DerivedData不并行构建。所有工作仍直接在真实仓库进行，禁用L3 Workflow，不擅自提交上传。

## 批量删除卡顿修复 · 2026-09-18

验证：35项XCTest全部通过（`/tmp/punctum-batch-delete-final-tests.log`），Release通过（`/tmp/punctum-batch-delete-release.log`）；IPA完整性与0.5.8/58核对通过。最新包 `iOS/IPA/Punctum-0.5.8-batch-delete-fix-unsigned.ipa`，SHA256 `63d5f706e2bb20ad7e9846a136dd0dd2f2cf2e0ba46490658e3d0b92c4c79fbb`。真机批量删除滚动性能待复测，未声称黑屏问题已完成真机验收。

用户在真机批量标记数十张、返回列表确认删除后卡死，继续滚动可能黑屏。源码发现：删除通知触发全图集刷新；概览扫描、排序与索引写盘占用主线程；行重排会批量请求原图元数据。未取得该手机的卡死/内存终止日志，因此不把黑屏归因写成已证实。

本轮修改：删除期间合并刷新并暂停追加分页，防重复提交；过期刷新结果丢弃；概览分批让出主线程且旧任务可取消；排序和索引写盘移到后台，写盘串行保持新旧顺序；列表期间不生成首页封面；移除列表单元格出现时的原图元数据预读，保留点击读取。原生280ms删除、对比模式、版本0.5.8/58不变。

需真机复测：同一图集标记30–50张，确认删除后连续上下滚动；系统取消后照片仍在；删除后继续分页；回首页封面更新。未用用户照片做删除测试。

## Detail pagination fix - 2026-09-18

Latest IPA: `iOS/IPA/Punctum-0.5.8-pagination-fix-unsigned.ipa`. SHA256: `e53c5526ab35a61ded34805c0f06c395f22eb685342d3e54ab97e9cd11d71404`. Release and ZIP/version checks passed. Logs: `/tmp/punctum-pagination-final-tests.log`, `/tmp/punctum-pagination-release.log`. Physical-device continuous browsing remains to be verified.

The old trigger compared an index into visiblePhotos with photos.count, which still included pending deletions. With 80 loaded and 4 removed, the maximum visible index was 75 while the trigger required 76. Checking only index changes also missed entry at the final loaded photo and deletions that kept the same index.

DetailPagination now keys the loading task on selection, visible/loaded counts, exhaustion and gesture completion. It checks on entry and after removals/appends, uses the visible boundary, cancels superseded checks, and retains the existing model loading guard. Empty visible batches refill until exhaustion; deletion state is cleared if the old pager has been removed. Published exhaustion allows an empty final batch to close correctly. Existing ID-based selection preservation remains.

Six strategy tests cover 300 photos across four batches, direct entry at the last photo, 4/20/50/79 pending deletions, unchanged-index deletion, empty batches and gesture completion. All 41 XCTest cases passed; this is model validation, not a claim of 300 physical swipe gestures on an iPhone. Includes comparison mode and the preceding batch-delete performance fix; version stays 0.5.8/58. Android unchanged; no commit or upload.
