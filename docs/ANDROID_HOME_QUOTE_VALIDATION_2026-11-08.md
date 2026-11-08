# 首页名言单击彩蛋验证 · 2026-11-08

## 最终行为

明信片、票根、胶片三种模式首页共用名言文字。单击时只让文字本身轻微缩小并下沉，按压回弹完成后，从原有 28 句中随机选取与当前句不同的一句。切换模式保留当前句。

使用现有 `punctumPressable`，文字缩放至 0.97、下沉 2dp，沿用按下与回弹节奏。效果直接作用于 `Text`，没有背景、边框、阴影、Ripple 或父布局变换。滑走取消按压时不换句。无障碍保留按钮点击动作；系统关闭动画时仍能换句。

入口为 `SwitcherScreen` 共用名言；`MainActivity` 主首页与切换覆层均接入 `GalleryViewModel.shuffleHomeSubtitle()`。原顶部标识行双击回顶代码保持。

## 构建与模拟器验证

- Android 0.7.1/91，版本号不变；Release、78 项 Release 单元测试、Lint（0 错误、17 条既有警告）及 APK v2 签名通过。
- 无实体手机连接。使用 API 36 arm64 模拟器，冷启动环境后安装最终正式 APK；测试素材仅为模拟器的 3 个 `PunctumQuoteQA` 图集、6 张已知测试图，未写入 APK。
- 明信片实际切换 4 次、票根 3 次、胶片 3 次，10 次均排除当前句；每次模式切换后的名言保持一致。
- 按住时名言保持原句，松开回弹后换句；按住后滑出取消，原句保持。
- 按压前后画面对照：应用区域中，名言包围区域之外的变动像素为 **0**；变动边界仅覆盖文字笔画，未出现热区底框。状态栏和系统导航区域不计入比较。
- 将系统动画时长设为 0 后再次点击，名言仍正常更新；完成后恢复原系统动画设置。
- 模拟器中最终 APK 独立拉回，SHA256 与本地正式包一致。

模拟器、截图和像素检查只能证明上述环境中的行为，不作为实体手机手感与性能结论。原始证据位于已忽略的 `docs/verification/2026-11-08/home-quote/`。

## 交付核验

- 包名：`com.punctum.gallery`；versionName `0.7.1`，versionCode `91`。
- 本地：`APK/Punctum-0.7.1-91-home-quote-easter-egg.apk`，40,227,232 字节。
- SHA256：`648754efb1f5737d657cf00979873bac364192d4b3aa26b569577b8962cbac3f`。
- [Google Drive 正式 APK](https://drive.google.com/file/d/1KzDPCjohrFJFWV9BkAQbmE8WWZ0A6O4o/view?usp=drivesdk)，文件 ID `1KzDPCjohrFJFWV9BkAQbmE8WWZ0A6O4o`。
- 上传完成后独立读取云端元数据，并通过认证原文件下载读回；大小、SHA256 与本地及模拟器包均一致。

包内同时保留 11 月 6 日熄灯锁定看片台修改。用户已确认本轮效果并要求以同版本同步 GitHub；结果见 [GitHub 核验](GITHUB_SYNC_VALIDATION_2026-11-08.md)。未改 iOS，历史云端文件保留；旧版本标签与 Release 不移动。
