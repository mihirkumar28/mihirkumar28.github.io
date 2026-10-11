# DayFlow persistent APK signing

This makes future release APKs update the same installed app. Keep the keystore private and back it up securely. Never commit the keystore or paste its contents into an issue, chat, or pull request.

## 1. Create a release key once

Use a trusted computer with Java's `keytool` installed. Pick a strong keystore password and remember it. This example uses alias `dayflow`; keep that alias for later builds.

```bash
keytool -genkeypair -v -keystore dayflow-release.jks -alias dayflow -keyalg RSA -keysize 2048 -validity 10000
```

Keep `dayflow-release.jks` somewhere safe and encrypted. Do not regenerate it for future app versions.

## 2. Encode the keystore for GitHub Actions

**macOS / Linux** (run in the directory containing the file):

```bash
base64 < dayflow-release.jks | tr -d '\n'
```

Copy the entire output directly into the GitHub secret named `DAYFLOW_KEYSTORE_BASE64`. The output is sensitive; treat it as the signing key.

**Windows PowerShell**:

```powershell
$bytes = [System.IO.File]::ReadAllBytes("$PWD\dayflow-release.jks")
[Convert]::ToBase64String($bytes) | Set-Clipboard
```

The Base64 text is now in the clipboard. Paste it into the GitHub secret without adding quotes.

## 3. Add the repository secrets

Open **GitHub → mihirkumar28/mihirkumar28.github.io → Settings → Secrets and variables → Actions → New repository secret**. Add these four:

| Secret name | Value |
| --- | --- |
| `DAYFLOW_KEYSTORE_BASE64` | Base64 output from Step 2 |
| `DAYFLOW_STORE_PASSWORD` | Password entered for the keystore |
| `DAYFLOW_KEY_PASSWORD` | Password for alias `dayflow` (can be the same password) |
| `DAYFLOW_KEY_ALIAS` | `dayflow` |

Do not share secret values in chat. GitHub hides secret values after creation, so keep a secure local copy of your passwords.

## 4. Build and confirm

Open **Actions → Build DayFlow Android → Run workflow** or push a code change. When the secrets are configured, the workflow should verify the keystore, build a release APK, and update the `latest` release. If none of the signing secrets are configured, CI builds a debug APK instead; a partial signing configuration fails visibly rather than silently using debug signing.

## 5. Migration from a debug APK

Android only allows an update when the installed APK and new APK have compatible signing certificates. If the installed DayFlow APK was a debug build, the new persistent release key will almost certainly differ.

1. In the currently installed app, go to **••• → Manage day → Export DayFlow backup** and save the JSON somewhere safe.
2. Configure the secrets above and make sure a **signed release** build succeeds.
3. If Android refuses the release as an update due to a signature mismatch, export the backup first, then uninstall the old app and install the signed release.
4. Open DayFlow and use **••• → Manage day → Import DayFlow backup** to restore your plans, attendance records, syllabus, and saved screen-time totals.

Uninstalling without a backup deletes app-local records. A backup cannot recover screen-time days that DayFlow never captured and Android no longer exposes.
