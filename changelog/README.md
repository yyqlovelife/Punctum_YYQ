# 更新说明怎么写

Punctum 是双端产品。Android 与 iOS 各自维护一份按版本倒序的更新说明，方便以后接手的人接着往下记。

## 文件

| 文件 | 用途 |
|---|---|
| [`changelog/android.md`](android.md) | Android 更新说明 |
| [`changelog/ios.md`](ios.md) | iOS 更新说明 |
| [`CHANGELOG.md`](../CHANGELOG.md) | 根目录索引 +「当前交接状态」（版本、安装包、构建、禁止事项） |

两端细节写在 `android.md` / `ios.md`。根目录那一份只指路，并给接手人一段能立刻开工的现状。

## 什么时候写

只要用户说「升级版本号」「对齐版本号」「发一版」，就必须同时：

1. 改对应端的版本号（Android：`app/build.gradle.kts` 的 `versionName` / `versionCode`；iOS：`iOS/Punctum/project.yml` 的 `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION`，改完后在 `iOS/Punctum` 执行 `xcodegen generate`）。
2. 在对应端的 changelog **最上面**加一节，日期用当天。
3. 更新根目录 `CHANGELOG.md` 里的「当前版本」一行。

没有升版本号的日常改动，不必另开一节；等下次升版本时并进那一节。

例外：用户明确说「更新交接日志 / 给同事接手」时，即使不升号，也要在对应端日志 **最上面** 加一节 `## 未发版 · YYYY-MM-DD`，并更新根目录「当前交接状态」。下次升版本时把「未发版」改成正式版本号，不要另起一节重复抄。

## 每一节怎么写

标题格式：

```
## 0.x.y · YYYY-MM-DD
```

正文先用两三句说这一版对用户意味着什么，再按主题列条目。条目写「为什么 / 用起来怎样」，不要只罗列文件名。

Android 的 `versionCode`、iOS 的 build 号与 `versionName` / `MARKETING_VERSION` 的末两位对齐，例如 `0.5.1` → `51`。

两端版本号默认对齐。若某一端单独发版，只改那一端的文件，并在该节开头标明「仅 Android」或「仅 iOS」。
