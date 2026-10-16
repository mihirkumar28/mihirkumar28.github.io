# DayFlow Android manual smoke-test checklist

Use this checklist on a real device or emulator before merging a change that affects app behavior or publishing a release. Record device model, Android version, app version, launcher (for widget checks), and any failed step in the PR. A successful unit-test task or APK build does not replace these checks.

## 1. Install, launch and navigation
- [ ] Fresh install launches to Today without a crash or a blank screen.
- [ ] Upgrade from the previous signed APK preserves existing timetable, roadmap, office log and settings data.
- [ ] Today, Plan, Progress, Office, Roadmap/Syllabus and More can all be opened repeatedly without stale or blank content.
- [ ] On-screen app content and bottom actions remain above Android Home/Back/gesture navigation areas; test gesture and three-button navigation where available.
- [ ] Return from system Settings, permission screens, dialogs and file pickers returns to the expected DayFlow screen.

## 2. Today and timetable
- [ ] Empty-day state shows a clear way to add the first block.
- [ ] Add, edit, complete, undo, skip, restore and delete a block; verify the summary updates after each action and after navigating away/back.
- [ ] Add adjacent blocks; verify they are allowed. Attempt overlapping or reversed intervals; verify they are rejected with a useful explanation.
- [ ] Add daily and weekday recurring blocks; verify weekend scope, conflict skipping and occurrence count/message.
- [ ] Switch WFH, Office and Weekend templates; verify the correct template is shown and templates still follow edits after reopening the app.
- [ ] Change selected date and return to Today; verify blocks and summary belong to the selected date, not a cached day.
- [ ] With 1 of 11 syllabus topics completed, verify Today shows 9%; toggle another topic and verify the percentage refreshes.

## 3. Home-screen widget
- [ ] Add DayFlow widget on the launcher; confirm it renders within its expected two-row height without clipping.
- [ ] Confirm the visible card ends just below its own content/buttons and the widget does not show an oversized blank card.
- [ ] On WFH days, verify office check-in actions are not shown as if an office shift were scheduled.
- [ ] Tap each visible widget action; verify the app state changes correctly and the widget refreshes.
- [ ] Change a timetable template in the app and confirm the widget reflects the new template, not stale/static content.
- [ ] Remove and re-add the widget; verify no broken layout or stale actions.

## 4. Roadmap, syllabus and monthly goals
- [ ] Toggle a syllabus topic complete and reopen it; verify topic count, Today percentage and roadmap summaries all refresh.
- [ ] In roadmap edit mode, drag a goal to a month with no goals; verify the move succeeds without pressing the Add button.
- [ ] After a move, verify both destination and source month remain visible, even when the source month becomes empty.
- [ ] Drop into a populated month and choose a parent if prompted; verify parent/child identity and month are correct.
- [ ] Attempt to move completed work or move across an incompatible track; verify continuity guards explain the rejection.
- [ ] Verify search/filter, collapse/expand and reopening Roadmap do not lose saved goals, create duplicates or conceal an empty month slot.

## 5. Office attendance and commute
- [ ] Check in and out twice on one date; verify both sessions and cumulative duration.
- [ ] Start a session and leave it open; verify it counts only for the current date and doesn't keep growing as a historical session.
- [ ] Record inbound and outbound commute separately; edit each start/end and verify totals refresh.
- [ ] Edit an old date and verify the corrected timestamp remains on that exact date.
- [ ] Attempt a malformed date or an end time before/equal to the start; verify the invalid interval is rejected or clearly flagged for repair.

## 6. Backup and restore
- [ ] Export a backup and verify the file is non-empty and can be selected for restore.
- [ ] Restore a valid backup; verify timetable, roadmap/goals, office history, settings and templates are retained.
- [ ] Try malformed, unsupported and truncated backups; verify restore fails before replacing saved state.
- [ ] After a failed restore, reopen the app and confirm the pre-restore data is still available; record any rollback warning.

## 7. Focus, screen time and permissions
- [ ] Start and stop Focus from a task; verify only started Focus sessions count as actual focus time.
- [ ] Test Screen Time with Usage access granted, denied and later revoked; empty/permission states must explain what is missing.
- [ ] Background and reopen the app; confirm screen-time history doesn't double-count or unexpectedly erase saved days.

## 8. Updates, accessibility and recovery
- [ ] Test update check when no update is available and when metadata describes a newer compatible release.
- [ ] Confirm a downloaded APK with a mismatched version or invalid signature is not installed; test download cancel/retry.
- [ ] With TalkBack enabled, verify page titles are headings, the active navigation destination is announced as selected, and important controls have meaningful labels.
- [ ] Increase system font/display size; check titles, goal descriptions, dialogs and buttons are not clipped.
- [ ] Test the recovery/adjust-day flow and confirm it does not overwrite fixed blocks or silently discard unfinished tasks.

## Result
- Device / Android version:
- Launcher (widget tests):
- App version:
- Date:
- Passed / failed / not tested:
- Reproduction notes / screenshots:
