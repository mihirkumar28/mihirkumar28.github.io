package com.mihirkumar.dayflow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class DayFlowUpdateDownloadWorker extends Worker {
    static final String UNIQUE_WORK_NAME = "dayflow-background-update-download";
    static final String WORK_TAG = "dayflow-update-download";
    static final String INPUT_VERSION_CODE = "versionCode";
    static final String INPUT_VERSION_NAME = "versionName";
    private static final String APK_URL = "https://github.com/mihirkumar28/mihirkumar28.github.io/releases/download/latest/app-release.apk";
    private static final String PREFS = "dayflow";
    private static final String PENDING_UPDATE_APK_KEY = "pendingUpdateApkPath";
    private static final String UPDATE_DOWNLOAD_ERROR_KEY = "appUpdateDownloadError";
    private static final long MAX_APK_BYTES = 120L * 1024L * 1024L;

    private static final String PROGRESS_CHANNEL_ID = "dayflow_update_progress";
    private static final String READY_CHANNEL_ID = "dayflow_update_ready";
    private static final int PROGRESS_NOTIFICATION_ID = 47101;
    private static final int READY_NOTIFICATION_ID = 47102;
    private static final int FAILED_NOTIFICATION_ID = 47103;
    private static final String INSTALL_UPDATE_ACTION = "com.mihirkumar.dayflow.ACTION_INSTALL_PENDING_UPDATE";

    public DayFlowUpdateDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        long expectedVersionCode = getInputData().getLong(INPUT_VERSION_CODE, -1L);
        String versionName = getInputData().getString(INPUT_VERSION_NAME);
        File apkFile = null;
        HttpURLConnection connection = null;

        try {
            if (expectedVersionCode <= 0L || versionName == null || versionName.trim().isEmpty()) {
                throw new IOException("The update information was incomplete. Check for updates again.");
            }
            createNotificationChannels(context);
            setForegroundAsync(makeForegroundInfo(context, versionName, "Preparing download…", 0, true))
                    .get(10, TimeUnit.SECONDS);

            File updatesDir = new File(context.getCacheDir(), "updates");
            if (!updatesDir.exists() && !updatesDir.mkdirs()) {
                throw new IOException("Couldn't create the update download folder.");
            }
            apkFile = new File(updatesDir, "dayflow-update.apk");
            if (apkFile.exists() && !apkFile.delete()) {
                throw new IOException("Couldn't replace an earlier partial update download.");
            }

            // Include the release version in the URL so a previously cached APK cannot shadow the new build.
            String versionedApkUrl = APK_URL + (APK_URL.contains("?") ? "&" : "?")
                    + "v=" + expectedVersionCode;
            connection = (HttpURLConnection) new URL(versionedApkUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "DayFlow-Android");
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
            connection.setRequestProperty("Pragma", "no-cache");
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                throw new IOException("Update download returned HTTP " + response + ".");
            }

            long expectedBytes = connection.getContentLengthLong();
            if (expectedBytes > MAX_APK_BYTES) {
                throw new IOException("The update file is unexpectedly large.");
            }

            long downloadedBytes = 0L;
            int lastPercent = -1;
            InputStream input = connection.getInputStream();
            try (FileOutputStream output = new FileOutputStream(apkFile, false)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (isStopped()) throw new IOException("The update download was stopped.");
                    downloadedBytes += count;
                    if (downloadedBytes > MAX_APK_BYTES) {
                        throw new IOException("The update file exceeded the size limit.");
                    }
                    output.write(buffer, 0, count);

                    int percent = expectedBytes > 0L
                            ? (int) Math.min(100L, downloadedBytes * 100L / expectedBytes)
                            : -1;
                    if (percent != lastPercent || downloadedBytes % (1024L * 1024L) < count) {
                        lastPercent = percent;
                        setProgressAsync(new Data.Builder()
                                .putLong("downloadedBytes", downloadedBytes)
                                .putLong("expectedBytes", expectedBytes)
                                .putInt("progress", Math.max(0, percent))
                                .build());
                        String text = expectedBytes > 0L
                                ? percent + "% · " + formatBytes(downloadedBytes) + " of " + formatBytes(expectedBytes)
                                : formatBytes(downloadedBytes) + " downloaded";
                        setForegroundAsync(makeForegroundInfo(context, versionName, text,
                                Math.max(0, percent), expectedBytes <= 0L));
                    }
                }
                output.flush();
            } finally {
                input.close();
                connection.disconnect();
                connection = null;
            }

            if (apkFile.length() <= 0L) throw new IOException("The downloaded update was empty.");

            PackageManager pm = context.getPackageManager();
            int packageFlags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES
                    : PackageManager.GET_SIGNATURES;
            PackageInfo candidate = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), packageFlags);
            if (candidate == null || !context.getPackageName().equals(candidate.packageName)) {
                throw new IOException("The downloaded file isn't a valid DayFlow package.");
            }
            long downloadedVersionCode = Build.VERSION.SDK_INT >= 28
                    ? candidate.getLongVersionCode() : candidate.versionCode;
            if (downloadedVersionCode != expectedVersionCode) {
                throw new IOException("The APK version doesn't match the published update information.");
            }

            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), packageFlags);
            long installedVersionCode = Build.VERSION.SDK_INT >= 28
                    ? installed.getLongVersionCode() : installed.versionCode;
            if (downloadedVersionCode <= installedVersionCode) {
                throw new IOException("This downloaded build isn't newer than the installed app.");
            }
            if (!hasSameSigningIdentity(installed, candidate)) {
                throw new IOException("The downloaded APK uses a different signing key and can't be installed as an in-place update.");
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(PENDING_UPDATE_APK_KEY, apkFile.getAbsolutePath())
                    .remove(UPDATE_DOWNLOAD_ERROR_KEY)
                    .apply();

            postReadyNotification(context, versionName);
            return Result.success(new Data.Builder()
                    .putString("apkPath", apkFile.getAbsolutePath())
                    .putString("versionName", versionName)
                    .build());
        } catch (Exception e) {
            if (connection != null) connection.disconnect();
            if (apkFile != null && apkFile.exists()) {
                // Keep no partial or invalid download around to be picked up accidentally.
                // If validation failed after a complete transfer, it must be downloaded again.
                // A valid package is saved to preferences only after all checks pass.
                //noinspection ResultOfMethodCallIgnored
                apkFile.delete();
            }
            String message = e.getMessage() == null || e.getMessage().trim().isEmpty()
                    ? "The update download failed. Please try again." : e.getMessage();
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(UPDATE_DOWNLOAD_ERROR_KEY, message)
                    .apply();
            postFailureNotification(context);
            return Result.failure(new Data.Builder().putString("error", message).build());
        }
    }

    private static boolean hasSameSigningIdentity(PackageInfo installed, PackageInfo candidate) {
        Signature[] installedSigners;
        Signature[] candidateSigners;
        if (Build.VERSION.SDK_INT >= 28) {
            if (installed.signingInfo == null || candidate.signingInfo == null) return false;
            installedSigners = installed.signingInfo.getApkContentsSigners();
            candidateSigners = candidate.signingInfo.getApkContentsSigners();
        } else {
            installedSigners = installed.signatures;
            candidateSigners = candidate.signatures;
        }
        if (installedSigners == null || candidateSigners == null
                || installedSigners.length == 0 || candidateSigners.length == 0
                || installedSigners.length != candidateSigners.length) return false;
        for (Signature current : installedSigners) {
            boolean found = false;
            for (Signature incoming : candidateSigners) {
                if (current.equals(incoming)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private ForegroundInfo makeForegroundInfo(Context context, String versionName, String text,
                                               int progress, boolean indeterminate) {
        Notification notification = new NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading DayFlow " + versionName)
                .setContentText(text)
                .setContentIntent(openAppPendingIntent(context, PROGRESS_NOTIFICATION_ID))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, progress, indeterminate)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification);
    }

    private static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(PROGRESS_CHANNEL_ID,
                "DayFlow update download", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(READY_CHANNEL_ID,
                "DayFlow update alerts", NotificationManager.IMPORTANCE_HIGH));
    }

    private static PendingIntent openAppPendingIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(INSTALL_UPDATE_ACTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    private static void postReadyNotification(Context context, String versionName) {
        createNotificationChannels(context);
        Notification notification = new NotificationCompat.Builder(context, READY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("DayFlow update ready")
                .setContentText("Version " + versionName + " is downloaded. Tap to install.")
                .setContentIntent(openAppPendingIntent(context, READY_NOTIFICATION_ID))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(READY_NOTIFICATION_ID, notification);
    }

    private static void postFailureNotification(Context context) {
        createNotificationChannels(context);
        Notification notification = new NotificationCompat.Builder(context, READY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("DayFlow update couldn't finish")
                .setContentText("Open DayFlow to see what happened and try again.")
                .setContentIntent(openAppPendingIntent(context, FAILED_NOTIFICATION_ID))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(FAILED_NOTIFICATION_ID, notification);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
