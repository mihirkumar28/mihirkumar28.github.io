package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertTrue;

public class GeminiCommuteFactsTest {
    private long timestamp(String value) throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.parse(value).getTime();
    }

    @Test
    public void buildsWeekdayProjectionFromRecordedLegsAndKeepsRoutineReadOnly() throws Exception {
        JSONArray log = new JSONArray();
        String[] dates = {"2026-10-05", "2026-10-12", "2026-10-19"};
        int[] inbound = {60, 70, 50};
        int[] outbound = {55, 65, 45};
        for (int i = 0; i < dates.length; i++) {
            log.put(new JSONObject()
                    .put("date", dates[i])
                    .put("commuteInStart", timestamp(dates[i] + " 08:00"))
                    .put("commuteInEnd", timestamp(dates[i] + " 08:00") + inbound[i] * 60_000L)
                    .put("commuteOutStart", timestamp(dates[i] + " 17:00"))
                    .put("commuteOutEnd", timestamp(dates[i] + " 17:00") + outbound[i] * 60_000L));
        }
        JSONArray plan = new JSONArray()
                .put(new JSONObject().put("date", "2026-11-02").put("hours", 8))
                .put(new JSONObject().put("date", "2026-11-09").put("hours", 8));
        JSONArray routines = new JSONArray().put(new JSONObject()
                .put("date", "2026-11-02")
                .put("arrangement", "office")
                .put("blocks", new JSONArray().put(new JSONObject()
                        .put("start", "09:00").put("end", "18:00")
                        .put("title", "Work").put("rigidity", "fixed").put("priority", "high"))));

        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.OCTOBER, 30, 12, 0, 0);
        now.set(Calendar.MILLISECOND, 0);
        String prompt = GeminiCommuteFacts.buildPrompt("2026-11", "2026-10-30",
                now.getTimeInMillis(), plan, log, routines,
                "Home", 18.5204, 73.8567, "Office", 18.5314, 73.8446);

        assertTrue(prompt.contains("Completed home-to-work samples: 3"));
        assertTrue(prompt.contains("Completed work-to-home samples: 3"));
        assertTrue(prompt.contains("home to work forecast 1h 00m median / 1h 10m 80th percentile"));
        assertTrue(prompt.contains("work to home forecast 0h 55m median / 1h 05m 80th percentile"));
        assertTrue(prompt.contains("Projected selected-month commute total"));
        assertTrue(prompt.contains("Home"));
        assertTrue(prompt.contains("Office"));
        assertTrue(prompt.contains("do not alter, add, delete, move, reschedule"));
    }

    @Test
    public void doesNotInventProjectionWhenThereAreNoCompletedSamples() throws Exception {
        JSONArray plan = new JSONArray()
                .put(new JSONObject().put("date", "2026-11-02").put("hours", 8));
        String prompt = GeminiCommuteFacts.buildPrompt("2026-11", "2026-10-30",
                System.currentTimeMillis(), plan, new JSONArray(), new JSONArray(),
                "Home", 18.5204, 73.8567, "Office", 18.5314, 73.8446);
        assertTrue(prompt.contains("No valid completed commute observations in the history window."));
        assertTrue(prompt.contains("No future commute leg currently has sufficient samples for a numerical forecast."));
        assertTrue(prompt.contains("full-month projection unavailable"));
    }

    @Test
    public void haversineUsesStraightLineDistance() {
        double distance = GeminiCommuteFacts.haversineKm(0d, 0d, 0d, 1d);
        assertTrue(distance > 111d && distance < 112d);
    }
}
