package com.mihirkumar.dayflow;

/** Pure rules for Today screen defaults and actionable states. */
public final class DayFlowScreenRules {
    private DayFlowScreenRules() {}

    public enum TodayState { EMPTY, IN_PROGRESS, UPCOMING, COMPLETE, NEEDS_REVIEW }

    /** Work arrangement is WFH or WFO; legacy "weekend" values are not arrangements. */
    public static String normalizeWorkArrangement(String savedArrangement, String fallbackArrangement) {
        if ("office".equals(savedArrangement) || "wfh".equals(savedArrangement)) return savedArrangement;
        if ("office".equals(fallbackArrangement) || "wfh".equals(fallbackArrangement)) return fallbackArrangement;
        return "wfh";
    }

    /** Weekend routines take precedence over weekday work arrangement. */
    public static String defaultTemplateKey(boolean weekend, String workArrangement) {
        if (weekend) return "weekend";
        return "office".equals(workArrangement) ? "office" : "wfh";
    }

    /**
     * Resolve exactly one of DayFlow's three routine types: WFH, WFO or Weekend.
     * An explicit per-date selection wins; otherwise weekends use Weekend and weekdays
     * inherit that date's saved WFH/WFO arrangement.
     */
    public static String resolveRoutineProfile(
            String savedProfile, boolean calendarWeekend, String workArrangement) {
        if ("wfh".equals(savedProfile) || "office".equals(savedProfile) || "weekend".equals(savedProfile)) {
            return savedProfile;
        }
        if (calendarWeekend) return "weekend";
        return "office".equals(workArrangement) ? "office" : "wfh";
    }

    /** Non-today dates are handled separately so they cannot expose live actions. */
    public static TodayState resolveTodayState(
            int totalBlocks, int completedBlocks, boolean hasCurrentBlock, boolean hasUpcomingBlock) {
        if (totalBlocks <= 0) return TodayState.EMPTY;
        if (hasCurrentBlock) return TodayState.IN_PROGRESS;
        if (hasUpcomingBlock) return TodayState.UPCOMING;
        if (completedBlocks >= totalBlocks) return TodayState.COMPLETE;
        return TodayState.NEEDS_REVIEW;
    }
}
