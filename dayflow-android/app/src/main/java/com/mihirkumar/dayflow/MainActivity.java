package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
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

    private final ArrayList<Block> blocks = new ArrayList<>();
    private final Handler handler = new Handler();

    private LinearLayout root, timeline, officeCard;
    private TextView dateView, modeView, completionView, progressPercent;
    private TextView currentTitle, currentTime, currentCountdown, plannedView, doneView;
    private TextView officeStatus, officeTimes, commuteView;
    private Button officeAction, commuteAction;
    private String mode = "wfh";
    private String selectedDateKey;

    private static class Block {
        long id;
        String start, end, title, priority, status;
        Block(long id, String start, String end, String title, String priority, String status) {
            this.id = id;
            this.start = start;
            this.end = end;
            this.title = title;
            this.priority = priority;
            this.status = status;
        }
    }

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

        handler.postDelayed(new Runnable() {
            @Override public void run() {
                render();
                handler.postDelayed(this, 30000);
            }
        }, 30000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
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

    private String fmtMillis(long ms) {
        long minutes = Math.max(0, ms / 60000L);
        return fmtShort((int) Math.min(minutes, Integer.MAX_VALUE));
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

        Button recover = actionButton("Catch up", color("#DAD7FF"), "#2C2A4A");
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(-1, dp(42));
        recLp.topMargin = dp(8);
        card.addView(recover, recLp);
        recover.setTag("recoveryButton");
        recover.setOnClickListener(v -> recoveryMode());
    }

    private void buildTodaySummary() {
        LinearLayout summary = horizontal();
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(72));
        sp.bottomMargin = dp(14);
        root.addView(summary, sp);

        LinearLayout left = vertical();
        completionView = text("0 / 0 complete", 14, Color.WHITE, true);
        progressPercent = text("0% of the plan", 12, color("#7F899D"), false);
        left.addView(completionView);
        left.addView(progressPercent);
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

        int total = blocks.size(), done = 0, plannedMinutes = 0, doneMinutes = 0;
        for (Block b : blocks) {
            int d = Math.max(0, mins(b.end) - mins(b.start));
            plannedMinutes += d;
            if ("done".equals(b.status)) {
                done++;
                doneMinutes += d;
            }
        }

        completionView.setText(done + " / " + total + " complete");
        int percent = total == 0 ? 0 : Math.round(done * 100f / total);
        progressPercent.setText(percent + "% of the plan");
        plannedView.setText(fmtShort(plannedMinutes));
        doneView.setText(fmt(doneMinutes) + " done");

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

    private void updateCurrentCard(Block current, Block next, int nowMins) {
        View doneButton = root.findViewWithTag("currentDone");
        View recoveryButton = root.findViewWithTag("recoveryButton");
        boolean viewingToday = selectedDateKey.equals(todayKey());
        if (!viewingToday) {
            currentTitle.setText(selectedDateKey.compareTo(todayKey()) < 0 ? "Historical day" : "Planned day");
            Date d = parseDateKey(selectedDateKey);
            currentTime.setText(d == null ? selectedDateKey : new SimpleDateFormat("EEE, d MMMM", Locale.getDefault()).format(d));
            currentCountdown.setText("Review timeline");
            if (doneButton != null) doneButton.setVisibility(View.GONE);
            if (recoveryButton != null) recoveryButton.setVisibility(View.GONE);
            return;
        }
        if (recoveryButton != null) recoveryButton.setVisibility(View.VISIBLE);
        Block target = current != null ? current : next;

        if (target == null) {
            currentTitle.setText("Day complete");
            currentTime.setText("Nice work.");
            currentCountdown.setText("✓");
            if (doneButton != null) doneButton.setVisibility(View.GONE);
            return;
        }

        if (doneButton != null) doneButton.setVisibility(current != null ? View.VISIBLE : View.GONE);
        if (current != null) {
            currentTitle.setText(current.title);
            currentTime.setText(time12(current.start) + " – " + time12(current.end));
            int remaining = Math.max(0, mins(current.end) - nowMins);
            currentCountdown.setText(fmt(remaining) + " left");
        } else {
            currentTitle.setText(next.title);
            currentTime.setText("Next • " + time12(next.start) + " – " + time12(next.end));
            int until = Math.max(0, mins(next.start) - nowMins);
            currentCountdown.setText("in " + fmt(until));
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
        TextView meta = text(priorityLabel(b.priority) + " • " + fmt(mins(b.end) - mins(b.start)), 11, color("#8E98AA"), false);
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

    private String priorityLabel(String value) {
        if (value == null || value.isEmpty()) return "Flexible";
        String result = value;
        return Character.toUpperCase(result.charAt(0)) + result.substring(1);
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
                        blocks.remove(b);
                        save();
                    } else if ("Undo completion".equals(a) || "Restore block".equals(a)) {
                        b.status = "pending";
                        save();
                    } else if ("Mark done".equals(a)) {
                        b.status = "done";
                        save();
                    } else if ("Skip block".equals(a)) {
                        b.status = "skipped";
                        save();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void completeCurrent() {
        int nowMins = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                + Calendar.getInstance().get(Calendar.MINUTE);
        for (Block b : blocks) {
            if ("pending".equals(b.status) && mins(b.start) <= nowMins && nowMins < mins(b.end)) {
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

        TextView behaviorLabel = text("BLOCK BEHAVIOR", 10, color("#6F7C91"), true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(12);
        form.addView(behaviorLabel, blp);

        LinearLayout behaviors = horizontal();
        form.addView(behaviors, new LinearLayout.LayoutParams(-1, dp(42)));
        String[] behaviorNames = {"Important", "Fixed", "Flexible", "Optional"};
        String[] behaviorValues = {"important", "fixed", "flexible", "optional"};
        String[] selectedBehavior = {"flexible"};
        for (int i = 0; i < behaviorNames.length; i++) {
            final int idx = i;
            Button b = chip(behaviorNames[i]);
            if (i == 2) {
                b.setTextColor(Color.WHITE);
                b.setBackground(bg("#413B73", 12));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(5);
            behaviors.addView(b, lp);
            b.setOnClickListener(v -> {
                selectedBehavior[0] = behaviorValues[idx];
                for (int j = 0; j < behaviors.getChildCount(); j++) {
                    ((Button) behaviors.getChildAt(j)).setTextColor(j == idx ? Color.WHITE : color("#AEB7C9"));
                    ((Button) behaviors.getChildAt(j)).setBackground(bg(j == idx ? "#413B73" : "#151A27", 12));
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
                result.add(new Block(o.getLong("id"), o.getString("start"), o.getString("end"),
                        o.getString("title"), o.getString("priority"), o.getString("status")));
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
        form.setPadding(dp(20), 0, dp(20), 0);

        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setText(b.title);
        title.setTextColor(Color.WHITE);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView timing = text("Current: " + time12(b.start) + " – " + time12(b.end), 12, color("#8E98AA"), false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(8);
        form.addView(timing, tp);

        new AlertDialog.Builder(this)
                .setTitle("Edit block")
                .setView(form)
                .setPositiveButton("Change time", (d, w) -> {
                    TimePickerDialog start = new TimePickerDialog(this, (v, h, m) -> {
                        int s = h * 60 + m;
                        TimePickerDialog end = new TimePickerDialog(this, (v2, h2, m2) -> {
                            int e = h2 * 60 + m2;
                            if (e <= s) {
                                Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String name = title.getText().toString().trim();
                            if (!name.isEmpty()) b.title = name;
                            b.start = toTime(s);
                            b.end = toTime(e);
                            save();
                        }, mins(b.end) / 60, mins(b.end) % 60, true);
                        end.setTitle("New end time");
                        end.show();
                    }, mins(b.start) / 60, mins(b.start) % 60, true);
                    start.setTitle("New start time");
                    start.show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void recoveryMode() {
        Collections.sort(blocks, Comparator.comparingInt(b -> mins(b.start)));

        int nowMins = currentMinutes();
        int activeIndex = -1;
        Block active = null;

        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if ("pending".equals(b.status) && mins(b.start) <= nowMins && nowMins < mins(b.end)) {
                active = b;
                activeIndex = i;
                break;
            }
        }

        if (active == null) {
            Toast.makeText(this, "No active block to catch up from.", Toast.LENGTH_SHORT).show();
            return;
        }

        final int selectedActiveIndex = activeIndex;
        new AlertDialog.Builder(this)
                .setTitle("Catch up?")
                .setMessage("Fixed blocks stay exactly where they are. Flexible, important, and optional blocks will be reflowed in order around those fixed anchors.")
                .setPositiveButton("Reflow day", (d, w) -> reflowRemainingDay(selectedActiveIndex))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reflowRemainingDay(int activeIndex) {
        int cursor = mins(blocks.get(activeIndex).end);
        int fixedIndex = activeIndex + 1;
        int moved = 0;

        // Fixed blocks are hard anchors. Everything else that is still pending
        // is movable and keeps its duration + relative order.
        while (fixedIndex < blocks.size()) {
            Block fixed = blocks.get(fixedIndex);
            if ("pending".equals(fixed.status) && "fixed".equals(fixed.priority)) break;
            fixedIndex++;
        }

        for (int i = activeIndex + 1; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (!"pending".equals(block.status)) continue;

            // If this block itself is fixed, it becomes the next hard anchor.
            if ("fixed".equals(block.priority)) {
                cursor = Math.max(cursor, mins(block.end));
                fixedIndex = i + 1;
                while (fixedIndex < blocks.size()) {
                    Block next = blocks.get(fixedIndex);
                    if ("pending".equals(next.status) && "fixed".equals(next.priority)) break;
                    fixedIndex++;
                }
                continue;
            }

            int duration = Math.max(1, mins(block.end) - mins(block.start));

            // Skip anchors that we've already passed.
            while (fixedIndex < blocks.size() &&
                    mins(blocks.get(fixedIndex).start) < cursor) {
                cursor = Math.max(cursor, mins(blocks.get(fixedIndex).end));
                fixedIndex++;
                while (fixedIndex < blocks.size()) {
                    Block next = blocks.get(fixedIndex);
                    if ("pending".equals(next.status) && "fixed".equals(next.priority)) break;
                    fixedIndex++;
                }
            }

            if (fixedIndex < blocks.size()) {
                Block nextFixed = blocks.get(fixedIndex);
                int fixedStart = mins(nextFixed.start);

                // Use the free gap before the anchor where possible.
                if (cursor + duration > fixedStart) {
                    cursor = mins(nextFixed.end);
                    fixedIndex++;
                    while (fixedIndex < blocks.size()) {
                        Block next = blocks.get(fixedIndex);
                        if ("pending".equals(next.status) && "fixed".equals(next.priority)) break;
                        fixedIndex++;
                    }
                }
            }

            int newStart = cursor;
            int newEnd = newStart + duration;

            if (newEnd > 1440) {
                // Never truncate work or move a fixed anchor. Leave this block
                // unchanged when the remaining day has no legal space.
                continue;
            }

            String oldStart = block.start;
            String oldEnd = block.end;
            block.start = toTime(newStart);
            block.end = toTime(newEnd);
            cursor = newEnd;

            if (!oldStart.equals(block.start) || !oldEnd.equals(block.end)) moved++;
        }

        save();

        if (moved == 0) {
            Toast.makeText(this, "Nothing needed reflowing.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,
                    moved + " movable block" + (moved == 1 ? "" : "s") +
                            " reflowed around fixed blocks.",
                    Toast.LENGTH_SHORT).show();
        }
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
                    else clearDay();
                })
                .setNegativeButton("Close", null)
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
                result.add(new Block(o.optLong("id", System.currentTimeMillis() + i),
                        o.optString("start", "06:00"),
                        o.optString("end", "07:00"),
                        o.optString("title", "Block"),
                        o.optString("priority", "flexible"),
                        "pending"));
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
                arr.put(o);
            }
            getPrefs().edit().putString(templateKey(key), arr.toString()).apply();
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
                            source.start, source.end, source.title, source.priority, "pending"));
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
            details.addView(text(time12(b.start) + " – " + time12(b.end) + "  •  " + priorityLabel(b.priority),
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
        final String[] priority = {original.priority};

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

        TextView behaviorLabel = text("BLOCK BEHAVIOR", 10, color("#6F7C91"), true);
        LinearLayout.LayoutParams behaviorLabelLp = new LinearLayout.LayoutParams(-1, -2);
        behaviorLabelLp.topMargin = dp(12);
        form.addView(behaviorLabel, behaviorLabelLp);

        LinearLayout behaviors = horizontal();
        form.addView(behaviors, new LinearLayout.LayoutParams(-1, dp(42)));
        String[] behaviorNames = {"Important", "Fixed", "Flexible", "Optional"};
        String[] behaviorValues = {"important", "fixed", "flexible", "optional"};
        int selected = 0;
        for (int i = 0; i < behaviorValues.length; i++) if (behaviorValues[i].equals(priority[0])) selected = i;
        for (int i = 0; i < behaviorNames.length; i++) {
            final int idx = i;
            Button b = chip(behaviorNames[i]);
            b.setTextColor(i == selected ? Color.WHITE : color("#AEB7C9"));
            b.setBackground(bg(i == selected ? "#413B73" : "#151A27", 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i > 0) lp.leftMargin = dp(5);
            behaviors.addView(b, lp);
            b.setOnClickListener(v -> {
                priority[0] = behaviorValues[idx];
                for (int j = 0; j < behaviors.getChildCount(); j++) {
                    Button child = (Button) behaviors.getChildAt(j);
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
                                name, priority[0], "pending"));
                    } else {
                        original.title = name;
                        original.start = toTime(start[0]);
                        original.end = toTime(end[0]);
                        original.priority = priority[0];
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
                    source.start, source.end, source.title, source.priority, "pending"));
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

            commuteView.setText("Today • Office " + fmtMillis(office)
                    + "  •  Commute " + fmtMillis(commute));

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
                    blocks.add(new Block(o.getLong("id"), o.getString("start"), o.getString("end"),
                            o.getString("title"), o.getString("priority"), o.getString("status")));
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
                o.put("status", b.status);
                arr.put(o);
            }
            getPrefs().edit()
                    .putString(blocksKey(selectedDateKey), arr.toString())
                    .putString(modeKey(selectedDateKey), mode)
                    .putString(KEY, selectedDateKey.equals(todayKey()) ? arr.toString() : getPrefs().getString(KEY, arr.toString()))
                    .putString(MODE_KEY, mode)
                    .apply();
            DayFlowWidgetProvider.refreshAll(this);
        } catch (Exception ignored) {
        }
        render();
    }

    private void load() {
        loadSelectedDate();
    }

    private void loadTemplateSilent() {
        for (Block source : getTemplateBlocks(mode)) {
            blocks.add(new Block(System.currentTimeMillis() + blocks.size(),
                    source.start, source.end, source.title, source.priority, "pending"));
        }
    }
}
