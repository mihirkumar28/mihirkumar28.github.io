package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Native reader for PDF files bundled under assets/plans/. */
public class OfflinePdfViewerActivity extends Activity {
    static final String EXTRA_FILENAME = "pdfFilename";
    static final String EXTRA_TITLE = "pdfTitle";
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private Bitmap pageBitmap;
    private ImageView pageImage;
    private TextView pageLabel;
    private Button previous;
    private Button next;
    private int pageIndex;

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(android.graphics.Typeface.DEFAULT,
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return t;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 21, 18));
        getWindow().setNavigationBarColor(Color.rgb(13, 21, 18));
        String filename = getIntent().getStringExtra(EXTRA_FILENAME);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (filename == null || filename.trim().isEmpty()) {
            showError("Document filename is missing.");
            return;
        }
        try {
            String safeName = new File(filename).getName();
            if (!safeName.equals(filename)) throw new IllegalArgumentException("Invalid PDF filename.");
            File local = new File(getCacheDir(), safeName);
            boolean found = false;
            try (java.util.zip.ZipInputStream in =
                         new java.util.zip.ZipInputStream(getAssets().open("plan_pdfs.zip"));
                 FileOutputStream out = new FileOutputStream(local, false)) {
                java.util.zip.ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    if (safeName.equals(entry.getName())) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                        found = true;
                        in.closeEntry();
                        break;
                    }
                    in.closeEntry();
                }
            }
            if (!found || local.length() < 5) throw new java.io.IOException("PDF not found in bundled archive: " + safeName);
            try (InputStream check = new java.io.FileInputStream(local)) {
                byte[] header = new byte[5];
                if (check.read(header) != 5 || !"%PDF-".equals(new String(header, java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw new java.io.IOException("Bundled document is not a valid PDF: " + safeName);
                }
            }
            descriptor = ParcelFileDescriptor.open(local, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            buildScreen(title == null ? filename : title);
            showPage(0);
        } catch (Exception e) {
            showError("Could not open the bundled PDF. Please check that the latest DayFlow build is installed.\n"
                    + e.getMessage());
        }
    }

    private void buildScreen(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(8));
        root.setBackgroundColor(Color.rgb(13, 21, 18));
        root.addView(label(title, 18, Color.rgb(242, 247, 243), true));
        pageLabel = label("Page —", 12, Color.rgb(160, 180, 168), false);
        pageLabel.setPadding(0, dp(4), 0, dp(8));
        root.addView(pageLabel);

        LinearLayout bar = new LinearLayout(this);
        previous = new Button(this);
        previous.setText("Previous");
        previous.setAllCaps(false);
        next = new Button(this);
        next.setText("Next");
        next.setAllCaps(false);
        bar.addView(previous, new LinearLayout.LayoutParams(0, dp(44), 1f));
        bar.addView(next, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        pageImage = new ImageView(this);
        pageImage.setAdjustViewBounds(true);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageImage.setBackgroundColor(Color.WHITE);
        scroll.addView(pageImage, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        previous.setOnClickListener(v -> showPage(pageIndex - 1));
        next.setOnClickListener(v -> showPage(pageIndex + 1));
        setContentView(root);
    }

    private void showPage(int index) {
        if (renderer == null || index < 0 || index >= renderer.getPageCount()) return;
        PdfRenderer.Page page = null;
        Bitmap fresh = null;
        try {
            page = renderer.openPage(index);
            int width = Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(28));
            float scale = (float) width / page.getWidth();
            fresh = Bitmap.createBitmap(width,
                    Math.max(1, Math.round(page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
            page.render(fresh, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            Bitmap old = pageBitmap;
            pageBitmap = fresh;
            fresh = null;
            pageImage.setImageBitmap(pageBitmap);
            if (old != null && !old.isRecycled()) old.recycle();
            pageIndex = index;
            pageLabel.setText("Page " + (index + 1) + " of " + renderer.getPageCount());
            previous.setEnabled(index > 0);
            next.setEnabled(index < renderer.getPageCount() - 1);
        } catch (Exception e) {
            showError("Could not render PDF page.\n" + e.getMessage());
        } finally {
            if (page != null) page.close();
            if (fresh != null) fresh.recycle();
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this).setTitle("PDF unavailable").setMessage(message)
                .setPositiveButton("Close", (dialog, which) -> finish()).show();
    }

    @Override protected void onDestroy() {
        if (pageBitmap != null && !pageBitmap.isRecycled()) pageBitmap.recycle();
        if (renderer != null) renderer.close();
        if (descriptor != null) {
            try { descriptor.close(); } catch (Exception e) { android.util.Log.w("DayFlowPdf", "Close failed", e); }
        }
        super.onDestroy();
    }
}
