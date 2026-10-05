# DayFlow Android

Native Android implementation of DayFlow. The app stores its timetable in Android SharedPreferences on the device, so closing the app does **not** erase the plan.

## Features in v1
- Native Android UI
- Persistent local timetable storage
- WFH / Office / Weekend templates
- Add blocks
- Done / Skip / Undo
- Live current + next block
- Recovery mode
- Daily execution metrics
- Offline-first: no backend required

## Build
Requires JDK 17 and Android Gradle Plugin 8.13.x. The project can be built by GitHub Actions.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
