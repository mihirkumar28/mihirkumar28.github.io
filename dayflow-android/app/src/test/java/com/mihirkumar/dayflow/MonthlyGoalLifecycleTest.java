package com.mihirkumar.dayflow;

import org.junit.Test;
import static org.junit.Assert.*;

public class MonthlyGoalLifecycleTest {
    @Test public void onlyPastActiveIncompleteGoalsNeedReview() {
        assertTrue(MonthlyGoalLifecycle.isPastUnfinished(
                MonthlyGoalLifecycle.ACTIVE, false, "2026-09", "2026-10"));
        assertFalse(MonthlyGoalLifecycle.isPastUnfinished(
                MonthlyGoalLifecycle.ACTIVE, true, "2026-09", "2026-10"));
        assertFalse(MonthlyGoalLifecycle.isPastUnfinished(
                MonthlyGoalLifecycle.ACTIVE, false, "2026-10", "2026-10"));
        assertFalse(MonthlyGoalLifecycle.isPastUnfinished(
                MonthlyGoalLifecycle.ACTIVE, false, "2026-11", "2026-10"));
        assertFalse(MonthlyGoalLifecycle.isPastUnfinished(
                MonthlyGoalLifecycle.RETIRED, false, "2026-09", "2026-10"));
        assertFalse(MonthlyGoalLifecycle.isPastUnfinished(
                MonthlyGoalLifecycle.SPLIT_PARENT, false, "2026-09", "2026-10"));
    }

    @Test public void finishedEarlyOffersChoicesOnlyWhenTrackPlanIsComplete() {
        assertTrue(MonthlyGoalLifecycle.shouldOfferEarlyCompletion(true, 12, 31, 3, 0));
        assertFalse(MonthlyGoalLifecycle.shouldOfferEarlyCompletion(true, 31, 31, 3, 0));
        assertFalse(MonthlyGoalLifecycle.shouldOfferEarlyCompletion(true, 12, 31, 3, 1));
        assertFalse(MonthlyGoalLifecycle.shouldOfferEarlyCompletion(true, 12, 31, 0, 0));
        assertFalse(MonthlyGoalLifecycle.shouldOfferEarlyCompletion(false, 12, 31, 3, 0));
    }

    @Test public void parentAndRetiredRowsAreNotCountedAsPlannedLeafGoals() {
        assertTrue(MonthlyGoalLifecycle.countsAsPlannedGoal(MonthlyGoalLifecycle.ACTIVE));
        assertTrue(MonthlyGoalLifecycle.countsAsPlannedGoal(MonthlyGoalLifecycle.COMPLETED));
        assertFalse(MonthlyGoalLifecycle.countsAsPlannedGoal(MonthlyGoalLifecycle.RETIRED));
        assertFalse(MonthlyGoalLifecycle.countsAsPlannedGoal(MonthlyGoalLifecycle.SPLIT_PARENT));
        assertTrue(MonthlyGoalLifecycle.showInMonthlyPanel(MonthlyGoalLifecycle.ACTIVE));
        assertFalse(MonthlyGoalLifecycle.showInMonthlyPanel(MonthlyGoalLifecycle.RETIRED));
        assertFalse(MonthlyGoalLifecycle.showInMonthlyPanel(MonthlyGoalLifecycle.SPLIT_PARENT));
    }
}