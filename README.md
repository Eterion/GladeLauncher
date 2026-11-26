# GladeLauncher

A small personal Android launcher with a quiet home screen, the current wallpaper, and an alphabetical app drawer. Android 10 or newer. No ads, accounts, analytics, or internet permission.

## Features

- Wallpaper-focused home screen with movable shortcuts.
- Alphabetical app drawer and Quick Access favorites.
- Smooth gestures, animations, and system light/dark themes.
- Consistent app icons and accessible labels.
- Quick links to wallpaper, battery, and app settings.
- Locally saved layouts and preferences.

## Develop

Install Android Studio and its SDK/build tools. Open this folder in Studio and sync Gradle, then run the `app` configuration. The Gradle wrapper pins the build tool and verifies its distribution checksum.

For terminal checks on Windows, with Git and PowerShell 7 available:

```powershell
pwsh -NoProfile -File scripts/verify.ps1 -Android
pwsh -NoProfile -File scripts/tests/checks.tests.ps1
```

With an emulator running, `gradlew.bat connectedDebugAndroidTest` also checks Android storage/migration, drawer spacing, and first-item accessibility. Set `ANDROID_SERIAL` when more than one test device is connected. The Android test runner may uninstall the debug app during cleanup; run the app again from Studio afterward.

The checker uses an existing `JAVA_HOME`/`ANDROID_HOME`, or detects Studio and the SDK at their default Windows locations. Its Gradle cache stays in ignored `.local/`. For custom installations set those variables, or configure the SDK in ignored `local.properties`.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. Keep the existing launcher installed when testing on a phone; switch back through system Settings > Apps > Default apps > Home app.

See `AGENTS.md` for development rules and hook activation. Plans, briefs, screenshots, and temporary working files belong in ignored `.local/`.
