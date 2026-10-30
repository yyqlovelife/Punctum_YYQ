# Punctum 新对话交接 · 2026-10-30 · Android 0.7.0

## 接续结论

用户已明确认可120胶片和最终开灯过曝效果，并要求锁定为重要升级 **Android 0.7.0 / versionCode 77**、上传GitHub及补齐交接。用户原话：“不错，终于调出舒服的效果了，锁定版本加0.1.0版本号”。以试装0.6.0/76的源码效果锁版，仅更新版本号和文档；不要继续主动调亮度或改造界面。

真实仓库：`/Users/80400763/Documents/Punctum`。聊天目录 `/Users/80400763/Documents/ChatGPT/Punctum-OPPO` 只作辅助，不在旧副本开发。新对话先读本文件、`PUNCTUM_HANDOFF.md`、`docs/RELEASE_VALIDATION_2026-10-30.md`，再核对实时Git与USB设备状态。旧交接中4:3白框、自动开灯、近黑暗片和“尚未上传”均按当时历史理解。

本次交接完成后等待用户新任务。无需主动新增功能、重做首页、移植iOS、更新Demo、上传NAS或再次安装别的手机。用户没有要求Demo时直接改正式App。不要使用L3 Workflow，不要reset/clean或覆盖用户已有改动。

## 当前版本与发布入口

- Android：**0.7.0/77**，正式包 `APK/Punctum-0.7.0-release.apk`。
- iOS：**0.5.10/60**，本轮未改、未重建、未安装，尚未移植Android新胶片体验。
- GitHub：`https://github.com/yyqlovelife/Punctum_YYQ`，发布分支`main`。
- 本地接续分支在发布记录中注明；新对话以实时`git status`、`git log -1`、远端main为准。
- 发布SHA、APK SHA256、测试/手机与远端核验结果集中在 `docs/RELEASE_VALIDATION_2026-10-30.md`。APK、IPA、签名、本机配置、原始照片/录像和 `docs/verification/` 均排除Git。

## 已验收的产品效果

1. **全沉浸胶片图集**：进入后隐藏手机状态栏，退出恢复；首页使用不随显隐变化的安全区，避免顶部来回跳。去掉选片台底部数量/LIGHT TABLE整行，44dp交给投影；标题、时间数量、胶片间距与底部20dp留白已调整。
2. **黑色120胶片**：3列2行，每页六张，横向翻页。照片中心正方形裁剪；投影仍保留原始画幅。边框使用参考黑色纹理、橙色文字和两枚三角，固定文字PUNCTUM 800。上方随机双位码随照片稳定，底部全图集序号从1开始，第二页7–12，勿按页重置。
3. **手动开灯**：进入后一直保持暗态，画面隐约显影；点击标题右侧小横向实体开关才亮，取消任何自动倒计时。关灯立即取消动画并回暗态，每次重入重新关灯。开关本体34×18dp、触控48dp，无矩形按压Ripple，仅本体改变，配CLOCK_TICK触感。
4. **透光与曝光**：点亮瞬间过亮，亮部先溢出、浓密暗部仍有轮廓；随后慢慢均匀回落并恢复原色。黑色片基独立处理，峰值仍有边框和轻微透射颜色。底板初始与恢复均保持中性，不能重加明显黄染；进入时不能先变得更暗。
5. **菜单与投影**：三个点位于屏幕右上top4dp/end20dp，44dp触控；投影最小上下留白48dp避开竖向3:4照片。原下拉菜单视觉、字体、颜色、菜单项保持。投影点片1.2秒、边缘点按与横滑快切、原比例和EXIF规则保持。

## 锁定的视觉参数与实现

- 外框坐标1519×1538；方形开口1420×1420，左51、上64、右48、下54。跟随单元格等比缩放，保留轻微角度。Bitmap源矩形中心裁切、抗锯齿和位图滤波解决边缘毛刺。
- 框图 `app/src/main/res/drawable-nodpi/film120_frame.png`，数字图集 `film120_digits.png`；参考照片未嵌入框图透明开口。随机码由照片URI稳定散列至10–99。
- 手动开灯直接从参考时间900ms开始，到4100ms结束，总3200ms，LinearEasing；无入场自动等待。系统禁用动画时进入仍关灯，手动打开直接终态。
- 底板峰值RGB253/253/253；参考1.10秒至3.50秒的2.40秒内smoothstep回落至232/232/230。
- 照片峰值编码RGB乘法增益3.0，点亮后加白偏置0；截断后保留8%原图细节，随回落恢复增益1、原图100%。这属于轻量透光视觉近似，不声称完整光谱或精确线性光仿真。此前大量加白会导致整张纯白，已撤回。
- 黑边关灯增益0.55；开灯相对最终片头测量值的RGB抬亮乘0.48，再随参考恢复进度归零；最终原黑框。黑边、照片和底板分别控制。
- 不逐张识别高光/阴影，不逐帧CPU扫描像素。曲线一次预计算，缓存位图和画笔，原生ColorMatrix/Canvas绘制，只在绘制阶段读取光状态。勿把动画状态上移为整页逐帧重组。

## 源码入口

- `app/src/main/java/com/punctum/gallery/ui/FilmReviewScreen.kt`：布局、开关、灯光、120框与缩略图、投影与菜单。
- `app/src/main/java/com/punctum/gallery/model/FilmLampTimeline.kt`：灯光时序与独立曝光曲线。
- `app/src/main/java/com/punctum/gallery/model/Film120Geometry.kt`：坐标、裁切、适配、随机码。
- `app/src/main/res/raw/film_lamp_reference.json`：视频测量数据；底板最终颜色及照片曝光在模型中独立规范。
- `MainActivity.kt`：沉浸状态栏与入场就位；`ui/SwitcherScreen.kt`：首页稳定安全区。
- 同目录模型测试 `FilmLampTimelineTest.kt`、`Film120GeometryTest.kt`、`FilmReviewLayoutTest.kt`。

## 删除与保护项

沿用0.6.0/63：所有模式列表长按完成立即弹观止删除确认，确认前照片/投影不变，取消保留。按住200ms后显示进度，再持续500ms；普通列表弹窗前隐藏角标并在finally清理。胶片列表长按不能回退成退出统一确认。

投影区上滑与旧大图仍使用待删除队列、退出统一确认。Android12+媒体管理权限流程保留，不能假设新手机已经授权。近几轮只验证展示与开关，未删除用户原始照片。

首页堆叠胶片与新封面仍暂缓；胶片投影未接入旧大图缩放/Motion Photo入口。iOS与NAS都不在本次发布范围。

## 验证边界与历史资料

58项JVM测试、Release、Lint、签名、版本与安装包拉回哈希验证。具体正式机型和结果以发布记录为准；USB已从PMA110换到诺贝尔PJW110，不复用旧设备脚本。

/76实机确认比/75更强的过曝与保留暗部；回落后的胶片区域与/75逐像素一致，用户最终认可。本次0.7.0只做版本锁定，保留同套逻辑。此前PMA110不录屏开灯样本81帧，P95 14ms，6帧jank，不能承诺所有设备零掉帧；录屏会额外增加负载。

私有原始证据在 `docs/verification/2026-10-30/`，不上传。原参考视频在 `/Users/80400763/Downloads/VID_20261030_101041.mp4`。辅助Demo位于聊天目录 `outputs/film-lamp-study-2026-10-30/demo`，与/75、/76正式照片曝光已有差异，不能用旧Demo覆盖原生最终参数。

细节验证：`docs/ANDROID_FILM120_VALIDATION_2026-10-30.md`、`ANDROID_FILM_TRANSMISSION_VALIDATION_2026-10-30.md`、`ANDROID_MANUAL_LIGHT_VALIDATION_2026-10-30.md` 等。优先读取本交接的最新规则，不把历史试验值当当前值。

构建时设置JAVA_HOME为 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`，执行 `./gradlew :app:assembleRelease :app:lintRelease :app:testReleaseUnitTest`。签名材料和密码仅从本机忽略文件读取，禁止输出或上传。Git普通推送可能遇到HTTP302；先核对远端parent，必要时用已认证Git Data API上传blob/tree/commit并非强制更新main，最后逐项比对完整文件树，不能把“Everything up-to-date”当上传成功。
