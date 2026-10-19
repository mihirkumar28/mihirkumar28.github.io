package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Offline reading copies of the user's study and career plans, bundled with the APK. */
public class PlanDocumentsActivity extends Activity {
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String s, int size, int c, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(c);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL); return t;
    }
    private GradientDrawable bg(String hex) {
        GradientDrawable d = new GradientDrawable(); d.setColor(Color.parseColor(hex));
        d.setCornerRadius(dp(15)); d.setStroke(dp(1), Color.parseColor("#DDE6DE")); return d;
    }
    private void open(String title, String filename, String body) {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this); box.setOrientation(1);
        box.setPadding(dp(20), dp(18), dp(20), dp(24)); box.setBackgroundColor(Color.WHITE);
        box.addView(label(title, 22, Color.parseColor("#18372E"), true));
        TextView f = label(filename, 11, Color.parseColor("#687A70"), false);
        f.setPadding(0, dp(7), 0, dp(16)); box.addView(f);
        TextView copy = label(body, 14, Color.parseColor("#25372E"), false);
        copy.setLineSpacing(dp(4), 1.05f); box.addView(copy); sc.addView(box);
        new AlertDialog.Builder(this).setView(sc).setPositiveButton("Close", null).show();
    }
    private void add(LinearLayout parent, String title, String detail, String filename, String body) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(1);
        card.setPadding(dp(15), dp(14), dp(15), dp(13)); card.setBackground(bg("#FFFFFF"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.bottomMargin = dp(10); parent.addView(card, cp);
        card.addView(label(title, 16, Color.parseColor("#18372E"), true));
        TextView sub = label(detail, 12, Color.parseColor("#687A70"), false);
        sub.setPadding(0, dp(5), 0, dp(12)); sub.setLineSpacing(dp(2), 1f); card.addView(sub);
        Button b = new Button(this); b.setText("Read offline copy"); b.setAllCaps(false);
        b.setTextSize(13); b.setTextColor(Color.WHITE); b.setBackground(bg("#23845F"));
        b.setOnClickListener(v -> open(title, filename, body)); card.addView(b, new LinearLayout.LayoutParams(-1, dp(44)));
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0D1512"));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this); page.setOrientation(1);
        page.setPadding(dp(18), dp(22), dp(18), dp(24)); page.setBackgroundColor(Color.parseColor("#0D1512"));
        page.addView(label("DAYFLOW  /  OFFLINE LIBRARY", 10, Color.parseColor("#65C5A0"), true));
        TextView h = label("Plans & Documents", 28, Color.parseColor("#F2F7F3"), true);
        h.setPadding(0, dp(4), 0, dp(7)); page.addView(h);
        TextView note = label("Bundled reading copies work without internet. These are concise offline copies, not the original PDF layouts.", 13, Color.parseColor("#9AABA0"), false);
        note.setLineSpacing(dp(3), 1f); note.setPadding(0, 0, 0, dp(18)); page.addView(note);

        add(page, "Unified Master Execution Plan", "October 2026 – January 2028 · Combined execution system",
            "Unified_Master_Execution_Plan_Oct2026_to_Jan2028_REVISED.pdf",
            "MASTER EXECUTION PLAN | OCT 2026 – JAN 2028\n\nPURPOSE\nCoordinate UPSC CSE preparation, software-career progression, health, and DayFlow in one execution system. Review progress regularly and adjust based on evidence rather than adding complexity.\n\nUPSC CSE\nKeep General Studies and Maths Optional as distinct study tracks. Use monthly subject targets, practice questions, revision, and progress tracking. The dedicated GS monthly plan is the source for month-by-month subject assignments.\n\nSOFTWARE CAREER\nPrepare for an engineering-management transition using a staged 6–9 month plan. Build interview readiness through system design, technical depth, leadership examples, and structured practice. Use the dedicated interview-readiness plan for the focused preparation track.\n\nHEALTH AND REVIEW\nReview weekly averages and adherence. Every four weeks review weight trend, waist, training tolerance, sleep, and relevant medical guidance. Reassess body-composition estimates every 8–12 weeks if useful, using a consistent method and allowing for measurement error. If adherence is poor, simplify the plan before making it stricter.\n\nDAYFLOW RULES\nIdentify weekday versus weekend and allow WFH/WFO switching. “Modify & Save Today’s Routine” edits only the selected date, never a reusable template. Distinguish fixed and flexible blocks. Track planned versus actual time and commute with minimal taps. Progress percentages must use the same source of truth as the detailed progress view. During March–May 2027, prioritize correctness blockers over cosmetic redesigns or new features.\n\nREVIEW CADENCE\nWeekly: review 7-day weight average, exercise, adherence, hunger/energy, and study load. Every four weeks: review weight trend, waist, training tolerance, sleep, and medical guidance. Every 8–12 weeks: review body-composition estimates if useful.\n\nThis is a concise offline reading copy. The original master PDF contains the complete tables, monthly detail, and references.");

        add(page, "UPSC 2027 Prelims GS Monthly Plan", "Monthly General Studies subject execution plan",
            "UPSC_2027_Prelims_GS_Monthly_Plan.pdf",
            "UPSC 2027 PRELIMS | GENERAL STUDIES MONTHLY PLAN\n\nOCTOBER 2026 — POLITY\nPrimary source: Vajiram Polity I + II. Listed source volume: 584 pages. Complete the main polity source once and use previous-year questions immediately after major constitutional topics.\n\nNOVEMBER 2026 — HISTORY\nPrimary source: Vajiram Modern (296 pages), Ancient (140 pages), and Medieval (103 pages); 539 pages total. Avoid over-noting. Build chronology and themes, then use PYQs to distinguish examinable material from low-return detail.\n\nDECEMBER 2026 — ENVIRONMENT + ART & CULTURE\nPrimary source: Vajiram Environment (307 pages) and NEXT Art & Culture (184 pages); 491 pages total. Compress factual material into short revision sheets.\n\nEXECUTION PRINCIPLES\nUse the source plan’s monthly assignments as the schedule of record. Pair reading with PYQs, keep notes concise, and reserve time for revision and factual consolidation. Do not silently substitute a different source or monthly subject sequence.\n\nThis offline copy preserves the opening monthly entries available in the source extract. The original PDF contains the remaining monthly assignments and detailed execution notes.");

        add(page, "2027 Job Switch — EM 6–9 Month Master Plan", "Career transition roadmap and staged preparation",
            "2027_Job_Switch_EM_6_9_Month_Master_Plan.pdf",
            "2027 JOB SWITCH | ENGINEERING MANAGEMENT | 6–9 MONTH MASTER PLAN\n\nOBJECTIVE\nPrepare for a software engineering management opportunity through a staged plan while keeping UPSC preparation and daily execution sustainable. The master plan coordinates career preparation with the wider October 2026–January 2028 execution timeline.\n\nSTAGED TARGET\nAround month 6: begin interviewing for Tier 3 and Tier 4 target companies to measure readiness and identify gaps. Around month 9: aim to be a stronger candidate for Tier 1 and Tier 2 targets.\n\nPREPARATION TRACKS\nBuild evidence for technical depth, system design, architecture decisions, difficult debugging, production ownership, project execution, people leadership, and behavioral interviews. Use practice interviews and review weak areas rather than relying on preparation hours alone.\n\nALIGNMENT\nKeep the company shortlist aligned with compensation goals and preparation constraints. Evaluate roles against actual scope and experience; do not assume an Engineering Manager title or compensation level is guaranteed.\n\nThis is a concise offline reading copy. The original master PDF contains the full roadmap, company tiers, and detailed scheduling.");

        add(page, "2027 Job Switch — EM Interview Readiness Plan", "Focused technical, system-design, leadership and interview preparation",
            "2027_Job_Switch_6_9_Month_EM_Interview_Readiness_Plan.pdf",
            "2027 JOB SWITCH | ENGINEERING MANAGEMENT INTERVIEW READINESS\n\nPURPOSE\nA focused plan for preparing for engineering-management interviews over a 6–9 month window. Use it alongside the job-switch master plan; it is the interview-preparation track, not a replacement for the wider roadmap.\n\nCORE PREPARATION\n• System design and architecture trade-offs.\n• Technical depth and difficult debugging.\n• Production ownership, reliability, and incident reasoning.\n• Project execution, prioritization, and cross-functional collaboration.\n• Leadership, conflict resolution, feedback, and behavioral examples.\n• Mock interviews, review of gaps, and repeated practice.\n\nREADINESS CHECKPOINTS\nUse Tier 3/4 interviews as a diagnostic checkpoint around month 6. Use the evidence to close gaps before targeting Tier 1/2 roles around month 9. Assess readiness from interview performance and feedback, not calendar time alone.\n\nThis is a concise offline reading copy. Consult the original interview-readiness PDF for the complete schedule, exercises, and detailed checkpoints.");

        Button back = new Button(this); back.setText("Back to DayFlow"); back.setAllCaps(false);
        back.setTextColor(Color.WHITE); back.setBackground(bg("#23845F")); back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(48)); blp.topMargin = dp(6); page.addView(back, blp);
        scroll.addView(page); setContentView(scroll);
    }
}