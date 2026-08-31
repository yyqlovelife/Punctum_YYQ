# Punctum 更新说明

Android 与 iOS 分开记，按版本倒序。写法见 [`changelog/README.md`](changelog/README.md)。

| 平台 | 当前版本号 | 工作区实际状态 | 完整日志 |
|---|---|---|---|
| Android | `0.5.4`（versionCode `54`） | 正式版，已完成 OPPO PMX110 真机验收 | [`changelog/android.md`](changelog/android.md) |
| iOS | `0.5.5`（build `55`） | 正式版，已完成 iPhone 14 Pro 真机验收 | [`changelog/ios.md`](changelog/ios.md) |

---

## 当前交接状态 · 2026-08-31

给接手同事的入口。Android 已在 2026 年 8 月 31 日升至正式 `0.5.4`，首页图集卡和主要按钮按压反馈已通过 OPPO PMX110 真机验收；iOS 同日升至正式 `0.5.5`，收录大图页真机修复、全局按压反馈，以及图片和参数信息的无感衔接优化。

### Git 状态

`main` 的正式基线包含 Android `0.5.4` 与 iOS `0.5.5` 的双端成果。安装包作为本地构建产物，不纳入 Git；换电脑后从源码重新构建即可。

### 安装包（本地构建输出，不纳入 Git）

| 平台 | 路径 | 说明 |
|---|---|---|
| Android | `APK/Punctum-0.5.4-release.apk` | 本地执行正式构建后生成；签名文件和密码需在新电脑安全迁移 |
| iOS | `iOS/IPA/Punctum-0.5.5-unsigned.ipa` | 本地执行未签名 IPA 脚本后生成；交给 AltStore / AltServer 签名后安装真机 |

### 怎么编

- **Android**：Android Studio 或 `./gradlew assembleRelease`。包名 `com.punctum.gallery`。
- **iOS**：在 `iOS/Punctum` 必要时先 `xcodegen generate`。模拟器 Debug 可用本地 ad-hoc 签名；真机未签名包跑 `scripts/build-unsigned-ipa.sh`。脚本里写死 `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`，不要随便改全局 `xcode-select`。
- iOS 工程 bundle id：`com.chessyyq.punctum`，最低 iOS 17。启动参数（模拟器联调）：`-punctumStyle postcard|ticket|reversal_film`、`-openFirstGallery`、`-openFirstDetail`、`-openAlbumPicker`。

### 两端各停在哪

**Android 0.5.4 正式版：** 包含 0.5.3 的字体与排版成果，并正式收录以下真机优化：

1. 首页三种图集卡在按下时顺滑缩小下沉，松手完整回弹后进入；滚动和移出会取消点击并平滑复位。
2. 首页右上角、画廊返回、大图返回及顶部工具按钮统一使用同款按压反馈；大图页左上角使用返回箭头。
3. 中文随机标题新增「观止，关心每一幅照片被重新看见的时刻」和「每一次回望，都重新感受影像的重量」。
4. 首页中文标题保持 Noto SemiBold、禁止描边；英文继续使用 Newsreader，票据与胶片排版规则保持 0.5.3 定稿。
5. 已在 OPPO PMX110 真机完成覆盖安装和交互验收，原有应用数据可保留。

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
