# 宽厚衬线凹印试版 · 2026-10-31 · Android 0.7.0/83

## 最终选择

用户要求寻找更接近ONYX凹印参考的字体，随后明确：先用免费近似字体试装；重点是宽字面、粗笔画，让凹面有面积，避免窄薄字体。本轮最终选Bodoni Moda，标题Black 900，小字SemiBold 600；中文标题保持思源宋体家族并加重到SemiBold，中文小字维持Normal。未改变标题文字内容或大小写。

字体研究依据：
- ONYX官方设计系统https://onyxcoffeelab.com/pages/da-compendium 明确其Display字体为Room-205；不能据此声称用户照片的每个定制字形已获精确识别。
- 作者Room 205销售页面列有付费App授权，用户选择先做免费近似款，本轮没有购买或嵌入Room 205，也没有从品牌网站提取字体文件。
- 免费候选比较了Averia Serif Libre、Cormorant、Cormorant Garamond、Bodoni Moda及粗字重；结合用户追加的宽厚要求，选用Bodoni Moda Black来保留大块压印凹面。
- 官方开源项目https://github.com/indestructible-type/Bodoni，源变量文件取自google/fonts/ofl/bodonimoda。SIL OFL文本同时内置APK assets及docs/licenses。
- 使用官方变量字体实例化静态字体：标题wght=900/opsz=72，小字wght=600/opsz=16；FontTools读回OS/2字重900、600。仅新增两个实例，原有Averia和既有字体保留。

Adobe Fonts检索初始化返回未授权，未提供Adobe目录结果；实际字体研究与获取使用品牌官网、字体作者及Google Fonts源文件。

## 实施与验证

只修改胶片新首页纸栏字形和字重，纸纹、字号、凹印绘制参数、固定174/175px间距、白栏116px和图集内灯光/投影保持。构建Release、Lint通过，0错误、17条既有警告；签名验证通过。

诺贝尔PJW110（RCI7IVR4JJJBMZLV）保留数据安装0.7.0/83。三卷区域仍为[0,653][1080,1123]、[0,1297][1080,1767]、[0,1942][1080,2412]，第三纸栏仍贴物理底边。真实截图检查宽厚标题压印、日期和英文数量完整显示。

四次滚动156帧，1帧jank，P95 11ms，0次slow bitmap upload；仅为该机该短时样本。

为三卷截图临时建立Preview 02/03、复制8张既有测试照片，截图后已清理；原测试图集15张、Camera3张保留。实机截图和日志位于docs/verification/2026-10-31/home120-bold-serif/，不进Git。

本地APK：APK/Punctum-0.7.0-home120-83.apk。交付包与手机独立核对的安装包SHA256一致：9c595b95a08f38e0de3836ea280fcde9350ce7fd650742d86d3ab5231214dbbe。

本轮未上传Google Drive、GitHub或飞牛。等待用户视觉验收；/77仍为此前已验收锁版。
