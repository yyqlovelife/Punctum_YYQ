# Android 0.7.1/91 发布核验 · 2026-11-05

## 发布范围与当前状态

用户已明确验收 0.7.0/90 的原图高清与快速切图体验，并要求增加 0.0.1、同步 GitHub。本轮 Android 升为 **0.7.1/91**，保持已验收实现；iOS 继续 **0.5.10/60**，未重新构建。

**本地构建与签名已通过，GitHub 待发布核验。** 不得将 /90 的安装与测试直接写成 /91 装机结论。

| 项目 | 当前结果 |
|---|---|
| Release 构建 | 0.7.1/91 通过 |
| Lint | 通过，0 错误、17 条既有警告 |
| 单元测试 | 78 项通过，0 失败 |
| APK 版本读包 | versionName=0.7.1、versionCode=91 |
| 正式包 | `APK/Punctum-0.7.1-91.apk`；同内容别名 `APK/Punctum-0.7.1-release.apk` |
| 字节数 / SHA256 | 40,227,232 字节；`3bf363a530f543ac15387e7dd48ac6594172ff5a510438b588b3fdccc6a8d974` |
| 签名核验 | 通过；证书 SHA256 `413b0241791e433edb2257e84bc7304b4669b48002fdfc1ec24eee1a08657daf` |
| 源码提交 / 文档提交 | 待补实际 commit 链接 |
| GitHub main 远端核对 | 待补 |
| 新标签 `android-v0.7.1` | 待补实际目标及远端核对 |
| GitHub Release / 下载入口 | [Release](https://github.com/yyqlovelife/Punctum_YYQ/releases/tag/android-v0.7.1) · [APK](https://github.com/yyqlovelife/Punctum_YYQ/releases/download/android-v0.7.1/Punctum-0.7.1-91.apk)，待发布核验 |
| Release APK 读回 | 待补附件大小、SHA256 与本地一致性 |
| 手机安装 | 本轮未安排 /91 安装；/90 已安装且用户验收 |
| Google Drive | 本轮不更新；继续保留 /90 历史交付 |

与已验收 /90 逐项 ZIP 比较，仅 `AndroidManifest.xml` 变化；全部 DEX、资源与素材一致，确认本次升号保持验收实现。

## 已验收 /90 的依据

- 本地 APK：`APK/Punctum-0.7.0-90-original-detail.apk`，40,227,228 字节。
- SHA256：`f1b4c9664e619efb7e8efbeaf54e6c5e12ba428c2938d04c4aab9fe6108d9f89`。
- 诺贝尔保留数据安装，独立读回设备 APK 哈希一致；用户已验收。
- Google Drive [历史 /90 下载](https://drive.google.com/file/d/184c7vu9ZlQlRyfNrYFIBf_jT-bzh-2GC/view?usp=drivesdk)读回哈希一致。
- Release、Lint（0 错误、17 条既有警告）、78 项单元测试与签名通过。
- 22 张临时媒体和临时画廊已清理，其他媒体保留；原测试图集仍 15 张，日志标签已恢复，无需再次清理。

原图、格式、缓存与性能证据见 [原图验证](ANDROID_ORIGINAL_DETAIL_VALIDATION_2026-11-05.md)。胶片拖动短样本 P95 32→13ms，边缘快切 77→40ms；仍有短帧抖动。左右各 5 张缓存达到 `11/11`，这些测量不代表任意设备或任意原图零耗时。

## 源码与资料边界

本次提交包括共享原图管线、原图分块、预取调度、胶片投影缩放及手势/颜色交接、幕布缓存、测试和说明。预模糊光影试验保持未接入最终 UI。首页纸纹、字体、固定布局与原灯光效果保留。

GitHub `main` 同步源码和文档；签名 APK 仅上传 Release 附件。APK/IPA、用户媒体、原始设备截图日志、密钥、本机配置不进 git 树；公开文档不记录个人设备完整标识或个人图集名。

原 `android-v0.7.0` 标签继续保留 /77；0.7.1 使用独立新标签，不移动旧标签。此次不更新 Google Drive，不安排 /91 手机安装。

## 最终发布回填

发布完成后将本节替换为实际源码/文档提交、main 树核对、新标签、Release 与附件读回结果，并同步 README、总交接和本日交接中的“待补”状态。
