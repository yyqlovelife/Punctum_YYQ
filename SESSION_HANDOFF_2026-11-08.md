# Punctum 接续 · 2026-11-08 · 首页名言彩蛋

已完成正式 Android App 的首页名言彩蛋并上传谷歌云：三个模式首页单击影像名言，只让文字下沉、回弹后再随机换句；每次排除当前句，模式切换保持当前句。复用现有按压动效，无底框、波纹或父布局移动。

Android 保持 **0.7.1/91**；本次 APK 同时包含此前熄灯锁定看片台修改。无实体手机连接，API 36 arm64 模拟器完成三模式 10 次换句、按住/松开/取消、关闭动画及模式切换验证。文字区域外像素差为 0。78 项单测、Release、Lint 和签名通过。

正式包 `APK/Punctum-0.7.1-91-home-quote-easter-egg.apk`，40,227,232 字节；SHA256 `648754efb1f5737d657cf00979873bac364192d4b3aa26b569577b8962cbac3f`。模拟器 APK、云端认证下载读回与本地哈希均一致。

[Google Drive 下载](https://drive.google.com/file/d/1KzDPCjohrFJFWV9BkAQbmE8WWZ0A6O4o/view?usp=drivesdk)。详细实现与验证见 [名言彩蛋验证](docs/ANDROID_HOME_QUOTE_VALIDATION_2026-11-08.md)。

用户已确认本轮效果并要求同步 GitHub，版本及构建号继续保持 0.7.1/91。此次一并同步 11 月 6 日熄灯锁定修改、名言彩蛋及相关记录；iOS 未改。实际交互验证范围仍为模拟器，未新增实体手机验收结论。GitHub `main` 已同步源码提交 [`577c210`](https://github.com/yyqlovelife/Punctum_YYQ/commit/577c210737dd422e3a097cf10fe48c9920c0da72)，260 个文件完整树逐项一致；文档核验补录后再次核对最终 `main`。结果见 [本次 GitHub 核验](docs/GITHUB_SYNC_VALIDATION_2026-11-08.md)。

旧 `android-v0.7.1` 标签和 Release 保留 11 月 5 日发布快照，不移动；最新 APK 使用上方已核对的 Google Drive 链接。历史发布见 [11 月 5 日](SESSION_HANDOFF_2026-11-05.md)，实体设备安装记录见 [11 月 6 日](SESSION_HANDOFF_2026-11-06.md)。
