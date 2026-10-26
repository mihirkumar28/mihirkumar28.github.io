package com.mihirkumar.dayflow;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Opens the official NIDDK Body Weight Planner as a first-class in-app screen.
 *
 * This intentionally loads the NIDDK-hosted calculator instead of recreating
 * the scientific model or presenting a simplified estimate as equivalent.
 * An internet connection is required.
 */
public final class NiddkBodyWeightPlannerActivity extends Activity {
    private static final String PLANNER_URL = "https://www.niddk.nih.gov/bwp";
    private static final int INK = Color.rgb(24, 55, 46);
    private static final int MUTED = Color.rgb(104, 122, 112);
    private static final int ACCENT = Color.rgb(40, 107, 85);
    private static final int PANEL = Color.rgb(234, 240, 233);

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout webContainer;
    private LinearLayout errorPanel;
    private TextView errorTitle;
    private TextView errorDetail;
    private Button retryButton;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int background, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private TextView label(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 21, 18));
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        buildScreen();
        configureWebView();
        if (state != null && webView.restoreState(state) != null) {
            // Keep the page and its navigation history across a configuration change.
        } else {
            webView.loadUrl(PLANNER_URL);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(4), dp(8), dp(4));
        toolbar.setBackgroundColor(Color.rgb(13, 21, 18));

        TextView back = label("‹", 30, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setMinWidth(dp(48));
        back.setMinHeight(dp(48));
        back.setOnClickListener(v -> goBackOrFinish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = label("NIDDK Body Weight Planner", 16, Color.WHITE, true);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.leftMargin = dp(4);
        toolbar.addView(title, titleLp);

        TextView openBrowser = label("↗", 20, Color.WHITE, true);
        openBrowser.setGravity(Gravity.CENTER);
        openBrowser.setContentDescription("Open official NIDDK calculator in browser");
        openBrowser.setMinWidth(dp(44));
        openBrowser.setMinHeight(dp(48));
        openBrowser.setOnClickListener(v -> openInBrowser());
        toolbar.addView(openBrowser, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(14), dp(9), dp(14), dp(9));
        info.setBackgroundColor(PANEL);
        info.addView(label("OFFICIAL NIDDK TOOL  ·  INTERNET REQUIRED", 10, ACCENT, true));
        TextView detail = label(
                "Enter your starting measurements, activity level and goal date on the NIDDK page. DayFlow does not copy those entries into its local planner.",
                11, INK, false);
        detail.setPadding(0, dp(4), 0, 0);
        detail.setLineSpacing(dp(1), 1f);
        info.addView(detail);
        root.addView(info, new LinearLayout.LayoutParams(-1, -2));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setIndeterminate(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
            progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(221, 230, 222)));
        }
        root.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(3)));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.WHITE);
        root.addView(webContainer, new LinearLayout.LayoutParams(-1, 0, 1f));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webContainer.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(28), dp(24), dp(28), dp(24));
        errorPanel.setBackgroundColor(Color.WHITE);
        errorPanel.setVisibility(View.GONE);

        errorTitle = label("Couldn't load the NIDDK calculator", 18, INK, true);
        errorTitle.setGravity(Gravity.CENTER);
        errorPanel.addView(errorTitle, new LinearLayout.LayoutParams(-1, -2));

        errorDetail = label("Check your internet connection and try again.", 13, MUTED, false);
        errorDetail.setGravity(Gravity.CENTER);
        errorDetail.setPadding(0, dp(8), 0, dp(16));
        errorPanel.addView(errorDetail, new LinearLayout.LayoutParams(-1, -2));

        retryButton = new Button(this);
        retryButton.setText("Retry");
        retryButton.setAllCaps(false);
        retryButton.setMinHeight(dp(48));
        retryButton.setOnClickListener(v -> {
            hideError();
            webView.reload();
        });
        errorPanel.addView(retryButton, new LinearLayout.LayoutParams(-1, dp(48)));
        webContainer.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setSafeBrowsingEnabled(true);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                hideError();
                progressBar.setProgress(0);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setProgress(100);
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    showError("This link can't be opened on this device.",
                            "Return to the calculator or open the NIDDK website in your browser.");
                }
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    showError("This link can't be opened on this device.",
                            "Return to the calculator or open the NIDDK website in your browser.");
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showError("Couldn't load the NIDDK calculator",
                            "Check your internet connection and try again. The calculator is hosted by NIDDK.");
                }
            }
        });
    }

    private void showError(String title, String detail) {
        errorTitle.setText(title);
        errorDetail.setText(detail);
        errorPanel.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    private void showError(String detail) {
        showError("Couldn't load the NIDDK calculator", detail);
    }

    private void hideError() {
        if (errorPanel != null) errorPanel.setVisibility(View.GONE);
    }

    private void openInBrowser() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PLANNER_URL)));
        } catch (Exception ignored) {
            showError("No browser available",
                    "Install or enable a browser, then try opening the NIDDK calculator again.");
        }
    }

    private void goBackOrFinish() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
