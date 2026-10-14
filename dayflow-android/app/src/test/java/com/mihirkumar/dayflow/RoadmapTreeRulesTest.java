package com.mihirkumar.dayflow;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoadmapTreeRulesTest {
    @Test public void onlyPastIncompleteGoalsAreOverdue() {
        assertTrue(RoadmapTreeRules.isOverdue("goal", false, "2026-09", "2026-10-14"));
        assertFalse(RoadmapTreeRules.isOverdue("goal", false, "2026-10", "2026-10-14"));
        assertFalse(RoadmapTreeRules.isOverdue("goal", false, "2026-11", "2026-10-14"));
        assertFalse(RoadmapTreeRules.isOverdue("goal", true, "2026-09", "2026-10-14"));
    }

    @Test public void milestoneDeadlineUsesExactDate() {
        assertTrue(RoadmapTreeRules.isOverdue("milestone", false, "2026-10-13", "2026-10-14"));
        assertFalse(RoadmapTreeRules.isOverdue("milestone", false, "2026-10-14", "2026-10-14"));
        assertFalse(RoadmapTreeRules.isOverdue("milestone", false, "2026-10-15", "2026-10-14"));
        assertFalse(RoadmapTreeRules.isOverdue("milestone", true, "2026-10-01", "2026-10-14"));
    }

    @Test public void topicsWithoutDeadlinesAreNeverGuessedOverdue() {
        assertFalse(RoadmapTreeRules.isOverdue("topic", false, null, "2026-10-14"));
        assertFalse(RoadmapTreeRules.isOverdue("topic", false, "", "2026-10-14"));
    }

    @Test public void statusFiltersApplyOnlyToActionableItems() {
        assertTrue(RoadmapTreeRules.matchesFilter("All", "career", false, null, "2026-10-14"));
        assertFalse(RoadmapTreeRules.matchesFilter("Open", "career", false, null, "2026-10-14"));
        assertTrue(RoadmapTreeRules.matchesFilter("Open", "goal", false, "2026-10", "2026-10-14"));
        assertFalse(RoadmapTreeRules.matchesFilter("Done", "goal", false, "2026-10", "2026-10-14"));
        assertTrue(RoadmapTreeRules.matchesFilter("Done", "milestone", true, "2026-10-01", "2026-10-14"));
        assertTrue(RoadmapTreeRules.matchesFilter("Overdue", "goal", false, "2026-09", "2026-10-14"));
        assertFalse(RoadmapTreeRules.matchesFilter("Overdue", "topic", false, "", "2026-10-14"));
    }

    @Test public void branchProgressCombinesCompletedAndIncompleteLeaves() {
        int[] total = RoadmapTreeRules.combineProgress(
                RoadmapTreeRules.leafProgress(true),
                RoadmapTreeRules.leafProgress(false));
        total = RoadmapTreeRules.combineProgress(total, new int[]{1, 2});
        assertArrayEquals(new int[]{2, 4}, total);
        assertArrayEquals(new int[]{0, 0}, RoadmapTreeRules.combineProgress(null, null));
    }
}