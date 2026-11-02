# Punctum 新对话交接 · 2026-10-30 · Android 0.7.0

## 当前整理与接续入口 · 2026-11-02 · Android 0.7.0/90

本次用户要求打包、更新GitHub及文档，**不改versionName与versionCode，继续0.7.0/90**。最新[11月2日交接](SESSION_HANDOFF_2026-11-02.md)汇总当前首页、两项刷新/闪黑修复、两台手机安装、产物与回退边界；[发布记录](docs/RELEASE_VALIDATION_2026-11-02.md)记录本轮构建和远端核验。当前英文Averia/中文思源宋体、黑灰文字与纸纹、固定诺贝尔间距保持，投影精确文案“观止，岂止于观”。

诺贝尔与个人PMA110均已安装/90，个人手机安装/启动通过。本轮源码、资源、测试与文档已同步GitHub main（源码提交5776b52，236文件完整树一致，后续补入发布核验记录）；不更新Google Drive，Drive仍为/88；iOS仍0.5.10/60。下方旧版本与“未同步GitHub”“PMA110仍/89”为当时历史，当前状态以新交接与发布记录为准。

## 最新本地修复 · 新照片缩略图切换闪黑 · 2026-11-02 · Android 0.7.0/90

FilmThumbnail在系统预览切换到本地高清缩略图时保留最后成功画面，避免请求Loading时绘制空底；状态按照片URI与文件修订隔离。诺贝尔保留数据安装并验证四张新照片的高清替换，四次Loading均保持可绘制画面，连续截图未见空底。重新接入后已确认四张测试照片清理完成，恢复15张原照片；冷启动进入/返回正常，手机APK哈希与本地一致，最终停留胶片首页。Release/Lint/签名通过。保留/89修复和原视觉/灯光；暂不上传Google Drive，云端仍为/88，新源码未同步GitHub。个人PMA110仍为先前已安装/89。详见 [新照片闪黑修复验证](docs/ANDROID_FILM_THUMBNAIL_FLASH_VALIDATION_2026-11-02.md)。

## 最新本地修复 · 胶片首页旧封面闪回 · 2026-11-02 · Android 0.7.0/89

修复点击图集时旧完整列表缓存反写最新首页四张封面的问题：缓存与当前首页媒体快照不一致时先扫描，复用缓存不再覆盖概览；异步返回增加过期内容检查。诺贝尔已保留数据试装，新增/删除/原位替换封面和重复进入验证通过。现有60项测试、Release/Lint/签名通过，字体纸纹布局及动效保持。用户本轮明确暂不上传Google Drive，云端仍为/88；源码仍未同步GitHub。详见 [封面闪回修复验证](docs/ANDROID_FILM_COVER_FLASH_VALIDATION_2026-11-02.md)。

## 最新云端交付 · 正字更正 · 2026-10-31 · Android 0.7.0/88

当前投影空状态精确文案为“观止，岂止于观”（使用“岂”字，无末尾句号），其余保留/87。Release/Lint/签名通过，APK内Dex核对精确新字符串且旧错误字符串不存在，诺贝尔已保留数据更新并启动。已上传Google Drive并读回名称、APK MIME及40,161,692字节大小一致：https://drive.google.com/file/d/1754TsHIdM708sqvttcGngUXcS9Bq69aM/view?usp=drivesdk 。以下旧“其止于观”均属历史，不可恢复为当前默认；新源码仍未同步GitHub。


## 最新云端交付 · 去掉句号 · 2026-10-31 · Android 0.7.0/87

投影空状态当前精确文案为“观止，其止于观”（没有末尾句号），其余保留/86。Release/Lint/签名通过，APK内Dex已核对新文案及旧带句号文案不存在；诺贝尔保留数据更新并正常启动。按用户指令直接上传Google Drive，独立读回文件名Punctum-0.7.0-87.apk、APK MIME和40,161,692字节大小一致。链接：https://drive.google.com/file/d/1gzlglziVC_bpVl-Cw8StgMpdaSqTiSfE/view?usp=drivesdk 。下方旧版本带句号文案均为历史，新源码仍未同步GitHub。


## 最新云端交付 · 2026-10-31 · Android 0.7.0/86

用户随后明确要求“上传”，已将带胶片首页下沉反馈的APK上传Google Drive，文件名Punctum-0.7.0-86.apk；独立读回云端名称、MIME及40,161,692字节大小，与本地一致。下载：https://drive.google.com/file/d/1r05LzQmDZqAV7iF8uYQymX9j2WcZiO8R/view?usp=drivesdk 。下方/86开发阶段“未上传”现为历史；新源码仍未同步GitHub。


## 最新本地试装 · 胶片首页下沉按压 · 2026-10-31 · Android 0.7.0/86

胶片首页整卷与纸栏复用明信片/票根的punctumPressable：缩至0.985、下移4dp、alpha0.96，松开立即进入图集，拖开取消回弹。诺贝尔保留数据安装，按下截图、取消后卡片逐像素复原、松开进入及关闭系统动画点击均实测通过，设置原值已恢复。字体、纸纹、122px白栏、固定165/166px间距和空状态“观止，其止于观。”保持。Release/Lint/签名和安装包哈希通过。详见 [按压验证与动效复核](docs/ANDROID_FILM_HOME_PRESS_VALIDATION_2026-10-31.md)。本轮未上传Google Drive，云端仍为已交付/85；新源码尚未同步GitHub。


## 最新交付 · 投影空状态文案 · 2026-10-31 · Android 0.7.0/85

投影空状态已按用户原文改为“观止，其止于观。”，其余沿用/84的Averia/思源宋体、黑灰文字、纸纹背景、122px白条及诺贝尔固定165/166px间距。Release/Lint/签名通过，诺贝尔保留数据安装并核对新文案和安装包哈希。用户本轮明确授权上传Google Drive，APK已上传且云端文件名/MIME/大小读回一致；先前暂不上传的指令已被本轮覆盖。详见 [文案与交付验证](docs/ANDROID_EMPTY_QUOTE_VALIDATION_2026-10-31.md)。新源码尚未同步GitHub，/77仍为此前已验收锁版。


## 最新本地试装 · 恢复原字体黑灰文字 · 2026-10-31 · Android 0.7.0/84

用户否定凹印和新字体，当前恢复Averia Serif Libre/思源宋体Regular、黑色标题与#606060小字，使用普通Text，保留附件图2纸纹。白条参考72→76，诺贝尔实机122px；固定间距165/166px，第三卷纸栏仍贴2412px底边。不自动适配其他屏幕。已保留数据安装诺贝尔，Release/Lint/签名与安装包哈希核对通过，临时预览图集和副本已清理。待用户验收，不上传Google Drive。详见 [纸纹黑灰文字验证](docs/ANDROID_FILM_HOME_PAPER_INK_VALIDATION_2026-10-31.md)。/82与/83凹印试验已撤下，不可作当前默认；/77仍为此前已验收锁版。


## 最新本地试装 · 宽厚衬线凹印 · 2026-10-31 · Android 0.7.0/83

用户选择免费近似字体，并追加明确要宽、粗，压印要有凹面面积，避免窄薄字体。最终改用Bodoni Moda：英文标题Black 900、小字SemiBold 600，中文标题思源宋体SemiBold。原图品牌官方Display字体为Room 205，付费App授权未购买；本轮使用Bodoni官方开源字体及OFL许可。纸纹、凹印参数、字号和诺贝尔固定174/175px间距保持，第三条纸栏仍贴2412px屏幕底边。已保留数据安装诺贝尔，Release/Lint/签名及安装包哈希核对通过；临时预览图集和副本已清理。等待用户视觉验收，不上传Google Drive。详见 [宽厚字体验证](docs/ANDROID_FILM_HOME_BOLD_SERIF_VALIDATION_2026-10-31.md)。/77仍为此前已验收锁版，以下均为历史阶段。


## 最新本地试装 · 纸纹与浅凹印 · 2026-10-31 · Android 0.7.0/82

用户要求将附件图2原纸纹贴入胶片新首页白栏，并参考图1做卡纸凹印；已澄清保留首页Averia Serif Libre和思源宋体。新增原图PNG及字形内壁缓存绘制，保留/81白栏116px、诺贝尔固定174/175px间距和全部字体/字号；实机全部文字边界与/81一致，第三条纸栏仍贴2412px物理底边。已保留数据安装诺贝尔，Release/Lint/签名通过；4次滚动160帧0 jank、P95 12ms为该机短时样本。临时两卷预览及副本已清理。待用户视觉验收，不上传Google Drive。详见 [纸纹凹印验证](docs/ANDROID_FILM_HOME_DEBOSS_VALIDATION_2026-10-31.md)。以下均为此前阶段；/77仍是此前已验收锁版。


## 最新本地试装 · 诺贝尔固定间距 · 2026-10-31 · Android 0.7.0/81

用户明确只以诺贝尔手机为锚点，不要按屏幕高度自动均分，不考虑其他机型比例。最终固定两段间距174/175px（诺贝尔密度3）；白条从参考68微增至72，实机116px，字体保持/79。实机第三卷含白条底边精确到2412px，与屏幕物理底边一致。已保留数据安装，Release/Lint/签名通过，待用户截图验收；不要上传Google Drive。旧首页代码和图集内灯光/投影保留。后两卷临时预览图集截图后清理。详见 [固定间距验证](docs/ANDROID_FILM_HOME_FIXED_VALIDATION_2026-10-31.md)。以下/79及/78均为此前阶段；/77仍为此前已验收锁版。


## 最新本地试装 · 首页文案与三卷布局 · 2026-10-31 · Android 0.7.0/79

三种首页数量说明统一为Stories Behind xx Photographs，明信片/票据用Newsreader，胶片用Averia Serif Libre。胶片白条和文字缩小，三模式共用页头；诺贝尔实测首卡上沿均为653px，首屏三卷与白栏完整显示。旧首页代码保留，图集内已验收灯光/投影源码未改。Release、Lint及60项测试通过，已保留数据安装诺贝尔PJW110。用户明确先截图验收，本轮不要上传Google Drive。临时两个预览图集及8张副本在截图后清理，原图集15张保留。新源码仍未同步GitHub。详见 [本轮验证](docs/ANDROID_FILM_HOME120_COMPACT_VALIDATION_2026-10-31.md)。以下/78与/77均为此前阶段；/77仍是此前已验收锁版。


## 新增本地试装 · 胶片首页复刻 · 2026-10-30 · Android 0.7.0/78

用户随后授权按参考图实现单列通栏四图120胶片首页、内置Averia Serif Libre并验证加载刷新。已保留旧首页完整代码及回退开关，诺贝尔PJW110保留数据试装/78；Release、Lint及60项测试通过。临时图片和额外图集已清理，原测试图集恢复15张。已上传Google Drive并核对云端元数据；新源码尚未提交GitHub。视觉仍待用户验收，0.7.0/77保留为此前已验收锁版。详见 [首页验证](docs/ANDROID_FILM_HOME120_VALIDATION_2026-10-30.md)。图集内手动开灯、曝光和投影源码未改。以下/77锁版记录为此前阶段。


## 接续结论

用户已明确认可120胶片和最终开灯过曝效果，并要求锁定为重要升级 **Android 0.7.0 / versionCode 77**、上传GitHub及补齐交接。用户原话：“不错，终于调出舒服的效果了，锁定版本加0.1.0版本号”。以试装0.6.0/76的源码效果锁版，仅更新版本号和文档；不要继续主动调亮度或改造界面。

真实仓库：`/Users/80400763/Documents/Punctum`。聊天目录 `/Users/80400763/Documents/ChatGPT/Punctum-OPPO` 只作辅助，不在旧副本开发。新对话先读本文件、`PUNCTUM_HANDOFF.md`、`docs/RELEASE_VALIDATION_2026-10-30.md`，再核对实时Git与USB设备状态。旧交接中4:3白框、自动开灯、近黑暗片和“尚未上传”均按当时历史理解。

本次交接完成后等待用户新任务。无需主动新增功能、重做首页、移植iOS、更新Demo、上传NAS或再次安装别的手机。用户没有要求Demo时直接改正式App。不要使用L3 Workflow，不要reset/clean或覆盖用户已有改动。

## 当前版本与发布入口

- Android：**0.7.0/77**，正式包 `APK/Punctum-0.7.0-release.apk`。
- iOS：**0.5.10/60**，本轮未改、未重建、未安装，尚未移植Android新胶片体验。
- GitHub：`https://github.com/yyqlovelife/Punctum_YYQ`，发布分支`main`。
- 本地接续分支 `codex/android-0.7.0-release`，版本标签 `android-v0.7.0`；新对话以实时`git status`、`git log -1`、远端main为准。
- 源码提交 `89831a0f18f621f8f6e3ffec66a54c5faa8d72bf` 已上传，211文件完整树比对通过；之后的文档补充提交与标签见远端。
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
