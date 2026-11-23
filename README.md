# GladeLauncher

A small personal Android launcher with a quiet home screen, the current wallpaper, and an alphabetical app drawer. Android 10 or newer. No ads, accounts, analytics, or internet permission.

## Use

- Swipe up anywhere on Home to open the app drawer. The whole Home grid is available for shortcuts, including the bottom edge above system navigation.
- Keep your finger down to move the drawer directly, reverse direction, or drag it back down. It fades in as it rises. Releasing settles to the nearest end; a quick flick follows its direction. Drag down from the top edge, or from the app list when it is at the top, to close it.
- Drawer colors and menus follow the system light/dark theme using neutral grays. The background is slightly translucent; icons and labels remain fully visible when open.
- Icons use a shared 50dp squircle shape. Home shortcuts show only the icon; the drawer keeps app names. Screen readers still receive each app's name on Home.
- The rounded drawer background appears first while Home icons move up and fade out. Apps begin fading in after the halfway release-to-open threshold. Each system bar gains a translucent, theme-matched gradient when drawer rows pass underneath, fading in and out over 180ms with a soft edge into the drawer. Bars remain clear over Home.
- Tap an app to launch it. Long-press a drawer app to add it to Home or open its system app-info page.
- The same menu offers **Add to Quick Access** or **Remove from Quick Access**. Chosen apps move to the top in the order added, separated from the remaining alphabetical apps by a divider. The selection is saved locally, and opening the drawer starts at the top.
- Long-press a Home shortcut and drag it to an empty position. Drop it on **Remove from Home** at the top to remove the shortcut; the app stays installed.
- Long-press empty Home space for wallpaper and default Home selection. Selecting Glade as the default is optional while testing.
- Home and Back close the drawer. There is no drawer search field.

Home has one page with a 5 × 7 placement grid. No cells are reserved for a dock. New shortcuts start in the bottom row, then fill available cells elsewhere. Older six-row layouts migrate once, preserving columns and keeping top/bottom shortcuts anchored. The drawer uses five icons per row at the original 106dp pitch. Quick Access and alphabetical apps share one scrolling list, which draws behind both system bars. Inset padding keeps its first and last rows fully reachable.

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
