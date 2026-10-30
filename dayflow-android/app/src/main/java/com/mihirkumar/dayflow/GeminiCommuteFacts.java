package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds a read-only commute review from DayFlow's saved records.
 * All projections are calculated here; Gemini is not asked to invent travel times.
 */
final class GeminiCommuteFacts {
    private static final int HISTORY_DAYS = 120;
    private static final int MAX_REASONABLE_LEG_MINUTES = 360;
    private static final int MIN_WEEKDAY_SAMPLES = 3;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private GeminiCommuteFacts() { }

    private static final class Sample {
        final String date;
        final DayOfWeek weekday;
        final String direction;
        final String departure;
        final int minutes;

        Sample(String date, DayOfWeek weekday, String direction, String departure, int minutes) {
            this.date = date;
            this.weekday = weekday;
            this.direction = direction;
            this.departure = departure;
            this.minutes = minutes;
        }
    }

    private static final class Stats {
        final int count;
        final int median;
        final int p80;
        final boolean weekdaySpecific;

        Stats(List<Sample> samples, boolean weekdaySpecific) {
            ArrayList<Integer> values = new ArrayList<>();
            for (Sample sample : samples) values.add(sample.minutes);
            Collections.sort(values);
            count = values.size();
            median = percentileMedian(values);
            p80 = percentile(values, 0.80d);
            this.weekdaySpecific = weekdaySpecific;
        }
    }

    static String buildPrompt(String monthKey, String todayKey, long nowMillis,
            JSONArray officePlan, JSONArray officeLog, JSONArray routines,
            String homeLabel, double homeLatitude, double homeLongitude,
            String workLabel, double workLatitude, double workLongitude) throws Exception {
        LocalDate today = parseDate(todayKey);
        if (today == null || monthKey == null || monthKey.length() != 7) {
            throw new IllegalArgumentException("The selected month or current date is invalid.");
        }

        TreeMap<String, Double> plannedDates = new TreeMap<>();
        for (int i = 0; officePlan != null && i < officePlan.length(); i++) {
            JSONObject item = officePlan.optJSONObject(i);
            if (item == null) continue;
            String date = item.optString("date", "");
            LocalDate parsed = parseDate(date);
            if (parsed == null || !date.startsWith(monthKey + "-")) continue;
            double hours = item.optDouble("hours", 0d);
            if (Double.isNaN(hours) || Double.isInfinite(hours)) continue;
            plannedDates.put(date, Math.max(0d, Math.min(24d, hours)));
        }
        if (plannedDates.isEmpty()) {
            throw new IllegalStateException("No office dates are saved for " + monthKey
                    + ". Add the dates in DayFlow's Office month plan first.");
        }

        Map<String, JSONObject> logByDate = new HashMap<>();
        ArrayList<Sample> inboundSamples = new ArrayList<>();
        ArrayList<Sample> outboundSamples = new ArrayList<>();
        LocalDate cutoff = today.minusDays(HISTORY_DAYS);
        int invalidIntervals = 0;
        int ignoredLongIntervals = 0;
        StringBuilder historyLines = new StringBuilder();

        for (int i = 0; officeLog != null && i < officeLog.length(); i++) {
            JSONObject day = officeLog.optJSONObject(i);
            if (day == null) continue;
            String date = day.optString("date", "");
            LocalDate parsed = parseDate(date);
            if (parsed == null) continue;
            logByDate.put(date, day);
            if (parsed.isBefore(cutoff) || parsed.isAfter(today)) continue;

            long inStart = day.optLong("commuteInStart", 0L);
            long inEnd = day.optLong("commuteInEnd", 0L);
            long outStart = day.optLong("commuteOutStart", 0L);
            long outEnd = day.optLong("commuteOutEnd", 0L);

            Sample inbound = createSample(date, parsed.getDayOfWeek(), "home to work",
                    inStart, inEnd, nowMillis);
            Sample outbound = createSample(date, parsed.getDayOfWeek(), "work to home",
                    outStart, outEnd, nowMillis);

            if (inStart > 0L && inEnd > 0L) {
                if (inEnd <= inStart) invalidIntervals++;
                else if (inbound == null) ignoredLongIntervals++;
                else inboundSamples.add(inbound);
            }
            if (outStart > 0L && outEnd > 0L) {
                if (outEnd <= outStart) invalidIntervals++;
                else if (outbound == null) ignoredLongIntervals++;
                else outboundSamples.add(outbound);
            }
        }

        ArrayList<Sample> chronological = new ArrayList<>();
        chronological.addAll(inboundSamples);
        chronological.addAll(outboundSamples);
        Collections.sort(chronological, (a, b) -> {
            int byDate = a.date.compareTo(b.date);
            return byDate != 0 ? byDate : a.direction.compareTo(b.direction);
        });
        for (Sample sample : chronological) {
            historyLines.append("- ").append(sample.date).append(" (")
                    .append(sample.weekday).append("), ").append(sample.direction)
                    .append(", departure ").append(sample.departure)
                    .append(", duration ").append(formatMinutes(sample.minutes)).append("\n");
        }

        int actualMinutes = 0;
        int forecastMedianMinutes = 0;
        int forecastP80Minutes = 0;
        int actualLegs = 0;
        int forecastLegs = 0;
        int unavailableForecastLegs = 0;
        int missingPastLegs = 0;
        int futureOfficeDates = 0;
        StringBuilder projectionLines = new StringBuilder();

        for (Map.Entry<String, Double> entry : plannedDates.entrySet()) {
            String date = entry.getKey();
            LocalDate parsed = parseDate(date);
            JSONObject actualDay = logByDate.get(date);
            Long actualInbound = completedDuration(actualDay, "commuteInStart", "commuteInEnd", nowMillis);
            Long actualOutbound = completedDuration(actualDay, "commuteOutStart", "commuteOutEnd", nowMillis);

            projectionLines.append("- ").append(date).append(" (")
                    .append(parsed == null ? "unknown weekday" : parsed.getDayOfWeek())
                    .append("), planned office time ")
                    .append(String.format(Locale.US, "%.2f h", entry.getValue()));

            if (actualInbound != null) {
                actualMinutes += actualInbound.intValue();
                actualLegs++;
                projectionLines.append("; home to work actual ")
                        .append(formatMinutes(actualInbound.intValue()));
            }
            if (actualOutbound != null) {
                actualMinutes += actualOutbound.intValue();
                actualLegs++;
                projectionLines.append("; work to home actual ")
                        .append(formatMinutes(actualOutbound.intValue()));
            }

            if (parsed != null && parsed.isBefore(today)) {
                if (actualInbound == null) missingPastLegs++;
                if (actualOutbound == null) missingPastLegs++;
            }

            if (parsed != null && !parsed.isBefore(today)) {
                futureOfficeDates++;
                Stats inStats = actualInbound == null
                        ? statsForDate(inboundSamples, parsed.getDayOfWeek()) : null;
                Stats outStats = actualOutbound == null
                        ? statsForDate(outboundSamples, parsed.getDayOfWeek()) : null;

                if (actualInbound == null) {
                    if (inStats == null) {
                        unavailableForecastLegs++;
                        projectionLines.append("; home to work forecast unavailable");
                    } else {
                        forecastLegs++;
                        forecastMedianMinutes += inStats.median;
                        forecastP80Minutes += inStats.p80;
                        projectionLines.append("; home to work forecast ")
                                .append(formatMinutes(inStats.median)).append(" median / ")
                                .append(formatMinutes(inStats.p80)).append(" 80th percentile (")
                                .append(inStats.count).append(" ")
                                .append(inStats.weekdaySpecific ? "weekday-specific samples)" : "overall fallback samples)");
                    }
                }
                if (actualOutbound == null) {
                    if (outStats == null) {
                        unavailableForecastLegs++;
                        projectionLines.append("; work to home forecast unavailable");
                    } else {
                        forecastLegs++;
                        forecastMedianMinutes += outStats.median;
                        forecastP80Minutes += outStats.p80;
                        projectionLines.append("; work to home forecast ")
                                .append(formatMinutes(outStats.median)).append(" median / ")
                                .append(formatMinutes(outStats.p80)).append(" 80th percentile (")
                                .append(outStats.count).append(" ")
                                .append(outStats.weekdaySpecific ? "weekday-specific samples)" : "overall fallback samples)");
                    }
                }
            }
            projectionLines.append("\n");
        }

        StringBuilder routineLines = new StringBuilder();
        int routineDateCount = 0;
        for (int i = 0; routines != null && i < routines.length(); i++) {
            JSONObject routine = routines.optJSONObject(i);
            if (routine == null) continue;
            String date = routine.optString("date", "");
            JSONArray blocks = routine.optJSONArray("blocks");
            if (blocks == null || blocks.length() == 0) continue;
            routineDateCount++;
            routineLines.append("Date ").append(date)
                    .append(" | arrangement=").append(routine.optString("arrangement", "not recorded"))
                    .append("\n");
            for (int j = 0; j < blocks.length(); j++) {
                JSONObject block = blocks.optJSONObject(j);
                if (block == null) continue;
                routineLines.append("  - ").append(block.optString("start", "?"))
                        .append("–").append(block.optString("end", "?"))
                        .append(" | ").append(block.optString("title", "Untitled block"))
                        .append(" | rigidity=").append(block.optString("rigidity", "unknown"))
                        .append(" | priority=").append(block.optString("priority", "unknown"))
                        .append("\n");
            }
        }

        double straightLineKm = haversineKm(homeLatitude, homeLongitude,
                workLatitude, workLongitude);
        StringBuilder prompt = new StringBuilder();
        prompt.append("DAYFLOW COMMUTE ANALYSIS AND PROJECTION\n");
        prompt.append("Selected office-plan month: ").append(monthKey).append("\n");
        prompt.append("Current local date: ").append(todayKey).append("\n");
        prompt.append("\nHOME POINT (user-saved): ").append(safeLabel(homeLabel)).append("\n");
        prompt.append(String.format(Locale.US, "Coordinates: %.6f, %.6f\n",
                homeLatitude, homeLongitude));
        prompt.append("WORK POINT (user-saved): ").append(safeLabel(workLabel)).append("\n");
        prompt.append(String.format(Locale.US, "Coordinates: %.6f, %.6f\n",
                workLatitude, workLongitude));
        prompt.append(String.format(Locale.US,
                "Straight-line distance between points: %.2f km (not road distance).\n", straightLineKm));
        prompt.append("\nDATA COVERAGE AND PROJECTION (calculated by DayFlow; authoritative):\n");
        prompt.append("Commute history window: the most recent ").append(HISTORY_DAYS)
                .append(" days through today. Only completed commute legs are samples.\n");
        prompt.append("Completed home-to-work samples: ").append(inboundSamples.size()).append("\n");
        prompt.append("Completed work-to-home samples: ").append(outboundSamples.size()).append("\n");
        prompt.append("Invalid intervals ignored: ").append(invalidIntervals).append("\n");
        prompt.append("Intervals longer than ").append(MAX_REASONABLE_LEG_MINUTES)
                .append(" minutes ignored as likely logging errors: ").append(ignoredLongIntervals).append("\n");
        prompt.append("Saved office dates in selected month: ").append(plannedDates.size()).append("\n");
        prompt.append("Dates today or later in the selected month: ").append(futureOfficeDates).append("\n");
        prompt.append("Completed commute legs on planned dates: ").append(actualLegs)
                .append(" totalling ").append(formatMinutes(actualMinutes)).append("\n");
        prompt.append("Forecast missing future legs: ").append(forecastLegs).append("\n");
        prompt.append("Future legs with insufficient history for a forecast: ")
                .append(unavailableForecastLegs).append("\n");
        prompt.append("Past planned commute legs without a completed record (not counted as zero): ")
                .append(missingPastLegs).append("\n");
        prompt.append("Forecast is based on observed median and 80th-percentile duration, not live traffic.\n");
        if (forecastLegs > 0) {
            prompt.append("Forecast duration for missing future legs: ")
                    .append(formatMinutes(forecastMedianMinutes)).append(" median total / ")
                    .append(formatMinutes(forecastP80Minutes)).append(" 80th-percentile total.\n");
        } else {
            prompt.append("No future commute leg currently has sufficient samples for a numerical forecast.\n");
        }
        if (unavailableForecastLegs == 0 && missingPastLegs == 0) {
            prompt.append("Projected selected-month commute total (actual completed legs plus forecast missing future legs): ")
                    .append(formatMinutes(actualMinutes + forecastMedianMinutes)).append(" median / ")
                    .append(formatMinutes(actualMinutes + forecastP80Minutes))
                    .append(" 80th-percentile scenario.\n");
        } else {
            prompt.append("Known subtotal only (actual completed legs plus forecastable future legs): ")
                    .append(formatMinutes(actualMinutes + forecastMedianMinutes))
                    .append(" median / ").append(formatMinutes(actualMinutes + forecastP80Minutes))
                    .append(" 80th-percentile scenario; this is not a complete month total because ")
                    .append(unavailableForecastLegs).append(" future legs cannot be estimated and ")
                    .append(missingPastLegs).append(" past legs were not logged. Missing historical records are unknown, not zero.\n");
        }

        prompt.append("\nRECENT COMPLETED COMMUTE OBSERVATIONS (local departure time):\n");
        if (historyLines.length() == 0) prompt.append("No valid completed commute observations in the history window.\n");
        else prompt.append(historyLines);
        prompt.append("\nOFFICE DATES AND INDIVIDUAL COMMUTE BREAKDOWN:\n");
        prompt.append(projectionLines);
        prompt.append("\nSAVED ROUTINE BLOCKS FOR PLANNED OFFICE DATES (read-only snapshots):\n");
        if (routineDateCount == 0) {
            prompt.append("No date-specific saved routine blocks were available for the selected office dates. Do not infer a routine from missing dates.\n");
        } else {
            prompt.append("Routine snapshots available for ").append(routineDateCount).append(" office date(s).\n");
            prompt.append(routineLines);
        }

        prompt.append("\nINSTRUCTIONS FOR GEMINI:\n");
        prompt.append("You are reviewing an existing commute pattern, not controlling a scheduler. DayFlow has no permission or operation to edit the timetable from this analysis. Treat every routine block and office date as read-only. Do not alter, add, delete, move, reschedule, or rewrite any routine block or office date, and do not present a changed schedule as an action. There is no apply button.\n");
        prompt.append("Use the saved home/work points to understand the endpoints and their approximate geographic relationship. Coordinates and straight-line distance do not reveal road routes, public-transport options, congestion or current traffic. Do not claim to have checked live traffic, a road route or a map unless supplied explicitly.\n");
        prompt.append("Use DayFlow's projected durations as the numerical baseline. Explain sample sizes and confidence. Do not manufacture a forecast when the data says insufficient history. Keep the median and 80th-percentile scenario separate.\n");
        prompt.append("Analyse inbound and outbound legs separately, including weekday and departure-time patterns. Identify the time windows associated with longer or shorter observed trips only when the actual observations support that conclusion.\n");
        prompt.append("Relate suggestions to the saved routine: identify whether the current routine creates a hard arrival/departure constraint, whether the commute is adjacent to a fixed block, or whether there is already a flexible/free window. Distinguish confirmed constraints from possibilities.\n");
        prompt.append("Suggest practical ways to reduce travel time without changing the saved routine: for example, a different commute departure window, route/mode comparison the user can verify, or using an already-existing gap. Do not suggest edits to the routine itself, and do not assume employer flexibility, transit availability, parking, school runs, or other unprovided facts. Quantify potential savings only when supported by the recorded data; otherwise call the benefit unquantified and say what needs to be measured.\n");
        prompt.append("Structure the response as: (1) baseline and data quality, (2) projected commute for the selected month, (3) what the home/work points imply and do not imply, (4) up to five evidence-based suggestions, and (5) what additional records would improve the forecast. Be specific, concise and candid.");
        return prompt.toString();
    }

    private static Sample createSample(String date, DayOfWeek weekday, String direction,
            long startMillis, long endMillis, long nowMillis) {
        if (startMillis <= 0L || endMillis <= startMillis || endMillis > nowMillis) return null;
        long roundedMinutes = Math.round((endMillis - startMillis) / 60000d);
        if (roundedMinutes <= 0L || roundedMinutes > MAX_REASONABLE_LEG_MINUTES) return null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startMillis);
        String departure = String.format(Locale.US, "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
        return new Sample(date, weekday, direction, departure, (int) roundedMinutes);
    }

    private static Long completedDuration(JSONObject day, String startKey, String endKey, long nowMillis) {
        if (day == null) return null;
        long start = day.optLong(startKey, 0L);
        long end = day.optLong(endKey, 0L);
        if (start <= 0L || end <= start || end > nowMillis) return null;
        long minutes = Math.round((end - start) / 60000d);
        if (minutes <= 0L || minutes > MAX_REASONABLE_LEG_MINUTES) return null;
        return minutes;
    }

    private static Stats statsForDate(List<Sample> samples, DayOfWeek day) {
        ArrayList<Sample> sameDay = new ArrayList<>();
        for (Sample sample : samples) {
            if (sample.weekday == day) sameDay.add(sample);
        }
        if (sameDay.size() >= MIN_WEEKDAY_SAMPLES) return new Stats(sameDay, true);
        if (samples.isEmpty()) return null;
        return new Stats(samples, false);
    }

    private static int percentileMedian(List<Integer> sorted) {
        if (sorted.isEmpty()) return 0;
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return (int) Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2d);
    }

    private static int percentile(List<Integer> sorted, double fraction) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(fraction * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatMinutes(int minutes) {
        int safe = Math.max(0, minutes);
        return (safe / 60) + "h " + String.format(Locale.US, "%02dm", safe % 60);
    }

    private static String safeLabel(String value) {
        if (value == null || value.trim().isEmpty()) return "Not labelled";
        return value.trim().replace('\n', ' ').replace('\r', ' ');
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0088d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);
        return earthRadiusKm * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }
}
