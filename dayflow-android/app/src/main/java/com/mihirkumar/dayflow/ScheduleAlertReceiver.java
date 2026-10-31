package com.mihirkumar.dayflow;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

/** Delivers slot-end alerts and restores today's schedule alarms after system events. */
public class ScheduleAlertReceiver extends BroadcastReceiver {
    static final String ACTION_SLOT_END_REMINDER = "com.mihirkumar.dayflow.SLOT_END_REMINDER";
    static final String ACTION_DAILY_RESCHEDULE = "com.mihirkumar.dayflow.DAILY_RESCHEDULE";
    static final String EXTRA_TITLE = "slotTitle";
    static final String EXTRA_END_TIME = "slotEndTime";
    static final String EXTRA_REQUEST_CODE = "requestCode";
    private static final String CHANNEL_ID = "dayflow_slot_end_alerts";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_SLOT_END_REMINDER.equals(action)) {
            showReminder(context, intent);
            return;
        }
        if (ACTION_DAILY_RESCHEDULE.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            ScheduleAlertScheduler.scheduleToday(context);
        }
    }

    private void showReminder(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(manager);

        String title = intent.getStringExtra(EXTRA_TITLE);
        String endTime = intent.getStringExtra(EXTRA_END_TIME);
        int notificationId = intent.getIntExtra(EXTRA_REQUEST_CODE, 1);
        if (title == null || title.trim().isEmpty()) title = "Your current slot";
        String body = endTime == null || endTime.trim().isEmpty()
                ? title + " is due to end in 10 minutes."
                : title + " ends at " + formatTime(endTime) + ".";

        Intent openApp = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, notificationId, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("10 minutes left")
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build();
        try {
            manager.notify(notificationId, notification);
        } catch (SecurityException ignored) {
            // Notification permission may have been revoked between the pre-check and delivery.
        }
    }

    private void ensureChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 26
                || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "DayFlow slot-end alerts", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Audible reminders ten minutes before a scheduled slot ends.");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 450, 250, 450});
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(alarmSound, attributes);
        manager.createNotificationChannel(channel);
    }

    private String formatTime(String value) {
        try {
            String[] parts = value.split(":");
            if (parts.length != 2) return value;
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return value;
            }
            return String.format(Locale.getDefault(), "%d:%02d %s",
                    (hour % 12 == 0 ? 12 : hour % 12), minute, hour < 12 ? "AM" : "PM");
        } catch (Exception ignored) {
            return value;
        }
    }
}
