# DayFlow app-wide audit and quality plan

**Scope:** Native Android app under `dayflow-android/`.
**Baseline reviewed:** 2026-10-16 source tree and latest successful release build.
**Approach:** Small, reversible changes; preserve current behavior; build and test after every phase.

## Status key
- [ ] Not yet implemented
- [x] Implemented and verified in source/build
- [~] Partially addressed; needs device-level verification

## Phase 1 — release safety and regression protection

- [x] Keep GitHub Actions release builds serialized by branch; cancel stale in-flight runs so an older commit cannot race to overwrite the `latest` release.
- [x] Publish the `latest` signed APK only from `main`.
- [x] Add a CI validator for widget layout inflation: supported RemoteViews classes only, unique IDs, and every widget-provider view reference present in XML.
- [x] Add a CI validator for widget RemoteViews compatibility and provider/layout IDs. [ ] Add Android UI/instrumentation coverage for actual widget inflation, task completion, office check-in/out, and navigation.
- [x] Add strict typed-backup validation and unit tests for unsupported formats, malformed entries, invalid numbers, bad string sets, and oversized documents; validate fully before preferences are cleared.
- [ ] Add workflow-level verification that the published update metadata and APK come from the same commit/build.

## Phase 2 — scheduling and data integrity

- [ ] Add explicit invariant tests for non-overlapping blocks, valid start/end times, fixed anchors, and behavior when work will not fit in the remaining day.
- [ ] Test recurrence scope (single occurrence vs future occurrences vs template) and ensure generated blocks are idempotent.
- [x] Validate every imported backup completely before mutating preferences; reject malformed/oversized files before clearing saved data. (Automatic rollback snapshot remains open.)
- [ ] Test attendance edits across multiple sessions, open sessions, invalid historical intervals, and date boundaries.
- [ ] Add migration tests for older saved preference formats.

## Phase 3 — design consistency and everyday usability

- [~] Fix the shared text helper so it honors the requested color; set dark text explicitly on light task cards and improve their secondary-label contrast. Establish reusable color, typography, spacing, shape, and button tokens; continue migrating screen-by-screen rather than doing a high-risk global replacement.
- [ ] Align Today, Plan, Progress, Office, Roadmap/Syllabus, Screen Time, and More around common page headers, cards, empty states, confirmation patterns, and button hierarchy.
- [ ] Review all flows for tap count, accessible labels, minimum touch size, truncation, large font scaling, contrast, and system-bar insets.
- [ ] Audit Today/Plan/progress metrics for a single definition of planned, completed, skipped, actual focus, and syllabus completion.
- [~] Widget: keep the latest redesigned content and wrap the visible card to its content; the actual rendered appearance still requires device verification.

## Phase 4 — roadmap and secondary workflows

- [ ] Preserve tree continuity, empty month slots, parent-child identity, and completed-goal invariants through add/move/drop/reorder/archive flows.
- [ ] Test that moving a node refreshes Today/Plan summaries immediately and does not create duplicate nodes or lose the source slot.
- [ ] Add edge-case tests for search/filter combinations, malformed periods, split/retire/restore goal lifecycle, and import/export.

## Phase 5 — maintainability

- [ ] Extract scheduling, persistence, attendance, backup/restore, updates, and per-screen UI from the very large `MainActivity` in small, tested steps.
- [ ] Keep pure business rules Android-independent where possible and add tests before moving logic.
- [ ] Add lint/static analysis and a documented smoke-test checklist before APK publication.

## Current constraints

- This repository uses native Android views and locally persisted SharedPreferences.
- A successful Gradle build is not equivalent to device-level UI verification.
- Do not replace the application wholesale or migrate storage/UI frameworks as a first step.
