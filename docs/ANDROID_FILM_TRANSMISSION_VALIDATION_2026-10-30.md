# 胶片透光曝光 · Android 0.6.0/75

## 后续加强 · 0.6.0/76

用户认可/75后，照片峰值增益调至3.0、原图保留8%，约比上版增加一档曝光。58项单测、Release、Lint、签名通过，保留数据安装PMA110，拉回安装包哈希一致：`e2482677427964627fad50126ea84a2c3178d33d3b6d5fcef25c8b4c0cb2c1b4`。实机录屏确认亮部更强、人物和暗部轮廓仍可见；稳定胶片区域与/75逐像素一致。证据在 `docs/verification/2026-10-30/transmission76/`。APK为 `APK/Punctum-0.6.0-film120-transmission-76.apk`。以下保留/75原理与验证历史。

## 原理与实现

Kodak《Basic Photographic Sensitometry Workbook》给出密度与透射率关系 D = log10(1/T)。透射光强随入射光强与透射率相乘；正片浓密的暗部透光少。参考录影同时受拍摄相机曝光、饱和及白平衡影响，不能将整段画面变白直接当成胶片真实透光变化。

原实现把逐帧拟合的 bias 直接加到照片上，峰值接近253，几乎所有像素一起变白。本次保留底板与黑边测量曲线，照片改为独立乘法曝光近似：点亮后零加白，编码RGB增益峰值2.2，截断后混入12%原图，再从参考1.1秒到3.5秒平滑恢复到增益1、原图100%。约相当于gamma2.2近似下的+2.5档峰值；这是适配普通缩略图的视觉近似，未声称完整光谱或精确sRGB线性光仿真。

黑位保持0；源24的暗部在峰值约49，源180的亮部约246。保留少量原图可避免超过截断阈值的亮处完全丢失层次。实际任意照片仍可能有大片白色区域，源图本来全白的部分不会凭空出现细节。

使用既有原生 ColorMatrixColorFilter 与位图绘制，兼容API26起，无图像识别、CPU逐像素扫描、直方图、额外位图或新shader。时间帧一次预计算，动画只读绘制状态；复用既有两次绘制路径。关闭系统动画分支保留。

参考：

- https://www.kodak.com/content/products-brochures/Film/Basic-Photographic-Sensitometry-Workbook.pdf
- https://developer.android.com/reference/android/graphics/ColorMatrix

## 构建与设备

58项JVM测试通过，新增不同灰阶在峰值保留层次及回落单调连续性验证。Release、Lint和签名验证通过。PMA110（3B164900L2N00000）保留数据覆盖安装，设备版本0.6.0/75。

APK：`APK/Punctum-0.6.0-film120-transmission.apk`。本地与拉回安装包SHA256一致：

`01c312321ce72a2d79e65100dfdffecf1fe753da7a0ab17ba358aafa334eeb1c`

## 实机证据

私有目录 `docs/verification/2026-10-30/transmission/`。

- `dark.png`、`light.mp4`、`peak.png`、`lit.png`、`comparison.png`：六张不同画面的峰值仍能看清暗处车身、人物、木格和店面；亮处先溢出。底板保持253峰值和232/232/230稳定值，黑边仍存在。
- `metrics.json`：六张内部抽样接近白色（三通道均大于245）的像素占比约0.9%–31.5%，随源图变化，未整体白化。录屏压缩及缩放存在误差，仅作诊断。
- 连续开关、中途关闭再开启正常。`after-interruption.png`的胶片区域与原稳定截图逐像素相同。
- 将系统动画倍率设0再开灯，`animations-disabled.png`胶片区域与原稳定截图一致；倍率已恢复1.0。
- 不录屏的单次开灯采样：81帧，6帧被系统标为janky，P50 12ms、P90 13ms、P95 14ms、P99 20ms，GPU P95 7ms，无missed vsync、无slow bitmap upload。录屏额外负载明显增大jank，相关原始统计均保留。有限单机采样仍有少量掉帧，不能据此承诺所有设备零掉帧。

## 动效复核

照片变亮时仍有图像结构；峰值后单向回落，暗态、手动点亮、黑边、底板、中性终态及系统减少动画行为保留。未改选片、投影与删除逻辑。未执行真实删除。仅更新正式App，Demo未改、未重新发布；未上传GitHub/NAS，iOS未改。
