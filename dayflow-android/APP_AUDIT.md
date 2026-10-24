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

- [x] Extract the legacy dark-to-light color remapping into a pure, unit-tested DayFlowPalette class; improve secondary-label contrast on light task cards. [ ] Continue migrating screens to explicit semantic tokens and add automated contrast checks without a high-risk global replacement.
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


## Today tab remediation — 2026-10-22

Source changes on `fix/today-tab-usability`:

- [x] Distinguish an empty plan, active task, upcoming task, fully completed plan and unfinished/missed blocks.
- [x] Show an apply-routine action for an empty day, and don't label an empty schedule as complete.
- [x] Keep weekday/weekend routine defaults separate from WFH/WFO arrangement; add migration handling for legacy `weekend` mode values.
- [x] Separate per-day block completion, planned duration, tracked focus time, study actual-vs-planned time and global UPSC syllabus progress.
- [x] Make the timeline heading follow the selected date and make Today-screen controls at least 48dp where updated.
- [x] Add pure JVM tests for Today state resolution, legacy arrangement normalization and date-type template defaults.
- [ ] Confirm CI unit tests/build results and verify the rendered screen on-device at narrow widths and enlarged accessibility text.


## Unified usability redesign — 2026-10-23

- [x] Combine a readable date navigator, automatic weekday/weekend badge, and separate WFH/WFO selector on Today.
- [x] Rework the current/next-task card into a compact action-first surface with state-aware controls.
- [x] Tighten the daily summary while keeping completion, planned minutes, focus time, study actual-versus-plan, and syllabus progress visible.
- [x] Improve timeline hierarchy with readable time labels, a single accent marker, task duration/rigidity, and consistent completion actions.
- [x] Clarify Plan as a routine-preview/apply workflow and label WFH/WFO/Weekend explicitly; communicate that applying replaces only the selected date's actual schedule.
- [x] Improve widget label/value readability and reclaim vertical space while preserving a transparent outer surface and content-wrapping card.
- [x] Normalize legacy widget mode values so a saved "weekend" label cannot create a false work arrangement.
- [ ] Verify the rendered Android UI and widget in emulator/device screenshots at narrow widths, widget sizes, and enlarged accessibility text.

## Roadmap Journey + Tree redesign — 2026-10-24

- [x] Consolidate Roadmap navigation into two modes: Journey (chronological planning and progress) and Tree (full goal hierarchy).
- [x] Add a compact horizontal month navigator to Journey. Planned month slots remain visible when empty; the selected month, focus list and mini progress indicators stay synchronized.
- [x] Simplify monthly focus and workstream presentation by removing nested card borders, redundant previous/next controls and repeated workstream footers.
- [x] Preserve the existing tree, completion store and drag/drop business rules; expose catalog-linked offline reading copies from source-linked goal details.
- [ ] Run PR CI, unit tests and offline PDF emulator verification.
- [ ] Verify month-strip interaction, layout at narrow widths, accessibility text scaling, and Journey/Tree state synchronization on an Android device.
