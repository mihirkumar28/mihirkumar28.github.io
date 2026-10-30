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
- [x] Remove the obsolete standalone timeline renderer so Journey has one chronological navigation surface instead of duplicate hidden UI.
- [x] Add a compact horizontal month navigator to Journey. Planned month slots remain visible when empty; the selected month, focus list and mini progress indicators stay synchronized.
- [x] Simplify monthly focus and workstream presentation by removing nested card borders, redundant previous/next controls and repeated workstream footers.
- [x] Preserve the existing tree, completion store and drag/drop business rules; expose catalog-linked offline reading copies from source-linked goal details.
- [ ] Run PR CI, unit tests and offline PDF emulator verification.
- [ ] Verify month-strip interaction, layout at narrow widths, accessibility text scaling, and Journey/Tree state synchronization on an Android device.

## Roadmap Flow and month selector polish — 2026-10-24

- [x] Add a Flow view that lays out all planned months in chronological order, with active monthly goals, nested goals, dated milestones and completion controls in one continuous vertical sequence.
- [x] Keep Flow synchronized with saved completion state and use the existing goal/milestone persistence pathways.
- [x] Replace the dark selected-month fill with a light sage highlight and stronger outline; keep text dark and improve miniature progress-track contrast.
- [ ] Run unit tests and signed APK build on the PR revision.
- [ ] Verify Journey/Flow/Tree on-device, including month selection contrast, screen width and enlarged accessibility text.

## Roadmap Flow UI refinement — 2026-10-24

- [x] Replace the oversized repeated Flow intro with a compact date-range/progress summary and a Jump to now action.
- [x] Organize each month as a connected timeline node with a slim vertical spine, compact month status and local completion bar.
- [x] Visually emphasize only the current/selected month; keep future and past sections calmer to reduce long-page clutter.
- [x] Simplify goal rows to track, title and only material subgoal/reschedule metadata; open the Tree for full goal detail rather than clipping long target excerpts into every row.
- [x] Retain completion controls for goals/milestones and existing saved-state pathways.
- [ ] Run Android unit tests and signed APK build.
- [ ] Check the screen on a physical device for text scaling, bottom-navigation overlap, long titles and scroll-to-current behavior.

## Roadmap Flow navigation, filtering and inline details — 2026-10-24

- [x] Expand the current month by default; provide per-month expand/collapse and Expand all / Collapse all controls.
- [x] Add a horizontally scrollable month-jump strip that selects, expands and scrolls to a month.
- [x] Add workstream filters plus an independent Open only toggle; preserve month order and month-level progress.
- [x] Add inline goal details including target, source, pages, estimated time, parent/reschedule metadata, Open in Tree and bundled offline source actions.
- [x] Add quarter labels and separate goal, milestone and overall progress counts.
- [x] Keep existing completion handlers, source catalog and offline PDF viewer pathways.
- [ ] Verify CI unit tests, signed APK build and offline-PDF emulator checks.
- [ ] Verify physical-device scroll behavior, filter interactions and font scaling.

## Roadmap Flow screenshot fixes — 2026-10-24

- [x] Move the status-bar inset to the ScrollView viewport and clip its children to the viewport padding, so Flow filters do not scroll under the system clock.
- [x] Keep page padding content-only to avoid double-counting the system inset.
- [x] Remove MATCH_PARENT height from the timeline rail's initial measurement; size the line after the month row is measured so collapsed months stay compact.
- [x] Add horizontal spacing between monthly completion counts and the expand/collapse chevron.
- [ ] Run unit tests, Android emulator PDF verification and signed release build.
- [ ] Confirm on the user's physical device that the status bar no longer overlaps controls and collapsed months have compact spacing.

## Roadmap Flow month count spacing — 2026-10-24

- [x] Put the monthly completion count (for example, 0 / 4) in its own compact rounded pill.
- [x] Increase separation from the expand/collapse chevron and give the chevron a predictable touch target.
- [x] Use a subtle completion-state treatment for the count pill without changing the underlying goal count or progress logic.
- [ ] Run Android unit tests and signed APK build.
- [ ] Verify count/chevron appearance on a physical device.

## Today tab — selectable weekend routine and linear timeline — 2026-10-24

- [x] Add a separate Weekday routine / Weekend routine selector on Today; the selection is persisted per date and is independent of WFH/WFO work arrangement.
- [x] Require confirmation before replacing a date's schedule with a different routine; use the existing template loader so matching blocks retain completion/focus history and reusable templates remain unchanged.
- [x] Keep WFH/WFO as its own two-option work-arrangement selector; applying Weekend no longer writes the legacy value "weekend" into the work-arrangement field.
- [x] Move the schedule timeline ahead of the stats panel so the primary tasks appear earlier in the scroll.
- [x] Compact the current-task panel and replace repeated schedule cards with a time-first linear row layout, readable dark text, status accents and compact completion actions.
- [x] Reduce free-time row height and add unit coverage for default and overridden weekday/weekend routine profiles.
- [ ] Verify Android regression tests and signed APK build.
- [ ] Verify the Weekend selector and linear list on a physical device, including longer task titles and WFO visibility.

## Correct Today routine model — 2026-10-24

- [x] Replace the nested Weekday/Weekend plus WFH/WFO controls with exactly three mutually exclusive routine choices: WFH, WFO and Weekend.
- [x] Persist the selected routine per date using the matching template key (`wfh`, `office`, `weekend`); legacy ambiguous `weekday` values fall back to the calendar day and valid WFH/WFO setting.
- [x] Weekend is never written to the internal WFH/WFO arrangement field and hides office attendance actions while selected.
- [x] Keep apply confirmation, date-only schedule replacement, matching block history, and reusable templates unchanged.
- [x] Add unit coverage for all three allowed routine types and per-date overrides.
- [ ] Run Android regression tests, offline-PDF emulator verification and signed release build.
- [ ] Verify the three-option selector and Weekend behavior on a physical device.


## P0 reliability and offline source-document work — 2026-10-25

- [x] Add a build-time catalog/archive validator for month keys, duplicate IDs/filenames, goal-to-document source references, PDF archive integrity, and truthful original-vs-condensed metadata.
- [x] Update the PDF packaging script to prefer original PDFs under app/src/main/assets/plans/ when present and package the actual source bytes into plan_pdfs.zip; generated catalog-based copies remain the explicit fallback until original files are checked in.
- [x] Synchronize document flags during the build so the APK UI labels a file as an original only when its original asset exists and has a valid PDF header.
- [x] Add a physical-device P0 smoke-test checklist. This is a checklist, not evidence that physical-device checks have been performed.
- [ ] Add and commit all five authoritative original PDFs under app/src/main/assets/plans/; until then the APK correctly labels condensed offline copies. App update availability is no longer blocked by this document-only follow-up.
- [ ] Run and inspect PR CI (unit tests, lint, offline PDF emulator test and APK build).
- [ ] Perform the physical-device smoke checklist, including widget behavior, routine isolation, Today/Plan synchronization, Roadmap Flow/Tree and airplane-mode access to every original PDF.


## Body-composition planner tab — 2026-10-26

- [x] Add a dedicated Body bottom-navigation tab with locally saved starting measurements, calorie/activity inputs, target-date selection, estimated TDEE/deficit, projected weight/body-fat output, and sensitivity to the assumed fat-loss partition.
- [x] Extract the scenario arithmetic into a pure Java calculator and cover the core equations with unit tests.
- [ ] Run and inspect Android CI (lint, unit tests, APK build); visually verify the Body tab on a physical device. The calculator is intentionally labelled as a simplified scenario model, not a clinical prediction.


## Four-destination navigation consolidation — 2026-10-26

- [x] Reduce the persistent bottom navigation from six destinations to Today, Plan, Roadmap and More.
- [x] Move Body and Office into the grouped More hub; nested tool pages retain the More selected state so users can return to the hub using the same destination.
- [x] Group tracking, routines/library, data/app and careful actions instead of presenting one flat settings list.
- [x] Combine four routine-template shortcuts into one template picker and combine backup export/import into one entry.
- [ ] Run PR CI (unit tests, lint and APK build) and verify the four-tab layout and More-hub flows on a physical device.


## Official NIDDK Body Weight Planner integration — 2026-10-26

- [x] Add an in-app screen that loads the official NIDDK-hosted Body Weight Planner rather than presenting a simplified local model as equivalent to NIDDK's dynamic model.
- [x] Add entry points from More and the existing local Body-fat planner without adding another bottom-navigation tab.
- [x] Include an explicit online-only notice, NIDDK attribution, retry behavior, browser fallback, WebView navigation/back handling and conservative file/mixed-content settings.
- [ ] Verify lint, unit tests and APK build in GitHub Actions; confirm the NIDDK page loads and its inputs work on an Android device.


## In-app update reliability — 2026-10-26

- [x] Make the More action explicit: Update DayFlow checks for an update and starts the in-app download/install flow.
- [x] Download the APK directly from the same GitHub Release asset referenced by the update manifest, avoiding a race with the separately deployed GitHub Pages mirror.
- [x] Publish signed in-app updates even while authoritative offline plan PDFs remain a separate outstanding content task; packaged copies remain labelled accurately.
- [ ] Verify main-branch Actions publishes the APK and update.json, the Pages mirror catches up, and an existing signed installation can install the update in place.
- [ ] Verify the one-time Android “install unknown apps” permission and system confirmation on a physical device.


## Atomic in-app update publication — 2026-10-26

- [x] Upload the signed APK asset before exposing its new version in update.json.
- [x] Deploy the matching APK and update.json to GitHub Pages, then publish the same update.json to GitHub Releases.
- [x] Preserve compatibility with already-installed app versions that still download updates through the Pages mirror; the manifest does not announce a new version until that mirror is live.
- [ ] Verify the end-to-end main-branch build, Pages deployment, Release manifest update, and in-app update install.


## Gemini commute intelligence — 2026-10-30

- [x] Add a separate commute-analysis screen reachable from the existing Gemini assistant; keep existing daily-routine and office-month review flows intact.
- [x] Save home/work labels and coordinates locally, with manual coordinate entry or address geocoding, route preview in the user's map app, and a clear-points action.
- [x] Exclude the exact home/work points from Android cloud backup and device transfer; ask for separate explicit consent before sending points, routine blocks, office dates and commute timestamps to Gemini.
- [x] Build directional commute samples from completed records over the preceding 120 days; ignore malformed intervals and implausible legs longer than six hours.
- [x] Calculate per-weekday medians and 80th percentiles when at least three weekday-specific samples exist; otherwise use the direction's overall history and label the fallback.
- [x] Project missing commute legs only for today/future planned office dates. Distinguish missing historical records and insufficient future samples from zero-duration commutes.
- [x] Include saved date-specific routine blocks as read-only context. The commute analysis screen has no timetable mutation or apply pathway.
- [x] Add JVM tests for directional projection, missing-data handling, read-only prompt constraints and straight-line distance.
- [ ] Run GitHub Actions lint, JVM tests, emulator smoke checks and APK build; verify geocoding, map handoff, consent and screen layout on a physical Android device.
