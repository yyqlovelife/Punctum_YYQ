# 观止最新交接 · 图集列表视觉评估 · 2026-10-08

## 双端最新验收与发布 · 2026-10-10

用户已明确将近期 Android 与 iOS 改动全部视为验收通过。版本保持 **Android 0.5.9/59、iOS 0.5.10/60**；本次授权将全部近期源码、资源、测试、构建配置和说明同步到 GitHub `main`。最新功能、安装包和验证范围见 [本次发布汇总](docs/RELEASE_VALIDATION_2026-10-10.md)。下方旧“待验收”“未提交／未上传”、空 IPA 目录及旧测试数量均为各自日期的历史记录；用户验收状态以本节为准。未执行的测试或真机操作保留原证据边界。


## 后续实现 · 2026-10-10

Android 已新增松手保留缩放、单指移动与双击复原，最新包已保留数据安装 PMA110；版本仍为 0.5.9/59。当前状态先读 [10 月 10 日交接](SESSION_HANDOFF_2026-10-10.md)，本文件继续作为列表视觉基线。原有改动全部保留，iOS 未改；下方旧安装包和松手回弹描述为当时状态。

## 结论与本轮范围

真实源码位于 `/Users/80400763/Documents/Punctum`。Android 当前 **0.5.9 / 59**，iOS 当前 **0.5.10 / 60**。10 月 6 日功能跟进仍为本地未提交改动；10 月 8 日只核对状态并更新交接与日志，没有改产品源码、版本、打包或上传。

下一项任务：请新 AI 评估**进入某个图集后的照片列表页**有哪些视觉优化机会，先说明观察与方案，等用户选择后再实施。评估可分别看双端，不能把首页邀请卡、系统图集选择器、大图页当成本轮目标。当前双端存在有意保留的差异，尚未授权统一。

复制给新 AI 的完整任务见 [视觉评估 prompt](docs/GALLERY_VISUAL_REVIEW_PROMPT_2026-10-08.md)。本文件取代旧交接中的“当前状态”；旧材料按日期保留历史。

## 工作约束

- 全部已有本地改动必须保留。辅助目录 `/Users/80400763/Documents/ChatGPT/Punctum-OPPO` 只作历史接续，不能在那里实现产品。
- 不使用 L3 Workflow。此次只评估，暂不改 UI、排序、分页、删除、动效或版本；未经新要求不上传 GitHub、Google Drive，不打包或安装。
- 先简洁说结论，再用通俗语言解释。遵守用户关于中文表达的限制。用户原文文案、标点不能擅自重写。
- 用户照片、相册标识、原录屏、设备日志及 APK/IPA 留本机；不要将私有证据加入 Git 或传到外部服务。需要示意图时使用匿名或合成内容；用户要求作图时调用 GPT Image 的能力。
- 若后续另获授权制作 demo，先交付 Codex 内可点击的本地网页，手机画幅居中；用户确认后才沿用相同资源和交互打包 APK。

## 最新状态与验收边界

| 平台 / 项目 | 当前生效内容 | 已有证据与待确认 |
|---|---|---|
| Android 0.5.9/59 | 最大 5 倍缩放；图片与文字整体 350ms 进入/返回；返回文字同步淡出及裁切；移除风格词条，排序移到右下数量行 | 用户 10 月 6 日明确说“安卓的我都确认了没问题”。这些跟进已验收，未再次上传 GitHub |
| Android 新用户标题 | 源码把 Select Exhibition 基础字号改为 32，窄屏自适应单行 | Kotlin 编译已通过；这项晚于最新 APK，没有重新打包或安装，不能扩写成设备验收通过 |
| iOS 0.5.10/60 | 拍摄／编辑／同步三种排序，系统图集自定义位置；当前同步图集回前台刷新；首次完整提示 4 秒，离开图集或进大图立即取消；新用户标题单行 | 10 月 6 日最终回归覆盖同步顺序、分页及前台刷新；用户原图库仍待 iPhone 验收 |
| iOS 删除后点击错位 | 点击、行内容、图片加载和大图会话按照片身份刷新；异步读元数据后重新按标识定位 | 10 月 6 日最终 58 项 XCTest 全通过，0 失败、0 跳过，Release/包检查通过。没有在用户原 iPhone 图库复现随机错位，修复后实际验收仍待用户确认 |
| 双端差异 | iOS 仍保留排序/风格两行，未移植安卓最新整体进出动效；Android 没有“同步”排序 | 差异为当前基线，不代表新 AI 已获授权抹平 |

Android 最终返回淡出/裁切阶段为 Release、Lint、真实录屏逐帧核对；排序下移阶段为 Release、截图、设备包一致核对。28 项完整单测在此前整体返回阶段执行。不要把不同阶段拼成“最新包重新跑过所有检查”。10 月 8 日未重跑应用构建、测试或设备交互。

## 图集列表页的当前视觉基线

### 共用气质与照片布局

产品目标是像翻摄影杂志一样回看作品，延续深色、衬线字体、暖白文字与克制金色信息。颜色：背景 `#0A0A0A`，暖白 `#EDE8DD`，次级文字 `#8A8170`，金色 `#C8A24B`，分隔线 `#2A2A28`；其他表面色 `#141414`、对话框 `#181715`。

英文使用 Newsreader，中文使用 Noto Serif SC。iOS 字体辅助函数 `georgia` 实际委托 Newsreader，不能仅凭函数名字认定界面用了 Georgia。

照片每行两张，以双方宽高比按比例分配列宽；参与行排版的比例限定在 0.45–2.4。当前照片行与列没有间隙，也没有左右外边距；最后仅一张时留出另一张对应的空位。原始比例、白边作品与预览/高清几何已有修复背景，提出裁切、留白或栅格建议时必须说明对这些内容的影响。

### 双端头部对照

| 项目 | Android | iOS |
|---|---|---|
| 顶部导航 | 左侧返回箭头 + Your Punctums，右侧改名铅笔 | 同类布局，导航与铅笔触区最小 44pt |
| 标题 | 44sp；中文 Noto Serif SC，英文 Newsreader | 44pt；中文 Noto Serif SC，英文 Newsreader |
| 第二层信息 | 左侧时间跨度 | 左侧时间跨度；右侧“排序 · 拍摄／编辑／同步” |
| 第三层信息 | 左侧“关于 N 幅作品的故事”；右侧“排序 · 拍摄／编辑”，基线对齐 | 左侧“关于 N 幅作品的故事”；右侧“风格 · 原幅” |
| 边距与间隔 | 头部左 24dp / 右 12dp；信息行另补右 12dp；上 18dp / 下 16dp；标题上隔 14dp、日期上隔 14dp、两信息行隔 6dp；分隔线上隔 16dp | 相应数值以 pt 使用；头部左 24pt / 右 12pt，信息行另补右 12pt；上 18pt / 下 16pt；间隔同类 |
| 信息字号 | 导航 12sp、日期/数量 15sp、排序 12sp | 导航 12pt、日期/数量 15pt、排序/风格 12pt |

表中为源码基准值；sp/dp 与 pt 不能按截图像素直接等同比较。长标题、多位数量、无日期、窄屏、不同照片比例的实际表现需要单独看。当前 iOS 测试截图没有日期，不代表真实图库取消了日期行。

可检查的方向包括标题和照片的主次、头部占屏比例、信息可读性、边距/对齐、排序入口可发现性、两张不同原幅照片的观看节奏、长名称与空白末行。这里只提供检查范围，不预先下优化结论。

## 新 AI 的源码阅读入口

所有下列路径相对真实仓库根目录；先读取工作区版本，再看历史差异。

| 用途 | 路径 |
|---|---|
| Android 列表与主题 | `app/src/main/java/com/punctum/gallery/ui/GalleryScreen.kt`；`app/src/main/java/com/punctum/gallery/ui/theme/Theme.kt` |
| Android 图片与导航保护 | `app/src/main/java/com/punctum/gallery/ui/SharedPhotoMotion.kt`、`app/src/main/java/com/punctum/gallery/ui/DetailScreen.kt`、`app/src/main/java/com/punctum/gallery/MainActivity.kt` |
| iOS 列表与主题 | `iOS/Punctum/Punctum/Views/GalleryScreen.swift`；`iOS/Punctum/Punctum/Theme.swift` |
| iOS 进入/返回与照片身份 | `iOS/Punctum/Punctum/Views/RootView.swift`；`iOS/Punctum/Punctum/ViewModels/GalleryViewModel.swift`、`iOS/Punctum/Punctum/Models/GalleryModels.swift`、`iOS/Punctum/Punctum/Services/PhotoImageLoader.swift` |
| iOS 同步排序与提示 | `iOS/Punctum/Punctum/Services/PhotoLibraryService.swift`、`iOS/Punctum/Punctum/Services/GalleryStore.swift`；`iOS/Punctum/Punctum/Views/Components.swift` |
| iOS 身份/排序回归 | `iOS/Punctum/PunctumTests/GalleryPhotoIdentityTests.swift`；`iOS/Punctum/PunctumTests/PhotoSortOrderTests.swift` |

必须保留的关联：iOS `GalleryPhotoRow` 外层以首张照片标识作返回滚动锚点，内层以两张照片组合标识刷新内容；二者用途不同。点击传 `PhotoItem`/照片标识，异步元数据完成后用当前图集快照再次定位，拒绝过期/已删除/跨图集请求；图片加载器清理旧图并检查请求代次；`RootView` 为每次大图访问创建独立 `detailSession`。不能为了布局简化恢复位置索引点击或旧行复用方式。

Android 使用按行位置的外层 key 和按照片 URI 的内层 key，不能直接移植 iOS 行身份方案。滚动时加载控制、预览到高清、缓存、原白边保护均需保留。

双端切换排序会回到顶部并短暂淡入；每个图集独立记忆。iOS 同步读取图集原有顺序，回到前台必须刷新**当前已打开**的同步图集；不能改成必须退出重进。大图返回：最后照片仍在进入前可见范围就保持列表位置，否则居中目标行。保留分页、长按删除和已验收原生删除动效；iOS 原生快照删除为 280ms。

iOS 首次提示必须完整显示在同一条 toast，4 秒，导航时立即取消，文案原样：

```text
可在以下三种模式之间切换图片顺序

拍摄：按真实拍摄时间排序
编辑：按编辑过的保存时间排序
同步：按系统相册自定义的位置排序
```

## 可用视觉证据与局限

以下均为本机已核对存在的绝对路径；`docs/verification` 是忽略目录，换机器或只有 GitHub 的 AI 无法自动获得它们。

| 证据 | 路径 | 可以据此判断什么 |
|---|---|---|
| Android 最终列表截图 | `/Users/80400763/Documents/Punctum/docs/verification/2026-10-06/sort-only-header/screen.png` | PMA110 真机、真实照片、去风格和排序右下的现状；10 月 8 日再次读取截图。含私人照片，仅本地查看 |
| iOS 最终身份回归列表 | `/Users/80400763/Documents/Punctum/docs/verification/2026-10-06/ios-delete-identity/attachments/BE6A3D1E-B74B-47BA-9DAA-58019A7789CA.png` | 真实 SwiftUI 页面、删除后的两列配对与头部布局；照片是测试色块，不能据此判断真实照片观看效果 |
| iOS 三模式提示 | `/Users/80400763/Documents/Punctum/docs/verification/2026-10-06/ios-sync-order/attachments/8625DF46-FB5E-41E2-9673-B99E75C86E5A.png` | 测试图库的完整提示；不能替代照片列表美感验证 |
| iOS 新用户单行标题 | `/Users/80400763/Documents/Punctum/docs/verification/2026-10-06/onboarding-title/attachments/19FEF8BA-7357-42D1-A02E-53BB80382468.png` | 标题单行历史验证；首页不在本轮列表审查范围 |

如缺少合适的 iOS 真实照片列表、长标题、窄屏或混合原幅证据，先明确盲区；可用只读方式获得当前截图或模拟器合成图库进行观察，不能把色块截图、文字描述或旧包当作当前 iPhone 视觉验收。动态与点击问题需要实际交互，静态截图不能证明这些行为正常。

10 月 6 日 iOS 原始结果：`docs/verification/2026-10-06/ios-delete-identity/tests-regression.log`（58 项通过）；`build-final.log`、`package-check.json`（历史 Release/IPA 检查）。这些文件 10 月 8 日核对存在，但未重新执行。

## 包、仓库与未提交改动

- 当前 Android 本地包仍存在：`APK/Punctum-0.5.9-sort-only-header.apk`，38,950,996 字节；10 月 8 日复核 SHA256 为 `1681fcb0418b713b18c8ae90ade90f627d672a90bf53298af6f9e20fb690a058`。这是先前已安装验收的包，不含后续新用户标题字号跟进。
- **当前 `iOS/IPA` 目录没有 IPA 文件**。10 月 6 日交付过 `Punctum-0.5.10-delete-identity-fix-unsigned.ipa`，历史 SHA256 `4a461f17a07988ff31e6fa3b9d0b94fb9936d6d11d75ae4abdd22d1e9e06ea5e`，28,572,947 字节；历史交付方式为未签名 IPA、AltStore 签名安装。旧材料里的“最新包”不能当作今天可下载的文件，本次没有重新打包。
- 仓库：`https://github.com/yyqlovelife/Punctum_YYQ.git`。10 月 8 日本地 HEAD 与 GitHub main 均为 `aaaa86be14187401312e98e33e80634cc24a2da8`，是 10 月 5 日基线；后续功能不在该远端提交里。新 AI 只看 GitHub 会漏掉最新变化。
- 本轮文档编辑前，工作区 38 个修改/未跟踪文件，其中 18 个源码/配置/测试、20 个 Markdown。新 handoff 与 prompt 会增加文档项；不要用该数量代替实时工作区检查。
- 审核快照保存在忽略目录 `docs/verification/2026-10-08/handoff-audit/`，包括文档编辑前 status、源码差异与源码哈希。最终比对 122 个源码、配置、测试与资源文件，哈希全部不变；新交接相对链接和所列本机路径存在，Git 差异格式检查通过。结果见该目录 `documentation-check.json`。

18 个已有源码/配置/测试改动（相对根目录）：

```text
app/src/main/java/com/punctum/gallery/MainActivity.kt
app/src/main/java/com/punctum/gallery/ui/DetailScreen.kt
app/src/main/java/com/punctum/gallery/ui/EmptyScreen.kt
app/src/main/java/com/punctum/gallery/ui/GalleryScreen.kt
app/src/main/java/com/punctum/gallery/ui/SharedPhotoMotion.kt
iOS/Punctum/project.yml
iOS/Punctum/Punctum.xcodeproj/project.pbxproj
iOS/Punctum/Punctum/Models/GalleryModels.swift
iOS/Punctum/Punctum/Services/GalleryStore.swift
iOS/Punctum/Punctum/Services/PhotoImageLoader.swift
iOS/Punctum/Punctum/Services/PhotoLibraryService.swift
iOS/Punctum/Punctum/ViewModels/GalleryViewModel.swift
iOS/Punctum/Punctum/Views/Components.swift
iOS/Punctum/Punctum/Views/EmptyScreen.swift
iOS/Punctum/Punctum/Views/GalleryScreen.swift
iOS/Punctum/Punctum/Views/RootView.swift
iOS/Punctum/PunctumTests/PhotoSortOrderTests.swift
iOS/Punctum/PunctumTests/GalleryPhotoIdentityTests.swift
```

最后一项原本未跟踪；正常 Git diff 不包含其文件内容，需直接阅读。

## 建议新 AI 的交付形式

先用一句话判断当前列表值得优化的重点；随后给 3–5 条有截图/源码依据的观察，每条说明影响、建议、优先级与可能风险。分别标明 Android、iOS 和双端共用项，区分事实与审美建议。给出一套克制微调方向；仅在确有收益时另列一套更明显调整方向。说明哪些现状值得保留，哪些缺少证据。最后列出用户需选择的具体项，等待选择再改代码。

不得把本交接列出的检查方向当成已确认缺陷，也不得把之前的删图 bug、平台差异或未完成真机验收当成此次视觉改版授权。后续若改布局，应验证长标题/窄屏/混合原幅、删除后再次点击、同步前台刷新、滚动返回锚点，按实际改动选择有意义的检查。

## 接续文档

- [Android 日志](changelog/android.md) / [iOS 日志](changelog/ios.md) / [根日志](CHANGELOG.md)
- [开发交接入口](PUNCTUM_HANDOFF.md) / [Design QA](design-qa.md)
- [Android 阶段验证](docs/ANDROID_LOCAL_FOLLOWUP_VALIDATION_2026-10-06.md)
- [iOS 同步排序](docs/IOS_SYNC_SORT_VALIDATION_2026-10-06.md) / [iOS 升号打包](docs/IOS_RELEASE_VALIDATION_2026-10-06.md) / [iOS 删除身份修复](docs/IOS_DELETE_IDENTITY_VALIDATION_2026-10-06.md)
- [10 月 6 日完整历史交接](SESSION_HANDOFF_2026-10-06.md) / [复制给新 AI 的 prompt](docs/GALLERY_VISUAL_REVIEW_PROMPT_2026-10-08.md)
