package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeminiScheduleFactsTest {
    @Test
    public void routineFactsDoNotInferSleepFromFirstTaskGap() throws Exception {
        JSONArray blocks = new JSONArray()
                .put(new JSONObject().put("start", "22:00").put("end", "22:15")
                        .put("title", "Sleep").put("status", "pending")
                        .put("rigidity", "fixed").put("priority", "important"))
                .put(new JSONObject().put("start", "05:00").put("end", "06:00")
                        .put("title", "Morning routine").put("status", "pending")
                        .put("rigidity", "fixed").put("priority", "normal"));

        String prompt = GeminiScheduleFacts.routinePrompt("2026-10-30", "wfh", blocks);

        assertTrue(prompt.contains("Sleep 22:00–22:15 = 0h 15m"));
        assertTrue(prompt.contains("Do not infer actual time in bed, sleep duration, or wake-up time"));
        assertFalse(prompt.contains("6h 45m sleep"));
    }

    @Test
    public void routineFactsIdentifyOnlyActualOverlaps() throws Exception {
        JSONArray blocks = new JSONArray()
                .put(new JSONObject().put("start", "08:00").put("end", "10:00")
                        .put("title", "GS").put("status", "pending"))
                .put(new JSONObject().put("start", "09:30").put("end", "11:00")
                        .put("title", "Maths").put("status", "pending"));

        String prompt = GeminiScheduleFacts.routinePrompt("2026-10-30", "wfh", blocks);

        assertTrue(prompt.contains("Overlapping block pairs: 1"));
        assertTrue(prompt.contains("overlaps Maths"));
        assertTrue(prompt.contains("0h 30m"));
    }

    @Test
    public void separateNonOverlappingWorkoutsAreNotPresentedAsConflicts() throws Exception {
        JSONArray blocks = new JSONArray()
                .put(new JSONObject().put("start", "12:30").put("end", "14:00")
                        .put("title", "Gym and lunch").put("status", "pending"))
                .put(new JSONObject().put("start", "18:30").put("end", "19:30")
                        .put("title", "Workout and swimming").put("status", "pending"));

        String prompt = GeminiScheduleFacts.routinePrompt("2026-10-30", "wfh", blocks);

        assertTrue(prompt.contains("Overlapping block pairs: 0"));
        assertTrue(prompt.contains("Do not treat two separate exercise blocks as a conflict"));
    }

    @Test
    public void monthlyOfficePlanUsesExactDatesHoursAndPolicyRules() throws Exception {
        JSONArray plan = new JSONArray();
        for (int day = 1; day <= 8; day++) {
            plan.put(new JSONObject()
                    .put("date", String.format(java.util.Locale.US, "2026-10-%02d", day))
                    .put("hours", 6.5));
        }

        String prompt = GeminiScheduleFacts.officeMonthPrompt("2026-10", plan, new JSONArray());

        assertTrue(prompt.contains("Planned office dates: 8"));
        assertTrue(prompt.contains("52.00 h"));
        assertTrue(prompt.contains("20.00 h"));
        assertTrue(prompt.contains("72.00 h"));
        assertTrue(prompt.contains("MET: at least 8 dates and at least 52 planned office hours"));
    }

    @Test
    public void nineOfficeDatesWaiveTheFiftyTwoHourMinimum() throws Exception {
        JSONArray plan = new JSONArray();
        for (int day = 1; day <= 9; day++) {
            plan.put(new JSONObject()
                    .put("date", String.format(java.util.Locale.US, "2026-10-%02d", day))
                    .put("hours", 4.0));
        }

        String prompt = GeminiScheduleFacts.officeMonthPrompt("2026-10", plan, new JSONArray());

        assertTrue(prompt.contains("Planned office dates: 9"));
        assertTrue(prompt.contains("36.00 h"));
        assertTrue(prompt.contains("MET: 9 or more planned office dates; the 52-hour minimum is waived"));
    }
}
