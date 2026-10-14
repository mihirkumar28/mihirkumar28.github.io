package com.mihirkumar.dayflow;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoadmapMonthlyPlanTest {
    @Test public void coversPlannedMonthRange() {
        assertEquals(16, RoadmapMonthlyPlan.monthCount());
        assertEquals("2026-10", RoadmapMonthlyPlan.monthKeyAt(0));
        assertEquals("2027-06", RoadmapMonthlyPlan.monthKeyAt(8));
        assertEquals("2028-01", RoadmapMonthlyPlan.monthKeyAt(15));
        assertNull(RoadmapMonthlyPlan.monthKeyAt(-1));
        assertNull(RoadmapMonthlyPlan.monthKeyAt(16));
    }

    @Test public void includesAllOctoberTracks() {
        assertEquals(3, RoadmapMonthlyPlan.rowsForMonth("2026-10").length);
        assertNotNull(RoadmapMonthlyPlan.findGoal("2026-10", "General Studies"));
        assertNotNull(RoadmapMonthlyPlan.findGoal("2026-10", "Maths Optional"));
        assertNotNull(RoadmapMonthlyPlan.findGoal("2026-10", "Software Interview Prep"));
    }

    @Test public void gsSequenceMatchesSavedPrelimsPlan() {
        assertTrue(RoadmapMonthlyPlan.findGoal("2026-10", "General Studies")[1].contains("Polity"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2026-11", "General Studies")[1].contains("History"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2026-12", "General Studies")[1].contains("Environment"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-01", "General Studies")[1].contains("Geography"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-02", "General Studies")[1].contains("Economy"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-03", "General Studies")[1].contains("Science"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-04", "CSE Revision & Tests")[2].contains("700"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-05", "CSE Revision & Tests")[2].contains("23 May 2027"));
    }

    @Test public void softwarePlanIsNineMonthsThenLabelsExtension() {
        for (int i = 0; i < 9; i++) {
            assertNotNull(RoadmapMonthlyPlan.findGoal(RoadmapMonthlyPlan.monthKeyAt(i), "Software Interview Prep"));
        }
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-03", "Software Interview Prep")[1].contains("Tier 3/4"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-06", "Software Interview Prep")[1].contains("Month 9"));
        assertTrue(RoadmapMonthlyPlan.findGoal("2027-07", "Software Interview Prep")[2].contains("Extension beyond"));
    }

    @Test public void defensiveCopiesAndInvalidMonths() {
        String[][] rows = RoadmapMonthlyPlan.rowsForMonth("2026-10");
        rows[0][1] = "changed";
        assertTrue(RoadmapMonthlyPlan.findGoal("2026-10", "General Studies")[1].contains("Polity"));
        assertEquals(0, RoadmapMonthlyPlan.rowsForMonth("bad").length);
        assertEquals(0, RoadmapMonthlyPlan.rowsForMonth("2028-02").length);
        assertNull(RoadmapMonthlyPlan.findGoal("2026-10", "Unknown"));
    }
}
