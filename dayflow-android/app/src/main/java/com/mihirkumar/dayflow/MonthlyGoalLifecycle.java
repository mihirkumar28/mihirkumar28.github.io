package com.mihirkumar.dayflow;

/** Pure lifecycle rules for monthly roadmap goals. */
final class MonthlyGoalLifecycle {
    static final String ACTIVE = "active";
    static final String COMPLETED = "completed";
    static final String RETIRED = "retired";
    static final String SPLIT_PARENT = "split-parent";

    private MonthlyGoalLifecycle() {}

    static boolean showInMonthlyPanel(String status) {
        return !RETIRED.equals(status) && !SPLIT_PARENT.equals(status);
    }

    static boolean countsAsPlannedGoal(String status) {
        return !RETIRED.equals(status) && !SPLIT_PARENT.equals(status);
    }

    static boolean isPastUnfinished(String status, boolean done, String scheduledMonth, String currentMonth) {
        return !done && ACTIVE.equals(status)
                && scheduledMonth != null && currentMonth != null
                && scheduledMonth.length() == 7 && currentMonth.length() == 7
                && scheduledMonth.compareTo(currentMonth) < 0;
    }

    static boolean shouldOfferEarlyCompletion(boolean isCurrentMonth, int dayOfMonth,
                                               int lastDayOfMonth, int plannedGoals, int unfinishedGoals) {
        return isCurrentMonth && dayOfMonth > 0 && lastDayOfMonth >= dayOfMonth
                && dayOfMonth < lastDayOfMonth && plannedGoals > 0 && unfinishedGoals == 0;
    }
}
