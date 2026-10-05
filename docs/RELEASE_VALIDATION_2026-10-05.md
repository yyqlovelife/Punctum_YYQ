# 2026-10-05 发布验证

## 发布范围

Android **0.5.9 / 59**，进出页面动效、每图集拍摄／编辑排序及新用户开屏文案。用户确认 350ms 图片文字整体展开稳定。iOS 已完成的排序与开屏文案同步源码，仍为 **0.5.9 / 59**；本轮进出动效未移植 iOS。

## Android

- 最终源码执行 `testReleaseUnitTest assembleRelease lintRelease`：6 组、28 项测试，失败/错误/跳过均为 0；Release 通过；Lint 0 错误、17 条既有警告。
- `apksigner verify` 通过，v2 签名有效；APK 包名 `com.punctum.gallery`，包内版本 0.5.9/59。
- 包：`APK/Punctum-0.5.9-release.apk`；SHA256 `d1b682982631e2b8ca55c6c92c29097be90cf9698402292d6f8d836fdbc761fd`。签名方式与原版本相同。
- 升号前同代码包 `Punctum-0.5.8-unified-photo-entry-350ms.apk` 已保留数据安装 PMA110，并独立核对设备 APK 与本地哈希；录屏检查整体进入、页内滑动及返回。用户随后确认状态稳定。正式升号包构建后设备已断开，覆盖安装未完成。
- 历次动效检查覆盖首页三个模式、横竖图进出、快速进出、正常翻页、可见范围内返回不动/范围外居中与系统关闭动画；本轮仅升版本，不重复认定此前撤回方案有效。原始录屏留在忽略目录。
- 排序核验 Phocus 502 张、DJI Album 255 张，见 [排序验证](ANDROID_PHOTO_SORT_VALIDATION_2026-10-05.md)。最终为词条式切换、首次 2 秒提示及基线对齐。大图日期保持拍摄时间；系统/导出工具是否更新修改时间决定编辑排序的时间来源。未实际删除/移动用户照片。

## iOS 既有检查与本轮复核

排序实现既有 47 项 XCTest 通过，排序 Release 与开屏 Release 构建日志为成功。本轮复核最新未签名 IPA 的 ZIP 完整性，包 `iOS/IPA/Punctum-0.5.9-onboarding-copy-unsigned.ipa`，SHA256 `10fd2656a9f699019ca9b714e95ddac524c673918bd91d32c32adb9666c6f084`。本轮没有新增 iOS 测试执行，也无 iPhone 真机；排版、首次提示、真实图集分页，以及此前封面恢复/进入/返回定位仍保留真机验收边界。见 [iOS 排序验证](IOS_PHOTO_SORT_VALIDATION_2026-10-05.md)。

## GitHub 接续

上传源码、资源、测试、版本配置、说明与交接；APK/IPA、密钥、本机配置及私有录屏/抓取排除。发布前远端 `main` 基线 `b1eb4b79228f77dd1b2e417e11f4c497245b5257`。上传后核对远端提交、完整文件树、README 与版本配置；该检查完成才报告上传成功。
