package com.mihirkumar.dayflow;

import java.util.Locale;

/** Pure scheduling rules for audible slot-end reminders. */
final class SlotReminderRules {
    private SlotReminderRules() {
    }

    static boolean shouldAlert(String title, String status, int startMinute, int endMinute) {
        if (status == null || !"pending".equalsIgnoreCase(status.trim())) return false;
        if (startMinute < 0 || endMinute > 1440 || endMinute <= startMinute) return false;
        if (endMinute - startMinute < 10) return false;
        return !isMorningAlarmTitle(title);
    }

    static boolean isMorningAlarmTitle(String title) {
        if (title == null) return false;
        String compact = title.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
        return compact.equals("alarm")
                || compact.contains("morningalarm")
                || compact.startsWith("wakeup");
    }
}
