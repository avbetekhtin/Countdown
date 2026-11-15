# Countdown

Android sleep timer built with Kotlin and Jetpack Compose.

## What it does

- Counts down hours and minutes from a foreground service.
- Keeps the timer alive when the app is in the background.
- Shows the remaining time in a persistent notification.
- Requests Android Device Administrator access and locks the device when the timer ends.

Android does not allow a regular third-party app to force-close other applications. When the timer finishes, Countdown locks the device; the system then handles the running apps. Device Administrator access must be enabled once from the app.

## Build

Open this folder in Android Studio and let it sync Gradle. From a terminal with the Android SDK configured:

```text
gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test flow

1. Install the debug APK on an Android 8.0+ device or emulator.
2. Grant notification permission when prompted.
3. Tap `Разрешить блокировку экрана` and activate the device administrator.
4. Set a short timer, start it, send the app to the background, and wait for the device to lock.
