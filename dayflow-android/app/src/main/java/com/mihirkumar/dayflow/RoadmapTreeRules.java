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

    static int[] leafProgress(boolean done) {
        return new int[]{done ? 1 : 0, 1};
    }

    static int[] combineProgress(int[] first, int[] second) {
        int[] a = first == null ? new int[]{0, 0} : first;
        int[] b = second == null ? new int[]{0, 0} : second;
        return new int[]{a[0] + b[0], a[1] + b[1]};
    }
}