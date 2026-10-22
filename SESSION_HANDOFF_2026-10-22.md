# Punctum 接续 · Android 0.6.0 · 2026-10-22

Android **0.6.0/61** 正式发布：上次 GitHub 0.5.9/59，开发试用版 0.6.0/60，本次构建号递增。用户已确认最终 1.2 秒投影节奏。iOS 保持 **0.5.10/60**，本轮未改。

反转胶片图集采用幕布与六格横向分页选片台合一；空幕布中央轻显“选择一张胶片，重映一段时光”；点片开片亮起 1.2 秒、左右边缘/横滑直接快切。原比例图片、横图与 EXIF 同 24dp 边距、竖图独立高度、参数常驻与空隙居中、必要时中心最多抬升 18dp。居中名称/时间数量，左返回右更多，切换排序后菜单不收起。下方按住与投影上滑加入待删除，退出图集统一确认，取消恢复；按住保留右上角进度图标，暗色蒙层已撤回。白色底板覆盖至导航区域并避让控件。

正式包 `APK/Punctum-0.6.0-release.apk`；Release、签名、Lint（0 错误/17 条既有警告）和 44 项单测通过。用户换接的新 PMA110 安装与远端上传核验见 [发布记录](docs/RELEASE_VALIDATION_2026-10-22.md)。用户确认与真实系统删除测试分开记录；本轮未删除用户照片。

首页保持旧样式，四张堆叠/黑色片基仅为设计探索，用户已暂缓；旧大图缩放和 Motion Photo 入口未移植至新选片台。完整迭代见 [选片台验证](docs/ANDROID_FILM_REVIEW_VALIDATION_2026-10-22.md)，新接续见 [本轮交接](SESSION_HANDOFF_2026-10-22.md)。以下带日期旧记录中的包、版本、未验收/未上传状态按各自阶段理解。

## 工作入口

真实仓库 `/Users/80400763/Documents/Punctum`；`Punctum-OPPO` 为辅助交接目录。保留已有修改，不使用 L3 Workflow。用户授权直接更新当前 USB 手机；当前新手机为 PMA110（序列号末尾 L2N00000），此前主手机末尾 XY00000，本次没有操作旧机。不更新 NAS。

## 源码入口与保护项

- `app/build.gradle.kts`：0.6.0/61，Release 原签名配置保留。
- `MainActivity.kt`：仅 REVERSAL_FILM 进入 `FilmReviewScreen`；返回等待待删除确认/系统结果，取消后正常回首页。
- `GalleryViewModel.kt`：`filmQueuedDeletionIDs`、`filmDeletionInProgress`、`filmExitReady` 协调批次；取消/失败恢复照片；不能绕开系统授权或抢先销毁当前图集。
- `ui/FilmReviewScreen.kt`：先保留当前投影直到新图解码完成，图片/比例/元数据同步更换；`FILM_PROJECTION_REVEAL_MILLIS = 1200` 同时控制初选与重选。动画 State 在绘制/图层读取，模糊光层与画笔缓存；避免重新引入逐帧整页重组。快切直接替换，系统禁用动画时直接显示。
- `model/FilmReviewLayout.kt`：六片分页、24dp 横图/EXIF 公共边距、最多 18dp 信息让位、方向优先级和删除替代身份。保持原比例且不裁切，滑动选片台不能误投影。
- `ui/GalleryScreen.kt`：原列表沿用，共用 `DeleteProgressBadge`；新选片台为按住 200ms 后显示进度、再 500ms 加入待删除，中途松手/滑动取消，右上角无整片暗色蒙层。
- `model/FilmReviewLayoutTest.kt`：比例、避让、日期、手势、替代顺序回归；最终总计 44 项测试。

## 视觉最终状态

黑灰幕布有织纹/褶皱/晕影，图像无白边；投影区高度固定、图像按比例上下居中。空态句子采用思源宋体 12sp、字距 0.7sp、Bone 22% 透明度。下方发光白底，六张白框 PUNCTUM 胶片带轻微倾角。图集名 18sp/日期数量 10sp 居中，底部页码旁小「i」；没有大面积阴影按钮。首页方案已暂缓，不要依据生成图自动继续改首页。

## 构建与验证

JDK 使用 Android Studio 随附 JBR，运行 `./gradlew :app:assembleRelease :app:lintRelease :app:testDebugUnitTest`。签名资料留在本机；APK、原始私有截图和设备日志为忽略输出。正式包哈希、新机安装、公开发布结果见 [发布验证](docs/RELEASE_VALIDATION_2026-10-22.md)。iOS 本次无新测试，沿用原有 0.5.10/60 记录，不将 Android 用户确认扩展成 iOS 新版验证。

最终节奏已获用户确认。新选片台尚未接入旧大图的缩放/Motion Photo 播放；本轮删除仅测试排队、确认出现和取消恢复，未真正删除用户媒体。后续按用户新反馈继续。

## 新机回归结果

0.6.0/61 已保留数据安装新 PMA110（末尾 L2N00000），设备包与本地正式 APK 哈希一致。空态/选片/边缘点按和横滑、参数常驻、菜单排序保持/恢复、重命名取消、按住取消与排队、退出统一确认及取消恢复均已通过。本次未实际删除照片。最终手机停在图集投影页。GitHub 精确提交与树核验记录见发布验证文档。

## GitHub 已发布

源码与本轮文档已发布到 main 的 [`929f92e`](https://github.com/yyqlovelife/Punctum_YYQ/commit/929f92e7dde89e8057ac0eb153ce7aed24dbb33e)，远端 191 个文件已与本地发布树逐项核对一致。常规推送失败后使用 Git Data API 非强制更新；本地后续工作在 `codex/android-0.6.0-release`，原开发分支与源提交保留。本节是源码发布后的文档核验补充，最终 main 提交以会话报告为准。
