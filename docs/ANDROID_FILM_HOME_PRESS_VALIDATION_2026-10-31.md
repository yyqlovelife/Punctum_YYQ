# 胶片首页下沉按压反馈 · 2026-10-31 · Android 0.7.0/86

用户要求胶片新首页点击时与明信片、票根具有相同的下沉动效。将整卷胶片与其纸质信息栏的普通clickable替换为现有punctumPressable；卷间黑色间距不参与缩放。复用相同参数：scale=0.985、下移4dp、alpha=0.96，松开立即进入图集；不等待回弹结束。

## 动效复核

| Before | After | Why |
| --- | --- | --- |
| 新胶片首页只有普通点击反馈 | 复用明信片和票根的punctumPressable及同一组参数 | 为点击提供轻微物理反馈，三种首页保持一致 |

**Approve。** 目的为Feedback，按图集进入频率采用轻微反馈。使用Compose现有Animatable和graphicsLayer，仅改变scale、translationY、alpha；无新依赖、无逐帧布局或图片解码。按下110ms LinearOutSlowInEasing、松开spring(0.72/900)、取消spring(0.76/900)均复用Components.kt:71起的现有实现，不另建曲线。FilmStripHome.kt:107–114为新调用点。无重复clickable处理器，图集URI保持原回调；原生语义提供Button/点击动作。

## 诺贝尔实测

- PJW110 / RCI7IVR4JJJBMZLV，保留数据安装0.7.0/86；Release、Lint和签名验证通过。
- 手指按住后截图看到缩小、下沉和微暗：左边纸栏采样由(252,252,252)变成底板(10,10,10)，证明整体缩进；纸栏纵向采样从1007–1128变为1018–1136。
- 拖出卡片再松开，仍留在首页；卡片区域[0,653][1080,1129]与按下前逐像素一致，无残留下沉或变暗。
- 正常松开立即进入图集，空状态“观止，其止于观。”保留；返回首页正常。
- 将animator_duration_scale临时设0，点击仍正常进入图集；结束后严格恢复原值null（删除临时设置）。
- 录屏尝试因设备写入权限受限未成功，证据使用真实按下、复位和打开图集截图及UI树；未把静态截图称作录屏。
- 证据位于docs/verification/2026-10-31/home120-press86/，Git忽略。未增删照片或图集。

本地APK：APK/Punctum-0.7.0-86.apk，SHA256 c1e07ef571c26233a32b0d0c3f9fc1c03ff656a3ef0f0fc3f3f3998cc06a9b28，与独立读取的手机安装包一致。首页字体、黑灰文字、纸纹、122px白栏与固定165/166px间距保持。

本轮未上传Google Drive或GitHub；云端仍为上轮已交付/85。等待用户体验反馈。

## 后续Google Drive交付

用户随后明确指示上传，已完成/86 APK上传。独立读取云端元数据，名称Punctum-0.7.0-86.apk、Android APK MIME和40,161,692字节大小一致。链接：https://drive.google.com/file/d/1r05LzQmDZqAV7iF8uYQymX9j2WcZiO8R/view?usp=drivesdk 。没有变更分享权限，未声称云端哈希比对。新源码仍未提交上传GitHub。
