package com.mihirkumar.dayflow;

/**
 * Pure interval rules for DayFlow's same-day timetable.
 *
 * Intervals are half-open: a block ending at 10:00 does not overlap one
 * starting at 10:00. DayFlow's time picker represents the latest end as 23:59.
 */
final class DayFlowScheduleRules {
    private static final int LAST_MINUTE_OF_DAY = 23 * 60 + 59;

    private DayFlowScheduleRules() {}

    static boolean isValidInterval(int startMinute, int endMinute) {
        return startMinute >= 0
                && startMinute < LAST_MINUTE_OF_DAY
                && endMinute > startMinute
                && endMinute <= LAST_MINUTE_OF_DAY;
    }

    static boolean overlaps(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        if (!isValidInterval(firstStart, firstEnd)
                || !isValidInterval(secondStart, secondEnd)) return false;
        return firstStart < secondEnd && secondStart < firstEnd;
    }
}
