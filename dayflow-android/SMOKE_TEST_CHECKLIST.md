# DayFlow P0 smoke-test checklist

Run this checklist on the installed APK after CI emulator tests pass. A successful build or unit-test run is not evidence that the rendered screens and launcher widget work correctly.

## Test setup
- Record APK version/build and commit SHA.
- Test on a physical Android phone and, if available, a narrow emulator profile.
- Repeat layout checks at default and enlarged Android display/text sizes.
- For offline documents, enable airplane mode and verify Wi-Fi remains disabled.

## Today and routine application
- [ ] Select WFH, WFO, and Weekend on both a weekday and a weekend date; Weekend must never expose office-attendance controls.
- [ ] Apply a different routine and verify confirmation identifies the selected date and replacement behavior.
- [ ] Change the selected date after applying a routine; no other date's schedule should change.
- [ ] Use “Modify & Save Today's Routine”; verify reusable templates and other dates remain unchanged.
- [ ] Complete a block, stop/restart a focus session if available, navigate away and back, and verify history remains consistent.
- [ ] Test an empty schedule, all-completed schedule, missed block, active block, and upcoming block; an empty day must not display as completed.
- [ ] Verify planned duration, completed blocks, tracked focus, study actual-versus-plan and syllabus progress are internally consistent.

## Widget
- [ ] Add the widget at the smallest supported size and a larger size; no controls or labels are clipped.
- [ ] Verify WFH, WFO, and Weekend display the correct actions and labels.
- [ ] Complete a task from the widget, open the app, and confirm both surfaces show the same state.
- [ ] Change a template/date in the app and verify the widget refreshes without a reinstall.
- [ ] Verify widget actions do not apply a reusable template to the wrong date.

## Office attendance and monthly planning
- [ ] Start and end an office session; verify elapsed time and open/closed state.
- [ ] Record multiple sessions on one date and verify the total is their sum.
- [ ] Edit a historical session and verify today's total does not change incorrectly.
- [ ] Verify Weekend/WFH states cannot create an unintended office attendance session.
- [ ] In Office → Office-day plan, mark dates and verify planned dates are distinct from recorded attendance.
- [ ] Plan future-month dates, leave planning mode, and verify attendance-history cells still cannot create future actual records.
- [ ] Verify eight planned days with 6.5 hours each meets the 52-hour minimum; eight days below 52 office hours does not qualify even if office time plus commute exceeds 52.
- [ ] Verify seven days never qualifies, even with 52+ planned office hours; nine days qualifies with the hour minimum waived.
- [ ] Verify commute is shown as 1 h 15 min each way (2 h 30 min per day), with 20 h travel for eight days and 22 h 30 min for nine days.
- [ ] Verify eight days with 52 office hours shows 72 h total commitment including travel.
- [ ] Edit a planned day's hours, remove a planned date, switch months, and verify the plan persists independently for each month.

## Roadmap and documents
- [ ] Move a goal between months, expand/collapse months, and verify the source month remains visible and parent-child identity is preserved.
- [ ] Verify monthly counts such as 0 / 4 match the goal/milestone items shown.
- [ ] Switch Flow and Tree and confirm completion state and selected month stay synchronized.
- [ ] Open each catalog document while offline. Original PDFs must be labelled as originals; generated condensed documents must be explicitly labelled as copies.
- [ ] For each original PDF, turn several pages and verify the content is the actual source document, not a regenerated summary.
- [ ] Verify missing assets produce an actionable error rather than a misleading successful open.

## Backup, navigation and accessibility
- [ ] Open the Body tab, change inputs and target date, calculate estimates, leave the tab and return; verify saved inputs persist and all scenarios refresh correctly.
- [ ] Restore a valid backup and verify saved data; attempt a malformed backup and verify existing data is preserved.
- [ ] Navigate through Today, Plan, Progress, Office, Roadmap/Syllabus, Screen Time and More; no screen should overlap system bars or bottom navigation.
- [ ] Test larger fonts, long task names, TalkBack focus order and primary-action touch targets.
- [ ] Confirm the app can still be opened and used after a forced process restart.

## Sign-off
- Tester/device:
- APK version and commit:
- Date:
- Failed checks / screenshots:
