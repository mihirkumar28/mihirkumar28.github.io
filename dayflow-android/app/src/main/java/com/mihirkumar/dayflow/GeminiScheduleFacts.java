package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/** Builds deterministic facts for Gemini. Calculations are performed by DayFlow, not guessed by the model. */
final class GeminiScheduleFacts {
    private GeminiScheduleFacts() { }

    private static final class BlockFact {
        final String title;
        final String start;
        final String end;
        final String status;
        final String rigidity;
        final String priority;
        final int startMinute;
        final int endMinute;
        final int duration;

        BlockFact(String title, String start, String end, String status, String rigidity,
                String priority, int startMinute, int endMinute) {
            this.title = title;
            this.start = start;
            this.end = end;
            this.status = status;
            this.rigidity = rigidity;
            this.priority = priority;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.duration = endMinute - startMinute;
        }
    }

    static String routinePrompt(String dateKey, String arrangement, JSONArray blocks) throws Exception {
        ArrayList<BlockFact> facts = new ArrayList<>();
        int invalidBlocks = 0;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject item = blocks.optJSONObject(i);
            if (item == null) continue;
            String start = item.optString("start", "");
            String end = item.optString("end", "");
            Integer startMinute = parseTime(start);
            Integer endMinute = parseTime(end);
            if (startMinute == null || endMinute == null || startMinute.intValue() == endMinute.intValue()) {
                invalidBlocks++;
                continue;
            }
            int normalizedEnd = endMinute.intValue();
            if (normalizedEnd < startMinute.intValue()) normalizedEnd += 1440;
            facts.add(new BlockFact(
                    item.optString("title", "Untitled block"),
                    start, end,
                    item.optString("status", "pending"),
                    item.optString("rigidity", "flexible"),
                    item.optString("priority", "normal"),
                    startMinute.intValue(), normalizedEnd));
        }
        if (facts.isEmpty()) {
            throw new IllegalStateException("There are no valid saved time blocks for " + dateKey + ".");
        }
        Collections.sort(facts, (a, b) -> Integer.compare(a.startMinute, b.startMinute));

        int rawScheduledMinutes = 0;
        int markedDoneMinutes = 0;
        int earliest = facts.get(0).startMinute;
        int latest = facts.get(0).endMinute;
        int occupiedMinutes = 0;
        int mergedStart = -1;
        int mergedEnd = -1;
        int overlapPairCount = 0;
        StringBuilder overlaps = new StringBuilder();
        StringBuilder entries = new StringBuilder();
        StringBuilder sleepEntries = new StringBuilder();

        for (int i = 0; i < facts.size(); i++) {
            BlockFact fact = facts.get(i);
            rawScheduledMinutes += fact.duration;
            if ("done".equalsIgnoreCase(fact.status)) markedDoneMinutes += fact.duration;
            earliest = Math.min(earliest, fact.startMinute);
            latest = Math.max(latest, fact.endMinute);
            entries.append("- ").append(fact.start).append("–").append(fact.end)
                    .append(" | duration=").append(formatMinutes(fact.duration))
                    .append(" | task=").append(fact.title)
                    .append(" | status=").append(fact.status)
                    .append(" | rigidity=").append(fact.rigidity)
                    .append(" | priority=").append(fact.priority).append("\n");

            if (fact.title.toLowerCase(Locale.ROOT).contains("sleep")) {
                if (sleepEntries.length() > 0) sleepEntries.append("; ");
                sleepEntries.append(fact.title).append(" ")
                        .append(fact.start).append("–").append(fact.end)
                        .append(" = ").append(formatMinutes(fact.duration));
            }

            if (mergedStart < 0) {
                mergedStart = fact.startMinute;
                mergedEnd = fact.endMinute;
            } else if (fact.startMinute <= mergedEnd) {
                mergedEnd = Math.max(mergedEnd, fact.endMinute);
            } else {
                occupiedMinutes += mergedEnd - mergedStart;
                mergedStart = fact.startMinute;
                mergedEnd = fact.endMinute;
            }

            for (int j = 0; j < i; j++) {
                BlockFact other = facts.get(j);
                int overlapStart = Math.max(fact.startMinute, other.startMinute);
                int overlapEnd = Math.min(fact.endMinute, other.endMinute);
                if (overlapEnd > overlapStart) {
                    overlapPairCount++;
                    overlaps.append("- ")
                            .append(other.title).append(" (").append(other.start).append("–").append(other.end)
                            .append(") overlaps ")
                            .append(fact.title).append(" (").append(fact.start).append("–").append(fact.end)
                            .append(") by ").append(formatMinutes(overlapEnd - overlapStart)).append("\n");
                }
            }
        }
        if (mergedStart >= 0) occupiedMinutes += mergedEnd - mergedStart;
        int windowMinutes = latest - earliest;
        int gapsMinutes = Math.max(0, windowMinutes - occupiedMinutes);

        String arrangementLabel = "office".equals(arrangement) ? "Work from office"
                : "weekend".equals(arrangement) ? "Weekend"
                : "wfh".equals(arrangement) ? "Work from home" : "Not recorded for this date";
        StringBuilder prompt = new StringBuilder();
        prompt.append("DAYFLOW ROUTINE REVIEW\n");
        prompt.append("Date: ").append(dateKey).append("\n");
        prompt.append("Day arrangement saved in DayFlow: ").append(arrangementLabel).append("\n");
        prompt.append("FACTS CALCULATED BY DAYFLOW (treat these as authoritative):\n");
        prompt.append("Valid time blocks: ").append(facts.size()).append("\n");
        prompt.append("Scheduled duration (sum of blocks, double-counts overlaps): ")
                .append(formatMinutes(rawScheduledMinutes)).append("\n");
        prompt.append("Occupied clock time after merging overlapping blocks: ")
                .append(formatMinutes(occupiedMinutes)).append("\n");
        prompt.append("First scheduled start: ").append(formatClock(earliest)).append("\n");
        prompt.append("Last scheduled end: ").append(formatClock(latest)).append("\n");
        prompt.append("Unscheduled gaps between first start and last end: ")
                .append(formatMinutes(gapsMinutes)).append("\n");
        prompt.append("Blocks marked done: ").append(formatMinutes(markedDoneMinutes)).append("\n");
        prompt.append("Overlapping block pairs: ").append(overlapPairCount).append("\n");
        prompt.append("Invalid or unparseable blocks omitted from calculations: ").append(invalidBlocks).append("\n");
        if (sleepEntries.length() == 0) {
            prompt.append("Explicit Sleep-labelled blocks: none. Sleep duration is not recorded in the supplied plan.\n");
        } else {
            prompt.append("Explicit Sleep-labelled blocks: ").append(sleepEntries).append("\n");
            prompt.append("Important: this is only the duration of the blocks literally labelled Sleep. Do not infer actual time in bed, sleep duration, or wake-up time from the gaps or from the first task.\n");
        }
        prompt.append("\nSAVED BLOCKS IN CHRONOLOGICAL ORDER:\n").append(entries);
        prompt.append("\nEXACT OVERLAPS FOUND BY DAYFLOW:\n");
        if (overlapPairCount == 0) prompt.append("None. Do not invent a conflict.\n");
        else prompt.append(overlaps);
        prompt.append("\nINSTRUCTIONS FOR THE REVIEWER:\n");
        prompt.append("Review only the selected date. Use the calculated totals and overlaps above; do not recalculate them or invent missing data.\n");
        prompt.append("Do not infer sleep duration from gaps or an assumed wake-up time. Do not call a schedule infeasible merely because it is busy or has a long total; identify a concrete constraint that is actually evidenced.\n");
        prompt.append("Do not treat two separate exercise blocks as a conflict or recommend deleting one unless their times overlap or a user-stated constraint makes them incompatible. Do not claim burnout, zero decompression, or cognitive unsustainability as fact without evidence.\n");
        prompt.append("Only call transitions 'zero buffer' when two blocks are exactly adjacent; even then state the observed adjacency, not a medical or psychological conclusion. Do not assume commute, meal, shower, or preparation time unless it appears as a block.\n");
        prompt.append("Distinguish: (a) observed facts, (b) potential risks that require confirmation, and (c) optional optimisations. Give at most three specific, proportionate suggestions. Preserve fixed blocks and priorities. Never change the schedule; recommendations only. If the data is insufficient, say exactly what is missing instead of guessing.");
        return prompt.toString();
    }

    static String officeMonthPrompt(String monthKey, JSONArray plan, JSONArray officeLog) throws Exception {
        ArrayList<JSONObject> plannedEntries = new ArrayList<>();
        Map<String, Double> plannedHoursByDate = new HashMap<>();
        int invalidEntries = 0;
        double plannedHours = 0d;
        for (int i = 0; i < plan.length(); i++) {
            JSONObject item = plan.optJSONObject(i);
            if (item == null) continue;
            String date = item.optString("date", "");
            if (!date.startsWith(monthKey + "-") || date.length() != 10) {
                invalidEntries++;
                continue;
            }
            double hours = item.optDouble("hours", OfficeMonthPlanRules.DEFAULT_HOURS_PER_DAY);
            if (Double.isNaN(hours) || Double.isInfinite(hours)) {
                invalidEntries++;
                continue;
            }
            hours = Math.max(0d, Math.min(24d, hours));
            JSONObject normalized = new JSONObject()
                    .put("date", date)
                    .put("hours", hours);
            plannedEntries.add(normalized);
            plannedHoursByDate.put(date, hours);
            plannedHours += hours;
        }
        Collections.sort(plannedEntries, (a, b) -> a.optString("date", "")
                .compareTo(b.optString("date", "")));
        int plannedDays = plannedEntries.size();
        double commuteHours = OfficeMonthPlanRules.totalCommuteHours(plannedDays);
        double combinedHours = OfficeMonthPlanRules.totalCommitmentHours(plannedHours, plannedDays);
        boolean compliant = OfficeMonthPlanRules.isCompliant(plannedDays, plannedHours);
        boolean waiver = OfficeMonthPlanRules.isHoursMinimumWaived(plannedDays);
        String policy;
        if (plannedDays < OfficeMonthPlanRules.MINIMUM_DAYS) {
            policy = "NOT MET: fewer than 8 planned office dates.";
        } else if (waiver) {
            policy = "MET: 9 or more planned office dates; the 52-hour minimum is waived.";
        } else if (compliant) {
            policy = "MET: at least 8 dates and at least 52 planned office hours.";
        } else {
            policy = "NOT MET: 8 dates are planned but total office hours are below 52.";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("DAYFLOW MONTHLY OFFICE-PRESENCE PLAN REVIEW\n");
        prompt.append("Selected month: ").append(monthKey).append("\n");
        prompt.append("FACTS CALCULATED BY DAYFLOW (authoritative):\n");
        prompt.append("Planned office dates: ").append(plannedDays).append("\n");
        prompt.append("Total planned hours physically in office: ").append(formatDecimalHours(plannedHours)).append("\n");
        prompt.append("Planned commute: ").append(formatDecimalHours(commuteHours))
                .append(" (2.5 hours per planned office date; app assumption is 75 minutes each way).\n");
        prompt.append("Combined office + planned commute commitment: ").append(formatDecimalHours(combinedHours)).append("\n");
        prompt.append("Policy result: ").append(policy).append("\n");
        prompt.append("Rule: at least 8 office dates are required. At exactly 8 dates, at least 52 office hours are required. At 9 or more dates, the 52-hour minimum is waived. Commute never counts as office hours.\n");
        prompt.append("Invalid saved plan entries ignored: ").append(invalidEntries).append("\n");
        prompt.append("\nPLANNED OFFICE DATES AND HOURS:\n");
        if (plannedEntries.isEmpty()) prompt.append("No office dates are currently saved for this month.\n");
        for (JSONObject item : plannedEntries) {
            prompt.append("- ").append(item.optString("date"))
                    .append(" | planned office time=").append(formatDecimalHours(item.optDouble("hours")))
                    .append(" | planned commute=2h 30m\n");
        }

        ArrayList<String> actualLines = new ArrayList<>();
        int actualDays = 0;
        double actualHours = 0d;
        int openSessions = 0;
        for (int i = 0; i < officeLog.length(); i++) {
            JSONObject day = officeLog.optJSONObject(i);
            if (day == null) continue;
            String date = day.optString("date", "");
            if (!date.startsWith(monthKey + "-") || date.length() != 10) continue;
            JSONArray sessions = day.optJSONArray("sessions");
            if (sessions == null) continue;
            long millis = 0L;
            for (int j = 0; j < sessions.length(); j++) {
                JSONObject session = sessions.optJSONObject(j);
                if (session == null) continue;
                long in = session.optLong("in", 0L);
                long out = session.optLong("out", 0L);
                if (in > 0L && out > in) millis += out - in;
                else if (in > 0L && out == 0L) openSessions++;
            }
            if (millis > 0L) {
                actualDays++;
                actualHours += millis / 3600000d;
                Double plannedForDate = plannedHoursByDate.get(date);
                String line = "- " + date + " | recorded time in office="
                        + formatDecimalHours(millis / 3600000d);
                if (plannedForDate != null) {
                    line += " | planned office time=" + formatDecimalHours(plannedForDate);
                } else {
                    line += " | no planned office entry for this date";
                }
                actualLines.add(line);
            }
        }
        prompt.append("\nRECORDED ACTUAL ATTENDANCE (separate from the plan):\n");
        prompt.append("Dates with completed recorded office sessions: ").append(actualDays).append("\n");
        prompt.append("Recorded completed office time: ").append(formatDecimalHours(actualHours)).append("\n");
        prompt.append("Open check-in sessions not included in completed actual hours: ").append(openSessions).append("\n");
        if (actualLines.isEmpty()) prompt.append("No completed attendance sessions are recorded for this month.\n");
        else for (String line : actualLines) prompt.append(line).append("\n");

        prompt.append("\nINSTRUCTIONS FOR THE REVIEWER:\n");
        prompt.append("Analyse the selected month's saved office plan, not a generic WFO/WFH template. Keep planned hours separate from actual attendance and commute. Never add commute to the 52-hour office requirement.\n");
        prompt.append("Assess whether the saved plan meets the exact rule above. Identify concentration of office days, unusually short/long planned office visits, and how planned dates distribute across the month. Do not assume weekdays, holidays, work policy, or availability that is not supplied.\n");
        prompt.append("If actual attendance exists, compare it with planned entries only for dates that have both records. Clearly say the attendance ledger is incomplete when no actual sessions are recorded; do not treat missing logs as zero attendance.\n");
        prompt.append("Recommend changes only when they improve policy compliance or meaningfully reduce the explicitly calculated office-plus-commute commitment. Preserve the user's existing dates and per-date durations unless proposing a clearly labelled alternative. Do not fabricate dates or claim changes have been saved. End with a concise, actionable assessment.");
        return prompt.toString();
    }

    private static Integer parseTime(String value) {
        try {
            String[] parts = value.split(":");
            if (parts.length != 2) return null;
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
            return hour * 60 + minute;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatMinutes(int minutes) {
        int safe = Math.max(0, minutes);
        return (safe / 60) + "h " + String.format(Locale.US, "%02dm", safe % 60);
    }

    private static String formatClock(int minutes) {
        int minuteOfDay = ((minutes % 1440) + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private static String formatDecimalHours(double hours) {
        return String.format(Locale.US, "%.2f h", Math.max(0d, hours));
    }

    static String formatMonthLabel(String monthKey) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM", Locale.US).parse(monthKey);
            return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date);
        } catch (Exception ignored) {
            return monthKey;
        }
    }

    static String formatDateLabel(String dateKey) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey);
            return new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(date);
        } catch (Exception ignored) {
            return dateKey;
        }
    }
}
