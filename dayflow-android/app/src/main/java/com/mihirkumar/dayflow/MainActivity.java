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
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
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
    private static final String OFFICE_IN = "officeIn";
    private static final String OFFICE_OUT = "officeOut";
    private static final String COMMUTE_OUT_START = "commuteOutStart";
    private static final String COMMUTE_OUT_END = "commuteOutEnd";
    private static final String OFFICE_LOG_KEY = "officeLog";
    private static final String DEBUG_LOG_KEY = "debugLog";
    private static final String DEBUG_TAG = "DayFlow";
    private static final String SYLLABUS_KEY = "syllabusTopics";
    private static final String SYLLABUS_TARGET_KEY = "syllabusTargetDate";
    private static final String SYLLABUS_START_KEY = "syllabusStartDate";
    private static final int REQUEST_EXPORT_BACKUP = 4201;
    private static final int REQUEST_IMPORT_BACKUP = 4202;
    private static final String[] SYLLABUS_SUBJECTS = {
            "General Studies", "Maths Optional", "CSAT", "Essay", "Interview"
    };

    private final ArrayList<Block> blocks = new ArrayList<>();
    private final Handler handler = new Handler();

    private LinearLayout root, timeline, officeCard;
    private TextView dateView, modeView, completionView, progressPercent;
    private TextView currentTitle, currentTime, currentCountdown, plannedView, doneView, studyActualView;
    private TextView officeStatus, officeTimes, commuteView;
    private Button officeAction, commuteAction, focusAction;
    private String mode = "wfh";
    private String selectedDateKey;

    private static class Block {
        long id;
        String start, end, title, priority, rigidity, status;
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
        SyllabusTopic(long id, String subject, String title, boolean done, long completedAt) {
            this.id = id;
            this.subject = subject;
            this.title = title;
            this.done = done;
            this.completedAt = completedAt;
        }
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
        window.setStatusBarColor(color("#0B0D12"));
        window.setNavigationBarColor(color("#0B0D12"));

        selectedDateKey = todayKey();
        load();
        buildUi();
        render();
        ensureScreenTimeWorker();
        if (ScreenTimeTracker.hasUsageAccess(this)) ScreenTimeTracker.refreshRecentDays(this, 7);

        handler.postDelayed(liveRefresh, Math.max(50L, 1000L - (System.currentTimeMillis() % 1000L)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureScreenTimeWorker();
        if (ScreenTimeTracker.refreshRecentDays(this, 7)) DayFlowWidgetProvider.refreshAll(this);
        render();
        DayFlowWidgetProvider.refreshAll(this);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(liveRefresh);
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
        return Color.parseColor(hex);
    }

    private GradientDrawable bg(String hex, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(hex));
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView text(String value, float sp, int c, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(c);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private Button actionButton(String value, int textColor, String background) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setBackground(bg(background, 12));
        return b;
    }

    private Button chip(String label) {
        Button b = actionButton(label, color("#AEB7C9"), "#151A27");
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

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(color("#0B0D12"));

        root = vertical();
        root.setPadding(dp(20), dp(12), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = Math.max(dp(12), insets.getSystemWindowInsetTop() + dp(6));
            int bottom = Math.max(dp(34), insets.getSystemWindowInsetBottom() + dp(20));
            v.setPadding(dp(20), top, dp(20), bottom);
            return insets;
        });

        buildHeader();
        buildModeRow();
        buildCurrentCard();
        buildTodaySummary();

        officeCard = vertical();
        officeCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        officeCard.setBackground(bg("#151C23", 18));
        LinearLayout.LayoutParams ocp = new LinearLayout.LayoutParams(-1, -2);
        ocp.bottomMargin = dp(16);
        root.addView(officeCard, ocp);
        buildOfficeCardContents();

        buildScheduleHeader();
        timeline = vertical();
        root.addView(timeline, new LinearLayout.LayoutParams(-1, -2));
        root.addView(spacer(8));

        TextView footer = text("Saved on this device • safe to close the app", 12, color("#697386"), false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(34)));
    }

    private void buildHeader() {
        LinearLayout top = horizontal();
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.bottomMargin = dp(8);
        root.addView(top, tp);

        TextView appName = text("DayFlow", 25, Color.WHITE, true);
        top.addView(appName, new LinearLayout.LayoutParams(0, -2, 1));

        Button add = actionButton("＋", Color.WHITE, "#191E2B");
        add.setTextSize(22);
        add.setContentDescription("Add block");
        add.setOnClickListener(v -> openAddDialog());
        top.addView(add, new LinearLayout.LayoutParams(dp(50), dp(48)));

        LinearLayout nav = horizontal();
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(42));
        np.bottomMargin = dp(14);
        root.addView(nav, np);

        Button prev = actionButton("‹", Color.WHITE, "#151A27");
        prev.setTextSize(24);
        prev.setContentDescription("Previous day");
        prev.setOnClickListener(v -> changeSelectedDate(-1));
        nav.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(38)));

        dateView = text("", 14, Color.WHITE, true);
        dateView.setGravity(Gravity.CENTER);
        dateView.setContentDescription("Selected day. Tap to choose a date.");
        dateView.setOnClickListener(v -> openDatePicker());
        nav.addView(dateView, new LinearLayout.LayoutParams(0, -2, 1));

        Button next = actionButton("›", Color.WHITE, "#151A27");
        next.setTextSize(24);
        next.setContentDescription("Next day");
        next.setOnClickListener(v -> changeSelectedDate(1));
        nav.addView(next, new LinearLayout.LayoutParams(dp(42), dp(38)));
    }

    private void buildModeRow() {
        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(44));
        rp.bottomMargin = dp(14);
        root.addView(row, rp);

        modeView = text("", 12, color("#8E98AA"), false);
        row.addView(modeView, new LinearLayout.LayoutParams(0, -2, 1));

        String[] modes = {"WFH", "Office", "Weekend"};
        for (String item : modes) {
            Button b = chip(item);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(item.equals("Weekend") ? 78 : 68), dp(38));
            lp.leftMargin = dp(6);
            row.addView(b, lp);
            b.setTag(item.toLowerCase(Locale.US));
            b.setOnClickListener(v -> chooseMode((String) v.getTag()));
        }
    }

    private void buildCurrentCard() {
        LinearLayout card = vertical();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(bg("#171A2A", 20));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(14);
        root.addView(card, cp);

        TextView label = text("RIGHT NOW", 11, color("#A59DFF"), true);
        card.addView(label);

        currentTitle = text("Loading…", 24, Color.WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(5);
        card.addView(currentTitle, tp);

        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(8);
        card.addView(row, rp);

        currentTime = text("—", 13, color("#A8B0C2"), false);
        currentCountdown = text("—", 13, color("#FFFFFF"), true);
        row.addView(currentTime, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(currentCountdown);

        Button markDone = actionButton("Mark done", Color.WHITE, "#8B7CFF");
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(-1, dp(46));
        doneLp.topMargin = dp(16);
        card.addView(markDone, doneLp);
        markDone.setTag("currentDone");
        markDone.setOnClickListener(v -> completeCurrent());

        focusAction = actionButton("Start focus", color("#D9E2F3"), "#263243");
        LinearLayout.LayoutParams focusLp = new LinearLayout.LayoutParams(-1, dp(40));
        focusLp.topMargin = dp(8);
        card.addView(focusAction, focusLp);
        focusAction.setTag("focusAction");
        focusAction.setOnClickListener(v -> toggleFocusCurrent());

        Button recover = actionButton("Catch up", color("#DAD7FF"), "#2C2A4A");
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(-1, dp(42));
        recLp.topMargin = dp(8);
        card.addView(recover, recLp);
        recover.setTag("recoveryButton");
        recover.setOnClickListener(v -> recoveryMode());
    }

    private void buildTodaySummary() {
        LinearLayout summary = horizontal();
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(90));
        sp.bottomMargin = dp(14);
        root.addView(summary, sp);

        LinearLayout left = vertical();
        completionView = text("0 / 0 complete", 14, Color.WHITE, true);
        progressPercent = text("0% of the plan", 12, color("#7F899D"), false);
        left.addView(completionView);
        left.addView(progressPercent);
        studyActualView = text("Study actual 0m / 0m planned", 11, color("#8E98AA"), false);
        left.addView(studyActualView);
        summary.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout metricBox = horizontal();
        metricBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        metricBox.setBackground(bg("#131722", 13));
        plannedView = text("0h", 15, Color.WHITE, true);
        doneView = text("0h done", 11, color("#8E98AA"), false);
        metricBox.addView(plannedView);
        metricBox.addView(spacerHorizontal(8));
        metricBox.addView(doneView);
        summary.addView(metricBox, new LinearLayout.LayoutParams(-2, dp(48)));
    }

    private void buildOfficeCardContents() {
        LinearLayout titleRow = horizontal();
        TextView title = text("OFFICE DAY", 12, color("#9FAFC4"), true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button history = actionButton("History", color("#C7D3E6"), "#202936");
        history.setTextSize(11);
        titleRow.addView(history, new LinearLayout.LayoutParams(dp(76), dp(34)));
        history.setOnClickListener(v -> showAttendanceHistory());
        officeCard.addView(titleRow);

        officeStatus = text("Ready", 20, Color.WHITE, true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8);
        officeCard.addView(officeStatus, sp);

        officeTimes = text("No office time logged today", 11, color("#8E98AA"), false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(5);
        officeCard.addView(officeTimes, tp);

        commuteView = text("Today • Office 0m  •  Commute 0m", 11, color("#8E98AA"), false);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(7);
        officeCard.addView(commuteView, cp);

        officeAction = actionButton("CHECK IN", Color.WHITE, "#8B7CFF");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(48));
        ap.topMargin = dp(14);
        officeCard.addView(officeAction, ap);
        officeAction.setOnClickListener(v -> {
            if (selectedDateKey.equals(todayKey())) toggleOfficeAttendance();
            else editAttendanceDay(selectedDateKey);
        });

        commuteAction = actionButton("Start commute", color("#C7D3E6"), "#202936");
        LinearLayout.LayoutParams cmp = new LinearLayout.LayoutParams(-1, dp(38));
        cmp.topMargin = dp(7);
        officeCard.addView(commuteAction, cmp);
        commuteAction.setOnClickListener(v -> {
            if (selectedDateKey.equals(todayKey())) toggleCommute();
            else showAttendanceDayDetail(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(parseDateKey(selectedDateKey)), selectedDateKey, getOfficeDay(selectedDateKey, false));
        });

        TextView hint = text("Tap CHECK IN when you enter. Tap CHECK OUT when you leave.", 10, color("#627087"), false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(8);
        officeCard.addView(hint, hp);
    }

    private void buildScheduleHeader() {
        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(10);
        root.addView(row, rp);

        TextView label = text("TODAY", 13, color("#8E98AA"), true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView modeLabel = text("", 11, color("#697386"), false);
        modeLabel.setTag("modeHeader");
        row.addView(modeLabel);

        Button more = actionButton("•••", color("#A8B0C2"), "#151A27");
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

        String modeLabel = mode.equals("office") ? "Office" : mode.equals("weekend") ? "Weekend" : "WFH";
        modeView.setText("TODAY • " + modeLabel);
        View headerMode = root.findViewWithTag("modeHeader");
        if (headerMode instanceof TextView) ((TextView) headerMode).setText(modeLabel.toUpperCase(Locale.US));

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
            TextView empty = text("Your day is empty.\nTap ＋ to add your first block.", 15, color("#8E98AA"), false);
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
                    TextView gapText = text("＋  " + fmtShort(mins(b.start) - lastEnd) + " free  •  Add", 11, color("#7F899D"), false);
                    gapText.setGravity(Gravity.CENTER);
                    gap.addView(gapText, new LinearLayout.LayoutParams(-1, dp(32)));
                    gap.setOnClickListener(v -> openAddDialogAt(gapStart));
                    timeline.addView(gap);
                }
                addBlockView(b, current != null && current.id == b.id, nowMins);
                lastEnd = Math.max(lastEnd, mins(b.end));
            }
        }
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
        timeCol.addView(text(time12(b.start), 12, color("#8E98AA"), true));
        timeCol.addView(text(time12(b.end), 10, color("#606A7B"), false));
        row.addView(timeCol, new LinearLayout.LayoutParams(dp(63), -2));

        LinearLayout card = horizontal();
        card.setPadding(dp(13), dp(11), dp(8), dp(11));

        String surface, border;
        if ("done".equals(b.status)) {
            surface = "#111A16"; border = "#2F7B58";
        } else if ("skipped".equals(b.status)) {
            surface = "#171214"; border = "#5C3038";
        } else if (current) {
            surface = "#26233E"; border = "#8B7CFF";
        } else {
            surface = "#151A27"; border = "#252C3B";
        }

        GradientDrawable cardBg = bg(surface, 16);
        cardBg.setStroke(dp(current ? 2 : 1), color(border));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1);
        cardLp.leftMargin = dp(9);
        row.addView(card, cardLp);

        LinearLayout details = vertical();
        TextView title = text(b.title, 15, Color.WHITE, true);
        TextView meta = text(blockBehaviorLabel(b) + " • " + fmt(mins(b.end) - mins(b.start)), 11, color("#8E98AA"), false);
        details.addView(title);
        details.addView(meta);

        if (current) {
            TextView state = text("IN PROGRESS", 10, color("#A59DFF"), true);
            state.setPadding(0, dp(6), 0, 0);
            details.addView(state);
        }

        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        if ("pending".equals(b.status)) {
            Button done = actionButton("✓", Color.WHITE, current ? "#8B7CFF" : "#202638");
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(44), dp(44));
            dlp.leftMargin = dp(8);
            card.addView(done, dlp);
            done.setOnClickListener(v -> {
                stopFocusForBlock(b, System.currentTimeMillis());
                b.status = "done";
                save();
            });
        } else if ("done".equals(b.status)) {
            TextView check = text("✓", 20, color("#47D18C"), true);
            check.setGravity(Gravity.CENTER);
            card.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));
        } else {
            Button undo = actionButton("↩", color("#FFB8C1"), "#2A2024");
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
        return b;
    }

    private void showBlockOptions(Block b) {
        ArrayList<String> actions = new ArrayList<>();
        actions.add("Edit block");
        if ("done".equals(b.status)) actions.add("Undo completion");
        else if ("skipped".equals(b.status)) actions.add("Restore block");
        else {
            actions.add("Mark done");
            actions.add("Skip block");
        }
        actions.add("Delete block");

        new AlertDialog.Builder(this)
                .setTitle(b.title)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String a = actions.get(which);
                    if ("Edit block".equals(a)) editBlockDialog(b);
                    else if ("Delete block".equals(a)) {
                        stopFocusForBlock(b, System.currentTimeMillis());
                        blocks.remove(b);
                        save();
                    } else if ("Undo completion".equals(a) || "Restore block".equals(a)) {
                        b.status = "pending";
                        save();
                    } else if ("Mark done".equals(a)) {
                        stopFocusForBlock(b, System.currentTimeMillis());
                        b.status = "done";
                        save();
                    } else if ("Skip block".equals(a)) {
                        stopFocusForBlock(b, System.currentTimeMillis());
                        b.status = "skipped";
                        save();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
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
        title.setTextColor(Color.WHITE);
        title.setHintTextColor(color("#697386"));
        title.setSingleLine(true);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView startLabel = text("START", 10, color("#6F7C91"), true);
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
                    ((Button) starts.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    ((Button) starts.getChildAt(j)).setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }

        if (suggestedStart >= 0 && starts.getChildCount() >= 3) {
            for (int j = 0; j < starts.getChildCount(); j++) {
                ((Button) starts.getChildAt(j)).setTextColor(j == 2 ? Color.WHITE : color("#AEB7C9"));
                ((Button) starts.getChildAt(j)).setBackground(bg(j == 2 ? "#413B73" : "#151A27", 12));
            }
        }

        TextView durationLabel = text("DURATION", 10, color("#6F7C91"), true);
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
                    ((Button) durations.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    ((Button) durations.getChildAt(j)).setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }

        Button custom = chip("Custom");
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(dp(72), dp(38));
        customLp.leftMargin = dp(6);
        durations.addView(custom, customLp);
        custom.setOnClickListener(v -> durationChoice[0] = -1);

        TextView rigidityLabel = text("RIGIDITY", 10, color("#6F7C91"), true);
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
            b.setTextColor(i == 1 ? Color.WHITE : color("#AEB7C9"));
            b.setBackground(bg(i == 1 ? "#413B73" : "#151A27", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(6);
            rigidities.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedRigidity[0] = rigidityValues[idx];
                selectedBehavior[0] = selectedRigidity[0] + ":" + selectedPriority[0];
                for (int j = 0; j < rigidities.getChildCount(); j++) {
                    ((Button) rigidities.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    ((Button) rigidities.getChildAt(j)).setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }

        TextView priorityTitle = text("PRIORITY", 10, color("#6F7C91"), true);
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
            b.setTextColor(i == 2 ? Color.WHITE : color("#AEB7C9"));
            b.setBackground(bg(i == 2 ? "#413B73" : "#151A27", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(4);
            priorities.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedPriority[0] = priorityValues[idx];
                selectedBehavior[0] = selectedRigidity[0] + ":" + selectedPriority[0];
                for (int j = 0; j < priorities.getChildCount(); j++) {
                    ((Button) priorities.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    ((Button) priorities.getChildAt(j)).setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }

        TextView repeatLabel = text("REPEAT", 10, color("#6F7C91"), true);
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
                b.setBackground(bg("#413B73", 12));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(6);
            repeats.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedRepeat[0] = repeatValues[idx];
                for (int j = 0; j < repeats.getChildCount(); j++) {
                    ((Button) repeats.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    ((Button) repeats.getChildAt(j)).setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }

        AlertDialog addDialog = new AlertDialog.Builder(this)
                .setTitle("Add to your day")
                .setView(form)
                .setPositiveButton("Add block", null)
                .setNegativeButton("Cancel", null)
                .create();

        addDialog.setOnShowListener(dialog -> {
                    AlertDialog d = (AlertDialog) dialog;
                    d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        String name = title.getText().toString().trim();
                        if (name.isEmpty()) {
                            title.setError("Give this block a name");
                            title.requestFocus();
                            return;
                        }
                        int start = startChoice[0] == 0 ? nextFreeStart() :
                                startChoice[0] == 1 ? currentMinutes() : -1;
                        if (startChoice[0] == 2) {
                            d.dismiss();
                            int defaultStart = suggestedStart >= 0 ? suggestedStart : currentMinutes();
                            TimePickerDialog picker = new TimePickerDialog(this, (tv, h, m) -> {
                                finishAddBlockRecurring(name, h * 60 + m, durationChoice[0], selectedBehavior[0], selectedRepeat[0]);
                            }, defaultStart / 60, defaultStart % 60, true);
                            picker.setTitle("Start time");
                            picker.show();
                            return;
                        }
                        if (durationChoice[0] == -1) {
                            d.dismiss();
                            TimePickerDialog picker = new TimePickerDialog(this, (tv, h, m) -> {
                                int end = h * 60 + m;
                                addCustomTimedBlockRecurring(name, start, end, selectedBehavior[0], selectedRepeat[0]);
                            }, Math.min(23, start / 60), start % 60, true);
                            picker.setTitle("End time");
                            picker.show();
                            return;
                        }
                        d.dismiss();
                        finishAddBlockRecurring(name, start, durationChoice[0], selectedBehavior[0], selectedRepeat[0]);
                    });
                    title.requestFocus();
                    if (d.getWindow() != null) {
                        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    }
                });
        addDialog.show();
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
        title.setTextColor(Color.WHITE);
        title.setHintTextColor(color("#697386"));
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

        TextView rigidityLabel = text("RIGIDITY", 10, color("#6F7C91"), true);
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
            button.setTextColor(selected ? Color.WHITE : color("#AEB7C9"));
            button.setBackground(bg(selected ? "#413B73" : "#151A27", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(6);
            rigidities.addView(button, lp);
            button.setOnClickListener(v -> {
                selectedRigidity[0] = rigidityValues[idx];
                for (int j = 0; j < rigidities.getChildCount(); j++) {
                    Button child = (Button) rigidities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    child.setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }

        TextView priorityTitle = text("PRIORITY", 10, color("#6F7C91"), true);
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
            button.setTextColor(selected ? Color.WHITE : color("#AEB7C9"));
            button.setBackground(bg(selected ? "#413B73" : "#151A27", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(4);
            priorities.addView(button, lp);
            button.setOnClickListener(v -> {
                selectedPriority[0] = priorityValues[idx];
                for (int j = 0; j < priorities.getChildCount(); j++) {
                    Button child = (Button) priorities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    child.setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }
        new AlertDialog.Builder(this).setTitle("Edit block").setView(form)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Give this block a name.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (end[0] <= start[0]) {
                        Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    stopFocusForBlock(b, System.currentTimeMillis());
                    b.title = name;
                    b.start = toTime(start[0]);
                    b.end = toTime(end[0]);
                    b.rigidity = selectedRigidity[0];
                    b.priority = selectedPriority[0];
                    save();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        int remainingMovable = 0;
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
                else remainingMovable++; // fixed future commitments are anchors, not moved work
            } else {
                remainingMovable++;
                if (mins(b.end) <= nowMins) overdueMovable++;
            }
        }

        String message = "Overdue movable blocks: " + overdueMovable
                + "\nFixed commitments missed/past: " + overdueFixed
                + "\n\nRecovery rules: keep the active block and Fixed anchors unchanged; reschedule overdue work; Critical first, Important next, Normal next, Optional last. Rigidity is separate from priority. Preserve durations; never overlap fixed anchors; report work that will not fit.";
        final int selectedActiveIndex = activeIndex;
        new AlertDialog.Builder(this)
                .setTitle("Build a recovery plan?")
                .setMessage(message)
                .setPositiveButton("Rebuild remaining day", (d, w) -> reflowRemainingDay(selectedActiveIndex))
                .setNegativeButton("Cancel", null)
                .show();
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
                // Include both future work and overdue work that was left pending.
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
        int couldNotFit = 0;

        for (Block block : movable) {
            int duration = Math.max(1, mins(block.end) - mins(block.start));
            while (anchorIndex < anchors.size() && mins(anchors.get(anchorIndex).start) < cursor) {
                cursor = Math.max(cursor, mins(anchors.get(anchorIndex).end));
                anchorIndex++;
            }

            if (anchorIndex < anchors.size() && cursor + duration > mins(anchors.get(anchorIndex).start)) {
                // The task cannot fit before this fixed anchor. Try after that commitment.
                cursor = Math.max(cursor, mins(anchors.get(anchorIndex).end));
                anchorIndex++;
                while (anchorIndex < anchors.size() && mins(anchors.get(anchorIndex).start) < cursor) {
                    cursor = Math.max(cursor, mins(anchors.get(anchorIndex).end));
                    anchorIndex++;
                }
            }

            if (cursor + duration > 1440) {
                couldNotFit++;
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
            }
        }

        save();
        String result = moved + " blocks reflowed; " + overdueRecovered + " overdue blocks recovered.";
        if (couldNotFit > 0) result += " " + couldNotFit + " block(s) did not fit and were left unchanged.";
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        debugLog("Catch up result: moved=" + moved + " recoveredOverdue=" + overdueRecovered
                + " unplaced=" + couldNotFit + " active=" + (active == null ? "none" : active.title));
    }

        private void chooseMode(String key) {
        if (mode.equals(key)) return;
        String label = key.equals("office") ? "Office" : key.equals("weekend") ? "Weekend" : "WFH";
        new AlertDialog.Builder(this)
                .setTitle("Switch to " + label + "?")
                .setMessage("This replaces today's current timeline with the " + label + " template.")
                .setPositiveButton("Switch", (d, w) -> {
                    mode = key;
                    getPrefs().edit().putString(MODE_KEY, mode).apply();
                    loadTemplate(key);
                })
                .setNegativeButton("Keep today", null)
                .show();
    }

    private void openManageDialog() {
        String[] items = {
                "Load WFH template",
                "Load Office template",
                "Load Weekend template",
                "Edit WFH template",
                "Edit Office template",
                "Edit Weekend template",
                "Screen time & history",
                "Syllabus tracker",
                "Enable precise live widget updates",
                "Export DayFlow backup",
                "Import DayFlow backup",
                "View diagnostics",
                "Clear selected day's plan"
        };
        new AlertDialog.Builder(this)
                .setTitle("Manage day")
                .setItems(items, (d, which) -> {
                    if (which == 0) chooseTemplateDirect("wfh");
                    else if (which == 1) chooseTemplateDirect("office");
                    else if (which == 2) chooseTemplateDirect("weekend");
                    else if (which == 3) openTemplateEditor("wfh");
                    else if (which == 4) openTemplateEditor("office");
                    else if (which == 5) openTemplateEditor("weekend");
                    else if (which == 6) showScreenTimeTracker();
                    else if (which == 7) showSyllabusTracker();
                    else if (which == 8) enablePreciseLiveWidgetUpdates();
                    else if (which == 9) requestExportBackup();
                    else if (which == 10) requestImportBackup();
                    else if (which == 11) showDiagnostics();
                    else clearDay();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private ArrayList<SyllabusTopic> readSyllabusTopics() {
        ArrayList<SyllabusTopic> topics = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(getPrefs().getString(SYLLABUS_KEY, "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                topics.add(new SyllabusTopic(
                        item.optLong("id", i + 1L),
                        item.optString("subject", "General Studies"),
                        item.optString("title", ""),
                        item.optBoolean("done", false),
                        item.optLong("completedAt", 0L)));
            }
        } catch (Exception e) {
            debugLog("Syllabus read failed: " + e.getMessage());
        }
        return topics;
    }

    private String syllabusProgressLabel() {
        ArrayList<SyllabusTopic> topics = readSyllabusTopics();
        if (topics.isEmpty()) return "Syllabus not set";
        int done = 0;
        for (SyllabusTopic topic : topics) if (topic.done) done++;
        return "Syllabus " + Math.round(done * 100f / topics.size()) + "%";
    }

    private String syllabusSummary(ArrayList<SyllabusTopic> topics) {
        String target = getPrefs().getString(SYLLABUS_TARGET_KEY, "2028-01-01");
        Date targetDate = parseDateKey(target);
        Calendar targetCalendar = Calendar.getInstance();
        if (targetDate != null) targetCalendar.setTime(targetDate);
        targetCalendar.set(Calendar.HOUR_OF_DAY, 0);
        targetCalendar.set(Calendar.MINUTE, 0);
        targetCalendar.set(Calendar.SECOND, 0);
        targetCalendar.set(Calendar.MILLISECOND, 0);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long daysLeft = Math.max(0L, (targetCalendar.getTimeInMillis() - today.getTimeInMillis()) / 86400000L);
        int done = 0, recentlyDone = 0;
        long weekAgo = System.currentTimeMillis() - 7L * 86400000L;
        LinkedHashMap<String, int[]> counts = new LinkedHashMap<>();
        for (SyllabusTopic topic : topics) {
            int[] values = counts.get(topic.subject);
            if (values == null) {
                values = new int[2];
                counts.put(topic.subject, values);
            }
            values[1]++;
            if (topic.done) {
                values[0]++;
                done++;
                if (topic.completedAt >= weekAgo) recentlyDone++;
            }
        }
        int remaining = Math.max(0, topics.size() - done);
        StringBuilder summary = new StringBuilder();
        summary.append("Overall: ").append(done).append("/").append(topics.size())
                .append(" complete (").append(topics.isEmpty() ? 0 : Math.round(done * 100f / topics.size())).append("%)")
                .append("\nTarget: ").append(target).append(" • ").append(daysLeft).append(" days left");
        if (remaining == 0 && !topics.isEmpty()) {
            summary.append("\nAll topics in your current list are complete.");
        } else if (!topics.isEmpty() && daysLeft > 0) {
            summary.append("\nRequired pace: ").append(String.format(Locale.US, "%.2f", remaining / (double) daysLeft))
                    .append(" topics/day");
            if (recentlyDone > 0) {
                double perDay = recentlyDone / 7.0;
                long projectedDays = (long) Math.ceil(remaining / perDay);
                Calendar forecast = Calendar.getInstance();
                forecast.add(Calendar.DAY_OF_YEAR, (int) Math.min(Integer.MAX_VALUE, projectedDays));
                summary.append("\n7-day pace: ").append(recentlyDone).append(" topics/week")
                        .append(" • projected finish ").append(new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(forecast.getTime()));
            } else {
                summary.append("\nFinish forecast: not enough recent completion history yet.");
            }
        }
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int[] values = entry.getValue();
            summary.append("\n").append(entry.getKey()).append(": ").append(values[0]).append("/")
                    .append(values[1]).append(" (").append(Math.round(values[0] * 100f / values[1])).append("%)");
        }
        if (topics.isEmpty()) summary.append("\nAdd or import topics. Percentages cover your list only, not syllabus items you have not entered.");
        return summary.toString();
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
            getPrefs().edit().putString(SYLLABUS_TARGET_KEY, key).apply();
            debugLog("Syllabus target date set: " + key);
            showSyllabusTracker();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        picker.setTitle("Syllabus target date");
        picker.show();
    }

    private void importSyllabusTopics() {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(4), dp(18), dp(4));
        Spinner subject = new Spinner(this);
        subject.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SYLLABUS_SUBJECTS));
        form.addView(subject, new LinearLayout.LayoutParams(-1, dp(44)));
        EditText lines = new EditText(this);
        lines.setGravity(Gravity.TOP);
        lines.setMinLines(6);
        lines.setHint("Paste topics, one per line");
        lines.setTextColor(Color.WHITE);
        lines.setHintTextColor(color("#697386"));
        form.addView(lines, new LinearLayout.LayoutParams(-1, dp(160)));
        new AlertDialog.Builder(this).setTitle("Import topic list")
                .setMessage("Each non-empty line becomes one topic under the selected subject.")
                .setView(form).setPositiveButton("Import", (d, w) -> {
                    String selectedSubject = subject.getSelectedItem().toString();
                    ArrayList<SyllabusTopic> all = readSyllabusTopics();
                    HashSet<String> existing = new HashSet<>();
                    for (SyllabusTopic topic : all) existing.add((topic.subject + "|" + topic.title).toLowerCase(Locale.US));
                    int added = 0;
                    for (String raw : lines.getText().toString().split("\\r?\\n")) {
                        String name = raw.trim();
                        if (name.isEmpty() || !existing.add((selectedSubject + "|" + name).toLowerCase(Locale.US))) continue;
                        all.add(new SyllabusTopic(System.currentTimeMillis() + added, selectedSubject, name, false, 0L));
                        added++;
                    }
                    if (added > 0 && !getPrefs().contains(SYLLABUS_START_KEY)) {
                        getPrefs().edit().putString(SYLLABUS_START_KEY, todayKey()).apply();
                    }
                    persistSyllabusTopics(all);
                    Toast.makeText(this, added + " topics imported.", Toast.LENGTH_SHORT).show();
                    showSyllabusTracker();
                }).setNegativeButton("Cancel", null).show();
    }


    private void loadStarterSyllabus() {
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
            {"Maths Optional", "Maths Optional Paper I: Linear Algebra"},
            {"Maths Optional", "Maths Optional Paper I: Calculus"},
            {"Maths Optional", "Maths Optional Paper I: Analytic Geometry"},
            {"Maths Optional", "Maths Optional Paper I: Ordinary Differential Equations"},
            {"Maths Optional", "Maths Optional Paper I: Dynamics and Statics"},
            {"Maths Optional", "Maths Optional Paper I: Vector Analysis"},
            {"Maths Optional", "Maths Optional Paper II: Algebra"},
            {"Maths Optional", "Maths Optional Paper II: Real Analysis"},
            {"Maths Optional", "Maths Optional Paper II: Complex Analysis"},
            {"Maths Optional", "Maths Optional Paper II: Linear Programming"},
            {"Maths Optional", "Maths Optional Paper II: Partial Differential Equations"},
            {"Maths Optional", "Maths Optional Paper II: Numerical Analysis"},
            {"Maths Optional", "Maths Optional Paper II: Computer Programming"},
            {"Maths Optional", "Maths Optional Paper II: Mechanics and Fluid Dynamics"},
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
            {"Interview", "Interview: Mock interview and feedback review"}
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
            all.add(new SyllabusTopic(System.currentTimeMillis() + added, row[0], row[1], false, 0L));
            added++;
        }
        if (added > 0 && !getPrefs().contains(SYLLABUS_START_KEY)) {
            getPrefs().edit().putString(SYLLABUS_START_KEY, todayKey()).apply();
        }
        persistSyllabusTopics(all);
        Toast.makeText(this, added + " syllabus items added.", Toast.LENGTH_LONG).show();
        debugLog("UPSC syllabus starter loaded: newItems=" + added + " source=UPSC CSE 2026 notice");
        showSyllabusTracker();
    }

    private void showSyllabusTracker() {
        ArrayList<SyllabusTopic> topics = readSyllabusTopics();
        String[] rows = new String[topics.size() + 4];
        rows[0] = "＋ Add one topic";
        rows[1] = "Import topic list (one topic per line)";
        rows[2] = "Load UPSC CSE syllabus starter (2026 notice)";
        rows[3] = "Set target date";
        for (int i = 0; i < topics.size(); i++) {
            SyllabusTopic topic = topics.get(i);
            rows[i + 4] = (topic.done ? "✓ " : "○ ") + topic.subject + " • " + topic.title;
        }
        new AlertDialog.Builder(this)
                .setTitle("Syllabus tracker")
                .setMessage(syllabusSummary(topics))
                .setItems(rows, (dialog, which) -> {
                    if (which == 0) editSyllabusTopic(null);
                    else if (which == 1) importSyllabusTopics();
                    else if (which == 2) loadStarterSyllabus();
                    else if (which == 3) chooseSyllabusTargetDate();
                    else showSyllabusTopicActions(topics.get(which - 4));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSyllabusTopicActions(SyllabusTopic topic) {
        String toggle = topic.done ? "Mark pending" : "Mark complete";
        String[] actions = {toggle, "Edit topic", "Delete topic"};
        new AlertDialog.Builder(this).setTitle(topic.title).setItems(actions, (dialog, which) -> {
            if (which == 0) {
                topic.done = !topic.done;
                topic.completedAt = topic.done ? System.currentTimeMillis() : 0L;
                saveSyllabusTopics();
            } else if (which == 1) {
                editSyllabusTopic(topic);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Delete topic?")
                        .setMessage(topic.title + " will be removed from your syllabus tracker.")
                        .setPositiveButton("Delete", (d, w) -> {
                            ArrayList<SyllabusTopic> all = readSyllabusTopics();
                            for (int i = all.size() - 1; i >= 0; i--) {
                                if (all.get(i).id == topic.id) all.remove(i);
                            }
                            persistSyllabusTopics(all);
                        })
                        .setNegativeButton("Keep", null)
                        .show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void editSyllabusTopic(SyllabusTopic existing) {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(4));
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Exact topic / syllabus item");
        title.setTextColor(Color.WHITE);
        title.setHintTextColor(color("#697386"));
        if (existing != null) title.setText(existing.title);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView label = text("SUBJECT", 10, color("#8E98AA"), true);
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
        }
        form.addView(subject, new LinearLayout.LayoutParams(-1, dp(48)));

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add syllabus topic" : "Edit syllabus topic")
                .setView(form)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Enter a topic name.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ArrayList<SyllabusTopic> all = readSyllabusTopics();
                    if (existing == null) {
                        if (!getPrefs().contains(SYLLABUS_START_KEY)) getPrefs().edit().putString(SYLLABUS_START_KEY, todayKey()).apply();
                        all.add(new SyllabusTopic(System.currentTimeMillis(), subject.getSelectedItem().toString(), name, false, 0L));
                    } else {
                        for (SyllabusTopic item : all) {
                            if (item.id == existing.id) {
                                item.subject = subject.getSelectedItem().toString();
                                item.title = name;
                            }
                        }
                    }
                    persistSyllabusTopics(all);
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                data.put(item);
            }
            getPrefs().edit().putString(SYLLABUS_KEY, data.toString()).apply();
            debugLog("Syllabus saved: topics=" + topics.size());
            DayFlowWidgetProvider.refreshAll(this);
        } catch (Exception e) {
            debugLog("Syllabus save failed: " + e.getMessage());
            Toast.makeText(this, "Could not save syllabus changes.", Toast.LENGTH_SHORT).show();
        }
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

    private void chooseTemplateDirect(String key) {
        String label = key.equals("office") ? "Office" : key.equals("weekend") ? "Weekend" : "WFH";
        new AlertDialog.Builder(this)
                .setTitle("Load " + label + " template?")
                .setMessage("Your current timeline will be replaced with your saved " + label + " template.")
                .setPositiveButton("Load", (d, w) -> {
                    mode = key;
                    getPrefs().edit().putString(modeKey(selectedDateKey), mode).putString(MODE_KEY, mode).apply();
                    loadTemplate(key);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String templateKey(String key) {
        return "template_" + key;
    }

    private String templateLabel(String key) {
        return "office".equals(key) ? "Office" : "weekend".equals(key) ? "Weekend" : "WFH";
    }

    private ArrayList<Block> getTemplateBlocks(String key) {
        ArrayList<Block> result = new ArrayList<>();
        String raw = getPrefs().getString(templateKey(key), null);
        try {
            if (raw == null) {
                String[][] defaults = defaultTemplate(key);
                for (int i = 0; i < defaults.length; i++) {
                    result.add(new Block(System.currentTimeMillis() + i, defaults[i][0], defaults[i][1],
                            defaults[i][2], defaults[i][3], "pending"));
                }
                saveTemplate(key, result);
                return result;
            }
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                result.add(blockFromJson(o, "pending"));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void saveTemplate(String key, ArrayList<Block> template) {
        try {
            JSONArray arr = new JSONArray();
            for (Block b : template) {
                JSONObject o = new JSONObject();
                o.put("id", b.id);
                o.put("start", b.start);
                o.put("end", b.end);
                o.put("title", b.title);
                o.put("priority", b.priority);
                o.put("rigidity", b.rigidity);
                arr.put(o);
            }
            getPrefs().edit().putString(templateKey(key), arr.toString()).apply();
            debugLog("Template saved: " + key + " blocks=" + template.size());
            DayFlowWidgetProvider.refreshAll(this);
        } catch (Exception ignored) {
        }
    }

    private String[][] defaultTemplate(String key) {
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

    private void openTemplateEditor(String key) {
        ArrayList<Block> template = getTemplateBlocks(key);
        LinearLayout list = vertical();
        list.setPadding(dp(16), dp(4), dp(16), 0);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -1));

        AlertDialog editor = new AlertDialog.Builder(this)
                .setTitle("Edit " + templateLabel(key) + " template")
                .setView(scroll)
                .setPositiveButton("Save template", null)
                .setNeutralButton("Save + apply", null)
                .setNegativeButton("Cancel", null)
                .create();

        editor.setOnShowListener(dialog -> {
            AlertDialog d = (AlertDialog) dialog;
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                saveTemplate(key, template);
                Toast.makeText(this, templateLabel(key) + " template saved.", Toast.LENGTH_SHORT).show();
                d.dismiss();
            });
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                saveTemplate(key, template);
                mode = key;
                blocks.clear();
                for (Block source : template) {
                    blocks.add(new Block(System.currentTimeMillis() + blocks.size(),
                            source.start, source.end, source.title, source.priority, source.rigidity, "pending"));
                }
                save();
                Toast.makeText(this, templateLabel(key) + " template saved and applied.", Toast.LENGTH_SHORT).show();
                d.dismiss();
            });
            renderTemplateEditorRows(list, template, editor);
        });
        editor.show();
    }

    private void renderTemplateEditorRows(LinearLayout list, ArrayList<Block> template, AlertDialog editor) {
        list.removeAllViews();
        TextView hint = text("Edit, add, or remove blocks. Existing days are unchanged unless you choose Save + apply.",
                11, color("#7F899D"), false);
        hint.setPadding(0, 0, 0, dp(10));
        list.addView(hint);

        Button add = actionButton("＋  Add template block", Color.WHITE, "#8B7CFF");
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, dp(44));
        addLp.bottomMargin = dp(10);
        list.addView(add, addLp);
        add.setOnClickListener(v -> editTemplateBlockDialog(template, -1, () ->
                renderTemplateEditorRows(list, template, editor)));

        Collections.sort(template, Comparator.comparingInt(b -> mins(b.start)));
        for (int i = 0; i < template.size(); i++) {
            final int index = i;
            Block b = template.get(i);
            LinearLayout row = horizontal();
            row.setPadding(dp(10), dp(8), dp(6), dp(8));
            row.setBackground(bg("#151A27", 14));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(7);
            list.addView(row, rowLp);

            LinearLayout details = vertical();
            details.addView(text(b.title, 14, Color.WHITE, true));
            details.addView(text(time12(b.start) + " – " + time12(b.end) + "  •  " + blockBehaviorLabel(b),
                    11, color("#8E98AA"), false));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

            Button del = actionButton("×", color("#FFB8C1"), "#2A2024");
            row.addView(del, new LinearLayout.LayoutParams(dp(42), dp(40)));
            row.setOnClickListener(v -> editTemplateBlockDialog(template, index, () ->
                    renderTemplateEditorRows(list, template, editor)));
            del.setOnClickListener(v -> {
                template.remove(index);
                renderTemplateEditorRows(list, template, editor);
            });
        }
    }

    private void editTemplateBlockDialog(ArrayList<Block> template, int index, Runnable onChanged) {
        boolean adding = index < 0;
        Block original;
        if (adding) {
            int start = 360;
            for (Block b : template) start = Math.max(start, mins(b.end));
            int end = Math.min(1439, start + 30);
            original = new Block(System.currentTimeMillis(), toTime(start), toTime(end),
                    "New block", "flexible", "pending");
        } else {
            original = template.get(index);
        }

        LinearLayout form = vertical();
        form.setPadding(dp(20), 0, dp(20), 0);
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Block name");
        title.setText(original.title);
        title.setTextColor(Color.WHITE);
        title.setHintTextColor(color("#697386"));
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

        TextView rigidityLabel = text("RIGIDITY", 10, color("#6F7C91"), true);
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
            b.setTextColor(selected ? Color.WHITE : color("#AEB7C9"));
            b.setBackground(bg(selected ? "#413B73" : "#151A27", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(6);
            rigidities.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedRigidity[0] = rigidityValues[idx];
                for (int j = 0; j < rigidities.getChildCount(); j++) {
                    Button child = (Button) rigidities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    child.setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }

        TextView priorityTitle = text("PRIORITY", 10, color("#6F7C91"), true);
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
            b.setTextColor(selected ? Color.WHITE : color("#AEB7C9"));
            b.setBackground(bg(selected ? "#413B73" : "#151A27", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
            if (i > 0) lp.leftMargin = dp(4);
            priorities.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedPriority[0] = priorityValues[idx];
                for (int j = 0; j < priorities.getChildCount(); j++) {
                    Button child = (Button) priorities.getChildAt(j);
                    child.setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    child.setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
                }
            });
        }

        new AlertDialog.Builder(this)
                .setTitle(adding ? "Add template block" : "Edit template block")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String name = title.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Give this block a name.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (end[0] <= start[0]) {
                        Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
                        return;
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
                    Collections.sort(template, Comparator.comparingInt(b -> mins(b.start)));
                    onChanged.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadTemplate(String key) {
        blocks.clear();
        for (Block source : getTemplateBlocks(key)) {
            blocks.add(new Block(System.currentTimeMillis() + blocks.size(),
                    source.start, source.end, source.title, source.priority, source.rigidity, "pending"));
        }
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
                officeAction.setBackground(bg("#8B7CFF", 12));
                officeAction.setEnabled(true);
            } else {
                officeStatus.setText(day == null ? "No office record" : "OFFICE RECORD");
                officeAction.setText("EDIT TIMES");
                officeAction.setBackground(bg("#334B63", 12));
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

    private void saveOfficeDay(JSONObject day) {
        try {
            if (day == null) return;
            String key = day.optString("date");
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
        } catch (Exception ignored) {
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

    private long commuteDuration(JSONObject day, long now) {
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

    private void toggleOfficeAttendance() {
        try {
            long now = System.currentTimeMillis();
            JSONObject day = getOfficeDay(todayKey(), true);
            JSONArray sessions = day.optJSONArray("sessions");
            if (sessions == null) {
                sessions = new JSONArray();
                day.put("sessions", sessions);
            }

            if (hasOpenOfficeSession(day)) {
                JSONObject last = sessions.optJSONObject(sessions.length() - 1);
                last.put("out", now);
                Toast.makeText(this, "Checked out at " + clock(now), Toast.LENGTH_SHORT).show();
            } else {
                JSONObject session = new JSONObject();
                session.put("in", now);
                session.put("out", 0);
                sessions.put(session);
                Toast.makeText(this, "Checked in at " + clock(now), Toast.LENGTH_SHORT).show();
            }
            saveOfficeDay(day);
            render();
        } catch (Exception ignored) {
        }
    }

    private void toggleCommute() {
        try {
            long now = System.currentTimeMillis();
            JSONObject day = getOfficeDay(todayKey(), true);

            long inStart = day.optLong("commuteInStart", 0);
            long inEnd = day.optLong("commuteInEnd", 0);
            long outStart = day.optLong("commuteOutStart", 0);
            long outEnd = day.optLong("commuteOutEnd", 0);

            if (inStart == 0) {
                day.put("commuteInStart", now);
                Toast.makeText(this, "Commute started.", Toast.LENGTH_SHORT).show();
            } else if (inEnd == 0) {
                day.put("commuteInEnd", now);
                Toast.makeText(this, "Office commute logged.", Toast.LENGTH_SHORT).show();
            } else if (outStart == 0) {
                day.put("commuteOutStart", now);
                Toast.makeText(this, "Home commute started.", Toast.LENGTH_SHORT).show();
            } else if (outEnd == 0) {
                day.put("commuteOutEnd", now);
                Toast.makeText(this, "Home commute logged.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Today's commute is already complete.", Toast.LENGTH_SHORT).show();
                return;
            }

            saveOfficeDay(day);
            render();
        } catch (Exception ignored) {
        }
    }

    private void showAttendanceHistory() {
        try {
            LinearLayout content = vertical();
            content.setPadding(dp(18), dp(4), dp(18), dp(4));

            TextView summary = text("", 14, Color.WHITE, true);
            content.addView(summary);

            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(false);
            LinearLayout days = vertical();
            days.setPadding(0, dp(10), 0, dp(12));
            scroll.addView(days);

            long totalWeek = 0;
            int weekDays = 0;
            Calendar cursor = Calendar.getInstance();
            int dow = cursor.get(Calendar.DAY_OF_WEEK);
            // Move to Monday of the current week.
            int daysSinceMonday = dow == Calendar.SUNDAY ? 6 : dow - Calendar.MONDAY;
            cursor.add(Calendar.DAY_OF_YEAR, -daysSinceMonday);

            for (int i = 0; i < 7; i++) {
                String k = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cursor.getTime());
                JSONObject day = getOfficeDay(k, false);
                long ref = k.equals(todayKey()) ? System.currentTimeMillis() : cursor.getTimeInMillis();
                long d = officeDuration(day, ref);
                if (d > 0) {
                    totalWeek += d;
                    weekDays++;
                }
                cursor.add(Calendar.DAY_OF_YEAR, 1);
            }
            summary.setText("This week  •  " + weekDays + " office days  •  " + fmtMillis(totalWeek));

            Calendar c = Calendar.getInstance();
            for (int i = 0; i < 31; i++) {
                String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
                JSONObject day = getOfficeDay(key, false);
                long ref = key.equals(todayKey()) ? System.currentTimeMillis() : c.getTimeInMillis();
                long office = officeDuration(day, ref);
                long commute = commuteDuration(day, ref);

                LinearLayout row = horizontal();
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                row.setBackground(bg(i == 0 ? "#20213A" : "#151A27", 14));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.bottomMargin = dp(7);
                days.addView(row, rp);

                LinearLayout info = vertical();
                TextView date = text(new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(c.getTime()), 13, Color.WHITE, true);
                info.addView(date);

                String detail = "No office record";
                if (day != null && office > 0) {
                    detail = fmtMillis(office) + " in office";
                    if (commute > 0) detail += "  •  " + fmtMillis(commute) + " commute";
                    if (hasOpenOfficeSession(day)) detail += "  •  active";
                }
                info.addView(text(detail, 10, color("#8792A6"), false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

                TextView marker = text((day != null && office > 0) ? "●" : "○",
                        17, (day != null && office > 0) ? color("#47D18C") : color("#4A5364"), true);
                row.addView(marker, new LinearLayout.LayoutParams(dp(26), -2));

                if (day != null) {
                    final JSONObject detailDay = day;
                    final String detailDate = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(c.getTime());
                    final String detailKey = key;
                    row.setOnClickListener(v -> showAttendanceDayDetail(detailDate, detailKey, detailDay));
                }
                c.add(Calendar.DAY_OF_YEAR, -1);
            }

            content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(500)));

            new AlertDialog.Builder(this)
                    .setTitle("Office attendance")
                    .setView(content)
                    .setPositiveButton("Done", null)
                    .show();
        } catch (Exception ignored) {
            Toast.makeText(this, "Couldn't load attendance history.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAttendanceDayDetail(String date, String dayKey, JSONObject day) {
        try {
            LinearLayout content = vertical();
            content.setPadding(dp(20), dp(2), dp(20), dp(2));

            if (day == null) {
                Toast.makeText(this, "No attendance record for this day.", Toast.LENGTH_SHORT).show();
                return;
            }
            long now = System.currentTimeMillis();
            long office = officeDuration(day, dayKey.equals(todayKey()) ? now : 0);
            long commute = commuteDuration(day, dayKey.equals(todayKey()) ? now : 0);

            TextView summary = text("Office " + fmtMillis(office) + "  •  Commute " + fmtMillis(commute),
                    15, Color.WHITE, true);
            content.addView(summary);

            JSONArray sessions = day.optJSONArray("sessions");
            if (sessions != null) {
                for (int i = 0; i < sessions.length(); i++) {
                    JSONObject s = sessions.optJSONObject(i);
                    if (s == null) continue;
                    long in = s.optLong("in", 0);
                    long out = s.optLong("out", 0);
                    String value = "Office session " + (i + 1) + ": " + (in > 0 ? clock(in) : "—")
                            + " → " + (out > 0 ? clock(out) : "Active");
                    TextView t = text(value, 12, color("#AEB7C9"), false);
                    t.setPadding(0, dp(10), 0, dp(2));
                    content.addView(t);
                }
            }

            long ciS = day.optLong("commuteInStart", 0);
            long ciE = day.optLong("commuteInEnd", 0);
            long coS = day.optLong("commuteOutStart", 0);
            long coE = day.optLong("commuteOutEnd", 0);
            if (ciS > 0 || ciE > 0) content.addView(text("To office: " + clock(ciS) + " → " + (ciE > 0 ? clock(ciE) : "Active"), 12, color("#AEB7C9"), false));
            if (coS > 0 || coE > 0) content.addView(text("Home: " + clock(coS) + " → " + (coE > 0 ? clock(coE) : "Active"), 12, color("#AEB7C9"), false));

            new AlertDialog.Builder(this)
                    .setTitle(date)
                    .setView(content)
                    .setNeutralButton("Edit times", (d, w) -> editAttendanceDay(dayKey))
                    .setPositiveButton("Done", null)
                    .show();
        } catch (Exception ignored) {
            Toast.makeText(this, "Couldn't load that day.", Toast.LENGTH_SHORT).show();
        }
    }


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

    private void showScreenTimeTracker() {
        if (!ScreenTimeTracker.hasUsageAccess(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Enable screen-time history")
                    .setMessage("DayFlow needs Android Usage access to read screen-interactive history. It stores daily totals locally on this device; it does not upload an app-by-app list. After access is granted, DayFlow snapshots recent days and keeps a durable local history.")
                    .setPositiveButton("Open Usage access", (d, w) -> {
                        try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
                        catch (Exception e) {
                            Toast.makeText(this, "Settings → Apps → Special app access → Usage access → DayFlow.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        ScreenTimeTracker.refreshRecentDays(this, 14);
        java.util.TreeSet<String> historyDates = new java.util.TreeSet<>(Collections.reverseOrder());
        for (String prefKey : getPrefs().getAll().keySet()) {
            if (prefKey.startsWith("screenTimeMs_")) {
                historyDates.add(prefKey.substring("screenTimeMs_".length()));
            }
        }
        // Include recent dates even when Android has not supplied a usable snapshot yet.
        for (int i = 0; i < 14; i++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, -i);
            historyDates.add(keyForDate(day.getTime()));
        }

        StringBuilder report = new StringBuilder("Phone screen-interactive time • saved locally\n");
        report.append("Showing all stored dates plus the most recent 14 days.\n\n");
        for (String key : historyDates) {
            Date day = parseDateKey(key);
            String label = day == null ? key
                    : new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(day);
            long value = ScreenTimeTracker.getStoredMillis(this, key);
            report.append(label).append("  —  ");
            if (value < 0) report.append("No DayFlow snapshot");
            else {
                report.append(ScreenTimeTracker.formatDuration(value));
                if (ScreenTimeTracker.isEstimated(this, key)) report.append(" (foreground-use estimate)");
                long captured = ScreenTimeTracker.getCapturedAt(this, key);
                if (captured > 0) report.append("\n    Last value change ").append(clock(captured));
            }
            report.append("\n");
        }
        report.append("\nAndroid retains detailed screen events only for a limited recent period. DayFlow keeps each daily total it has saved, but cannot recreate older totals that Android no longer provides.");
        TextView view = text(report.toString(), 13, color("#D7DFED"), false);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));
        view.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(color("#0F131C"));
        scroll.addView(view, new ScrollView.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("Screen time history")
                .setView(scroll)
                .setNeutralButton("Refresh", (d, w) -> showScreenTimeTracker())
                .setPositiveButton("Close", null)
                .show();
    }

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
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new java.io.IOException("Could not open backup file");
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] bytes = new byte[4096];
            int count;
            while ((count = input.read(bytes)) != -1) buffer.write(bytes, 0, count);
            JSONObject root = new JSONObject(buffer.toString(java.nio.charset.StandardCharsets.UTF_8.name()));
            if (!"DayFlow".equals(root.optString("app")) || root.optInt("formatVersion", 0) != 1
                    || !(root.opt("preferences") instanceof JSONObject)) {
                throw new IllegalArgumentException("This is not a supported DayFlow backup.");
            }
            JSONObject values = root.getJSONObject("preferences");
            new AlertDialog.Builder(this).setTitle("Restore backup?")
                    .setMessage("The backup contains " + values.length() + " values. It replaces current plans, attendance, syllabus and screen-time history.")
                    .setPositiveButton("Restore", (d, w) -> restoreBackupValues(values))
                    .setNegativeButton("Cancel", null).show();
        } catch (Exception e) {
            debugLog("Backup import failed: " + e.getMessage());
            Toast.makeText(this, "Couldn't read backup: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void restoreBackupValues(JSONObject values) {
        try {
            SharedPreferences.Editor editor = getPrefs().edit().clear();
            java.util.Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject typed = values.optJSONObject(key);
                if (typed == null) continue;
                String type = typed.optString("type");
                Object value = typed.opt("value");
                if ("string".equals(type)) editor.putString(key, value == null ? "" : String.valueOf(value));
                else if ("boolean".equals(type)) editor.putBoolean(key, typed.optBoolean("value", false));
                else if ("int".equals(type)) editor.putInt(key, typed.optInt("value", 0));
                else if ("long".equals(type)) editor.putLong(key, typed.optLong("value", 0L));
                else if ("float".equals(type)) editor.putFloat(key, (float) typed.optDouble("value", 0.0));
                else if ("stringSet".equals(type) && value instanceof JSONArray) {
                    HashSet<String> set = new HashSet<>();
                    JSONArray list = (JSONArray) value;
                    for (int i = 0; i < list.length(); i++) set.add(list.optString(i));
                    editor.putStringSet(key, set);
                }
            }
            editor.apply();
            selectedDateKey = todayKey();
            load();
            render();
            ensureScreenTimeWorker();
            DayFlowWidgetProvider.refreshAll(this);
            Toast.makeText(this, "DayFlow backup restored.", Toast.LENGTH_LONG).show();
            debugLog("Backup restored");
        } catch (Exception e) {
            debugLog("Backup restore failed: " + e.getMessage());
            Toast.makeText(this, "Backup restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showDiagnostics() {
        String log = getPrefs().getString(DEBUG_LOG_KEY, "No diagnostics recorded yet.");
        if (log.length() > 12000) log = log.substring(log.length() - 12000);
        TextView view = text(log, 11, color("#C7D0DF"), false);
        view.setTextIsSelectable(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(color("#0F131C"));
        scroll.addView(view, new ScrollView.LayoutParams(-1, -1));

        new AlertDialog.Builder(this)
                .setTitle("DayFlow diagnostics")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
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

    private void editAttendanceDay(String dayKey) {
        try {
            JSONObject day = getOfficeDay(dayKey, true);
            JSONArray sessions = day.optJSONArray("sessions");
            if (sessions == null) {
                sessions = new JSONArray();
                day.put("sessions", sessions);
            }
            JSONObject session;
            if (sessions.length() == 0) {
                session = new JSONObject();
                session.put("in", 0);
                session.put("out", 0);
                sessions.put(session);
            } else {
                session = sessions.optJSONObject(0);
            }

            LinearLayout content = vertical();
            content.setPadding(dp(18), dp(2), dp(18), dp(2));
            TextView note = text("Correct the recorded times. DayFlow stores the exact date + time.", 11, color("#7F899D"), false);
            content.addView(note);

            Button inBtn = actionButton("Office in  •  " + (session.optLong("in", 0) > 0 ? clock(session.optLong("in", 0)) : "Not set"), Color.WHITE, "#202638");
            Button outBtn = actionButton("Office out  •  " + (session.optLong("out", 0) > 0 ? clock(session.optLong("out", 0)) : "Not set"), Color.WHITE, "#202638");
            Button ciBtn = actionButton("Commute to office  •  " + timeLabel(day.optLong("commuteInStart", 0)) + " → " + timeLabel(day.optLong("commuteInEnd", 0)), Color.WHITE, "#202638");
            Button coBtn = actionButton("Commute home  •  " + timeLabel(day.optLong("commuteOutStart", 0)) + " → " + timeLabel(day.optLong("commuteOutEnd", 0)), Color.WHITE, "#202638");
            content.addView(inBtn, new LinearLayout.LayoutParams(-1, dp(42)));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, dp(42)); ml.topMargin = dp(7); content.addView(outBtn, ml);
            ml = new LinearLayout.LayoutParams(-1, dp(42)); ml.topMargin = dp(7); content.addView(ciBtn, ml);
            ml = new LinearLayout.LayoutParams(-1, dp(42)); ml.topMargin = dp(7); content.addView(coBtn, ml);

            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Edit attendance")
                    .setView(content).setPositiveButton("Done", (d, w) -> { saveOfficeDay(day); render(); })
                    .show();

            inBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, "officeIn", session, inBtn));
            outBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, "officeOut", session, outBtn));
            ciBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, "commuteInStart", day, ciBtn));
            coBtn.setOnClickListener(v -> pickAttendanceTime(dayKey, "commuteOutStart", day, coBtn));
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
        Date d = parseDateKey(dayKey);
        if (d != null) c.setTime(d);
        if (existing > 0) c.setTimeInMillis(existing);
        TimePickerDialog picker = new TimePickerDialog(this, (v, h, m) -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.YEAR, c.get(Calendar.YEAR));
            chosen.set(Calendar.MONTH, c.get(Calendar.MONTH));
            chosen.set(Calendar.DAY_OF_MONTH, c.get(Calendar.DAY_OF_MONTH));
            chosen.set(Calendar.HOUR_OF_DAY, h);
            chosen.set(Calendar.MINUTE, m);
            chosen.set(Calendar.SECOND, 0);
            chosen.set(Calendar.MILLISECOND, 0);
            try {
                target.put(field, chosen.getTimeInMillis());
                button.setText(button.getText().toString().split("  •  ")[0] + "  •  " + clock(chosen.getTimeInMillis()));
                saveOfficeDay(getOfficeDay(dayKey, false));
            } catch (Exception ignored) {
            }
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
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
        } catch (Exception ignored) {
        }
        render();
    }

    private void load() {
        loadSelectedDate();
        debugLog("App load: date=" + selectedDateKey + " mode=" + mode + " blocks=" + blocks.size());
    }

    private void loadTemplateSilent() {
        for (Block source : getTemplateBlocks(mode)) {
            blocks.add(new Block(System.currentTimeMillis() + blocks.size(),
                    source.start, source.end, source.title, source.priority, source.rigidity, "pending"));
        }
    }
}
