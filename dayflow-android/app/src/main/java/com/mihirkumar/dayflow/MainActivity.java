package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.ExistingWorkPolicy;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.provider.Settings;
import android.util.Log;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final String PREFS = "dayflow";
    private static final String KEY = "blocks";
    private static final String MODE_KEY = "mode";
    private static final String COMMUTE_IN_START = "commuteInStart";
    private static final String COMMUTE_IN_END = "commuteInEnd";
    private static final String COMMUTE_OUT_START = "commuteOutStart";
    private static final String COMMUTE_OUT_END = "commuteOutEnd";
    private static final String OFFICE_LOG_KEY = "officeLog";
    private static final String DEBUG_LOG_KEY = "debugLog";
    private static final String DEBUG_TAG = "DayFlow";
    private static final String UPDATE_METADATA_URL = "https://mihirkumar28.github.io/update.json";
    private static final String UPDATE_APK_URL = "https://mihirkumar28.github.io/app-release.apk";
    private static final String UPDATE_LAST_CHECK_KEY = "appUpdateLastCheckAt";
    private static final String UPDATE_PROMPTED_VERSION_KEY = "appUpdatePromptedVersion";
    private static final String PENDING_UPDATE_APK_KEY = "pendingUpdateApkPath";
    private static final String UPDATE_DOWNLOAD_ERROR_KEY = "appUpdateDownloadError";
    private static final String UPDATE_INSTALL_PERMISSION_PROMPTED_KEY = "appUpdateInstallPermissionPrompted";
    private static final int UPDATE_NOTIFICATION_PERMISSION_REQUEST = 4203;
    private static final long UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long MAX_UPDATE_APK_BYTES = 120L * 1024L * 1024L;
    private static final String SYLLABUS_KEY = "syllabusTopics";
    private static final String SYLLABUS_TARGET_KEY = "syllabusTargetDate";
    private static final String SYLLABUS_START_KEY = "syllabusStartDate";
    private static final String ROUTINE_MIGRATION_V2_KEY = "routineDefaultMigrationV2";
    private static final String ROUTINE_MIGRATION_V3_KEY = "routineDefaultMigrationV3";
    private static final String STARTER_SEEDED_KEY = "starterSyllabusSeeded_v2";
    private static final String INTERVIEW_PLAN_START_KEY = "softwareInterviewPlanStart";
    private static final String INTERVIEW_TARGET_KEY = "softwareInterviewTarget";
    private static final String MONTHLY_GOALS_KEY = "monthlyGoals";
    private static final String MONTHLY_PLAN_MIGRATION_KEY = "monthlyGoalsSourcePlanMigrationVersion";
    private static final String PDF_LIBRARY_ROADMAP_GOAL_MIGRATION_KEY = "pdfLibraryRoadmapGoalMigration_v1";
    private static final int MONTHLY_PLAN_MIGRATION_VERSION = 2;
    private static final String ROADMAP_MILESTONES_KEY = "roadmapMilestones";
    private static final String ROADMAP_EXPANSION_KEY = "roadmapExpandedNodeIds_v1";
    private static final String ROADMAP_MONTH_SLOTS_KEY_PREFIX = "roadmapMonthSlots_v1_";
    private static final String ROADMAP_MONTHLY_PANEL_COLLAPSED_KEY = "roadmapMonthlyGoalsCollapsed_v1";
    private static final String CSE_CHECKPOINT_KEY = "cseCheckpointDate";
    private static final String INTERVIEW_TIER34_KEY = "softwareTier34Target";
    private static final String CAREER_TARGET_KEY = "careerTargetMonthlyTakeHome";
    private static final String CAREER_TARGET_NOTES_KEY = "careerTargetNotes";
    private static final String[] MONTHLY_GOAL_TRACKS = {
            "General Studies", "Maths Optional", "Software Interview Prep", "CSE Revision & Tests", "Other"
    };
    private static final String[] MILESTONE_TRACKS = {"UPSC CSE", "Software interviews", "Personal / other"};
    private static final int REQUEST_EXPORT_BACKUP = 4201;
    private static final int REQUEST_IMPORT_BACKUP = 4202;
    private static final String[] SYLLABUS_SUBJECTS = {
            "General Studies", "Maths Optional", "CSAT", "Qualifying English",
            "Indian Language", "Essay", "Interview", "Software Interview Prep"
    };

    private final ArrayList<Block> blocks = new ArrayList<>();
    private final Handler handler = new Handler();

    private LinearLayout root, timeline, officeCard;
    private FrameLayout screenFrame;
    private LinearLayout pagePlan, pageProgress, pageOffice, pageMore, pageSyllabus, pageScreenTime;
    private LinearLayout planList, planTemplatePreview, actualScheduleContent, progressContent, officePageContent, moreContent, syllabusList, screenTimeList;
    private Button planDayScheduleToggle, todayRoutineEditButton;
    private LinearLayout bottomNavigation;
    private TextView syllabusSummaryView, screenTimeSummaryView, syllabusCoverageSummary;
    private LinearLayout syllabusDetailContent;
    private EditText syllabusSearch;
    private LinearLayout monthlyGoalsSection, monthlyGoalList, milestonesSection, milestoneList, syllabusTopicsSection;
    private LinearLayout roadmapDetailsSection;
    private TextView goalMonthLabel, careerGoalSummary;
    private LinearLayout roadmapFocusGoalList, roadmapFilterChips, roadmapTreeContainer, roadmapSearchRow, roadmapMonthlyGoalsContent;
    private LinearLayout roadmapOverdueReviewBanner;
    private TextView roadmapFocusMonthLabel, roadmapFocusCount, roadmapTreeSummary, roadmapOverdueReviewText;
    private EditText roadmapSearchInput;
    private ImageButton roadmapSearchToggle;
    private final Map<String, Button> roadmapFilterButtons = new LinkedHashMap<>();
    private final Set<String> expandedRoadmapNodeIds = new HashSet<>();
    private String roadmapSearchText = "";
    private String roadmapActiveFilter = "All";
    private boolean roadmapExpansionStateLoaded = false;
    private Button syllabusProgressToggle, roadmapDetailsToggle;
    private boolean syllabusDetailsExpanded = false;
    private boolean roadmapDetailsExpanded = false;
    private boolean roadmapMonthlyGoalsCollapsed = false;
    private Button roadmapMonthlyGoalsToggle, roadmapTreeEditButton;
    private boolean roadmapTreeEditMode = false;
    private String selectedGoalMonth;
    private String activePage = "home";
    private String syllabusFilter = "All";
    private String syllabusQuery = "";
    private final Map<String, TextView> navItems = new LinkedHashMap<>();
    private TextView dateView, modeView, completionView, progressPercent;
    private TextView currentTitle, currentTime, currentCountdown, plannedView, doneView, studyActualView;
    private TextView officeStatus, officeTimes, commuteView;
    private Button officeAction, commuteAction, focusAction;
    private String mode = "wfh";
    // Plan preview selection is deliberately separate from the template applied to the live date.
    private String planPreviewMode;
    private String selectedDateKey;
    private String officeSummaryPeriod = "all";
    private Calendar attendanceCalendarMonth = Calendar.getInstance();
    private boolean updateCheckRunning;
    private DayFlowUpdateInfo.ReleaseInfo updatePendingAfterNotificationPermission;
    private androidx.lifecycle.LiveData<WorkInfo> updateDownloadLiveData;
    private androidx.lifecycle.Observer<WorkInfo> updateDownloadObserver;

    private static class Block {
        long id;
        String start, end, title, priority, rigidity, status;
        // Template ownership is provenance, not storage identity. A daily block is still
        // stored under its date key and can never write back into any reusable template.
        String templateOwner;
        long actualMs, actualStartedAt;
        Block(long id, String start, String end, String title, String legacyBehavior, String status) {
            this(id, start, end, title, importanceFromBehavior(legacyBehavior), rigidityFromBehavior(legacyBehavior), status);
        }
        Block(long id, String start, String end, String title, String priority, String rigidity, String status) {
            this.id = id;
            this.start = start;
            this.end = end;
            this.title = title;
            this.priority = normalizeImportance(priority);
            this.rigidity = "fixed".equals(rigidity) ? "fixed" : "flexible";
            this.status = status;
            this.actualMs = 0L;
            this.actualStartedAt = 0L;
        }
        private static String importanceFromBehavior(String behavior) {
            if (behavior == null) return "normal";
            if (behavior.contains(":")) return normalizeImportance(behavior.substring(behavior.indexOf(':') + 1));
            if ("fixed".equals(behavior) || "important".equals(behavior)) return "important";
            if ("optional".equals(behavior)) return "optional";
            if ("critical".equals(behavior)) return "critical";
            return "normal";
        }
        private static String rigidityFromBehavior(String behavior) {
            if (behavior == null) return "flexible";
            if (behavior.contains(":")) return "fixed".equals(behavior.substring(0, behavior.indexOf(':'))) ? "fixed" : "flexible";
            return "fixed".equals(behavior) ? "fixed" : "flexible";
        }
        private static String normalizeImportance(String importance) {
            if ("critical".equals(importance) || "important".equals(importance)
                    || "normal".equals(importance) || "optional".equals(importance)) return importance;
            if ("fixed".equals(importance)) return "important";
            if ("flexible".equals(importance)) return "normal";
            return "normal";
        }
    }

    private static class SyllabusTopic {
        long id;
        String subject, title;
        boolean done;
        long completedAt;
        int sortOrder;
        SyllabusTopic(long id, String subject, String title, boolean done, long completedAt) {
            this.id = id;
            this.subject = subject;
            this.title = title;
            this.done = done;
            this.completedAt = completedAt;
            this.sortOrder = 0;
        }
    }

    private static class MonthlyGoal {
        long id;
        String month, track, title, target;
        // Structured execution metadata shown directly beneath each roadmap goal.
        String source, pages, estimatedTime;
        String originalMonth, lifecycleStatus;
        long completedAt, parentGoalId;
        int sortOrder;
        boolean done;
        final ArrayList<String> history = new ArrayList<>();

        MonthlyGoal(long id, String month, String track, String title, String target, boolean done) {
            this.id = id;
            this.month = month;
            this.track = track;
            this.title = title;
            this.target = target;
            this.source = "";
            this.pages = "";
            this.estimatedTime = "";
            this.done = done;
            this.originalMonth = month;
            this.lifecycleStatus = done ? MonthlyGoalLifecycle.COMPLETED : MonthlyGoalLifecycle.ACTIVE;
            this.completedAt = 0L;
            this.parentGoalId = 0L;
        }
    }

        private static class RoadmapMilestone {
        long id;
        String key, track, title, date, detail;
        boolean done;
        RoadmapMilestone(long id, String key, String track, String title, String date, String detail, boolean done) {
            this.id = id;
            this.key = key == null ? "" : key;
            this.track = track;
            this.title = title;
            this.date = date;
            this.detail = detail;
            this.done = done;
        }
    }

    /**
     * A view-model node assembled from existing local goals, syllabus topics and
     * milestones. It deliberately does not duplicate/persist those records.
     */
    private static class RoadmapNode {
        String id, title, type, subtitle;
        String rootKey, subjectKey, goalTrack, milestoneTrack;
        String month, statusPeriod, groupKind;
        Object item;
        boolean done;
        final ArrayList<RoadmapNode> children = new ArrayList<>();
    }

    private long lastRenderedMinute = -1;
    private final Runnable liveRefresh = new Runnable() {
        @Override public void run() {
            long minute = System.currentTimeMillis() / 60000L;
            if (selectedDateKey != null && selectedDateKey.equals(todayKey())) {
                if (minute != lastRenderedMinute) render();
                else updateLiveBits();
            } else {
                lastRenderedMinute = minute;
            }
            long remainder = System.currentTimeMillis() % 1000L;
            handler.postDelayed(this, Math.max(50L, 1000L - remainder));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(color("#0D1512"));
        window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        selectedDateKey = todayKey();
        migrateUneditedDefaultTemplates();
        migrateUntouchedWfhMorningPlan();
        load();
        buildUi();
        ensureStarterPlans();
        ensureRoadmapData();
        refreshRoadmapTabContent();
        render();
        ensureScreenTimeWorker();
        if (ScreenTimeTracker.hasUsageAccess(this)) ScreenTimeTracker.refreshRecentDays(this, 7);

        handler.postDelayed(liveRefresh, Math.max(50L, 1000L - (System.currentTimeMillis() % 1000L)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSelectedDate();
        ensureScreenTimeWorker();
        if (ScreenTimeTracker.refreshRecentDays(this, 7)) DayFlowWidgetProvider.refreshAll(this);
        render();
        DayFlowWidgetProvider.refreshAll(this);
        handlePendingUpdateOnResume();
        checkForAppUpdate(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if ("com.mihirkumar.dayflow.ACTION_INSTALL_PENDING_UPDATE".equals(intent.getAction())) {
            handler.post(this::handlePendingUpdateOnResume);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != UPDATE_NOTIFICATION_PERMISSION_REQUEST) return;
        DayFlowUpdateInfo.ReleaseInfo release = updatePendingAfterNotificationPermission;
        updatePendingAfterNotificationPermission = null;
        if (release == null) return;
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            enqueueBackgroundUpdateDownload(release);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Notifications are disabled")
                .setMessage("DayFlow can still download the update in the background, but Android may hide the notification when it's ready. You can continue now and reopen DayFlow to finish installation.")
                .setPositiveButton("Continue download", (d, w) -> enqueueBackgroundUpdateDownload(release))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(liveRefresh);
        if (updateDownloadLiveData != null && updateDownloadObserver != null) {
            updateDownloadLiveData.removeObserver(updateDownloadObserver);
            updateDownloadLiveData = null;
            updateDownloadObserver = null;
        }
        super.onDestroy();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int mins(String s) {
        String[] p = s.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private String time12(String t) {
        int m = mins(t);
        int h = (m / 60) % 12;
        if (h == 0) h = 12;
        return h + ":" + String.format(Locale.US, "%02d", m % 60) + (m >= 720 ? " PM" : " AM");
    }

    private String fmt(int n) {
        int h = n / 60, m = n % 60;
        if (h > 0) return h + "h " + String.format(Locale.US, "%02d", m) + "m";
        return m + "m";
    }

    private String fmtShort(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "m";
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }

    private String fmtMillisForLabel(long ms) {
        return fmtShort((int) Math.min(Integer.MAX_VALUE, Math.max(0L, ms / 60000L)));
    }

    private String fmtMillis(long ms) {
        long minutes = Math.max(0, ms / 60000L);
        return fmtShort((int) Math.min(minutes, Integer.MAX_VALUE));
    }
    private String fmtPreciseMillis(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0) return hours + "h " + String.format(Locale.US, "%02dm %02ds", minutes, remainder);
        if (minutes > 0) return minutes + "m " + String.format(Locale.US, "%02ds", remainder);
        return seconds + "s";
    }

    private long millisAtMinute(int minuteOfDay) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, minuteOfDay / 60)));
        c.set(Calendar.MINUTE, Math.max(0, Math.min(59, minuteOfDay % 60)));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long millisAtDateMinute(String dateKey, int minuteOfDay) {
        Calendar c = Calendar.getInstance();
        Date date = parseDateKey(dateKey);
        if (date != null) c.setTime(date);
        c.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, minuteOfDay / 60)));
        c.set(Calendar.MINUTE, Math.max(0, Math.min(59, minuteOfDay % 60)));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String formatCountdown(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0) return hours + "h " + String.format(Locale.US, "%02dm %02ds", minutes, remainder);
        if (minutes > 0) return minutes + "m " + String.format(Locale.US, "%02ds", remainder);
        return remainder + "s";
    }


    private String clock(long timestamp) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    private String toTime(int n) {
        n = Math.max(0, Math.min(1439, n));
        return String.format(Locale.US, "%02d:%02d", n / 60, n % 60);
    }

    private int roundUp5(int m) {
        return Math.min(1439, ((m + 4) / 5) * 5);
    }

    private int color(String hex) {
        return Color.parseColor(DayFlowPalette.textHex(hex));
    }

    private int backgroundColor(String hex) {
        return Color.parseColor(DayFlowPalette.backgroundHex(hex));
    }

    private double luminance(int c) {
        double r = Color.red(c) / 255.0;
        double g = Color.green(c) / 255.0;
        double b = Color.blue(c) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private GradientDrawable bg(String hex, float radius) {
        GradientDrawable g = new GradientDrawable();
        int fill = backgroundColor(hex);
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (luminance(fill) > 0.78) g.setStroke(dp(1), color("#DDE6DE"));
        return g;
    }

    private TextView text(String value, float sp, int c, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(c == Color.WHITE ? color("#18372E") : c);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private Button actionButton(String value, int textColor, String background) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(12);
        b.setAllCaps(false);
        int fill = backgroundColor(background);
        int foreground = textColor;
        if (textColor == Color.WHITE && luminance(fill) > 0.68) foreground = color("#18372E");
        b.setTextColor(foreground);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setBackground(bg(background, 12));
        return b;
    }

    private Button chip(String label) {
        Button b = actionButton(label, color("#D6E1D8"), "#1B2621");
        b.setTextSize(11);
        return b;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private View spacer(int height) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return s;
    }

    private View spacerHorizontal(int width) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(width), 1));
        return s;
    }

    private int statusBarInsetTop(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return insets.getInsets(WindowInsets.Type.statusBars()).top;
        }
        return insets.getSystemWindowInsetTop();
    }

    private void buildUi() {
        LinearLayout shell = vertical();
        shell.setBackgroundColor(color("#0D1512"));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(color("#0D1512"));

        screenFrame = new FrameLayout(this);
        root = vertical();
        root.setPadding(dp(18), dp(10), dp(18), dp(20));
        screenFrame.addView(root, new FrameLayout.LayoutParams(-1, -2));
        scroll.addView(screenFrame, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(shell);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = Math.max(dp(10), statusBarInsetTop(insets) + dp(4));
            v.setPadding(dp(18), top, dp(18), dp(20));
            return insets;
        });

        buildHeader();
        buildModeRow();
        buildCurrentCard();
        buildTodaySummary();

        officeCard = vertical();
        officeCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        officeCard.setBackground(bg("#17231D", 18));
        LinearLayout.LayoutParams ocp = new LinearLayout.LayoutParams(-1, -2);
        ocp.bottomMargin = dp(16);
        root.addView(officeCard, ocp);
        buildOfficeCardContents();

        buildScheduleHeader();
        timeline = vertical();
        root.addView(timeline, new LinearLayout.LayoutParams(-1, -2));
        root.addView(spacer(8));

        TextView footer = text("Saved privately on this device", 11, color("#82948A"), false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(32)));

        buildSecondaryPages();
        buildBottomNavigation(shell);
        switchPage("home");
    }

    private void buildSecondaryPages() {
        pagePlan = vertical(); pageProgress = vertical(); pageOffice = vertical();
        pageMore = vertical(); pageSyllabus = vertical(); pageScreenTime = vertical();
        for (LinearLayout page : new LinearLayout[]{pagePlan,pageProgress,pageOffice,pageMore,pageSyllabus,pageScreenTime}) {
            page.setPadding(dp(18), dp(18), dp(18), dp(24));
            page.setOnApplyWindowInsetsListener((v, insets) -> {
                // Secondary tabs share the same edge-to-edge window as Today.
                // Reserve the system status-bar height plus normal page spacing.
                int top = Math.max(dp(18), statusBarInsetTop(insets) + dp(18));
                v.setPadding(dp(18), top, dp(18), dp(24));
                return insets;
            });
            page.setVisibility(View.GONE);
            screenFrame.addView(page,new FrameLayout.LayoutParams(-1,-2));
        }
        buildPlanPage(); buildProgressPage(); buildOfficePage(); buildMorePage(); buildSyllabusPage(); buildScreenTimePage();
    }

    private void pageHeading(LinearLayout page,String eyebrow,String title,String subtitle) {
        TextView small=text(eyebrow.toUpperCase(Locale.US),10,color("#65C5A0"),true);
        small.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams sm=new LinearLayout.LayoutParams(-1,-2); sm.bottomMargin=dp(5); page.addView(small,sm);
        TextView heading=text(title,27,color("#F2F7F3"),true);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2); hp.bottomMargin=dp(5); page.addView(heading,hp);
        TextView detail=text(subtitle,13,color("#9AABA0"),false); detail.setLineSpacing(dp(2),1f);
        LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(-1,-2); detailLp.bottomMargin=dp(18); page.addView(detail,detailLp);
    }

    private GradientDrawable surfaceBackground(float radius) {
        GradientDrawable drawable = bg("#FFFFFF", radius);
        drawable.setStroke(dp(1), color("#DDE6DE"));
        return drawable;
    }

    private void sectionLabel(LinearLayout parent, String label) {
        TextView labelView = text(label, 14, color("#18372E"), true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        parent.addView(labelView, lp);
    }

    private void addManageRow(LinearLayout parent, String title, String detail, Runnable action) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(5), dp(10), dp(4), dp(10));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        parent.addView(row, rp);
        LinearLayout details = vertical();
        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1);
        detailsLp.rightMargin = dp(8);
        row.addView(details, detailsLp);
        details.addView(text(title, 13, color("#18372E"), true));
        TextView hint = text(detail, 11, color("#687A70"), false);
        hint.setLineSpacing(dp(1), 1f);
        details.addView(hint);
        row.addView(text("›", 23, color("#286B55"), true));
        row.setOnClickListener(v -> action.run());
        View divider = new View(this);
        divider.setBackgroundColor(color("#DDE6DE"));
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private LinearLayout surfaceCard() {
        LinearLayout card=vertical(); card.setPadding(dp(16),dp(15),dp(16),dp(15)); card.setBackground(bg("#18231E",18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);card.setLayoutParams(lp);return card;
    }

    private Button primaryButton(String label,Runnable action) {
        Button b=actionButton(label,color("#F5FBF7"),"#23845F");b.setTextSize(13);b.setMinHeight(dp(44));b.setPadding(dp(12),0,dp(12),0);b.setOnClickListener(v->action.run());return b;
    }
    private Button secondaryButton(String label,Runnable action) {
        Button b=actionButton(label,color("#D6E4DA"),"#23352C");b.setTextSize(12);b.setMinHeight(dp(40));b.setPadding(dp(10),0,dp(10),0);b.setOnClickListener(v->action.run());return b;
    }

    private void buildBottomNavigation(LinearLayout shell) {
        bottomNavigation=horizontal();
        bottomNavigation.setGravity(Gravity.CENTER);
        // Keep the 64dp tab row above Android's navigation controls. On Android 15+
        // edge-to-edge is enforced for this target SDK, so the nav-bar area must be
        // added to this view's bottom padding rather than covered by the tab row.
        bottomNavigation.setPadding(dp(8),dp(4),dp(8),dp(6));
        bottomNavigation.setBackgroundColor(Color.WHITE);
        bottomNavigation.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            } else {
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(dp(8),dp(4),dp(8),dp(6) + bottomInset);
            return insets;
        });
        String[][] items={{"⌂","Today","home"},{"▤","Plan","plan"},{"◔","Roadmap","syllabus"},{"◷","Office","office"},{"•••","More","more"}};
        for(String[] item:items){
            LinearLayout tab=vertical();tab.setGravity(Gravity.CENTER);tab.setPadding(dp(3),dp(5),dp(3),dp(4));
            TextView icon=text(item[0],19,color("#687A70"),true);icon.setGravity(Gravity.CENTER);
            TextView label=text(item[1],10,color("#687A70"),true);label.setGravity(Gravity.CENTER);
            tab.addView(icon);tab.addView(label);tab.setContentDescription(item[1]);tab.setOnClickListener(v->switchPage(item[2]));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.leftMargin=dp(2);lp.rightMargin=dp(2);bottomNavigation.addView(tab,lp);navItems.put(item[2],label);
        }
        // WRAP_CONTENT lets the view grow by the system-bar inset while preserving
        // a 64dp row for the five tabs, with a white background behind the system bar.
        shell.addView(bottomNavigation,new LinearLayout.LayoutParams(-1,-2));
        bottomNavigation.requestApplyInsets();
    }

    private void refreshCurrentWorkspace() {
        if ("home".equals(activePage)) render();
        else if ("plan".equals(activePage)) renderPlanPage();
        else if ("progress".equals(activePage)) renderProgressPage();
        else if ("office".equals(activePage)) renderOfficePage();
        else if ("more".equals(activePage)) renderMorePage();
        else if ("syllabus".equals(activePage)) refreshRoadmapTabContent();
        else if ("screenTime".equals(activePage)) renderScreenTimePage();
        renderBottomNavOnly();
    }

    private void switchPage(String page) {
        if(screenFrame==null)return;
        if("office".equals(page)&&!todayKey().equals(selectedDateKey)){
            save();selectedDateKey=todayKey();loadSelectedDate();render();
        }
        activePage=page;
        root.setVisibility("home".equals(page)?View.VISIBLE:View.GONE);
        pagePlan.setVisibility("plan".equals(page)?View.VISIBLE:View.GONE);
        pageProgress.setVisibility("progress".equals(page)?View.VISIBLE:View.GONE);
        pageOffice.setVisibility("office".equals(page)?View.VISIBLE:View.GONE);
        pageMore.setVisibility("more".equals(page)?View.VISIBLE:View.GONE);
        pageSyllabus.setVisibility("syllabus".equals(page)?View.VISIBLE:View.GONE);
        pageScreenTime.setVisibility("screenTime".equals(page)?View.VISIBLE:View.GONE);
        renderBottomNavOnly();
        if("plan".equals(page))renderPlanPage();
        else if("progress".equals(page))renderProgressPage();
        else if("office".equals(page))renderOfficePage();
        else if("more".equals(page))renderMorePage();
        else if("syllabus".equals(page))refreshRoadmapTabContent();
        else if("screenTime".equals(page))renderScreenTimePage();
        else if("home".equals(page))refreshTodaySummaryProgress();
    }

    private void buildPlanPage() {
        if (planPreviewMode == null) planPreviewMode = mode;
        pageHeading(pagePlan,"Build your day","Plan","Choose WFH, Office or Weekend to view its saved template. The selected date's actual schedule stays separate.");
        LinearLayout dateCard=surfaceCard();LinearLayout dateRow=horizontal();
        dateRow.addView(secondaryButton("‹",()->{changeSelectedDate(-1);renderPlanPage();}),new LinearLayout.LayoutParams(dp(45),dp(40)));
        TextView date=text("",14,color("#F1F6F2"),true);date.setGravity(Gravity.CENTER);date.setTag("planDate");dateRow.addView(date,new LinearLayout.LayoutParams(0,dp(40),1));
        dateRow.addView(secondaryButton("›",()->{changeSelectedDate(1);renderPlanPage();}),new LinearLayout.LayoutParams(dp(45),dp(40)));dateCard.addView(dateRow);
        TextView modeLabel=text("DAY TYPE",10,color("#687A70"),true);
        LinearLayout.LayoutParams modeLabelLp=new LinearLayout.LayoutParams(-1,-2);modeLabelLp.topMargin=dp(12);dateCard.addView(modeLabel,modeLabelLp);
        LinearLayout modes=horizontal();
        String[][] choices={{"WFH","wfh"},{"Office","office"},{"Weekend","weekend"}};
        for(String[] choice:choices){
            Button b=secondaryButton(choice[0],()->choosePlanPreviewMode(choice[1]));
            b.setTag("plan-mode-chip-"+choice[1]);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);
            if(modes.getChildCount()>0)lp.leftMargin=dp(6);
            modes.addView(b,lp);
        }
        LinearLayout.LayoutParams modesLp=new LinearLayout.LayoutParams(-1,dp(42));modesLp.topMargin=dp(6);dateCard.addView(modes,modesLp);
        LinearLayout templateRow=horizontal();
        Button applyRoutine=primaryButton("Apply template to date",()->showTemplatePicker());
        templateRow.addView(applyRoutine,new LinearLayout.LayoutParams(0,dp(42),1));
        Button editRoutine=secondaryButton("Edit templates",()->showTemplateEditorPicker());
        LinearLayout.LayoutParams editRoutineLp=new LinearLayout.LayoutParams(0,dp(42),1);editRoutineLp.leftMargin=dp(8);
        templateRow.addView(editRoutine,editRoutineLp);
        LinearLayout.LayoutParams templateRowLp=new LinearLayout.LayoutParams(-1,dp(44));templateRowLp.topMargin=dp(10);dateCard.addView(templateRow,templateRowLp);pagePlan.addView(dateCard);

        planTemplatePreview=vertical();
        LinearLayout.LayoutParams templatePreviewLp=new LinearLayout.LayoutParams(-1,-2);templatePreviewLp.topMargin=dp(12);templatePreviewLp.bottomMargin=dp(10);
        pagePlan.addView(planTemplatePreview,templatePreviewLp);

        planDayScheduleToggle=secondaryButton("Show this date's saved schedule",()->{
            boolean expand=actualScheduleContent!=null&&actualScheduleContent.getVisibility()!=View.VISIBLE;
            setPlanDayScheduleExpanded(expand);
        });
        LinearLayout.LayoutParams toggleLp=new LinearLayout.LayoutParams(-1,dp(44));toggleLp.topMargin=dp(4);toggleLp.bottomMargin=dp(10);
        pagePlan.addView(planDayScheduleToggle,toggleLp);

        actualScheduleContent=vertical();
        actualScheduleContent.setVisibility(View.GONE);
        TextView separationHint=text("This is the actual schedule saved for this date, including its progress and date-specific edits. It is separate from the reusable template above; selecting a day type does not overwrite it.",11,color("#687A70"),false);
        separationHint.setLineSpacing(dp(2),1f);
        LinearLayout.LayoutParams separationHintLp=new LinearLayout.LayoutParams(-1,-2);separationHintLp.bottomMargin=dp(10);
        actualScheduleContent.addView(separationHint,separationHintLp);
        Button add=primaryButton("＋  Add a block to this date",()->openAddDialog());
        LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(-1,dp(48));addLp.bottomMargin=dp(15);actualScheduleContent.addView(add,addLp);
        TextView section=text("SAVED BLOCKS FOR THIS DATE",11,color("#9AABA0"),true);
        LinearLayout.LayoutParams secLp=new LinearLayout.LayoutParams(-1,-2);secLp.bottomMargin=dp(9);actualScheduleContent.addView(section,secLp);
        planList=vertical();actualScheduleContent.addView(planList);
        pagePlan.addView(actualScheduleContent);
        renderPlanPage();
    }

    private void setPlanDayScheduleExpanded(boolean expanded) {
        if (actualScheduleContent != null) actualScheduleContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (planDayScheduleToggle != null) {
            planDayScheduleToggle.setText(expanded
                    ? "Hide this date's saved schedule"
                    : "Show this date's saved schedule");
            planDayScheduleToggle.setContentDescription(expanded
                    ? "Hide the saved schedule for this date"
                    : "Show the saved schedule for this date");
        }
    }

    private void renderPlanPage() {
        if (planList == null) return;
        TextView date = (TextView) pagePlan.findViewWithTag("planDate");
        if (date != null) {
            Date d = parseDateKey(selectedDateKey);
            String dayState = selectedDateKey.equals(todayKey()) ? "TODAY"
                    : selectedDateKey.compareTo(todayKey()) < 0 ? "PAST" : "PLANNED";
            date.setText((d == null ? selectedDateKey
                    : new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(d)) + "  •  " + dayState);
        }
        for (String key : new String[]{"wfh", "office", "weekend"}) {
            View choice = pagePlan.findViewWithTag("plan-mode-chip-" + key);
            if (choice instanceof Button) {
                boolean selected = (planPreviewMode == null ? mode : planPreviewMode).equals(key);
                ((Button) choice).setTextColor(selected ? Color.WHITE : color("#496257"));
                choice.setBackground(bg(selected ? "#286B55" : "#FFFFFF", 12));
            }
        }

        renderPlanTemplatePreview();
        planList.removeAllViews();
        ArrayList<Block> ordered = new ArrayList<>(blocks);
        Collections.sort(ordered, Comparator.comparingInt(b -> mins(b.start)));
        if (ordered.isEmpty()) {
            LinearLayout empty = surfaceCard();
            empty.addView(text("A clean slate", 19, color("#18372E"), true));
            TextView hint = text("Add a block for the next thing you need to do, or apply one of your saved routines.", 12, color("#687A70"), false);
            hint.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
            hp.topMargin = dp(5);
            empty.addView(hint, hp);
            LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, dp(44));
            actionsLp.topMargin = dp(12);
            LinearLayout actions = horizontal();
            actions.addView(primaryButton("＋ Add first block", () -> openAddDialog()),
                    new LinearLayout.LayoutParams(0, -1, 1));
            Button apply = secondaryButton("Apply routine", () -> showTemplatePicker());
            LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(0, -1, 1);
            applyLp.leftMargin = dp(8);
            actions.addView(apply, applyLp);
            empty.addView(actions, actionsLp);
            planList.addView(empty);
            return;
        }

        for (Block block : ordered) {
            LinearLayout card = surfaceCard();
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout time = vertical();
            time.addView(text(time12(block.start), 12, color("#286B55"), true));
            time.addView(text(time12(block.end), 10, color("#687A70"), false));
            row.addView(time, new LinearLayout.LayoutParams(dp(66), -2));

            LinearLayout details = vertical();
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(0, -2, 1);
            detailLp.leftMargin = dp(8);
            detailLp.rightMargin = dp(6);
            row.addView(details, detailLp);
            TextView title = text(block.title, 14, color("#18372E"), true);
            title.setMaxLines(3);
            details.addView(title);
            TextView meta = text(blockBehaviorLabel(block) + "  •  " + fmt(mins(block.end) - mins(block.start)), 10, color("#687A70"), false);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
            metaLp.topMargin = dp(2);
            details.addView(meta, metaLp);

            boolean isDone = "done".equals(block.status);
            boolean isSkipped = "skipped".equals(block.status);
            TextView state = text(isDone ? "COMPLETED" : isSkipped ? "SKIPPED" : "PENDING",
                    9, isDone ? color("#2E8060") : isSkipped ? color("#B44248") : color("#687A70"), true);
            LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(-1, -2);
            stateLp.topMargin = dp(4);
            details.addView(state, stateLp);
            details.setOnClickListener(v -> editBlockDialog(block));
            title.setOnClickListener(v -> editBlockDialog(block));

            Button stateAction;
            if (isDone) {
                stateAction = secondaryButton("↶", () -> { block.status = "pending"; save(); });
                stateAction.setContentDescription("Undo completion");
            } else if (isSkipped) {
                stateAction = secondaryButton("↶", () -> { block.status = "pending"; save(); });
                stateAction.setContentDescription("Restore skipped block");
            } else {
                stateAction = primaryButton("✓", () -> {
                    stopFocusForBlock(block, System.currentTimeMillis());
                    block.status = "done";
                    save();
                });
                stateAction.setContentDescription("Mark complete");
            }
            LinearLayout.LayoutParams stateActionLp = new LinearLayout.LayoutParams(dp(38), dp(38));
            stateActionLp.leftMargin = dp(3);
            row.addView(stateAction, stateActionLp);

            Button more = secondaryButton("•••", () -> showBlockOptions(block));
            more.setContentDescription("More actions for " + block.title);
            LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(dp(36), dp(38));
            moreLp.leftMargin = dp(5);
            row.addView(more, moreLp);

            row.setOnClickListener(v -> editBlockDialog(block));
            card.addView(row);
            planList.addView(card);
        }
    }

    private void renderPlanTemplatePreview() {
        if (planTemplatePreview == null) return;
        planTemplatePreview.removeAllViews();

        LinearLayout heading = horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headingText = vertical();
        headingText.addView(text("SELECTED TEMPLATE", 10, color("#2E8060"), true));
        String previewKey = planPreviewMode == null ? mode : planPreviewMode;
        headingText.addView(text(templateLabel(previewKey) + " routine", 18, color("#18372E"), true));
        heading.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1));
        planTemplatePreview.addView(heading);

        TextView hint = text("This is the saved " + templateLabel(previewKey).toLowerCase(Locale.ROOT) + " template combined with shared blocks. Save changes in Edit templates; use Apply template to date to replace this date's saved schedule.", 11, color("#687A70"), false);
        hint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = dp(4);
        hintLp.bottomMargin = dp(8);
        planTemplatePreview.addView(hint, hintLp);

        ArrayList<Block> preview = getCombinedTemplateBlocks(previewKey);
        Collections.sort(preview, Comparator.comparingInt(b -> mins(b.start)));
        if (preview.isEmpty()) {
            LinearLayout empty = surfaceCard();
            empty.addView(text("No blocks in this template yet", 14, color("#18372E"), true));
            empty.addView(text("Use Edit templates to add shared or " + templateLabel(previewKey).toLowerCase(Locale.ROOT) + "-specific blocks.", 11, color("#687A70"), false));
            planTemplatePreview.addView(empty);
            return;
        }

        for (Block block : preview) {
            LinearLayout card = surfaceCard();
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout time = vertical();
            time.addView(text(time12(block.start), 12, color("#286B55"), true));
            time.addView(text(time12(block.end), 10, color("#687A70"), false));
            row.addView(time, new LinearLayout.LayoutParams(dp(66), -2));

            LinearLayout details = vertical();
            LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1);
            detailsLp.leftMargin = dp(8);
            row.addView(details, detailsLp);
            TextView title = text(block.title, 13, color("#18372E"), true);
            title.setMaxLines(3);
            details.addView(title);
            String source = "common".equals(block.templateOwner) ? "SHARED · LOCKED" : templateLabel(previewKey).toUpperCase(Locale.ROOT) + " · TEMPLATE";
            TextView meta = text(source + "  •  " + fmt(Math.max(0, mins(block.end) - mins(block.start))), 9, color("#687A70"), false);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
            metaLp.topMargin = dp(3);
            details.addView(meta, metaLp);
            card.addView(row);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.bottomMargin = dp(6);
            planTemplatePreview.addView(card, cardLp);
        }
    }

    private void buildProgressPage() {
        pageHeading(pageProgress, "YOUR HABITS", "Execution",
                "Actual focus time and phone use. Your roadmap sets the goals; this page records how the day went.");
        progressContent = vertical();
        pageProgress.addView(progressContent);
        renderProgressPage();
    }

    private void renderProgressPage() {
        if (progressContent == null) return;
        progressContent.removeAllViews();

        long actual = 0L;
        int plannedStudy = 0;
        long now = System.currentTimeMillis();
        for (Block block : blocks) {
            actual += getActualDuration(block, now);
            if (isStudyBlock(block.title)) plannedStudy += Math.max(0, mins(block.end) - mins(block.start));
        }

        LinearLayout focus = surfaceCard();
        focus.addView(text("FOCUS TODAY", 10, color("#6BD0A6"), true));
        focus.addView(text(fmtPreciseMillis(actual), 25, color("#F2F7F3"), true));
        focus.addView(text("Study time planned: " + fmtShort(plannedStudy)
                + "\nActual focus counts only when you start Focus.", 12, color("#9AABA0"), false));
        Button todayPlan = secondaryButton("View today's plan", () -> switchPage("plan"));
        LinearLayout.LayoutParams planLp = new LinearLayout.LayoutParams(-1, dp(40));
        planLp.topMargin = dp(10);
        focus.addView(todayPlan, planLp);
        progressContent.addView(focus);

        LinearLayout screen = surfaceCard();
        screen.addView(text("PHONE SCREEN TIME", 10, color("#D5B56E"), true));
        long todayMs = ScreenTimeTracker.getStoredMillis(this, todayKey());
        screen.addView(text(todayMs < 0 ? "Not captured yet" : ScreenTimeTracker.formatDuration(todayMs),
                23, color("#F2F7F3"), true));
        long weekMs = 0L;
        int days = 0;
        for (int i = 0; i < 7; i++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, -i);
            long stored = ScreenTimeTracker.getStoredMillis(this, keyForDate(day.getTime()));
            if (stored >= 0) { weekMs += stored; days++; }
        }
        screen.addView(text(days == 0 ? "Grant Usage access to begin a saved history."
                : "7-day average: " + ScreenTimeTracker.formatDuration(weekMs / days)
                    + " across " + days + " recorded days.", 12, color("#9AABA0"), false));
        Button history = secondaryButton("View screen-time history", () -> showScreenTimeTracker());
        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(-1, dp(40));
        historyLp.topMargin = dp(10);
        screen.addView(history, historyLp);
        progressContent.addView(screen);
    }

    private void editCareerGoal() {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(10));
        TextView helper = text("These are career planning assumptions, not app-enforced targets. Edit them as your priorities change.",
                12, color("#687A70"), false);
        helper.setLineSpacing(dp(2), 1f);
        form.addView(helper);

        TextView targetLabel = text("MONTHLY TAKE-HOME TARGET", 10, color("#687A70"), true);
        LinearLayout.LayoutParams targetLabelLp = new LinearLayout.LayoutParams(-1, -2);
        targetLabelLp.topMargin = dp(12);
        form.addView(targetLabel, targetLabelLp);
        EditText target = new EditText(this);
        target.setSingleLine(true);
        target.setHint("e.g. ₹2.5–2.75L/month");
        target.setTextColor(color("#18372E"));
        target.setHintTextColor(color("#82948A"));
        target.setPadding(dp(12), dp(5), dp(12), dp(5));
        target.setBackground(bg("#FFFFFF", 12));
        target.setText(getPrefs().getString(CAREER_TARGET_KEY, "₹2.5–2.75L/month after taxes and deductions"));
        form.addView(target, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView notesLabel = text("ROLE / CAREER CONTEXT", 10, color("#687A70"), true);
        LinearLayout.LayoutParams notesLabelLp = new LinearLayout.LayoutParams(-1, -2);
        notesLabelLp.topMargin = dp(12);
        form.addView(notesLabel, notesLabelLp);
        EditText notes = new EditText(this);
        notes.setGravity(Gravity.TOP | Gravity.START);
        notes.setMinLines(3);
        notes.setTextColor(color("#18372E"));
        notes.setHintTextColor(color("#82948A"));
        notes.setPadding(dp(12), dp(10), dp(12), dp(10));
        notes.setBackground(bg("#FFFFFF", 12));
        notes.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notes.setText(getPrefs().getString(CAREER_TARGET_NOTES_KEY,
                "Exclude bonus and ESOPs. About 6 years of experience; assess IC and EM roles on fit."));
        LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(-1, dp(118));
        notesLp.topMargin = dp(4);
        form.addView(notes, notesLp);

        showFullScreenEditor("CAREER GOAL", "Edit career goal",
                "Your goal stays saved locally and can be updated at any time.", form, () -> {
                    String value = target.getText().toString().trim();
                    if (value.isEmpty()) {
                        target.setError("Enter your target or remove the target from this field");
                        target.requestFocus();
                        return false;
                    }
                    getPrefs().edit()
                            .putString(CAREER_TARGET_KEY, value)
                            .putString(CAREER_TARGET_NOTES_KEY, notes.getText().toString().trim())
                            .apply();
                    refreshRoadmapSummary();
                    renderProgressPage();
                    return true;
                });
    }

    private void buildOfficePage() {
        pageHeading(pageOffice,"Time ledger","Office & commute","One-tap attendance, commute sessions and weekly totals. Presence is separate from timetable completion.");
        officePageContent=vertical();pageOffice.addView(officePageContent);renderOfficePage();
    }
    private void renderOfficePage() {
        if (officePageContent == null) return;
        officePageContent.removeAllViews();

        // Cumulative totals lead the page: this is the primary view, not any single day.
        LinearLayout cumulative = surfaceCard();
        cumulative.addView(text("CUMULATIVE TIME", 10, color("#2E8060"), true));
        LinearLayout periodControls = horizontal();
        String[] periodKeys = {"all", "month", "week"};
        String[] periodLabels = {"All time", "This month", "This week"};
        for (int i = 0; i < periodKeys.length; i++) {
            final String chosenPeriod = periodKeys[i];
            Button periodButton = chosenPeriod.equals(officeSummaryPeriod)
                    ? primaryButton(periodLabels[i], () -> {
                        officeSummaryPeriod = chosenPeriod;
                        renderOfficePage();
                    })
                    : secondaryButton(periodLabels[i], () -> {
                        officeSummaryPeriod = chosenPeriod;
                        renderOfficePage();
                    });
            periodButton.setTextSize(11);
            periodButton.setMinHeight(dp(36));
            LinearLayout.LayoutParams periodLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (i > 0) periodLp.leftMargin = dp(5);
            periodControls.addView(periodButton, periodLp);
        }
        LinearLayout.LayoutParams periodsLp = new LinearLayout.LayoutParams(-1, dp(38));
        periodsLp.topMargin = dp(10);
        periodsLp.bottomMargin = dp(12);
        cumulative.addView(periodControls, periodsLp);

        long[] totals = attendanceTotalsForPeriod(officeSummaryPeriod);
        long officeTotal = totals[0];
        long commuteTotal = totals[1];
        long recordedDays = totals[2];
        TextView totalTime = text(fmtMillis(officeTotal + commuteTotal), 32, color("#18372E"), true);
        cumulative.addView(totalTime);
        cumulative.addView(text("TOTAL TIME LOGGED", 10, color("#687A70"), true));
        LinearLayout breakdown = horizontal();
        LinearLayout officeMetric = vertical();
        officeMetric.addView(text("IN OFFICE", 10, color("#687A70"), true));
        officeMetric.addView(text(fmtMillis(officeTotal), 17, color("#18372E"), true));
        breakdown.addView(officeMetric, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout commuteMetric = vertical();
        commuteMetric.addView(text("COMMUTE", 10, color("#687A70"), true));
        commuteMetric.addView(text(fmtMillis(commuteTotal), 17, color("#18372E"), true));
        breakdown.addView(commuteMetric, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams breakdownLp = new LinearLayout.LayoutParams(-1, -2);
        breakdownLp.topMargin = dp(12);
        cumulative.addView(breakdown, breakdownLp);
        cumulative.addView(text("Across " + recordedDays + " recorded day" + (recordedDays == 1 ? "" : "s")
                + " · totals update when a record is saved.", 11, color("#687A70"), false));
        officePageContent.addView(cumulative);

        JSONObject todayLog = getOfficeDay(todayKey(), false);
        boolean inside = hasOpenOfficeSession(todayLog);
        boolean travelling = hasOpenCommute(todayLog);
        LinearLayout status = surfaceCard();
        status.addView(text("TODAY'S STATUS", 10, color("#2E8060"), true));
        status.addView(text(inside ? "Clocked in" : travelling ? "Commute in progress" : "Not clocked in",
                20, color("#18372E"), true));
        LinearLayout quickActions = horizontal();
        Button check = primaryButton(inside ? "Check out" : "Check in", () -> {
            toggleOfficeAttendance();
            renderOfficePage();
        });
        Button travel = secondaryButton(travelling ? "Finish commute" : "Start commute", () -> {
            toggleCommute();
            renderOfficePage();
        });
        quickActions.addView(check, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams travelActionLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        travelActionLp.leftMargin = dp(8);
        quickActions.addView(travel, travelActionLp);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(-1, dp(44));
        quickLp.topMargin = dp(10);
        status.addView(quickActions, quickLp);
        officePageContent.addView(status);

        LinearLayout history = surfaceCard();
        history.addView(text("ATTENDANCE CALENDAR", 10, color("#2E8060"), true));
        history.addView(text("Tap any past date to edit its office and commute times.", 12,
                color("#687A70"), false));

        // Keep the visible month on day one so navigation and grids remain stable.
        Calendar monthStart = (Calendar) attendanceCalendarMonth.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 12);
        monthStart.set(Calendar.MINUTE, 0);
        monthStart.set(Calendar.SECOND, 0);
        monthStart.set(Calendar.MILLISECOND, 0);
        attendanceCalendarMonth = (Calendar) monthStart.clone();

        Calendar currentMonth = Calendar.getInstance();
        currentMonth.set(Calendar.DAY_OF_MONTH, 1);
        currentMonth.set(Calendar.HOUR_OF_DAY, 12);
        currentMonth.set(Calendar.MINUTE, 0);
        currentMonth.set(Calendar.SECOND, 0);
        currentMonth.set(Calendar.MILLISECOND, 0);
        boolean isCurrentMonth = monthStart.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR)
                && monthStart.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH);

        LinearLayout monthNavigation = horizontal();
        Button previousMonth = secondaryButton("‹", () -> changeAttendanceCalendarMonth(-1));
        previousMonth.setTextSize(21);
        previousMonth.setContentDescription("Previous month");
        monthNavigation.addView(previousMonth, new LinearLayout.LayoutParams(dp(44), dp(40)));
        TextView monthLabel = text(new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(monthStart.getTime()), 16, color("#18372E"), true);
        monthLabel.setGravity(Gravity.CENTER);
        monthNavigation.addView(monthLabel, new LinearLayout.LayoutParams(0, dp(40), 1f));
        Button nextMonth = secondaryButton("›", () -> changeAttendanceCalendarMonth(1));
        nextMonth.setTextSize(21);
        nextMonth.setContentDescription("Next month");
        nextMonth.setEnabled(!isCurrentMonth);
        if (isCurrentMonth) nextMonth.setAlpha(0.35f);
        monthNavigation.addView(nextMonth, new LinearLayout.LayoutParams(dp(44), dp(40)));
        LinearLayout.LayoutParams monthNavigationLp = new LinearLayout.LayoutParams(-1, dp(40));
        monthNavigationLp.topMargin = dp(10);
        monthNavigationLp.bottomMargin = dp(8);
        history.addView(monthNavigation, monthNavigationLp);

        LinearLayout weekdayHeader = horizontal();
        String[] weekdays = {"S", "M", "T", "W", "T", "F", "S"};
        for (String weekday : weekdays) {
            TextView label = text(weekday, 11, color("#687A70"), true);
            label.setGravity(Gravity.CENTER);
            weekdayHeader.addView(label, new LinearLayout.LayoutParams(0, dp(26), 1f));
        }
        history.addView(weekdayHeader);

        LinearLayout calendarGrid = vertical();
        Calendar firstDay = (Calendar) monthStart.clone();
        int firstOffset = firstDay.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH);
        int slotCount = ((firstOffset + daysInMonth + 6) / 7) * 7;
        String todayKey = todayKey();
        for (int rowIndex = 0; rowIndex < slotCount / 7; rowIndex++) {
            LinearLayout weekRow = horizontal();
            for (int column = 0; column < 7; column++) {
                int slot = rowIndex * 7 + column;
                int dayNumber = slot - firstOffset + 1;
                if (dayNumber < 1 || dayNumber > daysInMonth) {
                    View blank = new View(this);
                    weekRow.addView(blank, new LinearLayout.LayoutParams(0, dp(43), 1f));
                    continue;
                }

                Calendar dayCalendar = (Calendar) monthStart.clone();
                dayCalendar.set(Calendar.DAY_OF_MONTH, dayNumber);
                String dayKey = keyForDate(dayCalendar.getTime());
                JSONObject record = getOfficeDay(dayKey, false);
                boolean recorded = OfficeAttendanceTime.hasRecordedData(record);
                boolean isToday = dayKey.equals(todayKey);
                boolean futureDate = dayKey.compareTo(todayKey) > 0;

                LinearLayout cell = vertical();
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(1), dp(1), dp(1), dp(1));
                cell.setBackground(bg(recorded ? "#DCEBE2" : "#FFFFFF", 9));
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, dp(43), 1f);
                cellLp.setMargins(dp(1), dp(1), dp(1), dp(1));
                weekRow.addView(cell, cellLp);

                TextView dayNumberView = text(String.valueOf(dayNumber), 13,
                        futureDate ? color("#A8BAAE") : recorded ? color("#18372E")
                                : isToday ? color("#2E8060") : color("#50635A"),
                        recorded || isToday);
                dayNumberView.setGravity(Gravity.CENTER);
                cell.addView(dayNumberView, new LinearLayout.LayoutParams(-1, dp(24)));
                TextView marker = text(recorded ? "●" : isToday ? "○" : " ",
                        7, color(recorded ? "#23845F" : "#82948A"), true);
                marker.setGravity(Gravity.CENTER);
                cell.addView(marker, new LinearLayout.LayoutParams(-1, dp(10)));
                cell.setContentDescription(new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
                        .format(dayCalendar.getTime()) + (recorded ? ", attendance or commute recorded" : ", no record")
                        + (futureDate ? "" : ", tap to edit"));
                cell.setEnabled(!futureDate);
                if (futureDate) cell.setAlpha(0.45f);
                else cell.setOnClickListener(v -> editAttendanceDay(dayKey));
            }
            calendarGrid.addView(weekRow);
        }
        history.addView(calendarGrid);
        LinearLayout legend = horizontal();
        TextView recordedLegend = text("●  Record saved", 10, color("#23845F"), true);
        legend.addView(recordedLegend, new LinearLayout.LayoutParams(0, dp(25), 1f));
        legend.addView(text("○  Today", 10, color("#687A70"), false),
                new LinearLayout.LayoutParams(0, dp(25), 1f));
        LinearLayout.LayoutParams legendLp = new LinearLayout.LayoutParams(-1, dp(25));
        legendLp.topMargin = dp(3);
        history.addView(legend, legendLp);
        officePageContent.addView(history);
    }

    private void changeAttendanceCalendarMonth(int offset) {
        Calendar candidate = (Calendar) attendanceCalendarMonth.clone();
        candidate.add(Calendar.MONTH, offset);
        candidate.set(Calendar.DAY_OF_MONTH, 1);
        Calendar current = Calendar.getInstance();
        current.set(Calendar.DAY_OF_MONTH, 1);
        if (candidate.after(current)) return;
        attendanceCalendarMonth = candidate;
        renderOfficePage();
    }

    private long[] attendanceTotalsForPeriod(String period) {
        String today = todayKey();
        String start = "0000-01-01";
        if ("month".equals(period)) {
            start = today.substring(0, 7) + "-01";
        } else if ("week".equals(period)) {
            Calendar monday = Calendar.getInstance();
            monday.set(Calendar.HOUR_OF_DAY, 12);
            monday.set(Calendar.MINUTE, 0);
            monday.set(Calendar.SECOND, 0);
            monday.set(Calendar.MILLISECOND, 0);
            int dow = monday.get(Calendar.DAY_OF_WEEK);
            monday.add(Calendar.DAY_OF_YEAR, -(dow == Calendar.SUNDAY ? 6 : dow - Calendar.MONDAY));
            start = keyForDate(monday.getTime());
        }
        try {
            JSONArray records = new JSONArray(getPrefs().getString(OFFICE_LOG_KEY, "[]"));
            return OfficeAttendanceTime.totalsForDateRange(
                    records, start, today, today, System.currentTimeMillis());
        } catch (Exception e) {
            debugLog("Cumulative attendance totals failed: " + e.getMessage());
            return new long[]{0L, 0L, 0L};
        }
    }

    private void buildMorePage() {
        pageHeading(pageMore,"Tools & preferences","More","Routines, data management and less-frequent settings.");
        moreContent=vertical();pageMore.addView(moreContent);renderMorePage();
    }
    private void addMoreRow(String title,String subtitle,String symbol,Runnable action) {
        LinearLayout row=horizontal();row.setPadding(dp(14),dp(12),dp(10),dp(12));row.setBackground(bg("#18231E",16));
        LinearLayout details=vertical();details.addView(text(title,14,color("#F2F7F3"),true));TextView sub=text(subtitle,11,color("#9AABA0"),false);sub.setPadding(0,dp(3),0,0);details.addView(sub);
        row.addView(details,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text(symbol,18,color("#65C5A0"),true);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(32),dp(36)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);moreContent.addView(row,lp);row.setOnClickListener(v->action.run());
    }
    private void renderMorePage() {
        if(moreContent==null)return;moreContent.removeAllViews();
        addMoreRow("Focus & screen time","Actual focus sessions and phone-use history","↗",()->switchPage("progress"));
        addMoreRow("Plans & Documents","Read your study and career plans offline","↗",()->startActivity(new Intent(MainActivity.this, PlanDocumentsActivity.class)));
        addMoreRow("Edit shared routine","Blocks inherited by WFH, office and weekend days","↗",()->openTemplateEditor("common"));
        addMoreRow("Edit WFH routine","Change WFH-specific blocks alongside the shared routine","↗",()->openTemplateEditor("wfh"));
        addMoreRow("Edit office routine","Change office-specific blocks alongside the shared routine","↗",()->openTemplateEditor("office"));
        addMoreRow("Edit weekend routine","Change weekend-specific blocks alongside the shared routine","↗",()->openTemplateEditor("weekend"));
        addMoreRow("Precise widget timing","Schedule-boundary alarm setting","↗",()->enablePreciseLiveWidgetUpdates());
        addMoreRow("Attendance history","Review and correct office hours","↗",()->showAttendanceHistory());
        addMoreRow("Export backup","Save plans, attendance, syllabus and screen-time history","↗",()->requestExportBackup());
        addMoreRow("Import backup","Restore a previously exported DayFlow backup","↗",()->requestImportBackup());
        addMoreRow("Check for updates","Find and install the latest DayFlow version","↻",()->checkForAppUpdate(true));
        addMoreRow("Diagnostics","Inspect app and widget event logs","↗",()->showDiagnostics());
        addMoreRow("Clear selected plan","Remove blocks from the selected date","↗",()->clearDay());
    }


    private long installedVersionCode() {
        try {
            android.content.pm.PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return 0L;
        }
    }

    private String installedVersionName() {
        try {
            android.content.pm.PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName == null ? "unknown" : p.versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private boolean canRequestPackageInstalls() {
        return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
    }

    /** Queries a stable GitHub Release manifest; Android validates the downloaded package signature. */
    private void checkForAppUpdate(boolean manual) {
        if (updateCheckRunning) {
            if (manual) Toast.makeText(this, "Already checking for updates…", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        if (!manual && now - getPrefs().getLong(UPDATE_LAST_CHECK_KEY, 0L) < UPDATE_CHECK_INTERVAL_MS) return;
        updateCheckRunning = true;

        new Thread(() -> {
            DayFlowUpdateInfo.ReleaseInfo latest = null;
            String failure = null;
            boolean channelMissing = false;
            java.net.HttpURLConnection connection = null;
            try {
                connection = (java.net.HttpURLConnection) new java.net.URL(UPDATE_METADATA_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "DayFlow-Android");
                connection.setRequestProperty("Accept", "application/json");
                int response = connection.getResponseCode();
                if (response == 404) {
                    channelMissing = true;
                } else if (response < 200 || response >= 300) {
                    throw new java.io.IOException("Update server returned HTTP " + response);
                } else {
                    java.io.InputStream input = connection.getInputStream();
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    input.close();
                    latest = DayFlowUpdateInfo.parse(bytes.toString("UTF-8"));
                }
            } catch (Exception e) {
                failure = e.getMessage() == null ? "Network request failed" : e.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }

            final DayFlowUpdateInfo.ReleaseInfo result = latest;
            final String error = failure;
            final boolean missing = channelMissing;
            runOnUiThread(() -> {
                updateCheckRunning = false;
                getPrefs().edit().putLong(UPDATE_LAST_CHECK_KEY, System.currentTimeMillis()).apply();
                if (result == null) {
                    if (manual) {
                        String message = missing
                                ? "The in-app update channel is not published yet. A persistent release-signing key must be configured in GitHub Actions before signed updates can be published."
                                : "DayFlow couldn't check for updates. Check your connection and try again.\n\n" + (error == null ? "" : error);
                        new AlertDialog.Builder(this)
                                .setTitle(missing ? "Updates not set up yet" : "Couldn't check for updates")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                    return;
                }

                long current = installedVersionCode();
                if (DayFlowUpdateInfo.isNewerThan(current, result)) {
                    String prompted = getPrefs().getString(UPDATE_PROMPTED_VERSION_KEY, "");
                    if (manual || !String.valueOf(result.versionCode).equals(prompted)) {
                        getPrefs().edit().putString(UPDATE_PROMPTED_VERSION_KEY, String.valueOf(result.versionCode)).apply();
                        showAvailableUpdate(result);
                    }
                } else if (manual) {
                    new AlertDialog.Builder(this)
                            .setTitle("You're up to date")
                            .setMessage("DayFlow " + installedVersionName() + " is the latest published version.")
                            .setPositiveButton("Done", null)
                            .show();
                }
            });
        }, "DayFlow-update-check").start();
    }

    private void showAvailableUpdate(DayFlowUpdateInfo.ReleaseInfo release) {
        String notes = release.releaseNotes.isEmpty() ? "Bug fixes and improvements." : release.releaseNotes;
        new AlertDialog.Builder(this)
                .setTitle("DayFlow update available")
                .setMessage("Installed: " + installedVersionName()
                        + "\nAvailable: " + release.versionName
                        + "\n\n" + notes
                        + "\n\nFor a normal in-place update, Android must recognize the same signing identity. Android will ask you to approve installation.")
                .setPositiveButton("Download & install", (dialog, which) -> downloadAndInstallUpdate(release))
                .setNegativeButton("Later", null)
                .show();
    }

    private void downloadAndInstallUpdate(DayFlowUpdateInfo.ReleaseInfo release) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updatePendingAfterNotificationPermission = release;
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    UPDATE_NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        enqueueBackgroundUpdateDownload(release);
    }

    private void enqueueBackgroundUpdateDownload(DayFlowUpdateInfo.ReleaseInfo release) {
        getPrefs().edit()
                .remove(UPDATE_DOWNLOAD_ERROR_KEY)
                .putBoolean(UPDATE_INSTALL_PERMISSION_PROMPTED_KEY, false)
                .apply();

        Data input = new Data.Builder()
                .putLong(DayFlowUpdateDownloadWorker.INPUT_VERSION_CODE, release.versionCode)
                .putString(DayFlowUpdateDownloadWorker.INPUT_VERSION_NAME, release.versionName)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(DayFlowUpdateDownloadWorker.class)
                .setInputData(input)
                .addTag(DayFlowUpdateDownloadWorker.WORK_TAG)
                .build();

        WorkManager workManager = WorkManager.getInstance(getApplicationContext());
        workManager.enqueueUniqueWork(DayFlowUpdateDownloadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE, request);
        if (updateDownloadLiveData != null && updateDownloadObserver != null) {
            updateDownloadLiveData.removeObserver(updateDownloadObserver);
        }
        updateDownloadLiveData = workManager.getWorkInfoByIdLiveData(request.getId());
        updateDownloadObserver = info -> {
            if (info != null && info.getState().isFinished()) {
                androidx.lifecycle.LiveData<WorkInfo> observedLiveData = updateDownloadLiveData;
                androidx.lifecycle.Observer<WorkInfo> observer = updateDownloadObserver;
                if (observedLiveData != null && observer != null) {
                    observedLiveData.removeObserver(observer);
                    updateDownloadLiveData = null;
                    updateDownloadObserver = null;
                }
                handler.post(this::handlePendingUpdateOnResume);
            }
        };
        updateDownloadLiveData.observeForever(updateDownloadObserver);
        Toast.makeText(this,
                "The update is downloading in the background. DayFlow will notify you when it's ready.",
                Toast.LENGTH_LONG).show();
    }

    private void handlePendingUpdateOnResume() {
        String error = getPrefs().getString(UPDATE_DOWNLOAD_ERROR_KEY, "");
        if (error != null && !error.trim().isEmpty()) {
            getPrefs().edit().remove(UPDATE_DOWNLOAD_ERROR_KEY).apply();
            if (error.toLowerCase(Locale.US).contains("signing key")) {
                showSigningMigrationHelp();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Update download failed")
                        .setMessage(error + "\n\nTry again from More → Check for updates.")
                        .setPositiveButton("OK", null)
                        .show();
            }
            return;
        }

        String pendingPath = getPrefs().getString(PENDING_UPDATE_APK_KEY, "");
        if (pendingPath == null || pendingPath.trim().isEmpty()) return;
        if (canRequestPackageInstalls()) {
            maybeInstallPendingUpdate();
            return;
        }
        if (getPrefs().getBoolean(UPDATE_INSTALL_PERMISSION_PROMPTED_KEY, false)) return;
        getPrefs().edit().putBoolean(UPDATE_INSTALL_PERMISSION_PROMPTED_KEY, true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Allow DayFlow to install updates")
                .setMessage("Android requires a one-time permission for apps installed outside the Play Store. Allow installs from DayFlow on the next screen, then return here to finish installation.")
                .setPositiveButton("Open settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        Toast.makeText(this, "Open Android Settings and allow installs from DayFlow.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    private boolean apkMatchesInstalledSigner(String apkPath, int packageFlags) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo installed = pm.getPackageInfo(getPackageName(), packageFlags);
            android.content.pm.PackageInfo candidate = pm.getPackageArchiveInfo(apkPath, packageFlags);
            if (candidate == null || !getPackageName().equals(candidate.packageName)) return false;
            android.content.pm.Signature[] installedSigners;
            android.content.pm.Signature[] candidateSigners;
            if (Build.VERSION.SDK_INT >= 28) {
                if (installed.signingInfo == null || candidate.signingInfo == null) return false;
                installedSigners = installed.signingInfo.getApkContentsSigners();
                candidateSigners = candidate.signingInfo.getApkContentsSigners();
            } else {
                installedSigners = installed.signatures;
                candidateSigners = candidate.signatures;
            }
            if (installedSigners == null || candidateSigners == null || installedSigners.length == 0
                    || candidateSigners.length == 0 || installedSigners.length != candidateSigners.length) return false;
            for (android.content.pm.Signature current : installedSigners) {
                boolean found = false;
                for (android.content.pm.Signature incoming : candidateSigners) {
                    if (current.equals(incoming)) { found = true; break; }
                }
                if (!found) return false;
            }
            return true;
        } catch (Exception e) {
            debugLog("Update signature preflight failed: " + e.getMessage());
            return false;
        }
    }

    private void showSigningMigrationHelp() {
        new AlertDialog.Builder(this)
                .setTitle("One-time update setup needed")
                .setMessage("This APK is signed with a different key, so Android will not install it over the current app. This can happen when moving from temporary CI debug builds to a permanent release key.\n\n1. Export a backup from More → Export backup.\n2. Download the stable APK.\n3. Uninstall the current DayFlow, install the downloaded APK, then use More → Import backup.\n\nAfter this one-time move, future releases signed with the same key can install as normal updates.")
                .setPositiveButton("Export backup", (d, w) -> requestExportBackup())
                .setNeutralButton("Download stable APK", (d, w) -> openStableApkDownload())
                .setNegativeButton("Later", null).show();
    }

    private void openStableApkDownload() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_APK_URL)));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open the APK download link.", Toast.LENGTH_LONG).show();
        }
    }

    private void maybeInstallPendingUpdate() {
        String path = getPrefs().getString(PENDING_UPDATE_APK_KEY, "");
        if (path.isEmpty() || !canRequestPackageInstalls()) return;
        java.io.File apk = new java.io.File(path);
        if (!apk.exists() || apk.length() == 0L) {
            getPrefs().edit().remove(PENDING_UPDATE_APK_KEY).apply();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent installer = new Intent(Intent.ACTION_VIEW);
            installer.setDataAndType(uri, "application/vnd.android.package-archive");
            installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getPrefs().edit().remove(PENDING_UPDATE_APK_KEY).apply();
            startActivity(installer);
        } catch (Exception e) {
            debugLog("Could not open Android package installer: " + e.getMessage());
            Toast.makeText(this, "Couldn't open Android's installer. Download the update again.", Toast.LENGTH_LONG).show();
        }
    }

    private void buildSyllabusPage() {
        if (selectedGoalMonth == null) selectedGoalMonth = monthKeyFromDate(todayKey());
        pageHeading(pageSyllabus, "YOUR PLAN", "Roadmap",
                "One connected map for syllabus, monthly goals and deadlines.");

        // Compact execution panel: one shared source of truth with the full roadmap tree.
        roadmapMonthlyGoalsCollapsed = getPrefs().getBoolean(ROADMAP_MONTHLY_PANEL_COLLAPSED_KEY, false);
        LinearLayout monthlyCard = vertical();
        monthlyCard.setPadding(dp(14), dp(11), dp(14), dp(10));
        monthlyCard.setBackground(bg("#FFFFFF", 16));
        LinearLayout.LayoutParams monthlyCardLp = new LinearLayout.LayoutParams(-1, -2);
        monthlyCardLp.topMargin = dp(8);
        monthlyCardLp.bottomMargin = dp(3);

        LinearLayout monthlyHeader = horizontal();
        monthlyHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout monthlyHeaderText = vertical();
        monthlyHeaderText.addView(text("MONTHLY GOALS", 12, color("#2E8060"), true));
        roadmapFocusCount = text("", 10, color("#687A70"), false);
        roadmapFocusCount.setSingleLine(true);
        roadmapFocusCount.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams focusCountLp = new LinearLayout.LayoutParams(-1, -2);
        focusCountLp.topMargin = dp(2);
        monthlyHeaderText.addView(roadmapFocusCount, focusCountLp);
        monthlyHeader.addView(monthlyHeaderText, new LinearLayout.LayoutParams(0, -2, 1f));


        roadmapMonthlyGoalsToggle = secondaryButton(roadmapMonthlyGoalsCollapsed ? "›" : "⌄",
                () -> setRoadmapMonthlyGoalsCollapsed(!roadmapMonthlyGoalsCollapsed));
        roadmapMonthlyGoalsToggle.setTextSize(18);
        roadmapMonthlyGoalsToggle.setContentDescription(roadmapMonthlyGoalsCollapsed
                ? "Expand monthly goals" : "Collapse monthly goals");
        LinearLayout.LayoutParams monthlyToggleLp = new LinearLayout.LayoutParams(dp(34), dp(36));
        monthlyToggleLp.leftMargin = dp(5);
        monthlyHeader.addView(roadmapMonthlyGoalsToggle, monthlyToggleLp);
        monthlyCard.addView(monthlyHeader);

        roadmapMonthlyGoalsContent = vertical();
        LinearLayout monthNav = horizontal();
        monthNav.setGravity(Gravity.CENTER_VERTICAL);
        monthNav.setPadding(0, dp(7), 0, dp(4));
        monthNav.addView(secondaryButton("‹", () -> changeSelectedGoalMonth(-1)),
                new LinearLayout.LayoutParams(dp(36), dp(36)));
        roadmapFocusMonthLabel = text("", 12, color("#18372E"), true);
        roadmapFocusMonthLabel.setGravity(Gravity.CENTER);
        roadmapFocusMonthLabel.setBackground(bg("#F3F7F3", 9));
        roadmapFocusMonthLabel.setPadding(dp(8), 0, dp(8), 0);
        roadmapFocusMonthLabel.setOnClickListener(v -> chooseGoalMonth());
        roadmapFocusMonthLabel.setContentDescription("Choose roadmap goal month");
        LinearLayout.LayoutParams monthLabelLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        monthLabelLp.leftMargin = dp(8);
        monthLabelLp.rightMargin = dp(8);
        monthNav.addView(roadmapFocusMonthLabel, monthLabelLp);
        monthNav.addView(secondaryButton("›", () -> changeSelectedGoalMonth(1)),
                new LinearLayout.LayoutParams(dp(36), dp(36)));
        roadmapMonthlyGoalsContent.addView(monthNav);

        roadmapOverdueReviewBanner = horizontal();
        roadmapOverdueReviewBanner.setGravity(Gravity.CENTER_VERTICAL);
        roadmapOverdueReviewBanner.setPadding(dp(9), dp(7), dp(7), dp(7));
        roadmapOverdueReviewBanner.setBackground(bg("#FFF4E5", 10));
        roadmapOverdueReviewText = text("", 11, color("#81531D"), true);
        roadmapOverdueReviewText.setMaxLines(2);
        LinearLayout.LayoutParams overdueTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        overdueTextLp.rightMargin = dp(6);
        roadmapOverdueReviewBanner.addView(roadmapOverdueReviewText, overdueTextLp);
        Button reviewOverdue = secondaryButton("Review", () -> showUnfinishedGoalReview());
        reviewOverdue.setTextSize(10);
        roadmapOverdueReviewBanner.addView(reviewOverdue, new LinearLayout.LayoutParams(dp(66), dp(32)));
        roadmapOverdueReviewBanner.setVisibility(View.GONE);
        LinearLayout.LayoutParams overdueBannerLp = new LinearLayout.LayoutParams(-1, -2);
        overdueBannerLp.topMargin = dp(5);
        overdueBannerLp.bottomMargin = dp(4);
        roadmapMonthlyGoalsContent.addView(roadmapOverdueReviewBanner, overdueBannerLp);

        roadmapFocusGoalList = vertical();
        roadmapMonthlyGoalsContent.addView(roadmapFocusGoalList,
                new LinearLayout.LayoutParams(-1, -2));
        monthlyCard.addView(roadmapMonthlyGoalsContent);
        roadmapMonthlyGoalsContent.setVisibility(roadmapMonthlyGoalsCollapsed ? View.GONE : View.VISIBLE);
        pageSyllabus.addView(monthlyCard, monthlyCardLp);


        LinearLayout treeHeading = horizontal();
        treeHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView treeHeadingLabel = text("ROADMAP TREE", 14, color("#18372E"), true);
        treeHeading.addView(treeHeadingLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        Button addRoadmapItem = secondaryButton("+ Add item", () -> showRoadmapAddMenu());
        addRoadmapItem.setTextSize(10);
        treeHeading.addView(addRoadmapItem, new LinearLayout.LayoutParams(dp(92), dp(36)));

        roadmapTreeEditButton = secondaryButton("Edit", () -> toggleRoadmapTreeEditMode());
        roadmapTreeEditButton.setTextSize(10);
        roadmapTreeEditButton.setContentDescription("Edit roadmap tree order with drag and drop");
        LinearLayout.LayoutParams treeEditLp = new LinearLayout.LayoutParams(dp(56), dp(36));
        treeEditLp.leftMargin = dp(5);
        treeHeading.addView(roadmapTreeEditButton, treeEditLp);

        roadmapSearchToggle = new ImageButton(this);
        roadmapSearchToggle.setImageResource(android.R.drawable.ic_menu_search);
        roadmapSearchToggle.setColorFilter(color("#245B45"));
        roadmapSearchToggle.setBackground(bg("#FFFFFF", 10));
        roadmapSearchToggle.setPadding(dp(5), dp(5), dp(5), dp(5));
        roadmapSearchToggle.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        roadmapSearchToggle.setContentDescription("Search roadmap");
        roadmapSearchToggle.setOnClickListener(v -> setRoadmapSearchExpanded(true));
        LinearLayout.LayoutParams searchToggleLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        searchToggleLp.leftMargin = dp(6);
        treeHeading.addView(roadmapSearchToggle, searchToggleLp);
        LinearLayout.LayoutParams treeHeadingLp = new LinearLayout.LayoutParams(-1, -2);
        treeHeadingLp.topMargin = dp(12);
        pageSyllabus.addView(treeHeading, treeHeadingLp);

        roadmapTreeSummary = text("", 11, color("#687A70"), false);
        roadmapTreeSummary.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams treeSummaryLp = new LinearLayout.LayoutParams(-1, -2);
        treeSummaryLp.topMargin = dp(5);
        treeSummaryLp.bottomMargin = dp(8);
        pageSyllabus.addView(roadmapTreeSummary, treeSummaryLp);

        roadmapSearchRow = horizontal();
        roadmapSearchRow.setGravity(Gravity.CENTER_VERTICAL);
        roadmapSearchInput = new EditText(this);
        roadmapSearchInput.setSingleLine(true);
        roadmapSearchInput.setTextColor(color("#18372E"));
        roadmapSearchInput.setHintTextColor(color("#82948A"));
        roadmapSearchInput.setHint("Search goals, topics or milestones");
        roadmapSearchInput.setPadding(dp(12), 0, dp(12), 0);
        roadmapSearchInput.setBackground(bg("#FFFFFF", 11));
        roadmapSearchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        roadmapSearchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        roadmapSearchRow.addView(roadmapSearchInput,
                new LinearLayout.LayoutParams(0, dp(40), 1f));
        Button closeSearch = secondaryButton("×", () -> setRoadmapSearchExpanded(false));
        closeSearch.setContentDescription("Close roadmap search");
        LinearLayout.LayoutParams closeSearchLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        closeSearchLp.leftMargin = dp(6);
        roadmapSearchRow.addView(closeSearch, closeSearchLp);
        roadmapSearchRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams searchRowLp = new LinearLayout.LayoutParams(-1, -2);
        searchRowLp.bottomMargin = dp(7);
        pageSyllabus.addView(roadmapSearchRow, searchRowLp);
        roadmapSearchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                roadmapSearchText = s == null ? "" : s.toString().trim();
                renderRoadmapTreeBody();
            }
            public void afterTextChanged(Editable e) {}
        });

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setFillViewport(false);
        roadmapFilterChips = horizontal();
        String[] filters = {"All", "Open", "Done", "Overdue"};
        for (String filter : filters) {
            Button chip = secondaryButton(filter, () -> {
                roadmapActiveFilter = filter;
                refreshRoadmapFilterStyles();
                renderRoadmapTreeBody();
            });
            chip.setMinHeight(dp(32));
            chip.setTextSize(11);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, dp(34));
            chipLp.rightMargin = dp(6);
            roadmapFilterChips.addView(chip, chipLp);
            roadmapFilterButtons.put(filter, chip);
        }
        filterScroll.addView(roadmapFilterChips);
        LinearLayout.LayoutParams filterScrollLp = new LinearLayout.LayoutParams(-1, dp(36));
        filterScrollLp.bottomMargin = dp(8);
        pageSyllabus.addView(filterScroll, filterScrollLp);

        roadmapTreeContainer = vertical();
        pageSyllabus.addView(roadmapTreeContainer, new LinearLayout.LayoutParams(-1, -2));
        refreshRoadmapFilterStyles();
        refreshRoadmapTabContent();
    }

    private void setRoadmapMonthlyGoalsCollapsed(boolean collapsed) {
        roadmapMonthlyGoalsCollapsed = collapsed;
        getPrefs().edit().putBoolean(ROADMAP_MONTHLY_PANEL_COLLAPSED_KEY, collapsed).apply();
        if (roadmapMonthlyGoalsContent != null) {
            roadmapMonthlyGoalsContent.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        if (roadmapMonthlyGoalsToggle != null) {
            roadmapMonthlyGoalsToggle.setText(collapsed ? "›" : "⌄");
            roadmapMonthlyGoalsToggle.setContentDescription(collapsed
                    ? "Expand monthly goals" : "Collapse monthly goals");
        }
    }

    private void setRoadmapSearchExpanded(boolean expanded) {
        if (roadmapSearchRow == null || roadmapSearchToggle == null || roadmapSearchInput == null) return;
        roadmapSearchRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        roadmapSearchToggle.setVisibility(expanded ? View.GONE : View.VISIBLE);
        if (expanded) {
            roadmapSearchInput.requestFocus();
            roadmapSearchInput.post(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(roadmapSearchInput,
                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
        } else {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(roadmapSearchInput.getWindowToken(), 0);
            }
            roadmapSearchInput.setText("");
            roadmapSearchInput.clearFocus();
        }
    }

    private void toggleRoadmapTreeEditMode() {
        roadmapTreeEditMode = !roadmapTreeEditMode;
        if (roadmapTreeEditMode) {
            // Tree edits should never be made against a filtered or searched subset.
            roadmapActiveFilter = "All";
            roadmapSearchText = "";
            setRoadmapSearchExpanded(false);
            refreshRoadmapFilterStyles();
        }
        if (roadmapTreeEditButton != null) {
            roadmapTreeEditButton.setText(roadmapTreeEditMode ? "Done" : "Edit");
            roadmapTreeEditButton.setContentDescription(roadmapTreeEditMode
                    ? "Finish editing roadmap tree"
                    : "Edit roadmap tree order with drag and drop");
        }
        renderRoadmapTreeBody();
        Toast.makeText(this, roadmapTreeEditMode
                ? "Drag the handle beside a goal onto another goal. Track, parent and completion rules are enforced."
                : "Roadmap tree editing finished.", Toast.LENGTH_SHORT).show();
    }

    private void addSectionDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(color("#26372D"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(10);
        lp.bottomMargin = dp(16);
        parent.addView(divider, lp);
    }

    private void buildMonthlyGoalsSection() {
        LinearLayout heading = horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("THIS MONTH", 15, color("#F2F7F3"), true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        monthlyGoalsSection.addView(heading);

        LinearLayout monthNav = horizontal();
        monthNav.setGravity(Gravity.CENTER_VERTICAL);
        monthNav.setPadding(0, dp(8), 0, dp(8));
        monthNav.addView(secondaryButton("‹", () -> changeSelectedGoalMonth(-1)),
                new LinearLayout.LayoutParams(dp(42), dp(38)));
        goalMonthLabel = text(monthLabelForKey(selectedGoalMonth), 14, color("#D6E4DA"), true);
        goalMonthLabel.setGravity(Gravity.CENTER);
        goalMonthLabel.setMaxLines(1);
        goalMonthLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        goalMonthLabel.setContentDescription("Choose goal month");
        goalMonthLabel.setOnClickListener(v -> chooseGoalMonth());
        goalMonthLabel.setTooltipText("Tap to choose a month");
        LinearLayout.LayoutParams monthLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        monthLp.leftMargin = dp(6);
        monthLp.rightMargin = dp(6);
        monthNav.addView(goalMonthLabel, monthLp);
        monthNav.addView(secondaryButton("›", () -> changeSelectedGoalMonth(1)),
                new LinearLayout.LayoutParams(dp(42), dp(38)));
        monthlyGoalsSection.addView(monthNav);

        monthlyGoalList = vertical();
        monthlyGoalsSection.addView(monthlyGoalList, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildMilestonesSection() {
        LinearLayout heading = horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("UPCOMING MILESTONES", 15, color("#F2F7F3"), true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button addMilestone = secondaryButton("+ Add", () -> editRoadmapMilestone(null));
        addMilestone.setMinHeight(dp(36));
        heading.addView(addMilestone, new LinearLayout.LayoutParams(dp(88), dp(36)));
        milestonesSection.addView(heading);
        milestoneList = vertical();
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, -2);
        listLp.topMargin = dp(5);
        milestonesSection.addView(milestoneList, listLp);

        careerGoalSummary = text("", 11, color("#9AABA0"), false);
        careerGoalSummary.setPadding(dp(2), dp(9), dp(2), dp(5));
        careerGoalSummary.setContentDescription("Edit career goal");
        careerGoalSummary.setOnClickListener(v -> editCareerGoal());
        milestonesSection.addView(careerGoalSummary);
    }

    private void buildSyllabusTopicsSection() {
        LinearLayout heading = horizontal();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("SYLLABUS COVERAGE", 15, color("#F2F7F3"), true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button open = secondaryButton("Open checklist", () -> {
            syllabusDetailsExpanded = !syllabusDetailsExpanded;
            if (syllabusDetailContent != null) {
                syllabusDetailContent.setVisibility(syllabusDetailsExpanded ? View.VISIBLE : View.GONE);
            }
            if (syllabusProgressToggle != null) {
                syllabusProgressToggle.setText(syllabusDetailsExpanded ? "Close checklist" : "Open checklist");
            }
            if (syllabusDetailsExpanded) refreshSyllabusRows();
        });
        open.setMinHeight(dp(36));
        syllabusProgressToggle = open;
        heading.addView(open, new LinearLayout.LayoutParams(dp(122), dp(36)));
        syllabusTopicsSection.addView(heading);

        syllabusCoverageSummary = text("", 12, color("#9AABA0"), false);
        syllabusCoverageSummary.setPadding(dp(2), dp(7), dp(2), dp(9));
        syllabusCoverageSummary.setLineSpacing(dp(2), 1f);
        syllabusTopicsSection.addView(syllabusCoverageSummary);

        syllabusDetailContent = vertical();
        syllabusDetailContent.setVisibility(syllabusDetailsExpanded ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(4);
        syllabusTopicsSection.addView(syllabusDetailContent, detailLp);

        LinearLayout topicActions = horizontal();
        topicActions.addView(primaryButton("+ Topic", () -> editSyllabusTopic(null)),
                new LinearLayout.LayoutParams(0, dp(40), 1f));
        Button imp = secondaryButton("Import list", () -> importSyllabusTopics());
        LinearLayout.LayoutParams impLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        impLp.leftMargin = dp(7);
        topicActions.addView(imp, impLp);
        LinearLayout.LayoutParams topicActionsLp = new LinearLayout.LayoutParams(-1, dp(40));
        topicActionsLp.bottomMargin = dp(8);
        syllabusDetailContent.addView(topicActions, topicActionsLp);

        syllabusSearch = new EditText(this);
        syllabusSearch.setSingleLine(true);
        syllabusSearch.setTextColor(color("#F2F7F3"));
        syllabusSearch.setHintTextColor(color("#82948A"));
        syllabusSearch.setHint("Search syllabus topics");
        syllabusSearch.setText(syllabusQuery);
        syllabusSearch.setPadding(dp(12), 0, dp(12), 0);
        syllabusSearch.setBackground(bg("#18231E", 11));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(42));
        searchLp.bottomMargin = dp(8);
        syllabusDetailContent.addView(syllabusSearch, searchLp);
        syllabusSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                syllabusQuery = s.toString();
                refreshSyllabusRows();
            }
            public void afterTextChanged(Editable e) {}
        });

        HorizontalScrollView fs = new HorizontalScrollView(this);
        fs.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = horizontal();
        ArrayList<String> subjects = new ArrayList<>();
        subjects.add("All");
        subjects.addAll(Arrays.asList(SYLLABUS_SUBJECTS));
        for (String subject : subjects) {
            Button filter = chip(subject);
            boolean selected = subject.equals(syllabusFilter);
            filter.setTextColor(color(selected ? "#F2F7F3" : "#B7C7BC"));
            filter.setBackground(bg(selected ? "#245B45" : "#1B2621", 10));
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, dp(32));
            fp.rightMargin = dp(5);
            filters.addView(filter, fp);
            filter.setOnClickListener(v -> {
                syllabusFilter = subject;
                refreshSyllabusRows();
                refreshSyllabusFilterStyles(filters, subjects);
            });
        }
        fs.addView(filters);
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(-1, dp(36));
        filterLp.bottomMargin = dp(6);
        syllabusDetailContent.addView(fs, filterLp);

        syllabusList = vertical();
        syllabusDetailContent.addView(syllabusList, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshSyllabusFilterStyles(LinearLayout filters, ArrayList<String> subjects) {
        for (int i = 0; i < filters.getChildCount() && i < subjects.size(); i++) {
            View child = filters.getChildAt(i);
            if (child instanceof Button) {
                boolean selected = subjects.get(i).equals(syllabusFilter);
                ((Button) child).setTextColor(color(selected ? "#F2F7F3" : "#B7C7BC"));
                child.setBackground(bg(selected ? "#245B45" : "#1B2621", 12));
            }
        }
    }

    private String roadmapMonthSlotsPrefsKey(String track) {
        String safeTrack = track == null ? "" : track.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return ROADMAP_MONTH_SLOTS_KEY_PREFIX + safeTrack;
    }

    private boolean isValidRoadmapMonthKey(String month) {
        return month != null && month.matches("\\d{4}-(0[1-9]|1[0-2])");
    }

    private ArrayList<String> readRoadmapMonthSlots(String track) {
        ArrayList<String> months = new ArrayList<>();
        if (track == null || track.trim().isEmpty()) return months;
        Set<String> stored = getPrefs().getStringSet(roadmapMonthSlotsPrefsKey(track), Collections.emptySet());
        if (stored != null) {
            for (String month : stored) if (isValidRoadmapMonthKey(month)) months.add(month);
        }
        Collections.sort(months);
        return months;
    }

    private void rememberRoadmapMonthSlot(String track, String month) {
        if (track == null || track.trim().isEmpty() || !isValidRoadmapMonthKey(month)) return;
        String key = roadmapMonthSlotsPrefsKey(track);
        Set<String> stored = getPrefs().getStringSet(key, Collections.emptySet());
        HashSet<String> updated = new HashSet<>(stored == null ? Collections.emptySet() : stored);
        if (updated.add(month)) getPrefs().edit().putStringSet(key, updated).apply();
    }

    private void rememberRoadmapMonthSlotsFromGoals(List<MonthlyGoal> goals) {
        if (goals == null || goals.isEmpty()) return;
        for (String track : MONTHLY_GOAL_TRACKS) {
            String key = roadmapMonthSlotsPrefsKey(track);
            Set<String> stored = getPrefs().getStringSet(key, Collections.emptySet());
            HashSet<String> updated = new HashSet<>(stored == null ? Collections.emptySet() : stored);
            int previousSize = updated.size();
            for (MonthlyGoal goal : goals) {
                if (!track.equals(goal.track)) continue;
                if (isValidRoadmapMonthKey(goal.month)) updated.add(goal.month);
                if (isValidRoadmapMonthKey(goal.originalMonth)) updated.add(goal.originalMonth);
            }
            if (updated.size() != previousSize) getPrefs().edit().putStringSet(key, updated).apply();
        }
    }

    private String roadmapRootKeyForGoalTrack(String track) {
        return "Other".equals(track) ? "other"
                : "Software Interview Prep".equals(track) ? "software" : "upsc";
    }

    private void revealRoadmapMonthSlotsInTree(String track, String sourceMonth, String targetMonth) {
        if (track == null || track.trim().isEmpty()) return;
        String rootKey = roadmapRootKeyForGoalTrack(track);
        ArrayList<RoadmapNode> roots = buildRoadmapTreeNodes();
        String[] months = {sourceMonth, targetMonth};
        String sourceNodeId = null;
        for (String month : months) {
            if (!isValidRoadmapMonthKey(month)) continue;
            String targetId = Objects.equals(month, selectedGoalMonth)
                    ? roadmapId("group", "current-goals", rootKey, track)
                    : roadmapId("group", "goal-month", rootKey, track, month);
            ArrayList<String> path = new ArrayList<>();
            if (collectRoadmapPath(roots, targetId, path)) {
                expandedRoadmapNodeIds.addAll(path);
                if (Objects.equals(month, sourceMonth) && !Objects.equals(sourceMonth, targetMonth)) {
                    sourceNodeId = targetId;
                }
            }
        }
        persistRoadmapExpansionState();
        refreshRoadmapSummary();
        if (sourceNodeId != null && roadmapTreeContainer != null) {
            final String scrollTargetId = sourceNodeId;
            roadmapTreeContainer.post(() -> {
                View target = roadmapTreeContainer.findViewWithTag(scrollTargetId);
                if (target != null) {
                    target.requestRectangleOnScreen(
                            new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight()), true);
                }
            });
        }
    }

    private void refreshRoadmapTabContent() {
        if (roadmapTreeContainer == null) return;
        refreshRoadmapSummary();
    }

    private String roadmapId(String... parts) {
        StringBuilder id = new StringBuilder();
        for (String part : parts) {
            if (id.length() > 0) id.append(':');
            String safe = part == null ? "" : part.trim().replaceAll("[^A-Za-z0-9_-]", "_");
            id.append(safe);
        }
        return id.toString();
    }

    private RoadmapNode newRoadmapNode(String id, String title, String type) {
        RoadmapNode node = new RoadmapNode();
        node.id = id;
        node.title = title == null ? "" : title;
        node.type = type == null ? "group" : type;
        node.subtitle = "";
        node.rootKey = "";
        node.subjectKey = null;
        node.goalTrack = null;
        node.milestoneTrack = null;
        node.month = null;
        node.statusPeriod = null;
        node.groupKind = null;
        node.item = null;
        node.done = false;
        return node;
    }

    private void ensureRoadmapExpansionState(List<RoadmapNode> roots) {
        if (roadmapExpansionStateLoaded) return;
        if (getPrefs().contains(ROADMAP_EXPANSION_KEY)) {
            Set<String> saved = getPrefs().getStringSet(ROADMAP_EXPANSION_KEY, Collections.emptySet());
            if (saved != null) expandedRoadmapNodeIds.addAll(new HashSet<>(saved));
        } else {
            expandedRoadmapNodeIds.add(roadmapId("track", "upsc"));
            expandedRoadmapNodeIds.add(roadmapId("track", "software"));
            expandedRoadmapNodeIds.add(roadmapId("subject", "upsc", "General Studies"));
            expandedRoadmapNodeIds.add(roadmapId("subject", "upsc", "Maths Optional"));
            expandedRoadmapNodeIds.add(roadmapId("subject", "software", "Software Interview Prep"));
            expandedRoadmapNodeIds.add(roadmapId("group", "current-goals", "upsc", "General Studies"));
            expandedRoadmapNodeIds.add(roadmapId("group", "current-goals", "upsc", "Maths Optional"));
            expandedRoadmapNodeIds.add(roadmapId("group", "current-goals", "software", "Software Interview Prep"));
        }
        roadmapExpansionStateLoaded = true;
        persistRoadmapExpansionState();
    }

    private void persistRoadmapExpansionState() {
        getPrefs().edit().putStringSet(ROADMAP_EXPANSION_KEY,
                new HashSet<>(expandedRoadmapNodeIds)).apply();
    }

    private void changeRoadmapExpansion(RoadmapNode node) {
        if (node == null || node.children.isEmpty()) return;
        if (!expandedRoadmapNodeIds.add(node.id)) expandedRoadmapNodeIds.remove(node.id);
        persistRoadmapExpansionState();
        renderRoadmapTreeBody();
    }

    private void refreshRoadmapFilterStyles() {
        for (Map.Entry<String, Button> entry : roadmapFilterButtons.entrySet()) {
            boolean selected = entry.getKey().equals(roadmapActiveFilter);
            Button button = entry.getValue();
            button.setTextColor(color(selected ? "#F5FBF7" : "#50635A"));
            button.setBackground(bg(selected ? "#23845F" : "#FFFFFF", 10));
        }
    }

    private void showRoadmapAddMenu() {
        final String[] options = {"Monthly goal", "Syllabus topic", "Milestone / deadline",
                "Import topic list", "Career target"};
        new AlertDialog.Builder(this)
                .setTitle("Add to roadmap")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) editMonthlyGoal(null);
                    else if (which == 1) editSyllabusTopic(null);
                    else if (which == 2) editRoadmapMilestone(null);
                    else if (which == 3) importSyllabusTopics();
                    else editCareerGoal();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRoadmapSubjectAddMenu(RoadmapNode node) {
        if (node == null) return;
        boolean canAddGoal = node.goalTrack != null && !node.goalTrack.isEmpty();
        boolean canAddTopic = node.subjectKey != null && !node.subjectKey.isEmpty();
        if (canAddGoal && canAddTopic) {
            new AlertDialog.Builder(this)
                    .setTitle("Add to " + node.title)
                    .setItems(new String[]{"Syllabus topic", "Monthly goal"}, (dialog, which) -> {
                        if (which == 0) editSyllabusTopic(null, node.subjectKey);
                        else editMonthlyGoal(null, node.goalTrack);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else if (canAddTopic) {
            editSyllabusTopic(null, node.subjectKey);
        } else if (canAddGoal) {
            editMonthlyGoal(null, node.goalTrack);
        }
    }

    private ArrayList<RoadmapNode> buildRoadmapTreeNodes() {
        ArrayList<SyllabusTopic> topics = readSyllabusTopics();
        ArrayList<MonthlyGoal> goals = readMonthlyGoals();
        rememberRoadmapMonthSlotsFromGoals(goals);
        ArrayList<RoadmapMilestone> milestones = readRoadmapMilestones();
        String selectedMonth = selectedGoalMonth == null ? monthKeyFromDate(todayKey()) : selectedGoalMonth;
        ArrayList<RoadmapNode> roots = new ArrayList<>();

        RoadmapNode upsc = newRoadmapNode(roadmapId("track", "upsc"), "UPSC CSE", "track");
        upsc.rootKey = "upsc";
        upsc.subtitle = "Syllabus, monthly goals, revision and exam checkpoints";
        String[][] upscSubjects = {
                {"General Studies", "General Studies", "General Studies"},
                {"Maths Optional", "Maths Optional", "Maths Optional"},
                {"CSAT", "CSAT", ""},
                {"Qualifying English", "Qualifying English", ""},
                {"Indian Language", "Indian Language", ""},
                {"Essay", "Essay", ""},
                {"Interview", "Interview", ""},
                {"Revision & Tests", "", "CSE Revision & Tests"}
        };
        for (String[] spec : upscSubjects) {
            addRoadmapSubjectNode(upsc, "upsc", spec[0],
                    spec[1].isEmpty() ? null : spec[1],
                    spec[2].isEmpty() ? null : spec[2], topics, goals, selectedMonth);
        }
        addRoadmapMilestoneGroup(upsc, "upsc", "UPSC CSE", milestones);
        if (!upsc.children.isEmpty()) roots.add(upsc);

        RoadmapNode software = newRoadmapNode(roadmapId("track", "software"),
                "Software Interview Preparation", "track");
        software.rootKey = "software";
        software.subtitle = "Coding, Java, system design and readiness checkpoints";
        addRoadmapSubjectNode(software, "software", "Interview skills & topics",
                "Software Interview Prep", "Software Interview Prep", topics, goals, selectedMonth);
        addRoadmapMilestoneGroup(software, "software", "Software interviews", milestones);
        RoadmapNode career = newRoadmapNode(roadmapId("career", "software"), "Career target", "career");
        career.rootKey = "software";
        career.subtitle = getPrefs().getString(CAREER_TARGET_KEY,
                "₹2.5–2.75L/month after taxes and deductions");
        software.children.add(career);
        roots.add(software);

        RoadmapNode other = newRoadmapNode(roadmapId("track", "other"),
                "Personal & other", "track");
        other.rootKey = "other";
        other.subtitle = "Goals and checkpoints outside the two main tracks";
        addRoadmapGoalGroups(other, "other", "Other", goals, selectedMonth);
        addRoadmapMilestoneGroup(other, "other", "Personal / other", milestones);
        if (!other.children.isEmpty()) roots.add(other);
        return roots;
    }

    private void addRoadmapSubjectNode(RoadmapNode parent, String rootKey, String displayTitle,
                                       String subjectKey, String goalTrack,
                                       ArrayList<SyllabusTopic> allTopics,
                                       ArrayList<MonthlyGoal> allGoals, String selectedMonth) {
        ArrayList<SyllabusTopic> subjectTopics = new ArrayList<>();
        if (subjectKey != null) {
            for (SyllabusTopic topic : allTopics) {
                if (subjectKey.equals(topic.subject)) subjectTopics.add(topic);
            }
        }
        ArrayList<MonthlyGoal> subjectGoals = new ArrayList<>();
        if (goalTrack != null) {
            for (MonthlyGoal goal : allGoals) if (goalTrack.equals(goal.track)) subjectGoals.add(goal);
        }
        boolean hasGoalMonthSlots = goalTrack != null && !readRoadmapMonthSlots(goalTrack).isEmpty();
        boolean isPlannedGoalTrack = "General Studies".equals(goalTrack)
                || "Maths Optional".equals(goalTrack)
                || "Software Interview Prep".equals(goalTrack);
        if (subjectTopics.isEmpty() && subjectGoals.isEmpty() && !hasGoalMonthSlots && !isPlannedGoalTrack) return;

        RoadmapNode subject = newRoadmapNode(
                roadmapId("subject", rootKey, subjectKey == null ? goalTrack : subjectKey),
                displayTitle, "subject");
        subject.rootKey = rootKey;
        subject.subjectKey = subjectKey;
        subject.goalTrack = goalTrack;
        subject.subtitle = subjectTopics.size() + " topic" + (subjectTopics.size() == 1 ? "" : "s")
                + (subjectGoals.isEmpty() ? "" : " · " + subjectGoals.size() + " monthly goal" + (subjectGoals.size() == 1 ? "" : "s"));
        addRoadmapGoalGroups(subject, rootKey, goalTrack, subjectGoals, selectedMonth);

        if (!subjectTopics.isEmpty()) {
            RoadmapNode topicGroup = newRoadmapNode(
                    roadmapId("group", "topics", rootKey, subjectKey), "Syllabus & skills", "group");
            topicGroup.rootKey = rootKey;
            topicGroup.subjectKey = subjectKey;
            topicGroup.groupKind = "topics";
            topicGroup.subtitle = subjectTopics.size() + " syllabus item" + (subjectTopics.size() == 1 ? "" : "s");
            subjectTopics.sort(syllabusTopicOrderComparator());
            for (SyllabusTopic topic : subjectTopics) {
                RoadmapNode leaf = newRoadmapNode(roadmapId("topic", rootKey, subjectKey,
                        Long.toString(topic.id)), topic.title, "topic");
                leaf.rootKey = rootKey;
                leaf.subjectKey = topic.subject;
                leaf.item = topic;
                leaf.done = topic.done;
                topicGroup.children.add(leaf);
            }
            // Goals stay at the top of each branch; large syllabus lists remain collapsed.
            subject.children.add(topicGroup);
        }
        parent.children.add(subject);
    }

    private void addRoadmapGoalGroups(RoadmapNode parent, String rootKey, String goalTrack,
                                      ArrayList<MonthlyGoal> goals, String selectedMonth) {
        if (goalTrack == null) return;
        ArrayList<MonthlyGoal> current = new ArrayList<>(), future = new ArrayList<>(),
                past = new ArrayList<>(), retired = new ArrayList<>();
        for (MonthlyGoal goal : goals) {
            if (!goalTrack.equals(goal.track) || goal.parentGoalId > 0L) continue;
            if (MonthlyGoalLifecycle.RETIRED.equals(goal.lifecycleStatus)) retired.add(goal);
            else if (selectedMonth.equals(goal.month)) current.add(goal);
            else if (goal.month.compareTo(selectedMonth) > 0) future.add(goal);
            else past.add(goal);
        }
        Comparator<MonthlyGoal> byOrder = monthlyGoalOrderComparator(goals);
        current.sort(byOrder);
        boolean selectedMonthIsPlanned = false;
        if (!"Other".equals(goalTrack)) {
            for (int i = 0; i < RoadmapMonthlyPlan.monthCount(); i++) {
                if (selectedMonth.equals(RoadmapMonthlyPlan.monthKeyAt(i))) {
                    selectedMonthIsPlanned = true;
                    break;
                }
            }
        }
        boolean hasTrackGoals = false;
        for (MonthlyGoal goal : goals) {
            if (goalTrack.equals(goal.track)) { hasTrackGoals = true; break; }
        }
        boolean hasRememberedMonthSlots = !readRoadmapMonthSlots(goalTrack).isEmpty();
        // Keep a usable current-month drop target for any track that has a plan or month history.
        if (!current.isEmpty() || selectedMonthIsPlanned || hasTrackGoals || hasRememberedMonthSlots) {
            int count = 0;
            for (MonthlyGoal goal : current) count += countScheduledGoalLeaves(goal, goals);
            RoadmapNode group = newRoadmapNode(roadmapId("group", "current-goals", rootKey, goalTrack),
                    monthLabelForKey(selectedMonth) + " · " + count + " goal" + (count == 1 ? "" : "s"), "group");
            group.rootKey = rootKey; group.goalTrack = goalTrack; group.month = selectedMonth;
            group.groupKind = "current-goals"; group.subtitle = "Current focus";
            for (MonthlyGoal goal : current) group.children.add(roadmapGoalTreeNode(rootKey, goal, goals));
            parent.children.add(group);
        }
        addRoadmapMonthGoalGroup(parent, rootKey, goalTrack, future, "Upcoming goals", "upcoming-goals", true, goals, selectedMonth);
        addRoadmapMonthGoalGroup(parent, rootKey, goalTrack, past, "Past goals", "past-goals", false, goals, selectedMonth);
        if (!retired.isEmpty()) {
            retired.sort(byOrder);
            RoadmapNode archive = newRoadmapNode(roadmapId("group", "retired-goals", rootKey, goalTrack),
                    "Retired goals · " + retired.size(), "group");
            archive.rootKey = rootKey; archive.goalTrack = goalTrack; archive.groupKind = "retired-goals";
            archive.subtitle = "Archived intentionally; excluded from active progress";
            for (MonthlyGoal goal : retired) archive.children.add(roadmapArchivedGoalLeaf(rootKey, goal));
            parent.children.add(archive);
        }
    }

    private int countScheduledGoalLeaves(MonthlyGoal goal, ArrayList<MonthlyGoal> all) {
        if (MonthlyGoalLifecycle.RETIRED.equals(goal.lifecycleStatus)) return 0;
        if (!MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus)) return 1;
        int count = 0;
        for (MonthlyGoal child : all) {
            if (child.parentGoalId == goal.id && Objects.equals(goal.month, child.month)) {
                count += countScheduledGoalLeaves(child, all);
            }
        }
        return count;
    }

    private void addRoadmapMonthGoalGroup(RoadmapNode parent, String rootKey, String goalTrack,
                                           ArrayList<MonthlyGoal> goals, String title,
                                           String groupKind, boolean ascending, ArrayList<MonthlyGoal> allGoals,
                                           String selectedMonth) {
        ArrayList<String> goalMonthKeys = new ArrayList<>();
        for (MonthlyGoal goal : allGoals) {
            if (goalTrack.equals(goal.track) && goal.month != null) goalMonthKeys.add(goal.month);
        }
        // Month slots are independent of current goal occupancy: remember the source after its last goal leaves.
        goalMonthKeys.addAll(readRoadmapMonthSlots(goalTrack));
        ArrayList<String> plannedMonthKeys = new ArrayList<>();
        for (int i = 0; i < RoadmapMonthlyPlan.monthCount(); i++) {
            plannedMonthKeys.add(RoadmapMonthlyPlan.monthKeyAt(i));
        }
        // Planned months are slots in the roadmap, not just labels inferred from populated goals.
        // This preserves empty source months after a move so users can still see the slot.
        ArrayList<String> orderedMonths = RoadmapTreeRules.monthKeysForPeriod(
                selectedMonth, ascending, goalMonthKeys, plannedMonthKeys);
        if (orderedMonths.isEmpty()) return;
        RoadmapNode group = newRoadmapNode(roadmapId("group", groupKind, rootKey, goalTrack),
                title + " · " + goals.size(), "group");
        group.rootKey = rootKey; group.goalTrack = goalTrack; group.groupKind = groupKind;
        group.subtitle = "Expand a month to see its goals";
        for (String monthKey : orderedMonths) {
            ArrayList<MonthlyGoal> inMonth = new ArrayList<>();
            for (MonthlyGoal goal : goals) if (monthKey.equals(goal.month)) inMonth.add(goal);
            inMonth.sort(monthlyGoalOrderComparator(allGoals));
            int count = 0;
            for (MonthlyGoal goal : inMonth) count += countScheduledGoalLeaves(goal, allGoals);
            RoadmapNode month = newRoadmapNode(roadmapId("group", "goal-month", rootKey, goalTrack, monthKey),
                    monthLabelForKey(monthKey) + " · " + count + " goal" + (count == 1 ? "" : "s"), "group");
            month.rootKey = rootKey; month.goalTrack = goalTrack; month.month = monthKey; month.groupKind = "goal-month";
            for (MonthlyGoal goal : inMonth) month.children.add(roadmapGoalTreeNode(rootKey, goal, allGoals));
            group.children.add(month);
        }
        parent.children.add(group);
    }

    private RoadmapNode roadmapGoalTreeNode(String rootKey, MonthlyGoal goal, ArrayList<MonthlyGoal> all) {
        boolean archived = MonthlyGoalLifecycle.RETIRED.equals(goal.lifecycleStatus);
        boolean hasChildren = false;
        for (MonthlyGoal child : all) if (child.parentGoalId == goal.id
                && Objects.equals(goal.month, child.month)) { hasChildren = true; break; }
        if (MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus) || (archived && hasChildren)) {
            RoadmapNode group = newRoadmapNode(roadmapId(
                    archived ? "retired-split-goal" : "split-goal", rootKey, goal.track,
                    Long.toString(goal.id)), goal.title, "group");
            group.rootKey = rootKey; group.goalTrack = goal.track; group.month = goal.month;
            group.groupKind = archived ? "retired-split-goal" : "split-goal"; group.item = goal;
            group.subtitle = (archived ? "Archived split goal" : "Smaller goals")
                    + " · originally planned "
                    + monthLabelForKey(goal.originalMonth == null ? goal.month : goal.originalMonth);
            ArrayList<MonthlyGoal> children = new ArrayList<>();
            for (MonthlyGoal child : all) {
                if (child.parentGoalId == goal.id && Objects.equals(goal.month, child.month)
                        && (archived || !MonthlyGoalLifecycle.RETIRED.equals(child.lifecycleStatus))) {
                    children.add(child);
                }
            }
            children.sort(monthlyGoalOrderComparator(all));
            for (MonthlyGoal child : children) group.children.add(roadmapGoalTreeNode(rootKey, child, all));
            return group;
        }
        if (archived) return roadmapArchivedGoalLeaf(rootKey, goal);
        return roadmapGoalLeaf(rootKey, goal);
    }

    private RoadmapNode roadmapArchivedGoalLeaf(String rootKey, MonthlyGoal goal) {
        RoadmapNode leaf = newRoadmapNode(roadmapId("goal-archive", rootKey, goal.track, Long.toString(goal.id)),
                goal.title, "goal-archive");
        leaf.rootKey = rootKey; leaf.goalTrack = goal.track; leaf.month = goal.month; leaf.statusPeriod = goal.month;
        leaf.subtitle = "Retired · originally planned "
                + monthLabelForKey(goal.originalMonth == null ? goal.month : goal.originalMonth);
        leaf.item = goal; leaf.done = false;
        return leaf;
    }

        private RoadmapNode roadmapGoalLeaf(String rootKey, MonthlyGoal goal) {
        RoadmapNode leaf = newRoadmapNode(roadmapId("goal", rootKey, goal.track,
                Long.toString(goal.id)), goal.title, "goal");
        leaf.rootKey = rootKey;
        leaf.goalTrack = goal.track;
        leaf.month = goal.month;
        leaf.statusPeriod = goal.month;
        leaf.subtitle = goal.target == null ? "" : goal.target;
        leaf.item = goal;
        leaf.done = goal.done;
        return leaf;
    }

    private void addRoadmapMilestoneGroup(RoadmapNode parent, String rootKey, String milestoneTrack,
                                          ArrayList<RoadmapMilestone> allMilestones) {
        ArrayList<RoadmapMilestone> milestones = new ArrayList<>();
        for (RoadmapMilestone milestone : allMilestones) {
            if (milestoneTrack.equals(milestone.track)) milestones.add(milestone);
        }
        if (milestones.isEmpty()) return;
        milestones.sort((a, b) -> {
            if (a.done != b.done) return a.done ? 1 : -1;
            return (a.date == null ? "9999-12-31" : a.date)
                    .compareTo(b.date == null ? "9999-12-31" : b.date);
        });
        RoadmapNode group = newRoadmapNode(roadmapId("group", "milestones", rootKey),
                "Milestones & deadlines · " + milestones.size(), "group");
        group.rootKey = rootKey;
        group.milestoneTrack = milestoneTrack;
        group.groupKind = "milestones";
        group.subtitle = "Readiness checkpoints and target dates";
        for (RoadmapMilestone milestone : milestones) {
            RoadmapNode leaf = newRoadmapNode(roadmapId("milestone", rootKey,
                    Long.toString(milestone.id)), milestone.title, "milestone");
            leaf.rootKey = rootKey;
            leaf.milestoneTrack = milestone.track;
            leaf.statusPeriod = milestone.date;
            leaf.subtitle = milestone.detail == null ? "" : milestone.detail;
            leaf.item = milestone;
            leaf.done = milestone.done;
            group.children.add(leaf);
        }
        parent.children.add(group);
    }

    private boolean isRoadmapActionNode(RoadmapNode node) {
        return node != null && RoadmapTreeRules.isActionableType(node.type);
    }

    private int[] roadmapNodeProgress(RoadmapNode node) {
        if (node == null) return new int[]{0, 0};
        if ("goal-archive".equals(node.type)) return new int[]{0, 0};
        if ("goal".equals(node.type) && node.item instanceof MonthlyGoal
                && MonthlyGoalLifecycle.RETIRED.equals(((MonthlyGoal) node.item).lifecycleStatus)) {
            return new int[]{0, 0};
        }
        if (isRoadmapActionNode(node)) return RoadmapTreeRules.leafProgress(node.done);
        int[] progress = new int[]{0, 0};
        for (RoadmapNode child : node.children) {
            progress = RoadmapTreeRules.combineProgress(progress, roadmapNodeProgress(child));
        }
        return progress;
    }

    private String roadmapNodeMetadata(RoadmapNode node) {
        if (node == null) return "";
        String today = todayKey();
        boolean overdue = "goal".equals(node.type) && node.item instanceof MonthlyGoal
                ? MonthlyGoalLifecycle.isPastUnfinished(((MonthlyGoal) node.item).lifecycleStatus,
                        node.done, node.statusPeriod, monthKeyFromDate(today))
                : RoadmapTreeRules.isOverdue(node.type, node.done, node.statusPeriod, today);
        if ("track".equals(node.type) || "subject".equals(node.type)) {
            return node.subtitle == null ? "" : node.subtitle;
        }
        if ("group".equals(node.type)) {
            if ("goal-month".equals(node.groupKind) || "current-goals".equals(node.groupKind)) {
                // Drag-and-drop instructions only belong in Edit mode; in browse mode the
                // month title and goal count already provide the needed context.
                if (!roadmapTreeEditMode) return "";
                return node.children.isEmpty()
                        ? "Drop a goal here to move it into this month"
                        : "Drop a goal here to nest it under a parent";
            }
            if ("split-goal".equals(node.groupKind)) {
                return (node.subtitle == null ? "Split into smaller goals" : node.subtitle)
                        + " · progress counts smaller goals only";
            }
            if ("retired-split-goal".equals(node.groupKind)) {
                return (node.subtitle == null ? "Archived split goal" : node.subtitle)
                        + " · excluded from active progress";
            }
            return node.subtitle == null ? "" : node.subtitle;
        }
        if ("goal-archive".equals(node.type)) return node.subtitle == null ? "Retired" : node.subtitle;
        if ("topic".equals(node.type)) {
            SyllabusTopic topic = (SyllabusTopic) node.item;
            return topic.subject + (topic.done ? " · Complete" : " · Open");
        }
        if ("goal".equals(node.type)) {
            String line = monthLabelForKey(node.month);
            if (node.item instanceof MonthlyGoal) {
                MonthlyGoal goal = (MonthlyGoal) node.item;
                if (goal.originalMonth != null && !goal.originalMonth.equals(goal.month)) {
                    line += " · Moved from " + monthLabelForKey(goal.originalMonth);
                }
                if (goal.done && goal.completedAt > 0L) {
                    line += " · Completed " + new SimpleDateFormat("d MMM", Locale.getDefault())
                            .format(new Date(goal.completedAt));
                }
            }
            if (overdue) line += " · Overdue";
            return line;
        }
        if ("milestone".equals(node.type)) {
            String line = "Due " + displayRoadmapDate(node.statusPeriod);
            if (overdue) line += " · Overdue";
            else if (node.done) line += " · Complete";
            String preview = roadmapTreeDetailPreview(node.subtitle);
            if (!preview.isEmpty()) line += " · " + preview;
            return line;
        }
        if ("career".equals(node.type)) return node.subtitle;
        return node.subtitle == null ? "" : node.subtitle;
    }

    /**
     * Keeps the tree scannable by showing a short summary rather than a whole target paragraph.
     * The full description remains available when the user opens the roadmap item.
     */
    private String roadmapTreeDetailPreview(String detail) {
        if (detail == null || detail.trim().isEmpty()) return "";
        String cleaned = detail.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();

        // Month-by-month source descriptions often repeat the scheduled month.
        int separator = cleaned.indexOf(" · ");
        if (separator > 0 && separator <= 20
                && cleaned.substring(0, separator).matches(
                        "(January|February|March|April|May|June|July|August|September|October|November|December) \\d{4}")) {
            cleaned = cleaned.substring(separator + 3).trim();
        }

        int sentenceEnd = -1;
        int scanLimit = Math.min(cleaned.length() - 1, 68);
        for (int i = 0; i < scanLimit; i++) {
            char ch = cleaned.charAt(i);
            if ((ch == '.' || ch == '!' || ch == '?') && Character.isWhitespace(cleaned.charAt(i + 1))) {
                sentenceEnd = i;
                break;
            }
        }
        if (sentenceEnd >= 0) return cleaned.substring(0, sentenceEnd + 1).trim();
        if (cleaned.length() <= 70) return cleaned;

        int cut = cleaned.lastIndexOf(' ', 67);
        if (cut < 48) cut = 67;
        return cleaned.substring(0, cut).trim() + "…";
    }

    private String inferGoalSource(String target, String track) {
        String text = target == null ? "" : target.replace('\n', ' ').replace('\r', ' ').trim();
        java.util.regex.Matcher primary = java.util.regex.Pattern
                .compile("(?i)primary sources?:\\s*(.*?)(?:\\.|$)")
                .matcher(text);
        if (primary.find()) {
            String source = primary.group(1);
            source = source.replaceAll("(?i)\\s*\\((?:about|approximately|approx\\.?|~)?\\s*\\d+\\s*(?:pages?)?\\)", "");
            source = source.replaceAll("(?i),?\\s*(?:approximately|approx\\.?|about|~)\\s*\\d+\\s*pages?(?:\\s+combined)?", "");
            source = source.replaceAll("\\s+", " ").replaceAll("\\s+,", ",").trim();
            while (source.endsWith(",") || source.endsWith(";")) {
                source = source.substring(0, source.length() - 1).trim();
            }
            if (!source.isEmpty()) return source;
        }
        if (text.toLowerCase(Locale.US).contains("ims notes")) {
            return "December 2022 IMS notes";
        }
        if ("Software Interview Prep".equals(track)) {
            return "Interview syllabus and coding/design practice plan";
        }
        if ("CSE Revision & Tests".equals(track)) {
            return "UPSC PYQs, GS mocks and CSAT practice";
        }
        return "";
    }

    private String inferGoalPages(String target, String track) {
        String text = target == null ? "" : target;
        java.util.regex.Matcher combined = java.util.regex.Pattern
                .compile("(?i)(?:about|approximately|approx\\.?|~)?\\s*(\\d+)\\s*pages?\\s+combined")
                .matcher(text);
        String count = null;
        while (combined.find()) count = combined.group(1);
        if (count == null) {
            java.util.regex.Matcher pages = java.util.regex.Pattern
                    .compile("(?i)(?:about|approximately|approx\\.?|~)\\s*(\\d+)\\s*pages?")
                    .matcher(text);
            while (pages.find()) count = pages.group(1);
        }
        if (count != null) return "≈" + count + " pages";
        if ("Software Interview Prep".equals(track) || "CSE Revision & Tests".equals(track)) {
            return "Not page-based";
        }
        return "Not listed in the plan";
    }

    private String inferGoalEstimatedTime(String target, String track) {
        String text = target == null ? "" : target;
        java.util.regex.Matcher weekly = java.util.regex.Pattern
                .compile("(?i)(\\d+)\\s*[–-]\\s*(\\d+)\\s*(?:hours?|hrs?|h)\\s*(?:/\\s*week|per\\s+week)")
                .matcher(text);
        int lowPerWeek = 0;
        int highPerWeek = 0;
        int ranges = 0;
        while (weekly.find()) {
            lowPerWeek += Integer.parseInt(weekly.group(1));
            highPerWeek += Integer.parseInt(weekly.group(2));
            ranges++;
        }
        if (ranges > 0) {
            return "≈" + (lowPerWeek * 4) + "–" + (highPerWeek * 4)
                    + " study hours (4-week plan)";
        }
        if ("General Studies".equals(track) || "Maths Optional".equals(track)) {
            return "≈64 study hours (16 h/week × 4 weeks)";
        }
        return "";
    }

    private String goalInfoValue(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void addRoadmapGoalDetailPanel(LinearLayout parent, MonthlyGoal goal) {
        LinearLayout panel = vertical();
        panel.setPadding(dp(8), dp(7), dp(8), dp(7));
        GradientDrawable panelBackground = bg("#F3F7F3", 8);
        panelBackground.setStroke(dp(1), color("#E0E9E1"));
        panel.setBackground(panelBackground);

        TextView heading = text("PLAN DETAILS", 9, color("#2E8060"), true);
        heading.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.bottomMargin = dp(3);
        panel.addView(heading, headingLp);

        addRoadmapGoalDetailRow(panel, "SOURCE",
                goalInfoValue(goal.source, "Not specified — edit goal"), 2);
        addRoadmapGoalDetailRow(panel, "PAGES",
                goalInfoValue(goal.pages, "Not listed"), 1);
        addRoadmapGoalDetailRow(panel, "EST. TIME",
                goalInfoValue(goal.estimatedTime, "Not estimated — edit goal"), 2);

        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, -2);
        panelLp.topMargin = dp(5);
        panelLp.leftMargin = dp(2);
        panelLp.rightMargin = dp(2);
        parent.addView(panel, panelLp);
    }

    private void addRoadmapGoalDetailRow(LinearLayout parent, String label, String value, int maxLines) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(2);
        parent.addView(row, rowLp);

        TextView labelView = text(label, 9, color("#687F72"), true);
        labelView.setLetterSpacing(0.02f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(dp(76), -2);
        labelLp.topMargin = dp(1);
        row.addView(labelView, labelLp);

        TextView valueView = text(value, 11, color("#344A40"), false);
        valueView.setLineSpacing(dp(1), 1f);
        valueView.setMaxLines(maxLines);
        valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(valueView, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private String roadmapNodeSearchText(RoadmapNode node) {
        return ((node.title == null ? "" : node.title) + " "
                + (node.subtitle == null ? "" : node.subtitle) + " "
                + (node.subjectKey == null ? "" : node.subjectKey) + " "
                + (node.goalTrack == null ? "" : node.goalTrack) + " "
                + (node.milestoneTrack == null ? "" : node.milestoneTrack) + " "
                + (node.month == null ? "" : monthLabelForKey(node.month)) + " "
                + roadmapNodeMetadata(node)).toLowerCase(Locale.US);
    }

    private boolean roadmapNodeOwnMatchesQuery(RoadmapNode node, String query) {
        if (query == null || query.trim().isEmpty()) return false;
        String ownText = ((node.title == null ? "" : node.title) + " "
                + (node.subjectKey == null ? "" : node.subjectKey) + " "
                + (node.goalTrack == null ? "" : node.goalTrack) + " "
                + (node.milestoneTrack == null ? "" : node.milestoneTrack) + " "
                + (node.month == null ? "" : monthLabelForKey(node.month)) + " "
                + (node.subtitle == null ? "" : node.subtitle)).toLowerCase(Locale.US);
        return ownText.contains(query.toLowerCase(Locale.US));
    }

    private boolean roadmapNodeVisible(RoadmapNode node, String query, boolean forceInclude) {
        if (node == null) return false;
        if (forceInclude) return true;
        if (node.children.isEmpty()) {
            if (!roadmapActiveFilter.equals("All")
                    && !RoadmapTreeRules.matchesFilter(roadmapActiveFilter, node.type,
                    node.done, node.statusPeriod, todayKey())) return false;
            return query == null || query.trim().isEmpty()
                    || roadmapNodeSearchText(node).contains(query.toLowerCase(Locale.US));
        }
        if (query != null && !query.trim().isEmpty()
                && roadmapActiveFilter.equals("All")
                && roadmapNodeOwnMatchesQuery(node, query)) return true;
        if (query == null || query.trim().isEmpty()) {
            if (roadmapActiveFilter.equals("All")) return true;
        }
        for (RoadmapNode child : node.children) {
            if (roadmapNodeVisible(child, query, false)) return true;
        }
        return false;
    }

    private boolean roadmapHasVisibleChild(RoadmapNode node, String query) {
        if (node == null) return false;
        for (RoadmapNode child : node.children) {
            if (roadmapNodeVisible(child, query, false)) return true;
        }
        return false;
    }

    private void renderRoadmapTreeBody() {
        if (roadmapTreeContainer == null) return;
        ArrayList<RoadmapNode> roots = buildRoadmapTreeNodes();
        ensureRoadmapExpansionState(roots);
        roadmapTreeContainer.removeAllViews();
        int[] overall = new int[]{0, 0};
        for (RoadmapNode rootNode : roots) {
            overall = RoadmapTreeRules.combineProgress(overall, roadmapNodeProgress(rootNode));
        }
        String status = overall[0] + " of " + overall[1] + " roadmap items complete.";
        RoadmapMilestone next = null;
        for (RoadmapMilestone milestone : readRoadmapMilestones()) {
            if (milestone.done) continue;
            if (next == null || (milestone.date != null
                    && milestone.date.compareTo(next.date == null ? "9999-12-31" : next.date) < 0)) next = milestone;
        }
        if (next != null) status += " Next deadline: " + next.title + " · " + displayRoadmapDate(next.date) + ".";
        if (roadmapTreeEditMode) {
            status += " Edit mode: drop onto a goal to swap positions; drop onto an empty month to move a goal there, or a populated month to make it a sub-goal. Continuity rules stay enforced.";
        } else if (roadmapSearchText.trim().isEmpty() && roadmapActiveFilter.equals("All")) {
            status += " Expand a branch to explore; tap a leaf to edit it.";
        } else {
            status += " Showing matches in their parent branches.";
        }
        roadmapTreeSummary.setText(status);

        int rendered = 0;
        for (RoadmapNode rootNode : roots) {
            if (!roadmapNodeVisible(rootNode, roadmapSearchText, false)) continue;
            renderRoadmapNode(rootNode, 0, false);
            rendered++;
            View divider = new View(this);
            divider.setBackgroundColor(color("#DDE6DE"));
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
            dividerLp.topMargin = dp(5);
            dividerLp.bottomMargin = dp(5);
            roadmapTreeContainer.addView(divider, dividerLp);
        }
        if (rendered == 0) {
            LinearLayout empty = vertical();
            empty.setPadding(dp(12), dp(18), dp(12), dp(18));
            empty.addView(text("No roadmap items match.", 14, color("#18372E"), true));
            empty.addView(text("Try All, clear the search, or add a goal, topic or milestone.",
                    12, color("#687A70"), false));
            roadmapTreeContainer.addView(empty);
        }
    }

    private void renderRoadmapNode(RoadmapNode node, int depth, boolean forceInclude) {
        String query = roadmapSearchText.trim();
        if (!roadmapNodeVisible(node, query, forceInclude)) return;
        boolean hasChildren = !node.children.isEmpty();
        boolean ownMatch = !query.isEmpty() && roadmapActiveFilter.equals("All")
                && roadmapNodeOwnMatchesQuery(node, query);
        boolean autoExpand = hasChildren && (roadmapActiveFilter.equals("Overdue")
                || roadmapActiveFilter.equals("Open") || roadmapActiveFilter.equals("Done")
                || (!query.isEmpty() && (ownMatch || roadmapHasVisibleChild(node, query))));
        boolean expanded = hasChildren && (expandedRoadmapNodeIds.contains(node.id) || autoExpand);

        LinearLayout row = horizontal();
        row.setTag(node.id);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int left = 4 + Math.min(depth, 5) * 13;
        row.setPadding(dp(left), dp(7), dp(4), dp(7));
        if ("track".equals(node.type)) row.setBackground(bg("#EAF3EC", 11));
        else if ("subject".equals(node.type)) row.setBackground(bg("#F7F9F6", 9));
        else row.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = dp("track".equals(node.type) ? 5 : 1);
        roadmapTreeContainer.addView(row, rowLp);

        if (roadmapTreeEditMode && isRoadmapGoalNode(node)) {
            MonthlyGoal goal = (MonthlyGoal) node.item;
            boolean canDrag = canStartRoadmapGoalDrag(node);
            TextView dragHandle = text(canDrag ? "☷" : "🔒", 16,
                    color(canDrag ? "#23845F" : "#9AA79F"), true);
            dragHandle.setGravity(Gravity.CENTER);
            dragHandle.setContentDescription(canDrag
                    ? "Hold and drag " + goal.title + ". Drop onto another goal to swap slots, or onto a month to add it as a sub-goal."
                    : "Locked: completed work in this goal must stay in sequence");
            row.addView(dragHandle, new LinearLayout.LayoutParams(dp(26), dp(30)));
            if (canDrag) {
                dragHandle.setOnLongClickListener(v -> {
                    android.content.ClipData data = android.content.ClipData.newPlainText(
                            "dayflow-roadmap-goal-id", Long.toString(goal.id));
                    android.view.View.DragShadowBuilder shadow = new android.view.View.DragShadowBuilder(v);
                    boolean started;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        started = v.startDragAndDrop(data, shadow, null, 0);
                    } else {
                        started = v.startDrag(data, shadow, null, 0);
                    }
                    if (!started) {
                        Toast.makeText(MainActivity.this, "Couldn't start dragging this goal.", Toast.LENGTH_SHORT).show();
                    }
                    return started;
                });
            }
            attachRoadmapGoalDropTarget(row, node);
        } else if (roadmapTreeEditMode && isRoadmapMonthDropNode(node)) {
            // Attach after the row's children are built so text and the + button are drop targets too.
        }

        if (isRoadmapActionNode(node)) {
            Button mark = actionButton(node.done ? "✓" : "○",
                    color(node.done ? "#F5FBF7" : "#50635A"),
                    node.done ? "#23845F" : "#FFFFFF");
            mark.setTextSize(12);
            mark.setContentDescription(node.done ? "Mark pending" : "Mark complete");
            row.addView(mark, new LinearLayout.LayoutParams(dp(30), dp(30)));
            mark.setOnClickListener(v -> toggleRoadmapLeaf(node));
        } else if (hasChildren) {
            TextView disclosure = text(expanded ? "⌄" : "›", 19, color("#2E8060"), true);
            disclosure.setGravity(Gravity.CENTER);
            row.addView(disclosure, new LinearLayout.LayoutParams(dp(28), dp(30)));
        } else {
            TextView symbol = text("↗", 15, color("#82948A"), true);
            symbol.setGravity(Gravity.CENTER);
            row.addView(symbol, new LinearLayout.LayoutParams(dp(28), dp(30)));
        }

        LinearLayout detail = vertical();
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(0, -2, 1f);
        detailLp.leftMargin = dp(7);
        detailLp.rightMargin = dp(4);
        row.addView(detail, detailLp);
        float titleSize = "track".equals(node.type) ? 14f : "subject".equals(node.type) ? 13f : 12f;
        TextView title = text(node.title, titleSize,
                node.done && isRoadmapActionNode(node) ? color("#687A70") : color("#18372E"),
                !"group".equals(node.type) && !"topic".equals(node.type));
        title.setMaxLines("track".equals(node.type) || "subject".equals(node.type) ? 2 : 3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (node.done && isRoadmapActionNode(node)) {
            title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        detail.addView(title);
        String metadata = roadmapNodeMetadata(node);
        if (metadata != null && !metadata.trim().isEmpty()) {
            boolean trackDescription = "track".equals(node.type) || "subject".equals(node.type);
            TextView meta = text(metadata, trackDescription ? 12 : 11,
                    RoadmapTreeRules.isOverdue(node.type, node.done, node.statusPeriod, todayKey())
                            ? color("#A76821") : color("#52675C"),
                    false);
            // Darker neutral copy stays readable; regular weight preserves title hierarchy.
            meta.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            meta.setLineSpacing(dp(2), 1f);
            meta.setMaxLines(2);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
            metaLp.topMargin = dp(3);
            detail.addView(meta, metaLp);
        }

        // Monthly goal execution facts live in a visibly nested panel, not in a truncated paragraph.
        if ("goal".equals(node.type) && node.item instanceof MonthlyGoal) {
            addRoadmapGoalDetailPanel(detail, (MonthlyGoal) node.item);
        }

        int[] progress = roadmapNodeProgress(node);
        if (progress[1] > 0 && hasChildren) {
            TextView count = text(progress[0] + "/" + progress[1], 10, color("#2E8060"), true);
            count.setGravity(Gravity.CENTER);
            row.addView(count, new LinearLayout.LayoutParams(dp(42), dp(30)));
        }

        Runnable childAction = roadmapNodeChildAction(node);
        if (childAction != null) {
            Button addChild = actionButton("+", color("#2E8060"), "#FFFFFF");
            addChild.setTextSize(17);
            addChild.setContentDescription("Add item under " + node.title);
            row.addView(addChild, new LinearLayout.LayoutParams(dp(30), dp(30)));
            addChild.setOnClickListener(v -> childAction.run());
        }
        String editDropHint = roadmapTreeEditMode && isRoadmapMonthDropNode(node)
                ? (node.children.isEmpty()
                    ? ". Drop a goal here to move it into this month."
                    : ". Drop a goal here to add it as a sub-goal; choose a parent if needed.")
                : (roadmapTreeEditMode && isRoadmapGoalNode(node)
                    ? ". Drop another goal here to swap its slot." : "");
        row.setContentDescription(node.title + ". " + metadata
                + (hasChildren ? (expanded ? ". Tap to collapse." : ". Tap to expand.")
                : (roadmapTreeEditMode
                    ? ". Drag the goal handle; exit Edit to edit details."
                    : ". Tap to edit."))
                + editDropHint);
        row.setOnClickListener(v -> {
            if (hasChildren) {
                changeRoadmapExpansion(node);
            } else if (!roadmapTreeEditMode) {
                openRoadmapLeaf(node);
            }
        });
        row.setOnLongClickListener(v -> {
            if (roadmapTreeEditMode) return false;
            if (node.item instanceof MonthlyGoal) {
                showMonthlyGoalActions((MonthlyGoal) node.item);
                return true;
            }
            if (node.item instanceof SyllabusTopic) {
                showSyllabusTopicActions((SyllabusTopic) node.item);
                return true;
            }
            if (node.item instanceof RoadmapMilestone) {
                confirmDeleteRoadmapMilestone((RoadmapMilestone) node.item);
                return true;
            }
            return false;
        });

        // Attach after all row children (especially the + Add button) exist. Android routes
        // drag events to the child under the pointer, so the whole month row must accept drops.
        if (roadmapTreeEditMode && isRoadmapMonthDropNode(node)) {
            attachRoadmapMonthDropTarget(row, node);
        }

        if (expanded) {
            boolean forceChildren = forceInclude || ownMatch;
            for (RoadmapNode child : node.children) {
                if (roadmapNodeVisible(child, query, forceChildren)) {
                    renderRoadmapNode(child, depth + 1, forceChildren);
                }
            }
        }
    }

    private boolean isRoadmapGoalNode(RoadmapNode node) {
        if (node == null || !(node.item instanceof MonthlyGoal)) return false;
        MonthlyGoal goal = (MonthlyGoal) node.item;
        if (MonthlyGoalLifecycle.RETIRED.equals(goal.lifecycleStatus)) return false;
        return "goal".equals(node.type)
                || ("group".equals(node.type) && "split-goal".equals(node.groupKind));
    }

    private boolean canStartRoadmapGoalDrag(RoadmapNode node) {
        if (!isRoadmapGoalNode(node)) return false;
        MonthlyGoal goal = (MonthlyGoal) node.item;
        if (goal.done) return false;
        return !monthlyGoalGroupHasCompletedWork(goal, readMonthlyGoals());
    }

    private boolean isRoadmapMonthDropNode(RoadmapNode node) {
        if (node == null || !"group".equals(node.type) || node.goalTrack == null
                || node.goalTrack.trim().isEmpty() || node.month == null || node.month.trim().isEmpty()) {
            return false;
        }
        return "goal-month".equals(node.groupKind) || "current-goals".equals(node.groupKind);
    }

    private void attachRoadmapMonthDropTarget(LinearLayout row, RoadmapNode monthNode) {
        // Multiple child views can receive the same drag sequence. Share a guard so one drop
        // can never move the goal twice, while allowing drops over the title, hint, or + button.
        final boolean[] dropHandled = {false};
        View.OnDragListener listener = (target, event) -> {
            if (!roadmapTreeEditMode || !isRoadmapMonthDropNode(monthNode)) return false;
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    dropHandled[0] = false;
                    return event.getClipDescription() != null
                            && "dayflow-roadmap-goal-id".contentEquals(event.getClipDescription().getLabel());
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    row.setBackground(bg("#DDF3E4", 9));
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                    // Keep the row highlighted while moving between its child views.
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    row.setBackgroundColor(Color.TRANSPARENT);
                    return true;
                case android.view.DragEvent.ACTION_DROP:
                    row.setBackgroundColor(Color.TRANSPARENT);
                    if (dropHandled[0]) return true;
                    dropHandled[0] = true;
                    try {
                        long draggedId = Long.parseLong(
                                event.getClipData().getItemAt(0).getText().toString());
                        moveRoadmapGoalToMonth(draggedId, monthNode);
                    } catch (Exception ignored) {
                        Toast.makeText(MainActivity.this, "Couldn't add that goal to the month.", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                default:
                    return true;
            }
        };
        attachRoadmapMonthDropListenerRecursively(row, listener);
    }

    private void attachRoadmapMonthDropListenerRecursively(View view, View.OnDragListener listener) {
        view.setOnDragListener(listener);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                attachRoadmapMonthDropListenerRecursively(group.getChildAt(i), listener);
            }
        }
    }

    private void moveRoadmapGoalToMonth(long draggedId, RoadmapNode monthNode) {
        if (!roadmapTreeEditMode || !isRoadmapMonthDropNode(monthNode)) return;
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        MonthlyGoal dragged = null;
        for (MonthlyGoal candidate : all) {
            if (candidate.id == draggedId) { dragged = candidate; break; }
        }
        if (dragged == null) {
            Toast.makeText(this, "That goal is no longer available.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean hasCompletedWork = monthlyGoalGroupHasCompletedWork(dragged, all);
        if (!Objects.equals(dragged.track, monthNode.goalTrack)) {
            Toast.makeText(this, "Drop a goal only into the same CSE subject or track.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!RoadmapTreeRules.canDropGoalOnMonth(dragged.track, dragged.parentGoalId,
                dragged.month, hasCompletedWork, monthNode.goalTrack, monthNode.month)) {
            if (hasCompletedWork) {
                Toast.makeText(this, "Completed work stays in place to preserve logical continuity.", Toast.LENGTH_LONG).show();
            } else if (dragged.parentGoalId > 0L) {
                Toast.makeText(this, "A sub-goal must stay with its parent; move the parent goal instead.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "This track's month sequence must stay chronological. Only General Studies root goals can move across months.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        boolean hasOtherActiveGoalsInTargetMonth = false;
        for (MonthlyGoal candidate : all) {
            if (candidate.id == dragged.id || isDescendantOf(all, candidate, dragged.id)
                    || !Objects.equals(candidate.track, monthNode.goalTrack)
                    || !Objects.equals(candidate.month, monthNode.month)
                    || MonthlyGoalLifecycle.RETIRED.equals(candidate.lifecycleStatus)) {
                continue;
            }
            hasOtherActiveGoalsInTargetMonth = true;
            break;
        }
        if (RoadmapTreeRules.shouldMoveDroppedGoalAsRoot(hasOtherActiveGoalsInTargetMonth)) {
            if (Objects.equals(dragged.month, monthNode.month)) {
                Toast.makeText(this, "That goal is already in " + monthLabelForKey(monthNode.month) + ".", Toast.LENGTH_SHORT).show();
                return;
            }
            moveRoadmapGoalToEmptyMonth(dragged, monthNode.month, all);
            return;
        }

        ArrayList<MonthlyGoal> parentCandidates = new ArrayList<>();
        for (MonthlyGoal candidate : all) {
            if (candidate.id == dragged.id || candidate.parentGoalId != 0L
                    || !Objects.equals(candidate.track, monthNode.goalTrack)
                    || !Objects.equals(candidate.month, monthNode.month)
                    || MonthlyGoalLifecycle.RETIRED.equals(candidate.lifecycleStatus)
                    || monthlyGoalGroupHasCompletedWork(candidate, all)) {
                continue;
            }
            parentCandidates.add(candidate);
        }
        parentCandidates.sort(monthlyGoalOrderComparator(all));
        if (parentCandidates.isEmpty()) {
            Toast.makeText(this,
                    "This month already has goals, but none can safely contain another goal because of completed-work rules. Drop onto an eligible goal to swap positions, or choose an empty month.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (parentCandidates.size() == 1) {
            addRoadmapGoalAsSubgoal(dragged.id, parentCandidates.get(0).id, monthNode.month);
            return;
        }

        String[] parentTitles = new String[parentCandidates.size()];
        for (int i = 0; i < parentCandidates.size(); i++) {
            MonthlyGoal candidate = parentCandidates.get(i);
            parentTitles[i] = candidate.title
                    + (MonthlyGoalLifecycle.SPLIT_PARENT.equals(candidate.lifecycleStatus)
                        ? " · has sub-goals" : "");
        }
        final long draggedGoalIdForDialog = dragged.id;
        final String targetMonthForDialog = monthNode.month;
        new AlertDialog.Builder(this)
                .setTitle("Choose a parent goal")
                .setMessage("Add “" + dragged.title + "” as a sub-goal in "
                        + monthLabelForKey(monthNode.month) + ".")
                .setItems(parentTitles, (dialog, which) -> {
                    if (which >= 0 && which < parentCandidates.size()) {
                        addRoadmapGoalAsSubgoal(draggedGoalIdForDialog, parentCandidates.get(which).id, targetMonthForDialog);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void moveRoadmapGoalToEmptyMonth(MonthlyGoal dragged, String targetMonth, ArrayList<MonthlyGoal> all) {
        if (dragged == null || all == null || targetMonth == null) return;
        String sourceMonth = dragged.month;
        if (Objects.equals(sourceMonth, targetMonth)) return;
        rememberRoadmapMonthSlot(dragged.track, sourceMonth);
        rememberRoadmapMonthSlot(dragged.track, targetMonth);
        int nextSortOrder = nextMonthlyGoalSortOrder(all, targetMonth, dragged.track, 0L);
        moveMonthlyGoalGroupToMonth(dragged, targetMonth, all);
        dragged.sortOrder = nextSortOrder;
        recordMonthlyGoalEvent(dragged, "Moved from " + monthLabelForKey(sourceMonth)
                + " to " + monthLabelForKey(targetMonth) + " by dropping onto an empty month");
        selectedGoalMonth = targetMonth;
        persistMonthlyGoals(all);
        // Persist slots again after the goal save, then refresh both projections from the same state.
        rememberRoadmapMonthSlot(dragged.track, sourceMonth);
        rememberRoadmapMonthSlot(dragged.track, targetMonth);
        openMonthlyGoalInRoadmap(dragged);
        revealRoadmapMonthSlotsInTree(dragged.track, sourceMonth, targetMonth);
        Toast.makeText(this, "Goal moved to " + monthLabelForKey(targetMonth)
                + "; the empty source month remains visible and the monthly summary is updated.",
                Toast.LENGTH_LONG).show();
    }

    private void addRoadmapGoalAsSubgoal(long draggedId, long parentId, String targetMonth) {
        if (!roadmapTreeEditMode) return;
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        MonthlyGoal dragged = null;
        MonthlyGoal parent = null;
        for (MonthlyGoal candidate : all) {
            if (candidate.id == draggedId) dragged = candidate;
            if (candidate.id == parentId) parent = candidate;
        }
        if (dragged == null || parent == null) {
            Toast.makeText(this, "That goal is no longer available.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dragged.id == parent.id) {
            Toast.makeText(this, "A goal cannot be its own parent.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (MonthlyGoalLifecycle.RETIRED.equals(dragged.lifecycleStatus)
                || MonthlyGoalLifecycle.RETIRED.equals(parent.lifecycleStatus)) {
            Toast.makeText(this, "Retired goals cannot be used in this move.", Toast.LENGTH_LONG).show();
            return;
        }

        boolean draggedHasCompletedWork = monthlyGoalGroupHasCompletedWork(dragged, all);
        boolean parentHasCompletedWork = monthlyGoalGroupHasCompletedWork(parent, all);
        if (!RoadmapTreeRules.canNestGoalUnderParent(
                dragged.track, dragged.parentGoalId, dragged.month, draggedHasCompletedWork,
                parent.track, parent.parentGoalId, parent.month, parentHasCompletedWork, targetMonth)) {
            if (draggedHasCompletedWork || parentHasCompletedWork) {
                Toast.makeText(this, "Completed work stays in place to preserve logical continuity.", Toast.LENGTH_LONG).show();
            } else if (!Objects.equals(dragged.track, parent.track)) {
                Toast.makeText(this, "A sub-goal must stay within the same CSE subject or track.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "This goal cannot be nested here without breaking its parent or month sequence.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        int nextSortOrder = nextMonthlyGoalSortOrder(all, targetMonth, parent.track, parent.id);
        String previousMonth = dragged.month;
        rememberRoadmapMonthSlot(dragged.track, previousMonth);
        rememberRoadmapMonthSlot(dragged.track, targetMonth);
        if (!Objects.equals(dragged.month, targetMonth)) {
            moveMonthlyGoalGroupToMonth(dragged, targetMonth, all);
        }
        dragged.parentGoalId = parent.id;
        dragged.sortOrder = nextSortOrder;
        if (!MonthlyGoalLifecycle.SPLIT_PARENT.equals(parent.lifecycleStatus)) {
            parent.lifecycleStatus = MonthlyGoalLifecycle.SPLIT_PARENT;
            recordMonthlyGoalEvent(parent, "Became a split parent when “" + dragged.title + "” was added beneath it");
        }
        recordMonthlyGoalEvent(dragged, "Added as a sub-goal under “" + parent.title + "”"
                + (Objects.equals(previousMonth, targetMonth) ? "" : "; moved from "
                    + monthLabelForKey(previousMonth) + " to " + monthLabelForKey(targetMonth)));
        selectedGoalMonth = targetMonth;
        persistMonthlyGoals(all);
        openMonthlyGoalInRoadmap(parent);
        revealRoadmapMonthSlotsInTree(parent.track, previousMonth, targetMonth);
        Toast.makeText(this, "Sub-goal added under “" + parent.title
                + "”; month and roadmap summaries updated.", Toast.LENGTH_LONG).show();
    }

    private void attachRoadmapGoalDropTarget(LinearLayout row, RoadmapNode node) {
        row.setOnDragListener((target, event) -> {
            if (!roadmapTreeEditMode || !isRoadmapGoalNode(node)) return false;
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription() != null
                            && "dayflow-roadmap-goal-id".contentEquals(event.getClipDescription().getLabel());
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    target.setBackground(bg("#DDF3E4", 9));
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    target.setBackgroundColor(Color.TRANSPARENT);
                    return true;
                case android.view.DragEvent.ACTION_DROP:
                    target.setBackgroundColor(Color.TRANSPARENT);
                    try {
                        long draggedId = Long.parseLong(
                                event.getClipData().getItemAt(0).getText().toString());
                        MonthlyGoal dragged = null;
                        MonthlyGoal targetGoal = null;
                        for (MonthlyGoal candidate : readMonthlyGoals()) {
                            if (candidate.id == draggedId) dragged = candidate;
                            if (candidate.id == ((MonthlyGoal) node.item).id) targetGoal = candidate;
                        }
                        if (dragged == null || targetGoal == null) {
                            Toast.makeText(MainActivity.this, "That goal is no longer available.", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        if (dragged.id != targetGoal.id) reorderMonthlyGoalToTarget(dragged, targetGoal);
                        return true;
                    } catch (Exception ignored) {
                        Toast.makeText(MainActivity.this, "Couldn't reorder that goal.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                default:
                    return true;
            }
        });
    }

    private Runnable roadmapNodeChildAction(RoadmapNode node) {
        if (node == null) return null;
        if ("subject".equals(node.type)) {
            if ((node.goalTrack != null && !node.goalTrack.isEmpty())
                    || (node.subjectKey != null && !node.subjectKey.isEmpty())) {
                return () -> showRoadmapSubjectAddMenu(node);
            }
        }
        if (!"group".equals(node.type)) return null;
        if ("current-goals".equals(node.groupKind)) {
            return () -> editMonthlyGoal(null, node.goalTrack, node.month);
        }
        if ("upcoming-goals".equals(node.groupKind) || "past-goals".equals(node.groupKind)) {
            return () -> {
                String targetMonth = node.children.isEmpty()
                        ? selectedGoalMonth : node.children.get(0).month;
                editMonthlyGoal(null, node.goalTrack, targetMonth);
            };
        }
        if ("goal-month".equals(node.groupKind)) {
            return () -> editMonthlyGoal(null, node.goalTrack, node.month);
        }
        if ("topics".equals(node.groupKind)) return () -> editSyllabusTopic(null, node.subjectKey);
        if ("milestones".equals(node.groupKind)) return () -> editRoadmapMilestone(null, node.milestoneTrack);
        return null;
    }

    private void toggleRoadmapLeaf(RoadmapNode node) {
        if (node == null) return;
        if (node.item instanceof MonthlyGoal) {
            toggleMonthlyGoal((MonthlyGoal) node.item);
        } else if (node.item instanceof SyllabusTopic) {
            SyllabusTopic topic = (SyllabusTopic) node.item;
            topic.done = !topic.done;
            topic.completedAt = topic.done ? System.currentTimeMillis() : 0L;
            persistSyllabusTopics(readSyllabusTopicsWith(topic));
        } else if (node.item instanceof RoadmapMilestone) {
            toggleRoadmapMilestone((RoadmapMilestone) node.item);
        }
    }

    private boolean collectRoadmapPath(List<RoadmapNode> nodes, String targetId, List<String> path) {
        if (nodes == null) return false;
        for (RoadmapNode node : nodes) {
            path.add(node.id);
            if (targetId.equals(node.id) || collectRoadmapPath(node.children, targetId, path)) return true;
            path.remove(path.size() - 1);
        }
        return false;
    }

    private void openMonthlyGoalInRoadmap(MonthlyGoal goal) {
        if (goal == null) return;
        selectedGoalMonth = goal.month;
        if (!"syllabus".equals(activePage)) switchPage("syllabus");

        // A selected filter/search could hide this goal, so clear both before revealing its path.
        roadmapActiveFilter = "All";
        roadmapSearchText = "";
        refreshRoadmapFilterStyles();
        if (roadmapSearchInput != null && roadmapSearchInput.getText() != null
                && roadmapSearchInput.getText().length() > 0) {
            roadmapSearchInput.setText("");
        }

        String rootKey = "Other".equals(goal.track) ? "other"
                : "Software Interview Prep".equals(goal.track) ? "software" : "upsc";
        String targetKind = MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus) ? "split-goal" : "goal";
        String targetId = roadmapId(targetKind, rootKey, goal.track, Long.toString(goal.id));
        ArrayList<String> path = new ArrayList<>();
        if (collectRoadmapPath(buildRoadmapTreeNodes(), targetId, path)) {
            expandedRoadmapNodeIds.addAll(path);
            persistRoadmapExpansionState();
        }
        refreshRoadmapSummary();
        if (roadmapTreeContainer != null) {
            roadmapTreeContainer.post(() -> {
                View target = roadmapTreeContainer.findViewWithTag(targetId);
                if (target != null) {
                    target.requestRectangleOnScreen(
                            new android.graphics.Rect(0, 0, target.getWidth(), target.getHeight()), true);
                }
            });
        }
    }

    private void openRoadmapLeaf(RoadmapNode node) {
        if (node == null) return;
        if (node.item instanceof MonthlyGoal) {
            MonthlyGoal goal = (MonthlyGoal) node.item;
            if (MonthlyGoalLifecycle.RETIRED.equals(goal.lifecycleStatus)) showMonthlyGoalActions(goal);
            else if (!MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus)) editMonthlyGoal(goal);
        }
        else if (node.item instanceof SyllabusTopic) editSyllabusTopic((SyllabusTopic) node.item);
        else if (node.item instanceof RoadmapMilestone) editRoadmapMilestone((RoadmapMilestone) node.item);
        else if ("career".equals(node.type)) editCareerGoal();
    }

    private void renderRoadmapFocus() {
        if (roadmapFocusMonthLabel == null) return;
        String month = selectedGoalMonth == null ? monthKeyFromDate(todayKey()) : selectedGoalMonth;
        roadmapFocusMonthLabel.setText(monthLabelForKey(month));
        roadmapFocusMonthLabel.setContentDescription("Choose roadmap goal month, currently "
                + monthLabelForKey(month));
        if (roadmapFocusGoalList == null || roadmapFocusCount == null) return;
        roadmapFocusGoalList.removeAllViews();

        ArrayList<MonthlyGoal> goals = readMonthlyGoals();
        ArrayList<MonthlyGoal> focus = new ArrayList<>();
        for (MonthlyGoal goal : goals) {
            if (month.equals(goal.month) && MonthlyGoalLifecycle.showInMonthlyPanel(goal.lifecycleStatus)) {
                focus.add(goal);
            }
        }
        focus.sort(monthlyGoalOrderComparator(goals));

        int done = 0;
        for (MonthlyGoal goal : focus) if (goal.done) done++;
        roadmapFocusCount.setText(monthLabelForKey(month) + " · " + done + "/" + focus.size() + " complete");

        int overdueCount = unfinishedLeafCountFromEarlierMonths();
        boolean viewingCurrentMonth = month.equals(currentMonthKey());
        if (roadmapOverdueReviewBanner != null) {
            roadmapOverdueReviewBanner.setVisibility(viewingCurrentMonth && overdueCount > 0
                    ? View.VISIBLE : View.GONE);
            if (roadmapOverdueReviewText != null && overdueCount > 0) {
                roadmapOverdueReviewText.setText(overdueCount + " unfinished goal"
                        + (overdueCount == 1 ? "" : "s") + " from earlier months");
            }
        }

        if (focus.isEmpty()) {
            TextView empty = text("No goals planned for this month yet. Add goals from + Add item in the Roadmap tree.",
                    12, color("#687A70"), false);
            empty.setPadding(dp(2), dp(10), dp(2), dp(8));
            roadmapFocusGoalList.addView(empty);
            return;
        }

        for (int i = 0; i < focus.size(); i++) {
            MonthlyGoal goal = focus.get(i);
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(1), dp(5), dp(1), dp(5));

            Button mark = actionButton(goal.done ? "✓" : "○",
                    color(goal.done ? "#F5FBF7" : "#50635A"),
                    goal.done ? "#23845F" : "#FFFFFF");
            mark.setTextSize(12);
            mark.setContentDescription(goal.done ? "Reopen monthly goal" : "Mark monthly goal complete");
            row.addView(mark, new LinearLayout.LayoutParams(dp(30), dp(30)));

            LinearLayout details = vertical();
            LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1f);
            detailsLp.leftMargin = dp(8);
            detailsLp.rightMargin = dp(4);
            row.addView(details, detailsLp);
            boolean softwareTrack = "Software Interview Prep".equals(goal.track);
            details.addView(text(goal.track.toUpperCase(Locale.US), 9,
                    color(softwareTrack ? "#A76821" : "#2E8060"), true));
            TextView goalTitle = text(goal.title, 12,
                    color(goal.done ? "#687A70" : "#18372E"), true);
            goalTitle.setMaxLines(2);
            goalTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (goal.done) goalTitle.setPaintFlags(goalTitle.getPaintFlags()
                    | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            details.addView(goalTitle);
            if (goal.parentGoalId > 0L) {
                TextView subgoalLabel = text("SUBGOAL OF · " + monthlyGoalParentTitle(goals, goal), 9,
                        color("#687A70"), false);
                LinearLayout.LayoutParams subgoalLp = new LinearLayout.LayoutParams(-1, -2);
                subgoalLp.topMargin = dp(1);
                details.addView(subgoalLabel, subgoalLp);
            }
            if (goal.originalMonth != null && !goal.originalMonth.equals(goal.month)) {
                details.addView(text("Originally planned for " + monthLabelForKey(goal.originalMonth),
                        9, color("#687A70"), false));
            }

            Button viewInTree = actionButton("↗", color("#2E8060"), "#F3F7F3");
            viewInTree.setTextSize(13);
            viewInTree.setContentDescription("View " + goal.title + " in roadmap tree");
            LinearLayout.LayoutParams treeLp = new LinearLayout.LayoutParams(dp(30), dp(30));
            treeLp.leftMargin = dp(2);
            row.addView(viewInTree, treeLp);
            viewInTree.setOnClickListener(v -> openMonthlyGoalInRoadmap(goal));

            row.setContentDescription(goal.track + ": " + goal.title
                    + (goal.done ? ". Complete." : ". Open.")
                    + ". Tap to open this goal in the Roadmap tree.");
            row.setOnClickListener(v -> openMonthlyGoalInRoadmap(goal));
            mark.setOnClickListener(v -> toggleMonthlyGoal(goal));
            roadmapFocusGoalList.addView(row);

            if (i < focus.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(color("#E5ECE6"));
                LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
                dividerLp.topMargin = dp(2);
                roadmapFocusGoalList.addView(divider, dividerLp);
            }
        }
    }

    private void refreshRoadmapSummary() {
        ArrayList<SyllabusTopic> topics = readSyllabusTopics();
        int cseTotal = 0, cseDone = 0, interviewTotal = 0, interviewDone = 0;
        for (SyllabusTopic topic : topics) {
            if ("Software Interview Prep".equals(topic.subject)) {
                interviewTotal++;
                if (topic.done) interviewDone++;
            } else {
                cseTotal++;
                if (topic.done) cseDone++;
            }
        }

        if (syllabusCoverageSummary != null) {
            syllabusCoverageSummary.setText("UPSC CSE  " + cseDone + "/" + cseTotal
                    + " topics  ·  Interview prep  " + interviewDone + "/" + interviewTotal);
        }

        ArrayList<RoadmapMilestone> milestones = readRoadmapMilestones();
        Collections.sort(milestones, Comparator.comparing(m -> m.date == null ? "9999-12-31" : m.date));
        RoadmapMilestone next = null;
        for (RoadmapMilestone milestone : milestones) {
            if (!milestone.done) {
                next = milestone;
                break;
            }
        }
        if (syllabusSummaryView != null) {
            if (next == null) {
                syllabusSummaryView.setText("All listed milestones are complete.");
            } else {
                boolean overdue = next.date != null && next.date.compareTo(todayKey()) < 0;
                syllabusSummaryView.setText((overdue ? "OVERDUE  ·  " : "NEXT  ·  ")
                        + next.title + "  ·  " + displayRoadmapDate(next.date));
            }
        }

        if (careerGoalSummary != null) {
            String target = getPrefs().getString(CAREER_TARGET_KEY, "₹2.5–2.75L/month after taxes and deductions");
            careerGoalSummary.setText("Career target  ·  " + target + "    Edit");
        }

        // Keep the selected month and roadmap tree synchronised with the source-of-truth stores.
        if (roadmapFocusMonthLabel != null) renderRoadmapFocus();
        if (roadmapTreeContainer != null) renderRoadmapTreeBody();
        // Roadmap edits also change the Today screen's connected progress label.
        refreshTodaySummaryProgress();
    }

    private void refreshSyllabusRows() {
        if (syllabusList == null) return;
        ArrayList<SyllabusTopic> topics = readSyllabusTopics();
        topics.sort(syllabusTopicOrderComparator());
        syllabusList.removeAllViews();
        String query = syllabusQuery.trim().toLowerCase(Locale.US);
        int shown = 0;
        for (SyllabusTopic topic : topics) {
            if (!"All".equals(syllabusFilter) && !syllabusFilter.equals(topic.subject)) continue;
            if (!query.isEmpty()
                    && !topic.title.toLowerCase(Locale.US).contains(query)
                    && !topic.subject.toLowerCase(Locale.US).contains(query)) continue;
            shown++;

            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(9), dp(2), dp(9));
            row.setBackgroundColor(Color.TRANSPARENT);

            Button toggle = actionButton(topic.done ? "✓" : "○",
                    color(topic.done ? "#102017" : "#A8BAAE"),
                    topic.done ? "#70D6A5" : "#283A30");
            toggle.setContentDescription(topic.done ? "Mark topic pending" : "Mark topic complete");
            row.addView(toggle, new LinearLayout.LayoutParams(dp(34), dp(34)));

            LinearLayout details = vertical();
            details.setPadding(dp(10), 0, dp(2), 0);
            details.addView(text(topic.title, 13, color("#F2F7F3"), true));
            details.addView(text(topic.subject + (topic.done ? "  ·  Complete" : ""),
                    10, color("#9AABA0"), false));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setContentDescription(topic.title + ". Tap to edit; long press for more actions.");
            row.setOnClickListener(v -> editSyllabusTopic(topic));
            row.setOnLongClickListener(v -> {
                showSyllabusTopicActions(topic);
                return true;
            });
            toggle.setOnClickListener(v -> {
                topic.done = !topic.done;
                topic.completedAt = topic.done ? System.currentTimeMillis() : 0L;
                persistSyllabusTopics(readSyllabusTopicsWith(topic));
            });
            syllabusList.addView(row, new LinearLayout.LayoutParams(-1, -2));

            if (shown < topics.size()) {
                View divider = new View(this);
                divider.setBackgroundColor(color("#26372D"));
                syllabusList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        if (shown == 0) {
            LinearLayout empty = vertical();
            empty.setPadding(dp(8), dp(16), dp(8), dp(16));
            empty.addView(text("No topics match.", 14, color("#F2F7F3"), true));
            empty.addView(text("Clear the search or choose another subject.", 12, color("#9AABA0"), false));
            syllabusList.addView(empty);
        }
        refreshRoadmapSummary();
    }

    private String syllabusOverview(ArrayList<SyllabusTopic> topics) {
        int done = 0, cseTotal = 0, cseDone = 0, interviewTotal = 0, interviewDone = 0;
        for (SyllabusTopic topic : topics) {
            boolean interview = "Software Interview Prep".equals(topic.subject);
            if (interview) { interviewTotal++; if (topic.done) interviewDone++; }
            else { cseTotal++; if (topic.done) cseDone++; }
            if (topic.done) done++;
        }
        return "UPSC CSE  " + cseDone + "/" + cseTotal
                + " complete\nSoftware interviews  " + interviewDone + "/" + interviewTotal
                + " complete\nCombined  " + done + "/" + topics.size() + " topics complete";
    }

    private void renderMonthlyGoals() {
        if (monthlyGoalList == null) return;
        monthlyGoalList.removeAllViews();
        ArrayList<MonthlyGoal> goals = readMonthlyGoals();
        ArrayList<MonthlyGoal> monthGoals = new ArrayList<>();
        for (MonthlyGoal goal : goals) {
            if (selectedGoalMonth.equals(goal.month)
                    && MonthlyGoalLifecycle.showInMonthlyPanel(goal.lifecycleStatus)) monthGoals.add(goal);
        }
        monthGoals.sort(monthlyGoalOrderComparator(goals));
        int done = 0;
        for (MonthlyGoal goal : monthGoals) if (goal.done) done++;
        if (goalMonthLabel != null) {
            goalMonthLabel.setText(monthLabelForKey(selectedGoalMonth) + "  ·  " + done + "/" + monthGoals.size());
        }

        if (monthGoals.isEmpty()) {
            TextView empty = text("No active goals for this month. Add goals from the Roadmap tree.",
                    13, color("#9AABA0"), false);
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            monthlyGoalList.addView(empty);
            return;
        }

        for (int i = 0; i < monthGoals.size(); i++) {
            MonthlyGoal goal = monthGoals.get(i);
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(10), dp(2), dp(10));
            row.setBackgroundColor(Color.TRANSPARENT);

            Button mark = actionButton(goal.done ? "✓" : "○",
                    color(goal.done ? "#102017" : "#A8BAAE"),
                    goal.done ? "#70D6A5" : "#283A30");
            mark.setContentDescription(goal.done ? "Reopen goal" : "Mark goal complete");
            row.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));

            LinearLayout details = vertical();
            details.setPadding(dp(10), 0, dp(2), 0);
            int trackColor = "Software Interview Prep".equals(goal.track)
                    ? color("#D5B56E") : color("#65C5A0");
            details.addView(text(goal.track.toUpperCase(Locale.US), 9, trackColor, true));
            TextView title = text(goal.title, 14, color(goal.done ? "#A4B4AA" : "#F2F7F3"), true);
            title.setLineSpacing(dp(1), 1f);
            details.addView(title);
            if (goal.target != null && !goal.target.trim().isEmpty()) {
                TextView target = text(goal.target, 11, color("#9AABA0"), false);
                target.setMaxLines(2);
                target.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(-1, -2);
                targetLp.topMargin = dp(3);
                details.addView(target, targetLp);
            }
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setContentDescription(goal.track + ": " + goal.title + ". Tap to open in the Roadmap tree.");
            row.setOnClickListener(v -> openMonthlyGoalInRoadmap(goal));
            mark.setOnClickListener(v -> toggleMonthlyGoal(goal));
            monthlyGoalList.addView(row, new LinearLayout.LayoutParams(-1, -2));

            if (i < monthGoals.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(color("#26372D"));
                monthlyGoalList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
    }

    private int monthlyGoalTrackOrder(String track) {
        if ("General Studies".equals(track)) return 0;
        if ("Maths Optional".equals(track)) return 1;
        if ("Software Interview Prep".equals(track)) return 2;
        if ("CSE Revision & Tests".equals(track)) return 3;
        return 4;
    }

    private Comparator<MonthlyGoal> monthlyGoalOrderComparator(List<MonthlyGoal> source) {
        final ArrayList<MonthlyGoal> all = new ArrayList<>(source);
        return (left, right) -> {
            int trackOrder = Integer.compare(monthlyGoalTrackOrder(left.track), monthlyGoalTrackOrder(right.track));
            if (trackOrder != 0) return trackOrder;
            int monthOrder = safeGoalMonth(left.month).compareTo(safeGoalMonth(right.month));
            if (monthOrder != 0) return monthOrder;
            ArrayList<MonthlyGoal> leftPath = monthlyGoalPath(all, left);
            ArrayList<MonthlyGoal> rightPath = monthlyGoalPath(all, right);
            int length = Math.min(leftPath.size(), rightPath.size());
            for (int i = 0; i < length; i++) {
                MonthlyGoal l = leftPath.get(i), r = rightPath.get(i);
                int order = Integer.compare(l.sortOrder, r.sortOrder);
                if (order != 0) return order;
                order = Long.compare(l.id, r.id);
                if (order != 0) return order;
            }
            if (leftPath.size() != rightPath.size()) return Integer.compare(leftPath.size(), rightPath.size());
            return Long.compare(left.id, right.id);
        };
    }

    private String safeGoalMonth(String month) {
        return month == null ? "" : month;
    }

    private ArrayList<MonthlyGoal> monthlyGoalPath(List<MonthlyGoal> all, MonthlyGoal goal) {
        ArrayList<MonthlyGoal> path = new ArrayList<>();
        HashSet<Long> visited = new HashSet<>();
        MonthlyGoal current = goal;
        while (current != null && visited.add(current.id)) {
            path.add(current);
            MonthlyGoal parent = null;
            if (current.parentGoalId > 0L) {
                for (MonthlyGoal candidate : all) {
                    if (candidate.id == current.parentGoalId) { parent = candidate; break; }
                }
            }
            current = parent;
        }
        Collections.reverse(path);
        return path;
    }

    private String monthlyGoalParentTitle(List<MonthlyGoal> all, MonthlyGoal goal) {
        if (goal == null || goal.parentGoalId <= 0L) return "larger goal";
        for (MonthlyGoal candidate : all) {
            if (candidate.id == goal.parentGoalId) return candidate.title == null || candidate.title.trim().isEmpty()
                    ? "larger goal" : candidate.title;
        }
        return "larger goal";
    }

    private MonthlyGoal monthlyGoalRoot(List<MonthlyGoal> all, MonthlyGoal goal) {
        MonthlyGoal current = goal;
        HashSet<Long> visited = new HashSet<>();
        while (current.parentGoalId > 0L && visited.add(current.id)) {
            MonthlyGoal parent = null;
            for (MonthlyGoal candidate : all) if (candidate.id == current.parentGoalId) {
                parent = candidate; break;
            }
            if (parent == null) break;
            current = parent;
        }
        return current;
    }

    private int nextMonthlyGoalSortOrder(List<MonthlyGoal> all, String month, String track, long parentId) {
        int maximum = -100;
        for (MonthlyGoal item : all) {
            if (Objects.equals(month, item.month) && Objects.equals(track, item.track)
                    && item.parentGoalId == parentId
                    && !MonthlyGoalLifecycle.RETIRED.equals(item.lifecycleStatus)) {
                maximum = Math.max(maximum, item.sortOrder);
            }
        }
        return maximum + 100;
    }

    private ArrayList<MonthlyGoal> reorderableMonthlyGoalSiblings(MonthlyGoal target,
                                                                    ArrayList<MonthlyGoal> all) {
        ArrayList<MonthlyGoal> siblings = new ArrayList<>();
        boolean canChangeMonth = target.parentGoalId == 0L
                && "General Studies".equals(target.track);
        for (MonthlyGoal item : all) {
            boolean sameGroup = Objects.equals(target.track, item.track)
                    && target.parentGoalId == item.parentGoalId
                    && (canChangeMonth || Objects.equals(target.month, item.month));
            if (sameGroup
                    && (MonthlyGoalLifecycle.showInMonthlyPanel(item.lifecycleStatus)
                        || MonthlyGoalLifecycle.SPLIT_PARENT.equals(item.lifecycleStatus))) {
                // Split parents are structural anchors. Their children move with the parent.
                siblings.add(item);
            }
        }
        Comparator<MonthlyGoal> comparator = Comparator.comparing((MonthlyGoal g) -> g.month == null ? "" : g.month)
                .thenComparingInt(g -> g.sortOrder).thenComparingLong(g -> g.id);
        if (!canChangeMonth) {
            comparator = Comparator.comparingInt((MonthlyGoal g) -> g.sortOrder).thenComparingLong(g -> g.id);
        }
        siblings.sort(comparator);
        return siblings;
    }

    private boolean monthlyGoalGroupHasCompletedWork(MonthlyGoal root, ArrayList<MonthlyGoal> all) {
        if (root.done) return true;
        for (MonthlyGoal candidate : all) {
            if (isDescendantOf(all, candidate, root.id) && candidate.done) return true;
        }
        return false;
    }

    private void moveMonthlyGoalGroupToMonth(MonthlyGoal root, String month, ArrayList<MonthlyGoal> all) {
        root.month = month;
        for (MonthlyGoal candidate : all) {
            if (isDescendantOf(all, candidate, root.id)) {
                candidate.month = month;
                recordMonthlyGoalEvent(candidate, "Moved with parent goal to " + monthLabelForKey(month));
            }
        }
    }

    private void reorderMonthlyGoalToTarget(MonthlyGoal draggedGoal, MonthlyGoal targetGoal) {
        if (draggedGoal == null || targetGoal == null || draggedGoal.id == targetGoal.id) return;
        if (draggedGoal.done || targetGoal.done) {
            Toast.makeText(this, "Completed goals stay in place to preserve the recorded sequence.", Toast.LENGTH_LONG).show();
            return;
        }
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        MonthlyGoal dragged = null, target = null;
        for (MonthlyGoal item : all) {
            if (item.id == draggedGoal.id) dragged = item;
            if (item.id == targetGoal.id) target = item;
        }
        if (dragged == null || target == null) return;
        boolean gsRoots = dragged.parentGoalId == 0L && target.parentGoalId == 0L
                && "General Studies".equals(dragged.track) && "General Studies".equals(target.track);
        if (!RoadmapTreeRules.canReorderGoals(dragged.track, dragged.parentGoalId, dragged.month, dragged.done,
                target.track, target.parentGoalId, target.month, target.done)) {
            Toast.makeText(this, "Drop goals within the same parent and sequence.", Toast.LENGTH_LONG).show();
            return;
        }
        ArrayList<MonthlyGoal> siblings = reorderableMonthlyGoalSiblings(dragged, all);
        int from = -1, to = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).id == dragged.id) from = i;
            if (siblings.get(i).id == target.id) to = i;
        }
        if (from < 0 || to < 0) return;
        int low = Math.min(from, to), high = Math.max(from, to);
        for (int i = low; i <= high; i++) {
            if (siblings.get(i).id != dragged.id && monthlyGoalGroupHasCompletedWork(siblings.get(i), all)) {
                Toast.makeText(this, "That move would cross completed work, so the sequence was left unchanged.", Toast.LENGTH_LONG).show();
                return;
            }
        }
        ArrayList<String> monthSlots = new ArrayList<>();
        if (gsRoots) for (MonthlyGoal sibling : siblings) monthSlots.add(sibling.month);
        Collections.swap(siblings, from, to);
        for (int i = 0; i < siblings.size(); i++) {
            MonthlyGoal item = siblings.get(i);
            if (gsRoots) {
                String nextMonth = monthSlots.get(i);
                if (!Objects.equals(item.month, nextMonth)) moveMonthlyGoalGroupToMonth(item, nextMonth, all);
            }
            item.sortOrder = (i + 1) * 100;
            recordMonthlyGoalEvent(item, "Plan order updated by drag and drop");
        }
        persistMonthlyGoals(all);
        refreshRoadmapTabContent();
        Toast.makeText(this, "Goal slot swapped; monthly summaries updated.", Toast.LENGTH_SHORT).show();
    }

    private boolean canMoveMonthlyGoal(MonthlyGoal target, int delta) {
        if (target == null || target.done || delta == 0) return false;
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        ArrayList<MonthlyGoal> siblings = reorderableMonthlyGoalSiblings(target, all);
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).id == target.id) {
                int next = i + delta;
                if (next < 0 || next >= siblings.size()) return false;
                MonthlyGoal other = siblings.get(next);
                return !monthlyGoalGroupHasCompletedWork(other, all);
            }
        }
        return false;
    }

    private void moveMonthlyGoal(MonthlyGoal target, int delta) {
        if (target == null || target.done || delta == 0) return;
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        MonthlyGoal stored = null;
        for (MonthlyGoal item : all) if (item.id == target.id) { stored = item; break; }
        if (stored == null || stored.done) return;
        ArrayList<MonthlyGoal> siblings = reorderableMonthlyGoalSiblings(stored, all);
        int at = -1;
        for (int i = 0; i < siblings.size(); i++) if (siblings.get(i).id == stored.id) { at = i; break; }
        int next = at + delta;
        if (at < 0 || next < 0 || next >= siblings.size()) {
            Toast.makeText(this, "This goal is already at the edge of its group.", Toast.LENGTH_SHORT).show();
            return;
        }
        MonthlyGoal other = siblings.get(next);
        if (monthlyGoalGroupHasCompletedWork(other, all)) {
            Toast.makeText(this, "Completed goals stay in place to preserve the recorded sequence.", Toast.LENGTH_LONG).show();
            return;
        }
        String oldMonth = stored.month;
        String otherMonth = other.month;
        boolean movesAcrossMonths = !Objects.equals(oldMonth, otherMonth);
        if (movesAcrossMonths) {
            // Only General Studies root goals can cross month slots. Maths Optional and
            // interview-prep tracks remain chronological to preserve cumulative learning.
            moveMonthlyGoalGroupToMonth(stored, otherMonth, all);
            moveMonthlyGoalGroupToMonth(other, oldMonth, all);
        }
        int order = stored.sortOrder;
        stored.sortOrder = other.sortOrder;
        other.sortOrder = order;
        recordMonthlyGoalEvent(stored, delta < 0 ? "Moved earlier in plan" : "Moved later in plan");
        recordMonthlyGoalEvent(other, delta < 0 ? "Moved later in plan" : "Moved earlier in plan");
        persistMonthlyGoals(all);
        refreshRoadmapTabContent();
        Toast.makeText(this, movesAcrossMonths
                ? "General Studies plan updated; month summaries refreshed."
                : "Order saved within this month and goal group.", Toast.LENGTH_SHORT).show();
    }

    private Comparator<SyllabusTopic> syllabusTopicOrderComparator() {
        return Comparator.comparingInt((SyllabusTopic t) -> syllabusSubjectOrder(t.subject))
                .thenComparingInt(t -> t.sortOrder)
                .thenComparing(t -> t.title == null ? "" : t.title, String.CASE_INSENSITIVE_ORDER);
    }

    private int syllabusSubjectOrder(String subject) {
        for (int i = 0; i < SYLLABUS_SUBJECTS.length; i++) {
            if (Objects.equals(SYLLABUS_SUBJECTS[i], subject)) return i;
        }
        return SYLLABUS_SUBJECTS.length;
    }

    private int nextSyllabusTopicSortOrder(List<SyllabusTopic> topics, String subject) {
        int maximum = -100;
        for (SyllabusTopic topic : topics) if (Objects.equals(subject, topic.subject)) {
            maximum = Math.max(maximum, topic.sortOrder);
        }
        return maximum + 100;
    }

    private void moveSyllabusTopic(SyllabusTopic target, int delta) {
        if (target == null || delta == 0) return;
        ArrayList<SyllabusTopic> all = readSyllabusTopics();
        SyllabusTopic stored = null;
        for (SyllabusTopic topic : all) if (topic.id == target.id) { stored = topic; break; }
        if (stored == null) return;
        ArrayList<SyllabusTopic> siblings = new ArrayList<>();
        for (SyllabusTopic topic : all) if (Objects.equals(stored.subject, topic.subject)) siblings.add(topic);
        siblings.sort(Comparator.comparingInt((SyllabusTopic t) -> t.sortOrder)
                .thenComparing(t -> t.title == null ? "" : t.title, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(t -> t.id));
        int at = -1;
        for (int i = 0; i < siblings.size(); i++) if (siblings.get(i).id == stored.id) { at = i; break; }
        int next = at + delta;
        if (at < 0 || next < 0 || next >= siblings.size()) {
            Toast.makeText(this, "This topic is already at the edge of its subject.", Toast.LENGTH_SHORT).show();
            return;
        }
        SyllabusTopic other = siblings.get(next);
        int order = stored.sortOrder;
        stored.sortOrder = other.sortOrder;
        other.sortOrder = order;
        persistSyllabusTopics(all);
        Toast.makeText(this, "Topic order saved within " + stored.subject + ".", Toast.LENGTH_SHORT).show();
    }

    private void changeSelectedGoalMonth(int offset) {
        if (selectedGoalMonth == null) selectedGoalMonth = monthKeyFromDate(todayKey());
        Date date = parseDateKey(selectedGoalMonth + "-01");
        Calendar c = Calendar.getInstance();
        if (date != null) c.setTime(date);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, offset);
        selectedGoalMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(c.getTime());
        refreshRoadmapSummary();
        renderMonthlyGoals();
    }

    private void chooseGoalMonth() {
        Date date = parseDateKey((selectedGoalMonth == null ? monthKeyFromDate(todayKey()) : selectedGoalMonth) + "-01");
        Calendar c = Calendar.getInstance();
        if (date != null) c.setTime(date);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, 1, 0, 0, 0);
            selectedGoalMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(chosen.getTime());
            renderMonthlyGoals();
            refreshRoadmapSummary();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        picker.setTitle("Choose goal month");
        picker.show();
    }

    private void editMonthlyGoal(MonthlyGoal existing) {
        editMonthlyGoal(existing, null, null);
    }

    private void editMonthlyGoal(MonthlyGoal existing, String preferredTrack) {
        editMonthlyGoal(existing, preferredTrack, null);
    }

    private void editMonthlyGoal(MonthlyGoal existing, String preferredTrack, String preferredMonth) {
        final String[] draftMonth = {existing == null
                ? (preferredMonth != null ? preferredMonth
                    : (selectedGoalMonth == null ? monthKeyFromDate(todayKey()) : selectedGoalMonth))
                : existing.month};
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(10));
        TextView help = text("Change the month, track, goal and success criteria. Use ↑/↓ to reorder General Studies topics across month slots; Maths Optional and interview-prep month sequences stay in order. These changes are saved on this device.",
                12, color("#687A70"), false);
        help.setLineSpacing(dp(2), 1f);
        form.addView(help);

        final Button[] monthButtonRef = new Button[1];
        monthButtonRef[0] = secondaryButton(monthLabelForKey(draftMonth[0]), () -> {
            Date parsed = parseDateKey(draftMonth[0] + "-01");
            Calendar initial = Calendar.getInstance();
            if (parsed != null) initial.setTime(parsed);
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, monthOfYear, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, monthOfYear, 1, 0, 0, 0);
                draftMonth[0] = new SimpleDateFormat("yyyy-MM", Locale.US).format(chosen.getTime());
                monthButtonRef[0].setText(monthLabelForKey(draftMonth[0]));
            }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
            picker.setTitle("Goal month");
            picker.show();
        });
        Button month = monthButtonRef[0];
        LinearLayout.LayoutParams monthLp = new LinearLayout.LayoutParams(-1, dp(44));
        monthLp.topMargin = dp(12);
        form.addView(month, monthLp);

        form.addView(text("TRACK", 10, color("#687A70"), true));
        Spinner track = new Spinner(this);
        track.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MONTHLY_GOAL_TRACKS));
        if (existing != null) {
            for (int i = 0; i < MONTHLY_GOAL_TRACKS.length; i++) {
                if (MONTHLY_GOAL_TRACKS[i].equals(existing.track)) track.setSelection(i);
            }
        } else if (preferredTrack != null) {
            for (int i = 0; i < MONTHLY_GOAL_TRACKS.length; i++) {
                if (MONTHLY_GOAL_TRACKS[i].equals(preferredTrack)) track.setSelection(i);
            }
        }
        form.addView(track, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView titleLabel = text("GOAL", 10, color("#687A70"), true);
        LinearLayout.LayoutParams titleLabelLp = new LinearLayout.LayoutParams(-1, -2);
        titleLabelLp.topMargin = dp(10);
        form.addView(titleLabel, titleLabelLp);
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("What should be achieved this month?");
        title.setTextColor(color("#18372E"));
        title.setHintTextColor(color("#82948A"));
        title.setPadding(dp(12), dp(5), dp(12), dp(5));
        title.setBackground(bg("#FFFFFF", 12));
        if (existing != null) title.setText(existing.title);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView targetLabel = text("SUCCESS CRITERIA / TARGET", 10, color("#687A70"), true);
        LinearLayout.LayoutParams targetLabelLp = new LinearLayout.LayoutParams(-1, -2);
        targetLabelLp.topMargin = dp(10);
        form.addView(targetLabel, targetLabelLp);
        EditText target = new EditText(this);
        target.setGravity(Gravity.TOP | Gravity.START);
        target.setMinLines(3);
        target.setHint("What does 'done' look like?");
        target.setTextColor(color("#18372E"));
        target.setHintTextColor(color("#82948A"));
        target.setPadding(dp(12), dp(10), dp(12), dp(10));
        target.setBackground(bg("#FFFFFF", 12));
        target.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null && existing.target != null) target.setText(existing.target);
        LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(-1, dp(110));
        targetLp.topMargin = dp(4);
        form.addView(target, targetLp);

        TextView detailFieldsHeading = text("SOURCE, PAGES & ESTIMATED EFFORT", 11, color("#2E8060"), true);
        LinearLayout.LayoutParams detailFieldsHeadingLp = new LinearLayout.LayoutParams(-1, -2);
        detailFieldsHeadingLp.topMargin = dp(14);
        detailFieldsHeadingLp.bottomMargin = dp(4);
        form.addView(detailFieldsHeading, detailFieldsHeadingLp);
        TextView detailFieldsHelp = text("These fields appear directly beneath the goal in the Roadmap Tree. Estimates are study effort, not a guaranteed finish date.", 11, color("#687A70"), false);
        detailFieldsHelp.setLineSpacing(dp(1), 1f);
        form.addView(detailFieldsHelp);

        TextView sourceLabel = text("SOURCE", 10, color("#687A70"), true);
        LinearLayout.LayoutParams sourceLabelLp = new LinearLayout.LayoutParams(-1, -2);
        sourceLabelLp.topMargin = dp(10);
        form.addView(sourceLabel, sourceLabelLp);
        EditText source = new EditText(this);
        source.setSingleLine(true);
        source.setHint("e.g. Vajiram Yellow Books Polity I + II");
        source.setTextColor(color("#18372E"));
        source.setHintTextColor(color("#82948A"));
        source.setPadding(dp(12), dp(5), dp(12), dp(5));
        source.setBackground(bg("#FFFFFF", 12));
        String initialSource = existing == null ? "" : goalInfoValue(existing.source, inferGoalSource(existing.target, existing.track));
        if (!initialSource.isEmpty()) source.setText(initialSource);
        form.addView(source, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView pagesLabel = text("PAGES / SCOPE", 10, color("#687A70"), true);
        LinearLayout.LayoutParams pagesLabelLp = new LinearLayout.LayoutParams(-1, -2);
        pagesLabelLp.topMargin = dp(8);
        form.addView(pagesLabel, pagesLabelLp);
        EditText pages = new EditText(this);
        pages.setSingleLine(true);
        pages.setHint("e.g. ≈584 pages or Not page-based");
        pages.setTextColor(color("#18372E"));
        pages.setHintTextColor(color("#82948A"));
        pages.setPadding(dp(12), dp(5), dp(12), dp(5));
        pages.setBackground(bg("#FFFFFF", 12));
        String initialPages = existing == null ? "" : goalInfoValue(existing.pages, inferGoalPages(existing.target, existing.track));
        if (!initialPages.isEmpty()) pages.setText(initialPages);
        form.addView(pages, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView estimateLabel = text("ESTIMATED TIME TO COMPLETE", 10, color("#687A70"), true);
        LinearLayout.LayoutParams estimateLabelLp = new LinearLayout.LayoutParams(-1, -2);
        estimateLabelLp.topMargin = dp(8);
        form.addView(estimateLabel, estimateLabelLp);
        EditText estimatedTime = new EditText(this);
        estimatedTime.setSingleLine(true);
        estimatedTime.setHint("e.g. 60–68 study hours");
        estimatedTime.setTextColor(color("#18372E"));
        estimatedTime.setHintTextColor(color("#82948A"));
        estimatedTime.setPadding(dp(12), dp(5), dp(12), dp(5));
        estimatedTime.setBackground(bg("#FFFFFF", 12));
        String initialEstimate = existing == null ? "" : goalInfoValue(existing.estimatedTime, inferGoalEstimatedTime(existing.target, existing.track));
        if (!initialEstimate.isEmpty()) estimatedTime.setText(initialEstimate);
        form.addView(estimatedTime, new LinearLayout.LayoutParams(-1, dp(46)));

        showFullScreenEditor("MONTHLY GOALS", existing == null ? "Add monthly goal" : "Edit monthly goal",
                "Keep the goal specific enough that you can honestly mark it complete.", form, () -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        title.setError("Enter a goal");
                        title.requestFocus();
                        return false;
                    }
                    ArrayList<MonthlyGoal> all = readMonthlyGoals();
                    MonthlyGoal updated = new MonthlyGoal(existing == null ? System.currentTimeMillis() : existing.id,
                            draftMonth[0], track.getSelectedItem().toString(), name,
                            target.getText().toString().trim(), existing != null && existing.done);
                    updated.source = source.getText().toString().trim();
                    if (updated.source.isEmpty()) updated.source = inferGoalSource(updated.target, updated.track);
                    updated.pages = pages.getText().toString().trim();
                    if (updated.pages.isEmpty()) updated.pages = inferGoalPages(updated.target, updated.track);
                    updated.estimatedTime = estimatedTime.getText().toString().trim();
                    if (updated.estimatedTime.isEmpty()) updated.estimatedTime = inferGoalEstimatedTime(updated.target, updated.track);
                    if (existing != null) {
                        updated.originalMonth = existing.originalMonth == null ? existing.month : existing.originalMonth;
                        updated.completedAt = existing.completedAt;
                        updated.parentGoalId = existing.parentGoalId;
                        updated.lifecycleStatus = existing.lifecycleStatus;
                        updated.history.addAll(existing.history);
                        if (!Objects.equals(existing.month, draftMonth[0])) {
                            recordMonthlyGoalEvent(updated,
                                    "Scheduled month changed from " + monthLabelForKey(existing.month)
                                            + " to " + monthLabelForKey(draftMonth[0]));
                            if (updated.parentGoalId > 0L) {
                                updated.parentGoalId = 0L;
                                recordMonthlyGoalEvent(updated, "Detached from its split-goal group");
                            }
                        }
                    } else {
                        recordMonthlyGoalEvent(updated, "Created");
                    }
                    boolean replaced = false;
                    for (int i = 0; i < all.size(); i++) {
                        if (existing != null && all.get(i).id == existing.id) {
                            all.set(i, updated);
                            replaced = true;
                            break;
                        }
                    }
                    if (!replaced) all.add(updated);
                    selectedGoalMonth = draftMonth[0];
                    persistMonthlyGoals(all);
                    refreshRoadmapTabContent();
                    return true;
                });
    }

    private void toggleMonthlyGoal(MonthlyGoal goal) {
        if (goal == null) return;
        String scheduledMonth = goal.month;
        String completedTrack = goal.track;
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        boolean found = false, completed = false;
        for (MonthlyGoal item : all) {
            if (item.id == goal.id) {
                item.done = !item.done;
                completed = item.done;
                item.lifecycleStatus = item.done ? MonthlyGoalLifecycle.COMPLETED : MonthlyGoalLifecycle.ACTIVE;
                item.completedAt = item.done ? System.currentTimeMillis() : 0L;
                recordMonthlyGoalEvent(item, item.done ? "Completed" : "Reopened");
                found = true;
                break;
            }
        }
        if (!found) return;
        persistMonthlyGoals(all);
        refreshRoadmapTabContent();
        Toast.makeText(this, completed ? "Goal completed · progress updated" : "Goal reopened · progress updated",
                Toast.LENGTH_SHORT).show();
        if (completed) maybeOfferEarlyCompletion(scheduledMonth, completedTrack);
    }

        private void recordMonthlyGoalEvent(MonthlyGoal goal, String action) {
        if (goal == null) return;
        goal.history.add(System.currentTimeMillis() + "|" + action + "|" + (goal.month == null ? "" : goal.month));
        while (goal.history.size() > 80) goal.history.remove(0);
    }

    private String monthlyGoalHistoryText(MonthlyGoal goal) {
        StringBuilder out = new StringBuilder();
        String original = goal.originalMonth == null ? goal.month : goal.originalMonth;
        out.append("Originally planned: ").append(monthLabelForKey(original));
        if (!Objects.equals(original, goal.month)) out.append("\nCurrently scheduled: ").append(monthLabelForKey(goal.month));
        if (goal.completedAt > 0L) {
            out.append("\nLast completed: ").append(new SimpleDateFormat("dd MMM yyyy, HH:mm",
                    Locale.getDefault()).format(new Date(goal.completedAt)));
        }
        if (!goal.history.isEmpty()) {
            out.append("\n\nACTIVITY");
            int start = Math.max(0, goal.history.size() - 12);
            for (int i = goal.history.size() - 1; i >= start; i--) {
                String[] parts = goal.history.get(i).split("\\|", 3);
                out.append("\n• ");
                try {
                    out.append(new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                            .format(new Date(Long.parseLong(parts[0]))));
                } catch (Exception ignored) { out.append("Recorded"); }
                if (parts.length > 1) out.append(" · ").append(parts[1]);
                if (parts.length > 2 && !parts[2].isEmpty()) out.append(" (").append(monthLabelForKey(parts[2])).append(")");
            }
        } else {
            out.append("\n\nNo recorded activity yet. Earlier completion dates weren't stored by older versions.");
        }
        return out.toString();
    }

    private void showMonthlyGoalHistory(MonthlyGoal goal) {
        new AlertDialog.Builder(this).setTitle("Goal history")
                .setMessage(monthlyGoalHistoryText(goal)).setPositiveButton("Close", null).show();
    }

    private void showMonthlyGoalActions(MonthlyGoal goal) {
        if (goal == null) return;
        if (MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus)) {
            new AlertDialog.Builder(this).setTitle(goal.title)
                    .setItems(new String[]{"View history", "Delete goal group"}, (dialog, which) -> {
                        if (which == 0) showMonthlyGoalHistory(goal);
                        else confirmDeleteMonthlyGoal(goal);
                    }).setNegativeButton("Cancel", null).show();
            return;
        }
        ArrayList<String> options = new ArrayList<>();
        options.add("Edit goal"); options.add("View history");
        if (MonthlyGoalLifecycle.RETIRED.equals(goal.lifecycleStatus)) options.add("Restore to active plan");
        options.add("Delete goal");
        new AlertDialog.Builder(this).setTitle(goal.title)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String action = options.get(which);
                    if ("Edit goal".equals(action)) editMonthlyGoal(goal);
                    else if ("View history".equals(action)) showMonthlyGoalHistory(goal);
                    else if ("Restore to active plan".equals(action)) restoreMonthlyGoal(goal);
                    else if ("Delete goal".equals(action)) confirmDeleteMonthlyGoal(goal);
                }).setNegativeButton("Cancel", null).show();
    }

    private void restoreMonthlyGoal(MonthlyGoal goal) {
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        boolean hasDescendants = false;
        for (MonthlyGoal item : all) if (isDescendantOf(all, item, goal.id)) { hasDescendants = true; break; }
        for (MonthlyGoal item : all) {
            if (item.id != goal.id && !isDescendantOf(all, item, goal.id)) continue;
            boolean hasChildren = false;
            for (MonthlyGoal child : all) if (child.parentGoalId == item.id) { hasChildren = true; break; }
            item.lifecycleStatus = hasChildren ? MonthlyGoalLifecycle.SPLIT_PARENT : MonthlyGoalLifecycle.ACTIVE;
            item.done = false;
            item.completedAt = 0L;
            recordMonthlyGoalEvent(item, "Restored to active plan");
        }
        persistMonthlyGoals(all);
        Toast.makeText(this, hasDescendants
                ? "Goal group and its smaller goals restored." : "Goal restored.", Toast.LENGTH_SHORT).show();
    }

    private String currentMonthKey() { return monthKeyFromDate(todayKey()); }

    private ArrayList<MonthlyGoal> unfinishedGoalsFromEarlierMonths() {
        String currentMonth = currentMonthKey();
        ArrayList<MonthlyGoal> missed = new ArrayList<>();
        for (MonthlyGoal goal : readMonthlyGoals()) {
            if (MonthlyGoalLifecycle.isPastUnfinished(goal.lifecycleStatus, goal.done, goal.month, currentMonth)) missed.add(goal);
        }
        return missed;
    }

    private ArrayList<MonthlyGoal> goalsToReview() {
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        LinkedHashMap<Long, MonthlyGoal> review = new LinkedHashMap<>();
        for (MonthlyGoal goal : unfinishedGoalsFromEarlierMonths()) {
            if (goal.parentGoalId > 0L) {
                MonthlyGoal parent = null;
                for (MonthlyGoal candidate : all) if (candidate.id == goal.parentGoalId) parent = candidate;
                if (parent != null && MonthlyGoalLifecycle.SPLIT_PARENT.equals(parent.lifecycleStatus)) {
                    review.put(parent.id, parent); continue;
                }
            }
            review.put(goal.id, goal);
        }
        ArrayList<MonthlyGoal> result = new ArrayList<>(review.values());
        result.sort(Comparator.comparing((MonthlyGoal g) -> g.month == null ? "" : g.month)
                .thenComparing(g -> g.track == null ? "" : g.track)
                .thenComparing(g -> g.title == null ? "" : g.title, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private int unfinishedLeafCountFromEarlierMonths() { return unfinishedGoalsFromEarlierMonths().size(); }

    private void showUnfinishedGoalReview() {
        ArrayList<MonthlyGoal> items = goalsToReview();
        if (items.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Monthly review")
                    .setMessage("No unfinished goals from earlier months. Your plan is up to date.")
                    .setPositiveButton("Done", null).show();
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            MonthlyGoal goal = items.get(i);
            labels[i] = goal.track + " · " + goal.title + " · " + monthLabelForKey(goal.month)
                    + (MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus) ? " (goal group)" : "");
        }
        new AlertDialog.Builder(this).setTitle("Review unfinished goals")
                .setMessage("Choose a goal to decide what happens next. The original month and activity history are preserved.")
                .setItems(labels, (dialog, which) -> showUnfinishedGoalActions(items.get(which)))
                .setNegativeButton("Close", null).show();
    }

    private void showUnfinishedGoalActions(MonthlyGoal goal) {
        boolean splitGroup = MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus);
        ArrayList<String> options = new ArrayList<>();
        options.add("Carry forward to " + monthLabelForKey(currentMonthKey()));
        options.add("Reschedule to another month");
        if (!splitGroup) options.add("Split into two smaller goals");
        options.add("Defer to a later month");
        options.add("Retire this goal");
        options.add("View goal history");
        new AlertDialog.Builder(this).setTitle(goal.title)
                .setMessage("Original plan: " + monthLabelForKey(goal.originalMonth == null ? goal.month : goal.originalMonth)
                        + (splitGroup ? "\nRemaining parts will move together." : "")
                        + "\nChoose how to handle the unfinished work.")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String action = options.get(which);
                    if (action.startsWith("Carry forward")) rescheduleMonthlyGoal(goal, currentMonthKey(), "Carried forward");
                    else if ("Reschedule to another month".equals(action)) chooseMonthlyGoalMonth(goal, "Rescheduled", false);
                    else if ("Split into two smaller goals".equals(action)) showSplitMonthlyGoalDialog(goal);
                    else if ("Defer to a later month".equals(action)) chooseMonthlyGoalMonth(goal, "Deferred", true);
                    else if ("Retire this goal".equals(action)) retireMonthlyGoal(goal);
                    else if ("View goal history".equals(action)) showMonthlyGoalHistory(goal);
                }).show();
    }

    private void chooseMonthlyGoalMonth(MonthlyGoal goal, String action, boolean futureOnly) {
        Calendar initial = Calendar.getInstance();
        Date current = parseDateKey(currentMonthKey() + "-01");
        if (current != null) initial.setTime(current);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance(); chosen.set(year, month, 1, 0, 0, 0);
            String newMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(chosen.getTime());
            if (futureOnly && newMonth.compareTo(currentMonthKey()) <= 0) {
                Toast.makeText(this, "Choose a month after " + monthLabelForKey(currentMonthKey()), Toast.LENGTH_LONG).show();
                return;
            }
            rescheduleMonthlyGoal(goal, newMonth, action);
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), 1);
        picker.setTitle(futureOnly ? "Defer until which month?" : "Reschedule goal");
        picker.show();
    }

    private void rescheduleMonthlyGoal(MonthlyGoal goal, String newMonth, String action) {
        if (goal == null || newMonth == null || newMonth.trim().isEmpty()) return;
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        MonthlyGoal stored = null;
        for (MonthlyGoal item : all) if (item.id == goal.id) stored = item;
        if (stored == null) return;
        String oldMonth = stored.month;
        boolean splitGroup = MonthlyGoalLifecycle.SPLIT_PARENT.equals(stored.lifecycleStatus);
        if (!Objects.equals(oldMonth, newMonth)) {
            stored.sortOrder = nextMonthlyGoalSortOrder(all, newMonth, stored.track, 0L);
        }
        if (!splitGroup && stored.parentGoalId > 0L) {
            stored.parentGoalId = 0L;
            stored.sortOrder = nextMonthlyGoalSortOrder(all, newMonth, stored.track, 0L);
            recordMonthlyGoalEvent(stored, "Detached from split goal before rescheduling");
        }
        stored.month = newMonth;
        stored.lifecycleStatus = splitGroup ? MonthlyGoalLifecycle.SPLIT_PARENT : MonthlyGoalLifecycle.ACTIVE;
        stored.done = false; stored.completedAt = 0L;
        recordMonthlyGoalEvent(stored, action + " from " + monthLabelForKey(oldMonth) + " to " + monthLabelForKey(newMonth));
        if (splitGroup) for (MonthlyGoal child : all) if (isDescendantOf(all, child, goal.id)) {
            String oldChildMonth = child.month; child.month = newMonth;
            recordMonthlyGoalEvent(child, action + " with parent goal from "
                    + monthLabelForKey(oldChildMonth) + " to " + monthLabelForKey(newMonth));
        }
        persistMonthlyGoals(all); refreshRoadmapTabContent();
        Toast.makeText(this, action + " · original plan retained", Toast.LENGTH_SHORT).show();
    }

    private boolean isDescendantOf(ArrayList<MonthlyGoal> all, MonthlyGoal candidate, long parentId) {
        long id = candidate.parentGoalId; HashSet<Long> visited = new HashSet<>();
        while (id > 0L && visited.add(id)) {
            if (id == parentId) return true;
            long next = 0L; for (MonthlyGoal item : all) if (item.id == id) next = item.parentGoalId;
            id = next;
        }
        return false;
    }

    private void retireMonthlyGoal(MonthlyGoal goal) {
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        for (MonthlyGoal item : all) if (item.id == goal.id || isDescendantOf(all, item, goal.id)) {
            item.done = false; item.completedAt = 0L; item.lifecycleStatus = MonthlyGoalLifecycle.RETIRED;
            recordMonthlyGoalEvent(item, "Retired");
        }
        persistMonthlyGoals(all);
        Toast.makeText(this, "Goal archived · excluded from active progress", Toast.LENGTH_LONG).show();
    }

    private void showSplitMonthlyGoalDialog(MonthlyGoal goal) {
        if (goal == null || MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus)) return;
        LinearLayout form = vertical(); form.setPadding(dp(20), dp(8), dp(20), dp(4));
        form.addView(text("Replace this broad target with two smaller, measurable goals. The original target and history remain available.",
                12, color("#687A70"), false));
        EditText first = new EditText(this); first.setSingleLine(true); first.setHint("Part 1 · specific outcome");
        EditText firstTarget = new EditText(this); firstTarget.setSingleLine(true); firstTarget.setHint("Success criteria for part 1 (optional)");
        EditText second = new EditText(this); second.setSingleLine(true); second.setHint("Part 2 · specific outcome");
        EditText secondTarget = new EditText(this); secondTarget.setSingleLine(true); secondTarget.setHint("Success criteria for part 2 (optional)");
        for (EditText input : new EditText[]{first, firstTarget, second, secondTarget}) {
            input.setTextColor(color("#18372E")); input.setHintTextColor(color("#82948A"));
            input.setPadding(dp(10), dp(5), dp(10), dp(5)); input.setBackground(bg("#FFFFFF", 10));
            form.addView(input, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Split goal").setView(form)
                .setPositiveButton("Split goal", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = first.getText().toString().trim(), b = second.getText().toString().trim();
            if (a.isEmpty() || b.isEmpty() || a.equalsIgnoreCase(b)) {
                Toast.makeText(this, "Enter two different goal titles.", Toast.LENGTH_SHORT).show(); return;
            }
            ArrayList<MonthlyGoal> all = readMonthlyGoals();
            MonthlyGoal parent = null; for (MonthlyGoal item : all) if (item.id == goal.id) parent = item;
            if (parent == null) return;
            String oldMonth = parent.month, schedule = currentMonthKey();
            if (!Objects.equals(parent.month, schedule)) {
                parent.sortOrder = nextMonthlyGoalSortOrder(all, schedule, parent.track, 0L);
            }
            parent.month = schedule; parent.done = false; parent.completedAt = 0L;
            parent.lifecycleStatus = MonthlyGoalLifecycle.SPLIT_PARENT;
            recordMonthlyGoalEvent(parent, "Split from " + monthLabelForKey(oldMonth) + " into two smaller goals");
            long nextId = System.currentTimeMillis(); for (MonthlyGoal item : all) nextId = Math.max(nextId, item.id + 1L);
            MonthlyGoal one = new MonthlyGoal(nextId++, schedule, parent.track, a, firstTarget.getText().toString().trim(), false);
            one.parentGoalId = parent.id;
            one.sortOrder = nextMonthlyGoalSortOrder(all, schedule, parent.track, parent.id);
            recordMonthlyGoalEvent(one, "Created by splitting " + parent.title);
            MonthlyGoal two = new MonthlyGoal(nextId, schedule, parent.track, b, secondTarget.getText().toString().trim(), false);
            two.parentGoalId = parent.id;
            two.sortOrder = one.sortOrder + 100;
            recordMonthlyGoalEvent(two, "Created by splitting " + parent.title);
            all.add(one); all.add(two); persistMonthlyGoals(all);
            selectedGoalMonth = schedule; refreshRoadmapTabContent(); dialog.dismiss();
            Toast.makeText(this, "Goal split into two smaller tasks.", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void maybeOfferEarlyCompletion(String completedMonth, String track) {
        if (track == null || !currentMonthKey().equals(completedMonth)) return;
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_MONTH), days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        int total = 0, unfinished = 0;
        for (MonthlyGoal goal : readMonthlyGoals()) {
            if (!completedMonth.equals(goal.month) || !track.equals(goal.track)
                    || !MonthlyGoalLifecycle.countsAsPlannedGoal(goal.lifecycleStatus)) continue;
            total++; if (!goal.done && MonthlyGoalLifecycle.ACTIVE.equals(goal.lifecycleStatus)) unfinished++;
        }
        if (!MonthlyGoalLifecycle.shouldOfferEarlyCompletion(true, day, days, total, unfinished)) return;
        String[] actions = {"Start a next-month goal early", "Add a stretch goal",
                "Add extra revision / practice", "Keep the remaining days as buffer"};
        new AlertDialog.Builder(this).setTitle("Monthly goals completed early")
                .setMessage("Everything planned for " + track + " in " + monthLabelForKey(completedMonth)
                        + " is complete, with " + (days - day) + " day(s) left. Your original plan stays intact.")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) chooseUpcomingGoalToAdvance(completedMonth, track);
                    else if (which == 1) editMonthlyGoal(null, track, completedMonth);
                    else if (which == 2) addQuickRevisionGoal(completedMonth, track);
                    else Toast.makeText(this, "Buffer preserved. No goals were changed.", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void chooseUpcomingGoalToAdvance(String month, String track) {
        ArrayList<MonthlyGoal> choices = new ArrayList<>();
        for (MonthlyGoal goal : readMonthlyGoals()) if (goal.month.compareTo(month) > 0 && !goal.done
                && track.equals(goal.track) && goal.parentGoalId == 0L
                && MonthlyGoalLifecycle.ACTIVE.equals(goal.lifecycleStatus)) choices.add(goal);
        choices.sort(Comparator.comparing((MonthlyGoal g) -> g.month).thenComparing(g -> g.title, String.CASE_INSENSITIVE_ORDER));
        if (choices.isEmpty()) {
            Toast.makeText(this, "No upcoming goal for this track can be advanced safely.", Toast.LENGTH_LONG).show(); return;
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) labels[i] = monthLabelForKey(choices.get(i).month) + " · " + choices.get(i).title;
        new AlertDialog.Builder(this).setTitle("Choose a goal to start early")
                .setMessage("Choose manually so prerequisite-dependent work isn't moved automatically.")
                .setItems(labels, (dialog, which) -> {
                    MonthlyGoal goal = choices.get(which);
                    new AlertDialog.Builder(this).setTitle("Start goal early?")
                            .setMessage("Move “" + goal.title + "” into " + monthLabelForKey(month)
                                    + " for scheduling. Its original target month stays in history.")
                            .setPositiveButton("Start early", (d, w) -> rescheduleMonthlyGoal(goal, month, "Started early"))
                            .setNegativeButton("Cancel", null).show();
                }).setNegativeButton("Cancel", null).show();
    }

    private void addQuickRevisionGoal(String month, String track) {
        ArrayList<MonthlyGoal> all = readMonthlyGoals();
        long id = System.currentTimeMillis(); for (MonthlyGoal item : all) id = Math.max(id, item.id + 1L);
        MonthlyGoal extra = new MonthlyGoal(id, month, track, "Extra revision / practice",
                "Use remaining days for active recall, timed practice, problem-solving and recurring-error review.", false);
        recordMonthlyGoalEvent(extra, "Added as an early-completion stretch goal");
        all.add(extra); selectedGoalMonth = month; persistMonthlyGoals(all); refreshRoadmapTabContent();
    }

    private void confirmDeleteMonthlyGoal(MonthlyGoal goal) {
        new AlertDialog.Builder(this).setTitle("Delete this goal?")
                .setMessage("“" + goal.title + "” will be removed from " + monthLabelForKey(goal.month)
                        + (MonthlyGoalLifecycle.SPLIT_PARENT.equals(goal.lifecycleStatus) ? ", along with its smaller goals." : "."))
                .setPositiveButton("Delete", (d, w) -> {
                    ArrayList<MonthlyGoal> all = readMonthlyGoals();
                    all.removeIf(item -> item.id == goal.id || isDescendantOf(all, item, goal.id));
                    persistMonthlyGoals(all);
                    Toast.makeText(this, "Goal deleted.", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Keep goal", null).show();
    }

    private void renderMilestones() {
        if (milestoneList == null) return;
        milestoneList.removeAllViews();
        ArrayList<RoadmapMilestone> milestones = readRoadmapMilestones();
        Collections.sort(milestones, (a, b) -> {
            if (a.done != b.done) return a.done ? 1 : -1;
            return (a.date == null ? "9999-12-31" : a.date)
                    .compareTo(b.date == null ? "9999-12-31" : b.date);
        });
        if (milestones.isEmpty()) {
            TextView empty = text("No milestones yet. Add one when you have a meaningful checkpoint.",
                    13, color("#9AABA0"), false);
            empty.setPadding(dp(3), dp(12), dp(3), dp(12));
            milestoneList.addView(empty);
            return;
        }

        for (int i = 0; i < milestones.size(); i++) {
            RoadmapMilestone milestone = milestones.get(i);
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(10), dp(2), dp(10));
            row.setBackgroundColor(Color.TRANSPARENT);

            Button mark = actionButton(milestone.done ? "✓" : "○",
                    color(milestone.done ? "#102017" : "#A8BAAE"),
                    milestone.done ? "#70D6A5" : "#283A30");
            mark.setContentDescription(milestone.done ? "Reopen milestone" : "Mark milestone complete");
            row.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));

            LinearLayout details = vertical();
            details.setPadding(dp(10), 0, dp(2), 0);
            TextView title = text(milestone.title, 14,
                    color(milestone.done ? "#A4B4AA" : "#F2F7F3"), true);
            details.addView(title);
            boolean overdue = !milestone.done && milestone.date != null && milestone.date.compareTo(todayKey()) < 0;
            String dateLine = milestone.track + "  ·  " + displayRoadmapDate(milestone.date)
                    + (overdue ? "  ·  Overdue" : "");
            details.addView(text(dateLine, 10, color(overdue ? "#D5B56E" : "#9AABA0"), false));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setContentDescription(milestone.title + ", due " + displayRoadmapDate(milestone.date)
                    + ". Tap to edit; long press to delete if it is a custom milestone.");
            row.setOnClickListener(v -> editRoadmapMilestone(milestone));
            row.setOnLongClickListener(v -> {
                if (milestone.key == null || milestone.key.isEmpty()) {
                    confirmDeleteRoadmapMilestone(milestone);
                } else {
                    Toast.makeText(this, "Core checkpoints can be edited but not deleted.", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            mark.setOnClickListener(v -> toggleRoadmapMilestone(milestone));
            milestoneList.addView(row, new LinearLayout.LayoutParams(-1, -2));

            if (i < milestones.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(color("#26372D"));
                milestoneList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
    }

    private void editRoadmapMilestone(RoadmapMilestone existing) {
        editRoadmapMilestone(existing, null);
    }

    private void editRoadmapMilestone(RoadmapMilestone existing, String preferredTrack) {
        final String[] draftDate = {existing == null ? todayKey() : existing.date};
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(10));
        TextView help = text("A milestone is a deadline or readiness checkpoint, not a syllabus topic. Editing a saved checkpoint also updates its date anywhere DayFlow shows it.",
                12, color("#687A70"), false);
        help.setLineSpacing(dp(2), 1f);
        form.addView(help);

        TextView trackLabel = text("TRACK", 10, color("#687A70"), true);
        LinearLayout.LayoutParams trackLabelLp = new LinearLayout.LayoutParams(-1, -2);
        trackLabelLp.topMargin = dp(10);
        form.addView(trackLabel, trackLabelLp);
        Spinner track = new Spinner(this);
        track.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MILESTONE_TRACKS));
        if (existing != null) {
            for (int i = 0; i < MILESTONE_TRACKS.length; i++) {
                if (MILESTONE_TRACKS[i].equals(existing.track)) track.setSelection(i);
            }
        } else {
            track.setSelection(0);
            if (preferredTrack != null) {
                for (int i = 0; i < MILESTONE_TRACKS.length; i++) {
                    if (MILESTONE_TRACKS[i].equals(preferredTrack)) track.setSelection(i);
                }
            }
        }
        form.addView(track, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView titleLabel = text("MILESTONE", 10, color("#687A70"), true);
        LinearLayout.LayoutParams titleLabelLp = new LinearLayout.LayoutParams(-1, -2);
        titleLabelLp.topMargin = dp(10);
        form.addView(titleLabel, titleLabelLp);
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Name this milestone");
        title.setTextColor(color("#18372E"));
        title.setHintTextColor(color("#82948A"));
        title.setPadding(dp(12), dp(5), dp(12), dp(5));
        title.setBackground(bg("#FFFFFF", 12));
        if (existing != null) title.setText(existing.title);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView dateLabel = text("DEADLINE", 10, color("#687A70"), true);
        LinearLayout.LayoutParams dateLabelLp = new LinearLayout.LayoutParams(-1, -2);
        dateLabelLp.topMargin = dp(10);
        form.addView(dateLabel, dateLabelLp);
        final Button[] dateButtonRef = new Button[1];
        dateButtonRef[0] = secondaryButton(displayRoadmapDate(draftDate[0]), () -> {
            Date parsed = parseDateKey(draftDate[0]);
            Calendar initial = Calendar.getInstance();
            if (parsed != null) initial.setTime(parsed);
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, day, 0, 0, 0);
                draftDate[0] = keyForDate(chosen.getTime());
                dateButtonRef[0].setText(displayRoadmapDate(draftDate[0]));
            }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
            picker.setTitle("Milestone deadline");
            picker.show();
        });
        Button dateButton = dateButtonRef[0];
        form.addView(dateButton, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView detailLabel = text("SUCCESS CRITERIA", 10, color("#687A70"), true);
        LinearLayout.LayoutParams detailLabelLp = new LinearLayout.LayoutParams(-1, -2);
        detailLabelLp.topMargin = dp(10);
        form.addView(detailLabel, detailLabelLp);
        EditText detail = new EditText(this);
        detail.setGravity(Gravity.TOP | Gravity.START);
        detail.setMinLines(3);
        detail.setHint("How will you know this checkpoint is achieved?");
        detail.setTextColor(color("#18372E"));
        detail.setHintTextColor(color("#82948A"));
        detail.setPadding(dp(12), dp(10), dp(12), dp(10));
        detail.setBackground(bg("#FFFFFF", 12));
        detail.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (existing != null && existing.detail != null) detail.setText(existing.detail);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, dp(112));
        detailLp.topMargin = dp(4);
        form.addView(detail, detailLp);

        showFullScreenEditor("MILESTONES", existing == null ? "Add milestone" : "Edit milestone",
                "Use an observable outcome, not just a date.", form, () -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        title.setError("Enter a milestone name");
                        title.requestFocus();
                        return false;
                    }
                    ArrayList<RoadmapMilestone> all = readRoadmapMilestones();
                    RoadmapMilestone updated = new RoadmapMilestone(
                            existing == null ? System.currentTimeMillis() : existing.id,
                            existing == null ? "" : existing.key,
                            track.getSelectedItem().toString(), name, draftDate[0],
                            detail.getText().toString().trim(), existing != null && existing.done);
                    boolean replaced = false;
                    for (int i = 0; i < all.size(); i++) {
                        if (existing != null && all.get(i).id == existing.id) {
                            all.set(i, updated);
                            replaced = true;
                            break;
                        }
                    }
                    if (!replaced) all.add(updated);
                    persistRoadmapMilestones(all);
                    refreshRoadmapTabContent();
                    return true;
                });
    }

    private void toggleRoadmapMilestone(RoadmapMilestone milestone) {
        ArrayList<RoadmapMilestone> all = readRoadmapMilestones();
        for (RoadmapMilestone item : all) {
            if (item.id == milestone.id) {
                item.done = !item.done;
                break;
            }
        }
        persistRoadmapMilestones(all);
    }

    private void confirmDeleteRoadmapMilestone(RoadmapMilestone milestone) {
        if (milestone.key != null && !milestone.key.isEmpty()) {
            Toast.makeText(this, "Edit this checkpoint instead of deleting a core roadmap date.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete this milestone?")
                .setMessage("“" + milestone.title + "” will be removed from the roadmap.")
                .setPositiveButton("Delete", (d, w) -> {
                    ArrayList<RoadmapMilestone> all = readRoadmapMilestones();
                    all.removeIf(item -> item.id == milestone.id);
                    persistRoadmapMilestones(all);
                })
                .setNegativeButton("Keep milestone", null)
                .show();
    }

    private ArrayList<MonthlyGoal> readMonthlyGoals() {
        ArrayList<MonthlyGoal> goals = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(getPrefs().getString(MONTHLY_GOALS_KEY, "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i); if (item == null) continue;
                String month = item.optString("month", monthKeyFromDate(todayKey()));
                boolean done = item.optBoolean("done", false);
                MonthlyGoal goal = new MonthlyGoal(item.optLong("id", i + 1L), month,
                        item.optString("track", "General Studies"), item.optString("title", ""),
                        item.optString("target", ""), done);
                goal.originalMonth = item.optString("originalMonth", month);
                goal.sortOrder = item.optInt("sortOrder", i * 100);
                goal.completedAt = item.optLong("completedAt", 0L);
                goal.parentGoalId = item.optLong("parentGoalId", 0L);
                goal.source = item.optString("source", "");
                goal.pages = item.optString("pages", "");
                goal.estimatedTime = item.optString("estimatedTime", "");
                if (goal.source.trim().isEmpty()) goal.source = inferGoalSource(goal.target, goal.track);
                if (goal.pages.trim().isEmpty()) goal.pages = inferGoalPages(goal.target, goal.track);
                if (goal.estimatedTime.trim().isEmpty()) goal.estimatedTime = inferGoalEstimatedTime(goal.target, goal.track);
                goal.lifecycleStatus = item.optString("lifecycleStatus",
                        done ? MonthlyGoalLifecycle.COMPLETED : MonthlyGoalLifecycle.ACTIVE);
                if (done) goal.lifecycleStatus = MonthlyGoalLifecycle.COMPLETED;
                else if (MonthlyGoalLifecycle.COMPLETED.equals(goal.lifecycleStatus)) goal.lifecycleStatus = MonthlyGoalLifecycle.ACTIVE;
                JSONArray history = item.optJSONArray("history");
                if (history != null) for (int h = 0; h < history.length(); h++) {
                    String event = history.optString(h, ""); if (!event.isEmpty()) goal.history.add(event);
                }
                goals.add(goal);
            }
        } catch (Exception e) { debugLog("Monthly goals read failed: " + e.getMessage()); }
        return goals;
    }

    private void persistMonthlyGoals(ArrayList<MonthlyGoal> goals) {
        JSONArray data = new JSONArray();
        try {
            for (MonthlyGoal goal : goals) {
                JSONObject item = new JSONObject();
                item.put("id", goal.id); item.put("month", goal.month); item.put("track", goal.track);
                item.put("title", goal.title); item.put("target", goal.target == null ? "" : goal.target);
                item.put("source", goal.source == null || goal.source.trim().isEmpty()
                        ? inferGoalSource(goal.target, goal.track) : goal.source);
                item.put("pages", goal.pages == null || goal.pages.trim().isEmpty()
                        ? inferGoalPages(goal.target, goal.track) : goal.pages);
                item.put("estimatedTime", goal.estimatedTime == null || goal.estimatedTime.trim().isEmpty()
                        ? inferGoalEstimatedTime(goal.target, goal.track) : goal.estimatedTime);
                item.put("done", goal.done);
                item.put("sortOrder", goal.sortOrder);
                item.put("originalMonth", goal.originalMonth == null ? goal.month : goal.originalMonth);
                item.put("completedAt", goal.completedAt); item.put("parentGoalId", goal.parentGoalId);
                item.put("lifecycleStatus", goal.lifecycleStatus == null
                        ? (goal.done ? MonthlyGoalLifecycle.COMPLETED : MonthlyGoalLifecycle.ACTIVE) : goal.lifecycleStatus);
                JSONArray history = new JSONArray(); for (String event : goal.history) history.put(event);
                item.put("history", history); data.put(item);
            }
            getPrefs().edit().putString(MONTHLY_GOALS_KEY, data.toString()).apply();
            debugLog("Monthly roadmap goals saved: " + goals.size());
            refreshRoadmapSummary(); renderMonthlyGoals();
        } catch (Exception e) {
            debugLog("Monthly goals save failed: " + e.getMessage());
            Toast.makeText(this, "Could not save monthly goals.", Toast.LENGTH_SHORT).show();
        }
    }

        private ArrayList<RoadmapMilestone> readRoadmapMilestones() {
        ArrayList<RoadmapMilestone> milestones = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(getPrefs().getString(ROADMAP_MILESTONES_KEY, "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                milestones.add(new RoadmapMilestone(item.optLong("id", i + 1L),
                        item.optString("key", ""),
                        item.optString("track", "UPSC CSE"),
                        item.optString("title", ""),
                        item.optString("date", todayKey()),
                        item.optString("detail", ""),
                        item.optBoolean("done", false)));
            }
        } catch (Exception e) {
            debugLog("Roadmap milestones read failed: " + e.getMessage());
        }
        return milestones;
    }

    private void persistRoadmapMilestones(ArrayList<RoadmapMilestone> milestones) {
        JSONArray data = new JSONArray();
        try {
            SharedPreferences.Editor editor = getPrefs().edit();
            for (RoadmapMilestone milestone : milestones) {
                JSONObject item = new JSONObject();
                item.put("id", milestone.id);
                item.put("key", milestone.key == null ? "" : milestone.key);
                item.put("track", milestone.track);
                item.put("title", milestone.title);
                item.put("date", milestone.date);
                item.put("detail", milestone.detail == null ? "" : milestone.detail);
                item.put("done", milestone.done);
                data.put(item);
                if ("cse-checkpoint".equals(milestone.key)) editor.putString(CSE_CHECKPOINT_KEY, milestone.date);
                else if ("cse-target".equals(milestone.key)) editor.putString(SYLLABUS_TARGET_KEY, milestone.date);
                else if ("tier34".equals(milestone.key)) editor.putString(INTERVIEW_TIER34_KEY, milestone.date);
                else if ("tier12".equals(milestone.key)) editor.putString(INTERVIEW_TARGET_KEY, milestone.date);
            }
            editor.putString(ROADMAP_MILESTONES_KEY, data.toString()).apply();
            debugLog("Roadmap milestones saved: " + milestones.size());
            refreshRoadmapSummary();
            renderMilestones();
        } catch (Exception e) {
            debugLog("Roadmap milestone save failed: " + e.getMessage());
            Toast.makeText(this, "Could not save milestone changes.", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureRoadmapData() {
        SharedPreferences prefs = getPrefs();
        SharedPreferences.Editor defaults = prefs.edit();
        if (!prefs.contains(CSE_CHECKPOINT_KEY)) defaults.putString(CSE_CHECKPOINT_KEY, "2027-04-01");
        if (!prefs.contains(SYLLABUS_TARGET_KEY)) defaults.putString(SYLLABUS_TARGET_KEY, "2028-01-01");
        if (!prefs.contains(INTERVIEW_TIER34_KEY)) {
            String start = prefs.getString(INTERVIEW_PLAN_START_KEY, todayKey());
            Date startDate = parseDateKey(start);
            Calendar sixMonths = Calendar.getInstance();
            if (startDate != null) sixMonths.setTime(startDate);
            sixMonths.add(Calendar.MONTH, 6);
            defaults.putString(INTERVIEW_TIER34_KEY, keyForDate(sixMonths.getTime()));
        }
        defaults.apply();

        ArrayList<SyllabusTopic> oldTopics = readSyllabusTopics();
        boolean tier34Done = false, tier12Done = false;
        for (SyllabusTopic topic : oldTopics) {
            if (topic.title.startsWith("Roadmap milestone:") && topic.title.toLowerCase(Locale.US).contains("tier 3/4")) {
                tier34Done = topic.done;
            } else if (topic.title.startsWith("Roadmap milestone:") && topic.title.toLowerCase(Locale.US).contains("tier 1/2")) {
                tier12Done = topic.done;
            }
        }
        if (!prefs.contains(ROADMAP_MILESTONES_KEY)) {
            persistRoadmapMilestones(defaultRoadmapMilestones(tier34Done, tier12Done));
        }

        boolean removedLegacyRows = oldTopics.removeIf(topic ->
                topic.title.startsWith("Roadmap milestone:")
                        || topic.title.startsWith("Interview-prep time commitment:"));
        if (removedLegacyRows) persistSyllabusTopics(oldTopics);

        if (!prefs.contains(MONTHLY_GOALS_KEY)) {
            persistMonthlyGoals(seedDefaultMonthlyGoals());
        }
        reconcileMonthlyGoalsWithSourcePlans();
        ensurePdfLibraryRoadmapGoal();
    }

    /**
     * One-time, non-destructive migration from the older generic starter rows to the
     * saved source-aligned monthly plan. Only exact old starter fingerprints are
     * renamed; completed state and IDs are retained. User-edited/custom goals stay intact.
     */
    private void reconcileMonthlyGoalsWithSourcePlans() {
        SharedPreferences prefs = getPrefs();
        if (prefs.getInt(MONTHLY_PLAN_MIGRATION_KEY, 0) >= MONTHLY_PLAN_MIGRATION_VERSION) return;

        ArrayList<MonthlyGoal> goals = readMonthlyGoals();
        HashSet<String> legacyStarterFingerprints = new HashSet<>();
        for (MonthlyGoal legacy : seedDefaultMonthlyGoals()) {
            legacyStarterFingerprints.add(monthlyGoalFingerprint(legacy.track, legacy.title, legacy.target));
        }

        boolean changed = false;
        for (MonthlyGoal goal : goals) {
            if (!legacyStarterFingerprints.contains(
                    monthlyGoalFingerprint(goal.track, goal.title, goal.target))) continue;
            String[] planned = RoadmapMonthlyPlan.findGoal(goal.month, goal.track);
            if (planned == null) continue;
            // A completed old goal proves completion of its old scope, not the new source-plan scope.
            // Keep that completed record intact; the missing source-plan goal is added below.
            if (goal.done) continue;
            if (!planned[1].equals(goal.title) || !planned[2].equals(goal.target == null ? "" : goal.target)) {
                goal.title = planned[1];
                goal.target = planned[2];
                // Preserve its ID and date; it remains open under the revised target.
                changed = true;
            }
        }

        long nextId = System.currentTimeMillis();
        for (MonthlyGoal goal : goals) nextId = Math.max(nextId, goal.id + 1L);
        for (int i = 0; i < RoadmapMonthlyPlan.monthCount(); i++) {
            String monthKey = RoadmapMonthlyPlan.monthKeyAt(i);
            for (String[] row : RoadmapMonthlyPlan.rowsForMonth(monthKey)) {
                boolean plannedGoalExists = false;
                for (MonthlyGoal existing : goals) {
                    if (monthKey.equals(existing.month)
                            && row[0].equals(existing.track)
                            && row[1].equals(existing.title)) {
                        plannedGoalExists = true;
                        break;
                    }
                }
                if (!plannedGoalExists) {
                    goals.add(new MonthlyGoal(nextId++, monthKey, row[0], row[1], row[2], false));
                    changed = true;
                }
            }
        }

        if (changed && !persistMonthlyGoalsRaw(goals)) {
            debugLog("Monthly roadmap plan migration failed; will retry at next launch.");
            return;
        }
        prefs.edit().putInt(MONTHLY_PLAN_MIGRATION_KEY, MONTHLY_PLAN_MIGRATION_VERSION).apply();
        debugLog("Monthly roadmap plans reconciled (source-plan version "
                + MONTHLY_PLAN_MIGRATION_VERSION + ") without deleting existing goal history.");
    }

    /**
     * Adds the new offline PDF-library goal without rerunning the broader roadmap
     * migration. This avoids duplicating any existing user-edited monthly goals.
     */
    private void ensurePdfLibraryRoadmapGoal() {
        SharedPreferences prefs = getPrefs();
        if (prefs.getBoolean(PDF_LIBRARY_ROADMAP_GOAL_MIGRATION_KEY, false)) return;

        String month = "2026-10";
        String[] row = RoadmapMonthlyPlan.findGoal(month, "Other");
        if (row == null || row.length < 3) return;

        ArrayList<MonthlyGoal> goals = readMonthlyGoals();
        for (MonthlyGoal goal : goals) {
            if (month.equals(goal.month) && row[0].equals(goal.track) && row[1].equals(goal.title)) {
                prefs.edit().putBoolean(PDF_LIBRARY_ROADMAP_GOAL_MIGRATION_KEY, true).apply();
                return;
            }
        }

        long nextId = System.currentTimeMillis();
        for (MonthlyGoal goal : goals) nextId = Math.max(nextId, goal.id + 1L);
        goals.add(new MonthlyGoal(nextId, month, row[0], row[1], row[2], false));
        if (!persistMonthlyGoalsRaw(goals)) {
            debugLog("PDF-library roadmap goal migration failed; will retry at next launch.");
            return;
        }
        prefs.edit().putBoolean(PDF_LIBRARY_ROADMAP_GOAL_MIGRATION_KEY, true).apply();
        debugLog("Added October 2026 offline PDF-library roadmap goal.");
    }

    private String monthlyGoalFingerprint(String track, String title, String target) {
        return (track == null ? "" : track) + "|"
                + (title == null ? "" : title) + "|"
                + (target == null ? "" : target);
    }

    private ArrayList<RoadmapMilestone> defaultRoadmapMilestones(boolean tier34Done, boolean tier12Done) {
        SharedPreferences prefs = getPrefs();
        ArrayList<RoadmapMilestone> milestones = new ArrayList<>();
        milestones.add(new RoadmapMilestone(System.currentTimeMillis() + 1, "cse-checkpoint", "UPSC CSE",
                "CSE progress checkpoint", prefs.getString(CSE_CHECKPOINT_KEY, "2027-04-01"),
                "Audit General Studies and Maths Optional coverage, review test performance, and reset the next monthly goals.",
                false));
        milestones.add(new RoadmapMilestone(System.currentTimeMillis() + 2, "cse-target", "UPSC CSE",
                "CSE syllabus target", prefs.getString(SYLLABUS_TARGET_KEY, "2028-01-01"),
                "Compare actual syllabus coverage with the target and plan the next revision and testing phase.",
                false));
        milestones.add(new RoadmapMilestone(System.currentTimeMillis() + 3, "tier34", "Software interviews",
                "Tier 3/4 interview readiness", prefs.getString(INTERVIEW_TIER34_KEY, "2027-04-05"),
                "Review timed coding, system-design basics, behavioural stories and mock-interview feedback before applying.",
                tier34Done));
        milestones.add(new RoadmapMilestone(System.currentTimeMillis() + 4, "tier12", "Software interviews",
                "Tier 1/2 strong-hire readiness", prefs.getString(INTERVIEW_TARGET_KEY, "2027-07-11"),
                "Evaluate coding, system design, leadership examples and project deep-dives against strong-hire expectations.",
                tier12Done));
        return milestones;
    }

    private ArrayList<MonthlyGoal> seedDefaultMonthlyGoals() {
        String[][][] plan = {
            {
                {"General Studies", "Start current affairs and the GS baseline", "Confirm primary sources. Begin Prelims current events and History of India / National Movement. Keep a weekly recall and MCQ error log."},
                {"Maths Optional", "Paper I foundations: Linear Algebra + Calculus", "Map Paper I; cover vector spaces, bases, matrix systems, limits, continuity and differentiability. Record difficult proofs and problems."},
                {"Software Interview Prep", "Establish a Java / DSA / design baseline", "Use the saved 12–13-hour preparation commitment. Benchmark timed coding and one system-design explanation; record gaps."}
            },
            {
                {"General Studies", "Modern India, freedom struggle and culture", "Cover modern Indian history, the freedom struggle and Indian culture. Test recall weekly and link events to causes, outcomes and contributors."},
                {"Maths Optional", "Continue Linear Algebra and Calculus", "Cover linear transformations, rank/eigenvalues, derivatives, extrema and integration basics. Solve selected problems without looking at worked solutions."},
                {"Software Interview Prep", "Java fundamentals + arrays, strings and hashing", "Review OOP, collections, complexity and core Java. Practise timed array/string/hash problems and write down why each miss happened."}
            },
            {
                {"General Studies", "Geography and Indian society foundations", "Cover physical/world geography, Indian geography, resources, industries and core society themes. Use maps and short active-recall summaries."},
                {"Maths Optional", "Analytic Geometry + Ordinary Differential Equations", "Work through lines, planes and standard surfaces, then first- and higher-order ODE methods. Maintain a worked-problem and error log."},
                {"Software Interview Prep", "Core data structures and timed coding", "Practise linked lists, stacks, queues, trees and heaps. Complete timed questions, then re-solve the missed ones unaided."}
            },
            {
                {"General Studies", "Constitution, Parliament and governance", "Cover constitutional evolution/features, federalism, Parliament, Executive, Judiciary and separation of powers. Build concise comparison notes."},
                {"Maths Optional", "Paper I: Dynamics, Statics and Vector Analysis", "Cover motion, work/energy, equilibrium, potential energy, grad/div/curl and the major integral theorems; solve standard problems."},
                {"Software Interview Prep", "SQL, transactions and backend/API fundamentals", "Practise SQL, indexes, transactions/isolation, REST, pagination and idempotency. Explain one API design and its failure cases."}
            },
            {
                {"General Studies", "Economy, budgeting, agriculture and inclusion", "Cover growth, development, inclusive growth, budgeting, agriculture, MSP/PDS, food security and infrastructure. Review with mixed MCQs."},
                {"Maths Optional", "Paper II: Algebra and Real Analysis", "Cover groups/rings and core real-analysis concepts, sequences, series, continuity and convergence. Write definitions and key proof outlines from memory."},
                {"Software Interview Prep", "System-design foundations", "Practise requirements, capacity estimates, data modelling, caching, queues and trade-offs. Keep a short written design for review."}
            },
            {
                {"General Studies", "Science, environment, security and April checkpoint prep", "Cover core environment/biodiversity, science-tech, disaster management and internal security. Audit topic coverage and identify the highest-priority gaps for 1 April."},
                {"Maths Optional", "Audit Paper I; begin Complex Analysis + Linear Programming", "Check every Paper I unit against the syllabus list, solve selected PYQs, and begin analytic functions, contour integration and LP methods."},
                {"Software Interview Prep", "Prepare for the Tier 3/4 readiness checkpoint", "Run timed coding and a system-design mock; review resume/project examples and behavioural stories. Turn every weak area into a drill."}
            },
            {
                {"General Studies", "Use the 1 April checkpoint to reset CSE priorities", "Compare actual GS coverage and test performance with the plan. Close the largest gaps first and edit upcoming monthly goals based on evidence."},
                {"Maths Optional", "Paper II: Complex Analysis + Linear Programming", "Build a first-pass checklist for analytic functions, Cauchy results, singularities, residues, simplex and duality; practise exam-style questions."},
                {"Software Interview Prep", "Tier 3/4 readiness review", "Use mock results—not the date alone—to decide whether to apply or spend another cycle on coding, design and interview communication."}
            },
            {
                {"General Studies", "Governance, social justice and international relations", "Cover welfare, health/education, accountability, civil services, India’s neighbourhood, foreign policy and international institutions. Practise recall and short answers."},
                {"Maths Optional", "Paper II: PDE and Numerical Analysis", "Cover standard PDE methods, canonical forms, interpolation, numerical integration and ODE methods; solve representative problems."},
                {"Software Interview Prep", "Distributed systems and reliability", "Explain consistency, replication, partitioning/sharding, retries, timeouts, circuit breakers and backpressure with concrete design trade-offs."}
            },
            {
                {"General Studies", "Ethics, CSAT and remaining GS gaps", "Work through ethics, values, attitude, emotional intelligence, probity and case studies; practise CSAT comprehension, reasoning, numeracy and data interpretation."},
                {"Maths Optional", "Computer Programming, Mechanics and Fluid Dynamics", "Cover number systems/Boolean logic, algorithms/flowcharts, Lagrangian/Hamiltonian mechanics, continuity and standard fluid-flow concepts."},
                {"Software Interview Prep", "System design end to end", "Complete end-to-end designs: requirements, data model, capacity, APIs, storage, caching, failure handling, observability and cost."}
            },
            {
                {"General Studies", "World history, society, Essay and answer-writing", "Close remaining GS-I society/world-history gaps; practise structured Mains answers and outlines for essays across the saved topic areas."},
                {"Maths Optional", "Mixed Paper I/II PYQs and gap closure", "Map unresolved topics in both papers, attempt timed previous-year questions and use the error log to select the next revision targets."},
                {"Software Interview Prep", "Tier 1/2 readiness checkpoint", "Review coding, system design, leadership examples and project deep-dives with timed mocks. Record the gaps that still block strong-hire readiness."}
            },
            {
                {"General Studies", "Mixed GS tests and current-affairs consolidation", "Run mixed Prelims practice, review every error and connect current events to polity, economy, society, environment and international relations."},
                {"Maths Optional", "Paper I sectional tests", "Attempt timed Paper I sections, review marking/solution quality and re-solve missed questions until the method is clear."},
                {"Software Interview Prep", "Leadership stories, resume and project deep-dives", "Prepare evidence-based examples of ownership, delivery, prioritisation, conflict, mentoring and measurable impact."}
            },
            {
                {"General Studies", "Close first-pass GS gaps", "Use the syllabus tracker to identify every unchecked GS item. Finish high-priority uncovered topics and schedule short tests for weaker subjects."},
                {"Maths Optional", "Paper II sectional tests", "Attempt timed Paper II sections across Algebra, Analysis, Complex Analysis, LP, PDE, Numerical Analysis and applied topics; repair recurring errors."},
                {"Software Interview Prep", "Targeted interview drills and company shortlist", "Use mock feedback to choose drills and refresh the Tier 1/2 company shortlist; protect CSE study priorities."}
            },
            {
                {"General Studies", "Consolidate GS-I and GS-II", "Revise history, culture, geography, society, Constitution, governance and international relations through recall, PYQs and structured Mains answers."},
                {"Maths Optional", "Second pass: Linear Algebra, Calculus and ODE", "Revisit key results and methods, solve PYQs under time limits and update a concise formula/result sheet."},
                {"Software Interview Prep", "Keep coding and design interview-ready", "Maintain timed DSA practice, one-page design notes and behavioural review; track the real interview feedback loop."}
            },
            {
                {"General Studies", "Consolidate GS-III, GS-IV and CSAT", "Revise economy, agriculture, science-tech, environment, security and ethics; practise case studies and mixed CSAT sets."},
                {"Maths Optional", "Second pass: Paper II core areas", "Revisit Algebra, Real/Complex Analysis, LP, PDE and Numerical Analysis, prioritising weak results and problem types."},
                {"Software Interview Prep", "Close career narrative and leadership gaps", "Make resume/project stories measurable and coherent; practise explaining PM/EM/IC experience and role fit."}
            },
            {
                {"General Studies", "Previous-year questions and answer writing", "Use PYQs to find recurring themes, practise timed answers and essays, and keep a list of factual/analytical gaps."},
                {"Maths Optional", "Mixed Paper I/II previous-year questions", "Attempt mixed timed sections from both papers, track accuracy/time, revise formulas and repeat previously missed problems."},
                {"Software Interview Prep", "Mock interviews and focused repairs", "Run realistic coding, design and behavioural mocks; spend the next study cycle on the top three observed weaknesses."}
            },
            {
                {"General Studies", "1 January checkpoint: verify CSE first-pass completion", "The first pass should be complete by 31 December. On 1 January, audit every GS/CSAT item; name any carry-over explicitly and move into a revision/test cycle."},
                {"Maths Optional", "1 January checkpoint: verify both papers", "The first pass should be complete by 31 December. Audit Paper I and II, document any carry-over clearly, and set revision priorities from PYQs and the error log."},
                {"Software Interview Prep", "Review career milestones and set the next cycle", "Compare actual readiness with Tier 3/4 and Tier 1/2 checkpoints; update target roles and the next interview plan from mock and interview evidence."}
            }
        };
        ArrayList<MonthlyGoal> goals = new ArrayList<>();
        Calendar month = Calendar.getInstance();
        month.set(Calendar.DAY_OF_MONTH, 1);
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);
        Date targetDate = parseDateKey(getPrefs().getString(SYLLABUS_TARGET_KEY, "2028-01-01"));
        Calendar target = Calendar.getInstance();
        if (targetDate != null) target.setTime(targetDate);
        int maxMonths = Math.max(1, (target.get(Calendar.YEAR) - month.get(Calendar.YEAR)) * 12
                + target.get(Calendar.MONTH) - month.get(Calendar.MONTH) + 1);
        maxMonths = Math.min(maxMonths, plan.length);
        long baseId = System.currentTimeMillis();
        for (int i = 0; i < maxMonths; i++) {
            String monthKey = new SimpleDateFormat("yyyy-MM", Locale.US).format(month.getTime());
            for (int j = 0; j < plan[i].length; j++) {
                String[] row = plan[i][j];
                goals.add(new MonthlyGoal(baseId + i * 10L + j, monthKey, row[0], row[1], row[2], false));
            }
            month.add(Calendar.MONTH, 1);
        }
        return goals;
    }

    private boolean persistMonthlyGoalsRaw(ArrayList<MonthlyGoal> goals) {
        JSONArray data = new JSONArray();
        try {
            for (MonthlyGoal goal : goals) {
                JSONObject item = new JSONObject();
                item.put("id", goal.id); item.put("month", goal.month); item.put("track", goal.track);
                item.put("title", goal.title); item.put("target", goal.target == null ? "" : goal.target);
                item.put("source", goal.source == null || goal.source.trim().isEmpty()
                        ? inferGoalSource(goal.target, goal.track) : goal.source);
                item.put("pages", goal.pages == null || goal.pages.trim().isEmpty()
                        ? inferGoalPages(goal.target, goal.track) : goal.pages);
                item.put("estimatedTime", goal.estimatedTime == null || goal.estimatedTime.trim().isEmpty()
                        ? inferGoalEstimatedTime(goal.target, goal.track) : goal.estimatedTime);
                item.put("done", goal.done);
                item.put("sortOrder", goal.sortOrder);
                item.put("originalMonth", goal.originalMonth == null ? goal.month : goal.originalMonth);
                item.put("completedAt", goal.completedAt); item.put("parentGoalId", goal.parentGoalId);
                item.put("lifecycleStatus", goal.lifecycleStatus == null
                        ? (goal.done ? MonthlyGoalLifecycle.COMPLETED : MonthlyGoalLifecycle.ACTIVE) : goal.lifecycleStatus);
                JSONArray history = new JSONArray(); for (String event : goal.history) history.put(event);
                item.put("history", history); data.put(item);
            }
            getPrefs().edit().putString(MONTHLY_GOALS_KEY, data.toString()).apply();
            return true;
        } catch (Exception e) { debugLog("Monthly starter goals save failed: " + e.getMessage()); return false; }
    }

        private String monthKeyFromDate(String dateKey) {
        if (dateKey != null && dateKey.length() >= 7) return dateKey.substring(0, 7);
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    private String monthLabelForKey(String monthKey) {
        Date date = parseDateKey((monthKey == null ? monthKeyFromDate(todayKey()) : monthKey) + "-01");
        if (date == null) return monthKey == null ? "Choose month" : monthKey;
        return new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date);
    }

    private String displayRoadmapDate(String dateKey) {
        Date date = parseDateKey(dateKey == null ? "" : dateKey);
        return date == null ? (dateKey == null ? "No date" : dateKey)
                : new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date);
    }

    private RoadmapMilestone roadmapMilestoneForKey(String key) {
        for (RoadmapMilestone milestone : readRoadmapMilestones()) {
            if (key.equals(milestone.key)) return milestone;
        }
        return null;
    }

    private ArrayList<SyllabusTopic> readSyllabusTopicsWith(SyllabusTopic changed) {
        ArrayList<SyllabusTopic> all=readSyllabusTopics();for(int i=0;i<all.size();i++)if(all.get(i).id==changed.id){all.set(i,changed);break;}return all;
    }

    private void buildScreenTimePage() {
        pageHeading(pageScreenTime,"Digital wellbeing","Screen time","A local historical ledger of phone screen-interactive time. Android permission is required; older days are available only while Android exposes events.");
        LinearLayout controls=horizontal();controls.addView(secondaryButton("‹ Progress",()->switchPage("progress")),new LinearLayout.LayoutParams(dp(105),dp(40)));
        Button refresh=primaryButton("Refresh history",()->{if(ScreenTimeTracker.hasUsageAccess(this))ScreenTimeTracker.refreshRecentDays(this,7);renderScreenTimePage();});
        LinearLayout.LayoutParams refreshLp=new LinearLayout.LayoutParams(0,dp(40),1);refreshLp.leftMargin=dp(8);controls.addView(refresh,refreshLp);pageScreenTime.addView(controls);
        screenTimeSummaryView=text("",13,color("#B7C7BC"),false);screenTimeSummaryView.setPadding(dp(14),dp(12),dp(14),dp(12));screenTimeSummaryView.setBackground(bg("#18231E",16));
        LinearLayout.LayoutParams summLp=new LinearLayout.LayoutParams(-1,-2);summLp.topMargin=dp(12);summLp.bottomMargin=dp(12);pageScreenTime.addView(screenTimeSummaryView,summLp);
        screenTimeList=vertical();pageScreenTime.addView(screenTimeList);renderScreenTimePage();
    }
    private void renderScreenTimePage() {
        if(screenTimeList==null||screenTimeSummaryView==null)return;screenTimeList.removeAllViews();
        if(!ScreenTimeTracker.hasUsageAccess(this)){
            screenTimeSummaryView.setText("Usage access is not enabled. DayFlow can only save screen-time totals after you grant Android's special Usage access permission.");
            LinearLayout card=surfaceCard();card.addView(text("How to enable",15,color("#F2F7F3"),true));card.addView(text("Settings → Apps → Special app access → Usage access → DayFlow → Allow.",12,color("#9AABA0"),false));
            card.addView(primaryButton("Open Usage access settings",()->{try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(Exception e){Toast.makeText(this,"Open Usage access from Android Settings.",Toast.LENGTH_LONG).show();}}));screenTimeList.addView(card);return;
        }
        ScreenTimeTracker.refreshRecentDays(this,7);TreeSet<String> dates=new TreeSet<>(Collections.reverseOrder());
        for(String k:getPrefs().getAll().keySet())if(k.startsWith("screenTimeMs_")){String date=k.substring("screenTimeMs_".length());if(date.matches("\\d{4}-\\d{2}-\\d{2}"))dates.add(date);}
        for(int i=0;i<30;i++){Calendar c=Calendar.getInstance();c.add(Calendar.DAY_OF_YEAR,-i);dates.add(keyForDate(c.getTime()));}
        long total7=0,total30=0;int count7=0,count30=0;String d7=dayOffsetKey(6),d30=dayOffsetKey(29);
        for(String key:dates){long value=ScreenTimeTracker.getStoredMillis(this,key);if(value<0)continue;if(key.compareTo(d7)>=0){total7+=value;count7++;}if(key.compareTo(d30)>=0){total30+=value;count30++;}}
        long today=ScreenTimeTracker.getStoredMillis(this,todayKey());screenTimeSummaryView.setText("TODAY  "+(today<0?"Not recorded":ScreenTimeTracker.formatDuration(today))+"\nLAST 7 DAYS  "+ScreenTimeTracker.formatDuration(total7)+"  •  "+count7+" recorded days"+"\nLAST 30 DAYS  "+ScreenTimeTracker.formatDuration(total30)+"  •  "+count30+" recorded days"+"\nBlank dates mean Android has not provided a usable snapshot.");
        long max=1;for(String key:dates)max=Math.max(max,Math.max(0,ScreenTimeTracker.getStoredMillis(this,key)));
        for(String key:dates){Date d=parseDateKey(key);String label=d==null?key:new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(d);long ms=ScreenTimeTracker.getStoredMillis(this,key);LinearLayout card=surfaceCard();
            LinearLayout row=horizontal();row.addView(text(label,13,color("#F2F7F3"),true),new LinearLayout.LayoutParams(0,-2,1));row.addView(text(ms<0?"—":ScreenTimeTracker.formatDuration(ms),13,ms<0?color("#82948A"):color("#6BD0A6"),true));card.addView(row);
            if(ms>=0){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax((int)Math.min(Integer.MAX_VALUE,max));bar.setProgress((int)Math.min(Integer.MAX_VALUE,ms));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color("#40B58A")));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(5));bp.topMargin=dp(8);card.addView(bar,bp);if(ScreenTimeTracker.isEstimated(this,key))card.addView(text("Foreground-use estimate",10,color("#D5B56E"),false));}
            screenTimeList.addView(card);
        }
    }
    private String dayOffsetKey(int offset){Calendar c=Calendar.getInstance();c.add(Calendar.DAY_OF_YEAR,-offset);return keyForDate(c.getTime());}

    private void renderBottomNavOnly() {
        String tab=activePage.equals("progress")||activePage.equals("screenTime")?"more":activePage;
        for(Map.Entry<String,TextView> e:navItems.entrySet()){
            boolean selected=e.getKey().equals(tab);
            int foreground=color(selected?"#286B55":"#687A70");
            e.getValue().setTextColor(foreground);
            View parent=(View)e.getValue().getParent();
            parent.setBackground(bg(selected?"#DCEBE2":"#FFFFFF",14));
            if(parent instanceof LinearLayout){
                LinearLayout item=(LinearLayout)parent;
                if(item.getChildCount()>0 && item.getChildAt(0) instanceof TextView)
                    ((TextView)item.getChildAt(0)).setTextColor(foreground);
            }
        }
    }

    private void buildHeader() {
        LinearLayout top = horizontal();
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.bottomMargin = dp(12);
        root.addView(top, tp);

        LinearLayout brand = vertical();
        TextView eyebrow = text("DAYFLOW  /  YOUR DAY", 10, color("#2E8060"), true);
        eyebrow.setLetterSpacing(0.12f);
        brand.addView(eyebrow);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        TextView title = text(greeting, 26, Color.WHITE, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(3);
        brand.addView(title, titleLp);
        brand.addView(text("Make the next block count.", 12, color("#687A70"), false));
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        Button add = actionButton("＋", Color.WHITE, "#23845F");
        add.setTextSize(22);
        add.setContentDescription("Add block");
        add.setOnClickListener(v -> openAddDialog());
        top.addView(add, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout nav = horizontal();
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(42));
        np.bottomMargin = dp(13);
        root.addView(nav, np);

        Button prev = actionButton("‹", color("#496257"), "#EAF0E9");
        prev.setTextSize(24);
        prev.setContentDescription("Previous day");
        prev.setOnClickListener(v -> changeSelectedDate(-1));
        nav.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(38)));

        dateView = text("", 14, Color.WHITE, true);
        dateView.setGravity(Gravity.CENTER);
        dateView.setContentDescription("Selected day. Tap to choose a date.");
        dateView.setOnClickListener(v -> openDatePicker());
        nav.addView(dateView, new LinearLayout.LayoutParams(0, -2, 1));

        Button next = actionButton("›", color("#496257"), "#EAF0E9");
        next.setTextSize(24);
        next.setContentDescription("Next day");
        next.setOnClickListener(v -> changeSelectedDate(1));
        nav.addView(next, new LinearLayout.LayoutParams(dp(42), dp(38)));
    }

    private void buildModeRow() {
        LinearLayout wrapper = vertical();
        LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(-1, -2);
        wrapperLp.bottomMargin = dp(14);
        root.addView(wrapper, wrapperLp);

        // Calendar classification is automatic; WFH/WFO is the separate work arrangement.
        modeView = text("WEEKDAY", 10, color("#687A70"), true);
        wrapper.addView(modeView);
        TextView arrangementLabel = text("WORK ARRANGEMENT", 10, color("#687A70"), true);
        LinearLayout.LayoutParams arrangementLabelLp = new LinearLayout.LayoutParams(-1, -2);
        arrangementLabelLp.topMargin = dp(8);
        wrapper.addView(arrangementLabel, arrangementLabelLp);

        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(42));
        rp.topMargin = dp(6);
        wrapper.addView(row, rp);
        String[][] options = {{"WFH", "wfh"}, {"WFO", "office"}};
        for (String[] option : options) {
            String modeKey = option[1];
            Button b = chip(option[0]);
            b.setTag("mode-chip-" + modeKey);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            if (row.getChildCount() > 0) lp.leftMargin = dp(6);
            row.addView(b, lp);
            b.setContentDescription(option[0] + " work arrangement");
            b.setOnClickListener(v -> chooseMode(modeKey));
        }

        todayRoutineEditButton = secondaryButton("Modify & Save Today's Routine", this::openTodayRoutineEditor);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(-1, dp(44));
        editLp.topMargin = dp(7);
        wrapper.addView(todayRoutineEditButton, editLp);
        todayRoutineEditButton.setVisibility(View.VISIBLE);
    }

    private void buildCurrentCard() {
        LinearLayout card = vertical();
        card.setPadding(dp(18), dp(18), dp(18), dp(16));
        card.setBackground(bg("#DCEBE2", 20));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(14);
        root.addView(card, cp);

        TextView label = text("CURRENT TASK", 10, color("#2E8060"), true);
        label.setLetterSpacing(0.12f);
        card.addView(label);

        currentTitle = text("Loading…", 24, Color.WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(5);
        card.addView(currentTitle, tp);

        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(8);
        card.addView(row, rp);
        currentTime = text("—", 13, color("#496257"), false);
        currentCountdown = text("—", 13, color("#18372E"), true);
        row.addView(currentTime, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(currentCountdown);

        focusAction = actionButton("Start focus", Color.WHITE, "#23845F");
        LinearLayout.LayoutParams focusLp = new LinearLayout.LayoutParams(-1, dp(46));
        focusLp.topMargin = dp(16);
        card.addView(focusAction, focusLp);
        focusAction.setTag("focusAction");
        focusAction.setOnClickListener(v -> toggleFocusCurrent());

        Button markDone = actionButton("Mark complete", color("#496257"), "#FFFFFF");
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(-1, dp(42));
        doneLp.topMargin = dp(8);
        card.addView(markDone, doneLp);
        markDone.setTag("currentDone");
        markDone.setOnClickListener(v -> completeCurrent());

        Button recover = actionButton("Catch up", color("#496257"), "#FFFFFF");
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(-1, dp(40));
        recLp.topMargin = dp(8);
        card.addView(recover, recLp);
        recover.setTag("recoveryButton");
        recover.setOnClickListener(v -> recoveryMode());
    }

    private void buildTodaySummary() {
        LinearLayout summary = horizontal();
        summary.setPadding(dp(14), dp(12), dp(14), dp(12));
        summary.setBackground(surfaceCard().getBackground());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(104));
        sp.bottomMargin = dp(14);
        root.addView(summary, sp);

        LinearLayout left = vertical();
        completionView = text("0 / 0 complete", 14, Color.WHITE, true);
        progressPercent = text("0% of the plan", 12, color("#91A197"), false);
        left.addView(completionView);
        left.addView(progressPercent);
        studyActualView = text("Study actual 0m / 0m planned", 11, color("#A5B5AA"), false);
        left.addView(studyActualView);
        summary.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout metricBox = horizontal();
        metricBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        metricBox.setBackground(bg("#152019", 13));
        plannedView = text("0h", 15, Color.WHITE, true);
        doneView = text("0h done", 11, color("#A5B5AA"), false);
        metricBox.addView(plannedView);
        metricBox.addView(spacerHorizontal(8));
        metricBox.addView(doneView);
        summary.addView(metricBox, new LinearLayout.LayoutParams(-2, dp(48)));
    }

    private void buildOfficeCardContents() {
        LinearLayout titleRow = horizontal();
        TextView title = text("OFFICE DAY", 12, color("#A5B5AA"), true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button history = actionButton("History", color("#DCE7DF"), "#263A31");
        history.setTextSize(11);
        titleRow.addView(history, new LinearLayout.LayoutParams(dp(76), dp(34)));
        history.setOnClickListener(v -> showAttendanceHistory());
        officeCard.addView(titleRow);

        officeStatus = text("Ready", 20, Color.WHITE, true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8);
        officeCard.addView(officeStatus, sp);

        officeTimes = text("No office time logged today", 11, color("#A5B5AA"), false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(5);
        officeCard.addView(officeTimes, tp);

        commuteView = text("Today • Office 0m  •  Commute 0m", 11, color("#A5B5AA"), false);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(7);
        officeCard.addView(commuteView, cp);

        officeAction = actionButton("CHECK IN", Color.WHITE, "#40B58A");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(48));
        ap.topMargin = dp(14);
        officeCard.addView(officeAction, ap);
        officeAction.setOnClickListener(v -> {
            if (selectedDateKey.equals(todayKey())) toggleOfficeAttendance();
            else editAttendanceDay(selectedDateKey);
        });

        commuteAction = actionButton("Start commute", color("#DCE7DF"), "#263A31");
        LinearLayout.LayoutParams cmp = new LinearLayout.LayoutParams(-1, dp(38));
        cmp.topMargin = dp(7);
        officeCard.addView(commuteAction, cmp);
        commuteAction.setOnClickListener(v -> {
            if (selectedDateKey.equals(todayKey())) toggleCommute();
            else showAttendanceDayDetail(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(parseDateKey(selectedDateKey)), selectedDateKey, getOfficeDay(selectedDateKey, false));
        });

        TextView hint = text("Tap CHECK IN when you enter. Tap CHECK OUT when you leave.", 10, color("#7F9689"), false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(8);
        officeCard.addView(hint, hp);
    }

    private void buildScheduleHeader() {
        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(10);
        root.addView(row, rp);

        TextView label = text("TODAY", 13, color("#A5B5AA"), true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView modeLabel = text("", 11, color("#82948A"), false);
        modeLabel.setTag("modeHeader");
        row.addView(modeLabel);

        Button more = actionButton("•••", color("#A5B5AA"), "#1B2621");
        more.setTextSize(14);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(44), dp(34));
        mp.leftMargin = dp(7);
        row.addView(more, mp);
        more.setOnClickListener(v -> openManageDialog());
    }

    private void render() {
        lastRenderedMinute = System.currentTimeMillis() / 60000L;
        Collections.sort(blocks, Comparator.comparingInt(b -> mins(b.start)));

        Calendar now = Calendar.getInstance();
        int nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        Date selectedDate = parseDateKey(selectedDateKey);
        String date = selectedDate == null
                ? selectedDateKey
                : new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(selectedDate);
        if (selectedDateKey.equals(todayKey())) date += "  •  Today";
        else if (selectedDateKey.compareTo(todayKey()) < 0) date += "  •  Past";
        else date += "  •  Planned";
        dateView.setText(date);

        String modeLabel = mode.equals("office") ? "WFO" : mode.equals("weekend") ? "Weekend routine" : "WFH";
        if (modeView != null) {
            modeView.setText(isWeekendDate(selectedDateKey) ? "WEEKEND" : "WEEKDAY");
        }
        for (String key : new String[]{"wfh", "office"}) {
            View modeChoice = root.findViewWithTag("mode-chip-" + key);
            if (modeChoice instanceof Button) {
                boolean selectedMode = ("wfh".equals(key) && "wfh".equals(mode))
                        || ("office".equals(key) && "office".equals(mode));
                ((Button) modeChoice).setTextColor(selectedMode ? Color.WHITE : color("#496257"));
                modeChoice.setBackground(bg(selectedMode ? "#286B55" : "#FFFFFF", 12));
            }
        }
        View headerMode = root.findViewWithTag("modeHeader");
        if (headerMode instanceof TextView) ((TextView) headerMode).setText(modeLabel.toUpperCase(Locale.US));
        if (todayRoutineEditButton != null) {
            // This action edits only the selected date's own schedule; it never applies
            // or writes back to any reusable template.
            todayRoutineEditButton.setText(selectedDateKey.equals(todayKey())
                    ? "Modify & Save Today's Routine"
                    : "Modify & Save This Date's Routine");
            todayRoutineEditButton.setVisibility(View.VISIBLE);
        }

        Block current = null, next = null;
        boolean viewingToday = selectedDateKey.equals(todayKey());
        for (Block b : blocks) {
            if (viewingToday && "pending".equals(b.status) && mins(b.start) <= nowMins && nowMins < mins(b.end)) {
                current = b;
                break;
            }
        }
        if (current == null) {
            for (Block b : blocks) {
                if (viewingToday && "pending".equals(b.status) && mins(b.start) > nowMins) {
                    next = b;
                    break;
                }
            }
        }

        int total = blocks.size(), done = 0, plannedMinutes = 0, plannedStudyMinutes = 0;
        long actualFocusMs = 0L, actualStudyMs = 0L;
        long metricsNow = System.currentTimeMillis();
        for (Block b : blocks) {
            int d = Math.max(0, mins(b.end) - mins(b.start));
            plannedMinutes += d;
            if ("done".equals(b.status)) done++;
            long actual = getActualDuration(b, metricsNow);
            actualFocusMs += actual;
            if (isStudyBlock(b.title)) {
                plannedStudyMinutes += d;
                actualStudyMs += actual;
            }
        }

        completionView.setText(done + " / " + total + " complete");
        int percent = total == 0 ? 0 : Math.round(done * 100f / total);
        progressPercent.setText(percent + "% of plan • " + syllabusProgressLabel());
        plannedView.setText(fmtShort(plannedMinutes));
        doneView.setText(fmtPreciseMillis(actualFocusMs) + " focus");
        if (studyActualView != null) {
            studyActualView.setText("Study actual " + fmtMillisForLabel(actualStudyMs)
                    + " / " + fmtShort(plannedStudyMinutes) + " planned");
        }

        updateCurrentCard(current, next, nowMins);
        renderOfficeCard();

        timeline.removeAllViews();
        if (blocks.isEmpty()) {
            TextView empty = text("Your day is empty.\nTap ＋ to add your first block.", 15, color("#A5B5AA"), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, dp(42));
            timeline.addView(empty);
        } else {
            int lastEnd = -1;
            for (Block b : blocks) {
                if (lastEnd >= 0 && mins(b.start) - lastEnd >= 10) {
                    final int gapStart = lastEnd;
                    LinearLayout gap = horizontal();
                    gap.setPadding(dp(8), 0, dp(8), 0);
                    TextView gapText = text("＋  " + fmtShort(mins(b.start) - lastEnd) + " free  •  Add", 11, color("#91A197"), false);
                    gapText.setGravity(Gravity.CENTER);
                    gap.addView(gapText, new LinearLayout.LayoutParams(-1, dp(32)));
                    gap.setOnClickListener(v -> openAddDialogAt(gapStart));
                    timeline.addView(gap);
                }
                addBlockView(b, current != null && current.id == b.id, nowMins);
                lastEnd = Math.max(lastEnd, mins(b.end));
            }
        }
        if ("plan".equals(activePage)) renderPlanPage();
        else if ("progress".equals(activePage)) renderProgressPage();
        else if ("office".equals(activePage)) renderOfficePage();
        else if ("more".equals(activePage)) renderMorePage();
        else if ("syllabus".equals(activePage)) refreshRoadmapSummary();
    }

    private void updateLiveBits() {
        if (!selectedDateKey.equals(todayKey())) return;
        if (closeExpiredFocusSessions()) {
            save();
            return;
        }
        int nowMins = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                + Calendar.getInstance().get(Calendar.MINUTE);
        Block current = null, next = null;
        for (Block b : blocks) {
            if ("pending".equals(b.status) && mins(b.start) <= nowMins && nowMins < mins(b.end)) {
                current = b;
                break;
            }
        }
        if (current == null) {
            for (Block b : blocks) {
                if ("pending".equals(b.status) && mins(b.start) > nowMins) {
                    next = b;
                    break;
                }
            }
        }
        updateCurrentCard(current, next, nowMins);
        updateLiveOfficeMetrics();
        if (doneView != null) {
            long actualMs = 0L;
            long actualStudyMs = 0L;
            int plannedStudyMinutes = 0;
            long now = System.currentTimeMillis();
            for (Block block : blocks) {
                long actual = getActualDuration(block, now);
                actualMs += actual;
                if (isStudyBlock(block.title)) {
                    actualStudyMs += actual;
                    plannedStudyMinutes += Math.max(0, mins(block.end) - mins(block.start));
                }
            }
            doneView.setText(fmtPreciseMillis(actualMs) + " focus");
            if (studyActualView != null) {
                studyActualView.setText("Study actual " + fmtMillisForLabel(actualStudyMs)
                        + " / " + fmtShort(plannedStudyMinutes) + " planned");
            }
        }
    }

    private void updateLiveOfficeMetrics() {
        if (officeCard == null || !"office".equals(mode) || !selectedDateKey.equals(todayKey())) return;
        try {
            JSONObject day = getOfficeDay(selectedDateKey, false);
            long now = System.currentTimeMillis();
            boolean open = hasOpenOfficeSession(day);
            boolean commuteOpen = hasOpenCommute(day);
            long office = officeDuration(day, now);
            long commute = commuteDuration(day, now);
            commuteView.setText("Today • Office " + (open ? fmtPreciseMillis(office) : fmtMillis(office))
                    + "  •  Commute " + (commuteOpen ? fmtPreciseMillis(commute) : fmtMillis(commute)));
        } catch (Exception e) {
            debugLog("Live office metric update failed: " + e.getMessage());
        }
    }

    private void updateCurrentCard(Block current, Block next, int nowMins) {
        View doneButton = root.findViewWithTag("currentDone");
        View recoveryButton = root.findViewWithTag("recoveryButton");
        Button focusButton = (Button) root.findViewWithTag("focusAction");
        boolean viewingToday = selectedDateKey.equals(todayKey());
        if (!viewingToday) {
            currentTitle.setText(selectedDateKey.compareTo(todayKey()) < 0 ? "Historical day" : "Planned day");
            Date d = parseDateKey(selectedDateKey);
            currentTime.setText(d == null ? selectedDateKey : new SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(d));
            currentCountdown.setText("Review timeline");
            if (doneButton != null) doneButton.setVisibility(View.GONE);
            if (recoveryButton != null) recoveryButton.setVisibility(View.GONE);
            if (focusButton != null) focusButton.setVisibility(View.GONE);
            return;
        }
        if (recoveryButton != null) recoveryButton.setVisibility(View.VISIBLE);
        Block target = current != null ? current : next;

        if (target == null) {
            currentTitle.setText("Day complete");
            currentTime.setText("Nice work.");
            currentCountdown.setText("✓");
            if (doneButton != null) doneButton.setVisibility(View.GONE);
            if (focusButton != null) focusButton.setVisibility(View.GONE);
            return;
        }

        if (doneButton != null) doneButton.setVisibility(current != null ? View.VISIBLE : View.GONE);
        if (focusButton != null) {
            focusButton.setVisibility(current != null ? View.VISIBLE : View.GONE);
            if (current != null) {
                focusButton.setText(current.actualStartedAt > 0
                        ? "Stop focus • " + fmtPreciseMillis(getActualDuration(current, System.currentTimeMillis()))
                        : "Start focus • " + fmtShort(Math.max(0, mins(current.end) - mins(current.start))));
            }
        }
        if (current != null) {
            currentTitle.setText(current.title);
            currentTime.setText(time12(current.start) + " – " + time12(current.end));
            long remainingMs = millisAtMinute(mins(current.end)) - System.currentTimeMillis();
            currentCountdown.setText(formatCountdown(remainingMs) + " left");
        } else {
            currentTitle.setText(next.title);
            currentTime.setText("Next • " + time12(next.start) + " – " + time12(next.end));
            long untilMs = millisAtMinute(mins(next.start)) - System.currentTimeMillis();
            currentCountdown.setText("in " + formatCountdown(untilMs));
        }
    }

    private void addBlockView(Block b, boolean current, int nowMins) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = dp(10);
        timeline.addView(row, rowLp);

        LinearLayout timeCol = vertical();
        timeCol.setGravity(Gravity.RIGHT);
        timeCol.addView(text(time12(b.start), 12, color("#A5B5AA"), true));
        timeCol.addView(text(time12(b.end), 10, color("#75877C"), false));
        row.addView(timeCol, new LinearLayout.LayoutParams(dp(63), -2));

        LinearLayout card = horizontal();
        card.setPadding(dp(13), dp(11), dp(8), dp(11));

        String surface, border;
        if ("done".equals(b.status)) {
            surface = "#EAF0E9"; border = "#2E8060";
        } else if ("skipped".equals(b.status)) {
            surface = "#F8E8E6"; border = "#B44248";
        } else if (current) {
            surface = "#DCEBE2"; border = "#286B55";
        } else {
            surface = "#FFFFFF"; border = "#DDE6DE";
        }

        GradientDrawable cardBg = bg(surface, 16);
        cardBg.setStroke(dp(current ? 2 : 1), color(border));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1);
        cardLp.leftMargin = dp(9);
        row.addView(card, cardLp);

        LinearLayout details = vertical();
        TextView title = text(b.title, 15, Color.WHITE, true);
        TextView meta = text(blockBehaviorLabel(b) + " • " + fmt(mins(b.end) - mins(b.start)), 11, color("#496257"), false);
        details.addView(title);
        details.addView(meta);

        if (current) {
            TextView state = text("IN PROGRESS", 10, color("#286B55"), true);
            state.setPadding(0, dp(6), 0, 0);
            details.addView(state);
        }

        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        if ("pending".equals(b.status)) {
            Button done = actionButton("✓", Color.WHITE, current ? "#40B58A" : "#263A31");
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(44), dp(44));
            dlp.leftMargin = dp(8);
            card.addView(done, dlp);
            done.setOnClickListener(v -> {
                stopFocusForBlock(b, System.currentTimeMillis());
                b.status = "done";
                save();
            });
        } else if ("done".equals(b.status)) {
            TextView check = text("✓", 20, color("#6FD6A3"), true);
            check.setGravity(Gravity.CENTER);
            card.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));
        } else {
            Button undo = actionButton("↩", color("#FFC5C9"), "#3A2729");
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(44), dp(44));
            ulp.leftMargin = dp(8);
            card.addView(undo, ulp);
            undo.setOnClickListener(v -> {
                b.status = "pending";
                save();
            });
        }

        card.setOnClickListener(v -> showBlockOptions(b));
    }

    private boolean isStudyBlock(String title) {
        String value = title == null ? "" : title.toLowerCase(Locale.US);
        return value.contains("gs") || value.contains("general studies") || value.contains("math")
                || value.contains("optional") || value.contains("csat") || value.contains("essay")
                || value.contains("upsc") || value.contains("revision") || value.contains("interview prep")
                || value.contains("study") || value.contains("syllabus");
    }

    private String priorityLabel(String value) {
        if (value == null || value.isEmpty()) return "Normal";
        String result = value;
        return Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }

    private String blockBehaviorLabel(Block b) {
        return ("fixed".equals(b.rigidity) ? "Fixed" : "Flexible") + " • " + priorityLabel(b.priority);
    }

    private Block blockFromJson(JSONObject o, String statusFallback) {
        String priority = o.optString("priority", "flexible");
        String status = o.optString("status", statusFallback);
        Block b;
        if (o.has("rigidity")) {
            b = new Block(o.optLong("id", System.currentTimeMillis()),
                    o.optString("start", "06:00"), o.optString("end", "07:00"),
                    o.optString("title", "Block"), priority, o.optString("rigidity", "flexible"), status);
        } else {
            b = new Block(o.optLong("id", System.currentTimeMillis()),
                    o.optString("start", "06:00"), o.optString("end", "07:00"),
                    o.optString("title", "Block"), priority, status);
        }
        b.actualMs = o.optLong("actualMs", 0L);
        b.actualStartedAt = o.optLong("actualStartedAt", 0L);
        String owner = o.optString("templateOwner", "").trim();
        b.templateOwner = owner.isEmpty() ? null : owner;
        return b;
    }

    private void showBlockOptions(Block b) {
        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));

        LinearLayout summary = surfaceCard();
        summary.addView(text("SCHEDULE", 10, color("#2E8060"), true));
        summary.addView(text(time12(b.start) + "  →  " + time12(b.end), 24, color("#18372E"), true));
        summary.addView(text(fmt(mins(b.end) - mins(b.start)) + " planned  •  " + blockBehaviorLabel(b),
                12, color("#687A70"), false));
        String statusLabel = "done".equals(b.status) ? "Completed"
                : "skipped".equals(b.status) ? "Skipped" : "Pending";
        summary.addView(text("Status  •  " + statusLabel, 12, color("#687A70"), true));
        long actual = getActualDuration(b, System.currentTimeMillis());
        if (actual > 0) {
            summary.addView(text("Focus logged  •  " + fmtPreciseMillis(actual), 12, color("#286B55"), true));
        }
        content.addView(summary);

        LinearLayout actions = surfaceCard();
        sectionLabel(actions, "TASK ACTIONS");
        final android.app.Dialog[] detailDialog = new android.app.Dialog[1];
        String statusAction = "done".equals(b.status) ? "Reopen task"
                : "skipped".equals(b.status) ? "Restore task" : "Mark complete";
        actions.addView(primaryButton(statusAction, () -> {
            if (detailDialog[0] != null) detailDialog[0].dismiss();
            if ("done".equals(b.status) || "skipped".equals(b.status)) {
                b.status = "pending";
            } else {
                stopFocusForBlock(b, System.currentTimeMillis());
                b.status = "done";
            }
            save();
        }), new LinearLayout.LayoutParams(-1, dp(44)));

        if ("pending".equals(b.status)) {
            Button skip = secondaryButton("Skip this task", () -> {
                if (detailDialog[0] != null) detailDialog[0].dismiss();
                stopFocusForBlock(b, System.currentTimeMillis());
                b.status = "skipped";
                save();
            });
            LinearLayout.LayoutParams skipLp = new LinearLayout.LayoutParams(-1, dp(42));
            skipLp.topMargin = dp(8);
            actions.addView(skip, skipLp);
        }

        Button delete = actionButton("Delete task", color("#B44248"), "#F8E8E6");
        delete.setTextSize(12);
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(-1, dp(42));
        deleteLp.topMargin = dp(8);
        actions.addView(delete, deleteLp);
        delete.setOnClickListener(v -> {
            if (detailDialog[0] != null) detailDialog[0].dismiss();
            new AlertDialog.Builder(this)
                    .setTitle("Delete this task?")
                    .setMessage("\"" + b.title + "\" will be removed from this day's plan. Its recorded focus time will no longer be attached to a task.")
                    .setPositiveButton("Delete", (d, w) -> {
                        stopFocusForBlock(b, System.currentTimeMillis());
                        blocks.remove(b);
                        save();
                    })
                    .setNegativeButton("Keep task", null)
                    .show();
        });
        content.addView(actions);

        detailDialog[0] = showFullScreenViewer("TASK DETAILS", b.title,
                "Edit the plan, record completion, or skip it. Timing and focus history are shown separately.",
                content, "Edit task", () -> editBlockDialog(b));
    }

        private long getActualDuration(Block b, long now) {
        long total = Math.max(0L, b.actualMs);
        if (b.actualStartedAt > 0) {
            long end = millisAtDateMinute(selectedDateKey, mins(b.end));
            long effectiveEnd = Math.min(now, end);
            if (effectiveEnd > b.actualStartedAt) total += effectiveEnd - b.actualStartedAt;
        }
        return total;
    }

    private void stopFocusForBlock(Block block, long now) {
        if (block == null || block.actualStartedAt <= 0) return;
        long scheduledEnd = millisAtDateMinute(selectedDateKey, mins(block.end));
        long stopAt = Math.min(now, scheduledEnd);
        if (stopAt > block.actualStartedAt) block.actualMs += stopAt - block.actualStartedAt;
        block.actualStartedAt = 0L;
        debugLog("Focus stopped: " + block.title + " actualMs=" + block.actualMs);
    }

    private boolean closeExpiredFocusSessions() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Block block : blocks) {
            if (block.actualStartedAt <= 0) continue;
            long end = millisAtDateMinute(selectedDateKey, mins(block.end));
            if (now >= end) {
                block.actualMs += Math.max(0L, end - block.actualStartedAt);
                block.actualStartedAt = 0L;
                debugLog("Focus auto-stopped at block boundary: " + block.title
                        + " actualMs=" + block.actualMs);
                changed = true;
            }
        }
        return changed;
    }

    private void toggleFocusCurrent() {
        if (!selectedDateKey.equals(todayKey())) return;
        int nowMins = currentMinutes();
        Block current = null;
        for (Block b : blocks) {
            if ("pending".equals(b.status) && mins(b.start) <= nowMins && nowMins < mins(b.end)) {
                current = b;
                break;
            }
        }
        if (current == null) {
            Toast.makeText(this, "Focus timer works on the active task.", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        if (current.actualStartedAt > 0) {
            current.actualMs += Math.max(0L, now - current.actualStartedAt);
            current.actualStartedAt = 0L;
            debugLog("Focus stopped: " + current.title + " actualMs=" + current.actualMs);
        } else {
            for (Block other : blocks) {
                if (other != current && other.actualStartedAt > 0) {
                    long otherEnd = millisAtDateMinute(selectedDateKey, mins(other.end));
                    other.actualMs += Math.max(0L, Math.min(now, otherEnd) - other.actualStartedAt);
                    other.actualStartedAt = 0L;
                    debugLog("Focus auto-stopped when switching tasks: " + other.title);
                }
            }
            current.actualStartedAt = now;
            debugLog("Focus started: " + current.title);
        }
        save();
    }

    private void completeCurrent() {
        int nowMins = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                + Calendar.getInstance().get(Calendar.MINUTE);
        for (Block b : blocks) {
            if ("pending".equals(b.status) && mins(b.start) <= nowMins && nowMins < mins(b.end)) {
                stopFocusForBlock(b, System.currentTimeMillis());
                b.status = "done";
                save();
                return;
            }
        }
        Toast.makeText(this, "Nothing is in progress right now.", Toast.LENGTH_SHORT).show();
    }

    private void openAddDialog() {
        openAddDialogAt(-1);
    }

    private void openAddDialogAt(int suggestedStart) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(2));

        EditText title = new EditText(this);
        title.setHint("What are you doing?");
        title.setTextColor(color("#18372E"));
        title.setHintTextColor(color("#82948A"));
        title.setSingleLine(true);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView startLabel = text("START", 10, color("#91A197"), true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(10);
        form.addView(startLabel, slp);

        LinearLayout starts = horizontal();
        form.addView(starts, new LinearLayout.LayoutParams(-1, dp(42)));
        String[] startNames = {"Next free", "Now", "Pick time"};
        int[] startChoice = {suggestedStart >= 0 ? 2 : 0};
        for (int i = 0; i < startNames.length; i++) {
            final int idx = i;
            Button b = chip(startNames[i]);
            if (i == 0) b.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(6);
            starts.addView(b, lp);
            b.setOnClickListener(v -> {
                startChoice[0] = idx;
                for (int j = 0; j < starts.getChildCount(); j++) {
                    ((Button) starts.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    ((Button) starts.getChildAt(j)).setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        if (suggestedStart >= 0 && starts.getChildCount() >= 3) {
            for (int j = 0; j < starts.getChildCount(); j++) {
                ((Button) starts.getChildAt(j)).setTextColor(j == 2 ? Color.WHITE : color("#D6E1D8"));
                ((Button) starts.getChildAt(j)).setBackground(bg(j == 2 ? "#245B45" : "#1B2621", 12));
            }
        }

        TextView durationLabel = text("DURATION", 10, color("#91A197"), true);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
        dlp.topMargin = dp(12);
        form.addView(durationLabel, dlp);

        LinearLayout durations = horizontal();
        form.addView(durations, new LinearLayout.LayoutParams(-1, dp(42)));
        String[] durationNames = {"30m", "45m", "1h", "2h"};
        int[] durationChoice = {30};
        for (int i = 0; i < durationNames.length; i++) {
            final int value = i == 0 ? 30 : i == 1 ? 45 : i == 2 ? 60 : 120;
            final int idx = i;
            Button b = chip(durationNames[i]);
            if (i == 0) b.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(6);
            durations.addView(b, lp);
            b.setOnClickListener(v -> {
                durationChoice[0] = value;
                for (int j = 0; j < durations.getChildCount(); j++) {
                    ((Button) durations.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    ((Button) durations.getChildAt(j)).setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        Button custom = chip("Custom");
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(dp(72), dp(38));
        customLp.leftMargin = dp(6);
        durations.addView(custom, customLp);
        custom.setOnClickListener(v -> durationChoice[0] = -1);

        TextView rigidityLabel = text("RIGIDITY", 10, color("#91A197"), true);
        LinearLayout.LayoutParams rigidityLabelLp = new LinearLayout.LayoutParams(-1, -2);
        rigidityLabelLp.topMargin = dp(12);
        form.addView(rigidityLabel, rigidityLabelLp);
        LinearLayout rigidities = horizontal();
        form.addView(rigidities, new LinearLayout.LayoutParams(-1, dp(40)));
        String[] rigidityNames = {"Fixed time", "Flexible time"};
        String[] rigidityValues = {"fixed", "flexible"};
        String[] selectedRigidity = {"flexible"};
        String[] selectedPriority = {"normal"};
        String[] selectedBehavior = {"flexible:normal"};
        for (int i = 0; i < rigidityNames.length; i++) {
            final int idx = i;
            Button b = chip(rigidityNames[i]);
            b.setTextColor(i == 1 ? Color.WHITE : color("#D6E1D8"));
            b.setBackground(bg(i == 1 ? "#245B45" : "#1B2621", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(6);
            rigidities.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedRigidity[0] = rigidityValues[idx];
                selectedBehavior[0] = selectedRigidity[0] + ":" + selectedPriority[0];
                for (int j = 0; j < rigidities.getChildCount(); j++) {
                    ((Button) rigidities.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    ((Button) rigidities.getChildAt(j)).setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        TextView priorityTitle = text("PRIORITY", 10, color("#91A197"), true);
        LinearLayout.LayoutParams priorityTitleLp = new LinearLayout.LayoutParams(-1, -2);
        priorityTitleLp.topMargin = dp(10);
        form.addView(priorityTitle, priorityTitleLp);
        LinearLayout priorities = horizontal();
        form.addView(priorities, new LinearLayout.LayoutParams(-1, dp(40)));
        String[] priorityNames = {"Critical", "Important", "Normal", "Optional"};
        String[] priorityValues = {"critical", "important", "normal", "optional"};
        for (int i = 0; i < priorityNames.length; i++) {
            final int idx = i;
            Button b = chip(priorityNames[i]);
            b.setTextColor(i == 2 ? Color.WHITE : color("#D6E1D8"));
            b.setBackground(bg(i == 2 ? "#245B45" : "#1B2621", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(4);
            priorities.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedPriority[0] = priorityValues[idx];
                selectedBehavior[0] = selectedRigidity[0] + ":" + selectedPriority[0];
                for (int j = 0; j < priorities.getChildCount(); j++) {
                    ((Button) priorities.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    ((Button) priorities.getChildAt(j)).setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        TextView repeatLabel = text("REPEAT", 10, color("#91A197"), true);
        LinearLayout.LayoutParams repeatLabelLp = new LinearLayout.LayoutParams(-1, -2);
        repeatLabelLp.topMargin = dp(12);
        form.addView(repeatLabel, repeatLabelLp);

        LinearLayout repeats = horizontal();
        form.addView(repeats, new LinearLayout.LayoutParams(-1, dp(42)));
        String[] repeatNames = {"Never", "Weekdays", "Daily"};
        String[] repeatValues = {"never", "weekdays", "daily"};
        String[] selectedRepeat = {"never"};
        for (int i = 0; i < repeatNames.length; i++) {
            final int idx = i;
            Button b = chip(repeatNames[i]);
            if (i == 0) {
                b.setTextColor(Color.WHITE);
                b.setBackground(bg("#245B45", 12));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(6);
            repeats.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedRepeat[0] = repeatValues[idx];
                for (int j = 0; j < repeats.getChildCount(); j++) {
                    ((Button) repeats.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    ((Button) repeats.getChildAt(j)).setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        form.setBackgroundColor(color("#F4F6F1"));
        android.app.Dialog addDialog = new android.app.Dialog(this);
        addDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout editor = vertical();
        editor.setBackgroundColor(color("#F4F6F1"));
        LinearLayout topBar = horizontal();
        topBar.setPadding(dp(18), dp(12), dp(18), dp(10));
        Button back = secondaryButton("‹", () -> addDialog.dismiss());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(44), dp(42));
        backLp.rightMargin = dp(12);
        topBar.addView(back, backLp);
        LinearLayout heading = vertical();
        heading.addView(text("PLAN A TASK", 10, color("#2E8060"), true));
        heading.addView(text("Add to your day", 22, color("#18372E"), true));
        heading.addView(text("Choose the time, duration and importance.", 11, color("#687A70"), false));
        topBar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        editor.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        editor.addView(formScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = horizontal();
        footer.setPadding(dp(16), dp(10), dp(16), dp(12));
        footer.setBackgroundColor(Color.WHITE);
        Button cancel = secondaryButton("Cancel", () -> addDialog.dismiss());
        Button saveBlock = primaryButton("Add block", () -> {
            String name = title.getText().toString().trim();
            if (name.isEmpty()) {
                title.setError("Give this block a name");
                title.requestFocus();
                return;
            }
            int start = startChoice[0] == 0 ? nextFreeStart()
                    : startChoice[0] == 1 ? currentMinutes() : -1;
            if (startChoice[0] == 2) {
                addDialog.dismiss();
                int defaultStart = suggestedStart >= 0 ? suggestedStart : currentMinutes();
                TimePickerDialog picker = new TimePickerDialog(this, (tv, h, m) ->
                        finishAddBlockRecurring(name, h * 60 + m, durationChoice[0], selectedBehavior[0], selectedRepeat[0]),
                        defaultStart / 60, defaultStart % 60, true);
                picker.setTitle("Start time");
                picker.show();
                return;
            }
            if (durationChoice[0] == -1) {
                addDialog.dismiss();
                TimePickerDialog picker = new TimePickerDialog(this, (tv, h, m) ->
                        addCustomTimedBlockRecurring(name, start, h * 60 + m, selectedBehavior[0], selectedRepeat[0]),
                        Math.min(23, start / 60), start % 60, true);
                picker.setTitle("End time");
                picker.show();
                return;
            }
            addDialog.dismiss();
            finishAddBlockRecurring(name, start, durationChoice[0], selectedBehavior[0], selectedRepeat[0]);
        });
        footer.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        saveLp.leftMargin = dp(8);
        footer.addView(saveBlock, saveLp);
        editor.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        addDialog.setContentView(editor);
        android.view.Window win = addDialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color("#F4F6F1")));
            win.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT);
            win.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        addDialog.show();
        if (win != null) win.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT);
    }

    private void finishAddBlockRecurring(String name, int start, int duration, String behavior, String repeat) {
        if ("never".equals(repeat)) {
            finishAddBlock(name, start, duration, behavior);
            return;
        }
        int end = start + duration;
        if (duration <= 0 || end > 1439) {
            Toast.makeText(this, "That block would run past midnight.", Toast.LENGTH_SHORT).show();
            return;
        }
        addRecurringBlocks(name, start, end, behavior, repeat);
    }

    private void addCustomTimedBlockRecurring(String name, int start, int end, String behavior, String repeat) {
        if ("never".equals(repeat)) {
            addCustomTimedBlock(name, start, end, behavior);
            return;
        }
        if (end <= start) {
            Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
            return;
        }
        addRecurringBlocks(name, start, end, behavior, repeat);
    }

    private void addRecurringBlocks(String name, int start, int end, String behavior, String repeat) {
        Date base = parseDateKey(selectedDateKey);
        if (base == null) {
            finishAddBlock(name, start, end - start, behavior);
            return;
        }
        int count = 0;
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(base);
        for (int i = 0; i < 30; i++) {
            int dow = cursor.get(Calendar.DAY_OF_WEEK);
            boolean include = "daily".equals(repeat) || (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY);
            if (include) {
                String key = keyForDate(cursor.getTime());
                ArrayList<Block> dayBlocks = readBlocksForDate(key);
                dayBlocks.add(new Block(System.currentTimeMillis() + i + count,
                        toTime(start), toTime(end), name, behavior, "pending"));
                writeBlocksForDate(key, dayBlocks);
                count++;
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }
        loadSelectedDate();
        Toast.makeText(this, "Added " + count + " occurrences.", Toast.LENGTH_SHORT).show();
        render();
    }

    private ArrayList<Block> readBlocksForDate(String dayKey) {
        ArrayList<Block> result = new ArrayList<>();
        String raw = getPrefs().getString(blocksKey(dayKey), null);
        if (raw == null && dayKey.equals(todayKey())) raw = getPrefs().getString(KEY, null);
        if (raw == null) return result;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(blockFromJson(o, "pending"));
            }
        } catch (Exception e) {
            result.clear();
            debugLog("Could not read blocks for " + dayKey + ": " + e.getMessage());
        }
        return result;
    }

    private void writeBlocksForDate(String dayKey, ArrayList<Block> dayBlocks) {
        try {
            JSONArray arr = new JSONArray();
            for (Block b : dayBlocks) {
                JSONObject o = new JSONObject();
                o.put("id", b.id);
                o.put("start", b.start);
                o.put("end", b.end);
                o.put("title", b.title);
                o.put("priority", b.priority);
                o.put("rigidity", b.rigidity);
                o.put("status", b.status);
                arr.put(o);
            }
            getPrefs().edit().putString(blocksKey(dayKey), arr.toString()).apply();
        } catch (Exception e) {
            debugLog("Could not save blocks for " + dayKey + ": " + e.getMessage());
            Toast.makeText(this, "Could not save the schedule for that date.", Toast.LENGTH_LONG).show();
        }
    }

    private void finishAddBlock(String name, int start, int duration, String behavior) {
        if (duration <= 0) {
            Toast.makeText(this, "Choose a valid duration.", Toast.LENGTH_SHORT).show();
            return;
        }
        int end = start + duration;
        if (end > 1439) {
            Toast.makeText(this, "That block would run past midnight.", Toast.LENGTH_SHORT).show();
            return;
        }
        blocks.add(new Block(System.currentTimeMillis(), toTime(start), toTime(end), name, behavior, "pending"));
        save();
    }

    private void addCustomTimedBlock(String name, int start, int end, String behavior) {
        if (end <= start) {
            Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
            return;
        }
        blocks.add(new Block(System.currentTimeMillis(), toTime(start), toTime(end), name, behavior, "pending"));
        save();
    }

    private int currentMinutes() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private int currentHour() { return Calendar.getInstance().get(Calendar.HOUR_OF_DAY); }
    private int currentMinute() { return Calendar.getInstance().get(Calendar.MINUTE); }

    private int nextFreeStart() {
        int now = roundUp5(currentMinutes());
        int candidate = now;
        for (Block b : blocks) {
            if ("done".equals(b.status) || "skipped".equals(b.status)) continue;
            if (mins(b.start) <= candidate && candidate < mins(b.end)) candidate = mins(b.end);
        }
        return Math.min(1430, candidate);
    }

    private void editBlockDialog(Block b) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(4));
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setText(b.title);
        title.setHint("Block name");
        title.setTextColor(color("#18372E"));
        title.setHintTextColor(color("#82948A"));
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(50)));
        final int[] start = {mins(b.start)};
        final int[] end = {mins(b.end)};
        Button startButton = chip("Start • " + time12(b.start));
        Button endButton = chip("End • " + time12(b.end));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(40));
        startLp.topMargin = dp(8);
        form.addView(startButton, startLp);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(-1, dp(40));
        endLp.topMargin = dp(6);
        form.addView(endButton, endLp);
        startButton.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(this, (tv, h, m) -> {
                start[0] = h * 60 + m;
                startButton.setText("Start • " + time12(toTime(start[0])));
            }, start[0] / 60, start[0] % 60, true);
            picker.show();
        });
        endButton.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(this, (tv, h, m) -> {
                end[0] = h * 60 + m;
                endButton.setText("End • " + time12(toTime(end[0])));
            }, end[0] / 60, end[0] % 60, true);
            picker.show();
        });

        TextView rigidityLabel = text("RIGIDITY", 10, color("#91A197"), true);
        LinearLayout.LayoutParams rigidityLabelLp = new LinearLayout.LayoutParams(-1, -2);
        rigidityLabelLp.topMargin = dp(12);
        form.addView(rigidityLabel, rigidityLabelLp);
        LinearLayout rigidities = horizontal();
        form.addView(rigidities, new LinearLayout.LayoutParams(-1, dp(40)));
        String[] rigidityNames = {"Fixed time", "Flexible time"};
        String[] rigidityValues = {"fixed", "flexible"};
        String[] selectedRigidity = {b.rigidity};
        for (int i = 0; i < rigidityNames.length; i++) {
            final int idx = i;
            Button button = chip(rigidityNames[i]);
            boolean selected = rigidityValues[i].equals(selectedRigidity[0]);
            button.setTextColor(selected ? Color.WHITE : color("#D6E1D8"));
            button.setBackground(bg(selected ? "#245B45" : "#1B2621", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(6);
            rigidities.addView(button, lp);
            button.setOnClickListener(v -> {
                selectedRigidity[0] = rigidityValues[idx];
                for (int j = 0; j < rigidities.getChildCount(); j++) {
                    Button child = (Button) rigidities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    child.setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        TextView priorityTitle = text("PRIORITY", 10, color("#91A197"), true);
        LinearLayout.LayoutParams priorityTitleLp = new LinearLayout.LayoutParams(-1, -2);
        priorityTitleLp.topMargin = dp(10);
        form.addView(priorityTitle, priorityTitleLp);
        LinearLayout priorities = horizontal();
        form.addView(priorities, new LinearLayout.LayoutParams(-1, dp(40)));
        String[] priorityNames = {"Critical", "Important", "Normal", "Optional"};
        String[] priorityValues = {"critical", "important", "normal", "optional"};
        String[] selectedPriority = {b.priority};
        for (int i = 0; i < priorityNames.length; i++) {
            final int idx = i;
            Button button = chip(priorityNames[i]);
            boolean selected = priorityValues[i].equals(selectedPriority[0]);
            button.setTextColor(selected ? Color.WHITE : color("#D6E1D8"));
            button.setBackground(bg(selected ? "#245B45" : "#1B2621", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(4);
            priorities.addView(button, lp);
            button.setOnClickListener(v -> {
                selectedPriority[0] = priorityValues[idx];
                for (int j = 0; j < priorities.getChildCount(); j++) {
                    Button child = (Button) priorities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    child.setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }
        showFullScreenEditor("EDIT TASK", "Edit block",
                "Adjust timing, fixed/flexible behaviour and priority.", form, () -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        title.setError("Give this block a name");
                        title.requestFocus();
                        return false;
                    }
                    if (end[0] <= start[0]) {
                        Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    stopFocusForBlock(b, System.currentTimeMillis());
                    b.title = name;
                    b.start = toTime(start[0]);
                    b.end = toTime(end[0]);
                    b.rigidity = selectedRigidity[0];
                    b.priority = selectedPriority[0];
                    save();
                    return true;
                });
    }



    private void recoveryMode() {
        if (!selectedDateKey.equals(todayKey())) {
            Toast.makeText(this, "Catch up is available for today.", Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.sort(blocks, Comparator.comparingInt(b -> mins(b.start)));
        int nowMins = currentMinutes();
        int activeIndex = -1;
        int overdueMovable = 0;
        int overdueFixed = 0;
        int fixedAnchors = 0;
        int movablePending = 0;
        Block active = null;

        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if ("pending".equals(b.status) && mins(b.start) <= nowMins && nowMins < mins(b.end)) {
                active = b;
                activeIndex = i;
                break;
            }
        }
        for (Block b : blocks) {
            if (!"pending".equals(b.status) || b == active) continue;
            if ("fixed".equals(b.rigidity)) {
                if (mins(b.end) <= nowMins) overdueFixed++;
                else fixedAnchors++;
            } else {
                movablePending++;
                if (mins(b.end) <= nowMins) overdueMovable++;
            }
        }

        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));

        LinearLayout snapshot = surfaceCard();
        snapshot.addView(text("TODAY AT A GLANCE", 10, color("#2E8060"), true));
        snapshot.addView(text(overdueMovable + " overdue flexible task(s)", 20, color("#18372E"), true));
        snapshot.addView(text(fixedAnchors + " future fixed commitment(s) will stay at their scheduled times.",
                12, color("#687A70"), false));
        snapshot.addView(text(overdueFixed + " fixed commitment(s) are already in the past and will not be moved.",
                12, color("#687A70"), false));
        content.addView(snapshot);

        LinearLayout rules = surfaceCard();
        sectionLabel(rules, "HOW CATCH UP WORKS");
        String[][] ruleRows = {
                {"1", "Keep the active task", active == null ? "No task is active now." : active.title + " stays where it is."},
                {"2", "Protect fixed times", "Future Fixed blocks are hard anchors. DayFlow will not move them."},
                {"3", "Prioritise the rest", "Critical → Important → Normal → Optional. Flexible tasks can move."},
                {"4", "Respect real durations", "Tasks keep their scheduled duration. Work that does not fit remains unchanged and is reported."}
        };
        for (String[] rule : ruleRows) {
            LinearLayout row = horizontal();
            row.setGravity(Gravity.TOP);
            TextView number = text(rule[0], 13, color("#286B55"), true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(bg("#DCEBE2", 18));
            LinearLayout.LayoutParams numberLp = new LinearLayout.LayoutParams(dp(28), dp(28));
            numberLp.rightMargin = dp(10);
            row.addView(number, numberLp);
            LinearLayout detail = vertical();
            detail.addView(text(rule[1], 13, color("#18372E"), true));
            TextView desc = text(rule[2], 11, color("#687A70"), false);
            desc.setLineSpacing(dp(1), 1f);
            detail.addView(desc);
            row.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(12);
            rules.addView(row, rowLp);
        }
        content.addView(rules);

        if (movablePending == 0) {
            LinearLayout none = surfaceCard();
            none.addView(text("Nothing to reflow", 14, color("#18372E"), true));
            none.addView(text("There are no pending flexible tasks left in today's plan.", 12, color("#687A70"), false));
            content.addView(none);
        }

        final int selectedActiveIndex = activeIndex;
        final int selectedMovablePending = movablePending;
        showFullScreenViewer("RECOVERY PLANNER", "Catch up",
                "Review the rules before DayFlow rearranges today's remaining work.", content,
                "Rebuild remaining day", () -> {
                    if (selectedMovablePending == 0) {
                        Toast.makeText(this, "No flexible tasks need reflowing.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reflowRemainingDay(selectedActiveIndex);
                });
    }

        private int recoveryRank(Block block) {
        if ("critical".equals(block.priority)) return 0;
        if ("important".equals(block.priority)) return 1;
        if ("normal".equals(block.priority)) return 2;
        if ("optional".equals(block.priority)) return 3;
        return 2;
    }

    private void reflowRemainingDay(int activeIndex) {
        Collections.sort(blocks, Comparator.comparingInt(b -> mins(b.start)));
        int nowMins = currentMinutes();
        Block active = activeIndex >= 0 && activeIndex < blocks.size() ? blocks.get(activeIndex) : null;
        if (active != null && !"pending".equals(active.status)) active = null;

        int cursor = active == null ? roundUp5(nowMins) : Math.max(nowMins, mins(active.end));
        ArrayList<Block> anchors = new ArrayList<>();
        ArrayList<Block> movable = new ArrayList<>();
        for (Block block : blocks) {
            if (!"pending".equals(block.status) || block == active) continue;
            if ("fixed".equals(block.rigidity)) {
                if (mins(block.end) > nowMins) anchors.add(block);
            } else {
                movable.add(block);
            }
        }

        anchors.sort(Comparator.comparingInt(b -> mins(b.start)));
        movable.sort((a, b) -> {
            int priority = Integer.compare(recoveryRank(a), recoveryRank(b));
            return priority != 0 ? priority : Integer.compare(mins(a.start), mins(b.start));
        });

        int anchorIndex = 0;
        int moved = 0;
        int overdueRecovered = 0;
        ArrayList<String> movedDetails = new ArrayList<>();
        ArrayList<String> unplacedDetails = new ArrayList<>();

        for (Block block : movable) {
            int duration = Math.max(1, mins(block.end) - mins(block.start));
            while (anchorIndex < anchors.size() && mins(anchors.get(anchorIndex).start) < cursor) {
                cursor = Math.max(cursor, mins(anchors.get(anchorIndex).end));
                anchorIndex++;
            }

            if (anchorIndex < anchors.size() && cursor + duration > mins(anchors.get(anchorIndex).start)) {
                cursor = Math.max(cursor, mins(anchors.get(anchorIndex).end));
                anchorIndex++;
                while (anchorIndex < anchors.size() && mins(anchors.get(anchorIndex).start) < cursor) {
                    cursor = Math.max(cursor, mins(anchors.get(anchorIndex).end));
                    anchorIndex++;
                }
            }

            if (cursor + duration > 1440) {
                unplacedDetails.add(block.title + " • " + fmt(duration) + " • no remaining space");
                continue;
            }

            String oldStart = block.start;
            String oldEnd = block.end;
            boolean wasOverdue = mins(oldEnd) <= nowMins;
            block.start = toTime(cursor);
            block.end = toTime(cursor + duration);
            cursor += duration;

            if (!oldStart.equals(block.start) || !oldEnd.equals(block.end)) {
                moved++;
                if (wasOverdue) overdueRecovered++;
                movedDetails.add(block.title + "  •  " + time12(oldStart) + " → " + time12(block.start)
                        + "  •  " + fmt(duration));
            }
        }

        save();
        debugLog("Catch up result: moved=" + moved + " recoveredOverdue=" + overdueRecovered
                + " unplaced=" + unplacedDetails.size() + " active=" + (active == null ? "none" : active.title));

        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));

        LinearLayout summary = surfaceCard();
        summary.addView(text(moved == 0 ? "Plan left unchanged" : moved + " task(s) rescheduled",
                21, color("#18372E"), true));
        summary.addView(text(overdueRecovered + " overdue task(s) recovered • "
                        + anchors.size() + " future Fixed commitment(s) protected",
                12, color("#687A70"), false));
        content.addView(summary);

        if (!movedDetails.isEmpty()) {
            LinearLayout changed = surfaceCard();
            sectionLabel(changed, "RESCHEDULED");
            for (String detail : movedDetails) {
                addManageRow(changed, detail.substring(0, detail.indexOf("  •  ")),
                        detail.substring(detail.indexOf("  •  ") + 5), () -> {});
            }
            content.addView(changed);
        }

        if (!unplacedDetails.isEmpty()) {
            LinearLayout unplaced = surfaceCard();
            unplaced.addView(text("Could not fit", 15, color("#B44248"), true));
            unplaced.addView(text("These tasks were left at their original times rather than shortened or silently deleted.",
                    12, color("#687A70"), false));
            for (String detail : unplacedDetails) {
                addManageRow(unplaced, detail.substring(0, detail.indexOf(" • ")),
                        detail.substring(detail.indexOf(" • ") + 3), () -> {});
            }
            content.addView(unplaced);
        } else if (moved == 0) {
            LinearLayout unchanged = surfaceCard();
            unchanged.addView(text("No timing changes were needed.", 14, color("#18372E"), true));
            unchanged.addView(text("The remaining flexible tasks already fit around the protected commitments.",
                    12, color("#687A70"), false));
            content.addView(unchanged);
        }

        showFullScreenViewer("RECOVERY RESULT", "Catch up complete",
                "Here is what changed. Fixed commitments were not moved; tasks that did not fit remain unchanged.",
                content, "Return to today", () -> switchPage("home"));
    }

    private void choosePlanPreviewMode(String key) {
        planPreviewMode = key;
        renderPlanPage();
    }

    private void chooseMode(String key) {
        if (!"wfh".equals(key) && !"office".equals(key)) return;
        if (key.equals(mode)) return;
        // WFH/WFO changes only the work-arrangement setting for this date. It never
        // replaces date-specific blocks and never changes a reusable routine template.
        mode = key;
        SharedPreferences.Editor selection = getPrefs().edit()
                .putString(modeKey(selectedDateKey), mode);
        if (selectedDateKey.equals(todayKey())) selection.putString(MODE_KEY, mode);
        selection.apply();
        render();
        renderOfficePage();
        Toast.makeText(this,
                ("office".equals(mode) ? "WFO" : "WFH")
                        + " selected. The saved routine and reusable templates are unchanged.",
                Toast.LENGTH_SHORT).show();
    }

    private boolean isWeekendDate(String dateKey) {
        Date selected = parseDateKey(dateKey);
        if (selected == null) return false;
        Calendar day = Calendar.getInstance();
        day.setTime(selected);
        int weekday = day.get(Calendar.DAY_OF_WEEK);
        return weekday == Calendar.SATURDAY || weekday == Calendar.SUNDAY;
    }

    /**
     * Opens a staged editor for this date's actual schedule. The working copy is not
     * persisted until Save, and commit writes only through save() to the date key.
     * Reusable template storage is never touched by this flow.
     */
    private void openTodayRoutineEditor() {
        final String editingDate = selectedDateKey;
        final ArrayList<Block> draft = new ArrayList<>();
        for (Block block : blocks) draft.add(copyBlockForDailyEditor(block));

        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(8), dp(16), dp(16));
        LinearLayout protection = surfaceCard();
        protection.addView(text("DATE-ONLY CHANGES", 10, color("#2E8060"), true));
        protection.addView(text("Changes here are saved only to "
                + (editingDate.equals(todayKey()) ? "today" : editingDate)
                + ". Shared, WFH, Office and Weekend templates will not be changed.",
                12, color("#687A70"), false));
        content.addView(protection);

        LinearLayout draftRows = vertical();
        LinearLayout.LayoutParams rowsLp = new LinearLayout.LayoutParams(-1, -2);
        rowsLp.topMargin = dp(10);
        content.addView(draftRows, rowsLp);

        Button add = primaryButton("＋  Add block to this routine", () ->
                addBlockToDailyDraft(draft, draftRows));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, dp(46));
        addLp.topMargin = dp(8);
        content.addView(add, addLp);

        String editorTitle = editingDate.equals(todayKey())
                ? "Modify & Save Today's Routine"
                : "Modify & Save This Date's Routine";
        showFullScreenViewer("DATE-ONLY EDITOR", editorTitle,
                "Edit, add or remove blocks. Nothing is committed until you tap Save, and no reusable template is modified.",
                content, editingDate.equals(todayKey()) ? "Save Today's Routine" : "Save This Date's Routine",
                () -> saveTodayRoutineDraft(draft, editingDate));
        renderTodayRoutineDraftRows(draftRows, draft);
    }

    private Block copyBlockForDailyEditor(Block source) {
        Block copy = new Block(source.id, source.start, source.end, source.title,
                source.priority, source.rigidity, source.status);
        copy.actualMs = source.actualMs;
        copy.actualStartedAt = source.actualStartedAt;
        copy.templateOwner = source.templateOwner;
        return copy;
    }

    private void renderTodayRoutineDraftRows(LinearLayout rows, ArrayList<Block> draft) {
        rows.removeAllViews();
        Collections.sort(draft, Comparator.comparingInt(b -> mins(b.start)));
        if (draft.isEmpty()) {
            LinearLayout empty = surfaceCard();
            empty.addView(text("No blocks yet", 16, color("#18372E"), true));
            empty.addView(text("Add the blocks you want in this date's routine.", 12,
                    color("#687A70"), false));
            rows.addView(empty);
            return;
        }
        for (Block block : new ArrayList<>(draft)) {
            LinearLayout card = surfaceCard();
            LinearLayout line = horizontal();
            line.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout time = vertical();
            time.addView(text(time12(block.start), 12, color("#286B55"), true));
            time.addView(text(time12(block.end), 10, color("#687A70"), false));
            line.addView(time, new LinearLayout.LayoutParams(dp(66), -2));

            LinearLayout details = vertical();
            LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1);
            detailsLp.leftMargin = dp(8);
            detailsLp.rightMargin = dp(4);
            line.addView(details, detailsLp);
            TextView title = text(block.title, 13, color("#18372E"), true);
            title.setMaxLines(3);
            details.addView(title);
            TextView meta = text(blockBehaviorLabel(block) + "  •  "
                    + fmt(Math.max(0, mins(block.end) - mins(block.start))), 10, color("#687A70"), false);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
            metaLp.topMargin = dp(3);
            details.addView(meta, metaLp);

            Button edit = secondaryButton("Edit", () ->
                    editDailyDraftBlock(block, draft, rows));
            Button remove = secondaryButton("×", () -> new AlertDialog.Builder(this)
                    .setTitle("Remove from this date?")
                    .setMessage("\"" + block.title + "\" will be removed from the draft for this date only. Templates will not change.")
                    .setPositiveButton("Remove", (dialog, which) -> {
                        draft.remove(block);
                        renderTodayRoutineDraftRows(rows, draft);
                    })
                    .setNegativeButton("Keep", null)
                    .show());
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(dp(54), dp(38));
            editLp.leftMargin = dp(4);
            line.addView(edit, editLp);
            LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(dp(38), dp(38));
            removeLp.leftMargin = dp(4);
            line.addView(remove, removeLp);

            card.addView(line);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.bottomMargin = dp(7);
            rows.addView(card, cardLp);
        }
    }

    private void addBlockToDailyDraft(ArrayList<Block> draft, LinearLayout rows) {
        int start = roundUp5(currentMinutes());
        if (start >= 1380) start = 8 * 60;
        int end = Math.min(1439, start + 60);
        Block blank = new Block(System.currentTimeMillis() + draft.size(), toTime(start), toTime(end),
                "", "normal", "flexible", "pending");
        editDailyDraftBlock(blank, draft, rows);
    }

    private void editDailyDraftBlock(Block target, ArrayList<Block> draft, LinearLayout rows) {
        boolean isNew = target.title == null || target.title.trim().isEmpty();
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(4));

        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setText(target.title == null ? "" : target.title);
        title.setHint("Block name");
        title.setTextColor(color("#18372E"));
        title.setHintTextColor(color("#82948A"));
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(50)));

        final int[] start = {mins(target.start)};
        final int[] end = {mins(target.end)};
        Button startButton = chip("Start • " + time12(target.start));
        Button endButton = chip("End • " + time12(target.end));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(40));
        startLp.topMargin = dp(8);
        form.addView(startButton, startLp);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(-1, dp(40));
        endLp.topMargin = dp(6);
        form.addView(endButton, endLp);
        startButton.setOnClickListener(v -> new TimePickerDialog(this, (picker, h, m) -> {
            start[0] = h * 60 + m;
            startButton.setText("Start • " + time12(toTime(start[0])));
        }, start[0] / 60, start[0] % 60, true).show());
        endButton.setOnClickListener(v -> new TimePickerDialog(this, (picker, h, m) -> {
            end[0] = h * 60 + m;
            endButton.setText("End • " + time12(toTime(end[0])));
        }, end[0] / 60, end[0] % 60, true).show());

        TextView rigidityLabel = text("RIGIDITY", 10, color("#91A197"), true);
        LinearLayout.LayoutParams rigidityLabelLp = new LinearLayout.LayoutParams(-1, -2);
        rigidityLabelLp.topMargin = dp(12);
        form.addView(rigidityLabel, rigidityLabelLp);
        LinearLayout rigidities = horizontal();
        form.addView(rigidities, new LinearLayout.LayoutParams(-1, dp(40)));
        String[] rigidityNames = {"Fixed time", "Flexible time"};
        String[] rigidityValues = {"fixed", "flexible"};
        String[] selectedRigidity = {target.rigidity};
        for (int i = 0; i < rigidityNames.length; i++) {
            final int idx = i;
            Button button = chip(rigidityNames[i]);
            boolean selected = rigidityValues[i].equals(selectedRigidity[0]);
            button.setTextColor(selected ? Color.WHITE : color("#D6E1D8"));
            button.setBackground(bg(selected ? "#245B45" : "#1B2621", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(6);
            rigidities.addView(button, lp);
            button.setOnClickListener(v -> {
                selectedRigidity[0] = rigidityValues[idx];
                for (int j = 0; j < rigidities.getChildCount(); j++) {
                    Button child = (Button) rigidities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    child.setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        TextView priorityLabel = text("PRIORITY", 10, color("#91A197"), true);
        LinearLayout.LayoutParams priorityLabelLp = new LinearLayout.LayoutParams(-1, -2);
        priorityLabelLp.topMargin = dp(10);
        form.addView(priorityLabel, priorityLabelLp);
        LinearLayout priorities = horizontal();
        form.addView(priorities, new LinearLayout.LayoutParams(-1, dp(40)));
        String[] priorityNames = {"Critical", "Important", "Normal", "Optional"};
        String[] priorityValues = {"critical", "important", "normal", "optional"};
        String[] selectedPriority = {target.priority};
        for (int i = 0; i < priorityNames.length; i++) {
            final int idx = i;
            Button button = chip(priorityNames[i]);
            boolean selected = priorityValues[i].equals(selectedPriority[0]);
            button.setTextColor(selected ? Color.WHITE : color("#D6E1D8"));
            button.setBackground(bg(selected ? "#245B45" : "#1B2621", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(4);
            priorities.addView(button, lp);
            button.setOnClickListener(v -> {
                selectedPriority[0] = priorityValues[idx];
                for (int j = 0; j < priorities.getChildCount(); j++) {
                    Button child = (Button) priorities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    child.setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        showFullScreenEditor("DATE-ONLY EDITOR", isNew ? "Add block" : "Edit block",
                "Changes stay in this date's draft until you save the routine.", form, () -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        title.setError("Give this block a name");
                        title.requestFocus();
                        return false;
                    }
                    if (end[0] <= start[0]) {
                        Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    target.title = name;
                    target.start = toTime(start[0]);
                    target.end = toTime(end[0]);
                    target.rigidity = selectedRigidity[0];
                    target.priority = selectedPriority[0];
                    if (isNew && !draft.contains(target)) {
                        // Date-only additions have no ownership in a reusable template.
                        target.templateOwner = null;
                        target.status = "pending";
                        draft.add(target);
                    }
                    renderTodayRoutineDraftRows(rows, draft);
                    return true;
                });
    }

    private void saveTodayRoutineDraft(ArrayList<Block> draft, String editingDate) {
        // Never commit a stale draft over another date if navigation changed while editing.
        if (!editingDate.equals(selectedDateKey)) {
            Toast.makeText(this, "The selected date changed. Reopen the editor to save safely.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        long now = System.currentTimeMillis();
        for (Block original : new ArrayList<>(blocks)) {
            Block edited = null;
            for (Block candidate : draft) {
                if (candidate.id == original.id) {
                    edited = candidate;
                    break;
                }
            }
            boolean changedOrRemoved = edited == null
                    || !Objects.equals(original.title, edited.title)
                    || !Objects.equals(original.start, edited.start)
                    || !Objects.equals(original.end, edited.end);
            if (changedOrRemoved && original.actualStartedAt > 0) {
                stopFocusForBlock(original, now);
            }
            if (edited != null) {
                // Preserve the freshest focus totals/timer for matching daily blocks.
                edited.actualMs = original.actualMs;
                edited.actualStartedAt = original.actualStartedAt;
            }
        }
        blocks.clear();
        Collections.sort(draft, Comparator.comparingInt(b -> mins(b.start)));
        for (Block block : draft) blocks.add(block);
        save(); // Writes only blocksKey(editingDate), never template_common/wfh/office/weekend.
        Toast.makeText(this, editingDate.equals(todayKey())
                ? "Today's routine saved. Templates were not changed."
                : "This date's routine saved. Templates were not changed.",
                Toast.LENGTH_LONG).show();
    }

    private boolean scheduleMatchesTemplate(String key) {
        ArrayList<Block> expected = getCombinedTemplateBlocks(key);
        if (blocks.size() != expected.size()) return false;
        boolean[] matched = new boolean[blocks.size()];
        for (Block source : expected) {
            boolean found = false;
            for (int i = 0; i < blocks.size(); i++) {
                if (matched[i]) continue;
                Block actual = blocks.get(i);
                if (source.start.equals(actual.start) && source.end.equals(actual.end)
                        && source.title != null && source.title.equalsIgnoreCase(actual.title == null ? "" : actual.title)) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void confirmApplySelectedTemplate(String key) {
        String label = key.equals("office") ? "Office" : key.equals("weekend") ? "Weekend" : "WFH";
        new AlertDialog.Builder(this)
                .setTitle("Apply " + label + " template to this date?")
                .setMessage("This replaces the selected date's timeline with the shared routine plus the " + label
                        + "-specific routine. Blocks that do not belong to that template (including date-only additions) will be removed. "
                        + "Matching blocks keep their completion and focus history.")
                .setPositiveButton("Apply template", (d, w) -> {
                    mode = key;
                    planPreviewMode = key;
                    SharedPreferences.Editor editor = getPrefs().edit().putString(modeKey(selectedDateKey), mode);
                    if (selectedDateKey.equals(todayKey())) editor.putString(MODE_KEY, mode);
                    editor.apply();
                    loadTemplate(key);
                    setPlanDayScheduleExpanded(true);
                    render();
                    renderPlanPage();
                    renderOfficePage();
                    Toast.makeText(this, label + " template applied to " +
                            (selectedDateKey.equals(todayKey()) ? "today." : selectedDateKey + "."), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Keep current schedule", null)
                .show();
    }

    private android.app.Dialog showFullScreenViewer(String eyebrow, String title, String subtitle,
                                       View content, String primaryLabel, Runnable primaryAction) {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = vertical();
        shell.setBackgroundColor(color("#F4F6F1"));

        LinearLayout header = horizontal();
        header.setPadding(dp(18), dp(12), dp(18), dp(12));
        Button back = secondaryButton("‹", dialog::dismiss);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(44), dp(42));
        backLp.rightMargin = dp(12);
        header.addView(back, backLp);
        LinearLayout heading = vertical();
        heading.addView(text(eyebrow.toUpperCase(Locale.US), 10, color("#2E8060"), true));
        TextView titleView = text(title, 22, color("#18372E"), true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(2);
        heading.addView(titleView, titleLp);
        TextView sub = text(subtitle, 11, color("#687A70"), false);
        sub.setLineSpacing(dp(2), 1f);
        heading.addView(sub);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        if (content.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) content.getParent()).removeView(content);
        }
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = horizontal();
        footer.setPadding(dp(16), dp(10), dp(16), dp(12));
        footer.setBackgroundColor(Color.WHITE);
        if (primaryLabel == null || primaryLabel.trim().isEmpty()) {
            footer.addView(secondaryButton("Close", dialog::dismiss), new LinearLayout.LayoutParams(-1, dp(46)));
        } else {
            footer.addView(secondaryButton("Close", dialog::dismiss), new LinearLayout.LayoutParams(0, dp(46), 1));
            Button action = primaryButton(primaryLabel, () -> {
                dialog.dismiss();
                if (primaryAction != null) primaryAction.run();
            });
            LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(0, dp(46), 1);
            actionLp.leftMargin = dp(8);
            footer.addView(action, actionLp);
        }
        shell.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(shell);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color("#F4F6F1")));
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        if (window != null) window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT);
        return dialog;
    }

    private void showFullScreenEditor(String eyebrow, String title, String subtitle,
                                      View form, java.util.function.BooleanSupplier saveAction) {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout editor = vertical();
        editor.setBackgroundColor(color("#F4F6F1"));

        LinearLayout header = horizontal();
        header.setPadding(dp(18), dp(12), dp(18), dp(12));
        Button back = secondaryButton("‹", dialog::dismiss);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(44), dp(42));
        backLp.rightMargin = dp(12);
        header.addView(back, backLp);
        LinearLayout headings = vertical();
        headings.addView(text(eyebrow.toUpperCase(Locale.US), 10, color("#2E8060"), true));
        TextView titleView = text(title, 22, color("#18372E"), true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(2);
        headings.addView(titleView, titleLp);
        TextView subtitleView = text(subtitle, 11, color("#687A70"), false);
        subtitleView.setLineSpacing(dp(2), 1f);
        headings.addView(subtitleView);
        header.addView(headings, new LinearLayout.LayoutParams(0, -2, 1));
        editor.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        if (form.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) form.getParent()).removeView(form);
        }
        scroll.addView(form, new ScrollView.LayoutParams(-1, -2));
        editor.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = horizontal();
        footer.setPadding(dp(16), dp(10), dp(16), dp(12));
        footer.setBackgroundColor(Color.WHITE);
        footer.addView(secondaryButton("Cancel", dialog::dismiss),
                new LinearLayout.LayoutParams(0, dp(46), 1));
        Button save = primaryButton("Save changes", () -> {
            try {
                if (saveAction.getAsBoolean()) dialog.dismiss();
            } catch (Exception e) {
                debugLog("Full-screen editor save failed: " + e.getMessage());
                Toast.makeText(this, "Couldn't save. Your existing data is unchanged.", Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(46), 1);
        saveLp.leftMargin = dp(8);
        footer.addView(save, saveLp);
        editor.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(editor);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color("#F4F6F1")));
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        if (window != null) window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT);
    }

    private void openManageDialog() { switchPage("more"); }

    private ArrayList<SyllabusTopic> readSyllabusTopics() {
        ArrayList<SyllabusTopic> topics = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(getPrefs().getString(SYLLABUS_KEY, "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                SyllabusTopic topic = new SyllabusTopic(
                        item.optLong("id", i + 1L),
                        item.optString("subject", "General Studies"),
                        item.optString("title", ""),
                        item.optBoolean("done", false),
                        item.optLong("completedAt", 0L));
                topic.sortOrder = item.optInt("sortOrder", i * 100);
                topics.add(topic);
            }
        } catch (Exception e) {
            debugLog("Syllabus read failed: " + e.getMessage());
        }
        return topics;
    }

    private void refreshTodaySummaryProgress() {
        if (progressPercent == null) return;
        int done = 0;
        for (Block block : blocks) {
            if ("done".equals(block.status)) done++;
        }
        int total = blocks.size();
        int planPercent = total == 0 ? 0 : Math.round(done * 100f / total);
        progressPercent.setText(planPercent + "% of plan • " + syllabusProgressLabel());
    }

    private String syllabusProgressLabel() {
        // Use the same live progress calculation as the UPSC CSE roadmap branch.
        // The connected plan includes syllabus topics, monthly goals and exam checkpoints.
        for (RoadmapNode rootNode : buildRoadmapTreeNodes()) {
            if (!"upsc".equals(rootNode.rootKey)) continue;
            int[] progress = roadmapNodeProgress(rootNode);
            if (progress[1] == 0) return "Syllabus not set";
            return "Syllabus " + Math.round(progress[0] * 100f / progress[1]) + "%";
        }
        return "Syllabus not set";
    }

    private String syllabusSummary(ArrayList<SyllabusTopic> topics) {
        RoadmapMilestone cseCheckpoint = roadmapMilestoneForKey("cse-checkpoint");
        RoadmapMilestone cseTarget = roadmapMilestoneForKey("cse-target");
        RoadmapMilestone tier34 = roadmapMilestoneForKey("tier34");
        RoadmapMilestone tier12 = roadmapMilestoneForKey("tier12");
        return syllabusOverview(topics)
                + "\n\nCSE checkpoint: " + (cseCheckpoint == null
                    ? displayRoadmapDate(getPrefs().getString(CSE_CHECKPOINT_KEY, "2027-04-01"))
                    : displayRoadmapDate(cseCheckpoint.date))
                + "\nCSE target: " + (cseTarget == null
                    ? displayRoadmapDate(getPrefs().getString(SYLLABUS_TARGET_KEY, "2028-01-01"))
                    : displayRoadmapDate(cseTarget.date))
                + "\nTier 3/4 readiness: " + (tier34 == null
                    ? displayRoadmapDate(getPrefs().getString(INTERVIEW_TIER34_KEY, "2027-04-05"))
                    : displayRoadmapDate(tier34.date))
                + "\nTier 1/2 readiness: " + (tier12 == null
                    ? displayRoadmapDate(getPrefs().getString(INTERVIEW_TARGET_KEY, "2027-07-11"))
                    : displayRoadmapDate(tier12.date));
    }

    private void chooseSyllabusTargetDate() {
        String current = getPrefs().getString(SYLLABUS_TARGET_KEY, "2028-01-01");
        Date parsed = parseDateKey(current);
        Calendar c = Calendar.getInstance();
        if (parsed != null) c.setTime(parsed);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(year, month, day, 0, 0, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            String key = keyForDate(chosen.getTime());
            ArrayList<RoadmapMilestone> milestones = readRoadmapMilestones();
            boolean found = false;
            for (RoadmapMilestone milestone : milestones) {
                if ("cse-target".equals(milestone.key)) {
                    milestone.date = key;
                    found = true;
                    break;
                }
            }
            if (found) persistRoadmapMilestones(milestones);
            else getPrefs().edit().putString(SYLLABUS_TARGET_KEY, key).apply();
            debugLog("Syllabus target date set: " + key);
            refreshRoadmapSummary();
            showSyllabusTracker();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        picker.setTitle("Syllabus target date");
        picker.show();
    }

    private void importSyllabusTopics() {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(6), dp(18), dp(16));
        TextView helper = text("Each non-empty line becomes one topic. Duplicate topics under the same subject are skipped.",
                12, color("#687A70"), false);
        helper.setLineSpacing(dp(2), 1f);
        form.addView(helper);
        Spinner subject = new Spinner(this);
        subject.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SYLLABUS_SUBJECTS));
        LinearLayout.LayoutParams subjectLp = new LinearLayout.LayoutParams(-1, dp(48));
        subjectLp.topMargin = dp(10);
        form.addView(subject, subjectLp);
        EditText lines = new EditText(this);
        lines.setGravity(Gravity.TOP | Gravity.START);
        lines.setMinLines(8);
        lines.setHint("Paste topics, one per line");
        lines.setTextColor(color("#18372E"));
        lines.setHintTextColor(color("#82948A"));
        lines.setPadding(dp(12), dp(12), dp(12), dp(12));
        lines.setBackground(bg("#FFFFFF", 12));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(220));
        inputLp.topMargin = dp(10);
        form.addView(lines, inputLp);

        showFullScreenEditor("SYLLABUS TOOLS", "Import topic list",
                "Choose a subject, then paste your topic list.", form, () -> {
                    String selectedSubject = subject.getSelectedItem().toString();
                    ArrayList<SyllabusTopic> all = readSyllabusTopics();
                    HashSet<String> existing = new HashSet<>();
                    for (SyllabusTopic topic : all) {
                        existing.add((topic.subject + "|" + topic.title).toLowerCase(Locale.US));
                    }
                    int added = 0;
                    for (String raw : lines.getText().toString().split("\\r?\\n")) {
                        String name = raw.trim();
                        if (name.isEmpty() || !existing.add((selectedSubject + "|" + name).toLowerCase(Locale.US))) continue;
                        SyllabusTopic topic = new SyllabusTopic(System.currentTimeMillis() + added,
                                selectedSubject, name, false, 0L);
                        topic.sortOrder = nextSyllabusTopicSortOrder(all, selectedSubject);
                        all.add(topic);
                        added++;
                    }
                    if (added > 0 && !getPrefs().contains(SYLLABUS_START_KEY)) {
                        getPrefs().edit().putString(SYLLABUS_START_KEY, todayKey()).apply();
                    }
                    persistSyllabusTopics(all);
                    Toast.makeText(this, added + " topics imported.", Toast.LENGTH_SHORT).show();
                    showSyllabusTracker();
                    return true;
                });
    }

    private void loadStarterSyllabus() {
        loadStarterSyllabus(true);
    }

    private void ensureStarterPlans() {
        SharedPreferences prefs = getPrefs();
        if (!prefs.contains(INTERVIEW_PLAN_START_KEY)) {
            String start = todayKey();
            Calendar sixMonths = Calendar.getInstance();
            sixMonths.setTime(parseDateKey(start));
            sixMonths.add(Calendar.MONTH, 6);
            Calendar nineMonths = Calendar.getInstance();
            nineMonths.setTime(parseDateKey(start));
            nineMonths.add(Calendar.MONTH, 9);
            prefs.edit()
                    .putString(INTERVIEW_PLAN_START_KEY, start)
                    .putString(INTERVIEW_TARGET_KEY, keyForDate(nineMonths.getTime()))
                    .apply();
            debugLog("Software interview roadmap dates initialised: start=" + start
                    + " sixMonth=" + keyForDate(sixMonths.getTime())
                    + " nineMonth=" + keyForDate(nineMonths.getTime()));
        }
        if (!prefs.getBoolean(STARTER_SEEDED_KEY, false)) {
            loadStarterSyllabus(false);
            prefs.edit().putBoolean(STARTER_SEEDED_KEY, true).apply();
            debugLog("Saved CSE and software interview starter plan on first run.");
        }
    }

    private void loadStarterSyllabus(boolean showTracker) {
        String[][] seed = {
            {"General Studies", "Prelims: Current events of national and international importance"},
            {"General Studies", "Prelims: History of India and Indian National Movement"},
            {"General Studies", "Prelims: Indian and World Geography (physical, social and economic)"},
            {"General Studies", "Prelims: Indian Polity and Governance (Constitution, political system, Panchayati Raj, public policy and rights)"},
            {"General Studies", "Prelims: Economic and Social Development (sustainable development, poverty, inclusion, demographics and social sector)"},
            {"General Studies", "Prelims: Environment, biodiversity and climate change"},
            {"General Studies", "Prelims: General Science"},
            {"General Studies", "GS-I: Indian culture (art forms, literature and architecture)"},
            {"General Studies", "GS-I: Modern Indian history from the mid-18th century"},
            {"General Studies", "GS-I: Freedom struggle (stages, contributors and regional contributions)"},
            {"General Studies", "GS-I: Post-independence consolidation and reorganisation"},
            {"General Studies", "GS-I: World history since the 18th century"},
            {"General Studies", "GS-I: Indian society and diversity"},
            {"General Studies", "GS-I: Women, population, poverty, development and urbanisation"},
            {"General Studies", "GS-I: Globalisation and Indian society"},
            {"General Studies", "GS-I: Social empowerment, communalism, regionalism and secularism"},
            {"General Studies", "GS-I: World physical geography"},
            {"General Studies", "GS-I: Natural resources and location of industries"},
            {"General Studies", "GS-I: Geophysical phenomena and geographical changes"},
            {"General Studies", "GS-II: Constitution (history, evolution, features, amendments and basic structure)"},
            {"General Studies", "GS-II: Federalism, Union-State relations and local devolution"},
            {"General Studies", "GS-II: Separation of powers and dispute-redress institutions"},
            {"General Studies", "GS-II: Comparison of constitutional schemes"},
            {"General Studies", "GS-II: Parliament and State legislatures"},
            {"General Studies", "GS-II: Executive, judiciary, ministries and pressure groups"},
            {"General Studies", "GS-II: Representation of the People Act"},
            {"General Studies", "GS-II: Constitutional, statutory, regulatory and quasi-judicial bodies"},
            {"General Studies", "GS-II: Government policies and interventions"},
            {"General Studies", "GS-II: NGOs, SHGs and development stakeholders"},
            {"General Studies", "GS-II: Welfare schemes for vulnerable sections"},
            {"General Studies", "GS-II: Health, education and human resources"},
            {"General Studies", "GS-II: Poverty and hunger"},
            {"General Studies", "GS-II: Governance, transparency, accountability and e-governance"},
            {"General Studies", "GS-II: Role of civil services in democracy"},
            {"General Studies", "GS-II: India and its neighbourhood"},
            {"General Studies", "GS-II: Bilateral, regional and global groupings"},
            {"General Studies", "GS-II: Foreign policies, Indian interests and diaspora"},
            {"General Studies", "GS-II: International institutions and agencies"},
            {"General Studies", "GS-III: Economy, planning, resources, growth, development and employment"},
            {"General Studies", "GS-III: Inclusive growth and government budgeting"},
            {"General Studies", "GS-III: Agriculture, crops, irrigation, storage, transport and marketing"},
            {"General Studies", "GS-III: Farm subsidies, MSP, PDS, buffer stocks and food security"},
            {"General Studies", "GS-III: Food processing and supply chains"},
            {"General Studies", "GS-III: Land reforms"},
            {"General Studies", "GS-III: Liberalisation and industrial policy"},
            {"General Studies", "GS-III: Infrastructure and investment models"},
            {"General Studies", "GS-III: Science and technology applications"},
            {"General Studies", "GS-III: Space, IT, robotics, nanotechnology, biotechnology and IPR"},
            {"General Studies", "GS-III: Conservation, pollution, degradation and environmental impact assessment"},
            {"General Studies", "GS-III: Disaster management"},
            {"General Studies", "GS-III: Extremism and internal security"},
            {"General Studies", "GS-III: Cybersecurity, money laundering and communications networks"},
            {"General Studies", "GS-III: Border security, organised crime and terrorism"},
            {"General Studies", "GS-III: Security forces and agencies"},
            {"General Studies", "GS-IV: Ethics and human interface"},
            {"General Studies", "GS-IV: Human values and lessons from leaders and administrators"},
            {"General Studies", "GS-IV: Attitude, social influence and persuasion"},
            {"General Studies", "GS-IV: Civil service aptitude and foundational values"},
            {"General Studies", "GS-IV: Emotional intelligence in administration"},
            {"General Studies", "GS-IV: Moral thinkers and philosophers"},
            {"General Studies", "GS-IV: Public-service ethics, dilemmas and ethical guidance"},
            {"General Studies", "GS-IV: Accountability, ethical governance and probity"},
            {"General Studies", "GS-IV: Case-study practice"},
            {"CSAT", "CSAT: Comprehension"},
            {"CSAT", "CSAT: Interpersonal and communication skills"},
            {"CSAT", "CSAT: Logical reasoning and analytical ability"},
            {"CSAT", "CSAT: Decision-making and problem-solving"},
            {"CSAT", "CSAT: General mental ability"},
            {"CSAT", "CSAT: Basic numeracy"},
            {"CSAT", "CSAT: Data interpretation (charts, graphs, tables and data sufficiency)"},
            {"Maths Optional", "Paper I — Linear Algebra: vector spaces over R and C; subspaces; linear dependence and independence"},
            {"Maths Optional", "Paper I — Linear Algebra: bases, dimension and coordinates"},
            {"Maths Optional", "Paper I — Linear Algebra: linear transformations, rank-nullity and matrix representations"},
            {"Maths Optional", "Paper I — Linear Algebra: matrix algebra, row/column reduction and echelon forms"},
            {"Maths Optional", "Paper I — Linear Algebra: matrix rank/inverse and systems of linear equations"},
            {"Maths Optional", "Paper I — Linear Algebra: eigenvalues, eigenvectors, characteristic polynomials and Cayley–Hamilton theorem"},
            {"Maths Optional", "Paper I — Linear Algebra: symmetric, skew-symmetric, Hermitian, skew-Hermitian, orthogonal and unitary matrices"},
            {"Maths Optional", "Paper I — Calculus: real functions, limits, continuity and differentiability"},
            {"Maths Optional", "Paper I — Calculus: mean-value theorem, Taylor theorem with remainder and indeterminate forms"},
            {"Maths Optional", "Paper I — Calculus: maxima/minima, asymptotes and curve tracing"},
            {"Maths Optional", "Paper I — Calculus: functions of two/three variables, limits and continuity"},
            {"Maths Optional", "Paper I — Calculus: partial derivatives, extrema, Lagrange multipliers and Jacobians"},
            {"Maths Optional", "Paper I — Calculus: Riemann definite integrals, indefinite integrals and improper integrals"},
            {"Maths Optional", "Paper I — Calculus: double/triple integrals, areas, surfaces and volumes"},
            {"Maths Optional", "Paper I — Analytic Geometry: Cartesian/polar coordinates in three dimensions and second-degree equations"},
            {"Maths Optional", "Paper I — Analytic Geometry: reduction to canonical forms; lines and shortest distance between skew lines"},
            {"Maths Optional", "Paper I — Analytic Geometry: planes, spheres, cones, cylinders, paraboloids, ellipsoids and hyperboloids"},
            {"Maths Optional", "Paper I — Ordinary Differential Equations: formulation, first-order equations and integrating factors"},
            {"Maths Optional", "Paper I — Ordinary Differential Equations: orthogonal trajectories; first-order higher-degree equations, Clairaut and singular solutions"},
            {"Maths Optional", "Paper I — Ordinary Differential Equations: higher-order linear equations with constant coefficients; complementary function and particular integral"},
            {"Maths Optional", "Paper I — Ordinary Differential Equations: variable coefficients, Euler–Cauchy and variation of parameters"},
            {"Maths Optional", "Paper I — Ordinary Differential Equations: Laplace/inverse Laplace transforms and initial-value applications"},
            {"Maths Optional", "Paper I — Dynamics and Statics: rectilinear/plane motion, simple harmonic motion, projectiles and constrained motion"},
            {"Maths Optional", "Paper I — Dynamics and Statics: work, energy, conservation, Kepler's laws and central-force orbits"},
            {"Maths Optional", "Paper I — Dynamics and Statics: particle equilibrium, potential energy, friction, catenary and virtual work"},
            {"Maths Optional", "Paper I — Dynamics and Statics: stability of equilibrium and three-dimensional force equilibrium"},
            {"Maths Optional", "Paper I — Vector Analysis: scalar/vector fields and differentiation; gradient, divergence and curl"},
            {"Maths Optional", "Paper I — Vector Analysis: Cartesian/cylindrical coordinates, higher derivatives, identities and vector equations"},
            {"Maths Optional", "Paper I — Vector Analysis: curves in space, curvature, torsion and Serret–Frenet formulae"},
            {"Maths Optional", "Paper I — Vector Analysis: Gauss and Stokes theorems and Green's identities"},
            {"Maths Optional", "Paper II — Algebra: groups, subgroups, cyclic groups, cosets and Lagrange's theorem"},
            {"Maths Optional", "Paper II — Algebra: normal/quotient groups, homomorphisms, isomorphism theorems, permutation groups and Cayley's theorem"},
            {"Maths Optional", "Paper II — Algebra: rings, subrings, ideals, ring homomorphisms, integral domains, principal ideal domains and fields"},
            {"Maths Optional", "Paper II — Real Analysis: ordered real field, least-upper-bound property, sequences, Cauchy sequences and completeness"},
            {"Maths Optional", "Paper II — Real Analysis: series convergence, absolute/conditional convergence and rearrangement"},
            {"Maths Optional", "Paper II — Real Analysis: continuity, uniform continuity and continuous functions on compact sets"},
            {"Maths Optional", "Paper II — Real Analysis: Riemann/improper integrals and fundamental theorems of integral calculus"},
            {"Maths Optional", "Paper II — Real Analysis: uniform convergence and continuity/differentiability/integrability of function sequences and series"},
            {"Maths Optional", "Paper II — Real Analysis: partial derivatives and maxima/minima of functions of several variables"},
            {"Maths Optional", "Paper II — Complex Analysis: analytic functions and Cauchy–Riemann equations"},
            {"Maths Optional", "Paper II — Complex Analysis: Cauchy's theorem and integral formula; power series and Taylor series"},
            {"Maths Optional", "Paper II — Complex Analysis: singularities, Laurent series and residue theorem"},
            {"Maths Optional", "Paper II — Complex Analysis: contour integration"},
            {"Maths Optional", "Paper II — Linear Programming: formulation, basic/basic-feasible solutions and optimality"},
            {"Maths Optional", "Paper II — Linear Programming: graphical and simplex methods"},
            {"Maths Optional", "Paper II — Linear Programming: duality, transportation and assignment problems"},
            {"Maths Optional", "Paper II — Partial Differential Equations: formation of PDEs from families of surfaces"},
            {"Maths Optional", "Paper II — Partial Differential Equations: first-order quasilinear PDEs and method of characteristics"},
            {"Maths Optional", "Paper II — Partial Differential Equations: second-order constant-coefficient PDEs, canonical forms, vibrating string, heat and Laplace equations"},
            {"Maths Optional", "Paper II — Numerical Analysis: bisection, Regula-Falsi and Newton–Raphson methods"},
            {"Maths Optional", "Paper II — Numerical Analysis: Gaussian elimination, Gauss–Jordan and Gauss–Seidel methods"},
            {"Maths Optional", "Paper II — Numerical Analysis: Newton forward/backward and Lagrange interpolation"},
            {"Maths Optional", "Paper II — Numerical Analysis: trapezoidal/Simpson rules and Gaussian quadrature"},
            {"Maths Optional", "Paper II — Numerical Analysis: Euler and Runge–Kutta methods for ODEs"},
            {"Maths Optional", "Paper II — Computer Programming: binary/octal/hexadecimal systems and conversions; binary arithmetic/logic"},
            {"Maths Optional", "Paper II — Computer Programming: computer-system elements, memory, logic gates, truth tables and Boolean algebra"},
            {"Maths Optional", "Paper II — Computer Programming: integer/real representations, double precision, algorithms and flowcharts"},
            {"Maths Optional", "Paper II — Mechanics and Fluid Dynamics: generalized coordinates, D'Alembert, Lagrange and Hamilton equations"},
            {"Maths Optional", "Paper II — Mechanics and Fluid Dynamics: moment of inertia and planar rigid-body motion"},
            {"Maths Optional", "Paper II — Mechanics and Fluid Dynamics: continuity and Euler equation for inviscid flow"},
            {"Maths Optional", "Paper II — Mechanics and Fluid Dynamics: streamlines, path lines, potential flow, sources/sinks and vortex motion"},
            {"Maths Optional", "Paper II — Mechanics and Fluid Dynamics: two-dimensional/axisymmetric motion and Navier–Stokes equation"},
            {"Qualifying English", "Mains qualifying English: comprehension"},
            {"Qualifying English", "Mains qualifying English: précis writing"},
            {"Qualifying English", "Mains qualifying English: usage and vocabulary"},
            {"Qualifying English", "Mains qualifying English: short essays"},
            {"Indian Language", "Mains qualifying language: comprehension"},
            {"Indian Language", "Mains qualifying language: précis writing"},
            {"Indian Language", "Mains qualifying language: usage and vocabulary"},
            {"Indian Language", "Mains qualifying language: short essays"},
            {"Indian Language", "Mains qualifying language: English-to-selected-language and selected-language-to-English translation"},
            {"Essay", "Essay practice: Society, diversity and social change"},
            {"Essay", "Essay practice: Governance, democracy and public institutions"},
            {"Essay", "Essay practice: Economy, technology and development"},
            {"Essay", "Essay practice: Ethics, philosophy and human values"},
            {"Essay", "Essay practice: Environment, science and international relations"},
            {"Essay", "Timed essay writing and self-review"},
            {"Interview", "Interview: DAF, education, work experience and hometown"},
            {"Interview", "Interview: State and district issues"},
            {"Interview", "Interview: Current affairs and balanced judgement"},
            {"Interview", "Interview: Public-service motivation and ethical dilemmas"},
            {"Interview", "Interview: Mock interview and feedback review"},
            {"Software Interview Prep", "Java: OOP, inheritance, composition, polymorphism and SOLID"},
            {"Software Interview Prep", "Java: collections, generics, equals/hashCode and immutability"},
            {"Software Interview Prep", "Java: JVM memory, garbage collection, class loading and performance"},
            {"Software Interview Prep", "Java: concurrency, synchronisation, executors, futures and thread safety"},
            {"Software Interview Prep", "Java: exceptions, streams, lambdas and practical API design"},
            {"Software Interview Prep", "DSA: arrays, strings, hashing, two pointers and sliding windows"},
            {"Software Interview Prep", "DSA: linked lists, stacks, queues, trees, tries and heaps"},
            {"Software Interview Prep", "DSA: graphs, traversal, shortest paths and union-find"},
            {"Software Interview Prep", "Algorithms: sorting, searching, binary search, greedy and backtracking"},
            {"Software Interview Prep", "Algorithms: recursion, dynamic programming and complexity analysis"},
            {"Software Interview Prep", "Coding practice: solve timed problems in Java and explain trade-offs"},
            {"Software Interview Prep", "Databases: SQL, indexes, transactions, isolation and query plans"},
            {"Software Interview Prep", "Backend: HTTP, REST APIs, authentication, pagination and idempotency"},
            {"Software Interview Prep", "System design: requirements, capacity estimates and constraints"},
            {"Software Interview Prep", "System design: data modelling, caching, queues and asynchronous workflows"},
            {"Software Interview Prep", "Distributed systems: consistency, replication, partitioning and sharding"},
            {"Software Interview Prep", "Reliability: timeouts, retries, circuit breakers, rate limits and backpressure"},
            {"Software Interview Prep", "Operations: observability, SLOs, incident response and postmortems"},
            {"Software Interview Prep", "Architecture: document trade-offs, alternatives, failure modes and cost"},
            {"Software Interview Prep", "Leadership: project ownership, delivery, prioritisation and stakeholder alignment"},
            {"Software Interview Prep", "Leadership: mentoring, feedback, conflict resolution and team growth"},
            {"Software Interview Prep", "Career narrative: explain PM/EM/IC experience and target role coherently"},
            {"Software Interview Prep", "Behavioural: STAR stories for failure, conflict, ambiguity and impact"},
            {"Software Interview Prep", "Resume and project deep dives: quantify impact and defend key decisions"},
            {"Software Interview Prep", "Practice: mock coding, system design and leadership interviews; review gaps"}
        };
        ArrayList<SyllabusTopic> all = readSyllabusTopics();
        HashSet<String> known = new HashSet<>();
        for (SyllabusTopic topic : all) {
            known.add((topic.subject + "|" + topic.title).toLowerCase(Locale.US));
        }
        int added = 0;
        for (String[] row : seed) {
            if (row.length < 2) continue;
            String key = (row[0] + "|" + row[1]).toLowerCase(Locale.US);
            if (!known.add(key)) continue;
            SyllabusTopic topic = new SyllabusTopic(System.currentTimeMillis() + added, row[0], row[1], false, 0L);
            topic.sortOrder = nextSyllabusTopicSortOrder(all, row[0]);
            all.add(topic);
            added++;
        }
        if (added > 0 && !getPrefs().contains(SYLLABUS_START_KEY)) {
            getPrefs().edit().putString(SYLLABUS_START_KEY, todayKey()).apply();
        }
        persistSyllabusTopics(all);
        debugLog("CSE/software interview starter loaded: newItems=" + added);
        if (showTracker) {
            Toast.makeText(this, added + " syllabus and interview-prep items added.", Toast.LENGTH_LONG).show();
            showSyllabusTracker();
        }
    }

    private void showSyllabusTracker() {
        syllabusDetailsExpanded = true;
        if (syllabusDetailContent != null) syllabusDetailContent.setVisibility(View.VISIBLE);
        if (syllabusProgressToggle != null) syllabusProgressToggle.setText("Close checklist");
        switchPage("syllabus");
    }

    private void showSyllabusTopicActions(SyllabusTopic topic) {
        LinearLayout content = vertical();
        content.setPadding(dp(16), dp(8), dp(16), dp(18));

        LinearLayout summary = surfaceCard();
        summary.addView(text("SUBJECT", 10, color("#2E8060"), true));
        summary.addView(text(topic.subject, 16, color("#18372E"), true));
        summary.addView(text(topic.done ? "Completed" : "Not completed yet", 13,
                topic.done ? color("#286B55") : color("#687A70"), true));
        if (topic.done && topic.completedAt > 0) {
            summary.addView(text("Marked complete  •  "
                    + new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
                    .format(new Date(topic.completedAt)), 11, color("#687A70"), false));
        }
        content.addView(summary);

        LinearLayout actions = surfaceCard();
        sectionLabel(actions, "TOPIC ACTIONS");
        final android.app.Dialog[] detailDialog = new android.app.Dialog[1];
        Button toggle = primaryButton(topic.done ? "Mark as pending" : "Mark complete", () -> {
            if (detailDialog[0] != null) detailDialog[0].dismiss();
            topic.done = !topic.done;
            topic.completedAt = topic.done ? System.currentTimeMillis() : 0L;
            saveSyllabusTopics();
            switchPage("syllabus");
        });
        actions.addView(toggle, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout reorderRow = horizontal();
        LinearLayout.LayoutParams reorderRowLp = new LinearLayout.LayoutParams(-1, dp(42));
        reorderRowLp.topMargin = dp(8);
        Button moveEarlier = secondaryButton("↑ Move earlier", () -> {
            if (detailDialog[0] != null) detailDialog[0].dismiss();
            moveSyllabusTopic(topic, -1);
        });
        Button moveLater = secondaryButton("↓ Move later", () -> {
            if (detailDialog[0] != null) detailDialog[0].dismiss();
            moveSyllabusTopic(topic, 1);
        });
        reorderRow.addView(moveEarlier, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout.LayoutParams moveLaterLp = new LinearLayout.LayoutParams(0, -1, 1f);
        moveLaterLp.leftMargin = dp(7);
        reorderRow.addView(moveLater, moveLaterLp);
        actions.addView(reorderRow, reorderRowLp);

        Button delete = actionButton("Delete topic", color("#B44248"), "#F8E8E6");
        delete.setTextSize(12);
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(-1, dp(42));
        deleteLp.topMargin = dp(8);
        actions.addView(delete, deleteLp);
        delete.setOnClickListener(v -> {
            if (detailDialog[0] != null) detailDialog[0].dismiss();
            new AlertDialog.Builder(this)
                    .setTitle("Delete this topic?")
                    .setMessage("\"" + topic.title + "\" will be removed from the syllabus tracker. This does not change other topics.")
                    .setPositiveButton("Delete", (d, w) -> {
                        ArrayList<SyllabusTopic> all = readSyllabusTopics();
                        all.removeIf(item -> item.id == topic.id);
                        persistSyllabusTopics(all);
                        switchPage("syllabus");
                    })
                    .setNegativeButton("Keep topic", null)
                    .show();
        });
        content.addView(actions);

        detailDialog[0] = showFullScreenViewer("LEARNING LEDGER", topic.title,
                "Topic-level progress is tracked independently from scheduled study time.",
                content, "Edit topic", () -> editSyllabusTopic(topic));
    }

        private void editSyllabusTopic(SyllabusTopic existing) {
        editSyllabusTopic(existing, null);
    }

    private void editSyllabusTopic(SyllabusTopic existing, String preferredSubject) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(4));
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Exact topic / syllabus item");
        title.setTextColor(color("#18372E"));
        title.setHintTextColor(color("#82948A"));
        if (existing != null) title.setText(existing.title);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView label = text("SUBJECT", 10, color("#A5B5AA"), true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.topMargin = dp(10);
        form.addView(label, labelLp);
        Spinner subject = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SYLLABUS_SUBJECTS);
        subject.setAdapter(adapter);
        if (existing != null) {
            for (int i = 0; i < SYLLABUS_SUBJECTS.length; i++) {
                if (SYLLABUS_SUBJECTS[i].equals(existing.subject)) subject.setSelection(i);
            }
        } else if (preferredSubject != null) {
            for (int i = 0; i < SYLLABUS_SUBJECTS.length; i++) {
                if (SYLLABUS_SUBJECTS[i].equals(preferredSubject)) subject.setSelection(i);
            }
        }
        form.addView(subject, new LinearLayout.LayoutParams(-1, dp(48)));

        showFullScreenEditor("LEARNING LEDGER", existing == null ? "Add syllabus topic" : "Edit syllabus topic",
                "Use a specific syllabus item or competency so progress stays meaningful.", form, () -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        title.setError("Enter a topic name");
                        title.requestFocus();
                        return false;
                    }
                    ArrayList<SyllabusTopic> all = readSyllabusTopics();
                    if (existing == null) {
                        if (!getPrefs().contains(SYLLABUS_START_KEY)) {
                            getPrefs().edit().putString(SYLLABUS_START_KEY, todayKey()).apply();
                        }
                        String selectedSubject = subject.getSelectedItem().toString();
                        SyllabusTopic topic = new SyllabusTopic(System.currentTimeMillis(), selectedSubject, name, false, 0L);
                        topic.sortOrder = nextSyllabusTopicSortOrder(all, selectedSubject);
                        all.add(topic);
                    } else {
                        for (SyllabusTopic item : all) {
                            if (item.id == existing.id) {
                                String newSubject = subject.getSelectedItem().toString();
                                if (!Objects.equals(item.subject, newSubject)) {
                                    item.sortOrder = nextSyllabusTopicSortOrder(all, newSubject);
                                }
                                item.subject = newSubject;
                                item.title = name;
                            }
                        }
                    }
                    persistSyllabusTopics(all);
                    return true;
                });
    }



    private void saveSyllabusTopics() {
        persistSyllabusTopics(readSyllabusTopics());
    }

    private void persistSyllabusTopics(ArrayList<SyllabusTopic> topics) {
        JSONArray data = new JSONArray();
        try {
            for (SyllabusTopic topic : topics) {
                JSONObject item = new JSONObject();
                item.put("id", topic.id);
                item.put("subject", topic.subject);
                item.put("title", topic.title);
                item.put("done", topic.done);
                item.put("completedAt", topic.completedAt);
                item.put("sortOrder", topic.sortOrder);
                data.put(item);
            }
            getPrefs().edit().putString(SYLLABUS_KEY, data.toString()).apply();
            debugLog("Syllabus saved: topics=" + topics.size());
            DayFlowWidgetProvider.refreshAll(this);
        } catch (Exception e) {
            debugLog("Syllabus save failed: " + e.getMessage());
            Toast.makeText(this, "Could not save syllabus changes.", Toast.LENGTH_SHORT).show();
        }
        if (syllabusList != null) refreshSyllabusRows();
        refreshRoadmapSummary();
        render();
    }

    private void enablePreciseLiveWidgetUpdates() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            DayFlowWidgetProvider.refreshAll(this);
            Toast.makeText(this, "Live timers use the device clock; widget task names refresh at schedule boundaries.", Toast.LENGTH_LONG).show();
            return;
        }
        AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarms != null && alarms.canScheduleExactAlarms()) {
            DayFlowWidgetProvider.refreshAll(this);
            Toast.makeText(this, "Precise widget boundary updates are enabled.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Enable precise live updates")
                .setMessage("DayFlow's timers tick locally to the second. Allow Alarms & reminders so the widget can switch task names at scheduled boundaries. This uses one non-waking alarm for the next schedule change, not a background loop.")
                .setPositiveButton("Open settings", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        Toast.makeText(this, "Open Android Settings → Apps → DayFlow → Alarms & reminders.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    private void showTemplateEditorPicker() {
        String[] labels={"Shared routine · all day types","WFH-specific routine","Office-specific routine","Weekend-specific routine"};
        String[] keys={"common","wfh","office","weekend"};
        new AlertDialog.Builder(this)
                .setTitle("Which template would you like to edit?")
                .setItems(labels,(dialog,which)->openTemplateEditor(keys[which]))
                .setNegativeButton("Cancel",null)
                .show();
    }

    private void showTemplatePicker() {
        String[] labels={"Work from home","Office day","Weekend"};
        String[] keys={"wfh","office","weekend"};
        new AlertDialog.Builder(this)
                .setTitle("Apply a saved routine")
                .setMessage("The shared routine is combined with the selected day-type routine. Applying it replaces the selected day's blocks. Day-specific blocks take priority when a time or activity conflicts; office attendance and historical records are kept.")
                .setItems(labels,(dialog,which)->chooseTemplateDirect(keys[which]))
                .setNegativeButton("Cancel",null)
                .show();
    }

    private void chooseTemplateDirect(String key) {
        String label = key.equals("office") ? "Office" : key.equals("weekend") ? "Weekend" : "WFH";
        new AlertDialog.Builder(this)
                .setTitle("Load " + label + " template?")
                .setMessage("Your current timeline will be replaced with the shared routine plus your saved " + label + "-specific routine. Day-specific blocks take priority when a time or activity conflicts.")
                .setPositiveButton("Load", (d, w) -> {
                    mode = key;
                    planPreviewMode = key;
                    SharedPreferences.Editor selection = getPrefs().edit().putString(modeKey(selectedDateKey), mode);
                    if (selectedDateKey.equals(todayKey())) selection.putString(MODE_KEY, mode);
                    selection.apply();
                    loadTemplate(key);
                    setPlanDayScheduleExpanded(true);
                    renderPlanPage();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String templateKey(String key) {
        return "template_" + key;
    }

    private String templateLabel(String key) {
        if ("common".equals(key)) return "Shared";
        return "office".equals(key) ? "Office" : "weekend".equals(key) ? "Weekend" : "WFH";
    }

    private ArrayList<Block> getTemplateBlocks(String key) {
        ArrayList<Block> result = new ArrayList<>();
        String raw = getPrefs().getString(templateKey(key), null);
        boolean needsOwnershipMigration = false;
        try {
            if (raw == null) {
                String[][] defaults = defaultTemplate(key);
                for (int i = 0; i < defaults.length; i++) {
                    Block block = new Block(System.currentTimeMillis() + i, defaults[i][0], defaults[i][1],
                            defaults[i][2], defaults[i][3], "pending");
                    block.templateOwner = key;
                    result.add(block);
                }
                saveTemplate(key, result);
                return result;
            }
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Block block = blockFromJson(o, "pending");
                String owner = o.optString("templateOwner", "").trim();
                if (!owner.isEmpty()) {
                    block.templateOwner = owner;
                    // Never load a block owned by another reusable template into this
                    // template's editable collection. This is a source-of-truth check.
                    if (!key.equals(owner)) {
                        needsOwnershipMigration = true;
                        continue;
                    }
                } else {
                    // Legacy templates had no ownership marker. Remove recognizable old
                    // shared copies once; then mark the remaining rows as this template's
                    // own blocks so future edits never depend on title/time heuristics.
                    if (!"common".equals(key) && isSharedRoutineCopy(block)) {
                        needsOwnershipMigration = true;
                        continue;
                    }
                    block.templateOwner = key;
                    needsOwnershipMigration = true;
                }
                result.add(block);
            }
        } catch (Exception e) {
            result.clear();
            debugLog("Could not read " + key + " template: " + e.getMessage());
        }
        if (needsOwnershipMigration) saveTemplate(key, result);
        return result;
    }

    /**
     * Compose the shared routine with a day-type routine. Shared blocks have the
     * highest priority: a day-specific block is omitted if it conflicts with any
     * shared block by time overlap or matching activity name.
     */
    private ArrayList<Block> getCombinedTemplateBlocks(String key) {
        ArrayList<Block> shared = getTemplateBlocks("common");
        if ("common".equals(key)) return shared;
        for (Block block : shared) block.templateOwner = "common";

        ArrayList<Block> combined = new ArrayList<>(shared);
        ArrayList<Block> specific = getTemplateBlocks(key);
        for (Block dayBlock : specific) {
            dayBlock.templateOwner = key;
            boolean conflictsWithShared = false;
            for (Block sharedBlock : shared) {
                if (templateBlocksConflict(dayBlock, sharedBlock)) {
                    conflictsWithShared = true;
                    break;
                }
            }
            if (!conflictsWithShared) combined.add(dayBlock);
        }
        Collections.sort(combined, Comparator.comparingInt(b -> mins(b.start)));
        return combined;
    }

    private boolean templateBlocksConflict(Block first, Block second) {
        if (first.title != null && second.title != null
                && first.title.trim().equalsIgnoreCase(second.title.trim())) return true;
        int firstStart = mins(first.start);
        int firstEnd = mins(first.end);
        int secondStart = mins(second.start);
        int secondEnd = mins(second.end);
        return firstStart < secondEnd && secondStart < firstEnd;
    }

    private void saveTemplate(String key, ArrayList<Block> template) {
        // Each reusable template persists only blocks it owns. Shared rows are rendered
        // from template_common in a separate, read-only section of day-type editors.
        removeSharedRoutineCopies(key, template);
        for (int i = template.size() - 1; i >= 0; i--) {
            if (!key.equals(template.get(i).templateOwner) && template.get(i).templateOwner != null) {
                template.remove(i);
            }
        }
        try {
            JSONArray arr = new JSONArray();
            for (Block b : template) {
                b.templateOwner = key;
                JSONObject o = new JSONObject();
                o.put("id", b.id);
                o.put("start", b.start);
                o.put("end", b.end);
                o.put("title", b.title);
                o.put("priority", b.priority);
                o.put("rigidity", b.rigidity);
                o.put("templateOwner", key);
                arr.put(o);
            }
            getPrefs().edit().putString(templateKey(key), arr.toString()).apply();
            debugLog("Template saved: " + key + " blocks=" + template.size());
            DayFlowWidgetProvider.refreshAll(this);
        } catch (Exception e) {
            debugLog("Template save failed for " + key + ": " + e.getMessage());
            Toast.makeText(this, "Could not save the " + key + " routine.", Toast.LENGTH_LONG).show();
        }
    }

    private String[][] defaultTemplate(String key) {
        // Keep the shared routine empty by default so existing schedules remain unchanged.
        if ("common".equals(key)) return new String[0][4];
        if ("office".equals(key)) {
            return new String[][]{
                    {"06:00","06:30","Wake up + routine","fixed"},
                    {"06:30","08:00","GS study","important"},
                    {"08:00","09:00","Breakfast + commute","fixed"},
                    {"09:00","18:30","Work","fixed"},
                    {"18:30","19:15","Reset","flexible"},
                    {"19:15","21:15","Maths Optional","important"},
                    {"21:15","22:00","Dinner","fixed"},
                    {"22:00","23:00","Interview preparation","important"},
                    {"23:00","23:30","Wind down","flexible"}
            };
        }
        if ("weekend".equals(key)) {
            return new String[][]{
                    {"06:30","07:00","Morning routine","fixed"},
                    {"07:00","10:00","GS study","important"},
                    {"10:00","11:00","Breakfast + break","flexible"},
                    {"11:00","14:00","Maths Optional","important"},
                    {"14:00","15:00","Lunch","fixed"},
                    {"15:00","17:00","Rest / personal buffer","flexible"},
                    {"17:00","18:00","Exercise","important"},
                    {"18:00","20:00","Personal time","flexible"},
                    {"20:00","21:00","Dinner","fixed"},
                    {"21:00","22:30","Interview preparation","important"}
            };
        }
        return new String[][]{
                {"03:30","03:45","Wake up + routine","fixed"},
                {"03:45","05:45","GS study","important"},
                {"05:45","07:45","Maths Optional","important"},
                {"07:45","08:00","Breakfast / transition","flexible"},
                {"08:00","09:30","Interview preparation","fixed"},
                {"09:30","13:00","Work","fixed"},
                {"13:00","14:00","Lunch","fixed"},
                {"14:00","18:30","Work","fixed"},
                {"18:30","19:15","Exercise","important"},
                {"19:15","19:45","Dinner","fixed"},
                {"19:45","20:15","Wind down","flexible"}
        };
    }

    private String[][] legacyDefaultTemplate(String key) {
        if ("office".equals(key)) {
            return new String[][]{
                    {"06:00","06:30","Wake up + routine","fixed"},
                    {"06:30","08:00","Maths Optional","important"},
                    {"08:00","09:00","Breakfast + commute","fixed"},
                    {"09:00","18:30","Work","fixed"},
                    {"18:30","19:15","Reset","flexible"},
                    {"19:15","21:15","GS study","important"},
                    {"21:15","22:00","Dinner","fixed"},
                    {"22:00","23:00","Interview preparation","important"},
                    {"23:00","23:30","Wind down","flexible"}
            };
        }
        if ("weekend".equals(key)) {
            return new String[][]{
                    {"06:30","07:00","Morning routine","fixed"},
                    {"07:00","10:00","GS study","important"},
                    {"10:00","11:00","Breakfast + break","flexible"},
                    {"11:00","14:00","Maths Optional","important"},
                    {"14:00","15:00","Lunch","fixed"},
                    {"15:00","17:00","Maths / revision","important"},
                    {"17:00","18:00","Exercise","important"},
                    {"18:00","20:00","Personal time","flexible"},
                    {"20:00","21:00","Dinner","fixed"},
                    {"21:00","22:30","Interview preparation","important"}
            };
        }
        return new String[][]{
                {"06:00","06:20","Wake up","fixed"},
                {"06:20","07:00","Morning routine","flexible"},
                {"07:00","09:00","GS study","important"},
                {"09:00","09:30","Breakfast","flexible"},
                {"09:30","13:00","Work","fixed"},
                {"13:00","14:00","Lunch","fixed"},
                {"14:00","18:30","Work","fixed"},
                {"18:30","19:15","Exercise","important"},
                {"19:30","21:30","Maths Optional","important"},
                {"21:30","22:15","Dinner","fixed"},
                {"22:15","23:00","Interview preparation","important"},
                {"23:00","23:30","Wind down","flexible"}
        };
    }

    private boolean matchesSavedTemplate(String raw, String[][] expected) {
        try {
            JSONArray saved = new JSONArray(raw);
            if (saved.length() != expected.length) return false;
            for (int i = 0; i < expected.length; i++) {
                JSONObject record = saved.optJSONObject(i);
                if (record == null) return false;
                String[] spec = expected[i];
                if (!spec[0].equals(record.optString("start"))
                        || !spec[1].equals(record.optString("end"))
                        || !spec[2].equals(record.optString("title"))) return false;
                Block wanted = new Block(i, spec[0], spec[1], spec[2], spec[3], "pending");
                Block actual = blockFromJson(record, "pending");
                if (!wanted.priority.equals(actual.priority) || !wanted.rigidity.equals(actual.rigidity)) return false;
            }
            return true;
        } catch (Exception e) {
            debugLog("Routine migration comparison failed: " + e.getMessage());
            return false;
        }
    }

    private boolean matchesLegacyTemplate(String key, String raw) {
        return matchesSavedTemplate(raw, legacyDefaultTemplate(key));
    }

    private void migrateUntouchedWfhMorningPlan() {
        SharedPreferences prefs = getPrefs();
        if (prefs.getBoolean(ROUTINE_MIGRATION_V3_KEY, false)) return;
        String raw = prefs.getString(templateKey("wfh"), null);
        String[][] previousWfh = {
                {"05:00","05:15","Wake up","fixed"},
                {"05:15","05:45","Morning routine","flexible"},
                {"05:45","07:45","GS study","important"},
                {"07:45","08:00","Breakfast / transition","flexible"},
                {"08:00","09:30","Interview preparation","fixed"},
                {"09:30","13:00","Work","fixed"},
                {"13:00","14:00","Lunch","fixed"},
                {"14:00","18:30","Work","fixed"},
                {"18:30","19:15","Exercise","important"},
                {"19:15","21:15","Maths Optional","important"},
                {"21:15","21:45","Dinner","fixed"},
                {"21:45","22:15","Wind down","flexible"}
        };
        if (raw != null && matchesSavedTemplate(raw, previousWfh)) {
            ArrayList<Block> updated = new ArrayList<>();
            String[][] revised = defaultTemplate("wfh");
            for (int i = 0; i < revised.length; i++) {
                updated.add(new Block(System.currentTimeMillis() + i, revised[i][0], revised[i][1],
                        revised[i][2], revised[i][3], "pending"));
            }
            saveTemplate("wfh", updated);
            debugLog("Updated untouched built-in WFH routine to protect both morning CSE blocks and the 08:00–09:30 interview slot.");
        }
        prefs.edit().putBoolean(ROUTINE_MIGRATION_V3_KEY, true).apply();
    }

    private void migrateUneditedDefaultTemplates() {
        SharedPreferences prefs = getPrefs();
        if (prefs.getBoolean(ROUTINE_MIGRATION_V2_KEY, false)) return;
        for (String key : new String[]{"wfh", "office", "weekend"}) {
            String saved = prefs.getString(templateKey(key), null);
            if (saved == null || !matchesLegacyTemplate(key, saved)) continue;
            ArrayList<Block> updated = new ArrayList<>();
            String[][] defaults = defaultTemplate(key);
            for (int i = 0; i < defaults.length; i++) {
                updated.add(new Block(System.currentTimeMillis() + i, defaults[i][0], defaults[i][1],
                        defaults[i][2], defaults[i][3], "pending"));
            }
            saveTemplate(key, updated);
            debugLog("Updated untouched built-in " + key + " routine. User-edited templates are preserved.");
        }
        prefs.edit().putBoolean(ROUTINE_MIGRATION_V2_KEY, true).apply();
    }

        private void openTemplateEditor(String key) {
        ArrayList<Block> template = getTemplateBlocks(key);
        // Older versions could leave a copy of a shared block inside a day-type template.
        // Remove those from the editable model; the canonical shared block remains locked below.
        removeSharedRoutineCopies(key, template);
        LinearLayout list = vertical();
        list.setPadding(dp(16), dp(8), dp(16), dp(16));

        final android.app.Dialog editor = new android.app.Dialog(this);
        editor.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = vertical();
        shell.setBackgroundColor(color("#F4F6F1"));

        LinearLayout header = horizontal();
        header.setPadding(dp(18), dp(12), dp(18), dp(12));
        header.addView(secondaryButton("‹", editor::dismiss), new LinearLayout.LayoutParams(dp(44), dp(42)));
        LinearLayout heading = vertical();
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(0, -2, 1);
        headingLp.leftMargin = dp(12);
        header.addView(heading, headingLp);
        heading.addView(text("ROUTINE EDITOR", 10, color("#2E8060"), true));
        heading.addView(text("common".equals(key) ? "Shared routine" : templateLabel(key) + " routine", 22, color("#18372E"), true));
        String editorHint = "common".equals(key)
                ? "These blocks are shared by WFH, office and weekend. Shared blocks always take priority if a day-specific block overlaps in time or has the same activity name."
                : "This routine adds day-type-specific blocks. Shared blocks always take priority; conflicting day-specific blocks are omitted.";
        TextView subtitle = text(editorHint + " Save + apply updates only the selected day.", 11, color("#687A70"), false);
        subtitle.setLineSpacing(dp(2), 1f);
        heading.addView(subtitle);
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = vertical();
        footer.setPadding(dp(14), dp(9), dp(14), dp(12));
        footer.setBackgroundColor(Color.WHITE);
        LinearLayout row = horizontal();
        row.addView(secondaryButton("Cancel", editor::dismiss), new LinearLayout.LayoutParams(0, dp(44), 1));
        Button saveTemplate = secondaryButton("Save template", () -> {
            removeSharedRoutineCopies(key, template);
            if (!"common".equals(key) && hasProtectedSharedConflicts(template)) {
                Toast.makeText(this,
                        "Resolve blocks marked 'Blocked by shared routine' before saving. Shared times are protected.",
                        Toast.LENGTH_LONG).show();
                renderTemplateEditorRows(list, template, editor, key);
                return;
            }
            saveTemplate(key, template);
            editor.dismiss();
            Toast.makeText(this, templateLabel(key) + " template saved only to the " + templateLabel(key) + " template. Existing day schedules are unchanged.", Toast.LENGTH_SHORT).show();
            refreshCurrentWorkspace();
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(44), 1);
        saveLp.leftMargin = dp(8);
        row.addView(saveTemplate, saveLp);
        footer.addView(row);
        Button saveApply = primaryButton("Save + apply to selected day", () -> {
            removeSharedRoutineCopies(key, template);
            if (!"common".equals(key) && hasProtectedSharedConflicts(template)) {
                Toast.makeText(this,
                        "Resolve blocks marked 'Blocked by shared routine' before saving. Shared times are protected.",
                        Toast.LENGTH_LONG).show();
                renderTemplateEditorRows(list, template, editor, key);
                return;
            }
            saveTemplate(key, template);
            String applyMode;
            if (!"common".equals(key)) {
                mode = key;
                planPreviewMode = key;
                applyMode = key;
                SharedPreferences.Editor selection = getPrefs().edit().putString(modeKey(selectedDateKey), mode);
                if (selectedDateKey.equals(todayKey())) selection.putString(MODE_KEY, mode);
                selection.apply();
            } else {
                applyMode = mode;
                planPreviewMode = applyMode;
            }
            loadTemplate(applyMode);
            setPlanDayScheduleExpanded(true);
            editor.dismiss();
            String message = "common".equals(key)
                    ? "Shared routine saved and applied to this day."
                    : templateLabel(key) + " routine saved and applied.";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            refreshCurrentWorkspace();
        });
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(-1, dp(46));
        applyLp.topMargin = dp(8);
        footer.addView(saveApply, applyLp);
        shell.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        editor.setContentView(shell);
        android.view.Window window = editor.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color("#F4F6F1")));
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        editor.show();
        if (window != null) window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT);
        renderTemplateEditorRows(list, template, editor, key);
    }

    private void renderTemplateEditorRows(LinearLayout list, ArrayList<Block> template, android.app.Dialog editor, String key) {
        list.removeAllViews();
        // Keep any legacy shared copies out of the editable day-specific rows.
        removeSharedRoutineCopies(key, template);

        LinearLayout intro = surfaceCard();
        intro.addView(text("common".equals(key) ? "SHARED BLOCKS" : "DAY-TYPE-SPECIFIC BLOCKS", 10, color("#2E8060"), true));
        String introText = "common".equals(key)
                ? "These blocks apply to WFH, office and weekend. They have the highest priority over any day-specific routine."
                : "Shared blocks are shown below as locked and protect their time. Day-specific blocks cannot overlap them or reuse their activity names.";
        intro.addView(text(introText, 12, color("#687A70"), false));
        list.addView(intro);

        if (!"common".equals(key)) {
            ArrayList<Block> sharedBlocks = getTemplateBlocks("common");
            Collections.sort(sharedBlocks, Comparator.comparingInt(b -> mins(b.start)));

            LinearLayout protectedSection = surfaceCard();
            protectedSection.addView(text("SHARED BLOCKS  ·  LOCKED", 10, color("#286B55"), true));
            protectedSection.addView(text("Read-only · Protected for every day type", 11, color("#687A70"), false));
            LinearLayout.LayoutParams protectedSectionLp = new LinearLayout.LayoutParams(-1, -2);
            protectedSectionLp.topMargin = dp(8);
            protectedSectionLp.bottomMargin = dp(8);
            list.addView(protectedSection, protectedSectionLp);

            if (sharedBlocks.isEmpty()) {
                protectedSection.addView(text("No shared blocks yet. Add them in Edit shared routine.", 12, color("#687A70"), false));
            } else {
                for (Block shared : sharedBlocks) {
                    LinearLayout row = horizontal();
                    row.setPadding(dp(10), dp(9), dp(10), dp(9));
                    row.setBackground(surfaceBackground(12));
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                    rowLp.bottomMargin = dp(6);
                    list.addView(row, rowLp);

                    LinearLayout time = vertical();
                    time.addView(text(time12(shared.start), 12, color("#286B55"), true));
                    time.addView(text(time12(shared.end), 10, color("#687A70"), false));
                    row.addView(time, new LinearLayout.LayoutParams(dp(70), -2));

                    LinearLayout details = vertical();
                    LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1);
                    detailsLp.leftMargin = dp(8);
                    detailsLp.rightMargin = dp(6);
                    row.addView(details, detailsLp);
                    details.addView(text(shared.title, 13, color("#18372E"), true));
                    details.addView(text(blockBehaviorLabel(shared), 10, color("#687A70"), false));
                    TextView locked = text("LOCKED", 9, color("#286B55"), true);
                    locked.setGravity(Gravity.CENTER_VERTICAL);
                    row.addView(locked, new LinearLayout.LayoutParams(-2, -2));
                    // Intentionally no click listener or edit/delete controls: common blocks
                    // are visible here for context but can only be edited in the shared editor.
                }
            }

            list.addView(text("DAY-SPECIFIC BLOCKS", 10, color("#2E8060"), true));
        }

        Button add = primaryButton("＋  Add template block", () -> editTemplateBlockDialog(template, -1,
                () -> renderTemplateEditorRows(list, template, editor, key), key));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, dp(46));
        addLp.bottomMargin = dp(10);
        list.addView(add, addLp);

        Collections.sort(template, Comparator.comparingInt(b -> mins(b.start)));
        for (int i = 0; i < template.size(); i++) {
            final int index = i;
            Block b = template.get(i);
            boolean protectedConflict = !"common".equals(key) && hasProtectedSharedConflict(b);
            LinearLayout row = horizontal();
            row.setPadding(dp(10), dp(10), dp(8), dp(10));
            row.setBackground(surfaceBackground(14));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(7);
            list.addView(row, rowLp);

            LinearLayout time = vertical();
            time.addView(text(time12(b.start), 12, protectedConflict ? color("#A45B20") : color("#286B55"), true));
            time.addView(text(time12(b.end), 10, color("#687A70"), false));
            row.addView(time, new LinearLayout.LayoutParams(dp(70), -2));

            LinearLayout details = vertical();
            LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0, -2, 1);
            detailsLp.leftMargin = dp(8);
            detailsLp.rightMargin = dp(6);
            row.addView(details, detailsLp);
            details.addView(text(b.title, 13, color("#18372E"), true));
            details.addView(text(protectedConflict
                            ? "Blocked by shared routine · move or remove this block"
                            : blockBehaviorLabel(b),
                    10, protectedConflict ? color("#A45B20") : color("#687A70"), false));
            boolean lockedSharedCopy = !"common".equals(key) && isSharedRoutineCopy(b);
            if (!lockedSharedCopy) {
                row.addView(secondaryButton("›", () -> editTemplateBlockDialog(template, index,
                        () -> renderTemplateEditorRows(list, template, editor, key), key)),
                        new LinearLayout.LayoutParams(dp(38), dp(38)));
            }
            Button del = secondaryButton("×", () -> {
                if (!"common".equals(key) && isSharedRoutineCopy(b)) {
                    Toast.makeText(this, "Shared blocks are locked. Edit them in the Shared routine instead.", Toast.LENGTH_LONG).show();
                    return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Remove this block?")
                    .setMessage(b.title + " will be removed from the reusable routine. Existing days are unchanged.")
                    .setPositiveButton("Remove", (d, w) -> {
                        // Recheck on confirmation as a final guard before mutating the template.
                        if (!"common".equals(key) && isSharedRoutineCopy(b)) {
                            Toast.makeText(this, "Shared blocks can only be changed in the Shared routine.", Toast.LENGTH_LONG).show();
                            removeSharedRoutineCopies(key, template);
                            renderTemplateEditorRows(list, template, editor, key);
                            return;
                        }
                        template.remove(b);
                        renderTemplateEditorRows(list, template, editor, key);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
            if (!lockedSharedCopy) {
                LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(38), dp(38));
                delLp.leftMargin = dp(5);
                row.addView(del, delLp);
                row.setOnClickListener(v -> editTemplateBlockDialog(template, index,
                        () -> renderTemplateEditorRows(list, template, editor, key), key));
            } else {
                row.setContentDescription("Shared block locked. Edit only in the Shared routine.");
            }
        }
    }

    /**
     * True only when a day-type entry is actually a stale copy of a shared block,
     * rather than a separate day-specific block that merely conflicts with it.
     */
    private boolean isSharedRoutineCopy(Block candidate) {
        if (candidate == null) return false;
        for (Block shared : getTemplateBlocks("common")) {
            // Check identity and shared activity before trusting a legacy ownership marker.
            // Older versions could incorrectly mark a copied shared block as day-specific.
            if (candidate.id == shared.id) return true;
            if (candidate.title != null && shared.title != null
                    && !candidate.title.trim().isEmpty()
                    && candidate.title.trim().equalsIgnoreCase(shared.title.trim())) {
                return true;
            }
        }
        // Explicit ownership is authoritative after checking for recognizable legacy copies.
        // A distinct day-specific block that merely overlaps shared time remains editable so
        // the user can move or remove that conflicting day-specific block.
        return "common".equals(candidate.templateOwner);
    }

    /**
     * Removes stale copies of shared blocks from a day-specific editing model.
     * The canonical entries remain in template_common and are rendered read-only.
     */
    private void removeSharedRoutineCopies(String key, ArrayList<Block> template) {
        if (template == null || "common".equals(key)) return;
        for (int i = template.size() - 1; i >= 0; i--) {
            Block block = template.get(i);
            if (isSharedRoutineCopy(block)) template.remove(i);
        }
    }

    /**
     * Returns true when a day-specific block would conflict with a shared block by
     * overlapping protected time or repeating a shared activity name.
     */
    private boolean hasProtectedSharedConflict(Block candidate) {
        for (Block shared : getTemplateBlocks("common")) {
            if (templateBlocksConflict(candidate, shared)) return true;
        }
        return false;
    }

    private boolean hasProtectedSharedConflicts(ArrayList<Block> dayTemplate) {
        for (Block dayBlock : dayTemplate) {
            if (hasProtectedSharedConflict(dayBlock)) return true;
        }
        return false;
    }

    private void editTemplateBlockDialog(ArrayList<Block> template, int index, Runnable onChanged, String key) {
        if (!"common".equals(key) && index >= 0 && index < template.size()
                && isSharedRoutineCopy(template.get(index))) {
            Toast.makeText(this, "Shared blocks can only be changed in the Shared routine.", Toast.LENGTH_LONG).show();
            removeSharedRoutineCopies(key, template);
            onChanged.run();
            return;
        }
        boolean adding = index < 0;
        Block original;
        if (adding) {
            int start = 360;
            for (Block b : template) start = Math.max(start, mins(b.end));
            int end;
            if (!"common".equals(key)) {
                ArrayList<Block> occupiedBlocks = new ArrayList<>(getTemplateBlocks("common"));
                occupiedBlocks.addAll(template);
                boolean foundFreeSlot = false;
                for (int proposedStart = start; proposedStart + 30 <= 1439; proposedStart += 15) {
                    boolean occupied = false;
                    for (Block existing : occupiedBlocks) {
                        if (proposedStart < mins(existing.end) && mins(existing.start) < proposedStart + 30) {
                            occupied = true;
                            break;
                        }
                    }
                    if (!occupied) {
                        start = proposedStart;
                        foundFreeSlot = true;
                        break;
                    }
                }
                // If the day is fully occupied, leave validation to the save guard.
                end = foundFreeSlot ? start + 30 : Math.min(1439, start + 30);
            } else {
                end = Math.min(1439, start + 30);
            }
            original = new Block(System.currentTimeMillis(), toTime(start), toTime(end),
                    "New block", "flexible", "pending");
        } else {
            original = template.get(index);
        }
        if (original.templateOwner == null) original.templateOwner = key;

        LinearLayout form = vertical();
        form.setPadding(dp(20), 0, dp(20), 0);
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Block name");
        title.setText(original.title);
        title.setTextColor(color("#18372E"));
        title.setHintTextColor(color("#82948A"));
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        final int[] start = {mins(original.start)};
        final int[] end = {mins(original.end)};
        final String[] selectedPriority = {original.priority};
        final String[] selectedRigidity = {original.rigidity};

        Button startButton = chip("Start • " + time12(original.start));
        Button endButton = chip("End • " + time12(original.end));
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(40));
        startLp.topMargin = dp(8);
        form.addView(startButton, startLp);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(-1, dp(40));
        endLp.topMargin = dp(6);
        form.addView(endButton, endLp);

        startButton.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(this, (tv, h, m) -> {
                start[0] = h * 60 + m;
                startButton.setText("Start • " + time12(toTime(start[0])));
            }, start[0] / 60, start[0] % 60, true);
            picker.show();
        });
        endButton.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(this, (tv, h, m) -> {
                end[0] = h * 60 + m;
                endButton.setText("End • " + time12(toTime(end[0])));
            }, end[0] / 60, end[0] % 60, true);
            picker.show();
        });

        TextView rigidityLabel = text("RIGIDITY", 10, color("#91A197"), true);
        LinearLayout.LayoutParams rigidityLabelLp = new LinearLayout.LayoutParams(-1, -2);
        rigidityLabelLp.topMargin = dp(12);
        form.addView(rigidityLabel, rigidityLabelLp);
        LinearLayout rigidities = horizontal();
        form.addView(rigidities, new LinearLayout.LayoutParams(-1, dp(40)));
        String[] rigidityNames = {"Fixed time", "Flexible time"};
        String[] rigidityValues = {"fixed", "flexible"};
        for (int i = 0; i < rigidityNames.length; i++) {
            final int idx = i;
            Button b = chip(rigidityNames[i]);
            boolean selected = rigidityValues[i].equals(selectedRigidity[0]);
            b.setTextColor(selected ? Color.WHITE : color("#D6E1D8"));
            b.setBackground(bg(selected ? "#245B45" : "#1B2621", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(6);
            rigidities.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedRigidity[0] = rigidityValues[idx];
                for (int j = 0; j < rigidities.getChildCount(); j++) {
                    Button child = (Button) rigidities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    child.setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        TextView priorityTitle = text("PRIORITY", 10, color("#91A197"), true);
        LinearLayout.LayoutParams priorityTitleLp = new LinearLayout.LayoutParams(-1, -2);
        priorityTitleLp.topMargin = dp(10);
        form.addView(priorityTitle, priorityTitleLp);
        LinearLayout priorities = horizontal();
        form.addView(priorities, new LinearLayout.LayoutParams(-1, dp(40)));
        String[] priorityNames = {"Critical", "Important", "Normal", "Optional"};
        String[] priorityValues = {"critical", "important", "normal", "optional"};
        for (int i = 0; i < priorityNames.length; i++) {
            final int idx = i;
            Button b = chip(priorityNames[i]);
            boolean selected = priorityValues[i].equals(selectedPriority[0]);
            b.setTextColor(selected ? Color.WHITE : color("#D6E1D8"));
            b.setBackground(bg(selected ? "#245B45" : "#1B2621", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(4);
            priorities.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedPriority[0] = priorityValues[idx];
                for (int j = 0; j < priorities.getChildCount(); j++) {
                    Button child = (Button) priorities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#D6E1D8"));
                    child.setBackground(bg(j == idx ? "#245B45" : "#1B2621", 12));
                }
            });
        }

        showFullScreenEditor("ROUTINE TEMPLATE", adding ? "Add template block" : "Edit template block",
                "This changes the reusable routine. Existing days change only when you apply it.", form, () -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        title.setError("Give this block a name");
                        return false;
                    }
                    if (end[0] <= start[0]) {
                        Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    if (!"common".equals(key)) {
                        Block candidate = new Block(0L, toTime(start[0]), toTime(end[0]),
                                name, selectedPriority[0], selectedRigidity[0], "pending");
                        if (hasProtectedSharedConflict(candidate)) {
                            Toast.makeText(this,
                                    "That time or activity is protected by the Shared routine. Choose a non-conflicting slot or activity.",
                                    Toast.LENGTH_LONG).show();
                            return false;
                        }
                    }
                    if (adding) {
                        template.add(new Block(System.currentTimeMillis(), toTime(start[0]), toTime(end[0]),
                                name, selectedPriority[0], selectedRigidity[0], "pending"));
                    } else {
                        original.title = name;
                        original.start = toTime(start[0]);
                        original.end = toTime(end[0]);
                        original.priority = selectedPriority[0];
                        original.rigidity = selectedRigidity[0];
                    }
                    Collections.sort(template, Comparator.comparingInt(block -> mins(block.start)));
                    onChanged.run();
                    return true;
                });
    }



    private void loadTemplate(String key) {
        ArrayList<Block> previous = new ArrayList<>(blocks);
        ArrayList<Block> replacement = new ArrayList<>();
        for (Block source : getCombinedTemplateBlocks(key)) {
            Block matching = null;
            for (int i = 0; i < previous.size(); i++) {
                Block candidate = previous.get(i);
                if (source.start.equals(candidate.start) && source.end.equals(candidate.end)
                        && source.title != null && source.title.equalsIgnoreCase(candidate.title == null ? "" : candidate.title)) {
                    matching = candidate;
                    previous.remove(i);
                    break;
                }
            }
            if (matching == null) {
                matching = new Block(System.currentTimeMillis() + replacement.size(),
                        source.start, source.end, source.title, source.priority, source.rigidity, "pending");
            } else {
                // Keep progress/focus history for blocks that still exist in the applied template.
                matching.start = source.start;
                matching.end = source.end;
                matching.title = source.title;
                matching.priority = source.priority;
                matching.rigidity = source.rigidity;
            }
            matching.templateOwner = source.templateOwner == null ? key : source.templateOwner;
            replacement.add(matching);
        }
        blocks.clear();
        blocks.addAll(replacement);
        save();
    }

    private void renderOfficeCard() {
        if (officeCard == null) return;
        officeCard.setVisibility("office".equals(mode) ? View.VISIBLE : View.GONE);
        if (!"office".equals(mode)) return;

        try {
            JSONObject day = getOfficeDay(selectedDateKey, false);
            long now = System.currentTimeMillis();
            long office = officeDuration(day, now);
            long commute = commuteDuration(day, now);
            boolean open = hasOpenOfficeSession(day);
            boolean commuteOpen = hasOpenCommute(day);

            boolean viewingToday = selectedDateKey.equals(todayKey());
            if (viewingToday && open) {
                officeStatus.setText("IN OFFICE");
                officeAction.setText("CHECK OUT");
                officeAction.setBackground(bg("#B84B5C", 12));
                officeAction.setEnabled(true);
            } else if (viewingToday) {
                officeStatus.setText(day == null ? "Ready for office" : "OUT OF OFFICE");
                officeAction.setText("CHECK IN");
                officeAction.setBackground(bg("#40B58A", 12));
                officeAction.setEnabled(true);
            } else {
                officeStatus.setText(day == null ? "No office record" : "OFFICE RECORD");
                officeAction.setText("EDIT TIMES");
                officeAction.setBackground(bg("#34483D", 12));
                officeAction.setEnabled(true);
            }

            StringBuilder times = new StringBuilder();
            if (day != null) {
                JSONArray sessions = day.optJSONArray("sessions");
                if (sessions != null) {
                    for (int i = 0; i < sessions.length(); i++) {
                        JSONObject s = sessions.optJSONObject(i);
                        if (s == null) continue;
                        long in = s.optLong("in", 0);
                        long out = s.optLong("out", 0);
                        if (in > 0) {
                            if (times.length() > 0) times.append("  •  ");
                            times.append("IN ").append(clock(in));
                        }
                        if (out > 0) {
                            times.append("  OUT ").append(clock(out));
                        } else if (in > 0) {
                            times.append("  •  active");
                        }
                    }
                }
            }
            officeTimes.setText(times.length() == 0
                    ? "No office time logged today"
                    : times.toString());

            commuteView.setText("Today • Office " + (viewingToday && open ? fmtPreciseMillis(office) : fmtMillis(office))
                    + "  •  Commute " + (viewingToday && commuteOpen ? fmtPreciseMillis(commute) : fmtMillis(commute)));

            if (!selectedDateKey.equals(todayKey())) {
                commuteAction.setText("View commute details");
                commuteAction.setEnabled(day != null);
                commuteAction.setAlpha(day != null ? 1f : 0.5f);
            }

            long ciS = day == null ? 0 : day.optLong("commuteInStart", 0);
            long ciE = day == null ? 0 : day.optLong("commuteInEnd", 0);
            long coS = day == null ? 0 : day.optLong("commuteOutStart", 0);
            long coE = day == null ? 0 : day.optLong("commuteOutEnd", 0);
            if (ciS > 0 && ciE == 0) commuteAction.setText("Finish commute");
            else if (ciE > 0 && coS == 0) commuteAction.setText("Start home commute");
            else if (coS > 0 && coE == 0) commuteAction.setText("Finish home commute");
            else if (coE > 0) commuteAction.setText("Commute complete");
            else commuteAction.setText("Start commute");
            if (selectedDateKey.equals(todayKey())) {
                commuteAction.setEnabled(!(coE > 0));
                commuteAction.setAlpha(coE > 0 ? 0.55f : 1f);
            }
        } catch (Exception ignored) {
            officeStatus.setText("Ready");
            officeAction.setText("CHECK IN");
            commuteAction.setText("Start commute");
        }
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private String dateKey(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timestamp));
    }

    private JSONObject getOfficeDay(String key, boolean create) {
        try {
            SharedPreferences p = getPrefs();
            String raw = p.getString(OFFICE_LOG_KEY, "[]");
            JSONArray arr = new JSONArray(raw);
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

    private boolean saveOfficeDay(JSONObject day) {
        try {
            if (day == null) {
                debugLog("Office day save rejected: missing record.");
                return false;
            }
            String key = day.optString("date", "");
            if (key.trim().isEmpty()) {
                debugLog("Office day save rejected: missing date.");
                return false;
            }
            JSONArray arr = new JSONArray(getPrefs().getString(OFFICE_LOG_KEY, "[]"));
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
            getPrefs().edit().putString(OFFICE_LOG_KEY, arr.toString()).apply();
            DayFlowWidgetProvider.refreshAll(this);
            debugLog("Office day saved: " + key);
            return true;
        } catch (Exception e) {
            debugLog("Office day save failed: " + e.getMessage());
            Toast.makeText(this, "Could not save the attendance record.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private boolean hasOpenOfficeSession(JSONObject day) {
        if (day == null) return false;
        JSONArray sessions = day.optJSONArray("sessions");
        if (sessions == null || sessions.length() == 0) return false;
        JSONObject last = sessions.optJSONObject(sessions.length() - 1);
        return last != null && last.optLong("in", 0) > 0 && last.optLong("out", 0) == 0;
    }

    private boolean hasCommuteStart(JSONObject day) {
        return day != null && day.optLong("commuteOutStart", 0) == 0
                && day.optLong("commuteInStart", 0) > 0;
    }

    private boolean hasOpenCommute(JSONObject day) {
        if (day == null) return false;
        return (day.optLong("commuteInStart", 0) > 0 && day.optLong("commuteInEnd", 0) == 0)
                || (day.optLong("commuteOutStart", 0) > 0 && day.optLong("commuteOutEnd", 0) == 0);
    }

    private long officeDuration(JSONObject day, long now) {
        if (day == null) return 0;
        return OfficeAttendanceTime.officeDuration(day.optJSONArray("sessions"), now);
    }

    private long commuteDuration(JSONObject day, long now) {
        return OfficeAttendanceTime.commuteDuration(day, now);
    }

    private void toggleOfficeAttendance() {
        try {
            long now = System.currentTimeMillis();
            JSONObject day = getOfficeDay(todayKey(), true);
            if (day == null) {
                Toast.makeText(this, "Could not open today's attendance record.", Toast.LENGTH_LONG).show();
                return;
            }
            JSONArray sessions = day.optJSONArray("sessions");
            if (sessions == null) {
                sessions = new JSONArray();
                day.put("sessions", sessions);
            }

            boolean checkingOut = hasOpenOfficeSession(day);
            if (checkingOut) {
                JSONObject last = sessions.optJSONObject(sessions.length() - 1);
                if (last == null) throw new IllegalStateException("The open office session is missing.");
                last.put("out", now);
            } else {
                JSONObject session = new JSONObject();
                session.put("in", now);
                session.put("out", 0);
                sessions.put(session);
            }
            if (!saveOfficeDay(day)) return;
            Toast.makeText(this,
                    (checkingOut ? "Checked out at " : "Checked in at ") + clock(now),
                    Toast.LENGTH_SHORT).show();
            render();
        } catch (Exception e) {
            debugLog("Attendance toggle failed: " + e.getMessage());
            Toast.makeText(this, "Could not update attendance. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private void toggleCommute() {
        try {
            long now = System.currentTimeMillis();
            JSONObject day = getOfficeDay(todayKey(), true);
            if (day == null) {
                Toast.makeText(this, "Could not open today's attendance record.", Toast.LENGTH_LONG).show();
                return;
            }

            long inStart = day.optLong("commuteInStart", 0);
            long inEnd = day.optLong("commuteInEnd", 0);
            long outStart = day.optLong("commuteOutStart", 0);
            long outEnd = day.optLong("commuteOutEnd", 0);
            String statusMessage;

            if (inStart == 0) {
                day.put("commuteInStart", now);
                statusMessage = "Commute started.";
            } else if (inEnd == 0) {
                day.put("commuteInEnd", now);
                statusMessage = "Office commute logged.";
            } else if (outStart == 0) {
                day.put("commuteOutStart", now);
                statusMessage = "Home commute started.";
            } else if (outEnd == 0) {
                day.put("commuteOutEnd", now);
                statusMessage = "Home commute logged.";
            } else {
                Toast.makeText(this, "Today's commute is already complete.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!saveOfficeDay(day)) return;
            Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show();
            render();
        } catch (Exception e) {
            debugLog("Commute toggle failed: " + e.getMessage());
            Toast.makeText(this, "Could not update commute tracking. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private void showAttendanceHistory() {
        switchPage("office");
    }

    /**
     * Opens any historical date, not just the rolling 31-day ledger. A date with
     * no existing entry opens a blank editor so a missed record can be entered.
     */
    private void chooseAttendanceHistoryDate() {
        Calendar initial = Calendar.getInstance();
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.clear();
            chosen.set(year, month, day, 12, 0, 0);
            String dayKey = keyForDate(chosen.getTime());
            JSONObject record = getOfficeDay(dayKey, false);
            JSONArray sessions = record == null ? null : record.optJSONArray("sessions");
            if (sessions != null && sessions.length() > 1) {
                Date parsed = parseDateKey(dayKey);
                String label = parsed == null ? dayKey
                        : new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(parsed);
                showAttendanceDayDetail(label, dayKey, record);
            } else {
                editAttendanceDay(dayKey);
            }
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        picker.setTitle("Choose attendance date");
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }

    private long[] attendanceTotalsForWeek(String mondayKey) {
        long officeTotal = 0L;
        long commuteTotal = 0L;
        Date startDate = parseDateKey(mondayKey);
        if (startDate == null) return new long[]{officeTotal, commuteTotal};
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(startDate);
        cursor.set(Calendar.HOUR_OF_DAY, 12);
        cursor.set(Calendar.MINUTE, 0);
        cursor.set(Calendar.SECOND, 0);
        cursor.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            String key = keyForDate(cursor.getTime());
            JSONObject record = getOfficeDay(key, false);
            long referenceTime = key.equals(todayKey()) ? now : 0L;
            officeTotal += officeDuration(record, referenceTime);
            commuteTotal += commuteDuration(record, referenceTime);
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }
        return new long[]{officeTotal, commuteTotal};
    }

    private void showAttendanceDayDetail(String date, String dayKey, JSONObject day) {
        try {
            if (day == null) {
                Toast.makeText(this, "No attendance record for this day.", Toast.LENGTH_SHORT).show();
                return;
            }
            long now = System.currentTimeMillis();
            long office = officeDuration(day, dayKey.equals(todayKey()) ? now : 0);
            long commute = commuteDuration(day, dayKey.equals(todayKey()) ? now : 0);

            LinearLayout content = vertical();
            content.setPadding(dp(18), dp(8), dp(18), dp(16));
            LinearLayout summary = surfaceCard();
            summary.addView(text("RECORDED TOTALS", 10, color("#2E8060"), true));
            summary.addView(text("Office  " + fmtMillis(office), 21, color("#18372E"), true));
            summary.addView(text("Commute  " + fmtMillis(commute), 13, color("#687A70"), false));
            content.addView(summary);

            if (OfficeAttendanceTime.hasInvalidRecordedTimes(day)) {
                LinearLayout warning = surfaceCard();
                warning.addView(text("CHECK RECORDED TIMES", 10, color("#A76821"), true));
                warning.addView(text("At least one end time is earlier than its start time, so that interval currently contributes 0m. For example, 5:05 PM is 17:05 in a 24-hour clock. Edit the affected time before saving.",
                        12, color("#687A70"), false));
                LinearLayout.LayoutParams warningLp = new LinearLayout.LayoutParams(-1, -2);
                warningLp.topMargin = dp(10);
                content.addView(warning, warningLp);
            }

            Calendar weekStart = Calendar.getInstance();
            Date selectedHistoryDate = parseDateKey(dayKey);
            if (selectedHistoryDate != null) weekStart.setTime(selectedHistoryDate);
            weekStart.set(Calendar.HOUR_OF_DAY, 12);
            weekStart.set(Calendar.MINUTE, 0);
            weekStart.set(Calendar.SECOND, 0);
            weekStart.set(Calendar.MILLISECOND, 0);
            int weekday = weekStart.get(Calendar.DAY_OF_WEEK);
            weekStart.add(Calendar.DAY_OF_YEAR, -(weekday == Calendar.SUNDAY ? 6 : weekday - Calendar.MONDAY));
            Calendar weekEnd = (Calendar) weekStart.clone();
            weekEnd.add(Calendar.DAY_OF_YEAR, 6);
            long[] weekTotals = attendanceTotalsForWeek(keyForDate(weekStart.getTime()));
            String weekRange = new SimpleDateFormat("d MMM", Locale.getDefault()).format(weekStart.getTime())
                    + " – " + new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(weekEnd.getTime());
            LinearLayout weekly = surfaceCard();
            weekly.addView(text("WEEKLY CUMULATIVE  ·  " + weekRange.toUpperCase(Locale.getDefault()),
                    10, color("#2E8060"), true));
            weekly.addView(text("Office  " + fmtMillis(weekTotals[0]), 18, color("#18372E"), true));
            weekly.addView(text("Commute  " + fmtMillis(weekTotals[1]), 12, color("#687A70"), false));
            LinearLayout.LayoutParams weeklyLp = new LinearLayout.LayoutParams(-1, -2);
            weeklyLp.topMargin = dp(10);
            content.addView(weekly, weeklyLp);

            TextView sessionsTitle = text("OFFICE SESSIONS", 10, color("#2E8060"), true);
            LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2);
            stp.topMargin = dp(14);
            stp.bottomMargin = dp(6);
            content.addView(sessionsTitle, stp);
            final android.app.Dialog[] attendanceDetailDialog = new android.app.Dialog[1];
            JSONArray sessions = day.optJSONArray("sessions");
            boolean anySession = false;
            if (sessions != null) {
                for (int i = 0; i < sessions.length(); i++) {
                    JSONObject session = sessions.optJSONObject(i);
                    if (session == null) continue;
                    anySession = true;
                    long in = session.optLong("in", 0);
                    long out = session.optLong("out", 0);
                    String value = "Session " + (i + 1);
                    String times = (in > 0 ? clock(in) : "Not recorded") + "  →  " + (out > 0 ? clock(out) : "Still open");
                    final int selectedSessionIndex = i;
                    addManageRow(content, value, times, () -> {
                        if (attendanceDetailDialog[0] != null && attendanceDetailDialog[0].isShowing()) {
                            attendanceDetailDialog[0].dismiss();
                        }
                        editAttendanceDay(dayKey, selectedSessionIndex, true);
                    });
                }
            }
            if (!anySession) content.addView(text("No office sessions recorded.", 12, color("#687A70"), false));

            long ciStart = day.optLong("commuteInStart", 0);
            long ciEnd = day.optLong("commuteInEnd", 0);
            long coStart = day.optLong("commuteOutStart", 0);
            long coEnd = day.optLong("commuteOutEnd", 0);
            LinearLayout travel = surfaceCard();
            LinearLayout.LayoutParams travelLp = new LinearLayout.LayoutParams(-1, -2);
            travelLp.topMargin = dp(10);
            content.addView(travel, travelLp);
            sectionLabel(travel, "Commute records");
            travel.addView(text("To office", 12, color("#18372E"), true));
            travel.addView(text(timeLabel(ciStart) + "  →  " + timeLabel(ciEnd), 12, color("#687A70"), false));
            LinearLayout.LayoutParams homeLp = new LinearLayout.LayoutParams(-1, -2);
            homeLp.topMargin = dp(10);
            travel.addView(text("Home", 12, color("#18372E"), true), homeLp);
            travel.addView(text(timeLabel(coStart) + "  →  " + timeLabel(coEnd), 12, color("#687A70"), false));

            attendanceDetailDialog[0] = showFullScreenViewer("ATTENDANCE HISTORY", date,
                    "Exact office and commute records for this day.", content, "Edit recorded times",
                    () -> editAttendanceDay(dayKey, 0, true));
        } catch (Exception e) {
            debugLog("Attendance detail open failed: " + e.getMessage());
            Toast.makeText(this, "Couldn't load that day.", Toast.LENGTH_SHORT).show();
        }
    }

    private void editAttendanceDay(String dayKey) {
        editAttendanceDay(dayKey, 0, false);
    }

    private void editAttendanceDay(String dayKey, int requestedSessionIndex, boolean returnToDetail) {
        try {
            JSONObject day = getOfficeDay(dayKey, true);
            JSONArray sessions = day.optJSONArray("sessions");
            if (sessions == null) {
                sessions = new JSONArray();
                day.put("sessions", sessions);
            }
            if (sessions.length() == 0) {
                JSONObject session = new JSONObject();
                session.put(OfficeAttendanceTime.SESSION_IN, 0);
                session.put(OfficeAttendanceTime.SESSION_OUT, 0);
                sessions.put(session);
            }
            int sessionIndex = Math.max(0, Math.min(requestedSessionIndex, sessions.length() - 1));
            JSONObject session = sessions.optJSONObject(sessionIndex);
            if (session == null) {
                session = new JSONObject();
                session.put(OfficeAttendanceTime.SESSION_IN, 0);
                session.put(OfficeAttendanceTime.SESSION_OUT, 0);
                sessions.put(sessionIndex, session);
            }
            final JSONObject sessionForEdit = session;

            LinearLayout content = vertical();
            content.setPadding(dp(18), dp(2), dp(18), dp(2));
            TextView note = text("Correct the recorded times. Changes are applied only when you save.", 11, color("#687A70"), false);
            content.addView(note);

            Button inBtn = actionButton("Office in  •  " + (session.optLong(OfficeAttendanceTime.SESSION_IN, 0) > 0 ? clock(session.optLong(OfficeAttendanceTime.SESSION_IN, 0)) : "Not set"), Color.WHITE, "#263A31");
            Button outBtn = actionButton("Office out  •  " + (session.optLong(OfficeAttendanceTime.SESSION_OUT, 0) > 0 ? clock(session.optLong(OfficeAttendanceTime.SESSION_OUT, 0)) : "Not set"), Color.WHITE, "#263A31");
            Button ciStartBtn = actionButton("Commute to office · start  •  " + timeLabel(day.optLong("commuteInStart", 0)), Color.WHITE, "#263A31");
            Button ciEndBtn = actionButton("Commute to office · end  •  " + timeLabel(day.optLong("commuteInEnd", 0)), Color.WHITE, "#263A31");
            Button coStartBtn = actionButton("Commute home · start  •  " + timeLabel(day.optLong("commuteOutStart", 0)), Color.WHITE, "#263A31");
            Button coEndBtn = actionButton("Commute home · end  •  " + timeLabel(day.optLong("commuteOutEnd", 0)), Color.WHITE, "#263A31");

            for (Button b : new Button[]{inBtn, outBtn, ciStartBtn, ciEndBtn, coStartBtn, coEndBtn}) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
                lp.topMargin = dp(7);
                content.addView(b, lp);
            }

            showFullScreenEditor("OFFICE RECORD", "Edit attendance",
                    "Correct the exact timestamps. Totals are recalculated from these saved times.", content, () -> {
                        if (OfficeAttendanceTime.hasInvalidClosedInterval(
                                sessionForEdit.optLong(OfficeAttendanceTime.SESSION_IN, 0L),
                                sessionForEdit.optLong(OfficeAttendanceTime.SESSION_OUT, 0L))
                                || OfficeAttendanceTime.hasInvalidCommuteIntervals(day)) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Check the recorded times")
                                    .setMessage("An end time is earlier than its start time. Correct it before saving; use the PM option for an afternoon checkout such as 5:05 PM.")
                                    .setPositiveButton("Review times", null)
                                    .show();
                            return false;
                        }
                        if (!saveOfficeDay(day)) return false;
                        render();
                        renderOfficePage();
                        if (returnToDetail) {
                            handler.post(() -> {
                                Date parsed = parseDateKey(dayKey);
                                String label = parsed == null ? dayKey
                                        : new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(parsed);
                                showAttendanceDayDetail(label, dayKey, getOfficeDay(dayKey, false));
                            });
                        }
                        return true;
                    });

            inBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, OfficeAttendanceTime.SESSION_IN, sessionForEdit, inBtn));
            outBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, OfficeAttendanceTime.SESSION_OUT, sessionForEdit, outBtn));
            ciStartBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, "commuteInStart", day, ciStartBtn));
            ciEndBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, "commuteInEnd", day, ciEndBtn));
            coStartBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, "commuteOutStart", day, coStartBtn));
            coEndBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, "commuteOutEnd", day, coEndBtn));
        } catch (Exception ignored) {
            Toast.makeText(this, "Couldn't edit attendance.", Toast.LENGTH_SHORT).show();
        }
    }

    private String timeLabel(long value) {
        return value > 0 ? clock(value) : "Not set";
    }

    private void pickAttendanceTime(String dayKey, String field, JSONObject target, Button button) {
        long existing = target.optLong(field, 0);
        Calendar c = Calendar.getInstance();
        Date selectedDate = parseDateKey(dayKey);
        if (selectedDate != null) c.setTime(selectedDate);
        if (existing > 0) {
            Calendar previous = Calendar.getInstance();
            previous.setTimeInMillis(existing);
            c.set(Calendar.HOUR_OF_DAY, previous.get(Calendar.HOUR_OF_DAY));
            c.set(Calendar.MINUTE, previous.get(Calendar.MINUTE));
        }
        TimePickerDialog picker = new TimePickerDialog(this, (v, h, m) -> {
            try {
                long chosenTimestamp = OfficeAttendanceTime.timestampForDayAndTime(dayKey, h, m);
                if (OfficeAttendanceTime.SESSION_IN.equals(field) || OfficeAttendanceTime.SESSION_OUT.equals(field)) {
                    OfficeAttendanceTime.setSessionTimestamp(target, field, chosenTimestamp);
                } else {
                    target.put(field, chosenTimestamp);
                }
                String[] parts = button.getText().toString().split("  •  ");
                button.setText((parts.length == 0 ? "Time" : parts[0]) + "  •  " + clock(chosenTimestamp));
            } catch (Exception e) {
                debugLog("Attendance time edit failed: " + e.getMessage());
                Toast.makeText(this, "Couldn't update that time.", Toast.LENGTH_SHORT).show();
            }
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false);
        picker.show();
    }

    private void debugLog(String message) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + " | APP | " + message;
        Log.d(DEBUG_TAG, message);
        try {
            SharedPreferences p = getPrefs();
            String old = p.getString(DEBUG_LOG_KEY, "");
            String combined = old + line + "\n";
            if (combined.length() > 20000) combined = combined.substring(combined.length() - 20000);
            p.edit().putString(DEBUG_LOG_KEY, combined).apply();
        } catch (Exception ignored) {
        }
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Block b : blocks) {
                JSONObject o = new JSONObject();
                o.put("id", b.id);
                o.put("start", b.start);
                o.put("end", b.end);
                o.put("title", b.title);
                o.put("priority", b.priority);
                o.put("rigidity", b.rigidity);
                o.put("status", b.status);
                o.put("actualMs", b.actualMs);
                o.put("actualStartedAt", b.actualStartedAt);
                if (b.templateOwner != null) o.put("templateOwner", b.templateOwner);
                arr.put(o);
            }
            getPrefs().edit()
                    .putString(blocksKey(selectedDateKey), arr.toString())
                    .putString(modeKey(selectedDateKey), mode)
                    .putString(KEY, selectedDateKey.equals(todayKey()) ? arr.toString() : getPrefs().getString(KEY, arr.toString()))
                    .putString(MODE_KEY, mode)
                    .apply();
            debugLog("Day saved: date=" + selectedDateKey + " mode=" + mode + " blocks=" + blocks.size());
            DayFlowWidgetProvider.refreshAll(this);
        } catch (Exception e) {
            debugLog("Day save failed for " + selectedDateKey + ": " + e.getMessage());
            Toast.makeText(this, "Could not save the schedule. Please try again.", Toast.LENGTH_LONG).show();
        }
        render();
    }

    private void load() {
        loadSelectedDate();
        debugLog("App load: date=" + selectedDateKey + " mode=" + mode + " blocks=" + blocks.size());
    }

    private void loadTemplateSilent() {
        for (Block source : getCombinedTemplateBlocks(mode)) {
            Block dailyCopy = new Block(System.currentTimeMillis() + blocks.size(),
                    source.start, source.end, source.title, source.priority, source.rigidity, "pending");
            dailyCopy.templateOwner = source.templateOwner == null ? mode : source.templateOwner;
            blocks.add(dailyCopy);
        }
    }

    // Restored utility methods kept independent from the workspace UI refactor.

private void ensureScreenTimeWorker() {
        try {
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    ScreenTimeWorker.class, 15, java.util.concurrent.TimeUnit.MINUTES).build();
            WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                    "dayflow-screen-time-history", ExistingPeriodicWorkPolicy.KEEP, request);
        } catch (Exception e) {
            debugLog("Screen-time work scheduling failed: " + e.getMessage());
        }
    }

private void showScreenTimeTracker() { switchPage("screenTime"); }

private void requestExportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "dayflow-backup-" + todayKey() + ".json");
        try { startActivityForResult(intent, REQUEST_EXPORT_BACKUP); }
        catch (Exception e) { Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show(); }
    }

private void requestImportBackup() {
        new AlertDialog.Builder(this).setTitle("Import DayFlow backup?")
                .setMessage("Restoring a backup replaces all current local DayFlow data. Export the current data first if it needs to be kept.")
                .setPositiveButton("Choose file", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    try { startActivityForResult(intent, REQUEST_IMPORT_BACKUP); }
                    catch (Exception e) { Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show(); }
                }).setNegativeButton("Cancel", null).show();
    }

@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_EXPORT_BACKUP) writeBackup(data.getData());
        else if (requestCode == REQUEST_IMPORT_BACKUP) readBackupForRestore(data.getData());
    }

private JSONObject buildBackupDocument() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "DayFlow");
        root.put("formatVersion", 1);
        root.put("exportedAt", System.currentTimeMillis());
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : getPrefs().getAll().entrySet()) {
            Object value = entry.getValue();
            JSONObject typed = new JSONObject();
            if (value instanceof String) { typed.put("type", "string"); typed.put("value", value); }
            else if (value instanceof Boolean) { typed.put("type", "boolean"); typed.put("value", value); }
            else if (value instanceof Integer) { typed.put("type", "int"); typed.put("value", value); }
            else if (value instanceof Long) { typed.put("type", "long"); typed.put("value", value); }
            else if (value instanceof Float) { typed.put("type", "float"); typed.put("value", value); }
            else if (value instanceof Set) {
                JSONArray set = new JSONArray();
                for (Object item : (Set<?>) value) set.put(String.valueOf(item));
                typed.put("type", "stringSet");
                typed.put("value", set);
            } else continue;
            values.put(entry.getKey(), typed);
        }
        root.put("preferences", values);
        return root;
    }

    private void writeBackup(Uri uri) {
        try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new java.io.IOException("Could not open backup destination");
            output.write(buildBackupDocument().toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
            debugLog("Backup exported");
            Toast.makeText(this, "DayFlow backup exported.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            debugLog("Backup export failed: " + e.getMessage());
            Toast.makeText(this, "Backup export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

private void readBackupForRestore(Uri uri) {
        final int maxBytes = DayFlowBackupCodec.MAX_DOCUMENT_CHARS;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new java.io.IOException("Could not open backup file.");
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] bytes = new byte[4096];
            int count;
            int total = 0;
            while ((count = input.read(bytes)) != -1) {
                total += count;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("Backup is too large to restore safely.");
                }
                buffer.write(bytes, 0, count);
            }
            String raw = buffer.toString(java.nio.charset.StandardCharsets.UTF_8.name());
            DayFlowBackupCodec.Document document = DayFlowBackupCodec.parse(raw);
            Map<String, Object> values = document.preferences;
            new AlertDialog.Builder(this).setTitle("Restore backup?")
                    .setMessage("The backup contains " + values.size()
                            + " validated values. It replaces current plans, attendance, syllabus and settings.")
                    .setPositiveButton("Restore", (d, w) -> restoreBackupValues(values))
                    .setNegativeButton("Cancel", null).show();
        } catch (Exception e) {
            debugLog("Backup import rejected: " + e.getMessage());
            Toast.makeText(this, "Couldn't use this backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void restoreBackupValues(Map<String, Object> values) {
        if (values == null) {
            Toast.makeText(this, "Backup contains no validated data.", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            // DayFlowBackupCodec validates all entries before this editor is ever cleared.
            SharedPreferences.Editor editor = getPrefs().edit().clear();
            for (Map.Entry<String, Object> item : values.entrySet()) {
                String key = item.getKey();
                Object value = item.getValue();
                if (value instanceof String) editor.putString(key, (String) value);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Long) editor.putLong(key, (Long) value);
                else if (value instanceof Float) editor.putFloat(key, (Float) value);
                else if (value instanceof java.util.Set) {
                    java.util.HashSet<String> set = new java.util.HashSet<>();
                    for (Object member : (java.util.Set<?>) value) {
                        if (!(member instanceof String)) {
                            throw new IllegalStateException("Validated backup contained a non-string set member.");
                        }
                        set.add((String) member);
                    }
                    editor.putStringSet(key, set);
                } else {
                    throw new IllegalStateException("Validated backup contained an unsupported value type.");
                }
            }
            if (!editor.commit()) {
                throw new IllegalStateException("Android could not save the restored data.");
            }
            selectedDateKey = todayKey();
            load();
            ensureRoadmapData();
            refreshRoadmapTabContent();
            render();
            ensureScreenTimeWorker();
            DayFlowWidgetProvider.refreshAll(this);
            Toast.makeText(this, "DayFlow backup restored.", Toast.LENGTH_LONG).show();
            debugLog("Backup restored: values=" + values.size());
        } catch (Exception e) {
            debugLog("Backup restore failed: " + e.getMessage());
            Toast.makeText(this, "Backup restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showDiagnostics() {
        String log = getPrefs().getString(DEBUG_LOG_KEY, "No diagnostics recorded yet.");
        if (log.length() > 12000) log = log.substring(log.length() - 12000);
        LinearLayout content = vertical();
        content.setPadding(dp(14), dp(6), dp(14), dp(16));
        TextView explanation = text("Recent app/widget events, saves and timer transitions. Select and copy this text when reporting a problem.", 12, color("#687A70"), false);
        explanation.setLineSpacing(dp(2), 1f);
        content.addView(explanation);
        TextView view = text(log, 11, color("#18372E"), false);
        view.setTextIsSelectable(true);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackground(bg("#FFFFFF", 12));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.topMargin = dp(10);
        content.addView(view, vp);
        Button refresh = secondaryButton("Refresh log", () -> showDiagnostics());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(42));
        rp.topMargin = dp(10);
        content.addView(refresh, rp);
        showFullScreenViewer("TROUBLESHOOTING", "Diagnostics", "Recent local logs, with no automatic upload.", content, null, null);
    }

private void clearDay() {
        new AlertDialog.Builder(this)
                .setTitle("Clear selected day's plan?")
                .setMessage("All blocks for the selected day will be removed from this device.")
                .setPositiveButton("Clear", (d, w) -> {
                    blocks.clear();
                    save();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

private Date parseDateKey(String key) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key);
        } catch (Exception e) {
            return null;
        }
    }

private String keyForDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

private boolean isBeforeToday(String key) {
        return key.compareTo(todayKey()) < 0;
    }

private void changeSelectedDate(int delta) {
        save();
        Calendar c = Calendar.getInstance();
        Date d = parseDateKey(selectedDateKey);
        if (d == null) d = new Date();
        c.setTime(d);
        c.add(Calendar.DAY_OF_YEAR, delta);
        selectedDateKey = keyForDate(c.getTime());
        setPlanDayScheduleExpanded(false);
        loadSelectedDate();
        render();
    }

private void openDatePicker() {
        Date d = parseDateKey(selectedDateKey);
        Calendar c = Calendar.getInstance();
        if (d != null) c.setTime(d);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.YEAR, year);
            chosen.set(Calendar.MONTH, month);
            chosen.set(Calendar.DAY_OF_MONTH, day);
            save();
            selectedDateKey = keyForDate(chosen.getTime());
            setPlanDayScheduleExpanded(false);
            loadSelectedDate();
            render();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

private String blocksKey(String dayKey) {
        return "blocks_" + dayKey;
    }

private String modeKey(String dayKey) {
        return "mode_" + dayKey;
    }

private void loadSelectedDate() {
        blocks.clear();
        SharedPreferences p = getPrefs();
        mode = p.getString(modeKey(selectedDateKey), p.getString(MODE_KEY, "wfh"));
        String raw = p.getString(blocksKey(selectedDateKey), null);

        try {
            if (raw == null && selectedDateKey.equals(todayKey())) {
                raw = p.getString(KEY, null);
                if (raw != null) {
                    p.edit().putString(blocksKey(selectedDateKey), raw).putString(modeKey(selectedDateKey), mode).apply();
                }
            }

            if (raw != null) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    blocks.add(blockFromJson(o, "pending"));
                }
            } else if (!isBeforeToday(selectedDateKey)) {
                loadTemplateSilent();
                save();
            }
        } catch (Exception e) {
            blocks.clear();
        }
    }

}
