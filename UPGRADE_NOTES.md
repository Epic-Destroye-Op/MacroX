# Macro X 2.0 upgrade notes

The original project was inspected before changes. The upgraded project keeps the original package/application identity and adds persistent macro editing, supported accessibility gestures, real execution history, permission status checks, foreground execution, and floating controls.

No preset/demo macros are created by the upgraded app.

The original uploaded ZIP was not modified or overwritten.

## v2.1
- Removed the floating overlay controls and the "Display over other apps" permission.
- Home screen now has a circular power button that runs the selected macro; tap again to stop.
- Volume-key trigger is a toggle in Settings.
- Settings and macro lists were restyled into cards.

## v2.2
- Added Key Press, Left Click, Mouse Down and Mouse Up steps to the macro editor, with a categorized key picker.
- Key presses run `input keyevent` through Shizuku (if running and granted) or root. Failures are recorded in history with the reason.
- Settings → Permissions now shows Shizuku status with a grant button.
- Left Click holds for a configurable duration. Mouse Down/Up hold a touch-level left press.
- Tap and Swipe actions removed. Saved Tap steps load as Left Click (1 ms hold). Saved Swipe steps are dropped on load. Unknown action names are skipped, not fatal.
- Not implemented: key hold/release, right click, input recording, nested repeat blocks. See README.

## v2.3
- Key output setting: Shizuku or root (any app), or Macro X keyboard (no root, focused text field only). Select it in Settings → Key output.
- Macro X keyboard: enable it in input settings, then start the macro with a volume key while a text field is focused. Starting from the Home button brings Macro X to the front, which removes that focus.
- Macros tab: full-screen editor, cleaner step cards, and a simpler action picker (Key / Mouse / Wait, with Click / Hold start / Release for mouse).
- Settings scrolls. Key categories are a dropdown.
- Home: red flames around the power button while a macro runs. The top bar shows RUNNING with the macro name. Pressing power without Accessibility or a macro shows a message.
- Tab switching animates. The title has a red X.
- Not verified on a device. Built without an Android SDK in the sandbox.

