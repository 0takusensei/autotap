# Auto Tap

An Android app: press and hold anywhere on screen, and that spot gets an
automatic double-tap repeated every ~250ms, following your finger if it
moves, until you lift up. A quick tap (no hold) still passes through as a
normal single tap.

## How it works

- **`TapAccessibilityService`** — an Android Accessibility Service. This is
  the only kind of component allowed to inject real touch gestures into
  *other* apps, which is why the app needs you to enable it.
- **`OverlayService`** — draws a small draggable floating button (like
  Android's AssistiveTouch). Tapping it turns "Auto-Tap mode" on/off. When
  on, a transparent full-screen window captures your touches, detects a
  press-and-hold, and repeatedly asks `TapAccessibilityService` to fire a
  double-tap at that location.
- **`MainActivity`** — walks you through granting the two permissions and
  starting/stopping the overlay.

## Building it via GitHub Actions (no local Android Studio needed)

1. Create a new GitHub repository and push this whole folder to it (the
   `.github/workflows/android-build.yml` file must end up at that exact path).
2. Go to the repo's **Actions** tab. The workflow runs automatically on every
   push to `main`, or trigger it manually with **Run workflow**.
3. When it finishes, open the run and download the **AutoTap-debug-apk**
   artifact — that's a zip containing `app-debug.apk`.
4. Copy the APK to your phone and install it (you'll need to allow
   "install unknown apps" for whichever app you transfer it with).

```bash
git init
git add .
git commit -m "Auto Tap app"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

## Using the app

1. Open **Auto Tap**, tap **Enable Accessibility Service** → find "Auto Tap"
   in the list → turn it on (Android will show a warning dialog about
   gesture access — this is expected and required).
2. Tap **Grant Overlay Permission** → allow "display over other apps".
3. Tap **Start / Stop Auto-Tap Overlay**. A small blue floating circle
   appears — you can drag it anywhere.
4. Tap the circle once to turn Auto-Tap mode **on** (it turns green). Now
   press and hold anywhere on screen to trigger repeating double-taps at
   that spot; lift your finger to stop. Tap the circle again to turn it
   **off** and use your phone normally.

## Notes

- Minimum Android version: 8.0 (API 26), needed for the floating-window
  APIs used here.
- Because Auto-Tap mode intercepts all screen touches while it's on, use
  the floating toggle to turn it off whenever you want normal phone use —
  that's the point of the draggable button always staying on top.
- Some apps and games prohibit automated/scripted input in their terms of
  service — that's between you and whatever app you point this at, so use
  it on things you're allowed to automate.
