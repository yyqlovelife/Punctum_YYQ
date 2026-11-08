# GitHub 同步核验 · 2026-11-08

## 范围与版本

用户已确认首页名言彩蛋效果，要求同步 GitHub 并保持当前版本。Android versionName **0.7.1**、versionCode **91** 均不变，iOS 维持 0.5.10/60。

此次包括：看片台熄灯时禁止翻页与点选；所有模式首页单击名言，只让文字下沉、回弹后随机换成不同的一句；两处首页入口及文档更新。此前已验收视觉、取图、投影手势和旧首页回退保留。

同步目标：`yyqlovelife/Punctum_YYQ` 的 `main`，已核对基线提交 `4b5f9b7fd42080bcbe310a1fabb4651dd1926d4a`。已完成源码提交及远端完整树核对，结果见下文。

## 已完成验证

- 最终代码的 Release、78 项单元测试、Lint（0 错误、17 条既有警告）和 APK v2 签名通过。
- API 36 arm64 模拟器完成三模式 10 次换句、按住/松开/取消、关闭动画与模式切换验证；名言区域外的按压像素差为 0。本轮无新增实体手机验证。
- APK：`APK/Punctum-0.7.1-91-home-quote-easter-egg.apk`，40,227,232 字节。
- SHA256：`648754efb1f5737d657cf00979873bac364192d4b3aa26b569577b8962cbac3f`。
- [Google Drive APK](https://drive.google.com/file/d/1KzDPCjohrFJFWV9BkAQbmE8WWZ0A6O4o/view?usp=drivesdk)已认证下载读回，与本地及模拟器安装包哈希一致。

详细记录见 [名言彩蛋验证](ANDROID_HOME_QUOTE_VALIDATION_2026-11-08.md)和[熄灯锁定交接](../SESSION_HANDOFF_2026-11-06.md)。本次仅发布既有已验证代码和说明，不新增功能改动。

## 发布边界

仅上传源码、版本配置与文档。APK/IPA、签名、本机配置、用户媒体、原始截图和设备日志不进入源码树。原 `android-v0.7.0`、`android-v0.7.1` 标签与 Release 保持原目标；本次不新建版本标签或 Release，最新安装包由上方 Google Drive 链接交付。

## 远端结果

- 源码提交：[`577c210`](https://github.com/yyqlovelife/Punctum_YYQ/commit/577c210737dd422e3a097cf10fe48c9920c0da72)；`main` 已独立读回为完整 SHA `577c210737dd422e3a097cf10fe48c9920c0da72`。
- 源码树：`87197bcb514568f60a2ed76f06f000803215cd87`；260 个文件的路径、模式、类型和 blob SHA 与本地逐项一致，递归响应未截断。
- 保留标签：`android-v0.7.1` 仍指向 `294151056027aecdaa1e3a1b42f6f8c59ef8a9af`，`android-v0.7.0` 仍指向 `21673dadc91fd5f4653a82c9a0be230323f7cc1e`；既有 Release 未修改。
- 文档核验记录由源码提交之后的补录提交保存，最终 `main` 同样核对完整文件树。本次没有改动版本配置或新增二进制附件。
- 发布通过已登录 GitHub 账号的 Git Data API 完成，并独立核对远端 ref、完整树及标签；本地工作树与远端最终提交保持一致。
