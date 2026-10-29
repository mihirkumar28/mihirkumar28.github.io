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
- Optional Gemini AI assistant for testing a personal Google AI Studio API key and reviewing today's saved routine
- Gemini API key encrypted locally with Android Keystore; the encrypted secret is excluded from device/cloud backup
- Gemini analysis is opt-in and sends current-day task titles, times and statuses to Google; Google may use free-tier content to improve its products
- Offline-first core timetable: no backend required

## Build
Requires JDK 17 and Android Gradle Plugin 8.13.x. The project can be built by GitHub Actions.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
