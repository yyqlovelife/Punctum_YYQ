# GitHub 同步核验 · 2026-11-08

## 范围与版本

用户已确认首页名言彩蛋效果，要求同步 GitHub 并保持当前版本。Android versionName **0.7.1**、versionCode **91** 均不变，iOS 维持 0.5.10/60。

此次包括：看片台熄灯时禁止翻页与点选；所有模式首页单击名言，只让文字下沉、回弹后随机换成不同的一句；两处首页入口及文档更新。此前已验收视觉、取图、投影手势和旧首页回退保留。

同步目标：`yyqlovelife/Punctum_YYQ` 的 `main`，已核对基线提交 `4b5f9b7fd42080bcbe310a1fabb4651dd1926d4a`。源码提交及完整远端树核验待发布后回填。

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

待发布后填入实际提交、完整树、文件数和标签保留核验结果；不以本地提交或 push 输出代替远端读回。
