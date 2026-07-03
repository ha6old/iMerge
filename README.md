# iMerge

一款极简的原生 Android 照片拼接工具。选择多张照片、调整顺序，并以纵向或横向合并后直接保存到系统相册。

## 功能

- Android 系统照片选择器，一次选择最多 30 张照片
- 纵向 / 横向无缝拼接预览
- 长按缩略图后左右拖动排序
- 高质量 JPEG 导出到 `Pictures/iMerge`
- 导出完成后明确提示保存位置，可选择保留或删除拼接前原图
- 删除本地原图时使用 Android 系统确认；云端或不支持删除的来源会安全保留
- Release 版本每次启动自动检查 GitHub Releases 更新
- 新版本由系统在后台下载，校验 SHA-256 后自动打开 Android 安装界面
- 大图按 2400 万像素与 16000 像素边长预算自动缩放
- 支持浅色和深色系统主题，无广告、无水印、无需存储权限

## 技术栈

- Kotlin + Jetpack Compose
- Android Gradle Plugin 9.2（内置 Kotlin）
- 最低 Android 10（API 29），目标 Android 16（API 36）

## 构建

```bash
./gradlew assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

运行单元测试和静态检查：

```bash
./gradlew testDebugUnitTest lintDebug
```

连接 Android 设备后，可运行完整的两图纵向/横向导出回归测试：

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.haroldadmin.imerge.MergeFlowTest
```

## 自动发布与更新

每次推送到 `main` 后，[GitHub Actions](.github/workflows/release.yml) 会自动运行测试、签名打包，并发布 `iMerge.apk` 与 `update.json`。更新协议和签名要求见 [`docs/UPDATE_PROTOCOL.md`](docs/UPDATE_PROTOCOL.md)。

普通 Android 应用不能静默升级：首次更新时需要允许 iMerge 作为安装来源，每次安装仍需在 Android 系统安装器中确认。
