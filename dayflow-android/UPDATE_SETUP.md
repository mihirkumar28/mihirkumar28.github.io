# DayFlow in-app updates

Update metadata and APK are published to stable GitHub Release asset URLs:
- https://github.com/mihirkumar28/mihirkumar28.github.io/releases/download/latest/update.json
- https://github.com/mihirkumar28/mihirkumar28.github.io/releases/download/latest/app-release.apk

The Android workflow publishes these only when persistent release signing is configured. Temporary debug APK artifacts are not the update channel.

## Configure persistent signing once

Create a release keystore and keep a secure offline backup. Never commit the keystore or passwords to this repository.

Create the key with:
keytool -genkeypair -v -keystore dayflow-release.jks -alias dayflow -keyalg RSA -keysize 4096 -validity 10000

Then base64-encode it (Linux: base64 -w0 dayflow-release.jks).

In GitHub, open Settings → Secrets and variables → Actions and add these repository secrets:
- DAYFLOW_KEYSTORE_BASE64: output of the base64 command
- DAYFLOW_STORE_PASSWORD: keystore password
- DAYFLOW_KEY_PASSWORD: key password
- DAYFLOW_KEY_ALIAS: dayflow

Never commit the keystore or passwords. Back up the key securely: all future in-app updates must use the same signing identity.

## First stable install

Earlier builds were temporary CI debug builds. Android may reject the first permanently signed APK as an update because its signing key differs. If DayFlow shows the one-time migration prompt:
1. Use More → Export backup and save the JSON outside the app.
2. Download the stable APK from the update prompt.
3. Uninstall the current debug-signed DayFlow, install the downloaded stable APK, and open it.
4. Use More → Import backup to restore local plans and history.

After this one-time migration, every push that builds a signed release updates the fixed latest release assets. DayFlow checks once about every 24 hours and also provides More → Check for updates. Android always asks the user to approve installation; DayFlow cannot silently install packages.