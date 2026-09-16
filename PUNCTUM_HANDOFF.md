# Punctum 开发交接

更新：2026-09-16。当前文档随源码同步至 GitHub；它描述本次 0.5.8 上传快照。历史试验与构建记录保留在 [design-qa.md](design-qa.md)，分平台变化见 [Android](changelog/android.md) / [iOS](changelog/ios.md)。

## 当前结论

- Android、iOS 均保持 **0.5.8 / 58**，本次上传不再升号。
- Android 照片双指放大、放大后双指平移和松手回弹已获用户真机确认。
- iOS 原生快照删除已获用户确认“确实流畅了很多”；由 400ms 调至 **280ms** 后，用户确认“可以了这个状态”。当前保留 280ms。
- 用户已明确授权本次 GitHub 上传，范围包含近期源码、资源引用、项目配置、测试和文档。后续没有明确要求时，不擅自提交或上传。
- 所有 Punctum 工作直接在真实仓库进行，永远不启用 L3 Workflow。保留既有功能、回退开关和用户改动。
- “按文件修改时间排序”切换已回退，不在当前版本中。

## 接续与同步范围

GitHub：<https://github.com/yyqlovelife/Punctum_YYQ>，接续开发使用远端 `main`。

本轮以 Android 0.5.7 基线为起点，核对全部未提交源码、新增测试和 9 月 15–16 日验证记录，收录 Android 照片手势、iOS 能力对齐及其后反馈修复。上传前本地 `3b714ba` 与远端 `b1d0ba5` 文件树相同，SHA 差异来自此前 API 同步；不能将它们误判为产品功能分叉。当前提交身份以包含本文件的 Git 历史和远端分支为准，不在文件内硬写自身 SHA。

原机器真实仓库为 `/Users/80400763/Documents/Punctum`；聊天目录只存交接副本，不能作为源码仓库。在其他电脑直接克隆完整仓库，签名和本机环境单独配置。

## Android 当前状态

- `app/build.gradle.kts`：`versionName=0.5.8`、`versionCode=58`。
- `ui/DetailScreen.kt`：照片区域 1–3 倍双指缩放、二维平移，参数黑区不动；松手位置和大小一起 spring 复原。缩放与平移同时进行时保持指下内容连续，回弹可被下一次捏合打断。
- `PHOTO_PINCH_ENABLED = true` 为回退开关；只在正常当前大图页启用，删除卡片和移动过渡不启用。双指期间暂停分页、详情滚动、实况及单指删除。
- 原横图居中、竖图贴顶、边缘瞬切、实况播放、上滑删除参数保持既有实现；不要随 iOS 调整顺手修改 Android。
- 0.5.7 已包含首页样式过渡、约 180ms 大图 fade-through、排序 placement spring（0.82 / 700）；既有 LEGACY / SUBTLE / EMPHASIZED 开关保留。
- 首页进图集黑屏修复有构建/安装记录，用户未单独明确确认的体验不补写为验收通过。

Android 源码入口相对于 `app/src/main/java/com/punctum/gallery/`。

## iOS 当前状态

项目入口 `iOS/Punctum/`，版本在 `project.yml` 和生成的 Xcode 工程中均为 0.5.8 / 58。源码入口相对于 `iOS/Punctum/Punctum/`。

### 删除与大图手势

- `Views/NativeDeletionPager.swift`：UIKit 单指上滑只移动当前分页的一张不可交互快照；原分页保留层级、暂时隐藏渲染，避免逐帧重建照片、EXIF 或分页树。
- 参照 Android：140pt 行程，0.72 门槛；超程阻力 0.14，最大进度 1.12；上移 132–150pt，缩放至 0.92–0.91。松手以 **280ms** 收缩到垃圾桶，位移 quadratic、形变 smoothstep；取消 300ms 回弹，减少动态效果使用 160ms 淡出。
- 完成回调后更新照片，再布局、恢复分页。失活时取消未提交拖动；已确认松手的删除只完成一次。释放被暂停的滚动手势。
- `Views/DetailScreen.swift`：`nativeDeletionEnabled = true`。改为 false 可对照保留的 SwiftUI 路径；旧路径 400ms 不代表当前默认时长。
- 删除只加入本次待删除集合，退出大图统一确认；取消保留图集列表，系统删除成功后才更新列表。不要为调动画改变删除语义。
- 照片双指缩放 1–3 倍、双指平移与约 0.3s 复原；黑色参数区不动。与翻页、删除、Live Photo 互斥，结束/失活时清理状态。

### 浏览与 EXIF

- 每张照片对应固定 `DetailPage` 和稳定 tag；禁止恢复导致翻页回弹的动态占位分页方案。
- 竖图顶边为零，不加安全区留白，不加负顶部偏移；超长竖图等比适配，横图位置保留。
- `PhotoImageLoader.swift`：合并高清请求，ImageIO 串行后台解码，取消及过期回调按请求身份隔离。
- `MetadataService.swift`：优先通过原始文件 URL 读取属性，无 URL 时回退 Data；相邻参数按张发布，预读任务按照片 ID 触发，连续删除不依赖页码变化。云端原图仍可能等待。
- `CaptureDateIndex.swift` / `PhotoLibraryService.swift`：本地 EXIF 时间缓存，数据代复用，一次图集浏览固定排序快照；进入图集不等待整库索引或高清图，先展示首批 80 张。缩略图按屏幕请求 360–900px。

### 首页、面板和生命周期

- 原生 `SortGalleriesSheet` 支持长按、跨页自动滚动和顺序持久化；面板优先滚动内容，列表使用剩余高度。旧箭头组件保留。
- 已添加图集锁定；排序 Sheet 关闭后再打开添加 Sheet。三类面板统一暖深灰、圆角和描边。
- 首页三态过渡、顶部标签、28 条名言与 Android 对齐。卡片窗口坐标移动超过 10pt 后取消点击；滚动/减速中不触发进入图集。
- 仅照片权限变化时在前台恢复补刷新，其余照片变化由 PhotoKit 监听；失活时清理按钮按压、实况长按和缩放，恢复暂停的翻页识别。

## 验证与验收边界

- Android 0.5.8 Release/Lint 已通过，之前已覆盖安装到 OPPO PMX110 并核验版本启动；本次同步另做 Release/Lint 检查，不重复改动 Android 交互。
- iOS 原生删除版本最近一轮 **21 项测试、0 失败**；最终 280ms 仅改时长，Release 和安装包完整性通过，用户随后真机确认该节奏。
- 自动测试覆盖模型、EXIF 日期、解码、布局、点击移动判定、删除轨迹；不能代替帧率、多指或所有组合手势验证。
- 模拟器曾启动失败，恢复后本轮自动 drag 诊断仅收到点击（无 touchesMoved，dy=0）。因此本轮模拟器记录不证明删除拖动或取消动作通过；原生删除体验的确认来自用户真机反馈。
- 邀请卡、后台恢复、长列表、大库 EXIF 以及组合手势仍按各自证据保留复测边界，用户本次认可删除节奏不等于全部场景验收。

## 构建与安装包

Android（仓库根目录）：

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleRelease :app:lintRelease
```

产物 `app/build/outputs/apk/release/app-release.apk`，本地验收留档 `APK/Punctum-0.5.8-release.apk`。包名 `com.punctum.gallery`。换手机时先查连接设备，优先覆盖安装，核验版本和启动；不默认卸载或清数据。

iOS：

```bash
xcodegen generate --spec iOS/Punctum/project.yml
iOS/Punctum/scripts/build-unsigned-ipa.sh
```

详见 [iOS README](iOS/Punctum/README.md)。最低 iOS 17，基础 Bundle ID `com.chessyyq.punctum`。固定 `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`，同一个 DerivedData 不同时运行多个构建。

- 当前验收留档：`iOS/IPA/Punctum-0.5.8-native-delete-280ms-unsigned.ipa`。
- SHA256：`d0f3d143d550f7bef6a92fe18b26fe04029a8880b6752f53092f31c3e58d12e8`。
- 通用脚本生成 `iOS/IPA/Punctum-0.5.8-unsigned.ipa`；重新构建同一源码时内容以当前源码为准，打包时间不同可导致哈希不同。
- 用户使用 AltStore / AltServer 自行签名侧载，沿用原账号和应用身份覆盖安装。未签名包不代表已直接装到真机。
- APK、IPA、签名密钥、证书、profile、密码、local.properties 和构建缓存不上传 GitHub。

## 后续维护

动效实施用 `animate`，完成后用 `review-animations` 复核。更新文档覆盖上次交接以来全部差异；当前默认方案写入版本节，过期方案明确标为历史。上传必须核对远端分支和文件树，不能只看本地 commit 或 push 文本。
