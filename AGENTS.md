# iMerge iteration policy

After completing an implementation iteration:

1. Run `./gradlew testDebugUnitTest lintDebug assembleRelease`.
2. Commit only the intended iMerge changes.
3. Push the verified commit to `main`.
4. Confirm the `Build and publish iMerge` GitHub Actions workflow succeeds and creates the latest GitHub Release.

Never commit the release keystore, passwords, `release.env`, or generated APKs. A release is complete only after its APK, `update.json`, and SHA-256 checksum are available from the latest GitHub Release.
