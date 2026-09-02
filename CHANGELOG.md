# Punctum 更新说明

Android 与 iOS 分开记，按版本倒序。写法见 [`changelog/README.md`](changelog/README.md)。

| 平台 | 当前版本号 | 工作区实际状态 | 完整日志 |
|---|---|---|---|
| Android | `0.5.6`（versionCode `56`） | 正式版，已完成 OPPO PMX110 真机验收 | [`changelog/android.md`](changelog/android.md) |
| iOS | `0.5.5`（build `55`） | 正式版；另有待下次发版收录的弹窗视觉更新 | [`changelog/ios.md`](changelog/ios.md) |

---

## 当前交接状态 · 2026-09-02

给接手同事的入口。Android 已在 2026 年 9 月 2 日升至正式 `0.5.6`，收录大图页退出后的统一删除确认、高清缩略图缓存，以及三类弹窗的视觉与动效优化，并通过 OPPO PMX110 真机验收；iOS 当前为正式 `0.5.5`，本轮已同步三类弹窗的视觉层级，等待下次正式发版收录。

### Git 状态

`main` 的正式基线包含 Android `0.5.6`、iOS `0.5.5`，以及 iOS 待下次发版收录的弹窗视觉更新。安装包作为本地构建产物，不纳入 Git；换电脑后从源码重新构建即可。

### 安装包（本地构建输出，不纳入 Git）

| 平台 | 路径 | 说明 |
|---|---|---|
| Android | `APK/Punctum-0.5.6-release.apk` | 本地执行正式构建后生成；签名文件和密码需在新电脑安全迁移 |
| iOS | `iOS/IPA/Punctum-0.5.5-unsigned.ipa` | 本地执行未签名 IPA 脚本后生成；交给 AltStore / AltServer 签名后安装真机 |

### 怎么编

- **Android**：Android Studio 或 `./gradlew assembleRelease`。包名 `com.punctum.gallery`。
- **iOS**：在 `iOS/Punctum` 必要时先 `xcodegen generate`。模拟器 Debug 可用本地 ad-hoc 签名；真机未签名包跑 `scripts/build-unsigned-ipa.sh`。脚本里写死 `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`，不要随便改全局 `xcode-select`。
- iOS 工程 bundle id：`com.chessyyq.punctum`，最低 iOS 17。启动参数（模拟器联调）：`-punctumStyle postcard|ticket|reversal_film`、`-openFirstGallery`、`-openFirstDetail`、`-openAlbumPicker`。

### 两端各停在哪

**Android 0.5.6 正式版：** 包含 0.5.5 的列表稳定性与新版大图上滑动效，并正式收录以下改进：

1. 大图页上滑仅在当前会话中标记待删除照片；退出大图页时统一显示「本次删除 N 项」，确认后才进入系统回收站，取消则完整保留。
2. 返回列表时，在确认前维持原列表稳定；确认后被删除照片统一移除，后续照片直接补位。批量删除、系统权限确认和失败恢复均走同一链路。
3. 列表缩略图使用约 900px 长边的高清缓存，Camera 等图集最近拍摄照片的清晰度得到改善；缓存分版后不会继续复用旧低清结果。
4. 首页图集排序、添加图集和大图页移动图集统一使用暖深灰卡片、淡描边、阴影与页面遮罩，弹窗和背后页面层级更清楚。
5. 三类 Android 弹窗统一改为页面内浮层，修复从屏幕右下角出现的感受，以及排序到图集选择时双层重叠、前后闪和尺寸二次变化。
6. 图集列表提前加载；首页弹窗切换保持同一遮罩亮度，大图页移动图集的遮罩以 180ms 平滑加深，卡片以 180ms 从 `0.98` 缩放到原大小。
7. Release 构建已通过，并在 OPPO PMX110 完成覆盖安装与本轮交互验收。

**iOS 0.5.5 正式版：** 包含与 Android 对齐的三态首页、实况、图集多选、列表原比例和移动到图集，并正式收录以下真机优化：

1. 首页中文标题用 Noto SemiBold 的 UILabel，禁止描边、禁止 Button 合成加粗；胶片中文光学居中。
2. 大图集列表先取 80 张再分页；缩略图长边 360、带缓存；进列表不再对每张图查 `PHAssetResource`。真机滑动已确认顺畅。
3. 列表长按：UIKit 小圈画在格子右上角；滑动不再误出圈；只有单击进大图，长按松手不进大图。
4. 实况角标贴原图右下角（26pt + 6pt 边距，热区 44×44）；点角标只开播；左右 25% 切图。长按会真正开播并出声（opportunistic 加载 + 播放音轨）。
5. 大图上滑删除：真实页面跟手上滑并顺滑飞出；背后冻结替代照片，删除后透明重建分页，避免旧图回闪和下一张横向滑入。
6. 黑底参数区与横图顶部留白支持切换沉浸态；实况长按、角标点击和页面热区恢复稳定。
7. 首页图集、列表返回、首页右上角与大图页顶部按钮统一为按下下沉、松手回弹、回弹后执行；大图页左上角使用返回箭头。
8. 画廊可见照片提前读取参数，大图页预备当前照片前后各 4 张；从列表进入、左右切图和删除后承接下一张均无参数加载跳变。
9. 大图复用画廊缩略图缓存，并移除入口淡入重影；图片和参数首帧同步出现。
10. 修复真机实况照片只有震动、没有画面和声音的问题；长按和实况角标播放已在 iPhone 14 Pro 验收通过。

**iOS 未发版视觉更新（2026-09-02）：** 首页调整图集、首页添加/选择图集和大图页移动到图集已统一为暖深灰弹窗底、28pt 连续圆角、淡米白描边、柔和阴影和额外页面遮罩；保留原生 Sheet 的拉起、拖动、滚动与关闭体验，并已在 iPhone 14 Pro、iOS 18.2 模拟器验证。

### 双端不要弄反的约定

- **纸纹**在 Android `app/src/main/res/drawable/`（票据、胶片、明信片票尾），iOS 工程直接打进包，不要各复制一份。
- **实况**：Android 是 Motion Photo（JPEG 尾部 MP4）；iOS 是静图 + 配对 MOV，走 `PHLivePhoto`。不要把安卓解析套到 iOS。
- **上滑删除**：安卓是 `progress > 0` 时藏 `HorizontalPager`，只变换当前页；iOS 必须用静图，不要再对整个 TabView 做 scale。
- **中文标题不要描边。** 用户明确禁止。
- **不要把徕卡 / LFI 商业字体拷进仓库。** 英文用 Newsreader。
- 内网环境：不要上传文件到公网，不要做内网穿透。

### 关键代码入口

| 场景 | Android | iOS |
|---|---|---|
| 首页三态卡 | `ui/SwitcherScreen.kt` | `Views/SwitcherScreen.swift` |
| 图集列表 | `ui/GalleryScreen.kt` | `Views/GalleryScreen.swift` |
| 大图 / 实况 / 上滑删 | `ui/DetailScreen.kt` | `Views/DetailScreen.swift`、`LivePhotoViews.swift` |
| 图集选择 | `ui/AlbumPickerDialog.kt` | `Views/AlbumPickerView.swift` |
| 相册数据 | `data/PhotoRepository.kt`、`MotionPhotoService.kt` | `Services/PhotoLibraryService.swift`、`PhotoImageLoader.swift` |
| 版本号 | `app/build.gradle.kts` | `iOS/Punctum/project.yml`（改完 `xcodegen generate`） |
