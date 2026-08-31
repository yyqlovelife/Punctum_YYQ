# iOS 更新说明

## 0.5.3 · 2026-08-31

本版正式收录 8 月 30 日下午到傍晚完成的真机性能、长按删除、实况播放和上滑删除调整，并与 Android 对齐为 `0.5.3`（build `53`）。本地未签名构建输出为 `iOS/IPA/Punctum-0.5.3-unsigned.ipa`，安装包不纳入 Git，真机安装前仍需签名。

### 首页中文标题

- 封面中文用 **Noto SemiBold** 的 `UILabel`（`GalleryTitleLabel` / `LockedFontLabel` / `CenteredTitleHost`），英文用 Newsreader SemiBold。不要走 SwiftUI `Button` 默认合成加粗。
- **禁止描边。** 加粗两号时只改字重，不要描边充粗。
- 胶片中文标题在槽内垂直居中，再上移约 `pointSize * 0.1` 做光学对齐；不要再给中文加 `offset(y: 3)`。英文胶片标题仍保留 `offset(y: 3 * visualScale)`。
- 图集选择列表的中文和数字不要被系统加粗；用锁定字重的 Label。

### 大图集列表性能

- 进画廊先取 **80** 张（`galleryPageCount`），滑到底再 `loadMorePhotos`。不要一进来就扫完 Recents 里几万张。
- 缩略图长边 360、`skipDegraded: false`，`PhotoThumbnailCache` 按 id + size 缓存。
- 列表不要对每张图查 `PHAssetResource`。真机滑动已确认明显好转。

### 列表长按删除

- 根因：以前长按要等滑动失败，圈和系统删除框几乎同时出现；SwiftUI 小圈还会被透明 UIView 挡住。
- 现为 `GridPressCatcher` + UIKit `DeleteBadgeView`，圈画在手势层右上角。preview 约 0.22s、commit 约 0.7s，`allowableMovement = 8`。
- 用 `shouldBeRequiredToFailBy` 让滑动优先，不要再 `shouldRequireFailureOf pan`。
- **只有单击进大图。** 长按松手（没删成）不能进大图（`ignoreTap` + 约 0.4s）。

### 大图实况

- 角标贴 **原图图像区域右下角**：26pt 图标 + 6pt 边距，热区 **44×44**，不要再扩成 52+12 再 inset。图标 `allowsHitTesting(false)`，点击由整页 catcher 处理。
- 点角标只 `playOnce`，不切图。屏幕左右 25% 切上一张 / 下一张；中间点开关控件。
- 长按：`requestLivePhoto` 用 `.opportunistic`，第一张非空（含 degraded）就用；`PHLivePhotoView` 关掉系统自带长按；开播前 `AVAudioSession` `.playback`。mode 已是 hold/playOnce 时，livePhoto 后到也要自动 `startPlayback`。
- 不要先震再空播：`startPlayback` 在 `livePhoto == nil` 时只记 mode，资源到了再播。

### 大图上滑删除

- **不要**再对整个 `TabView` 做 scale / offset。缩小过程里还能左右翻，删完就会闪。
- 手势成立时 `window.drawHierarchy` 截一张静图，藏起 TabView，只对这张图做缩小推向删除键（参数仍用原来的 `pageScale` / `pageOffset` / `pageRadius` / `pageRotation`）。
- 背后放下一张的静态 `DetailPage`（`pagingEnabled: false`）。松手不删则丢掉截图、恢复 TabView。
- 删除提交时用 `Transaction.disablesAnimations`，先记下下一张 id 再改 `pendingDeletedIDs`。

### 接手注意

- 大图入口：`Views/DetailScreen.swift`、`Views/LivePhotoViews.swift`；列表手势：`Views/GalleryScreen.swift`；分页：`GalleryViewModel.swift` / `PhotoLibraryService.swift`。
- 模拟器没有真机实况时，会写入 LIVE A / LIVE B 合成 Live Photo。真机用系统拍的实况即可。
- Debug 编进模拟器若遇到资源目录问题，可参考：`CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY="-" AD_HOC_CODE_SIGNING_ALLOWED=YES`，以及必要时 `EXCLUDED_SOURCE_FILE_NAMES=Assets.xcassets`。

## 0.5.1 · 2026-08-30

与 Android `0.5.1` 对齐（build `51`）。本版把三态首页、实况照片、图集列表和删除交互收到与安卓同一套用户表现；纸纹与安卓共用 `app/src/main/res/drawable/` 里的 JPEG。

### 明信片

- 底部票尾铺安卓同款牛皮纸 `postcard_footer_paper_texture`（从应用包内 JPG 读取，不再回落到纯色）。
- `PUNCTUMS` 暗纹画在奶油底色之上、封面之下。竖排从下到上为 P→S：P 距牛皮纸上沿约 4pt，S 距明信片上沿约 4pt。
- 票尾文案仍为 `MOMENT · PUNCTUM · STUDIUM` / `TAP TO ENTER EXHIBITION`。

### 首页

- 右上角两个图标按符号墨迹本身排布，中间约 10pt，作为一组靠右。去掉系统按钮内边距和 SF Symbol 空白框，避免细线图标在黑底上看起来离得很开。
- 风格循环：明信片 → 票据 → 反转胶片。纸纹（票据、反转胶片、明信片票尾）与安卓同一批资源。

### 图集列表

- 格子按原图像素宽高比排布，缩略图解码整帧 JPEG/HEIC，不再使用系统方图中心裁切。带白边水印的原图（例如 VIVO 上下白边）在列表里完整可见。
- 长按删除的小圆钮固定在该格右上角。
- 长按后先弹出系统删除确认，列表里这张图仍在；点确定之后才撤掉。取消则列表不变。
- 确认删除后，后面的照片直接补位，不再先闪黑再加载。删掉一张后，紧挨着的下一张宫格也继续显示已有缩略图，不再先黑一下再解码。
- 胶片卡封面的下沉阴影贴在裁切后的四条边上，不再只有左右。

### 移动到图集

- 大图页右上角删除改为「移动到其他图集」。上滑仍可删除。
- 点图标拉起与添加画廊同款的图集列表，单选、点一行即移动。
- 照片加入目标系统图集，并从当前 Punctum 图集映射里拿掉；下一张从右侧滑入，并提示已移动到目标图集。
- 大图页编号、拍摄信息、地点和删除提示字号整体缩小一号。

### 实况照片

- 苹果实况是静止图（JPEG/HEIC）加配对 MOV，用同一 content identifier 绑定，不是安卓 Motion Photo 那种「JPEG 尾部塞 MP4」。
- 大图优先 `PHImageManager.requestLivePhoto`；失败则导出 `.photo` / `.pairedVideo`（含 fullSize 资源）再拼 `PHLivePhoto`。
- 按住约 150ms 循环播放，松手当帧停；角标播一整段。开播 200ms 淡入，停播不淡出。
- 模拟器若还没有实况，会写入两张合成 Live Photo（LIVE A / LIVE B）便于联调。真机用系统拍摄的实况即可；删除 `PHAsset` 会带走静图和视频。

### 图集与封面

- 图集多选添加用户相册及 Recents / 截屏 / 自拍 / 全景。确定后回首页，提示「添加完成」，并滚到新加的第一本。
- 封面按 `creationDate` 最新：票据 / 反转胶片 1 张，明信片 4 张。
- 名称为 Recents 的图集若本地相册 ID 失效，会回退到系统图库（`smartAlbumUserLibrary` / 全部图片），避免列表空掉。

## 0.3.3 · 2026-08-29

iOS 首次与 Android 0.3.3 对齐（build `33`），以 `PHAssetCollection` 映射系统图集。

- 首页按 `creationDate → modificationDate → localIdentifier` 排序；票据最新 1 张，明信片最新 4 张。
- 删除经 `PHAssetChangeRequest.deleteAssets` 进入「最近删除」，并保留约 120 秒本地墓碑防止回弹。
- 票据同步暖白纸纹、主色票根、内嵌相框、齿孔与 `CAPTURE` 编号。
