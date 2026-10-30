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
- Optional Gemini AI assistant for testing a personal Google AI Studio API key, reviewing a saved daily routine or office month, and running a separate commute analysis
- Read-only Gemini commute analysis using saved home/work points, office dates, routine blocks and completed commute logs
- On-device commute projection for future planned office dates: directional median and 80th-percentile estimates, using weekday-specific samples when at least three exist and an overall fallback otherwise
- Home/work coordinates can be entered manually or resolved from place names; the route can be previewed in an external map app
- Home/work points are stored separately and excluded from Android cloud/device backup; sending them and commute/routine data to Gemini requires separate explicit consent
- Gemini API key encrypted locally with Android Keystore; the encrypted secret is excluded from device/cloud backup
- Gemini analysis is opt-in and sends only the selected analysis data to Google; free-tier content may be used by Google to improve its products
- Commute forecasts use recorded journeys rather than live traffic; historical missing records are treated as unknown, not zero, and the model is instructed never to edit the saved timetable
- Local Gemini diagnostics record request timestamps, model/attempt, HTTP status, response latency, limited request IDs and sanitized error details; they never record API keys, prompts, task titles, or response text
- Gemini diagnostics can be viewed, copied, or exported from the Gemini assistant screen and attached to a support conversation; logs are not automatically uploaded
- Offline-first core timetable: no backend required

## Build
Requires JDK 17 and Android Gradle Plugin 8.13.x. The project can be built by GitHub Actions.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
