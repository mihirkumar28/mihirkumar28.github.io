package com.mihirkumar.dayflow;

/** Pure rules used by the roadmap tree and its regression tests. */
final class RoadmapTreeRules {
    private RoadmapTreeRules() {}

    static boolean isActionableType(String type) {
        return "topic".equals(type) || "goal".equals(type) || "milestone".equals(type);
    }

    /**
     * Goal months are the planned completion window; milestone dates are exact dates.
     * Completed items never remain overdue, and topics without due dates are not guessed overdue.
     */
    static boolean isOverdue(String type, boolean done, String statusPeriod, String todayKey) {
        if (done || statusPeriod == null || statusPeriod.trim().isEmpty()
                || todayKey == null || todayKey.length() < 7) return false;
        if ("goal".equals(type)) {
            if (statusPeriod.length() < 7) return false;
            return statusPeriod.substring(0, 7).compareTo(todayKey.substring(0, 7)) < 0;
        }
        if ("milestone".equals(type)) return statusPeriod.compareTo(todayKey) < 0;
        return false;
    }

    static boolean matchesFilter(String filter, String type, boolean done,
                                 String statusPeriod, String todayKey) {
        String selected = filter == null ? "All" : filter;
        if ("All".equals(selected)) return true;
        if (!isActionableType(type)) return false;
        if ("Open".equals(selected)) return !done;
        if ("Done".equals(selected)) return done;
        if ("Overdue".equals(selected)) return isOverdue(type, done, statusPeriod, todayKey);
        return true;
    }

    /**
     * Whether two goals can be reordered without changing their logical track/parent.
     * Only General Studies root goals may exchange month slots; other tracks remain
     * chronological, and nested goals must remain within their existing parent.
     */
    static boolean canReorderGoals(String draggedTrack, long draggedParentId, String draggedMonth,
                                   boolean draggedDone, String targetTrack, long targetParentId,
                                   String targetMonth, boolean targetDone) {
        if (draggedDone || targetDone) return false;
        if (!java.util.Objects.equals(draggedTrack, targetTrack)
                || draggedParentId != targetParentId) return false;
        boolean generalStudiesRootSlots = draggedParentId == 0L && targetParentId == 0L
                && "General Studies".equals(draggedTrack);
        return generalStudiesRootSlots || java.util.Objects.equals(draggedMonth, targetMonth);
    }

    /**
     * Whether a root goal is eligible to be added as a sub-goal in the dropped-on month.
     * The parent is selected from that month's existing root goals after this check.
     * Cross-month moves are limited to General Studies; completed work stays in place.
     */
    static boolean canDropGoalOnMonth(String draggedTrack, long draggedParentId, String draggedMonth,
                                      boolean hasCompletedWork, String targetTrack, String targetMonth) {
        if (hasCompletedWork || draggedParentId > 0L
                || !java.util.Objects.equals(draggedTrack, targetTrack)
                || !isValidMonthKey(draggedMonth)
                || !isValidMonthKey(targetMonth)) return false;
        if (java.util.Objects.equals(draggedMonth, targetMonth)) return true;
        return "General Studies".equals(draggedTrack);
    }

    /** Empty target months receive a root goal; populated months use the parent-selection flow. */
    static boolean shouldMoveDroppedGoalAsRoot(boolean hasOtherActiveGoalsInTargetMonth) {
        return !hasOtherActiveGoalsInTargetMonth;
    }

    /**
     * Whether an eligible root goal can be nested under a root goal in the destination month.
     * Nested goals stay within a track and month sequence, and neither branch may contain
     * completed work. Only General Studies root goals may be nested across month boundaries.
     */
    static boolean canNestGoalUnderParent(String draggedTrack, long draggedParentId, String draggedMonth,
                                          boolean draggedHasCompletedWork, String parentTrack,
                                          long parentParentId, String parentMonth,
                                          boolean parentHasCompletedWork, String targetMonth) {
        if (draggedHasCompletedWork || parentHasCompletedWork
                || draggedParentId != 0L || parentParentId != 0L
                || !java.util.Objects.equals(draggedTrack, parentTrack)
                || !isValidMonthKey(draggedMonth) || !isValidMonthKey(parentMonth)
                || !isValidMonthKey(targetMonth)
                || !java.util.Objects.equals(parentMonth, targetMonth)) return false;
        return java.util.Objects.equals(draggedMonth, targetMonth)
                || "General Studies".equals(draggedTrack);
    }

    private static boolean isValidMonthKey(String month) {
        return month != null && month.matches("\\d{4}-(0[1-9]|1[0-2])");
    }

    /**
     * Returns the roadmap month slots on one side of the selected month. Planned slots stay
     * visible even when no goals currently occupy them; goal-derived months preserve custom dates.
     * Upcoming slots are chronological, while past slots are reverse chronological.
     */
    static java.util.ArrayList<String> monthKeysForPeriod(String selectedMonth, boolean ascending,
                                                          java.util.List<String> goalMonthKeys,
                                                          java.util.List<String> plannedMonthKeys) {
        java.util.ArrayList<String> ordered = new java.util.ArrayList<>();
        if (!isValidMonthKey(selectedMonth)) return ordered;
        java.util.TreeSet<String> monthKeys = new java.util.TreeSet<>();
        addMonthKeysForPeriod(monthKeys, selectedMonth, ascending, goalMonthKeys);
        addMonthKeysForPeriod(monthKeys, selectedMonth, ascending, plannedMonthKeys);
        ordered.addAll(monthKeys);
        if (!ascending) java.util.Collections.reverse(ordered);
        return ordered;
    }

    private static void addMonthKeysForPeriod(java.util.Set<String> destination, String selectedMonth,
                                              boolean ascending, java.util.List<String> candidates) {
        if (candidates == null) return;
        for (String month : candidates) {
            if (!isValidMonthKey(month)) continue;
            int comparison = month.compareTo(selectedMonth);
            if ((ascending && comparison > 0) || (!ascending && comparison < 0)) {
                destination.add(month);
            }
        }
    }

    static int[] leafProgress(boolean done) {
        return new int[]{done ? 1 : 0, 1};
    }

    static int[] combineProgress(int[] first, int[] second) {
        int[] a = first == null ? new int[]{0, 0} : first;
        int[] b = second == null ? new int[]{0, 0} : second;
        return new int[]{a[0] + b[0], a[1] + b[1]};
    }
}