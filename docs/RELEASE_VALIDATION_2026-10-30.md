# Android 0.7.0 正式发布 · 2026-10-30

## 锁版与范围

用户认可最终0.6.0/76的120胶片和透光过曝效果，要求增加0.1.0并锁版。正式版本 **0.7.0 / versionCode 77**。仅更新版本与文档；与/76逐项比较APK ZIP内容，唯一发生变化的同名文件是AndroidManifest.xml，代码dex、图片资源和曲线均一致。

本次发布自GitHub旧基线0.6.0/63纳入沉浸布局、稳定首页安全区、投影增高、菜单高位避让、横向手动灯开关、120黑色正方形框与橙字、中心方形裁切、抗锯齿、底板与黑边独立透光、保留暗部的乘法过曝和中性白回落。已验收参数与保护项见 [新对话交接](../SESSION_HANDOFF_2026-10-30.md)。

## 构建、签名与安装

- Release、Lint、58项Release JVM测试通过，0失败；Lint 0错误、17条既有警告、8条信息。
- 正式APK签名验证通过。路径 `APK/Punctum-0.7.0-release.apk`。
- SHA256：`54e8279536f7bb39b75de42e5def24a16bb5d0e0cf5334e4632802da008b36d8`。
- 发布时USB从PMA110换为诺贝尔 **PJW110（RCI7IVR4JJJBMZLV）**；实时核对原安装0.6.0/69，保留应用数据升级至0.7.0/77，正常启动。拉回base.apk与本地正式包哈希一致。
- 在该机已有“Punctum投影测试”图集检查进入保持暗态、手动开灯、120框及方形照片、回落稳定显示。未新增或删除用户照片。

本轮私有截图、UI树和拉回包在 `docs/verification/2026-10-30/release070/`。/76过曝实机证据在同日期 `transmission76/`；用户已确认其观感。仅版本锁定，不重复声称跨设备性能结论。

## 文档与排除项

更新README、CHANGELOG、Android changelog、PUNCTUM_HANDOFF、design-qa；新增2026-10-30接续交接和本发布记录，纳入各轮沉浸/抗锯齿/灯光/120胶片/透光验证文档、模型测试、框图、数字图集和曲线资源。

iOS仍0.5.10/60，本轮未改、未构建。Demo本轮未改、未发布，后续未明确要求Demo时直接改正式App。NAS不在本次发布范围。APK/IPA、签名材料、本机配置、原始照片与录像、docs/verification均不进GitHub。

## GitHub发布核验

目标 `yyqlovelife/Punctum_YYQ` / `main`，发布前远端基线 `5ad81c7f1db4c71d10a0e91ae2e62d52f13c65f9`。常规Git push遭遇curl55连接中断，通过已认证Git Data API上传blob/tree/commit，非强制更新main。

源码与完整文档已发布至 [89831a0](https://github.com/yyqlovelife/Punctum_YYQ/commit/89831a0f18f621f8f6e3ffec66a54c5faa8d72bf)，tree `72db69485e535f790595b133bc3c2d269c822fe0`。独立重查远端main及递归树，**211个文件**的路径、类型、模式和blob SHA与本地逐项一致；树未截断，二进制胶片资源也核对SHA。未包含任何APK/IPA、签名、本机配置或私有验证素材。

本节核验结果随后以文档补充提交发布；最终main高于上述源码提交，源码行为与0.7.0锁版保持一致。版本标签 `android-v0.7.0` 指向补充文档后的锁版提交。新对话本地分支 `codex/android-0.7.0-release`；以实时main/tag与完整树核验为准。
