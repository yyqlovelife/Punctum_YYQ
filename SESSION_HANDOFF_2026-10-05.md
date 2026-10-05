# 观止新对话接续 · 2026-10-05

源码目录 `/Users/80400763/Documents/Punctum`；`Documents/ChatGPT/Punctum-OPPO` 是辅助交接目录。直接在真实仓库继续工作，保留未提交变更。GitHub 为 `yyqlovelife/Punctum_YYQ` 的 `main`。

## 近期维护同步 · 2026-10-05（版本不变）

Android 保持 **0.5.8/58**，iOS 保持 **0.5.9/59**。本次同步近期修复及说明，不升版本。

- Android 中文地名：请求并保留简体中文地名；照片位置权限后来获准时，旧空 GPS 缓存可重新读取。文件本身缺少 GPS 时仍无法补出坐标，中文地名服务覆盖率继续观察。
- Android 大图版式：已撤回去除 `No.xx` 的试验，保留用户接受的原编号、字号与时间地点位置。
- Android 拍摄时间：所有来源统一优先文件原始 EXIF 时间，大图显示、列表倒序、首页封面及时间跨度一致；旧缓存核验后展示。Phocus 500 张、DJI Album 255 张在 PMA110 上逐张通过，修复后时间不匹配与列表逆序均为 0；23 项单测、Release、Lint、签名通过。
- 当前 APK `APK/Punctum-0.5.8-original-capture-time.apk` 已保留数据覆盖安装 PMA110，手机与本地 SHA256 同为 `4682d62e3c5da7978cc2c4f0af7b061a68b382367cb377e6c6cd35c8325dc715`，已上传 Google Drive。
- iOS 延续 9 月 25 日状态：43 项 XCTest/Release 通过；首页冷启动滑动已获用户确认，最新封面、进入与返回定位仍待 iPhone 真机复测。本轮未改 iOS 代码。

详见 [新对话交接](SESSION_HANDOFF_2026-10-05.md)、[开发交接](PUNCTUM_HANDOFF.md)及 [Android 真机摘要](docs/ANDROID_CAPTURE_TIME_VALIDATION_2026-10-05.md)。源码、测试和文档同步 GitHub；安装包、签名资料、原始照片元数据与设备抓取留在本地/Drive。

## 继续工作的边界

- Android 拍摄时间以文件 DateTimeOriginal、DateTimeDigitized 优先；系统相册修改日期与文件冲突时优先原始拍摄时间。10 月 2 日的“接近导入时间”条件修复已被 10 月 5 日统一规则取代。
- Photo 保存 exifTakenMillis，GalleryStore 持久化；元数据缓存为 v4。进入图集前校正已有缓存时间，尚未读原始时间的旧缓存先完成核验。保留超过 500ms 才显示、可取消的加载状态。
- 大图仍有 No.xx，保留当前原布局。中文地名/GPS 缓存改动继续保留。
- Android 系统快速预览及异常比例修复保留，SINGLE_PASS_LIST 继续关闭；返回列表仍遵守已接受的可见范围/居中规则。
- 本轮正式包已安装；临时同签名核验工具已卸载。私人原始数据在忽略目录 `docs/verification/2026-10-05/capture-time/`，公开核验摘要不含照片和完整 URI。
- iOS 最新本地 IPA 仍为 `iOS/IPA/Punctum-0.5.9-return-center-unsigned.ipa`，不要把 Android 本轮测试写成 iOS 验收结果。

## 安装包

[Google Drive 当前 Android APK](https://drive.google.com/file/d/1ynjBKkkOsgJli0pb8MXZZlxS3LzGlXDu/view?usp=drivesdk)。历史 capture-time 条件版和去编号试验版均不再推荐。

## 构建

使用 Android Studio 自带 JBR；检查 `:app:testDebugUnitTest`、`:app:assembleRelease`、`:app:lintRelease`，并核对 APK 签名和设备安装哈希。版本号按用户指示保持不变。
