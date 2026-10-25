# DayFlow Android

Native Android implementation of DayFlow. The app stores its timetable in Android SharedPreferences on the device, so closing the app does **not** erase the plan.

## Features in v1
- Native Android UI
- Persistent local timetable storage
- WFH / Office / Weekend templates
- Add blocks
- Done / Skip / Undo
- Compact home-screen widget with live task timing and progress
- Office check-in/out and commute actions, plus WFH/weekend Done/Skip actions
- Recovery mode
- Daily execution metrics
- Body-composition planner tab with locally saved inputs, target-date selection, TDEE/deficit estimates and fat-loss-partition scenarios
- Offline-first: no backend required

## Build
Requires JDK 17 and Android Gradle Plugin 8.13.x. The project can be built by GitHub Actions.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
