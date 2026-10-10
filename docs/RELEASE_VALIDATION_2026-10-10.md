# 双端近期改动验收与 GitHub 发布 · 2026-10-10

结论：用户明确将近期两端全部改动视为验收通过，并要求将说明和源码同步 GitHub。Android 保持 **0.5.9/59**，iOS 保持 **0.5.10/60**，本次不再升版本。

## 发布范围

### Android

- 大图最大 5 倍缩放；松手保留倍率和位置、放大后单指移动、双击复原；放大时隔离翻页/删除/Live Photo，切图、后台、返回清理。
- 图片和文字整体进入，保留 350ms 进入节奏；返回文字淡出与裁切。
- 图集列表移除风格词条，将排序放到右下。
- 新用户首页 Select Exhibition 缩小字号，单行完整显示。

### iOS

- 排序“拍摄 → 编辑 → 同步”，同步保持系统用户图集自定义位置。
- 首次排序完整三模式说明显示 4 秒，离开图集/进入大图关闭。
- 新用户首页 Select Exhibition 单行完整显示。
- 删除/重排后点击跟随当前照片标识；两列配对、缩略图复用、异步元数据与独立大图会话保持一致。
- 三种模式回前台异步刷新当前图集，删除动效后台中断后恢复手势；观止确认完整退出后再请求系统删除，待删批次在后台保留；串行提交、当前资产解析与提交后核对。

iOS 的排序/风格两行及原进出动效保留，平台差异没有自动统一。

## 用户验收与开发验证

用户于 2026-10-10 明确要求将近期双端改动全部视为验收通过。此前各文档的待验收状态由本次确认更新。实际测试和设备操作仍按记录描述：

- Android：34 项 Release 单测通过；Release、Lint（0 错误、17 条既有警告）与 APK 签名通过；保留缩放包保留数据覆盖安装 OPPO PMA110，设备拉回包与本地哈希相同；横竖图保持/移动/复原、后台与返回已验证。
- iOS：66 项 XCTest 全通过，0 失败、0 跳过；iPhone arm64 Release、ZIP 完整性、应用标识与包内版本检查通过。PhotoKit 图集重排/移除成员及前台刷新、原生确认回调与手势中断回归已覆盖。本轮没有连接 iPhone或执行原图库连续两轮系统删除确认；用户统一验收不改变这项开发测试范围。
- 本次发布只更新文档、提交及上传，沿用同日已经通过的应用测试和构建结果，没有改产品代码或重复运行测试。

详情：[Android 保留缩放](ANDROID_RETAINED_ZOOM_VALIDATION_2026-10-10.md)、[Android 较早跟进](ANDROID_LOCAL_FOLLOWUP_VALIDATION_2026-10-06.md)、[iOS 同步](IOS_SYNC_SORT_VALIDATION_2026-10-06.md)、[iOS 照片身份](IOS_DELETE_IDENTITY_VALIDATION_2026-10-06.md)、[iOS 后台与删除](IOS_FOREGROUND_DELETE_VALIDATION_2026-10-10.md)。

## 本地交付包

| 平台 | 当前包 | SHA256 |
|---|---|---|
| Android | `APK/Punctum-0.5.9-retained-zoom.apk` | `668d95dae00985b33cb62c3673392a3a5230ba706283495bf774fd55fdb5aad0` |
| iOS | `iOS/IPA/Punctum-0.5.10-foreground-delete-fix-unsigned.ipa` | `eee2d1d08c9092fecb46cabf388fbffc42432227490f7eea500ffc1e5d0cac9b` |

iOS 包未签名，沿用 AltStore。以上两个包本次重新核对本地哈希，无重新打包或安装操作。包、签名资料、机器配置、私有照片和原始设备资料不上传 GitHub。

## GitHub 同步

目标仓库：[yyqlovelife/Punctum_YYQ](https://github.com/yyqlovelife/Punctum_YYQ)，目标分支 `main`。发布前本地 HEAD 与远端 main 同为 `aaaa86be14187401312e98e33e80634cc24a2da8`。发布完整近期源码、资源、测试、版本/构建配置与公开说明；保留全部原有本地改动，没有使用 L3 Workflow 或上传 Google Drive。

首个源码发布提交为 [`f3e2d75`](https://github.com/yyqlovelife/Punctum_YYQ/commit/f3e2d750b221550273a4b26d8c1b5dced0d33b41)，远端树 `03d6992c991b686213cfec7a77f77451302079a0`。普通 Git 推送因传输错误失败后，通过 GitHub Git Data API 完成无强制更新；重新查询 `main` 与完整递归树，全部 **185 个文件**的路径、类型、模式和 blob 与本地发布树逐项一致，48 个近期改动文件完整纳入。

随后补充这份已完成的核验记录，并修正 Android 日志中的一处旧相对链接。本地接续同内容的远端源码提交以保持历史可接续，保留全部原本改动；源码、版本配置与同日已通过的测试/安装包均未改变。最终文档补充提交再次核对 GitHub main 与完整文件树；结果在本次会话报告。
