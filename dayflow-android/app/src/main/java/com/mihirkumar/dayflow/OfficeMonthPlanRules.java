package com.mihirkumar.dayflow;

/** Pure validation for monthly office-day plans. */
final class OfficeMonthPlanRules {
    static final int MINIMUM_DAYS = 8;
    static final int HOURS_WAIVER_DAYS = 9;
    static final double MINIMUM_HOURS = 52.0d;
    static final double DEFAULT_HOURS_PER_DAY = 6.5d;
    static final int COMMUTE_MINUTES_EACH_WAY = 75;
    static final int DAILY_COMMUTE_MINUTES = COMMUTE_MINUTES_EACH_WAY * 2;

    private OfficeMonthPlanRules() {}

    /** Commute is planned separately and never counts toward required office hours. */
    static double dailyCommuteHours() {
        return DAILY_COMMUTE_MINUTES / 60.0d;
    }

    static double totalCommuteHours(int plannedDays) {
        return Math.max(0, plannedDays) * (double) DAILY_COMMUTE_MINUTES / 60.0d;
    }

    static double dailyCommitmentHours(double plannedOfficeHours) {
        return Math.max(0d, plannedOfficeHours) + dailyCommuteHours();
    }

    static double totalCommitmentHours(double plannedOfficeHours, int plannedDays) {
        return Math.max(0d, plannedOfficeHours) + totalCommuteHours(plannedDays);
    }

    /** Reference scenario: the eight-day route at 6.5 office hours/day plus its commute. */
    static double minimumDaysBaselineCommitmentHours() {
        return MINIMUM_DAYS * DEFAULT_HOURS_PER_DAY + totalCommuteHours(MINIMUM_DAYS);
    }

    /** Negative means fewer office-plus-commute hours than the eight-day reference. */
    static double commitmentDeltaVsMinimumDaysBaseline(double plannedOfficeHours, int plannedDays) {
        return totalCommitmentHours(plannedOfficeHours, plannedDays)
                - minimumDaysBaselineCommitmentHours();
    }

    static boolean isHoursMinimumWaived(int plannedDays) {
        return plannedDays >= HOURS_WAIVER_DAYS;
    }

    static boolean isCompliant(int plannedDays, double plannedHours) {
        if (plannedDays < MINIMUM_DAYS) return false;
        if (isHoursMinimumWaived(plannedDays)) return true;
        return plannedHours + 0.000001d >= MINIMUM_HOURS;
    }
}
