# Punctum 更新说明

## 当前版本与发布 · 2026-10-05

| 平台 | 版本 / 构建号 | 当前状态 |
|---|---|---|
| Android | **0.5.9 / 59** | 350ms 图片文字整体展开已获用户稳定确认；新增拍摄／编辑排序 |
| iOS | **0.5.9 / 59** | 排序与开屏文案已同步；本轮真实图库交互待 iPhone 验证 |

Android 从 0.5.8/58 升级为 **0.5.9/59**。本版主要更新进出页面动效与照片排序方式；用户已确认最新 350ms 整体进入效果稳定，可以发布。

- **进出动效**：列表缩略图展开进入大图，图片、间距与文字作为一个整体完成 350ms 展开；返回先用 80ms 隐去信息，再用 280ms 将图片缩回列表对应位置。页内左右切图保持稳定分页。首页三种邀请卡与图集按来源位置展开/收回，均为 280ms。
- **照片排序**：「排序 · 拍摄／编辑」位于「风格 · 原幅」上方，两行分别与左侧时间、照片数量对齐；一点切换、最新在前，每个图集独立记忆。首次点击提示 2 秒：「可切换按「拍摄时间」或「编辑时间」排序」。编辑模式使用系统文件修改时间，缺失时回退拍摄时间；大图日期、首页封面及时间跨度继续使用真实拍摄时间。
- **新用户开屏**：标题为「Select Exhibition」，正文为「选择一个图集，作为你的第一个画廊」「让每一次回望，都重新感受影像的重量」，移除句号。

发布检查：28 项 Release 单测零失败，Release 构建通过；Lint 0 错误、17 条既有警告，APK v2 签名通过，包内版本核对为 0.5.9/59。正式包 `APK/Punctum-0.5.9-release.apk`，SHA256 `d1b682982631e2b8ca55c6c92c29097be90cf9698402292d6f8d836fdbc761fd`。350ms 同代码试用包已在 PMA110 核对设备包一致并获得用户稳定反馈；正式升号包生成时手机已断开，未完成该包的覆盖安装。

本轮一起同步此前完成的 iOS 排序与开屏文案源码及文档，iOS 保持 **0.5.9/59**，进出动效仍仅 Android 实现。既有 iOS 47 项 XCTest、Release 通过；最新本地 IPA `iOS/IPA/Punctum-0.5.9-onboarding-copy-unsigned.ipa` 的 ZIP 完整性本轮复核通过，iPhone 本轮视觉与真实图库交互仍待真机验证。公开检查见 [发布验证](docs/RELEASE_VALIDATION_2026-10-05.md)。

源码、资源、测试、版本配置和本轮文档一并同步 GitHub `main`；安装包、签名凭据与原始设备素材留本机。完整日志见 [Android](changelog/android.md)、[iOS](changelog/ios.md)，接续见 [会话交接](SESSION_HANDOFF_2026-10-05.md)。

## 历史阶段记录

下方版本、安装包、待确认和 Git 状态均对应各自记录时点，当前状态以上方发布章节为准。

## Android 大图整体进入节奏 · 2026-10-05（350ms 试用）

用户认可图片与文字整体展开，反馈 280ms 太快。本轮仅大图进入改为 350ms，并使用 `(0.32, 0.72, 0, 1)` 曲线放缓起步；整体绘制层与最终排版保持。返回图片及首页/图集动效仍为 280ms。上一包 `APK/Punctum-0.5.8-unified-photo-entry.apk` 保留回退。

Release 构建通过，`APK/Punctum-0.5.8-unified-photo-entry-350ms.apk` 已保留数据覆盖安装 PMA110。安装包与设备拉回包 SHA256 均为 `189a61a1701f5a65bc192c0002638447beca39a38229ae634d835cce5a67b21a`。系统相机图集检查整体进入、左右切图与返回，返回列表正常，近期日志未发现崩溃；录屏存于忽略目录 `docs/verification/2026-10-05/unified-photo-entry-350ms`。本次只调整动效参数，未新增测试；版本维持 Android 0.5.8/58。新节奏待用户试用确认，以下为历史方案。

## Android 图片与文字整体展开 · 2026-10-05（试用待用户确认）

按用户新想法，进入大图时图片、原有间距和整块信息由同一个绘制层、同一套 280ms 位移/缩放一起展开，文字随图片一起从小到大；移除上一版进入时的独立文字透明度动画及下沿避让位移。用大图最终照片矩形映射到被点击的缩略图位置，落位后移除变换，最终字体与排版不变。布局准备时保留源缩略图，准备完成后切换到整体内容，避免双图或空白帧。返回仍沿用先隐去文字再缩回图片，页内切图维持正常分页。

最新 `APK/Punctum-0.5.8-unified-photo-entry.apk` 已保留数据覆盖安装 PMA110，设备/本地 SHA256 一致：`8c3b2f9f29519a3433c812b1392073c977a852767043e706dc58894e135790b2`。28 项单测、Release、Lint 通过；有效录屏核对风光信仰 No.13 竖图与 No.14 横图整体展开、正常分页及快速进出，未发现崩溃日志。私有逐帧证据以忽略目录 `docs/verification/2026-10-05/unified-photo-entry` 的 `entry-verified` 和 `landscape-verified` 为准。未删除/移动任何照片，Android 0.5.8/58 与 iOS 不变，实际手感待用户试用。

上一试用包 `APK/Punctum-0.5.8-photo-info-follow.apk` 保留作回退，改前源文件也已保存。接续以本节为准，以下为历史方案。

## Android 图片与信息同步进入 · 2026-10-05（试用待用户确认）

用户反馈上一版“图片结束后再显示文字”有停顿感。进入改为图片放大与 160ms 信息淡入同时开始，整块信息按 `max(0, 当前过渡图片下沿 - 最终图片下沿)` 向下平移；来自列表下半部的图片经过最终信息位置时，文字始终位于图片下方保留原间距。图片落位时文字同时回到原布局，无结束后单独出现的阶段。返回继续沿用上一版先隐去文字再缩回，页内切图逻辑不变。

最新 `APK/Punctum-0.5.8-photo-info-follow.apk` 已安装 PMA110，设备/本地 SHA256 一致：`a3522c56263fe57280927ce4238a5cb353414a66f148703608ef3c3a69545bbc`。28 项单测、Release、Lint 通过，录屏逐帧检查下半部进入时文字跟随间距、上半部进入、正常分页与快速返回，无崩溃日志。原 0.5.8/58 版本号不变，手感仍待用户体验。上一版包 `APK/Punctum-0.5.8-sequential-photo-info.apk` 保留用于回退，本次改前源文件与私有帧在忽略目录 `docs/verification/2026-10-05/photo-info-follow`。接续以本节为准，以下为历史方案。

## Android 图片落位后显示文字 · 2026-10-05（试用待用户确认）

根据最新录屏，取消文字在图片过渡进度 65% 时提前出现。进入先完成 280ms 图片放大，撤下过渡位图并等一帧交接到固定图片，再将整块信息用 120ms 淡入；返回先用 80ms 淡出信息、等一帧，再缩回图片。文字始终参与排版，仅改变透明度，分页逻辑不变。

`APK/Punctum-0.5.8-sequential-photo-info.apk` 已保留数据覆盖安装 PMA110，设备包与本地 SHA256 一致：`012c2bc9868a47ead0d2d1bdbe18c5034fced92021f2a51c415a304805806a6b`。28 项单测、Release、Lint 通过；真机录屏逐帧检查从列表下半部分进入与返回、分页、快速返回，关闭系统动画时文字正常显示，设置已恢复。只调整 Android 的展示时序，0.5.8/58 不变，实际手感由用户试用确认。

回退包保留 `APK/Punctum-0.5.8-stable-photo-motion.apk`，其 SHA256 为 `2ef040729beffadef543c7898e6d7411624cddcfbe3ca09d8b1c2b28899fc051`；本次修改前的 `MainActivity.kt` 与私有 QA 帧保存在忽略目录 `docs/verification/2026-10-05/sequential-photo-info`。接续以本节为准，以下为之前的方案和验证历史。

## Android 大图进入闪动与翻页飞图修复 · 2026-10-05

读取 PMA110 最新 18.7 秒录屏，确认进入大图时列表和底部信息叠着淡入，以及分页改变共享匹配后再次触发缩放。将图片动效改为仅在进入、返回时创建的缓存位图过渡层；页内滑动/点击切图不再参与共享匹配，也关闭图片加载时的微缩放。进入时背景立即遮住列表，图片从原位置放大，信息在落位前最后一段淡入；返回时先布置目标行，再淡出信息并缩回。使用实际窗口高度稳定横图位置，280ms 和原返回定位规则保留。

28 项单测、Release 与 Lint 通过。真机检查横/竖图进入、滑动与点击切图、快速进出、连续点击至第 42 张后的居中返回、系统关闭动画；动画倍率已恢复到原值 1.0。未执行实际照片删除/移动。最新包 `APK/Punctum-0.5.8-stable-photo-motion.apk` 已保留数据覆盖安装，设备与本地 SHA256 一致：`2ef040729beffadef543c7898e6d7411624cddcfbe3ca09d8b1c2b28899fc051`。iOS 和版本号不变。旧动效包为初版历史记录，接续以本节为准。源码尚未推送 GitHub，视觉手感待用户体验确认。

## Android 从来源位置展开与缩回 · 2026-10-05

参考用户手机最新 15 秒系统相册录屏，新增两组连续动效：缩略图从列表当前位置放大进入大图，返回时缩回对应缩略图；首页明信片、票据、反转胶片卡片分别从被点击的位置展开图集，返回时沿原路径收回。页面继续使用现有颜色、布局、加载提示与排序规则，iOS 本轮未改。

图片使用 Compose 共享元素和照片 URI 对应身份，首页使用实际卡片边界驱动图集表面的位移/缩放；时长 280ms，沿用现有强 ease-out 曲线。大图返回先依旧规则判断是否居中并完成目标行布局，再缩回；动画完成才移除大图和展示待删除确认。图库页固定本次访问的状态栏留白，避免恢复状态栏时出现末帧位移。详情预览复用已缓存缩略图；删除/移动临时画面不参与共享匹配。退出时屏蔽重复触摸，系统关闭动画时直接切换。

28 项单测、Release、Lint 通过。PMA110 已保留数据覆盖安装，设备包与本地 SHA256 均为 `ea00ef61a2cb28db1f0f609c7fee6b3b151103c4a50ee9111bfbf0ffb2a69d9b`。真机录屏检查覆盖三种首页卡片进出、横/竖图进出、连续翻到第 15 张后居中返回、取消一张暂存删除后 1420 张系统相机照片完整保留、快速返回以及系统动画关闭；系统设置已恢复，原始用户录屏保留未修改。开发中的一次票据/DJI 翻页与返回采样共 1056 帧、8 个 janky frames（0.76%，legacy 3.79%，无先前基线）；该采样早于最后的退出触摸保护调整，不能作为所有设备帧率承诺。

最新包 `APK/Punctum-0.5.8-shared-motion.apk` 已装在手机，Android 版本仍为 0.5.8/58。未上传 Google Drive，源码尚未推送 GitHub。私有参考录屏与 QA 帧保存在忽略目录 `docs/verification/2026-10-05/shared-motion`，手机临时 QA 输出已清理。实际手感仍请用户体验确认。

## iOS 图集排序与 Android 对齐 · 2026-10-05

新增各图集独立保存的「排序 · 拍摄／编辑」一点切换，放在「风格 · 原幅」上方，使用相同字号、字距与金色。两行分别与时间、照片数量按 firstTextBaseline 对齐。按键沿用点击下沉动效，切换后列表回顶部并短暂淡入；首次有效点击显示「可切换按「拍摄时间」或「编辑时间」排序」2 秒，已读状态跨重启保存。

编辑模式按 PHAsset.modificationDate 最新在前，时间缺失回退已解析拍摄时间，同值比较拍摄时间与稳定 ID。完整图集顺序后台排序并固定给分页与大图浏览；取消删除、移动和刷新沿用当前模式。首页封面独立使用拍摄顺序，大图日期保持原拍摄时间。旧图库配置缺少排序字段时默认拍摄，保留原图集。9 月撤回的排序入口约束由本次明确需求取代。

47 项 XCTest（新增 4 项排序与配置兼容测试）、Debug/Release 构建、IPA ZIP 与版本核验通过。iOS 仍为 0.5.9/59，包为 `iOS/IPA/Punctum-0.5.9-photo-sort-unsigned.ipa`，SHA256 `9c3539c218216921757f9a123ebfb432d435e47cc3da3b9ba013f76e8db0bcb9`。未签名，使用原 AltStore/AltServer 方式签名安装。本轮无 iPhone 真机，Mac 锁屏限制模拟器鼠标操作，排版、首次提示及真实图集分页仍待用户安装体验。未上传 Google Drive，源码尚未推送 GitHub。

## Android 排序入口简化 · 2026-10-05

移除图集右上角排序图标，改在「风格 · 原幅」正上方右对齐显示「排序 · 拍摄／编辑」，颜色与字体完全沿用风格词条，保留点击下沉与一点切换。首次有效点击显示「可切换按「拍摄时间」或「编辑时间」排序」2 秒；提示已读标记保存在应用设置，切换图集及重启后不再提示。仅显示名称改为编辑，底层仍使用文件修改时间，已有图集排序偏好兼容。用户明确要求：以后仅在明确指示上传时上传 Google Drive。本次包保留本地与手机，未上传 Drive。

## Android 图集排序切换 · 2026-10-05（未升版本）

图集右上角新增一点切换的「拍摄／修改」按钮：相机代表拍摄，时钟代表修改，两种状态都有倒序符号和文字；修改态为金色。默认按拍摄时间最新在前，每个图集独立保存选择，长按显示完整说明，沿用点击下沉动效。切换在后台重排已加载照片，列表回到顶部并短暂淡入。

修改时间采用现有 Photo.modifiedMillis（Android MediaStore DATE_MODIFIED，秒转毫秒；文件来源使用 lastModified）。修改时间相同时依次比较拍摄时间、照片 URI；缺失修改时间回退拍摄时间。部分编辑器的非破坏性编辑不会更新原文件修改时间，这类操作无法保证置顶。大图日期始终显示真实拍摄时间，翻页跟随当前列表；取消删除与移动后的重排沿用对应图集模式。首页封面与时间跨度继续按拍摄时间计算。

PMA110 真机：Phocus 502 张、DJI Album 255 张，两种模式全量排序均通过；修改时间与媒体库记录一致，缓存拍摄时间与已有 EXIF 一致。已验证独立记忆、重启保持、长按不切换、修改模式的大图翻页/返回和取消待删除恢复。真实照片未执行删除或移动；未穷举第三方编辑器的修改时间写入行为。28 项单测、Release、Lint 与签名通过。摘要见 [排序验证](docs/ANDROID_PHOTO_SORT_VALIDATION_2026-10-05.md)。

最新包 `APK/Punctum-0.5.8-photo-sort.apk` 已保留数据覆盖安装，手机 APK 与本地 SHA256 均为 `d90db77319c4bc6c675fabd0607d5d064139058ee9963489a57249e9f1dc47b7`；已上传 [Google Drive](https://drive.google.com/file/d/1avFG-Geu-g1GO4ixbn0cj-etlKj4ubLa/view?usp=drivesdk) 并核对大小 38,950,996 字节。Android 仍为 0.5.8/58，iOS 本轮未改。以下原始时间修复记录为前一阶段，本轮源码和文档尚未推送 GitHub。

## 近期维护同步 · 2026-10-05（版本不变）

Android 保持 **0.5.8/58**，iOS 保持 **0.5.9/59**。本次同步近期修复及说明，不升版本。

- Android 中文地名：请求并保留简体中文地名；照片位置权限后来获准时，旧空 GPS 缓存可重新读取。文件本身缺少 GPS 时仍无法补出坐标，中文地名服务覆盖率继续观察。
- Android 大图版式：已撤回去除 `No.xx` 的试验，保留用户接受的原编号、字号与时间地点位置。
- Android 拍摄时间：所有来源统一优先文件原始 EXIF 时间，大图显示、列表倒序、首页封面及时间跨度一致；旧缓存核验后展示。Phocus 500 张、DJI Album 255 张在 PMA110 上逐张通过，修复后时间不匹配与列表逆序均为 0；23 项单测、Release、Lint、签名通过。
- 当前 APK `APK/Punctum-0.5.8-original-capture-time.apk` 已保留数据覆盖安装 PMA110，手机与本地 SHA256 同为 `4682d62e3c5da7978cc2c4f0af7b061a68b382367cb377e6c6cd35c8325dc715`，已上传 Google Drive。
- iOS 延续 9 月 25 日状态：43 项 XCTest/Release 通过；首页冷启动滑动已获用户确认，最新封面、进入与返回定位仍待 iPhone 真机复测。本轮未改 iOS 代码。

详见 [新对话交接](SESSION_HANDOFF_2026-10-05.md)、[开发交接](PUNCTUM_HANDOFF.md)及 [Android 真机摘要](docs/ANDROID_CAPTURE_TIME_VALIDATION_2026-10-05.md)。源码、测试和文档同步 GitHub；安装包、签名资料、原始照片元数据与设备抓取留在本地/Drive。

## 当前交接状态 · 2026-09-25（未升版本）

- **iOS 0.5.9 / 59**：冷启动先显示已保存的首页概览，照片索引与图集刷新移到后台；修复封面黑屏和点击图集短暂停顿；大图返回列表时，仍在进入前可见范围的照片保持原位置，浏览到范围外的照片所在行居中。43 项 XCTest 与 Release 构建通过。冷启动首页可滑动已获用户确认，其余最新交互待 iPhone 真机复测。最新本地 IPA：`iOS/IPA/Punctum-0.5.9-return-center-unsigned.ipa`。
- **Android 0.5.8 / 58**：系统相册批量编辑、调整拍摄时间后的图集排序现在由完整扫描一次更新，当前系统时间覆盖旧缓存。19 项单测、Release、Lint、签名检查通过；用户远程安装后反馈暂时状态不错。最新本地 APK：`APK/Punctum-0.5.8-sort-stability.apk`，另有 Google Drive 副本。
- 详细说明见 [Android](changelog/android.md)、[iOS](changelog/ios.md) 更新记录和 [开发交接](PUNCTUM_HANDOFF.md)。安装包、签名资料与含用户照片的日志不进入 GitHub。

## 上次发布记录 · 2026-09-20

源码与文档已上传GitHub main，首个发布提交 `7d8e74580d9d73f1545e381254923d4bfda93db5`；远端文件树已与本地逐项核对一致。iOS 0.5.9/59重新运行41项XCTest全部通过。此记录取代下方历史“未提交上传”状态。

- **iOS 0.5.9 / 59**：新增大图对比模式，支持系统单选、横竖布局、独立/联动缩放及删除返回；同时包含1–5倍保留缩放、批量删除性能和连续翻页续载修复。
- **Android 0.5.8 / 58**：包含按进入时可见范围判断的返回定位、返回闪动修复、缓存合并写入、按实际尺寸解码、逐张高清发布及异常预览比例修复。首次进入图集支持可取消加载，超过500ms才显示居中小卡片，完成即关闭。
- 安卓最新本地包：`APK/Punctum-0.5.8-delayed-loading.apk`，已覆盖安装PMX110；iOS本地包：`iOS/IPA/Punctum-0.5.9-unsigned.ipa`。
- 当前实现与待验收项见本节及 `SESSION_HANDOFF_2026-09-20.md`。下方旧日期内容为历史记录，旧“最新包”“未提交上传”及版本号均按当时范围理解。
- 发布源码、资源、测试、构建配置与文档到GitHub；安装包、签名凭据和含用户照片/URI的原始录屏与设备日志仅留本机。公开验证摘要见 `docs/RELEASE_VALIDATION_2026-09-20.md`。


## Android 加载提示样式与延迟显示 · 2026-09-20（当前）

最新包 `APK/Punctum-0.5.8-delayed-loading.apk`，SHA256 `46e49e77c218ca5d2f2fc661bafdb29a48ff5dd70c8bdf93b4d9bef489063de8`。16项现有回归、Release/Lint与签名验证通过；已保留数据覆盖安装PMX110（Success）。证据在docs/verification/2026-09-20/delayed-loading/。

- 用户反馈旧AlertDialog文案过大、转圈未居中，快速进入时弹窗闪现。本次改为屏幕中央272dp圆角小卡片，28dp细线加载环、14sp居中文案和13sp取消按钮，保留正常可点击区域。
- 进入图集后连续等待500ms仍未达到内容ready才显示；500ms内完成不弹。图集切换、完成或取消会取消计时并重置状态，避免旧图集延迟弹出。加载完成立即关闭，不强制最短停留时长，不为展示提示额外阻塞进入。
- 取消按钮和系统返回保留；提示未出现的前500ms内系统返回也直接取消加载。点击卡片外不意外取消，返回首页保留图集/授权。
- 保留上一轮可取消加载和最新stable-preview构图修复，SINGLE_PASS_LIST=false。只改Android界面，版本0.5.8/58，未提交上传。样式和快慢图集实际观感待真机反馈。


## Android 首次进入图集：可取消加载 · 2026-09-20（当前）

最新包 `APK/Punctum-0.5.8-cancellable-gallery-loading.apk`；SHA256 `4efcaebb93bf7020ad09beff3b5fe05c8236ae9601ff3fd3bbc045a49994162d`。16项回归、Release/Lint与APK签名通过；已保留数据覆盖安装PMX110（安装Success）。证据归档 `docs/verification/2026-09-20/cancellable-gallery-loading/`。

- 开工前核对最新未提交工作树及交接，基于stable-preview版继续，SHA256 76864ba4c6862510c52db72deefdc3b2e7f7486ac1fe00cf792edf311778e2ec。保留系统快速预览、异常比例校正、逐张高清发布及按进入可见范围判断的返回规则；SINGLE_PASS_LIST仍关闭。
- MainActivity新增独立于隐藏列表层的加载弹窗，提示“项目数量较多，加载中”，提供取消按钮；系统返回/关闭弹窗同样取消。完成数据加载且首行ready后消失，空图集沿用内容ready回调。
- 缓存JSON读取移至IO线程；首次未缓存图集直接完整加载一次，避免先为4张封面全量扫描排序，再完整读取一次。加载期间暂停周期刷新竞争；已有缓存仍快速展示后后台刷新。
- 取消后回首页、清空当前展示，保留已添加图集及持久授权。取消entry Job及对应刷新，用generation隔离过期结果；逐张读取中检查协程取消。正在执行的系统提供商单次读取可能需要返回后才停止，界面无需等待它完成。
- 失败退出加载态并提示重试；不把加载错误伪装成一直等待。实现仅Android，本次不改iOS。版本0.5.8/58；未提交上传。
- 现有16项回归用于保护返回位置、缓存及预览规则；首次超大图集加载、取消后重进与系统返回的真机流程待用户复测，不能用这些单测替代新流程验收。


## Android 高清替换构图跳变修复 · 2026-09-20（当前）

- 聚焦用户反馈“停住补高清时图片动一下”，保留系统快速预览；SINGLE_PASS_LIST继续false，不恢复整屏黑色的单次加载试验。
- 已在StreetPho真机录屏复现：原图5952×3968(3:2)的系统预览返回160×120(4:3)，换高清636×424后，树/栏杆明显改变位置、出现范围和大小。普通关闭加载动画不足以解决这个构图差异。
- PhotoStill携带原图方向修正后的宽高，对系统预览宽高比做2%容差检查（允许整数取整）。正常预览保持快速路径；比例异常时用同一个原图解码器生成长边320px预览，最多2个异常预览解码并行。失败时保留系统图避免黑块。高清缓存1400px及列表按实际尺寸解码保持；预览key版本v3隔离旧错误构图。
- 16项回归（新增4项真实比例异常/取整/方向/未知尺寸测试）、Release、Lint、APK签名通过。
- 同照片设备对照：修复前preview160×120->hq636×424；修复后preview320×213->hq636×424。after.mp4中相同场景的树、栏杆、人物构图保持，清晰度提升，未再见此前明显缩放/跳变。录屏复测用仅ADB开启的PunctumPreviewQA开关让已有缓存重放2200ms预览，不删除用户照片或缓存；开关与PunctumThumb日志均已设INFO并重启应用，交付为正常行为。该钩子默认关闭。
- 最新APK/Punctum-0.5.8-stable-preview.apk已保留数据覆盖安装PMX110，0.5.8/58。SHA256 76864ba4c6862510c52db72deefdc3b2e7f7486ac1fe00cf792edf311778e2ec。
- before.mp4/after.mp4及对应log在本目录。记录包含用户照片，未来上传前需排除私有录屏素材。验证覆盖已复现照片，不代表所有提供商的同宽高比异构图均可检测；仍需用户原问题图集复测。
- 未提交上传。


## Android 单次加载试验撤回 · 2026-09-20（当前状态）

- 用户真机反馈single-pass版比之前更差，快速滑动时整片黑屏无图。立即撤回该方案；系统预览必须保留，不能为了消除高清替换而移除快速预览。
- 已通过adb install -r将APK/Punctum-0.5.8-progressive-thumbnails.apk覆盖安装回PMX110，保留数据；安装Success、版本0.5.8/58和启动已核对。旧包SHA256 3319a19c994fb3a9f492a062f20c801a045cd1e22ad51334787a98b4c18d535c。
- 源码PhotoStill.SINGLE_PASS_LIST=false，恢复系统预览+后台逐张高清发布的默认行为；试验代码保留但禁用，后续构建不得再次默认开启。
- single-pass采集已停止，status complete，218秒；DEBUG尺寸开关由脚本关闭。
- 原来的高清替换时似乎移动问题仍未解决，不将回退报告为修复该问题。此前少量代理滑动、静止bounds和帧统计不足以验收图片可见性，后续必须验证快速滚动时的内容覆盖率/空白时长及用户体验。
- 当前保留按实际像素加载、背景缓存合并、逐张高清发布和按进入范围条件返回的功能；不追加安装未经充分验证的新替代方案。未提交上传。


## Android 列表单次图片加载试用 · 2026-09-20

- 用户换图集后仍看到自上而下逐张加载，替换时似乎移动。源码中列表animateOnLoad=false、crossfade=false，路径发布不改变Photo宽高，未证实布局移动；系统预览切换到另一个高清源是待验证的视觉跳动因素。
- 新试用：PhotoStill.SINGLE_PASS_LIST=true。有1400px高清文件就使用；没有时按单元格实际尺寸直接解码原图，不先请求系统预览。请求按URI/修改时间稳定，路径变化不重新请求。只对列表的fetch/decode使用共享limitedParallelism(2)，大图不变。
- 单次模式关闭后台整批高清生成和发布，避免同时解码压缩同一原图和二次换图。保留原生成代码及false回退开关。现有1400px缓存继续使用，但本模式不额外为列表生成新的1400px磁盘缓存，未缓存图片跨会话可能再次读取原图；首次显示仍可能逐张出现，整体速度需跨图集验证。
- 12项回归、Release、Lint、签名通过；APK/Punctum-0.5.8-single-pass-thumbnails.apk已保留数据覆盖安装PMX110，0.5.8/58。SHA256 f0d0c29a421465436e865e37645079ca45603d502182663c52c8678f0cbbb617。
- 真机Camera图集快速滑动5次，图像正常显示；两次静止UI采样12张缩略图bounds一致。初始短采样323帧/janky6（1.86%），P95=11ms，仅启动和代理手势小样本，不与历史用户测试作性能对照。日志按实际636×848、636×477等尺寸解码，无hq-ready，无已捕获崩溃。未证实用户原来的瞬间动一下在所有情况下消失。
- 本轮采集docs/verification/2026-09-20/single-pass-thumbnails/live/，脚本/tmp/punctum_single_capture.py，最长30分钟；用户测试结束创建live/STOP后分析。旧progressive包保留可回退。未提交上传。


## Android 高清缩略图逐张发布 · 2026-09-20

- 用户确认按实际像素加载的清晰度可以接受，肉眼看不出变化；新反馈为滑动停住后高清替换等待2–3秒。
- 代码确认旧任务等待可见区+后续8张全部生成后统一发布；现改为可见照片优先、每完成一张立即发布路径，后续预加载不阻塞前面的照片显示。
- 停稳等待250ms缩为80ms；GalleryScreen观察可见行和照片数量，路径更新不重启LaunchedEffect/生成任务。保留滚动时取消额外生成、串行解码、1400px磁盘缓存、实际像素显示和条件返回位置规则。
- 单张发布仍在后台合并列表，校验快照身份以避免覆盖删除/刷新；持久化沿用合并写入。
- 12项回归、Release、Lint、APK签名通过。已覆盖安装PMX110。最新APK/Punctum-0.5.8-progressive-thumbnails.apk，0.5.8/58，SHA256 3319a19c994fb3a9f492a062f20c801a045cd1e22ad51334787a98b4c18d535c。
- 真机进入4322张图集并滑动，工具读取日志观察到hq-ready slot7=1627ms、slot8=1814ms，并在各自之后约50ms记录对应decoded；最后slot21=3678ms。这证明批次完成前逐张发布，不代表所有照片能在固定时长内清晰。原始该段环形日志随后未能重新导出，以上数值来自本轮已读工具输出。
- 上轮adaptive-thumbnails采集已停止：2258帧，janky72（3.19%），P95 12ms/P99 18ms。场景不同，无前后严格对照，不以其判断性能变差。
- 本次采集已重新开启，目录docs/verification/2026-09-20/progressive-thumbnails/live/，脚本/tmp/punctum_progressive_capture.py，最长30分钟。查看status.json；创建live/STOP结束，再读取用户测试数据。实际等待改善待用户复测。
- 未提交上传。


## Android 按显示尺寸加载缩略图试用 · 2026-09-20

- 用户授权试用按实际显示像素加载列表缩略图。GalleryScreen去掉固定size(900)，使用AsyncImage按实际约束解析尺寸；原比例、1400px高清磁盘缓存与JPEG质量92保留，大图未修改。缓存key增加fit-v1避免复用旧请求的尺寸结果。
- PMX110日志实证：实际解码636×435、815×611、458×611，来源DISK；列表已正常显示。Components里PunctumThumb仅在DEBUG日志开关启用时记录解码尺寸和来源，不记录照片身份。
- 12项回归、Release、Lint、签名验证通过。最新APK/Punctum-0.5.8-adaptive-thumbnails.apk已保留数据覆盖安装PMX110，0.5.8/58。SHA256 7c5b6d0f45aeecf865acf389d7fb93fe5084d087c95e23b100d6e709f6791048。
- 清晰度和性能改善仍待用户试用。前一个已验收规则包APK/Punctum-0.5.8-smart-return.apk可作回退；现有条件返回规则与滚动优化均保留。
- 本次实时采集通过/tmp/punctum_adaptive_capture.py运行，最长30分钟，当前输出docs/verification/2026-09-20/adaptive-thumbnails/live/：每10秒gfxinfo，每60秒meminfo，持续应用logcat。查看status.json判断运行状态；创建live/STOP文件结束，脚本退出会关闭尺寸DEBUG开关。用户说测完时先停止，再分析增量，不能把安装检查帧当成纯滑动测量。未开长期自动化，无完整Perfetto采集。
- 未提交上传。


## Android 按进入时可见范围返回 · 2026-09-20（最新规则）

- 用户修正规则：最后查看的照片若仍属于进入大图时列表的可见范围，返回不滚动；超出这个范围才居中。本规则取代上一节无条件居中。
- 点击打开大图前记录当时可见照片URI，含边缘部分可见的行；每次进入重新记录，以照片身份判断，避免删除后行号变化改变判断。范围内不发起滚动，范围外沿用大图遮挡下居中定位。首尾受正常滚动边界限制。
- PMX110真机验证通过：范围内返回前后目标bounds均为[0,1776][636,2211]；另一次从最后完整可见行进入并连续翻6页，目标已超原范围，返回bounds=[636,1169][1272,1604]，中心1386.5px，屏幕中线1386px。未测试真实照片删除。
- 12项JVM回归、Release、Lint、签名通过；最新包 APK/Punctum-0.5.8-smart-return.apk 已保留数据覆盖安装PMX110，仍为0.5.8/58，保留滚动优化。
- SHA256 1d2eeef364b7db24c9e108e496cab4a99de2f9cdf3f3c749492756a87ccf6a35。证据 docs/verification/2026-09-20/smart-return/。未提交上传。


## Android 返回列表居中 · 2026-09-20

- 用户已确认首轮滚动优化“确实流畅多了”。新增要求：退出大图后，最后查看照片的缩略图中心对齐列表可见区域中心。
- 按实际照片行高和viewport计算滚动差值；目标不在可见区时先定位测量，再居中，保留大图遮挡直至定位完成。已在可见区时直接调整，避免再次先跳顶部。列表首尾受正常滚动边界限制。
- 最新包 APK/Punctum-0.5.8-centered-return.apk，SHA256 96f1dea846ec836f424c590fccbfe36d5d311873ba4162f04e30f536a39ca603；保留此前滚动优化。已覆盖安装PMX110，0.5.8/58。
- 10项JVM测试通过，Release/Lint/签名验证通过。PMX110真机打开No.15再返回，目标缩略图bounds=[0,1168][636,1603]，中心1385.5px，屏幕中线1386px；未执行真实照片删除测试。
- 性能采集完成：gfxinfo共4671帧，109帧janky（2.33%），P95=12ms，P99=17ms；应用日志未检出FATAL EXCEPTION、ANR in或OutOfMemoryError。统计包含采集期间整体界面操作，未具备优化前同场景对照，不作为提升比例。Perfetto已拉回，约134MB达到配置上限，可能提前结束；尚未做完整轨迹归因。
- 证据：docs/verification/2026-09-20/centered-return/、pmx110-scroll-live/。未提交或上传。


## Android 列表滚动首轮优化 · 2026-09-20

- 照片缓存JSON整理和保存移到单一后台线程；400ms窗口合并同图集更新，删除与保存按序执行，未落盘的新快照可立即读取。缓存最多存在短暂延迟，进程突然终止可能丢失最近的可重建缓存更新。
- 列表拖动/惯性滚动时取消额外高清缩略图任务；停稳250ms后恢复。已在进行的底层解码不能立即中断，完成后检查取消，不继续压缩/发布；生成任务用互斥锁防止重叠，临时文件原子改名避免半成品缓存。
- 缩略图路径合并移到后台，仅更新路径；检测原列表身份，避免覆盖期间发生的删除或刷新。
- 原有1400px缓存、900px列表请求、画质、布局和手势保留。首轮未做按显示尺寸加载优化。
- 8项JVM测试通过（6项返回位置、2项缓存合并与写入顺序），Release、Lint和APK签名验证通过。
- 最新包：`APK/Punctum-0.5.8-scroll-performance.apk`；SHA256 `cf73c33d949e1a837173ffaf21c1247b73c67908f6352bcb6ad0121f3b8808b4`。
- 已保留数据覆盖安装至本次连接的PMA110（3B15CV002XY00000，原0.5.7），安装后核验0.5.8/58并发出启动请求。交接中的PMX110并非本次设备。
- 尚无同一大图集的优化前后帧耗时对照，滚动体感待用户验收；不宣称卡顿已消除。证据：docs/verification/2026-09-20/scroll-performance/。
- 未提交、推送或升版本；已有iOS及Android未提交工作保留。


## 当前接续状态 · 2026-09-20

新对话先读 [本轮交接](SESSION_HANDOFF_2026-09-20.md)。两端仍为0.5.8/58，本轮增量未提交上传。

- Android最新包：`APK/Punctum-0.5.8-return-flash-fix.apk`，已覆盖安装PMX110。用户确认返回定位逻辑正确；随后的一帧闪动已调整显示顺序，尚待用户复测。6项位置回归通过。
- iOS最新包：`iOS/IPA/Punctum-0.5.8-pagination-fix-unsigned.ipa`，含保留缩放、对比模式、批删卡顿和连续翻页修复；41项回归通过，剩余真机验收见交接。
- 构建证据归档：`docs/verification/2026-09-20/`。以下旧日期章节保留历史，其“最新包”和验收结论以当时范围为准。


## Android return flash follow-up - 2026-09-19

6 position tests and Release passed; APK signature verified. Package: `APK/Punctum-0.5.8-return-flash-fix.apk`; SHA256 `04b25fddbc3d521fbd67200dcab0ff34b0317924f5d112e66f487b028d4d36da`. Log: `/tmp/punctum-android-return-flash.log`.

The first return-position patch dismissed detail before the LaunchedEffect scrolled the gallery, allowing the old list position to appear for a frame. Close is now a two-phase handoff: resolve the photo anchor and retain detail, scroll the underlying list, await a frame, then dismiss detail and present any delete confirmation. Repeated back requests are coalesced; empty destinations finish immediately. Existing row-by-URI restoration and user-drag cancellation remain unchanged.

This addresses the observed ordering defect. Frame-level physical-device visual acceptance remains pending; JVM position tests do not establish absence of a flash.


## Android detail return position - 2026-09-19

6 JVM tests passed, Release built, APK signature verified. Package: `APK/Punctum-0.5.8-detail-return-fix.apk`; SHA256 `06696feb6af1174eb5348c3a891e73edfcc5a2d0104de73aba009dc84d38bc51`. Build log: `/tmp/punctum-android-return-position-final.log`.

Returning from detail now anchors the gallery to the last viewed photo URI instead of retaining the original list position. Detail reports the displayed photo after composition; close resolves an undeleted successor or predecessor if needed. The gallery scrolls directly to that photo's two-photo row (including the header offset). Pending delete confirmation, removal, cancellation and restoration recalculate the row by URI. Manual list dragging clears the anchor; opening detail or switching galleries clears the previous return request.

Regression scope: ordinary browsing, earlier photos removed/restored, deleting the current/last photo, empty albums, and header/two-column row offsets. PMX110 connected near the end of the run; `adb install -r` completed successfully, preserving app data. Physical navigation and deletion flows still need verification. iOS unchanged by this task. Version remains 0.5.8 / 58; no commit or upload.


## Detail pagination fix - 2026-09-18

Latest IPA: `iOS/IPA/Punctum-0.5.8-pagination-fix-unsigned.ipa`. SHA256: `e53c5526ab35a61ded34805c0f06c395f22eb685342d3e54ab97e9cd11d71404`. Release and ZIP/version checks passed. Logs: `/tmp/punctum-pagination-final-tests.log`, `/tmp/punctum-pagination-release.log`. Physical-device continuous browsing remains to be verified.

The old trigger compared an index into visiblePhotos with photos.count, which still included pending deletions. With 80 loaded and 4 removed, the maximum visible index was 75 while the trigger required 76. Checking only index changes also missed entry at the final loaded photo and deletions that kept the same index.

DetailPagination now keys the loading task on selection, visible/loaded counts, exhaustion and gesture completion. It checks on entry and after removals/appends, uses the visible boundary, cancels superseded checks, and retains the existing model loading guard. Empty visible batches refill until exhaustion; deletion state is cleared if the old pager has been removed. Published exhaustion allows an empty final batch to close correctly. Existing ID-based selection preservation remains.

Six strategy tests cover 300 photos across four batches, direct entry at the last photo, 4/20/50/79 pending deletions, unchanged-index deletion, empty batches and gesture completion. All 41 XCTest cases passed; this is model validation, not a claim of 300 physical swipe gestures on an iPhone. Includes comparison mode and the preceding batch-delete performance fix; version stays 0.5.8/58. Android unchanged; no commit or upload.


## 批量删除卡顿修复 · 2026-09-18

验证：35项XCTest全部通过（`/tmp/punctum-batch-delete-final-tests.log`），Release通过（`/tmp/punctum-batch-delete-release.log`）；IPA完整性与0.5.8/58核对通过。最新包 `iOS/IPA/Punctum-0.5.8-batch-delete-fix-unsigned.ipa`，SHA256 `63d5f706e2bb20ad7e9846a136dd0dd2f2cf2e0ba46490658e3d0b92c4c79fbb`。真机批量删除滚动性能待复测，未声称黑屏问题已完成真机验收。

用户在真机批量标记数十张、返回列表确认删除后卡死，继续滚动可能黑屏。源码发现：删除通知触发全图集刷新；概览扫描、排序与索引写盘占用主线程；行重排会批量请求原图元数据。未取得该手机的卡死/内存终止日志，因此不把黑屏归因写成已证实。

本轮修改：删除期间合并刷新并暂停追加分页，防重复提交；过期刷新结果丢弃；概览分批让出主线程且旧任务可取消；排序和索引写盘移到后台，写盘串行保持新旧顺序；列表期间不生成首页封面；移除列表单元格出现时的原图元数据预读，保留点击读取。原生280ms删除、对比模式、版本0.5.8/58不变。

需真机复测：同一图集标记30–50张，确认删除后连续上下滚动；系统取消后照片仍在；删除后继续分页；回首页封面更新。未用用户照片做删除测试。


Android 与 iOS 分开记录，按版本倒序。写法见 [`changelog/README.md`](changelog/README.md)。

| 平台 | 当前版本 | 当前确认状态 | 完整日志 |
|---|---|---|---|
| Android | `0.5.8 / 58` | 照片放大、双指平移与回弹已确认 | [`android.md`](changelog/android.md) |
| iOS | `0.5.8 / 58` | 原生快照删除与 280ms 节奏已确认 | [`ios.md`](changelog/ios.md) |

## 当前交接状态 · 2026-09-18

iOS对比模式已实现，35项回归及Release通过；包 `iOS/IPA/Punctum-0.5.8-comparison-unsigned.ipa`，已验证部分模拟器交互与删新选测试图返回，剩余验收见交接。版本不变，未提交上传。详见 `COMPARISON_HANDOFF.md`。

## 上轮交接状态 · 2026-09-17

iOS 新增保留式 1–5 倍缩放，支持单指/双指四向拖动、连续捏合和双击复原；版本保持 `0.5.8 / 58`，280ms 删除动效保留。Android 本轮未修改。本轮改动尚未提交或上传。最新试用包为 `iOS/IPA/Punctum-0.5.8-persistent-zoom-unsigned.ipa`，验证记录见 `design-qa.md`。

## 历史交接状态 · 2026-09-16

本次同步包含 Android 0.5.8 照片手势，以及 iOS 能力对齐、照片缩放和平移、分页/竖图/图集准备修复、EXIF 加载优化、首页及面板交互修复、原生删除动效。iOS 当前删除收缩为 **280ms**，用户已确认这个状态；旧 SwiftUI 路径和回退开关保留。两端版本不再增加。

本次 GitHub 同步同时收录源码、项目配置、测试、README、分平台日志、验证记录和仓库内的 [`PUNCTUM_HANDOFF.md`](PUNCTUM_HANDOFF.md)，便于换电脑接续。完整实现、保护项和验收范围以交接文档为准。修改时间排序切换继续保持回退状态。

最新本地产物：Android `APK/Punctum-0.5.8-release.apk`；iOS `iOS/IPA/Punctum-0.5.8-native-delete-280ms-unsigned.ipa`。构建脚本会生成通用版本名 IPA；带动效后缀的文件是本次验收留档。安装包和签名凭据不纳入 Git。

Android Release/Lint 通过；iOS 最近一轮 21 项回归通过、最终 280ms Release 通过。详细证据与剩余复测边界见 [`design-qa.md`](design-qa.md)。

以下保留历史记录，旧版本、旧 Git 状态及旧安装包不代表当前状态。

## 历史交接状态 · 2026-09-07

给接手同事的入口。本段以文档上一次正式基线 `2026-09-02 / f726124` 为起点，汇总此后完成的全部变化。Android 正式版保持 `0.5.6`：已修复大图返回列表时标题区受状态栏显隐影响而跳动的问题，调整删除动效、明信片与票据文字，完善图集排序弹窗的固定高度、跨页拖动、自动滚动和多处列表滚动提示；反转胶片卡最终恢复原版；首页顶部标签更新为 `- PUNCTUM · STUDIUM -`，三个首页模式整体下移 `6dp`；已加入的系统图集在添加面板中保持勾选并锁定；首页随机名言库由 17 条扩充到 28 条。以上内容等待下次正式发版收录。iOS 当前为正式 `0.5.5`，三类弹窗的视觉层级更新同样等待下次正式发版收录。

### Git 状态

`origin/main` 当前为 `f7cab89`，已经包含 Android `0.5.6` 之后的 2026-09-03 至 09-04 未发版更新、README 刷新，以及 iOS 待下次发版收录的弹窗视觉更新。本地 `HEAD` 为 `4a4cc01`，两组既有提交 SHA 不同但补丁等价；本轮又补充了 `CHANGELOG.md`、`changelog/android.md`、`changelog/README.md` 三份尚未提交的文档。安装包作为本地构建产物，不纳入 Git；换电脑后从源码重新构建即可。

### 安装包（本地构建输出，不纳入 Git）

| 平台 | 路径 | 说明 |
|---|---|---|
| Android | `APK/Punctum-0.5.6-release.apk` | 本地执行正式构建后生成；签名文件和密码需在新电脑安全迁移 |
| iOS | `iOS/IPA/Punctum-0.5.5-unsigned.ipa` | 本地执行未签名 IPA 脚本后生成；交给 AltStore / AltServer 签名后安装真机 |

### 怎么编

- **Android**：Android Studio 或 `./gradlew assembleRelease`。包名 `com.punctum.gallery`。
- **iOS**：在 `iOS/Punctum` 必要时先 `xcodegen generate`。模拟器 Debug 可用本地 ad-hoc 签名；真机未签名包跑 `scripts/build-unsigned-ipa.sh`。脚本里写死 `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`，不要随便改全局 `xcode-select`。
- iOS 工程 bundle id：`com.chessyyq.punctum`，最低 iOS 17。启动参数（模拟器联调）：`-punctumStyle postcard|ticket|reversal_film`、`-openFirstGallery`、`-openFirstDetail`、`-openAlbumPicker`。

### 两端各停在哪

**Android 0.5.6 正式版：** 包含 0.5.5 的列表稳定性与新版大图上滑动效，并正式收录以下改进：

1. 大图页上滑仅在当前会话中标记待删除照片；退出大图页时统一显示「本次删除 N 项」，确认后才进入系统回收站，取消则完整保留。
2. 返回列表时，在确认前维持原列表稳定；确认后被删除照片统一移除，后续照片直接补位。批量删除、系统权限确认和失败恢复均走同一链路。
3. 列表缩略图使用约 900px 长边的高清缓存，Camera 等图集最近拍摄照片的清晰度得到改善；缓存分版后不会继续复用旧低清结果。
4. 首页图集排序、添加图集和大图页移动图集统一使用暖深灰卡片、淡描边、阴影与页面遮罩，弹窗和背后页面层级更清楚。
5. 三类 Android 弹窗统一改为页面内浮层，修复从屏幕右下角出现的感受，以及排序到图集选择时双层重叠、前后闪和尺寸二次变化。
6. 图集列表提前加载；首页弹窗切换保持同一遮罩亮度，大图页移动图集的遮罩以 180ms 平滑加深，卡片以 180ms 从 `0.98` 缩放到原大小。
7. Release 构建已通过，并在 OPPO PMX110 完成覆盖安装与本轮交互验收。

**iOS 0.5.5 正式版：** 包含与 Android 对齐的三态首页、实况、图集多选、列表原比例和移动到图集，并正式收录以下真机优化：

1. 首页中文标题用 Noto SemiBold 的 UILabel，禁止描边、禁止 Button 合成加粗；胶片中文光学居中。
2. 大图集列表先取 80 张再分页；缩略图长边 360、带缓存；进列表不再对每张图查 `PHAssetResource`。真机滑动已确认顺畅。
3. 列表长按：UIKit 小圈画在格子右上角；滑动不再误出圈；只有单击进大图，长按松手不进大图。
4. 实况角标贴原图右下角（26pt + 6pt 边距，热区 44×44）；点角标只开播；左右 25% 切图。长按会真正开播并出声（opportunistic 加载 + 播放音轨）。
5. 大图上滑删除：真实页面跟手上滑并顺滑飞出；背后冻结替代照片，删除后透明重建分页，避免旧图回闪和下一张横向滑入。
6. 黑底参数区与横图顶部留白支持切换沉浸态；实况长按、角标点击和页面热区恢复稳定。
7. 首页图集、列表返回、首页右上角与大图页顶部按钮统一为按下下沉、松手回弹、回弹后执行；大图页左上角使用返回箭头。
8. 画廊可见照片提前读取参数，大图页预备当前照片前后各 4 张；从列表进入、左右切图和删除后承接下一张均无参数加载跳变。
9. 大图复用画廊缩略图缓存，并移除入口淡入重影；图片和参数首帧同步出现。
10. 修复真机实况照片只有震动、没有画面和声音的问题；长按和实况角标播放已在 iPhone 14 Pro 验收通过。

**iOS 未发版视觉更新（2026-09-02）：** 首页调整图集、首页添加/选择图集和大图页移动到图集已统一为暖深灰弹窗底、28pt 连续圆角、淡米白描边、柔和阴影和额外页面遮罩；保留原生 Sheet 的拉起、拖动、滚动与关闭体验，并已在 iPhone 14 Pro、iOS 18.2 模拟器验证。

**Android 未发版更新（2026-09-03）：** 大图页隐藏状态栏期间，列表标题继续保留固定的状态栏安全区，返回列表时顶部标题和宫格不再发生纵向跳动；上滑删除确认飞出动效由 300ms 缩短为 200ms；明信片卡片的两组双行文字均增加 2dp 间距；首页图集顺序管理改为长按整行上下拖动，原上下按钮替换为拖动横线，并锁定拖动期间的排序快照、实时重算起始索引，避免后台刷新或同一弹窗内连续拖动造成换位失效。顺序列表固定为 290dp 高度，超出 5 行后在弹窗内部滚动。反转胶片卡最终恢复为本轮视觉实验前的原版米灰纸框、标题、日期和 `SLIDE · DIAPOSITIVE` 排版；三款彩色反转片实验代码完整保留并关闭。Release 构建与 Lint 已通过，并在 OPPO PMA110（Android 16 / ColorOS 16.1）完成覆盖安装、返回路径、明信片显示、拖动排序与反转胶片视觉回归。

**Android 未发版更新（2026-09-04）：** 首页顶部标签更新为 `- PUNCTUM · STUDIUM -`，并与右侧两个按钮按上下中线对齐为同一横排；三个首页模式的共同顶部留白增加 `6dp`，整体视觉重心同步轻微下移；添加图集面板会将已加入 Punctum 的图集保持勾选并锁定，状态文案显示为「已添加」，确认时同步过滤已加入项以避免重复提交；首页随机中文名言库由 17 条扩充到 28 条，继续在冷启动时随机展示。

### 双端不要弄反的约定

- **纸纹**在 Android `app/src/main/res/drawable/`（票据、胶片、明信片票尾），iOS 工程直接打进包，不要各复制一份。
- **实况**：Android 是 Motion Photo（JPEG 尾部 MP4）；iOS 是静图 + 配对 MOV，走 `PHLivePhoto`。不要把安卓解析套到 iOS。
- **上滑删除**：安卓是 `progress > 0` 时藏 `HorizontalPager`，只变换当前页；iOS 必须用静图，不要再对整个 TabView 做 scale。
- **中文标题不要描边。** 用户明确禁止。
- **不要把徕卡 / LFI 商业字体拷进仓库。** 英文用 Newsreader。
- 内网环境：不要上传文件到公网，不要做内网穿透。

### 关键代码入口

| 场景 | Android | iOS |
|---|---|---|
| 首页三态卡 | `ui/SwitcherScreen.kt` | `Views/SwitcherScreen.swift` |
| 图集列表 | `ui/GalleryScreen.kt` | `Views/GalleryScreen.swift` |
| 大图 / 实况 / 上滑删 | `ui/DetailScreen.kt` | `Views/DetailScreen.swift`、`LivePhotoViews.swift` |
| 图集选择 | `ui/AlbumPickerDialog.kt` | `Views/AlbumPickerView.swift` |
| 相册数据 | `data/PhotoRepository.kt`、`MotionPhotoService.kt` | `Services/PhotoLibraryService.swift`、`PhotoImageLoader.swift` |
| 版本号 | `app/build.gradle.kts` | `iOS/Punctum/project.yml`（改完 `xcodegen generate`） |
