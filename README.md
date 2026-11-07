# Macro X

Macro X is an Android accessibility-based macro runner. It stores user-created macros locally and executes only Android-supported accessibility gestures.

## What is implemented

- Macro X branding with black/red UI.
- Persistent macro storage.
- Create, edit, delete and reorder macro steps.
- Wait, left click, mouse down/up and key press steps.
- Configurable X/Y coordinates, hold duration and delay-after values.
- Per-macro repeat count with a configurable maximum repeat limit.
- Execution speed control.
- Real Volume Up/Volume Down accessibility trigger selection.
- Foreground execution service.
- Real floating Start / Pause / Resume / Emergency Stop controls when overlay permission is granted.
- Real execution history with status and duration.
- Actual Android status checks for accessibility, overlay and battery optimization.
- GitHub Actions debug APK build.

## Keyboard and mouse actions (v2.2)

- **Key Press** sends a real key event (A–Z, 0–9, F1–F12, arrows, navigation, Shift/Ctrl/Alt/Win, Space, Enter, Tab, Esc, Backspace, and punctuation) chosen from a categorized key picker. It runs `input keyevent <code>` through **Shizuku** if Shizuku is running and granted to Macro X, otherwise through **root** (`su`). With neither, the step fails and the history shows the reason.
- **Left Click** taps a screen coordinate with a configurable hold duration, using `dispatchGesture`. No root needed.
- **Mouse Down / Mouse Up** hold and release the left button at a coordinate, using a continued touch stroke. It is touch-level only. Android has no hover, so this is not a mouse pointer.
- **Wait** works as before. Old **Tap** steps load as Left Click with a 1 ms hold. Old **Swipe** steps are dropped on load, since no remaining action can express them.

Not implemented, and why:

- **Hold/release for keys** (keyboard key down, key up, timed holds such as "hold W for 2 s"): the `input` shell command only sends complete presses. Timed holds would need `sendevent` on the keyboard device node, which varies per device. Not done.
- **Right click**: Android gestures have no secondary button. No right-click action is offered.
- **Mouse movement without a button**: not possible with accessibility gestures.
- **Input recording**: MacroX has no recorder. Macros are authored in the editor.
- **Nested repeat blocks**: only the whole-macro repeat count exists.

## Android limitations

Macro X does not inject keyboard events into other apps on its own. Android's public APIs give an ordinary app no way to send key events to another app. Key presses need one of:

- **Shizuku** (recommended, no root). Install the Shizuku app and start it. On Android 11+ it can be started on the device through Wireless debugging. Then open Macro X → Settings and grant Shizuku access. Shizuku must be running again after each reboot unless you use its root mode.
- **Root** (`su`), as a fallback.

Shizuku is included as the `dev.rikka.shizuku:api` and `provider` 12.1.0 dependencies. Shizuku's docs mark `Shizuku.newProcess` for future removal in favor of UserService. Macro X uses `newProcess` for simplicity, so a future Shizuku release may require updating this code.

Left clicks use `AccessibilityService.dispatchGesture`, which requires the user to explicitly enable the Macro X accessibility service. Some apps can restrict or behave differently with accessibility gestures.

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
