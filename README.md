<p align="center">
  <img src="iOS/Punctum/Punctum/Assets.xcassets/AppIcon.appiconset/Punctum-AppIcon-1024.png" width="160" alt="Punctum 图标">
</p>

<h1 align="center">Punctum · 观止</h1>

<p align="center"><strong>为回看而生的影像画廊</strong></p>

> **Punctum 是一款致力于为影像爱好者提供充足情绪价值的软件。你可以像翻阅一本大师摄影杂志一样，浏览和回看自己的作品，并通过自然的手势快速取舍、整理照片，让每一次回望都重新感受到影像的重量。**

我们希望把相册从普通的图片容器，变成一座属于每个人自己的影像画廊。照片保留原本的比例、拍摄时间、地点和影像参数，以更接近摄影画册与展览的方式被重新观看；首页、画廊和大图页也围绕“浏览、感受、取舍”这条路径展开。

Punctum 直接映射设备里的系统图集，照片仍由系统相册管理。你可以把不同主题的照片分别组织成一座座画廊，在回看中完成筛选、移动与删除。


## 当前版本 · 2026-10-22

| 平台 | 版本 / 构建号 | 当前内容 |
|---|---|---|
| Android | **0.6.0 / 61** | 反转胶片模式新增选片台与投影一体界面，用户已确认最终 1.2 秒投影节奏 |
| iOS | **0.5.10 / 60** | 保持 10 月 10 日版本，本轮未移植 Android 选片台 |

Android 从上次 GitHub 发布的 0.5.9/59 正式升级至 0.6.0/61。开发试用包使用过 0.6.0/60；构建号 61 支持保留数据覆盖安装。

## 反转胶片的新观看方式

进入图集后，上方是有织纹、褶皱和晕影的深色投影幕布，下方是延伸至系统导航区域的白色发光选片台。看大图与选片在同一页完成。

- **先选片，再投影**：默认空幕布，中央轻显“选择一张胶片，重映一段时光”；点击胶片以 **1.2 秒**开片、亮起和轻展开。左右横滑或点屏幕边缘可直接快切。
- **六张一页的选片台**：3 列 × 2 行，横向整页切换；保留最新在前的拍摄/编辑排序。原比例照片装在带 PUNCTUM 和编号的白色胶片框中，留有间隙和轻微摆放角度。
- **完整呈现画幅**：投影区高度固定，图像按比例适配；横图与 EXIF 共用左右 24dp 边距，竖图拥有独立高度预算。
- **按需查看影像信息**：底部「i」切换 EXIF，开启后跨换片保持；参数在图像下方空隙中居中，需要让位时图像中心最多上移 18dp。
- **统一整理删除**：投影区上滑或选片台按住胶片加入待删除；按住时右上角显示进度，中途松手取消。退出图集回首页时统一确认，描述为“被删除的项目将会进入系统相册回收站”。
- **简洁图集片签**：居中名称与精简年月/数量，左返回、右更多；排序切换后菜单保持展开，重命名仍打开原改名框。

反转胶片首页保持现有封面；四张堆叠与黑色片基仅做过设计探索，用户决定暂不修改。明信片和票据模式沿用原有列表/大图体验；本轮新选片台暂未提供旧大图的缩放和 Motion Photo 播放入口。

## 其他核心体验

- 直接映射系统图集，保留作品原比例、真实拍摄时间、地点和影像参数。
- 首页支持明信片、票据、反转胶片三种邀请卡；支持多选添加图集、重命名与顺序调整。
- 常规大图支持 1–5 倍保留缩放、单指移动、双击复原与快速取舍。
- Android Motion Photo / iOS Live Photo 在各自原有大图页播放；iOS 支持拍摄、编辑、同步三种排序及后台恢复。

## 验证与交付

Android 本次 Release、Lint（0 错误、17 条既有警告）、**44 项单元测试**、签名检查通过。正式包为本地 `APK/Punctum-0.6.0-release.apk`；安装及 GitHub 核验见 [0.6.0 发布记录](docs/RELEASE_VALIDATION_2026-10-22.md)。

用户确认与开发验证分别记录。既有设备上的空幕布、原比例适配、快切、分页、参数常驻、排序菜单、按住删除及退出统一确认/取消均有记录；本轮未实际删除用户照片。历史帧统计只代表对应设备和版本，不能用于承诺所有图库零卡顿。

GitHub 保存源码、资源、测试、版本配置与文档。APK/IPA、签名资料、本机配置、用户照片和原始设备截图日志不纳入仓库。本次不更新 NAS。

## 本地开发

### Android

使用 Android Studio 打开仓库根目录。包名 `com.punctum.gallery`，最低 Android 8.0（API 26），目标 API 35。使用 JDK 17：

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease :app:lintRelease :app:testDebugUnitTest
```

Release 需要本机 `punctum-release.jks`，以及 `local.properties` 中的 `PUNCTUM_STORE_PASSWORD`、`PUNCTUM_KEY_PASSWORD`；签名资料和配置通过安全方式单独迁移。

### iOS

工程位于 `iOS/Punctum`，最低 iOS 17，Bundle Identifier `com.chessyyq.punctum`。必要时运行 `xcodegen generate`；未签名 IPA 构建脚本为 `iOS/Punctum/scripts/build-unsigned-ipa.sh`。签名证书与描述文件单独迁移。本轮未重建或安装 iOS。

## 文档与源码入口

- [当前更新索引](CHANGELOG.md) · [Android 日志](changelog/android.md) · [iOS 日志](changelog/ios.md)
- [本轮交接](SESSION_HANDOFF_2026-10-22.md) · [总交接](PUNCTUM_HANDOFF.md)
- [正式发布验证](docs/RELEASE_VALIDATION_2026-10-22.md) · [选片台迭代验证](docs/ANDROID_FILM_REVIEW_VALIDATION_2026-10-22.md)
- [设计与交互验证](design-qa.md) · [日志维护规则](changelog/README.md)
- [产品需求](PRD/PRD-Punctum观止.md)

Android 入口：`MainActivity.kt` 按风格路由，`FilmReviewScreen.kt` 实现幕布和选片台，`FilmReviewLayout.kt` 管理比例/参数避让/手势判断，`GalleryViewModel.kt` 协调待删除和退出。

历史版本、包与验收边界保留在带日期的日志和交接中，以本页及 2026-10-22 交接为当前入口。
