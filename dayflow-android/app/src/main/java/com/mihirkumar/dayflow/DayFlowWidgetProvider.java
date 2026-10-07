package com.mihirkumar.dayflow;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DayFlowWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "dayflow";
    private static final String OFFICE_LOG_KEY = "officeLog";
    private static final String BLOCKS_KEY = "blocks";
    private static final String MODE_KEY = "mode";
    private static final String ACTION_TOGGLE = "com.mihirkumar.dayflow.CHECK_IN_OUT";
    private static final String DEBUG_LOG_KEY = "debugLog";
    private static final String DEBUG_TAG = "DayFlow";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            toggleAttendance(context);
            refreshAll(context);
        } else if ("com.mihirkumar.dayflow.COMMUTE".equals(intent.getAction())) {
            toggleCommute(context);
            refreshAll(context);
        }
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, DayFlowWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(component)) {
            update(context, manager, id);
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
        String timing = "Open DayFlow to plan";
        try {
            JSONArray blocks = getTodayBlocks(context);
            int now = currentMinutes();
            debugLog(context, "Widget read blocks=" + blocks.length()
                    + " now=" + now + " mode=" + mode);
            JSONObject active = null;
            JSONObject next = null;

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

            JSONObject target = active != null ? active : next;
            if (target != null) {
                current = target.optString("title", "Current task");
                debugLog(context, "Widget selected task='" + current + "'"
                        + " start=" + target.optString("start", "")
                        + " end=" + target.optString("end", ""));
                int start = mins(target.optString("start", "00:00"));
                int end = mins(target.optString("end", "00:00"));
                if (active != null) {
                    timing = fmt(end - now) + " left";
                } else {
                    timing = "Next • " + time12(target.optString("start", "00:00"));
                }
            }
        } catch (Exception ignored) {
        }

        String statusText;
        if (!officeMode) {
            statusText = mode.equals("weekend") ? "WEEKEND" : "WFH";
        } else {
            statusText = inOffice ? "IN OFFICE • " + fmtMillis(officeMs)
                                  : "OUT • OFFICE " + fmtMillis(officeMs);
        }
        views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_status, statusText);
        views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_task, current);
        views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_timing,
                timing + " • Commute " + fmtMillis(commuteMs));

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
                + "' task='" + current + "' timing='" + timing + "'");
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
