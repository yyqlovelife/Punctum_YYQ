# Punctum接续 · 2026-11-03 · 胶片投影交互本地试装

后续缩放候选以[11月4日交接](SESSION_HANDOFF_2026-11-04.md)为准；当前未插手机，仅本地构建/模拟器验证。下方11月3日真机结论属于旧候选。

## 最新本地修复 · PMA110上滑性能与颜色交接 · 2026-11-03

个人PMA110已安装上滑优化包：逐帧变换改在渲染层执行，旧光影退出/新光影接替平滑交接，并在提交前准备好接替原图。相同五次上滑回弹短样本掉帧58.86%→2.51%、P95 89→16ms；接替后的投影区与该照片静止参考逐像素一致。测试标记已取消，原59张保留。包为`APK/Punctum-0.7.0-90-projection-performance.apk`，哈希451a6639…；版本保持0.7.0/90，诺贝尔仍是上一版d32cac5，未同步GitHub/谷歌。详见[性能与颜色验证](docs/ANDROID_FILM_PROJECTION_PERF_VALIDATION_2026-11-03.md)。下方先前设备/包状态按各阶段理解。

当前新增胶片投影跟手横向分页、照片独立上滑及与其他大图页共享的删除提示/收拢动效。**0.7.0/90不升号**，仅诺贝尔PJW110保留数据试装，63项测试、Release/Lint/签名和安装包哈希通过。用户已确认首版跟手能力OK，随后恢复边缘点按立即快切并永久隐藏胶片上滑提示文字；删除圆钮及动效保留，新补充已在诺贝尔验证。不要自行改回旧位移抖动或更改已验收视觉/灯光。

先读[投影交互验证与实现记录](docs/ANDROID_FILM_PROJECTION_GESTURE_VALIDATION_2026-11-03.md)，其他首页、缓存、设备与发布约束沿用[11月2日交接](SESSION_HANDOFF_2026-11-02.md)。真实仓库`/Users/80400763/Documents/Punctum`，不使用L3 Workflow，保留所有当前改动。

试装包`APK/Punctum-0.7.0-90-projection-gestures.apk`，SHA256 `d32cac571094fdbbbb214403addc9c22ab5c0ae57ed9e12cbb64f627e12ce880`。手机已安装相同包，最后位于原15张图集第1张投影页；临时3张手势测试图及图集记录已全部清理，动画设置1.0已恢复，原照片未删除。

本轮源码尚未提交或同步GitHub，未上传Google Drive。GitHub仍为5a0ca2c发布基线，云端仍/88。个人PMA110（串号已省略）现已保留数据安装最新快切/无提示包，0.7.0/90，启动正常，手机包哈希d32cac571094fdbbbb214403addc9c22ab5c0ae57ed9e12cbb64f627e12ce880与诺贝尔验收包一致；未在个人手机重复全部手势回归。此前`APK/Punctum-0.7.0-90.apk`与release别名仍是旧交互包（哈希07c857…）；与同版本号新试装包必须按文件名/哈希区分。

后续补充：边缘点按scrollToPage立即切图，手指拖动仍分页；上滑toastVisible=false。专项Release/Lint/签名与手机安装哈希通过，原照片未删除，手机仍停在第1张投影页。首版96a6885包归档before-quickcut文件。

本地改动包括FilmReviewScreen、FilmReviewLayout方向锁、共享SwipeDeleteMotion、DetailScreen复用提取及新测试/文档。原图集内的投影比例、揭片、信息、手动灯光曲线、首页字体/布局/纸纹、旧首页回退、刷新与缩略图防闪逻辑保持。
