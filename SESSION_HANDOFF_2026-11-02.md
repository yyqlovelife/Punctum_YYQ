# Punctum接续交接 · 2026-11-02

## 最新本地试装 · 2026-11-03 · 胶片投影跟手交互

胶片投影区改为原生跟手横向分页；上滑仅移动照片，幕布/选片台固定。与其他大图页共享删除阈值、圆钮/提示、收拢与回弹曲线。诺贝尔已保留数据试装，63项测试及Release/Lint/签名通过，临时图集/照片已清理，最后停在可直接体验的投影页。版本保持0.7.0/90；本轮未提交GitHub或上传谷歌，个人手机未重装。试装包以`Punctum-0.7.0-90-projection-gestures.apk`区分此前已发布/90。接续见[11月3日交接](SESSION_HANDOFF_2026-11-03.md)与[验证记录](docs/ANDROID_FILM_PROJECTION_GESTURE_VALIDATION_2026-11-03.md)，下方是此前发布状态。

## 当前结论

用户要求当前实现重新打包、更新GitHub及文档，**不再升级版本号或构建号**。Android保持**0.7.0/90**；iOS保持0.5.10/60。本次同步范围为/77之后全部首页、资源、缓存与两项闪烁修复。此前/77是已确认的灯光锁版，本轮保持其图集内灯光与投影手感。用户已确认/89旧封面闪回修复有效；/90高清替换修复有开发实机证据并已安装两台手机，完整用户体验验收未另行声明。

## 仓库与工作规则

真实源码：`/Users/80400763/Documents/Punctum`。`/Users/80400763/Documents/ChatGPT/Punctum-OPPO`仅为辅助交接目录。保留既有改动，不用L3 Workflow，不reset/clean，不主动改UI。GitHub为`yyqlovelife/Punctum_YYQ/main`，实时状态及源码提交见[发布记录](docs/RELEASE_VALIDATION_2026-11-02.md)。原`android-v0.7.0`标签保留指向/77，不能将标签直接当/90。重新打包亦保持0.7.0/90。

## GitHub已核验状态

源码提交[5776b52](https://github.com/yyqlovelife/Punctum_YYQ/commit/5776b52e516d05a786b4a21e922fbc4fee0ae4da)已进入main，递归完整树236文件的路径/类型/模式/blob SHA与本地逐项一致，tree `57a7d5d818592486d82cb3055ed99cf8efda814b`；发布结果随后以文档补充提交纳入main。APK与私有素材未入库，原/77标签未移动。后续核对以实时main为准。

## 当前视觉与交互

- 胶片首页由最新四张照片组成一卷120胶片；不足四张留空。FilmStripHomeEnabled开启，旧ReversalFilmGrid完整保留回退。
- 纸栏用用户原PNG纸纹。字体恢复Averia Serif Libre英文/思源宋体中文，普通Text，标题黑色、小字#606060。Bodoni与PaperDebossText等试验资源保留但未启用。
- 诺贝尔1080×2412、density3固定校准：首卷上沿653px；条高354px，纸栏122px；后续间距165/166px，三卷含纸栏下沿贴2412px。不按屏幕高度均分，不要求其他手机同比例。
- 所有首页数量文案为`Stories Behind xx Photographs`；字体随对应首页英文图集名。胶片日期数量用Averia，其他首页沿用Newsreader。
- 胶片整卷含纸栏复用punctumPressable，缩0.985、下移4dp、alpha0.96；松开进入、拖开取消。
- 投影空状态精确文案：**观止，岂止于观**（岂字，无末尾句号）。图集内手动开灯、过曝和回落、投影交互保持。

## 刷新与性能保护

GalleryOverview保留coverUris/coverRevisions，四封面Coil请求720px，缓存键带文件修改时间与大小；EXIF读取有界缓存，胶片首页跳过旧明信片/票根合成。

/89：旧完整列表可能落后于已扫描的首页。内容签名（数量、时间跨度、四URI及修订）不一致时先真实扫描再进入；复用缓存不再写回首页概览。异步概览/合成结果核对当前状态，防止旧结果覆盖。

/90：FilmThumbnail在同一照片由系统预览切换本地高清缩略图时保留最后成功位图；Loading/Empty/Error继续绘制已有图，Success直接替换。位图和painter按URI/修改时间/大小隔离，避免跨照片沿用。无新的淡入或灯光改动。

## 产物与验证

- `APK/Punctum-0.7.0-90.apk`及正式别名`APK/Punctum-0.7.0-release.apk`，40,161,692字节。
- SHA256：`07c857712127c2aba044fd94e924c120c54a22956355f8b377c142a8829ffb33`。本轮重新构建字节哈希不变；/77归档为`APK/Punctum-0.7.0-77.apk`。
- 本轮Release/Lint/60项Release JVM单测/签名通过，0测试失败；Lint为0错误/17既有警告。
- 诺贝尔PJW110，serial `RCI7IVR4JJJBMZLV`：/90已保留数据安装，安装包哈希核对一致；有旧封面回归与高清替换日志/截图。四张临时测试照片已清理，恢复15张原照片，最后停留胶片首页。
- 用户PMA110，serial `3B164900L2N00000`：/90已保留数据安装，版本和启动正常；未另做完整视觉回归。新任务前重新核对实际USB目标，不对未指定手机写入。
- 私有证据在`docs/verification/2026-11-02`，不入GitHub。该诺贝尔原生screenrecord输出遇过Permission denied；当前验证以截图采样和状态日志交叉核对，不声称逐帧视频或所有图库性能保证。

## 交付边界与接续

本轮只更新GitHub源码/资源/测试/配置/文档；APK/IPA、签名、本机配置和私有媒体不入仓库。本轮不更新Google Drive或NAS；Drive仍为此前/88：https://drive.google.com/file/d/1754TsHIdM708sqvttcGngUXcS9Bq69aM/view 。未明确要求Demo时直接维护正式App，不主动重做Demo或移植iOS。

下一步等待用户体验反馈，不主动升级版本或调参数。有关锁版与过往试验见[10月30日历史交接](SESSION_HANDOFF_2026-10-30.md)，当前以本文件为准。
