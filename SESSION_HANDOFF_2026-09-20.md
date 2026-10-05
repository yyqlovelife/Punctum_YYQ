# 观止新对话接续 · 2026-09-20

## 近期维护同步 · 2026-10-05（版本不变）

Android 保持 **0.5.8/58**，iOS 保持 **0.5.9/59**。本次同步近期修复及说明，不升版本。

- Android 中文地名：请求并保留简体中文地名；照片位置权限后来获准时，旧空 GPS 缓存可重新读取。文件本身缺少 GPS 时仍无法补出坐标，中文地名服务覆盖率继续观察。
- Android 大图版式：已撤回去除 `No.xx` 的试验，保留用户接受的原编号、字号与时间地点位置。
- Android 拍摄时间：所有来源统一优先文件原始 EXIF 时间，大图显示、列表倒序、首页封面及时间跨度一致；旧缓存核验后展示。Phocus 500 张、DJI Album 255 张在 PMA110 上逐张通过，修复后时间不匹配与列表逆序均为 0；23 项单测、Release、Lint、签名通过。
- 当前 APK `APK/Punctum-0.5.8-original-capture-time.apk` 已保留数据覆盖安装 PMA110，手机与本地 SHA256 同为 `4682d62e3c5da7978cc2c4f0af7b061a68b382367cb377e6c6cd35c8325dc715`，已上传 Google Drive。
- iOS 延续 9 月 25 日状态：43 项 XCTest/Release 通过；首页冷启动滑动已获用户确认，最新封面、进入与返回定位仍待 iPhone 真机复测。本轮未改 iOS 代码。

详见 [新对话交接](SESSION_HANDOFF_2026-10-05.md)、[开发交接](PUNCTUM_HANDOFF.md)及 [Android 真机摘要](docs/ANDROID_CAPTURE_TIME_VALIDATION_2026-10-05.md)。源码、测试和文档同步 GitHub；安装包、签名资料、原始照片元数据与设备抓取留在本地/Drive。

## 2026-09-25 接续更新

今天的完整增量及最新验收状态见 [PUNCTUM_HANDOFF.md](PUNCTUM_HANDOFF.md)、[Android 更新说明](changelog/android.md)、[iOS 更新说明](changelog/ios.md)。iOS 0.5.9/59 包含冷启动首页后台刷新、封面恢复、进入图集后台加载及大图返回列表定位；43 项 XCTest 与 Release 构建通过，冷启动滑动已获用户确认，其他最新交互待 iPhone 真机复测。Android 0.5.8/58 包含系统相册调整拍摄时间后的排序稳定性修复；19 项单测、Release、Lint 与签名验证通过，用户远程安装后反馈目前状态不错。最新本地包分别是 `iOS/IPA/Punctum-0.5.9-return-center-unsigned.ipa` 和 `APK/Punctum-0.5.8-sort-stability.apk`；安装包不进入 GitHub。

## 当前发布与接续 · 2026-09-20

源码与文档已上传GitHub main，首个发布提交 `7d8e74580d9d73f1545e381254923d4bfda93db5`；远端文件树已与本地逐项核对一致。iOS 0.5.9/59重新运行41项XCTest全部通过。此记录取代下方历史“未提交上传”状态。

iOS 0.5.9安装包SHA256：`a55562270301e7b8e61acd10890c7a072667d1510ad63a9b1f396f1c9bc27c2e`，Release与包内版本资源核对通过。

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


## 先读这里

- 真实源码：`/Users/80400763/Documents/Punctum`。聊天目录 `/Users/80400763/Documents/ChatGPT/Punctum-OPPO` 仅放交接副本，不要在旧副本里开发。
- 直接在仓库工作，禁止使用 L3 Workflow。保留当前全部未提交修改和新增文件。
- 两端版本均为 **0.5.8 / 58**；当前 HEAD 为 `4fea135`。本轮增量尚未提交、推送或发布，不要把旧远端版本当成包含本轮修改。
- 最新用户请求是更新文档后换新对话。没有新功能待实现；优先接收最新安卓闪动修复的真机反馈。
- 本文为当前状态入口；其他文档的旧日期章节保留历史，旧章节中的“最新包”“Android 未改”等仅适用于当次记录。

## 最新交付

| 平台 | 最新包（相对真实仓库） | 验证与安装 |
|---|---|---|
| Android | `APK/Punctum-0.5.8-return-flash-fix.apk` | 6项JVM位置测试通过，Release及签名验证通过；9月19日已通过 `adb install -r` 成功覆盖安装到 PMX110，保留数据 |
| iOS | `iOS/IPA/Punctum-0.5.8-pagination-fix-unsigned.ipa` | 41项XCTest通过，Release、ZIP和版本检查通过；沿用用户现有AltStore身份签名覆盖安装，最新包真机验收未确认 |

9月20日重新核对文件SHA256：
- Android：`04b25fddbc3d521fbd67200dcab0ff34b0317924f5d112e66f487b028d4d36da`
- iOS：`e53c5526ab35a61ded34805c0f06c395f22eb685342d3e54ab97e9cd11d71404`

旧的comparison、batch-delete-fix、detail-return-fix包保留为历史产物，继续试用应取上表最新包。本次只整理文档，没有重新构建或安装。

## Android：返回位置及一帧闪动

1. 用户规则：普通浏览和删除后，退出大图都要回到最后查看照片对应的列表位置。
2. 已实现按照片URI记录当前位置，按双列照片行加顶部header偏移定位。确认删除、取消及失败恢复导致行号变化时，仍按同一URI重算。当前照片删除时用下一张，末张用上一张，空图集不保留锚点。
3. 用户已明确确认“这个逻辑是实现了”，随后报告返回瞬间闪一帧；这是定位规则的用户确认，不代表所有删除分支均单独实测通过。
4. 最新修复把返回分为两步：保留大图遮挡→底层列表scrollToItem→等待一帧→finishDetailReturn关闭大图并弹待删除确认。旧实现提前把selectedIndex置空，可能先露出旧列表位置。
5. 用户主动拖动列表后清除定位锚点，避免反复拉回；重复返回请求合并，换图集和重新进入大图时清理旧请求。
6. 最新闪动包安装成功，但尚未收到“闪动消失”的用户反馈。不能把6项位置单测说成帧级动效验收。

代码入口：
- `app/src/main/java/com/punctum/gallery/GalleryReturnPosition.kt`：照片身份、回退及行号计算。
- `GalleryViewModel.kt`：recordDetailPhoto、closeDetail、detailReturnPending、finishDetailReturn。
- `MainActivity.kt`：底层列表定位与关闭顺序、手动拖动取消锚点。
- `ui/DetailScreen.kt`：SideEffect同步已显示照片；用户原有翻页/删除手势保留。
- `app/src/test/java/com/punctum/gallery/GalleryReturnPositionTest.kt`：6项回归。JUnit依赖已加入app/build.gradle.kts。

接下来首先让用户测试：普通右翻后返回、边看边删后返回、确认/取消删除、末张删除；观察旧位置闪现和缩略图短暂空白。如仍闪，先区分列表布局跳变、图片加载和状态栏恢复，避免盲目延长动画。

## iOS：本轮累计变更

- 大图1–5倍保留缩放，松手保留，单/双指平移，双击放大区域或捏回1倍复原。
- 对比模式：系统单选照片；两竖图左右排，其余上下排；独立/联动缩放平移；确认删除和对应返回；统一下沉反馈。详见COMPARISON_HANDOFF.md。
- 批量删除后列表卡顿：合并刷新，概览分批让出主线程，排序和索引写盘后台执行，首页封面延后生成，取消列表出现时批量预读原图元数据。用户报告过卡死/黑屏，未取得该手机诊断日志，黑屏根因和修复效果未完成真机验收。
- 连续翻页接续：旧判断混用了visiblePhotos索引和未过滤的photos.count；80张标删4张就可能达不到续载门槛。现用DetailPagination统一检查初入、翻页、删除、追加及手势完成，空批次继续加载，耗尽才退出。
- 41项测试包含6项分页策略回归，覆盖300张跨批模拟；不能宣称真机已连续滑动300张。

关键文件（相对 `iOS/Punctum/Punctum`）：Models/PhotoZoomState.swift、ComparisonState.swift、DetailPagination.swift；Views/DetailScreen.swift、ComparisonScreen.swift、ComparisonImagePane.swift、ComparisonPhotoPicker.swift、LivePhotoViews.swift；ViewModels/GalleryViewModel.swift；Services/PhotoLibraryService.swift、CaptureDateIndex.swift。

对比模式已观察：系统选图、混合布局、独立/联动点击放大、双击复原、应用/系统取消、删新选合成测试图返回原图。仍待：最终图标显示、两竖图/两横图实屏布局、删原图/末张/唯一图、受限照片权限、云端失败重试、真机多指手感。Mac此前自动锁屏中断过界面检查；不要反复重做已完成部分。

保护已确认的iOS原生280ms删除动效、列表稳定与照片原比例。对比模式目前只实现iOS，Android没有移植本轮对比功能。

## 验证和接续命令

Android（真实仓库根目录）：
```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest assembleRelease
/Users/80400763/Library/Android/sdk/platform-tools/adb devices
```
设备上次为PMX110 / `3B167100EXP00000`，新会话必须重新查连接，不能假定一直在线。使用覆盖安装，不卸载清数据。

iOS：
```sh
xcodegen generate --spec iOS/Punctum/project.yml
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild -project iOS/Punctum/Punctum.xcodeproj -scheme Punctum -configuration Debug -destination 'platform=iOS Simulator,id=FAE0B8A7-B631-4AEA-AE84-133224A0A1E2' -derivedDataPath /tmp/punctum-compare-derived CODE_SIGNING_ALLOWED=NO test
iOS/Punctum/scripts/build-unsigned-ipa.sh
```
模拟器ID须先确认仍存在。同一DerivedData不要并发构建。脚本产出通用unsigned包，交付时复制到明确命名的任务包。

构建证据已复制到 `docs/verification/2026-09-20/`：安卓最终构建日志及6项JUnit结果、iOS最终41项测试及Release日志。原始/tmp路径可能被清理，以仓库内归档为接续依据。

## 文档索引

- PUNCTUM_HANDOFF.md：总体交接及历史变更。
- COMPARISON_HANDOFF.md：iOS对比实现与未验收项。
- CHANGELOG.md、changelog/android.md、changelog/ios.md：总日志与分平台日志。
- design-qa.md：已验证范围及历史试验。
- README.md、iOS/Punctum/README.md：说明入口。
- PRD/PRD-Punctum观止.md：产品规则；实现和验收状态以本交接为准。
