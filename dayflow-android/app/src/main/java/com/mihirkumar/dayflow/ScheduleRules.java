package com.mihirkumar.dayflow;

import java.util.Calendar;

/** Pure scheduling invariants for same-day timetable blocks and recurring additions. */
final class ScheduleRules {
    private static final int MINUTES_PER_DAY = 24 * 60;
    private ScheduleRules() {}

    /** The app stores an end as a time within the selected day, not as next-day midnight. */
    static boolean isValidInterval(int startMinute, int endMinute) {
        return startMinute >= 0 && startMinute < MINUTES_PER_DAY
                && endMinute > startMinute && endMinute < MINUTES_PER_DAY;
    }

    /** Half-open intervals allow adjacent blocks, but reject any positive overlap. */
    static boolean overlaps(int startMinute, int endMinute,
                            int otherStartMinute, int otherEndMinute) {
        if (!isValidInterval(startMinute, endMinute)
                || !isValidInterval(otherStartMinute, otherEndMinute)) return false;
        return startMinute < otherEndMinute && otherStartMinute < endMinute;
    }

    static boolean shouldRepeatOnDay(String repeat, int dayOfWeek) {
        if ("daily".equals(repeat)) return dayOfWeek >= Calendar.SUNDAY
                && dayOfWeek <= Calendar.SATURDAY;
        if ("weekdays".equals(repeat)) {
            return dayOfWeek >= Calendar.MONDAY && dayOfWeek <= Calendar.FRIDAY;
        }
        return false;
    }
}
