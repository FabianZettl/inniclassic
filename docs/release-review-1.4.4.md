# 1.4.4 release candidate review

Status: debug-signed test candidate, not published.

## Scope

Video list input now reaches the shared wheel navigation handler. Center opens the selected video. Classic video rows use the shared styling; the list starts below the status bar. Video startup pauses active music. Previous/Next seek by ten seconds.

Classic Now Playing uses slimmer bars, fixed-width time labels and evenly spaced rating marks. Pausing keeps artwork opaque, hides the format overlay, and retains the selected bottom-row mode. Remaining time is also kept consistent on pause and when either audio engine becomes ready. Progress callbacks no longer accumulate on repeated updates.

Center is intercepted before screen-specific handlers. An 800 ms hold locks or unlocks, release after holding does not also select an item, and locked hardware/touch input is consumed. Short Center cycles Progress / Seek / Shuffle & Repeat / Rating. The Now Playing context menu is assigned to long Play/Pause. Double-Center no longer toggles favorites.

## Validation

- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew assembleDebug testDebugUnitTest lintDebug`: successful. Three existing unit tests pass; these do not cover hardware gestures. Lint reports zero errors and 467 warnings.
- Connected Innioasis Y1, Android API 17: installed as an update without clearing application data.
- Selected the existing MP4 in Videos using wheel/Center and observed playback with an advancing counter.
- Checked Now Playing and pause visually on the 480×360 display; exercised short Center mode changes and long Center locking/unlocking through the device input interface.
- Screenshots and input scripts are in `/tmp/inni-144` for this workstation session.

## Limits before publication

This is a focused repair, not a complete architecture or security audit. Video codec coverage, listening/AV-sync quality, long playback, battery drain, Bluetooth, radio, and every modal dialog/theme combination have not been validated. The three existing unit tests are file/web-server regression tests plus the template smoke test, not comprehensive UI coverage.

Package-update sequences intermittently produced an Android framework application-start exception in `LoadedApk.initializeJavaContextClassLoader`; the app subsequently started and ran. This also appeared during the final update, so resolve/reproduce the update-start race and repeat cold-start testing before publication. Hardware hold timing should also be checked by hand, including dialogs and unplugged standby.
