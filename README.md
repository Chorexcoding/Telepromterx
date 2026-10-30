# Float Teleprompter (Phase 1)

Package: `com.rajesh.floatprompter` | Java 17 | minSdk 26 | target/compile SDK 35 | AGP 8.6.0 | Gradle 8.7

## Build (GitHub Actions)
1. Put every file from this folder in the repo root. Keep the folder structure.
2. Delete your old workflow file in `.github/workflows/`. The new `build.yml` replaces it.
   The old one rewrote all source files from heredocs on every push, which would overwrite these files.
3. Commit and push. Open the Actions tab, run "Build Float Teleprompter APK".
4. Download artifact **Float-Teleprompter-APK** (contains `app-debug.apk`).

## Install
1. Copy the APK to the phone and open it. Allow "Install unknown apps" for your file manager or browser if asked.
2. Open the app, paste a script, press Start.
3. Allow "Display over other apps" when Android opens that screen, then press Start again.
4. Android 13+: allow notifications (needed to see the Pause/Stop notification).

## Permissions
| Permission | Why |
|---|---|
| SYSTEM_ALERT_WINDOW | Draw the teleprompter over other apps |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE | Keep the overlay running (Android 14+ special-use type) |
| POST_NOTIFICATIONS | Show the service notification with Pause/Stop (Android 13+) |

No internet, microphone or storage permission is used in this phase.

## Known limitations
- Orientation lock is experimental: it asks Android for an orientation through a hidden overlay window. Whether another app (camera) follows depends on the phone maker. It cannot force a camera app to rotate.
- Notification permission denied: the prompter still works; use the buttons inside the prompter.
- Not yet compiled or run on a device by the author. The first Actions build is the first real compile.
