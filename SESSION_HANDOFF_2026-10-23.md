# Punctum 新对话交接 · 2026-10-23

## 10 月 23 日接续：GitHub 同步

用户已明确要求继续完成 GitHub 上传。本次同步目标为 Android **0.6.0/63** 的全部现有源码和文档，保留 iOS **0.5.10/60**，不操作手机或 NAS。下文“尚未提交或上传”“先等待用户新任务”“不提交/推送”均为本文件初始交接时的历史状态，本次上传授权已替代该项等待约定。远端完成情况以本次接续的远端 ref 和完整树核对记录为准。

## 先读结论

真实源码在 `/Users/80400763/Documents/Punctum`。本地 Android 最新 **0.6.0 / versionCode 63**，包含列表长按立即确认、媒体管理授权和取消后删除角标清理；这两轮修复已装 PMA110，但**尚未提交或上传 GitHub**。GitHub main 仍是 **0.6.0/61**。iOS 保持 **0.5.10/60**，本轮未移植新选片台。

用户最后要求制作交接、换新对话。最后一项产品修复已经完成验证，没有收到新的功能指令；先等待用户新任务。不要主动重做首页、移植 iOS、上传 GitHub、上传 NAS 或操作新手机。

本交接已于 2026-10-23 实时核对本地版本、源码关键值、APK 哈希、GitHub main 和 USB 设备清单；未重新构建、运行应用测试或安装手机。下列测试和安装记录发生在 10 月 22 日。

## 工作目录与 Git 状态

- 实际仓库：`/Users/80400763/Documents/Punctum`。
- 本聊天目录 `/Users/80400763/Documents/ChatGPT/Punctum-OPPO` 只放交接副本，不在旧源码副本里开发。
- 当前分支：`codex/android-0.6.0-release`。
- 本地 HEAD 与本次查询的 GitHub main：`8cac0563b70f179d2cd5e7f5b787decd53fd8dd3`。
- 该远端提交对应 0.6.0/61 的源码、README、日志和交接，已核对 191 个文件完整一致。发布源提交为 `929f92e7dde89e8057ac0eb153ce7aed24dbb33e`；之后是发布核验文档补充提交。
- 0.6.0/62、/63 的源码及文档仍是工作区未提交改动，必须全部保留。不要 reset/clean/覆盖、误把 origin/main 当作最新修复。
- 未提交源码包括版本配置、`GalleryViewModel.kt`、`MainActivity.kt`、`PhotoRepository.kt`、`FilmReviewScreen.kt`、`GalleryScreen.kt`；还有 README、更新日志、交接文档，以及新增 `docs/ANDROID_LIST_DELETE_CONFIRM_VALIDATION_2026-10-22.md`。以实时 `git status` 为完整清单；本交接自身及入口指针更新也是未提交文档。

## 安装包、手机与验证

最新本地 APK：`APK/Punctum-0.6.0-delete-badge-reset.apk`。

SHA256：`a4cc36f00c861c89f7758512324eb4c1d00ab0295f752845008ba06568cf842e`。

0.6.0/63 已保留应用数据安装到 **PMA110，序列号 `3B164900L2N00000`**；从手机拉回 base.apk 与本地哈希一致。此前主手机 `3B15CV002XY00000` 也为 PMA110，别把两台混为一台。

**本次只读设备清单发现当前 USB 变成 PJW110，序列号 `RCI7IVR4JJJBMZLV`。未检查该机安装版本，未给这台新机安装，不能声称当前连接手机已有 0.6.0/63。** 下次需要操作手机时重新确认目标设备，不复用旧脚本里的固定序列号。

0.6.0/63 的 Release、APK 签名、Lint（0 错误、17 条既有警告）、44 项 JVM 单测通过。明信片、票据两个模式各连续做两次“长按完成 → 取消”，并各做一次 400ms 提前松手：照片保留、删除角标无残留。没有再次真正删除用户照片；最后把原有明信片风格恢复，停在 Camera 普通列表。

## 删除交互：以这套最终规则为准

### 所有模式的列表长按

明信片、票据普通列表，以及反转胶片选片台：按住 200ms 后显示进度，再持续 500ms 完成长按，**立即弹观止自己的确认窗**。

- 标题：“确定删除这张照片？”
- 描述：“被删除的项目将会进入系统相册回收站”
- 按钮：“取消”／“确定删除”
- 确认前不能移走照片或切换当前投影；取消后原照片、投影不变。
- 胶片列表长按不再加入退出时统一确认的队列。
- 普通列表进度角标必须在打开弹窗前隐藏，并在手势 `finally` 中无条件清理。此前按长按是否已完成跳过清理，导致取消后角标挂住，0.6.0/63 已修好。
- 胶片删除图标位于整张胶片右上角；用户已撤销整片暗色蒙层，别加回来。

### 投影区上滑、旧大图取舍

仍保留暂存待删除、退出统一确认的原有逻辑。这与列表长按立即确认分开；不要依据早期“所有删除都在退出确认”的历史日志回退新规则。

### 系统二次确认与媒体管理权限

0.6.0/62 调整 Android 12+ 流程：没有 `MANAGE_MEDIA` 时先走一次媒体管理设置授权，授权后执行回收站操作，避免每次再弹系统 Google 删除确认。PMA110（L2N00000）上的该设置已确认开启；**不能假设新接 PJW110 同样已授权**。

已用独立生成的临时 JPEG 实测：只点一次观止“确定删除”，界面没有第二个系统弹窗，MediaStore 查询 `is_trashed=1`，确实进入系统回收站。测试后只清理这张临时测试图，用户原照片未实际删除。权限拒绝或失败需要恢复照片，不能假成功。Android 11 等不支持该特殊权限的系统仍需服从平台强制确认，不声称所有系统都能去掉原生授权。

## 反转胶片改版的最终设计

- 仅反转胶片模式进入图集后使用一体页：上方固定高度幕布，下方 3 列 × 2 行、每页六片的横向分页选片台。普通明信片/票据保留旧列表与大图关系。
- 幕布有黑灰织纹、褶皱与晕影，照片不加白边；默认不选片。
- 空态中央原句：“选择一张胶片，重映一段时光”。思源宋体 12sp，字距 0.7sp，Bone 透明度 **22%**。每次空态显示，投影后隐藏。
- 点选胶片开片、亮起与轻展开：**1200ms / 1.2 秒**，用户已确认；初选/重选一致。左右边缘点按、投影区横滑快速切换，保持直接换图。系统禁用动画时尊重设置。
- 避免逐帧整页重组：动画 State 在绘制/图层阶段读取，缓存幕布/光层和画笔，保留已有性能修复。
- 图片完整保留原比例；横图与 EXIF 左右同 **24dp** 边距。竖图使用独立高度预算；默认上下居中。
- EXIF 默认关闭，底部页码旁小「i」控制，开启后跨换片常驻；文字组在图像底边与幕布底边之间垂直居中。需要让位时中心最多抬升 **18dp**，其余等比缩小，勿大幅上移。
- 白色发光选片台延伸至底部导航区域，控件按导航条避让；PUNCTUM 白色胶片框、编号、照片原比例、间隙和轻微角度保留。
- 图集名称 18sp 与精简年月/数量居中，左返回、右更多。切拍摄/编辑排序后菜单保持展开，重命名仍关闭菜单再打开改名框。
- 首页四张胶片叠片、黑色片基/金字方案只做了设计探索，用户说“先不改首页”，代码没有采用。不要拿生成稿当现状或继续实现。
- 新选片台暂未接入旧大图的缩放/Motion Photo 播放入口；iOS 未移植本次界面。

## 源码入口

| 文件 | 职责／保护项 |
|---|---|
| `app/build.gradle.kts` | `versionName=0.6.0`、`versionCode=63` |
| `GalleryViewModel.kt` | `pendingListDeleteConfirmation`、`deletePhoto`、`confirmListDeletion`、`cancelListDeletion`；与批量退出确认分开 |
| `MainActivity.kt` | 模式路由；列表与批量共用确认窗的呈现；媒体权限返回链路 |
| `data/PhotoRepository.kt` | 真正移入回收站、特殊权限请求结果、失败恢复；不得改成直接永久删除来躲弹窗 |
| `ui/GalleryScreen.kt` | 明信片/票据普通列表长按进度与 finally 清理，共用 `DeleteProgressBadge` |
| `ui/FilmReviewScreen.kt` | 一体界面、`onRequestListDelete`、1200ms 投影、空态文案、EXIF 与选片手势 |
| `model/FilmReviewLayout.kt` | 六片分页、比例、24dp 边距、18dp 避让、手势判定与替代照片身份 |
| `model/FilmReviewLayoutTest.kt` | 画幅、避让、日期、手势和删除替代顺序测试 |

上表 Kotlin 路径分别相对 `app/src/main/java/com/punctum/gallery/`，测试相对 `app/src/test/java/com/punctum/gallery/`。

## 构建与记录

在真实仓库运行：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleRelease :app:lintRelease :app:testDebugUnitTest
```

ADB：`/Users/80400763/Library/Android/sdk/platform-tools/adb`。安装需明确 `-s` 选择实时设备、使用 `install -r` 保留数据；不要清数据。签名需要本机 `punctum-release.jks` 与 `local.properties`，不把凭据写入交接或上传。

关键记录：

- `docs/ANDROID_LIST_DELETE_CONFIRM_VALIDATION_2026-10-22.md`：0.6.0/62 和 /63 的删除及角标修复。
- `docs/ANDROID_FILM_REVIEW_VALIDATION_2026-10-22.md`：选片台迭代，旧数值和旧删除规则按历史理解。
- `docs/RELEASE_VALIDATION_2026-10-22.md`：已上传 GitHub 的 0.6.0/61 发布核验。
- `changelog/android.md`、`CHANGELOG.md`：最新本地日志。
- `docs/verification/2026-10-22/delete-badge-reset/`：/63 私有实机截图和已安装 APK。
- `docs/verification/2026-10-22/list-delete-confirm/`：/62 私有删除确认与临时图测试资料。

这次只写交接与更新入口，不重新运行测试或改产品代码，不提交/推送 GitHub，不安装或操作当前 PJW110。

## 接续约定

保留全部工作区修改；不使用 L3 Workflow。答复先简洁说结论，避免用户禁止的对比句式。做设计稿时用 GPT Image；制作 demo 遵循先可点击手机比例网页、确认后打包的用户规则。

当前交付优先用户连接手机，不主动上传 NAS。未来用户明确要求放到“APK 目录”，目标为 `/vol1/1000/临时共享/APK`，远程域名 `https://yyq666.fnos.net`；已约定完成的 iOS IPA 自动上传并核验。不要把 NAS 密码放进普通交接或仓库。GitHub 上传源码/资源/配置/测试/文档，APK/IPA、签名材料、机器配置和用户原始媒体/设备日志继续忽略。常规 Git push 若报 curl 55 或输出误导的 Everything up-to-date，查远端 ref；必要时用 Git Data API 非强制更新，再逐项核对远端树。
