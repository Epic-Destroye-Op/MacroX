# Macro X

Macro X is an Android accessibility-based macro runner. It stores user-created macros locally and executes only Android-supported accessibility gestures.

## What is implemented

- Macro X branding with black/red UI.
- Persistent macro storage.
- Create, edit, delete and reorder macro steps.
- Tap, swipe and delay steps.
- Configurable X/Y coordinates, swipe duration and delay-after values.
- Per-macro repeat count with a configurable maximum repeat limit.
- Execution speed control.
- Real Volume Up/Volume Down accessibility trigger selection.
- Foreground execution service.
- Real floating Start / Pause / Resume / Emergency Stop controls when overlay permission is granted.
- Real execution history with status and duration.
- Actual Android status checks for accessibility, overlay and battery optimization.
- GitHub Actions debug APK build.

## Android limitations

Macro X does not inject arbitrary keyboard events into other apps. Android's public APIs do not provide a general-purpose way for an ordinary app to synthesize arbitrary keys in another app.

Tap and swipe execution uses `AccessibilityService.dispatchGesture`, which requires the user to explicitly enable the Macro X accessibility service. Some apps can restrict or behave differently with accessibility gestures.

Volume-key filtering is device/Android-version dependent. Android may reserve hardware keys for system behavior.

The overlay is shown only when the user has granted Android's "display over other apps" permission.

## Build locally

Open the `MacroX` folder in Android Studio with JDK 17 and sync the project. Then build the debug APK.

This repository intentionally does not include a Gradle wrapper. GitHub Actions installs Gradle 8.7 directly.

## GitHub Actions

The workflow at `.github/workflows/build.yml` builds `app-debug.apk` and uploads it as a workflow artifact.

## Phone + Termux upload

After installing Git in Termux:

```bash
termux-setup-storage
cd ~/storage/downloads
unzip MacroX-Android-Project-upgraded.zip
cd MacroX
git init
git add .
git commit -m "Upgrade Macro X"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

For an existing GitHub repository, replace `YOUR_USERNAME/YOUR_REPO` with the repository path and use `git remote set-url origin ...` if `origin` already exists.

On GitHub, open **Actions → Build Macro X → Run workflow**. After it finishes, download the `MacroX-debug` artifact.
