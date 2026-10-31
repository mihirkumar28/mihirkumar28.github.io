package com.mihirkumar.dayflow;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Keeps one-shot 10-minute-before-end alarms aligned with today's saved schedule. */
final class ScheduleAlertScheduler {
    private static final String DAYFLOW_PREFS = "dayflow";
    private static final String ALERT_PREFS = "dayflow_slot_alerts";
    private static final String REQUEST_CODES_KEY = "scheduled_request_codes";
    private static final String DEBUG_TAG = "DayFlowAlerts";

    private ScheduleAlertScheduler() {
    }

    static boolean hasExactAlarmAccess(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        try {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return manager != null && manager.canScheduleExactAlarms();
        } catch (Exception ignored) {
            return false;
        }
    }

    static void scheduleToday(Context context) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SharedPreferences dayPrefs = context.getSharedPreferences(DAYFLOW_PREFS, Context.MODE_PRIVATE);
        String raw = dayPrefs.getString("blocks_" + today, null);
        if (raw == null || raw.trim().isEmpty()) raw = dayPrefs.getString("blocks", "[]");
        scheduleForDate(context, today, raw);
    }

    private static void scheduleForDate(Context context, String dateKey, String rawBlocks) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        SharedPreferences alertPrefs = context.getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE);
        Set<String> previousCodes = new HashSet<>(alertPrefs.getStringSet(
                REQUEST_CODES_KEY, Collections.<String>emptySet()));

        if (alarms != null) {
            for (String value : previousCodes) {
                try {
                    int requestCode = Integer.parseInt(value);
                    Intent intent = new Intent(context, ScheduleAlertReceiver.class)
                            .setAction(ScheduleAlertReceiver.ACTION_SLOT_END_REMINDER);
                    PendingIntent existing = PendingIntent.getBroadcast(context, requestCode, intent,
                            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                    if (existing != null) {
                        alarms.cancel(existing);
                        existing.cancel();
                    }
                } catch (Exception e) {
                    Log.w(DEBUG_TAG, "Could not cancel a previous reminder", e);
                }
            }
        }

        Set<String> nextCodes = new HashSet<>();
        if (alarms == null) {
            alertPrefs.edit().putStringSet(REQUEST_CODES_KEY, nextCodes).apply();
            return;
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            dateFormat.setLenient(false);
            Date parsedDate = dateFormat.parse(dateKey);
            if (parsedDate == null) throw new IllegalArgumentException("Invalid schedule date");
            Calendar day = Calendar.getInstance();
            day.setTime(parsedDate);
            day.set(Calendar.HOUR_OF_DAY, 0);
            day.set(Calendar.MINUTE, 0);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);

            JSONArray blocks = new JSONArray(rawBlocks == null ? "[]" : rawBlocks);
            long now = System.currentTimeMillis();
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block == null) continue;
                String title = block.optString("title", "Scheduled task");
                String status = block.optString("status", "pending");
                int startMinute = parseMinute(block.optString("start", ""));
                int endMinute = parseMinute(block.optString("end", ""));
                if (!SlotReminderRules.shouldAlert(title, status, startMinute, endMinute)) continue;

                Calendar trigger = (Calendar) day.clone();
                trigger.set(Calendar.HOUR_OF_DAY, endMinute / 60);
                trigger.set(Calendar.MINUTE, endMinute % 60);
                trigger.set(Calendar.SECOND, 0);
                trigger.set(Calendar.MILLISECOND, 0);
                trigger.add(Calendar.MINUTE, -10);
                long triggerAt = trigger.getTimeInMillis();
                if (triggerAt <= now + 1000L) continue;

                long blockId = block.optLong("id", i);
                int requestCode = requestCode(dateKey, blockId);
                Intent intent = new Intent(context, ScheduleAlertReceiver.class)
                        .setAction(ScheduleAlertReceiver.ACTION_SLOT_END_REMINDER)
                        .putExtra(ScheduleAlertReceiver.EXTRA_TITLE, title)
                        .putExtra(ScheduleAlertReceiver.EXTRA_END_TIME, block.optString("end", ""))
                        .putExtra(ScheduleAlertReceiver.EXTRA_REQUEST_CODE, requestCode);
                PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
                    } else {
                        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
                    }
                } catch (SecurityException e) {
                    // Keep a reminder queued even when exact-alarm access is denied.
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
                }
                nextCodes.add(String.valueOf(requestCode));
            }
            alertPrefs.edit().putStringSet(REQUEST_CODES_KEY, nextCodes).apply();
            Log.d(DEBUG_TAG, "Scheduled " + nextCodes.size() + " slot reminders for " + dateKey);
        } catch (Exception e) {
            alertPrefs.edit().putStringSet(REQUEST_CODES_KEY, nextCodes).apply();
            Log.e(DEBUG_TAG, "Could not schedule slot reminders for " + dateKey, e);
        }
    }

    private static int parseMinute(String value) {
        try {
            String[] parts = value.split(":");
            if (parts.length != 2) return -1;
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return hour * 60 + minute;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static int requestCode(String dateKey, long blockId) {
        int value = (dateKey + ":" + blockId).hashCode() & 0x7fffffff;
        return value == 0 ? 1 : value;
    }
}
