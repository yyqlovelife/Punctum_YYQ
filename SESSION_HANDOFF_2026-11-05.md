# Punctum 接续 · 2026-11-05 · 0.7.1 原图高清发布

## 当前结论

用户已验收 **0.7.0/90 原图高清与快速切图体验**，要求上传 GitHub，并增加 0.0.1 版本。本轮已发布 **Android 0.7.1/91**，仅升号、不改验收效果。iOS 保持 0.5.10/60。

/91 正式构建、78 项单元测试、Lint（0 错误、17 条既有警告）及签名已通过。`APK/Punctum-0.7.1-91.apk` 与 `APK/Punctum-0.7.1-release.apk` 同为 40,227,232 字节，SHA256 `3bf363a530f543ac15387e7dd48ac6594172ff5a510438b588b3fdccc6a8d974`；签名证书 SHA256 `413b0241791e433edb2257e84bc7304b4669b48002fdfc1ec24eee1a08657daf`。对比已验收 /90，仅 AndroidManifest.xml 改动，DEX、资源和素材全部一致。GitHub 源码提交 `2941510`、`android-v0.7.1` 标签和 Release 已发布并核对，256 个文件完整树一致。签名 APK 已在本地生成；附件上传遇到本机网络写入错误（Permission denied / EACCES），当前 Release 无 APK 附件。本轮未安装 /91、未更新 Google Drive。见 [本次发布核验](docs/RELEASE_VALIDATION_2026-11-05.md)。

[0.7.1 Release](https://github.com/yyqlovelife/Punctum_YYQ/releases/tag/android-v0.7.1) · [源码提交](https://github.com/yyqlovelife/Punctum_YYQ/commit/294151056027aecdaa1e3a1b42f6f8c59ef8a9af)。

## 已验收 /90 的最终状态

- 诺贝尔手机已保留数据安装最终包，设备 APK 独立读回与本地一致。用户明确确认体验。
- 22 张临时测试媒体及临时画廊已清理，其他 MediaStore 项全部保留，原测试图集仍为 15 张。手机停在原图集第 1 张投影，临时日志标签已恢复。**无需再次清理，禁止沿用早先待清理步骤。**
- 本地包为 `APK/Punctum-0.7.0-90-original-detail.apk`，40,227,228 字节；SHA256 为 `f1b4c9664e619efb7e8efbeaf54e6c5e12ba428c2938d04c4aab9fe6108d9f89`。
- [Google Drive /90 历史交付](https://drive.google.com/file/d/184c7vu9ZlQlRyfNrYFIBf_jT-bzh-2GC/view?usp=drivesdk)已下载读回，SHA256 与本地及设备包一致。本次只继续 GitHub 发布。
- Release、Lint（0 错误、17 条既有警告）、78 项单元测试与签名核验通过。早先 USB 断开和待安装/待清理状态均已结束。

## 本轮代码范围

1. 普通大图与胶片投影共用原图管线，按真实显示像素解码底图，移除固定 1800 像素限制；放大、超大或超长图由原图可见分块补足细节。
2. 当前图优先、左右各 5 张预取、限定并发和取消过期请求。列表缩略图加载保持原路径，列表停稳后准备大图，快滑时暂停。
3. 处理 EXIF 方向、透明图分块叠色、JPEG/PNG/HEIC 与 Motion Photo 静态 JPEG 边界。
4. 幕布纹理光影缓存复用，减少重复绘制；预模糊光影实验保持未接入。保留 1–5 倍缩放、边缘快切、跟手分页与照片独立上滑，胶片不显示上滑教学提示语。

实测预取达到 `ready=11/11 left=5/5 right=5/5`。最终胶片拖动短样本 P95 为 13ms、快切 40ms；冷底图解码样本为 98ms。性能数字只代表对应设备短样本，不能承诺任意原图零等待或所有场景零掉帧。详细证据见 [原图验证](docs/ANDROID_ORIGINAL_DETAIL_VALIDATION_2026-11-05.md)。

## 保留的验收约束

- 四图 120 首页、旧首页回退、Averia Serif Libre/思源宋体、原颜色与纸纹保留；诺贝尔固定三卷布局不按屏高均分。
- 空状态文案为 **观止，岂止于观**，无句号。
- 手动开灯、可辨识的暗片、曝光节奏、照片原比例与边缘保持；投影缩放范围不得越过整个投影区。
- 旧封面闪回、高清替换闪黑及删除接替颜色修复保留。
- 用户原媒体不删除；原始截图、日志、APK、签名和本机配置不进入源码树。

## 发布接续

真实仓库为 `/Users/80400763/Documents/Punctum`。保留现有工作，不启动 L3，不改 iOS，不移动指向 /77 的 `android-v0.7.0` 标签。0.7.1 使用新标签及 GitHub Release；APK 上传失败，正式包保留本机。

本轮 /91 验证与远端发布结果集中更新 [发布核验](docs/RELEASE_VALIDATION_2026-11-05.md)，并核验 README 的 Release 与 APK 下载入口。无需再次执行临时媒体清理，也无需安装 /91 或更新 Google Drive。

历史过程：[11 月 4 日缩放](SESSION_HANDOFF_2026-11-04.md)、[11 月 3 日手势与性能](SESSION_HANDOFF_2026-11-03.md)、[总交接](PUNCTUM_HANDOFF.md)。
