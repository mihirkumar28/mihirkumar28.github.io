package com.mihirkumar.dayflow;

import android.content.Context;

/**
 * Backward-compatible facade for the Roadmap's monthly source plan.
 *
 * The content lives in assets/plans_catalog.json and is shared with the
 * Plans & Documents library; this class contains no duplicate plan data.
 */
final class RoadmapMonthlyPlan {
    private RoadmapMonthlyPlan() {}

    static void initialize(Context context) {
        PlanCatalog.initialize(context);
    }

    static void initializeFromJson(String jsonText) {
        PlanCatalog.initializeFromJson(jsonText);
    }

    static int monthCount() {
        return PlanCatalog.monthCount();
    }

    static String monthKeyAt(int index) {
        return PlanCatalog.monthKeyAt(index);
    }

    static String[][] rowsForMonth(String monthKey) {
        return PlanCatalog.rowsForMonth(monthKey);
    }

    static String[] findGoal(String monthKey, String track) {
        return PlanCatalog.findGoal(monthKey, track);
    }
}
