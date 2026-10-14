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
