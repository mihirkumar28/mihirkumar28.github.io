package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final String PREFS = "dayflow";
    private static final String KEY = "blocks";

    private final ArrayList<Block> blocks = new ArrayList<>();
    private final Handler handler = new Handler();

    private LinearLayout root;
    private LinearLayout timeline;
    private TextView dateView, completionView, progressPercent, currentTitle, currentTime, currentCountdown;
    private TextView plannedView, doneView;
    private ProgressBar progressBar;

    private static class Block {
        long id;
        String start, end, title, priority, status;
        Block(long id, String start, String end, String title, String priority, String status) {
            this.id=id; this.start=start; this.end=end; this.title=title; this.priority=priority; this.status=status;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(color("#0B0D12"));
        window.setNavigationBarColor(color("#0B0D12"));

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
        return h > 0 ? h + "h " + String.format(Locale.US, "%02d", m) + "m" : m + "m";
    }

    private String toTime(int n) {
        n = Math.max(0, Math.min(1439, n));
        return String.format(Locale.US, "%02d:%02d", n / 60, n % 60);
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

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(color("#0B0D12"));

        root = vertical();
        root.setPadding(dp(20), dp(12), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        // Extra inset safety for Android gesture/status bars.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = Math.max(dp(12), insets.getSystemWindowInsetTop() + dp(6));
            int bottom = Math.max(dp(34), insets.getSystemWindowInsetBottom() + dp(20));
            v.setPadding(dp(20), top, dp(20), bottom);
            return insets;
        });

        buildHeader();
        buildCurrentCard();
        buildTodaySummary();
        buildScheduleHeader();
        timeline = vertical();
        root.addView(timeline, new LinearLayout.LayoutParams(-1, -2));
        root.addView(spacer(8));

        TextView footer = text("Saved on this device • safe to close the app", 12, color("#697386"), false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(34));
        fp.topMargin = dp(6);
        root.addView(footer, fp);
    }

    private void buildHeader() {
        LinearLayout header = horizontal();
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = dp(20);
        root.addView(header, hp);

        LinearLayout titleCol = vertical();
        TextView appName = text("DayFlow", 24, Color.WHITE, true);
        dateView = text("", 13, color("#8E98AA"), false);
        titleCol.addView(appName);
        titleCol.addView(dateView);
        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        Button menu = actionButton("＋", Color.WHITE, "#191E2B");
        menu.setTextSize(22);
        menu.setOnClickListener(v -> openAddDialog());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(48), dp(48));
        header.addView(menu, mp);
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
        row.addView(currentCountdown, new LinearLayout.LayoutParams(-2, -2));

        Button markDone = actionButton("Mark done", Color.WHITE, "#8B7CFF");
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(-1, dp(46));
        doneLp.topMargin = dp(16);
        card.addView(markDone, doneLp);
        markDone.setTag("currentDone");
        markDone.setOnClickListener(v -> completeCurrent());

        Button recover = actionButton("Adjust my day", color("#DAD7FF"), "#2C2A4A");
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(-1, dp(42));
        recLp.topMargin = dp(8);
        card.addView(recover, recLp);
        recover.setOnClickListener(v -> recoveryMode());
    }

    private void buildTodaySummary() {
        LinearLayout summary = horizontal();
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(76));
        sp.bottomMargin = dp(18);
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

    private View spacerHorizontal(int width) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(width), 1));
        return s;
    }

    private void buildScheduleHeader() {
        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(10);
        root.addView(row, rp);

        TextView label = text("TODAY", 13, color("#8E98AA"), true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView hint = text("Tap ✓ when you're done", 11, color("#697386"), false);
        row.addView(hint);
    }

    private void render() {
        Collections.sort(blocks, Comparator.comparingInt(b -> mins(b.start)));

        Calendar now = Calendar.getInstance();
        int nowMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now.getTime());
        dateView.setText(date);

        Block current = null;
        Block next = null;

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

        int total = blocks.size();
        int done = 0;
        int plannedMinutes = 0;
        int doneMinutes = 0;

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

        timeline.removeAllViews();
        if (blocks.isEmpty()) {
            TextView empty = text("Your day is empty. Tap ＋ to add a block.", 15, color("#8E98AA"), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, dp(42));
            timeline.addView(empty);
        } else {
            for (Block b : blocks) {
                addBlockView(b, current != null && current.id == b.id, nowMins);
            }
        }
    }

    private String fmtShort(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return m + "m";
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }

    private void updateCurrentCard(Block current, Block next, int nowMins) {
        View doneButton = root.findViewWithTag("currentDone");

        Block target = current != null ? current : next;
        if (target == null) {
            currentTitle.setText("Day complete");
            currentTime.setText("Nice work.");
            currentCountdown.setText("✓");
            if (doneButton != null) doneButton.setVisibility(View.GONE);
            return;
        }

        if (doneButton != null) doneButton.setVisibility(View.VISIBLE);

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
        TextView start = text(time12(b.start), 12, color("#8E98AA"), true);
        TextView end = text(time12(b.end), 10, color("#606A7B"), false);
        timeCol.addView(start);
        timeCol.addView(end);
        row.addView(timeCol, new LinearLayout.LayoutParams(dp(63), -2));

        LinearLayout card = horizontal();
        card.setPadding(dp(13), dp(12), dp(8), dp(12));

        String surface;
        String border;
        if ("done".equals(b.status)) {
            surface = "#111A16";
            border = "#2F7B58";
        } else if ("skipped".equals(b.status)) {
            surface = "#171214";
            border = "#5C3038";
        } else if (current) {
            surface = "#26233E";
            border = "#8B7CFF";
        } else {
            surface = "#151A27";
            border = "#252C3B";
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
            TextView check = text("✓", color("#47D18C"), true);
            check.setTextSize(20);
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
        if (value == null) return "Flexible";
        if (value.length() == 0) return "Flexible";
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void showBlockOptions(Block b) {
        String[] actions;
        if ("done".equals(b.status)) {
            actions = new String[]{"Undo completion"};
        } else if ("skipped".equals(b.status)) {
            actions = new String[]{"Restore block"};
        } else {
            actions = new String[]{"Mark done", "Skip block"};
        }

        new AlertDialog.Builder(this)
                .setTitle(b.title)
                .setItems(actions, (dialog, which) -> {
                    if ("done".equals(b.status) || "skipped".equals(b.status)) {
                        b.status = "pending";
                    } else if (which == 0) {
                        b.status = "done";
                    } else {
                        b.status = "skipped";
                    }
                    save();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void completeCurrent() {
        int nowMins = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE);
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
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(4), dp(20), dp(4));

        EditText title = new EditText(this);
        title.setHint("What are you doing?");
        title.setTextColor(Color.WHITE);
        title.setHintTextColor(color("#697386"));
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));

        String[] priority = {"Important", "Fixed", "Flexible", "Optional"};

        new AlertDialog.Builder(this)
                .setTitle("New time block")
                .setView(form)
                .setSingleChoiceItems(priority, 0, null)
                .setPositiveButton("Choose time", (d, w) -> {
                    int which = ((AlertDialog) d).getListView().getCheckedItemPosition();
                    which = which < 0 ? 0 : which;
                    final int selectedPriority = which;
                    TimePickerDialog start = new TimePickerDialog(this, (v, h, m) -> {
                        final String s = String.format(Locale.US, "%02d:%02d", h, m);
                        TimePickerDialog end = new TimePickerDialog(this, (v2, h2, m2) -> {
                            String e = String.format(Locale.US, "%02d:%02d", h2, m2);
                            if (mins(e) <= mins(s)) {
                                Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String p = priority[selectedPriority].toLowerCase(Locale.US);
                            String name = title.getText().toString().trim();
                            if (name.isEmpty()) {
                                Toast.makeText(this, "Give the block a name.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            blocks.add(new Block(System.currentTimeMillis(), s, e, name, p, "pending"));
                            save();
                        }, 20, 0, true);
                        end.setTitle("End time");
                        end.show();
                    }, 19, 0, true);
                    start.setTitle("Start time");
                    start.show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void recoveryMode() {
        int nowMins = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE);
        Block active = null;

        for (Block b : blocks) {
            if ("pending".equals(b.status) && mins(b.start) < nowMins && nowMins < mins(b.end)) {
                active = b;
                break;
            }
        }

        if (active == null) {
            Toast.makeText(this, "No active block to adjust.", Toast.LENGTH_SHORT).show();
            return;
        }

        int late = nowMins - mins(active.start);
        int index = blocks.indexOf(active);

        for (int i = index + 1; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (!"pending".equals(b.status) || "fixed".equals(b.priority)) continue;

            int start = mins(b.start) + late;
            int end = mins(b.end) + late;
            if (end < 1440) {
                b.start = toTime(start);
                b.end = toTime(end);
            }
        }

        save();
        Toast.makeText(this, "Adjusted the flexible part of your day by " + late + " minutes.", Toast.LENGTH_SHORT).show();
    }

    private void loadTemplate(String key) {
        String[][] t;
        if ("office".equals(key)) {
            t = new String[][]{
                    {"06:00","06:30","Wake up + routine","fixed"},
                    {"06:30","08:00","Maths Optional","important"},
                    {"08:00","09:00","Breakfast + commute","fixed"},
                    {"09:00","18:30","Work + commute","fixed"},
                    {"18:30","19:15","Reset","flexible"},
                    {"19:15","21:15","GS study","important"},
                    {"21:15","22:00","Dinner","fixed"},
                    {"22:00","23:00","Interview preparation","important"},
                    {"23:00","23:30","Wind down","flexible"}
            };
        } else if ("weekend".equals(key)) {
            t = new String[][]{
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
        } else {
            t = new String[][]{
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

        blocks.clear();
        for (int i = 0; i < t.length; i++) {
            blocks.add(new Block(System.currentTimeMillis() + i, t[i][0], t[i][1], t[i][2], t[i][3], "pending"));
        }
        save();
    }

    private void clearDay() {
        new AlertDialog.Builder(this)
                .setTitle("Clear today's plan?")
                .setMessage("All blocks for today will be removed from this device.")
                .setPositiveButton("Clear", (d, w) -> {
                    blocks.clear();
                    save();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
        render();
    }

    private void load() {
        try {
            String raw = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
            if (raw != null) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    blocks.add(new Block(
                            o.getLong("id"),
                            o.getString("start"),
                            o.getString("end"),
                            o.getString("title"),
                            o.getString("priority"),
                            o.getString("status")
                    ));
                }
            } else {
                loadTemplateSilent();
            }
        } catch (Exception e) {
            blocks.clear();
            loadTemplateSilent();
        }
    }

    private void loadTemplateSilent() {
        String[][] t = new String[][]{
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

        for (int i = 0; i < t.length; i++) {
            blocks.add(new Block(System.currentTimeMillis() + i, t[i][0], t[i][1], t[i][2], t[i][3], "pending"));
        }
    }
}
