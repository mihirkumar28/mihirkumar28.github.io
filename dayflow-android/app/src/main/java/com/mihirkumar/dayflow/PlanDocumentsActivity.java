package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
    private void add(LinearLayout parent, String title, String detail, String filename, String body,
                     boolean offlinePdfCopyBundled, boolean originalPdfBundled) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(1);
        card.setPadding(dp(15), dp(14), dp(15), dp(13)); card.setBackground(bg("#FFFFFF"));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.bottomMargin = dp(10); parent.addView(card, cp);
        card.addView(label(title, 16, Color.parseColor("#18372E"), true));
        TextView sub = label(detail, 12, Color.parseColor("#687A70"), false);
        sub.setPadding(0, dp(5), 0, dp(12)); sub.setLineSpacing(dp(2), 1f); card.addView(sub);
        Button b = new Button(this);
        b.setText(originalPdfBundled ? "Open original PDF"
                : (offlinePdfCopyBundled ? "Open offline PDF copy" : "Read offline copy"));
        b.setAllCaps(false);
        b.setTextSize(13); b.setTextColor(Color.WHITE); b.setBackground(bg("#23845F"));
        b.setOnClickListener(v -> {
            if (originalPdfBundled || offlinePdfCopyBundled) {
                Intent intent = new Intent(this, OfflinePdfViewerActivity.class);
                intent.putExtra(OfflinePdfViewerActivity.EXTRA_FILENAME, filename);
                intent.putExtra(OfflinePdfViewerActivity.EXTRA_TITLE, title);
                startActivity(intent);
            } else {
                open(title, filename, body);
            }
        }); card.addView(b, new LinearLayout.LayoutParams(-1, dp(44)));
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PlanCatalog.initialize(this);
        getWindow().setStatusBarColor(Color.parseColor("#0D1512"));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this); page.setOrientation(1);
        page.setPadding(dp(18), dp(22), dp(18), dp(24)); page.setBackgroundColor(Color.parseColor("#0D1512"));
        page.addView(label("DAYFLOW  /  OFFLINE LIBRARY", 10, Color.parseColor("#65C5A0"), true));
        TextView h = label("Plans & Documents", 28, Color.parseColor("#F2F7F3"), true);
        h.setPadding(0, dp(4), 0, dp(7)); page.addView(h);
        TextView note = label("Offline PDF reading copies are generated from the bundled catalog and open without a network connection. They are condensed copies, not the original source PDFs; the original-PDF milestone remains open.", 13, Color.parseColor("#9AABA0"), false);
        note.setLineSpacing(dp(3), 1f); note.setPadding(0, 0, 0, dp(18)); page.addView(note);

        for (PlanCatalog.Document document : PlanCatalog.documents()) {
            String detail = document.subtitle;
            if (document.originalPdfBundled) {
                // The original source PDF is embedded in the archive.
            } else if (document.offlinePdfCopyBundled) {
                detail += " · offline PDF reading copy";
            } else {
                detail += " · summary only";
            }
            add(page, document.title, detail, document.filename, document.content,
                    document.offlinePdfCopyBundled, document.originalPdfBundled);
        }

        Button back = new Button(this); back.setText("Back to DayFlow"); back.setAllCaps(false);
        back.setTextColor(Color.WHITE); back.setBackground(bg("#23845F")); back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(48)); blp.topMargin = dp(6); page.addView(back, blp);
        scroll.addView(page); setContentView(scroll);
    }
}