package com.mihirkumar.dayflow;

import android.app.PendingIntent;
import android.app.AlarmManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import android.util.Log;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;

public class DayFlowWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "dayflow";
    private static final String OFFICE_LOG_KEY = "officeLog";
    private static final String BLOCKS_KEY = "blocks";
    private static final String MODE_KEY = "mode";
    private static final String ACTION_TOGGLE = "com.mihirkumar.dayflow.CHECK_IN_OUT";
    private static final String DEBUG_LOG_KEY = "debugLog";
    private static final String DEBUG_TAG = "DayFlow";
    private static final String SYLLABUS_KEY = "syllabusTopics";
    private static final String ACTION_TICK = "com.mihirkumar.dayflow.WIDGET_TICK";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
        scheduleNextBoundary(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            toggleAttendance(context);
            refreshAll(context);
        } else if ("com.mihirkumar.dayflow.COMMUTE".equals(action)) {
            toggleCommute(context);
            refreshAll(context);
        } else if (ACTION_TICK.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            refreshAll(context);
        }
    }

    @Override
    public void onDisabled(Context context) {
        cancelBoundaryAlarm(context);
        super.onDisabled(context);
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, DayFlowWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) update(context, manager, id);
        scheduleNextBoundary(context);
    }

    private static PendingIntent boundaryPendingIntent(Context context) {
        Intent intent = new Intent(context, DayFlowWidgetProvider.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, 1004, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelBoundaryAlarm(Context context) {
        try {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) alarms.cancel(boundaryPendingIntent(context));
        } catch (Exception e) {
            debugLog(context, "Boundary alarm cancel failed: " + e.getMessage());
        }
    }

    private static void scheduleNextBoundary(Context context) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, DayFlowWidgetProvider.class);
            if (manager.getAppWidgetIds(component).length == 0) {
                cancelBoundaryAlarm(context);
                return;
            }
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray blocks = new JSONArray(prefs.getString("blocks_" + today,
                    prefs.getString(BLOCKS_KEY, "[]")));
            long now = System.currentTimeMillis();
            long next;
            Calendar todayCalendar = Calendar.getInstance();
            Calendar midnight = (Calendar) todayCalendar.clone();
            midnight.add(Calendar.DAY_OF_YEAR, 1);
            midnight.set(Calendar.HOUR_OF_DAY, 0);
            midnight.set(Calendar.MINUTE, 0);
            midnight.set(Calendar.SECOND, 0);
            midnight.set(Calendar.MILLISECOND, 0);
            next = midnight.getTimeInMillis();

            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.optJSONObject(i);
                if (block == null || !"pending".equals(block.optString("status"))) continue;
                String[] times = {block.optString("start", ""), block.optString("end", "")};
                for (String value : times) {
                    if (value.isEmpty()) continue;
                    int minute = mins(value);
                    Calendar boundary = (Calendar) todayCalendar.clone();
                    boundary.set(Calendar.HOUR_OF_DAY, minute / 60);
                    boundary.set(Calendar.MINUTE, minute % 60);
                    boundary.set(Calendar.SECOND, 0);
                    boundary.set(Calendar.MILLISECOND, 0);
                    long when = boundary.getTimeInMillis();
                    if (when > now + 250L && when < next) next = when;
                }
            }

            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) return;
            PendingIntent pending = boundaryPendingIntent(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
                alarms.setExact(AlarmManager.RTC, next, pending);
                debugLog(context, "Scheduled precise task boundary at=" + new Date(next));
            } else {
                alarms.set(AlarmManager.RTC, next, pending);
                debugLog(context, "Scheduled battery-friendly inexact boundary at=" + new Date(next));
            }
        } catch (Exception e) {
            debugLog(context, "Boundary schedule failed: " + e.getMessage());
        }
    }

    private static void debugLog(Context context, String message) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + " | WIDGET | " + message;
        Log.d(DEBUG_TAG, message);
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String old = p.getString(DEBUG_LOG_KEY, "");
            String combined = old + line + "\n";
            if (combined.length() > 20000) combined = combined.substring(combined.length() - 20000);
            p.edit().putString(DEBUG_LOG_KEY, combined).apply();
        } catch (Exception ignored) {
        }
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), com.mihirkumar.dayflow.R.layout.widget_dayflow);

        JSONObject day = getTodayDay(context, false);
        String mode = getTodayMode(context);
        debugLog(context, "Refresh widget id=" + id + " mode=" + mode
                + " dayExists=" + (day != null));
        boolean officeMode = "office".equals(mode);
        boolean inOffice = hasOpenSession(day);
        long officeMs = officeDuration(day, System.currentTimeMillis());
        long commuteMs = commuteDuration(day, System.currentTimeMillis());

        String current = "Nothing active";
        JSONArray blocks = getTodayBlocks(context);
        JSONObject active = null;
        JSONObject next = null;
        int now = currentMinutes();
        try {
            debugLog(context, "Widget read blocks=" + blocks.length()
                    + " now=" + now + " mode=" + mode);
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject b = blocks.optJSONObject(i);
                if (b == null || !"pending".equals(b.optString("status"))) continue;
                int start = mins(b.optString("start", "00:00"));
                int end = mins(b.optString("end", "00:00"));
                if (start <= now && now < end) {
                    active = b;
                    break;
                }
                if (start > now && next == null) next = b;
            }
        } catch (Exception e) {
            debugLog(context, "Widget task selection failed: " + e.getMessage());
        }

        JSONObject target = active != null ? active : next;
        long nowWall = System.currentTimeMillis();
        if (target != null) {
            current = target.optString("title", "Current task");
            int targetMinute = mins(target.optString(active != null ? "end" : "start", "00:00"));
            Calendar boundary = Calendar.getInstance();
            boundary.set(Calendar.HOUR_OF_DAY, targetMinute / 60);
            boundary.set(Calendar.MINUTE, targetMinute % 60);
            boundary.set(Calendar.SECOND, 0);
            boundary.set(Calendar.MILLISECOND, 0);
            long remainingMs = Math.max(0L, boundary.getTimeInMillis() - nowWall);
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_timing, active != null ? "ENDS IN" : "NEXT IN");
            views.setChronometer(com.mihirkumar.dayflow.R.id.widget_countdown,
                    SystemClock.elapsedRealtime() + remainingMs, "%s", true);
            views.setChronometerCountDown(com.mihirkumar.dayflow.R.id.widget_countdown, true);
            views.setViewVisibility(com.mihirkumar.dayflow.R.id.widget_countdown, android.view.View.VISIBLE);
            debugLog(context, "Widget selected task='" + current + "' remainingMs=" + remainingMs);
        } else {
            current = "Day complete";
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_timing, "SCHEDULE");
            views.setChronometer(com.mihirkumar.dayflow.R.id.widget_countdown,
                    SystemClock.elapsedRealtime(), "%s", false);
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_countdown, "Done");
            views.setViewVisibility(com.mihirkumar.dayflow.R.id.widget_countdown, android.view.View.VISIBLE);
        }

        String statusText;
        if (!officeMode) {
            statusText = mode.equals("weekend") ? "WEEKEND" : "WFH";
        } else {
            statusText = inOffice ? "IN OFFICE" : "OUT OF OFFICE";
        }
        views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_status, statusText);
        views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_task, current);

        int doneBlocks = 0;
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block != null && "done".equals(block.optString("status"))) doneBlocks++;
        }
        if (officeMode && hasOpenCommute(day)) {
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_metrics_label, "COMMUTE");
            views.setChronometer(com.mihirkumar.dayflow.R.id.widget_metrics,
                    SystemClock.elapsedRealtime() - commuteMs, "%s", true);
            views.setChronometerCountDown(com.mihirkumar.dayflow.R.id.widget_metrics, false);
        } else if (officeMode && inOffice) {
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_metrics_label, "OFFICE");
            views.setChronometer(com.mihirkumar.dayflow.R.id.widget_metrics,
                    SystemClock.elapsedRealtime() - officeMs, "%s", true);
            views.setChronometerCountDown(com.mihirkumar.dayflow.R.id.widget_metrics, false);
        } else if (officeMode) {
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_metrics_label, "TOTAL");
            views.setChronometer(com.mihirkumar.dayflow.R.id.widget_metrics,
                    SystemClock.elapsedRealtime(), "%s", false);
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_metrics,
                    "O " + fmtMillis(officeMs) + " • C " + fmtMillis(commuteMs));
        } else {
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_metrics_label, "PLAN");
            views.setChronometer(com.mihirkumar.dayflow.R.id.widget_metrics,
                    SystemClock.elapsedRealtime(), "%s", false);
            String syllabus = syllabusProgressPercent(context);
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_metrics,
                    doneBlocks + "/" + blocks.length() + " • S " + syllabus + "%");
        }

        views.setViewVisibility(com.mihirkumar.dayflow.R.id.widget_action,
                officeMode ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(com.mihirkumar.dayflow.R.id.widget_commute,
                officeMode ? android.view.View.VISIBLE : android.view.View.GONE);

        if (officeMode) {
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_action, inOffice ? "OUT" : "IN");

            String commuteAction = commuteAction(day);
            views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_commute, commuteAction);
            views.setOnClickPendingIntent(com.mihirkumar.dayflow.R.id.widget_commute, commutePendingIntent(context));
        }

        Intent toggle = new Intent(context, DayFlowWidgetProvider.class).setAction(ACTION_TOGGLE);
        PendingIntent togglePi = PendingIntent.getBroadcast(
                context, 1001, toggle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(com.mihirkumar.dayflow.R.id.widget_action, togglePi);

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                context, 1002, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(com.mihirkumar.dayflow.R.id.widget_container, openPi);

        manager.updateAppWidget(id, views);
        debugLog(context, "Widget rendered id=" + id + " status='" + statusText
                + "' task='" + current + "' timerState='"
                + (target == null ? "done" : active != null ? "active" : "next") + "'");
    }

    private static PendingIntent commutePendingIntent(Context context) {
        Intent intent = new Intent(context, DayFlowWidgetProvider.class)
                .setAction("com.mihirkumar.dayflow.COMMUTE");
        return PendingIntent.getBroadcast(context, 1003, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String getTodayMode(Context context) {
        String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getString("mode_" + key, p.getString(MODE_KEY, "wfh"));
    }

    private static String commuteAction(JSONObject day) {
        if (day == null) return "START";
        long a = day.optLong("commuteInStart", 0);
        long b = day.optLong("commuteInEnd", 0);
        long c = day.optLong("commuteOutStart", 0);
        long d = day.optLong("commuteOutEnd", 0);
        if (a > 0 && b == 0) return "END";
        if (b > 0 && c == 0) return "START";
        if (c > 0 && d == 0) return "END";
        return "DONE";
    }

    private static void toggleCommute(Context context) {
        try {
            long now = System.currentTimeMillis();
            JSONObject day = getTodayDay(context, true);
            long a = day.optLong("commuteInStart", 0);
            long b = day.optLong("commuteInEnd", 0);
            long c = day.optLong("commuteOutStart", 0);
            long d = day.optLong("commuteOutEnd", 0);
            if (a == 0) day.put("commuteInStart", now);
            else if (b == 0) day.put("commuteInEnd", now);
            else if (c == 0) day.put("commuteOutStart", now);
            else if (d == 0) day.put("commuteOutEnd", now);
            saveDay(context, day);
        } catch (Exception ignored) {
        }
    }

    private static JSONArray getTodayBlocks(Context context) {
        try {
            String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = p.getString("blocks_" + key, null);
            if (raw == null) raw = p.getString(BLOCKS_KEY, "[]");
            JSONArray result = new JSONArray(raw);
            debugLog(context, "Read blocks key=blocks_" + key + " rawFound=" + (p.getString("blocks_" + key, null) != null)
                    + " count=" + result.length());
            return result;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void toggleAttendance(Context context) {
        try {
            long now = System.currentTimeMillis();
            JSONObject day = getTodayDay(context, true);
            JSONArray sessions = day.optJSONArray("sessions");
            if (sessions == null) {
                sessions = new JSONArray();
                day.put("sessions", sessions);
            }

            if (hasOpenSession(day)) {
                JSONObject last = sessions.optJSONObject(sessions.length() - 1);
                last.put("out", now);
            } else {
                JSONObject session = new JSONObject();
                session.put("in", now);
                session.put("out", 0);
                sessions.put(session);
            }
            saveDay(context, day);
        } catch (Exception ignored) {
        }
    }

    private static boolean hasOpenSession(JSONObject day) {
        if (day == null) return false;
        JSONArray sessions = day.optJSONArray("sessions");
        if (sessions == null || sessions.length() == 0) return false;
        JSONObject last = sessions.optJSONObject(sessions.length() - 1);
        return last != null && last.optLong("in", 0) > 0 && last.optLong("out", 0) == 0;
    }

    private static long officeDuration(JSONObject day, long now) {
        if (day == null) return 0;
        long total = 0;
        JSONArray sessions = day.optJSONArray("sessions");
        if (sessions == null) return 0;
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null) continue;
            long in = s.optLong("in", 0);
            long out = s.optLong("out", 0);
            if (in > 0) total += (out > in ? out : now) - in;
        }
        return Math.max(0, total);
    }

    private static long commuteDuration(JSONObject day, long now) {
        if (day == null) return 0;
        long total = 0;
        long a = day.optLong("commuteInStart", 0);
        long b = day.optLong("commuteInEnd", 0);
        if (a > 0) total += (b > a ? b : now) - a;
        a = day.optLong("commuteOutStart", 0);
        b = day.optLong("commuteOutEnd", 0);
        if (a > 0) total += (b > a ? b : now) - a;
        return Math.max(0, total);
    }

    private static JSONObject getTodayDay(Context context, boolean create) {
        try {
            String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString(OFFICE_LOG_KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && key.equals(o.optString("date"))) return o;
            }
            if (!create) return null;

            JSONObject fresh = new JSONObject();
            fresh.put("date", key);
            fresh.put("sessions", new JSONArray());
            fresh.put("commuteInStart", 0);
            fresh.put("commuteInEnd", 0);
            fresh.put("commuteOutStart", 0);
            fresh.put("commuteOutEnd", 0);
            arr.put(fresh);
            p.edit().putString(OFFICE_LOG_KEY, arr.toString()).apply();
            return fresh;
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveDay(Context context, JSONObject day) {
        try {
            String key = day.optString("date");
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString(OFFICE_LOG_KEY, "[]"));
            boolean replaced = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && key.equals(o.optString("date"))) {
                    arr.put(i, day);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) arr.put(day);
            p.edit().putString(OFFICE_LOG_KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static boolean hasOpenCommute(JSONObject day) {
        if (day == null) return false;
        long a = day.optLong("commuteInStart", 0);
        long b = day.optLong("commuteInEnd", 0);
        long c = day.optLong("commuteOutStart", 0);
        long d = day.optLong("commuteOutEnd", 0);
        return (a > 0 && b == 0) || (c > 0 && d == 0);
    }

    private static String syllabusProgressPercent(Context context) {
        try {
            JSONArray topics = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(SYLLABUS_KEY, "[]"));
            if (topics.length() == 0) return "—";
            int done = 0;
            for (int i = 0; i < topics.length(); i++) {
                JSONObject topic = topics.optJSONObject(i);
                if (topic != null && topic.optBoolean("done", false)) done++;
            }
            return String.valueOf(Math.round(done * 100f / topics.length()));
        } catch (Exception e) {
            return "—";
        }
    }

    private static int currentMinutes() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
    }

    private static int mins(String value) {
        String[] p = value.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private static String time12(String value) {
        int m = mins(value);
        int h = (m / 60) % 12;
        if (h == 0) h = 12;
        return h + ":" + String.format(Locale.US, "%02d", m % 60) + (m >= 720 ? " PM" : " AM");
    }

    private static String fmt(int minutes) {
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + String.format(Locale.US, "%02d", minutes % 60) + "m";
    }

    private static String fmtMillis(long ms) {
        return fmt((int) Math.min(Integer.MAX_VALUE, Math.max(0, ms / 60000L)));
    }
}
