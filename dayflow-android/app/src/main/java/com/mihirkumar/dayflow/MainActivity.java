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
import android.text.Editable;
import android.text.TextWatcher;
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
    private static final String STARTER_SEEDED_KEY = "starterSyllabusSeeded_v2";
    private static final String INTERVIEW_PLAN_START_KEY = "softwareInterviewPlanStart";
    private static final String INTERVIEW_TARGET_KEY = "softwareInterviewTarget";
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
    private LinearLayout planList, progressContent, officePageContent, moreContent, syllabusList, screenTimeList;
    private LinearLayout bottomNavigation;
    private TextView syllabusSummaryView, screenTimeSummaryView;
    private LinearLayout syllabusBreakdown;
    private EditText syllabusSearch;
    private String activePage = "home";
    private String syllabusFilter = "All";
    private String syllabusQuery = "";
    private final Map<String, TextView> navItems = new LinkedHashMap<>();
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
        window.setStatusBarColor(color("#0D1512"));
        window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        selectedDateKey = todayKey();
        load();
        buildUi();
        ensureStarterPlans();
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
        String key = hex == null ? "" : hex.toUpperCase(Locale.US);
        switch (key) {
            case "#0D1512": case "#0B0D12": case "#0F131C": case "#111A16":
                return Color.parseColor("#F4F6F1");
            case "#131D18": case "#152019": case "#1B2621":
                return Color.parseColor("#EAF0E9");
            case "#17231D": case "#1B2922": case "#18231E":
                return Color.parseColor("#FFFFFF");
            case "#23352C": case "#283A30": case "#263243": case "#202936":
                return Color.parseColor("#E6EEE7");
            case "#23845F": case "#40B58A": case "#27513E": case "#245B45":
            case "#243F34": case "#2C8061": case "#3F8E69":
                return Color.parseColor("#286B55");
            case "#70D6A5": case "#6BD0A6": case "#7DD6B2": case "#6FD6A3":
            case "#65C5A0":
                return Color.parseColor("#2E8060");
            case "#203E30": case "#172B22": case "#DCEBE3":
                return Color.parseColor("#DCEBE2");
            case "#3A2729": case "#B84B5C":
                return Color.parseColor("#F8E8E6");
            case "#D5B56E": return Color.parseColor("#A76821");
            case "#F2F7F3": case "#F1F6F2": case "#F5FBF7": case "#F5F6F1":
                return Color.parseColor("#18372E");
            case "#D6E4DA": case "#D6E1D8": case "#DCE2EE": case "#B7C7BC":
                return Color.parseColor("#496257");
            case "#9AABA0": case "#91A197": case "#82948A": case "#A5B5AA":
            case "#A8B0C2": case "#8A9A90": case "#8E98AA": case "#AEB7C9":
            case "#B7C7BC": return Color.parseColor("#687A70");
            case "#FFC5C9": return Color.parseColor("#B44248");
            case "#D2F1E2": return Color.parseColor("#DDECE2");
            case "#FFFFFF": return Color.WHITE;
            default: return Color.parseColor(hex);
        }
    }

    private GradientDrawable bg(String hex, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(hex));
        g.setCornerRadius(dp(radius));
        String key = hex == null ? "" : hex.toUpperCase(Locale.US);
        if (!key.equals("#23845F") && !key.equals("#40B58A") && !key.equals("#27513E")
                && !key.equals("#245B45") && !key.equals("#70D6A5") && !key.equals("#6BD0A6")
                && !key.equals("#7DD6B2") && !key.equals("#65C5A0") && !key.equals("#B84B5C")) {
            g.setStroke(dp(1), color("#DDE6DE"));
        }
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
        boolean solidBackground = "#23845F".equalsIgnoreCase(background)
                || "#40B58A".equalsIgnoreCase(background) || "#27513E".equalsIgnoreCase(background)
                || "#245B45".equalsIgnoreCase(background) || "#70D6A5".equalsIgnoreCase(background)
                || "#6BD0A6".equalsIgnoreCase(background) || "#7DD6B2".equalsIgnoreCase(background)
                || "#65C5A0".equalsIgnoreCase(background) || "#B84B5C".equalsIgnoreCase(background);
        b.setTextColor(textColor == Color.WHITE && !solidBackground ? color("#18372E") : textColor);
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
            int top = Math.max(dp(10), insets.getSystemWindowInsetTop() + dp(4));
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
            page.setPadding(dp(18),dp(18),dp(18),dp(24));
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
        bottomNavigation.setPadding(dp(8),dp(7),dp(8),dp(8));
        bottomNavigation.setBackgroundColor(Color.WHITE);
        String[][] items={{"⌂","Today","home"},{"▤","Plan","plan"},{"◔","Study","progress"},{"◷","Office","office"},{"•••","More","more"}};
        for(String[] item:items){
            LinearLayout tab=vertical();tab.setGravity(Gravity.CENTER);tab.setPadding(dp(3),dp(5),dp(3),dp(4));
            TextView icon=text(item[0],19,color("#687A70"),true);icon.setGravity(Gravity.CENTER);
            TextView label=text(item[1],10,color("#687A70"),true);label.setGravity(Gravity.CENTER);
            tab.addView(icon);tab.addView(label);tab.setContentDescription(item[1]);tab.setOnClickListener(v->switchPage(item[2]));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.leftMargin=dp(2);lp.rightMargin=dp(2);bottomNavigation.addView(tab,lp);navItems.put(item[2],label);
        }
        shell.addView(bottomNavigation,new LinearLayout.LayoutParams(-1,dp(64)));
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
        else if("syllabus".equals(page))refreshSyllabusRows();
        else if("screenTime".equals(page))renderScreenTimePage();
    }

    private void buildPlanPage() {
        pageHeading(pagePlan,"Build your day","Plan","Edit the selected date without hunting through the timeline. Fixed times stay anchored; flexible tasks can move.");
        LinearLayout dateCard=surfaceCard();LinearLayout dateRow=horizontal();
        dateRow.addView(secondaryButton("‹",()->{changeSelectedDate(-1);renderPlanPage();}),new LinearLayout.LayoutParams(dp(45),dp(40)));
        TextView date=text("",14,color("#F1F6F2"),true);date.setGravity(Gravity.CENTER);date.setTag("planDate");dateRow.addView(date,new LinearLayout.LayoutParams(0,dp(40),1));
        dateRow.addView(secondaryButton("›",()->{changeSelectedDate(1);renderPlanPage();}),new LinearLayout.LayoutParams(dp(45),dp(40)));dateCard.addView(dateRow);
        LinearLayout modes=horizontal();String[][] choices={{"WFH","wfh"},{"Office","office"},{"Weekend","weekend"}};
        for(String[] choice:choices){Button b=secondaryButton(choice[0],()->chooseMode(choice[1]));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.leftMargin=dp(4);lp.rightMargin=dp(4);lp.topMargin=dp(10);modes.addView(b,lp);}dateCard.addView(modes);
        LinearLayout templateRow=horizontal();
        for(String[] choice:choices){Button b=secondaryButton("Load "+choice[0],()->chooseTemplateDirect(choice[1]));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1);lp.leftMargin=dp(3);lp.rightMargin=dp(3);lp.topMargin=dp(8);templateRow.addView(b,lp);}dateCard.addView(templateRow);pagePlan.addView(dateCard);
        Button add=primaryButton("＋  Add a block",()->openAddDialog());LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(-1,dp(48));addLp.bottomMargin=dp(15);pagePlan.addView(add,addLp);
        TextView section=text("YOUR BLOCKS",11,color("#9AABA0"),true);LinearLayout.LayoutParams secLp=new LinearLayout.LayoutParams(-1,-2);secLp.bottomMargin=dp(9);pagePlan.addView(section,secLp);
        planList=vertical();pagePlan.addView(planList);renderPlanPage();
    }

    private void renderPlanPage() {
        if(planList==null)return;
        TextView date=(TextView)pagePlan.findViewWithTag("planDate");
        if(date!=null){Date d=parseDateKey(selectedDateKey);date.setText((d==null?selectedDateKey:new SimpleDateFormat("EEE, d MMM",Locale.getDefault()).format(d))+"  •  "+mode.toUpperCase(Locale.US));}
        planList.removeAllViews();ArrayList<Block> ordered=new ArrayList<>(blocks);Collections.sort(ordered,Comparator.comparingInt(b->mins(b.start)));
        if(ordered.isEmpty()){planList.addView(text("Nothing planned yet. Add a block or load a saved routine.",14,color("#9AABA0"),false));return;}
        for(Block block:ordered){
            LinearLayout card=surfaceCard();LinearLayout row=horizontal();row.setGravity(Gravity.TOP);
            LinearLayout time=vertical();time.addView(text(time12(block.start),12,color("#6BD0A6"),true));time.addView(text(time12(block.end),10,color("#8A9A90"),false));row.addView(time,new LinearLayout.LayoutParams(dp(72),-2));
            LinearLayout details=vertical();details.addView(text(block.title,15,color("#F1F6F2"),true));details.addView(text(blockBehaviorLabel(block)+"  •  "+fmt(mins(block.end)-mins(block.start)),11,color("#9AABA0"),false));
            String status="done".equals(block.status)?"Completed":"skipped".equals(block.status)?"Skipped":"Pending";details.addView(text(status,10,"done".equals(block.status)?color("#6BD0A6"):color("#9AABA0"),true));row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(secondaryButton("•••",()->showBlockOptions(block)),new LinearLayout.LayoutParams(dp(45),dp(38)));card.addView(row);
            if("pending".equals(block.status)){Button done=primaryButton("Mark complete",()->{stopFocusForBlock(block,System.currentTimeMillis());block.status="done";save();});LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(40));dlp.topMargin=dp(10);card.addView(done,dlp);}
            else if("done".equals(block.status)){Button undo=secondaryButton("Undo completion",()->{block.status="pending";save();});LinearLayout.LayoutParams ulp=new LinearLayout.LayoutParams(-1,dp(38));ulp.topMargin=dp(8);card.addView(undo,ulp);}
            row.setOnClickListener(v->showBlockOptions(block));planList.addView(card);
        }
    }

    private void buildProgressPage() {
        pageHeading(pageProgress,"Long game","Progress","One honest view of preparation, real work completed, and phone use. Forecasts use your saved history—not invented completion.");
        progressContent=vertical();pageProgress.addView(progressContent);renderProgressPage();
    }
    private void renderProgressPage() {
        if(progressContent==null)return;progressContent.removeAllViews();ArrayList<SyllabusTopic> topics=readSyllabusTopics();
        int cseTotal=0,cseDone=0,intTotal=0,intDone=0;
        for(SyllabusTopic t:topics){boolean sw="Software Interview Prep".equals(t.subject);if(sw){intTotal++;if(t.done)intDone++;}else{cseTotal++;if(t.done)cseDone++;}}
        LinearLayout cse=surfaceCard();cse.addView(text("UPSC CSE",11,color("#6BD0A6"),true));cse.addView(text(cseDone+" / "+cseTotal+" topics complete",22,color("#F2F7F3"),true));
        cse.addView(text(cseTotal==0?"Load the syllabus starter to begin tracking.":Math.round(cseDone*100f/Math.max(1,cseTotal))+"% of your current topic list",12,color("#9AABA0"),false));
        ProgressBar cseBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);cseBar.setMax(Math.max(1,cseTotal));cseBar.setProgress(cseDone);cseBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color("#40B58A")));cse.addView(cseBar);
        Button openCse=primaryButton("Open CSE syllabus",()->{syllabusFilter="All";showSyllabusTracker();});LinearLayout.LayoutParams openLp=new LinearLayout.LayoutParams(-1,dp(42));openLp.topMargin=dp(10);cse.addView(openCse,openLp);progressContent.addView(cse);
        LinearLayout career=surfaceCard();career.addView(text("SOFTWARE INTERVIEW PREP",11,color("#D5B56E"),true));career.addView(text(intDone+" / "+intTotal+" topics complete",22,color("#F2F7F3"),true));
        career.addView(text("Month 6 • Tier 3/4 readiness\nMonth 9 • Tier 1/2 strong-hire target\nInterview-prep commitment: 12–13 hours (track actual sessions with Focus).\nCareer outcome: ₹2.5–2.75L monthly take-home after taxes/deductions; bonus and ESOPs excluded.\nExperience context: about 6 years; evaluate IC and EM paths on fit rather than assuming title alone.",12,color("#9AABA0"),false));
        ProgressBar intBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);intBar.setMax(Math.max(1,intTotal));intBar.setProgress(intDone);intBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color("#D5B56E")));career.addView(intBar);
        Button openCareer=secondaryButton("Open interview roadmap",()->{syllabusFilter="Software Interview Prep";syllabusQuery="";showSyllabusTracker();});LinearLayout.LayoutParams careerLp=new LinearLayout.LayoutParams(-1,dp(42));careerLp.topMargin=dp(10);career.addView(openCareer,careerLp);progressContent.addView(career);
        LinearLayout focus=surfaceCard();long actual=0;int planned=0;long now=System.currentTimeMillis();
        for(Block b:blocks){long d=getActualDuration(b,now);actual+=d;if(isStudyBlock(b.title))planned+=Math.max(0,mins(b.end)-mins(b.start));}
        focus.addView(text("EXECUTION",11,color("#6BD0A6"),true));focus.addView(text(fmtPreciseMillis(actual)+" tracked focus",22,color("#F2F7F3"),true));
        focus.addView(text("Study time planned today: "+fmtShort(planned)+" • actual focus is measured only when you start Focus.",12,color("#9AABA0"),false));focus.addView(secondaryButton("View today's plan",()->switchPage("plan")));progressContent.addView(focus);
        LinearLayout screen=surfaceCard();screen.addView(text("PHONE SCREEN TIME",11,color("#D5B56E"),true));long t=ScreenTimeTracker.getStoredMillis(this,todayKey());
        screen.addView(text(t<0?"Today's total not captured yet":ScreenTimeTracker.formatDuration(t)+" today",22,color("#F2F7F3"),true));
        long week=0;int days=0;for(int i=0;i<7;i++){Calendar c=Calendar.getInstance();c.add(Calendar.DAY_OF_YEAR,-i);long ms=ScreenTimeTracker.getStoredMillis(this,keyForDate(c.getTime()));if(ms>=0){week+=ms;days++;}}
        screen.addView(text(days==0?"Grant Usage access to begin a saved history.":"7-day average: "+ScreenTimeTracker.formatDuration(week/days)+" across "+days+" recorded days.",12,color("#9AABA0"),false));
        Button viewTime=secondaryButton("Screen-time history",()->showScreenTimeTracker());LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,dp(42));stp.topMargin=dp(10);screen.addView(viewTime,stp);progressContent.addView(screen);
    }

    private void buildOfficePage() {
        pageHeading(pageOffice,"Time ledger","Office & commute","One-tap attendance, commute sessions and weekly totals. Presence is separate from timetable completion.");
        officePageContent=vertical();pageOffice.addView(officePageContent);renderOfficePage();
    }
    private void renderOfficePage() {
        if(officePageContent==null)return;officePageContent.removeAllViews();JSONObject day=getOfficeDay(todayKey(),false);long now=System.currentTimeMillis();
        long officeMs=officeDuration(day,now),commuteMs=commuteDuration(day,now);boolean inside=hasOpenOfficeSession(day),travelling=hasOpenCommute(day);
        LinearLayout status=surfaceCard();status.addView(text(inside?"CURRENTLY AT THE OFFICE":travelling?"COMMUTE IN PROGRESS":"OFFICE STATUS",11,color("#6BD0A6"),true));
        status.addView(text(inside?"Clocked in":travelling?"Commute running":"Not clocked in",24,color("#F2F7F3"),true));
        status.addView(text("Office total  "+fmtMillis(officeMs)+"\nCommute total  "+fmtMillis(commuteMs),13,color("#9AABA0"),false));
        Button check=primaryButton(inside?"Check out":"Check in",()->{toggleOfficeAttendance();renderOfficePage();});LinearLayout.LayoutParams checkLp=new LinearLayout.LayoutParams(-1,dp(48));checkLp.topMargin=dp(12);status.addView(check,checkLp);
        Button travel=secondaryButton(travelling?"Finish commute":"Start commute",()->{toggleCommute();renderOfficePage();});LinearLayout.LayoutParams travelLp=new LinearLayout.LayoutParams(-1,dp(42));travelLp.topMargin=dp(8);status.addView(travel,travelLp);pageOffice.addView(status);
        LinearLayout week=surfaceCard();week.addView(text("THIS WEEK",11,color("#D5B56E"),true));long total=0;int count=0;Calendar cursor=Calendar.getInstance();int dow=cursor.get(Calendar.DAY_OF_WEEK);cursor.add(Calendar.DAY_OF_YEAR,-(dow==Calendar.SUNDAY?6:dow-Calendar.MONDAY));
        for(int i=0;i<7;i++){String key=keyForDate(cursor.getTime());JSONObject log=getOfficeDay(key,false);long value=officeDuration(log,key.equals(todayKey())?now:cursor.getTimeInMillis());if(value>0){total+=value;count++;}cursor.add(Calendar.DAY_OF_YEAR,1);}
        week.addView(text(fmtMillis(total)+" logged across "+count+" office days",18,color("#F2F7F3"),true));week.addView(secondaryButton("Attendance history & corrections",()->showAttendanceHistory()));pageOffice.addView(week);
        LinearLayout note=surfaceCard();note.addView(text("No inferred attendance",14,color("#F2F7F3"),true));note.addView(text("Use Check in/out for presence and Start/finish commute for travel. Editing a timetable never fabricates office hours.",12,color("#9AABA0"),false));pageOffice.addView(note);
    }

    private void buildMorePage() {
        pageHeading(pageMore,"Tools & preferences","More","Configuration and occasional actions stay out of your daily execution flow.");
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
        addMoreRow("Syllabus & interview roadmap","Topic completion, targets and pace","↗",()->showSyllabusTracker());
        addMoreRow("Screen-time history","Daily totals retained on this device","↗",()->showScreenTimeTracker());
        addMoreRow("Edit WFH routine","Change the reusable work-from-home template","↗",()->openTemplateEditor("wfh"));
        addMoreRow("Edit office routine","Change the reusable office-day template","↗",()->openTemplateEditor("office"));
        addMoreRow("Edit weekend routine","Change the reusable weekend template","↗",()->openTemplateEditor("weekend"));
        addMoreRow("Precise widget timing","Schedule-boundary alarm setting","↗",()->enablePreciseLiveWidgetUpdates());
        addMoreRow("Attendance history","Review and correct office hours","↗",()->showAttendanceHistory());
        addMoreRow("Export backup","Save plans, attendance, syllabus and screen-time history","↗",()->requestExportBackup());
        addMoreRow("Import backup","Restore a previously exported DayFlow backup","↗",()->requestImportBackup());
        addMoreRow("Diagnostics","Inspect app and widget event logs","↗",()->showDiagnostics());
        addMoreRow("Clear selected plan","Remove blocks from the selected date","↗",()->clearDay());
    }

    private void buildSyllabusPage() {
        pageHeading(pageSyllabus,"Learning ledger","Syllabus & interview prep","Track completed topics. UPSC CSE and software interviews keep separate progress and deadlines.");
        LinearLayout actions=horizontal();actions.addView(secondaryButton("‹ Progress",()->switchPage("progress")),new LinearLayout.LayoutParams(dp(105),dp(40)));
        Button target=secondaryButton("Target date",()->chooseSyllabusTargetDate());LinearLayout.LayoutParams targetLp=new LinearLayout.LayoutParams(0,dp(40),1);targetLp.leftMargin=dp(7);actions.addView(target,targetLp);pageSyllabus.addView(actions);
        syllabusSummaryView=text("",14,color("#D6E4DA"),true);syllabusSummaryView.setPadding(dp(14),dp(12),dp(14),dp(12));syllabusSummaryView.setBackground(bg("#18231E",16));
        LinearLayout.LayoutParams sumLp=new LinearLayout.LayoutParams(-1,-2);sumLp.topMargin=dp(12);sumLp.bottomMargin=dp(8);pageSyllabus.addView(syllabusSummaryView,sumLp);
        HorizontalScrollView overviewScroll=new HorizontalScrollView(this);overviewScroll.setHorizontalScrollBarEnabled(false);
        syllabusBreakdown=horizontal();overviewScroll.addView(syllabusBreakdown);
        LinearLayout.LayoutParams breakdownLp=new LinearLayout.LayoutParams(-1,-2);breakdownLp.bottomMargin=dp(12);pageSyllabus.addView(overviewScroll,breakdownLp);
        LinearLayout topicActions=horizontal();
        topicActions.addView(primaryButton("＋ Add topic",()->editSyllabusTopic(null)),new LinearLayout.LayoutParams(0,dp(42),1));
        Button imp=secondaryButton("Import list",()->importSyllabusTopics());LinearLayout.LayoutParams impLp=new LinearLayout.LayoutParams(0,dp(42),1);impLp.leftMargin=dp(6);topicActions.addView(imp,impLp);
        Button starter=secondaryButton("Load syllabus",()->loadStarterSyllabus(true));LinearLayout.LayoutParams seedLp=new LinearLayout.LayoutParams(0,dp(42),1);seedLp.leftMargin=dp(6);topicActions.addView(starter,seedLp);pageSyllabus.addView(topicActions);
        syllabusSearch=new EditText(this);syllabusSearch.setSingleLine(true);syllabusSearch.setTextColor(color("#F2F7F3"));syllabusSearch.setHintTextColor(color("#82948A"));syllabusSearch.setHint("Search topics, papers or subjects");syllabusSearch.setText(syllabusQuery);syllabusSearch.setPadding(dp(12),0,dp(12),0);syllabusSearch.setBackground(bg("#18231E",12));
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(-1,dp(44));searchLp.topMargin=dp(10);searchLp.bottomMargin=dp(8);pageSyllabus.addView(syllabusSearch,searchLp);
        syllabusSearch.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){} public void onTextChanged(CharSequence s,int st,int before,int count){syllabusQuery=s.toString();refreshSyllabusRows();} public void afterTextChanged(Editable e){}});
        HorizontalScrollView fs=new HorizontalScrollView(this);fs.setHorizontalScrollBarEnabled(false);LinearLayout filters=horizontal();ArrayList<String> subjects=new ArrayList<>();subjects.add("All");subjects.addAll(Arrays.asList(SYLLABUS_SUBJECTS));
        for(String subject:subjects){Button filter=chip(subject);boolean selected=subject.equals(syllabusFilter);filter.setTextColor(color(selected?"#F2F7F3":"#B7C7BC"));filter.setBackground(bg(selected?"#245B45":"#1B2621",12));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,dp(34));fp.rightMargin=dp(6);filters.addView(filter,fp);filter.setOnClickListener(v->{syllabusFilter=subject;refreshSyllabusRows();});}
        fs.addView(filters);LinearLayout.LayoutParams filterLp=new LinearLayout.LayoutParams(-1,dp(38));filterLp.bottomMargin=dp(8);pageSyllabus.addView(fs,filterLp);
        syllabusList=vertical();pageSyllabus.addView(syllabusList);refreshSyllabusRows();
    }
    private void refreshSyllabusRows() {
        if(syllabusList==null||syllabusSummaryView==null)return;
        ArrayList<SyllabusTopic> topics=readSyllabusTopics();
        syllabusSummaryView.setText(syllabusOverview(topics));
        renderSyllabusBreakdown(topics);
        syllabusList.removeAllViews();
        String query=syllabusQuery.trim().toLowerCase(Locale.US);int shown=0;
        for(SyllabusTopic topic:topics){
            if(!"All".equals(syllabusFilter)&&!syllabusFilter.equals(topic.subject))continue;
            if(!query.isEmpty()&&!topic.title.toLowerCase(Locale.US).contains(query)&&!topic.subject.toLowerCase(Locale.US).contains(query))continue;
            shown++;LinearLayout row=horizontal();row.setGravity(Gravity.TOP);row.setPadding(dp(11),dp(10),dp(7),dp(10));row.setBackground(bg(topic.done?"#172B22":"#18231E",14));
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.bottomMargin=dp(7);syllabusList.addView(row,rowLp);
            Button toggle=actionButton(topic.done?"✓":"○",color(topic.done?"#102017":"#A8BAAE"),topic.done?"#70D6A5":"#283A30");row.addView(toggle,new LinearLayout.LayoutParams(dp(38),dp(38)));
            LinearLayout details=vertical();details.setPadding(dp(9),0,dp(3),0);details.addView(text(topic.title,13,color("#F2F7F3"),true));details.addView(text(topic.subject+(topic.done?" • Complete":" • Pending"),10,color("#9AABA0"),false));row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            Button more=secondaryButton("⋮",()->showSyllabusTopicActions(topic));row.addView(more,new LinearLayout.LayoutParams(dp(36),dp(36)));
            toggle.setOnClickListener(v->{topic.done=!topic.done;topic.completedAt=topic.done?System.currentTimeMillis():0L;persistSyllabusTopics(readSyllabusTopicsWith(topic));});
            details.setOnClickListener(v->{syllabusFilter=topic.subject;syllabusQuery=topic.title;switchPage("syllabus");});
        }
        if(shown==0)syllabusList.addView(text("No topics match these filters. Try All or clear the search.",13,color("#9AABA0"),false));
    }
    private String syllabusOverview(ArrayList<SyllabusTopic> topics) {
        int done=0,cseTotal=0,cseDone=0,interviewTotal=0,interviewDone=0;
        for(SyllabusTopic topic:topics){
            boolean interview="Software Interview Prep".equals(topic.subject);
            if(interview){interviewTotal++;if(topic.done)interviewDone++;}
            else{cseTotal++;if(topic.done)cseDone++;}
            if(topic.done)done++;
        }
        return "OVERALL  "+done+" / "+topics.size()+" complete  •  "+(topics.isEmpty()?0:Math.round(done*100f/topics.size()))+"%"
                +"\nUPSC CSE  "+cseDone+" / "+cseTotal+"     •     SOFTWARE INTERVIEWS  "+interviewDone+" / "+interviewTotal;
    }

    private void renderSyllabusBreakdown(ArrayList<SyllabusTopic> topics) {
        if(syllabusBreakdown==null)return;
        syllabusBreakdown.removeAllViews();
        for(String subject:SYLLABUS_SUBJECTS){
            int total=0,done=0;
            for(SyllabusTopic topic:topics)if(subject.equals(topic.subject)){total++;if(topic.done)done++;}
            LinearLayout card=surfaceCard();card.setPadding(dp(12),dp(11),dp(12),dp(11));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(164),dp(104));lp.rightMargin=dp(7);card.setLayoutParams(lp);
            TextView label=text(subject,11,color("#B7C7BC"),true);label.setMaxLines(2);label.setMinHeight(dp(28));card.addView(label);
            card.addView(text(done+"/"+total,20,color("#F2F7F3"),true));
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,total));bar.setProgress(done);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color("#40B58A")));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(5));bp.topMargin=dp(5);card.addView(bar,bp);
            card.setOnClickListener(v->{syllabusFilter=subject;syllabusQuery="";if(syllabusSearch!=null)syllabusSearch.setText("");refreshSyllabusRows();});
            syllabusBreakdown.addView(card);
        }
        LinearLayout roadmap=surfaceCard();roadmap.setPadding(dp(12),dp(11),dp(12),dp(11));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(210),dp(104));rp.rightMargin=dp(7);roadmap.setLayoutParams(rp);
        roadmap.addView(text("ROADMAP",11,color("#D5B56E"),true));
        String start=getPrefs().getString(INTERVIEW_PLAN_START_KEY,todayKey());
        Date sd=parseDateKey(start);Calendar six=Calendar.getInstance();if(sd!=null)six.setTime(sd);six.add(Calendar.MONTH,6);
        roadmap.addView(text("CSE checkpoint  1 Apr 2027\nCSE target  1 Jan 2028\nTier 3/4  "+keyForDate(six.getTime())+"\nTier 1/2  "+getPrefs().getString(INTERVIEW_TARGET_KEY,"2027-07-11"),10,color("#D6E4DA"),false));
        syllabusBreakdown.addView(roadmap);
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
        String tab=activePage.equals("syllabus")||activePage.equals("screenTime")?"progress":activePage;
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
        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(44));
        rp.bottomMargin = dp(14);
        root.addView(row, rp);

        modeView = text("", 12, color("#A5B5AA"), false);
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
        card.setBackground(bg("#1B2922", 20));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.bottomMargin = dp(14);
        root.addView(card, cp);

        TextView label = text("RIGHT NOW", 11, color("#7DD6B2"), true);
        card.addView(label);

        currentTitle = text("Loading…", 24, Color.WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(5);
        card.addView(currentTitle, tp);

        LinearLayout row = horizontal();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(8);
        card.addView(row, rp);

        currentTime = text("—", 13, color("#A5B5AA"), false);
        currentCountdown = text("—", 13, color("#FFFFFF"), true);
        row.addView(currentTime, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(currentCountdown);

        Button markDone = actionButton("Mark done", Color.WHITE, "#40B58A");
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(-1, dp(46));
        doneLp.topMargin = dp(16);
        card.addView(markDone, doneLp);
        markDone.setTag("currentDone");
        markDone.setOnClickListener(v -> completeCurrent());

        focusAction = actionButton("Start focus", color("#D6E4DA"), "#263A31");
        LinearLayout.LayoutParams focusLp = new LinearLayout.LayoutParams(-1, dp(40));
        focusLp.topMargin = dp(8);
        card.addView(focusAction, focusLp);
        focusAction.setTag("focusAction");
        focusAction.setOnClickListener(v -> toggleFocusCurrent());

        Button recover = actionButton("Catch up", color("#D2F1E2"), "#27513E");
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
        else if ("syllabus".equals(activePage)) refreshSyllabusRows();
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
            surface = "#111A16"; border = "#40976D";
        } else if ("skipped".equals(b.status)) {
            surface = "#171214"; border = "#5C3038";
        } else if (current) {
            surface = "#254C3C"; border = "#40B58A";
        } else {
            surface = "#1B2621"; border = "#2D3C34";
        }

        GradientDrawable cardBg = bg(surface, 16);
        cardBg.setStroke(dp(current ? 2 : 1), color(border));
        card.setBackground(cardBg);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1);
        cardLp.leftMargin = dp(9);
        row.addView(card, cardLp);

        LinearLayout details = vertical();
        TextView title = text(b.title, 15, Color.WHITE, true);
        TextView meta = text(blockBehaviorLabel(b) + " • " + fmt(mins(b.end) - mins(b.start)), 11, color("#A5B5AA"), false);
        details.addView(title);
        details.addView(meta);

        if (current) {
            TextView state = text("IN PROGRESS", 10, color("#7DD6B2"), true);
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

    private void openManageDialog() { switchPage("more"); }

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
        String interviewStart = getPrefs().getString(INTERVIEW_PLAN_START_KEY, todayKey());
        String interviewTarget = getPrefs().getString(INTERVIEW_TARGET_KEY, "2027-07-11");
        Calendar interviewSixMonth = Calendar.getInstance();
        Date interviewStartDate = parseDateKey(interviewStart);
        if (interviewStartDate != null) interviewSixMonth.setTime(interviewStartDate);
        interviewSixMonth.add(Calendar.MONTH, 6);
        String sixMonthDate = keyForDate(interviewSixMonth.getTime());
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
        int done = 0, recentlyDone = 0, cseDone = 0, cseTotal = 0, interviewDone = 0, interviewTotal = 0;
        long weekAgo = System.currentTimeMillis() - 7L * 86400000L;
        LinkedHashMap<String, int[]> counts = new LinkedHashMap<>();
        for (SyllabusTopic topic : topics) {
            int[] values = counts.get(topic.subject);
            if (values == null) {
                values = new int[2];
                counts.put(topic.subject, values);
            }
            values[1]++;
            boolean softwareInterview = "Software Interview Prep".equals(topic.subject);
            if (softwareInterview) interviewTotal++; else cseTotal++;
            if (topic.done) {
                values[0]++;
                done++;
                if (softwareInterview) interviewDone++; else cseDone++;
                if (topic.completedAt >= weekAgo && !softwareInterview) recentlyDone++;
            }
        }
        int cseRemaining = Math.max(0, cseTotal - cseDone);
        int interviewRemaining = Math.max(0, interviewTotal - interviewDone);
        StringBuilder summary = new StringBuilder();
        summary.append("Overall: ").append(done).append("/").append(topics.size())
                .append(" complete (").append(topics.isEmpty() ? 0 : Math.round(done * 100f / topics.size())).append("%)")
                .append("\n\nCSE syllabus target: ").append(target).append(" • ").append(daysLeft).append(" days left");
        if (cseTotal > 0 && daysLeft > 0) {
            summary.append("\nCSE pace needed: ").append(String.format(Locale.US, "%.2f", cseRemaining / (double) daysLeft))
                    .append(" topics/day (software interview topics excluded)");
            if (recentlyDone > 0) {
                double perDay = recentlyDone / 7.0;
                long projectedDays = (long) Math.ceil(cseRemaining / perDay);
                Calendar forecast = Calendar.getInstance();
                forecast.add(Calendar.DAY_OF_YEAR, (int) Math.min(Integer.MAX_VALUE, projectedDays));
                summary.append("\nRecent CSE pace: ").append(recentlyDone).append(" topics/week")
                        .append(" • projected finish ").append(new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(forecast.getTime()));
            } else {
                summary.append("\nCSE finish forecast: not enough completion history yet.");
            }
        }
        summary.append("\n\nSoftware interview prep: ").append(interviewDone).append("/").append(interviewTotal)
                .append(" complete")
                .append("\n6-month Tier 3/4 readiness checkpoint: ").append(sixMonthDate)
                .append("\n9-month Tier 1/2 strong-hire target: ").append(interviewTarget);
        if (interviewTotal > 0) {
            summary.append("\nInterview topics remaining: ").append(interviewRemaining);
        }
        summary.append("\n\nRoutine anchors from your saved plan:")
                .append("\n• WFH: protect GS, Maths Optional and the 08:00–09:30 interview-prep slot.")
                .append("\n• Office: Maths Optional is after work, not in the morning.")
                .append("\n• Weekends: 6 hours CSE — 3 hours GS + 3 hours Maths Optional.")
                .append("\n• Interview target: Tier 3/4 readiness around month 6; Tier 1/2 strong-hire target around month 9.");
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
        lines.setHintTextColor(color("#82948A"));
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
            {"Software Interview Prep", "Roadmap milestone: after 6 months, target Tier 3/4 interview readiness"},
            {"Software Interview Prep", "Interview-prep time commitment: use the 12–13 hours in the saved prep plan; log actual sessions with Focus"},
            {"Software Interview Prep", "Roadmap milestone: after 9 months, target strong-hire readiness for Tier 1/2"},
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
            all.add(new SyllabusTopic(System.currentTimeMillis() + added, row[0], row[1], false, 0L));
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

    private void showSyllabusTracker() { switchPage("syllabus"); }

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
                11, color("#91A197"), false);
        hint.setPadding(0, 0, 0, dp(10));
        list.addView(hint);

        Button add = actionButton("＋  Add template block", Color.WHITE, "#40B58A");
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
            row.setBackground(bg("#1B2621", 14));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(7);
            list.addView(row, rowLp);

            LinearLayout details = vertical();
            details.addView(text(b.title, 14, Color.WHITE, true));
            details.addView(text(time12(b.start) + " – " + time12(b.end) + "  •  " + blockBehaviorLabel(b),
                    11, color("#A5B5AA"), false));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

            Button del = actionButton("×", color("#FFC5C9"), "#3A2729");
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
                row.setBackground(bg(i == 0 ? "#214036" : "#1B2621", 14));
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
                info.addView(text(detail, 10, color("#91A197"), false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

                TextView marker = text((day != null && office > 0) ? "●" : "○",
                        17, (day != null && office > 0) ? color("#6FD6A3") : color("#47584D"), true);
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
                    TextView t = text(value, 12, color("#D6E1D8"), false);
                    t.setPadding(0, dp(10), 0, dp(2));
                    content.addView(t);
                }
            }

            long ciS = day.optLong("commuteInStart", 0);
            long ciE = day.optLong("commuteInEnd", 0);
            long coS = day.optLong("commuteOutStart", 0);
            long coE = day.optLong("commuteOutEnd", 0);
            if (ciS > 0 || ciE > 0) content.addView(text("To office: " + clock(ciS) + " → " + (ciE > 0 ? clock(ciE) : "Active"), 12, color("#D6E1D8"), false));
            if (coS > 0 || coE > 0) content.addView(text("Home: " + clock(coS) + " → " + (coE > 0 ? clock(coE) : "Active"), 12, color("#D6E1D8"), false));

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
        TextView view = text(log, 11, color("#DEE8E0"), false);
        view.setTextIsSelectable(true);
        view.setPadding(dp(16), dp(12), dp(16), dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(color("#111A16"));
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
            TextView note = text("Correct the recorded times. DayFlow stores the exact date + time.", 11, color("#91A197"), false);
            content.addView(note);

            Button inBtn = actionButton("Office in  •  " + (session.optLong("in", 0) > 0 ? clock(session.optLong("in", 0)) : "Not set"), Color.WHITE, "#263A31");
            Button outBtn = actionButton("Office out  •  " + (session.optLong("out", 0) > 0 ? clock(session.optLong("out", 0)) : "Not set"), Color.WHITE, "#263A31");
            Button ciBtn = actionButton("Commute to office  •  " + timeLabel(day.optLong("commuteInStart", 0)) + " → " + timeLabel(day.optLong("commuteInEnd", 0)), Color.WHITE, "#263A31");
            Button coBtn = actionButton("Commute home  •  " + timeLabel(day.optLong("commuteOutStart", 0)) + " → " + timeLabel(day.optLong("commuteOutEnd", 0)), Color.WHITE, "#263A31");
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
