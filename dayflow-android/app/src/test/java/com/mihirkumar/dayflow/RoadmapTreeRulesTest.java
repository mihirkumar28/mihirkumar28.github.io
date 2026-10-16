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

    @Test public void goalReorderingPreservesLogicalContinuity() {
        assertTrue(RoadmapTreeRules.canReorderGoals("General Studies", 0L, "2026-10", false,
                "General Studies", 0L, "2026-11", false));
        assertTrue(RoadmapTreeRules.canReorderGoals("Maths Optional", 0L, "2026-10", false,
                "Maths Optional", 0L, "2026-10", false));
        assertTrue(RoadmapTreeRules.canReorderGoals("General Studies", 42L, "2026-10", false,
                "General Studies", 42L, "2026-10", false));
        assertFalse(RoadmapTreeRules.canReorderGoals("Maths Optional", 0L, "2026-10", false,
                "Maths Optional", 0L, "2026-11", false));
        assertFalse(RoadmapTreeRules.canReorderGoals("Software Interview Prep", 0L, "2026-10", false,
                "Software Interview Prep", 0L, "2026-11", false));
        assertFalse(RoadmapTreeRules.canReorderGoals("General Studies", 0L, "2026-10", false,
                "Maths Optional", 0L, "2026-10", false));
        assertFalse(RoadmapTreeRules.canReorderGoals("General Studies", 1L, "2026-10", false,
                "General Studies", 2L, "2026-10", false));
        assertFalse(RoadmapTreeRules.canReorderGoals("General Studies", 0L, "2026-10", true,
                "General Studies", 0L, "2026-11", false));
    }

    @Test public void monthDropAllowsGeneralStudiesRootGoalToChangeMonth() {
        assertTrue(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 0L, "2026-11", false,
                "General Studies", "2026-10"));
    }

    @Test public void monthDropKeepsOtherTracksChronological() {
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "Maths Optional", 0L, "2026-11", false,
                "Maths Optional", "2026-10"));
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "Software Interview Prep", 0L, "2026-11", false,
                "Software Interview Prep", "2026-10"));
    }

    @Test public void monthDropRejectsCrossSubjectAndNestedGoalMoves() {
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 0L, "2026-11", false,
                "Maths Optional", "2026-10"));
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 42L, "2026-10", false,
                "General Studies", "2026-10"));
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 0L, "2026-11", true,
                "General Studies", "2026-10"));
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 0L, "2026-11", false,
                "General Studies", "bad-month"));
    }

    @Test public void monthDropRejectsMalformedMonthKeysEvenWhenSevenCharactersLong() {
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 0L, "2026-13", false,
                "General Studies", "2026-10"));
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 0L, "2026-1x", false,
                "General Studies", "2026-10"));
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 0L, "2026-10", false,
                "General Studies", "abcd-01"));
        assertFalse(RoadmapTreeRules.canDropGoalOnMonth(
                "General Studies", 0L, "2026-10", false,
                "General Studies", "2026-00"));
    }

    @Test public void monthSlotListDeduplicatesGoalsAndPlannedMonthsAndIgnoresMalformedKeys() {
        assertEquals(java.util.Arrays.asList("2026-11", "2026-12", "2027-01"),
                RoadmapTreeRules.monthKeysForPeriod("2026-10", true,
                        java.util.Arrays.asList("2026-11", "2026-12", "2026-99", "junk!!"),
                        java.util.Arrays.asList("2026-10", "2026-11", "2026-12", "2027-01", "2027-1x")));
        assertEquals(java.util.Arrays.asList("2026-09", "2026-08", "2026-07"),
                RoadmapTreeRules.monthKeysForPeriod("2026-10", false,
                        java.util.Arrays.asList("2026-09", "2026-08"),
                        java.util.Arrays.asList("2026-10", "2026-08", "2026-07", "2026-13")));
    }

    @Test public void sameMonthRootGoalCanTargetItsMonthWithoutBreakingSequence() {
        assertTrue(RoadmapTreeRules.canDropGoalOnMonth(
                "Maths Optional", 0L, "2026-10", false,
                "Maths Optional", "2026-10"));
    }


    @Test public void droppingIntoAnEmptyMonthKeepsTheGoalAtRootLevel() {
        assertTrue(RoadmapTreeRules.shouldMoveDroppedGoalAsRoot(false));
        assertFalse(RoadmapTreeRules.shouldMoveDroppedGoalAsRoot(true));
    }

    @Test public void emptyCustomMonthSlotRemainsVisibleAfterItsGoalMovesOut() {
        assertEquals(java.util.Arrays.asList("2027-03"),
                RoadmapTreeRules.monthKeysForPeriod("2027-01", true,
                        java.util.Collections.emptyList(),
                        java.util.Arrays.asList("2027-03")));
    }

    @Test public void monthDropCanNestRootGoalsUnderARootGoalInTheSameMonth() {
        assertTrue(RoadmapTreeRules.canNestGoalUnderParent(
                "Maths Optional", 0L, "2026-10", false,
                "Maths Optional", 0L, "2026-10", false, "2026-10"));
        assertTrue(RoadmapTreeRules.canNestGoalUnderParent(
                "General Studies", 0L, "2026-11", false,
                "General Studies", 0L, "2026-10", false, "2026-10"));
    }

    @Test public void monthDropNestingPreservesContinuityAndCompletedWorkGuards() {
        assertFalse(RoadmapTreeRules.canNestGoalUnderParent(
                "Maths Optional", 0L, "2026-11", false,
                "Maths Optional", 0L, "2026-10", false, "2026-10"));
        assertFalse(RoadmapTreeRules.canNestGoalUnderParent(
                "General Studies", 0L, "2026-11", false,
                "Maths Optional", 0L, "2026-10", false, "2026-10"));
        assertFalse(RoadmapTreeRules.canNestGoalUnderParent(
                "General Studies", 42L, "2026-10", false,
                "General Studies", 0L, "2026-10", false, "2026-10"));
        assertFalse(RoadmapTreeRules.canNestGoalUnderParent(
                "General Studies", 0L, "2026-10", false,
                "General Studies", 42L, "2026-10", false, "2026-10"));
        assertFalse(RoadmapTreeRules.canNestGoalUnderParent(
                "General Studies", 0L, "2026-11", true,
                "General Studies", 0L, "2026-10", false, "2026-10"));
        assertFalse(RoadmapTreeRules.canNestGoalUnderParent(
                "General Studies", 0L, "2026-11", false,
                "General Studies", 0L, "2026-10", true, "2026-10"));
        assertFalse(RoadmapTreeRules.canNestGoalUnderParent(
                "General Studies", 0L, "2026-11", false,
                "General Studies", 0L, "2026-10", false, "2026-09"));
    }

    @Test public void emptyPlannedSourceMonthRemainsInUpcomingRoadmap() {
        // November has no remaining goal after its last goal moves, but it remains a planned slot.
        assertEquals(java.util.Arrays.asList("2026-11", "2026-12", "2027-01"),
                RoadmapTreeRules.monthKeysForPeriod("2026-10", true,
                        java.util.Arrays.asList("2026-12"),
                        java.util.Arrays.asList("2026-10", "2026-11", "2026-12", "2027-01")));
    }

    @Test public void emptyPlannedSourceMonthRemainsInPastRoadmap() {
        assertEquals(java.util.Arrays.asList("2026-11", "2026-10"),
                RoadmapTreeRules.monthKeysForPeriod("2026-12", false,
                        java.util.Arrays.asList("2026-10"),
                        java.util.Arrays.asList("2026-10", "2026-11", "2026-12", "2027-01")));
    }

    @Test public void malformedSelectedMonthHasNoDerivedPeriodSlots() {
        assertTrue(RoadmapTreeRules.monthKeysForPeriod("bad-month", true,
                java.util.Arrays.asList("2026-11"),
                java.util.Arrays.asList("2026-12")).isEmpty());
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