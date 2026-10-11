package com.mihirkumar.dayflow;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Stores a durable local daily screen-interactive-time ledger. No app-by-app history is retained. */
public final class ScreenTimeTracker {
    private static final String PREFS = "dayflow";
    private static final String VALUE_PREFIX = "screenTimeMs_";
    private static final String CAPTURE_PREFIX = "screenTimeCapturedAt_";
    private static final String ESTIMATED_PREFIX = "screenTimeEstimated_";

    private ScreenTimeTracker() {}

    public static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean refreshRecentDays(Context context, int count) {
        if (!hasUsageAccess(context)) return false;
        boolean changed = false;
        Calendar today = Calendar.getInstance();
        for (int offset = Math.max(0, count - 1); offset >= 0; offset--) {
            Calendar day = (Calendar) today.clone();
            day.add(Calendar.DAY_OF_YEAR, -offset);
            String key = dateKey(day.getTime());
            Long value = queryScreenTimeMillis(context, day, offset == 0);
            if (value == null) continue;
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long old = prefs.getLong(VALUE_PREFIX + key, -1L);
            boolean differs = old < 0 || Math.abs(value - old) >= 30000L;
            if (differs) {
                boolean estimated = Build.VERSION.SDK_INT < Build.VERSION_CODES.P;
                prefs.edit()
                        .putLong(VALUE_PREFIX + key, Math.max(0L, value))
                        .putLong(CAPTURE_PREFIX + key, System.currentTimeMillis())
                        .putBoolean(ESTIMATED_PREFIX + key, estimated)
                        .apply();
                changed = true;
            }
        }
        return changed;
    }

    private static Long queryScreenTimeMillis(Context context, Calendar date, boolean today) {
        try {
            UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (manager == null) return null;
            Calendar start = (Calendar) date.clone();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            long dayStart = start.getTimeInMillis();
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_YEAR, 1);
            long dayEnd = today ? System.currentTimeMillis() : end.getTimeInMillis();
            if (dayEnd <= dayStart) return 0L;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                UsageEvents events = manager.queryEvents(dayStart - 86400000L, dayEnd);
                if (events == null) return null;
                UsageEvents.Event event = new UsageEvents.Event();
                boolean interactive = false;
                boolean sawScreenEvent = false;
                long sessionStart = 0L;
                long total = 0L;
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    int type = event.getEventType();
                    long timestamp = event.getTimeStamp();
                    if (type == UsageEvents.Event.SCREEN_INTERACTIVE) {
                        sawScreenEvent = true;
                        if (!interactive) {
                            interactive = true;
                            sessionStart = timestamp;
                        }
                    } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                        sawScreenEvent = true;
                        if (interactive) {
                            total += overlap(sessionStart, timestamp, dayStart, dayEnd);
                            interactive = false;
                        }
                    }
                }
                if (!sawScreenEvent) return null;
                if (interactive) total += overlap(sessionStart, dayEnd, dayStart, dayEnd);
                return Math.max(0L, total);
            }

            List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd);
            if (stats == null || stats.isEmpty()) return null;
            long total = 0L;
            for (UsageStats stat : stats) total += Math.max(0L, stat.getTotalTimeInForeground());
            return total;
        } catch (SecurityException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static long overlap(long sessionStart, long sessionEnd, long dayStart, long dayEnd) {
        return Math.max(0L, Math.min(sessionEnd, dayEnd) - Math.max(sessionStart, dayStart));
    }

    private static String dateKey(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    public static long getStoredMillis(Context context, String dateKey) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(VALUE_PREFIX + dateKey, -1L);
    }

    public static long getCapturedAt(Context context, String dateKey) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(CAPTURE_PREFIX + dateKey, 0L);
    }

    public static boolean isEstimated(Context context, String dateKey) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ESTIMATED_PREFIX + dateKey, false);
    }

    public static String formatDuration(long millis) {
        long minutes = Math.max(0L, millis / 60000L);
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        if (hours > 0) return hours + "h " + String.format(Locale.US, "%02dm", rest);
        return minutes + "m";
    }
}
