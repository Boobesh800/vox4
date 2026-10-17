# VoxControl – fixes for "open app / alarm / app closed / offline / screen off"

## Why it failed
1. **Open app** – Android 10+ silently blocks `startActivity()` from a background service. The old code
   reported "success" anyway. Also, *partial* speech results ("open you…") were executed, which opened wrong
   apps and then ran the real command a second time.
2. **Alarm** – it asked the Clock app to set the alarm via an activity launch (blocked in background) and
   depended on that Clock app's behaviour.
3. **Offline** – if the offline speech pack is not installed the recognizer errors out; the old code just
   restarted it every 450 ms forever with no feedback.
4. **App closed / screen off** – on Android 12+ a microphone service cannot be restarted from the
   background, and phone makers kill it unless battery/auto-start access is granted.

## What changed
- `controller/BackgroundLauncher.kt` (new): opens apps from the background using the "Display over other
  apps" overlay trick; falls back to a tap-to-open notification and now reports the truth in the log.
- `alarm/*` (new): own alarm system on `AlarmManager.setAlarmClock` (works with app closed, offline, Doze,
  screen off). Rings on the alarm stream, full-screen over the lock screen, Snooze/Dismiss, restored after
  reboot. Voice "stop alarm" stops a ringing alarm; "turn off alarm [at 7 am]" cancels a scheduled one.
- `VoiceListenerService`: no more execution on partial results; proper error handling with back-off;
  asks Android to download the offline language pack (Android 13+); never switches to the online/system
  recognizer when an offline language pack is missing; shows an error/restart notification instead.
- `CommandParser`: understands "a.m./p.m.", "7 30 pm", "in 10 minutes", "stop the alarm"; app-name matching
  no longer picks random apps from short/partial words.
- Manifest: `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `USE_FULL_SCREEN_INTENT`, media-playback FGS.
- App start now opens the *next missing* access screen (overlay → battery), one per launch.

## One-time phone setup (needed for real "app closed + screen off")
1. Allow **Display over other apps** for VoxControl.
2. Battery → VoxControl → **Unrestricted / Don't optimize**.
3. Redmi/Xiaomi, Realme, Oppo, Vivo, OnePlus: turn on **Autostart**, and
   **Display pop-up windows while running in the background** + **Show on lock screen**; lock the app in Recents.
4. Offline voice: Settings → Google → Voice (or Google app → Settings → Voice) → **Offline speech
   recognition** → download your language (e.g. English (India)).
5. Open the app once and tap the mic button to start listening (Android requires this after every reboot).

## Validation status and known limits
- Static source fixes are included, but this environment does not have an Android SDK/Gradle wrapper, so an APK build and on-device tests have not been run. Do not treat this ZIP as device-verified.
- Android restricts automatic activity launches from background apps. Overlay-assisted launch is best-effort; the notification is the fallback and may require a tap. A PIN/pattern must still be unlocked.
- Offline voice works only when the device supports Android on-device recognition and the required language pack is installed.
- Alarm timing, sound, lock-screen display, reboot restoration, and snooze/dismiss still need testing on the target phone and Android version.
