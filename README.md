# Zone Mail Android 壳 App

WebView 打开 https://cloud-mail.liziran254.workers.dev/ 。

- 包名 `com.zonemail.app`，应用名 Zone Mail，蓝色信封图标
- 正式版域名内链接在 App 内打开，外链走系统浏览器
- 附件上传文件选择器；附件下载用 DownloadManager（blob/data 链接直接存到「下载」）
- 登录态、Cookie 持久保存在 App 内

## 构建
推送到 `main` 或手动运行 Actions「Build Zone Mail APK」，会打 Debug APK 并发布到 Releases（标签 `latest`，名称「Zone Mail 最新版」，文件 `zone-mail.apk`）。

下载：https://github.com/LzRanbaby/zone-mail-app/releases/download/latest/zone-mail.apk
