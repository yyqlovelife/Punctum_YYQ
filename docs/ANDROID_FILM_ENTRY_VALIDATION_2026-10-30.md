# Android 胶片入场与首页顶部修复 · 2026-10-30

## 0.6.0/71

用户视频 Record_2026-10-30-11-05-55_b783bf344239542886fee7b.mp4 展示：首页在隐藏及恢复状态栏时顶部间距改变，进入后暗停较久。

- SwitcherScreen 将 statusBarsPadding 改为 windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)，首页继续挂载时顶部位置保持不变。
- /70 的等待是页面解码且入场结束后 900ms；已有入场 280ms，额外动效时间合计约 1180ms，加载另计。
- /71 的等待为 150ms；从参考时间轴 750ms 开始，开灯仍在参考帧 900ms，保留其后原有节奏。播放持续时间从 4100ms 改为 3350ms，仅去掉前 750ms 恒定暗态，额外动效时间约 430ms，加载另计。触感同步改为入场就位后 150ms。
- 原有减少动态效果、退出取消协程、4:3 裁切、抗锯齿及原色终态保留。

## 构建与安装

49 项 JVM 测试 0 失败；新增测试覆盖 150ms 开灯及恢复曲线时间映射不变。assembleRelease、lintRelease 成功，0 错误、17 条既有警告、8 条信息。APK v2 签名通过。

APK/Punctum-0.6.0-film-entry-fix.apk，已保留数据安装当前连接 PMA110（3B164900L2N00000），设备 versionCode=71 / versionName=0.6.0。拉回 base.apk 与本地 APK SHA256 一致：

cd2b48545257e9c3223d87e13c79e0d49d92e839be70ce27df0e3dbcba1809fc

## 实机证据

私有证据路径 docs/verification/2026-10-30/film-entry-fix/。

- entry-exit-71.mp4、contact-71.png：录制一次进入、开灯与退出，目视确认顶部内容未出现原片中的跳动，暗停明显缩短。
- home-71-before.xml 与 home-71-after.xml：顶部 PUNCTUM 行均为 [96,324][806,372]，Your Punctums 均为 [96,504][1077,640]，Camera 和风光信仰坐标也完全一致。
- home-71-before.png 与 home-71-after.png：进出首页截图。参考视频分解为 reference-contact.png。
- installed.apk：设备拉回安装包，仅本地验证，不上传。AndroidRuntime 检查未发现本轮崩溃。
- 录屏可变帧率，不作为严格的帧率或精确输入延迟测量；150ms 与 280ms 由代码及时间映射测试确认。

动效复核：无新增布局动画；固定顶部空间，缩短无反馈等待；开灯后的原有亮度曲线及可中断生命周期保持。通过。

本轮未提交或上传 GitHub/NAS，iOS 未改。手机停在首页供用户重入体验。
