# Android 0.7.0/90 打包与GitHub同步 · 2026-11-02

## 范围与版本

用户指令：“打包更新GitHub以及各种文档，不更新版本号”。保持versionName **0.7.0**、versionCode **90**，本轮不更改任何产品逻辑或视觉参数。同步此前/77到/90的四图120首页、纸纹与内置字体、英文数量文案、按压反馈、投影精确文案及两项刷新/闪黑修复，保留旧首页与已撤下的字体/凹印试验以便回退。

iOS仍0.5.10/60；本轮只补跨平台文档入口，未改iOS源码或构建。Google Drive仍为/88，本轮不上传；NAS和Demo不在本轮范围。

## 构建与产物

本轮重新执行`:app:assembleRelease :app:lintRelease :app:testReleaseUnitTest`，Release/Lint/60项Release JVM测试通过，0失败/0错误；Lint为0错误/17既有警告。APK签名校验及aapt包版本核对通过，包名com.punctum.gallery，versionName0.7.0/versionCode90。

| 本地产物 | 内容 |
| --- | --- |
| `APK/Punctum-0.7.0-90.apk` | 当前/90，40,161,692字节 |
| `APK/Punctum-0.7.0-release.apk` | 当前/90正式别名，与上行逐字节一致 |
| `APK/Punctum-0.7.0-77.apk` | 保留此前已验收灯光锁版包 |

当前/90 SHA256：`07c857712127c2aba044fd94e924c120c54a22956355f8b377c142a8829ffb33`，重新构建与已试装包一致。/77归档SHA256：`54e8279536f7bb39b75de42e5def24a16bb5d0e0cf5334e4632802da008b36d8`。

## 设备与验收边界

此前同日已保留数据安装/90到诺贝尔PJW110及用户PMA110，并核对版本与启动。诺贝尔安装APK哈希与本地一致，临时测试照片已清理、原图集恢复15张，冷启动进入返回正常。PMA110仅核对安装启动，未声称重复全部诺贝尔用例。本轮发布打包不再重复安装或改动设备数据。

/89旧封面问题已在/88复现并在修复后核对新增/删除/原位替换/重复进入；用户随后确认有效。/90高清替换实测四次Loading仍保留可绘制位图，连续12张截图未见空底；此为截图采样与状态日志交叉验证，没有声称逐帧视频覆盖。详见[旧封面修复](ANDROID_FILM_COVER_FLASH_VALIDATION_2026-11-02.md)与[缩略图修复](ANDROID_FILM_THUMBNAIL_FLASH_VALIDATION_2026-11-02.md)。

## 文档及仓库边界

更新README、CHANGELOG、Android changelog、日志索引、design-qa、总交接与旧交接当前指针，新增11月2日交接及本发布记录；iOS README/changelog补平台状态。同步全部相关源码、字体/纸纹资源、OFL许可、测试与构建配置。

GitHub只保存源码/资源/测试/配置/文档。APK/IPA、签名、本机配置、docs/verification、原始用户照片及录屏不入库。此前标签android-v0.7.0仍指向/77，不移动标签。

## GitHub核验

目标：`yyqlovelife/Punctum_YYQ/main`。发布前远端与本地基线均为`21673dadc91fd5f4653a82c9a0be230323f7cc1e`。常规git push遇到curl55连接中断，使用已认证Git Data API上传43个变更文件并非强制更新main；没有移动原锁版标签。

源码发布提交：[5776b52](https://github.com/yyqlovelife/Punctum_YYQ/commit/5776b52e516d05a786b4a21e922fbc4fee0ae4da)，tree `57a7d5d818592486d82cb3055ed99cf8efda814b`。独立重读main引用及递归文件树（未截断），**236个文件**的路径、类型、模式、blob SHA全部与本地Git树一致，包括新增纸纹PNG及四份字体资源。未将APK/IPA、签名、本机配置和私有验证素材纳入仓库。

本节结果通过后续文档提交补入，main最终包含源码提交及发布核验记录；最终提交后再次完整比对远端树。源码与0.7.0/90 APK保持一致，文档补充不改变应用版本或构建产物。
