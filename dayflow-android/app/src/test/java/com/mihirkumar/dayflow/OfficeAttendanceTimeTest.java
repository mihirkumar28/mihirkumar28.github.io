package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static org.junit.Assert.*;

public class OfficeAttendanceTimeTest {
    private static final long HOUR = 60L * 60L * 1000L;

    @Test public void editingCheckInChangesTheCalculatedOfficeTotal() throws Exception {
        JSONObject session = new JSONObject().put("in", 9 * HOUR).put("out", 17 * HOUR);
        JSONArray sessions = new JSONArray().put(session);
        assertEquals(8 * HOUR, OfficeAttendanceTime.officeDuration(sessions, 20 * HOUR));

        OfficeAttendanceTime.setSessionTimestamp(session, OfficeAttendanceTime.SESSION_IN, 10 * HOUR);

        assertEquals(7 * HOUR, OfficeAttendanceTime.officeDuration(
                new JSONArray(sessions.toString()), 20 * HOUR));
        assertEquals(10 * HOUR, session.optLong("in"));
        assertFalse("UI-only officeIn field must never be written", session.has("officeIn"));
    }

    @Test public void editingCheckOutChangesTheCalculatedOfficeTotal() throws Exception {
        JSONObject session = new JSONObject().put("in", 9 * HOUR).put("out", 17 * HOUR);
        OfficeAttendanceTime.setSessionTimestamp(session, OfficeAttendanceTime.SESSION_OUT, 16 * HOUR);
        assertEquals(7 * HOUR, OfficeAttendanceTime.officeDuration(new JSONArray().put(session), 20 * HOUR));
        assertEquals(16 * HOUR, session.optLong("out"));
    }

    @Test public void invalidHistoricalIntervalsAreFlaggedForCorrection() throws Exception {
        JSONObject day = new JSONObject().put("sessions", new JSONArray()
                .put(new JSONObject().put("in", 9 * HOUR).put("out", 5 * HOUR)))
                .put("commuteInStart", 8 * HOUR)
                .put("commuteInEnd", 9 * HOUR)
                .put("commuteOutStart", 18 * HOUR)
                .put("commuteOutEnd", 17 * HOUR);

        assertTrue(OfficeAttendanceTime.hasInvalidRecordedTimes(day));
        assertTrue(OfficeAttendanceTime.hasInvalidClosedInterval(9 * HOUR, 5 * HOUR));
        assertFalse(OfficeAttendanceTime.hasInvalidClosedInterval(9 * HOUR, 0));
        assertFalse(OfficeAttendanceTime.hasInvalidClosedInterval(9 * HOUR, 10 * HOUR));

        day.getJSONArray("sessions").getJSONObject(0).put("out", 17 * HOUR);
        day.put("commuteOutEnd", 19 * HOUR);
        assertFalse(OfficeAttendanceTime.hasInvalidRecordedTimes(day));
    }

    @Test public void openSessionRunsUntilNowButClosedInvalidSessionDoesNot() throws Exception {
        JSONObject open = new JSONObject().put("in", 9 * HOUR).put("out", 0);
        JSONObject invalidClosed = new JSONObject().put("in", 17 * HOUR).put("out", 16 * HOUR);
        JSONArray sessions = new JSONArray().put(open).put(invalidClosed);
        assertEquals(3 * HOUR, OfficeAttendanceTime.officeDuration(sessions, 12 * HOUR));
    }

    @Test public void editingCommuteStartAndEndUpdatesCommuteTotal() throws Exception {
        JSONObject day = new JSONObject().put("commuteInStart", 8 * HOUR)
                .put("commuteInEnd", 9 * HOUR)
                .put("commuteOutStart", 17 * HOUR)
                .put("commuteOutEnd", 18 * HOUR);
        assertEquals(2 * HOUR, OfficeAttendanceTime.commuteDuration(day, 20 * HOUR));
        day.put("commuteInStart", 8 * HOUR + 30 * 60 * 1000L);
        day.put("commuteOutEnd", 18 * HOUR + 30 * 60 * 1000L);
        assertEquals(2 * HOUR, OfficeAttendanceTime.commuteDuration(day, 20 * HOUR));
    }

    @Test public void editedTimeAlwaysBelongsToTheSelectedDate() {
        long timestamp = OfficeAttendanceTime.timestampForDayAndTime("2026-10-08", 8, 35);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        assertEquals(2026, c.get(Calendar.YEAR));
        assertEquals(Calendar.OCTOBER, c.get(Calendar.MONTH));
        assertEquals(8, c.get(Calendar.DAY_OF_MONTH));
        assertEquals(8, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(35, c.get(Calendar.MINUTE));
        assertEquals(0, c.get(Calendar.SECOND));
        assertEquals(0, c.get(Calendar.MILLISECOND));
    }

    @Test public void editingDateOlderThanTheVisibleLedgerPreservesThatHistoricalDate() throws Exception {
        Calendar selected = Calendar.getInstance();
        selected.add(Calendar.DAY_OF_YEAR, -90);
        String oldDayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selected.getTime());

        long timestamp = OfficeAttendanceTime.timestampForDayAndTime(oldDayKey, 7, 45);
        Calendar actual = Calendar.getInstance();
        actual.setTimeInMillis(timestamp);

        assertEquals(selected.get(Calendar.YEAR), actual.get(Calendar.YEAR));
        assertEquals(selected.get(Calendar.MONTH), actual.get(Calendar.MONTH));
        assertEquals(selected.get(Calendar.DAY_OF_MONTH), actual.get(Calendar.DAY_OF_MONTH));
        assertEquals(7, actual.get(Calendar.HOUR_OF_DAY));
        assertEquals(45, actual.get(Calendar.MINUTE));
    }

    @Test public void sessionUpdaterRejectsWrongFieldNames() throws Exception {
        try {
            OfficeAttendanceTime.setSessionTimestamp(new JSONObject(), "officeIn", 123L);
            fail("Incorrect UI-only field name should be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("'in' or 'out'"));
        }
    }
}
