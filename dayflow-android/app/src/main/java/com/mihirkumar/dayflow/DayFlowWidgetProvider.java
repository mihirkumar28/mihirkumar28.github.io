package com.mihirkumar.dayflow;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DayFlowWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "dayflow";
    private static final String OFFICE_LOG_KEY = "officeLog";
    private static final String ACTION_TOGGLE = "com.mihirkumar.dayflow.CHECK_IN_OUT";
    private static final String ACTION_OPEN = "com.mihirkumar.dayflow.OPEN";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            toggleAttendance(context);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, DayFlowWidgetProvider.class);
            for (int id : manager.getAppWidgetIds(component)) update(context, manager, id);
        }
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), com.mihirkumar.dayflow.R.layout.widget_dayflow);
        JSONObject day = getTodayDay(context, false);
        boolean open = hasOpenSession(day);

        views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_status, open ? "IN OFFICE" : "OUT OF OFFICE");
        views.setTextViewText(com.mihirkumar.dayflow.R.id.widget_action, open ? "CHECK OUT" : "CHECK IN");

        Intent toggle = new Intent(context, DayFlowWidgetProvider.class).setAction(ACTION_TOGGLE);
        PendingIntent togglePi = PendingIntent.getBroadcast(
                context, 1001, toggle, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(com.mihirkumar.dayflow.R.id.widget_action, togglePi);

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                context, 1002, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(com.mihirkumar.dayflow.R.id.widget_container, openPi);

        manager.updateAppWidget(id, views);
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
}
