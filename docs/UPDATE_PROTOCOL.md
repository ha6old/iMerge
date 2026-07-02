# iMerge update protocol

At each app startup, iMerge requests:

`https://github.com/ha6old/iMerge/releases/latest/download/update.json`

The manifest format is:

```json
{
  "versionCode": 100001,
  "versionName": "1.0.1",
  "minSdk": 29,
  "apkUrl": "https://github.com/ha6old/iMerge/releases/latest/download/iMerge.apk",
  "sha256": "64 lowercase hexadecimal characters",
  "changelog": "Release notes"
}
```

When `versionCode` is newer than the installed build, Android `DownloadManager` downloads the APK to the app-owned external files directory. iMerge verifies SHA-256 before opening Android's package installer. Android still requires the user to trust iMerge as an install source and confirm each installation.

GitHub Actions creates the manifest and signed APK after every push to `main`. The signing keystore is stored only as encrypted GitHub repository secrets.
