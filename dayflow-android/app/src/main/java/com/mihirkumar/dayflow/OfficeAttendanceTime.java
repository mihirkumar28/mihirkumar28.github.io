package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Pure attendance timestamp and duration rules shared by the UI and unit tests. */
final class OfficeAttendanceTime {
    static final String SESSION_IN = "in";
    static final String SESSION_OUT = "out";

    private OfficeAttendanceTime() {}

    static void setSessionTimestamp(JSONObject session, String field, long timestamp) throws JSONException {
        if (!SESSION_IN.equals(field) && !SESSION_OUT.equals(field)) {
            throw new IllegalArgumentException("Session time must use the 'in' or 'out' field.");
        }
        session.put(field, timestamp);
    }

    /** An interval is open only when its end is absent (zero), not when its end precedes its start. */
    static long intervalDuration(long start, long end, long now) {
        if (start <= 0) return 0L;
        long effectiveEnd = end == 0 ? now : end;
        return effectiveEnd > start ? effectiveEnd - start : 0L;
    }

    /** Returns true when a completed interval has an end at or before its start. */
    static boolean hasInvalidClosedInterval(long start, long end) {
        return start > 0L && end > 0L && end <= start;
    }

    static boolean hasInvalidOfficeSessions(JSONArray sessions) {
        if (sessions == null) return false;
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            if (session != null && hasInvalidClosedInterval(
                    session.optLong(SESSION_IN, 0L), session.optLong(SESSION_OUT, 0L))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasInvalidCommuteIntervals(JSONObject day) {
        if (day == null) return false;
        return hasInvalidClosedInterval(day.optLong("commuteInStart", 0L), day.optLong("commuteInEnd", 0L))
                || hasInvalidClosedInterval(day.optLong("commuteOutStart", 0L), day.optLong("commuteOutEnd", 0L));
    }

    static boolean hasInvalidRecordedTimes(JSONObject day) {
        return day != null && (hasInvalidOfficeSessions(day.optJSONArray("sessions"))
                || hasInvalidCommuteIntervals(day));
    }

    static long officeDuration(JSONArray sessions, long now) {
        if (sessions == null) return 0L;
        long total = 0L;
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            if (session == null) continue;
            total += intervalDuration(
                    session.optLong(SESSION_IN, 0L),
                    session.optLong(SESSION_OUT, 0L),
                    now);
        }
        return total;
    }

    static long commuteDuration(JSONObject day, long now) {
        if (day == null) return 0L;
        return intervalDuration(day.optLong("commuteInStart", 0L), day.optLong("commuteInEnd", 0L), now)
                + intervalDuration(day.optLong("commuteOutStart", 0L), day.optLong("commuteOutEnd", 0L), now);
    }

    /**
     * Returns {officeMillis, commuteMillis, recordedDays} for inclusive yyyy-MM-dd bounds.
     * Only today's open sessions accrue time; old open sessions are never carried forward.
     */
    /** True if the day has at least one saved office or commute timestamp. */
    static boolean hasRecordedData(JSONObject day) {
        if (day == null) return false;
        JSONArray sessions = day.optJSONArray("sessions");
        if (sessions != null) {
            for (int i = 0; i < sessions.length(); i++) {
                JSONObject session = sessions.optJSONObject(i);
                if (session != null && (session.optLong(SESSION_IN, 0L) > 0L
                        || session.optLong(SESSION_OUT, 0L) > 0L)) return true;
            }
        }
        return day.optLong("commuteInStart", 0L) > 0L
                || day.optLong("commuteInEnd", 0L) > 0L
                || day.optLong("commuteOutStart", 0L) > 0L
                || day.optLong("commuteOutEnd", 0L) > 0L;
    }

    static long[] totalsForDateRange(JSONArray records, String fromDateInclusive,
                                     String throughDateInclusive, String todayKey, long now) {
        long officeTotal = 0L;
        long commuteTotal = 0L;
        long recordedDays = 0L;
        if (records == null || fromDateInclusive == null || throughDateInclusive == null) {
            return new long[]{0L, 0L, 0L};
        }
        for (int i = 0; i < records.length(); i++) {
            JSONObject day = records.optJSONObject(i);
            if (day == null) continue;
            String key = day.optString("date", "");
            if (key.compareTo(fromDateInclusive) < 0 || key.compareTo(throughDateInclusive) > 0) continue;
            long referenceTime = key.equals(todayKey) ? now : 0L;
            long office = officeDuration(day.optJSONArray("sessions"), referenceTime);
            long commute = commuteDuration(day, referenceTime);
            officeTotal += office;
            commuteTotal += commute;
            if (office > 0L || commute > 0L) recordedDays++;
        }
        return new long[]{officeTotal, commuteTotal, recordedDays};
    }

    static long timestampForDayAndTime(String dayKey, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        formatter.setLenient(false);
        try {
            Date selectedDate = formatter.parse(dayKey);
            if (selectedDate == null) throw new IllegalArgumentException("Invalid date: " + dayKey);
            calendar.setTime(selectedDate);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid date: " + dayKey, e);
        }
        calendar.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        calendar.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
