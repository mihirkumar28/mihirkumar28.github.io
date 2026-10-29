package com.mihirkumar.dayflow;

/** Pure validation for monthly office-day plans. */
final class OfficeMonthPlanRules {
    static final int MINIMUM_DAYS = 8;
    static final int HOURS_WAIVER_DAYS = 9;
    static final double MINIMUM_HOURS = 52.0d;
    static final double DEFAULT_HOURS_PER_DAY = 6.5d;

    private OfficeMonthPlanRules() {}

    static boolean isHoursMinimumWaived(int plannedDays) {
        return plannedDays >= HOURS_WAIVER_DAYS;
    }

    static boolean isCompliant(int plannedDays, double plannedHours) {
        if (plannedDays < MINIMUM_DAYS) return false;
        if (isHoursMinimumWaived(plannedDays)) return true;
        return plannedHours + 0.000001d >= MINIMUM_HOURS;
    }
}
