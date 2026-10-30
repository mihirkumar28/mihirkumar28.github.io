package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Optional Gemini-powered analysis. The schedule is never changed by model output. */
public class GeminiAssistantActivity extends Activity {
    private static final String DAYFLOW_PREFS = "dayflow";
    private static final String CONSENT_PREFS = "dayflow_gemini_preferences";
    private static final String CONSENT_KEY = "schedule_analysis_consent_v1";
    private static final int REQUEST_EXPORT_GEMINI_LOGS = 7908;

    private EditText apiKeyInput;
    private Button saveKeyButton;
    private Button testButton;
    private Button analyzeButton;
    private TextView connectionStatus;
    private TextView resultView;
    private boolean requestRunning;
    private final Handler requestHandler = new Handler(Looper.getMainLooper());
    private long requestGeneration;
    private Runnable requestTimeout;
    private String analysisMode = "routine";
    private String selectedRoutineDate;
    private String selectedOfficeMonth;
    private Button routineModeButton;
    private Button officeModeButton;
    private Button analysisPeriodButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(13, 21, 18));
        getWindow().setNavigationBarColor(Color.rgb(13, 21, 18));
        getWindow().getDecorView().setSystemUiVisibility(0);
        selectedRoutineDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        selectedOfficeMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        buildUi();
        refreshStatus();
        refreshAnalysisControls();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable background(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinHeight(dp(46));
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        button.setTextColor(primary ? Color.WHITE : Color.rgb(215, 231, 220));
        button.setBackground(background(primary ? Color.rgb(35, 132, 95) : Color.rgb(35, 53, 44),
                12, Color.TRANSPARENT));
        return button;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 21, 18));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        // Android 15 enforces edge-to-edge for targetSdk 35. Reserve the system bars so
        // the back link, heading, and final privacy note remain readable and scrollable.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(dp(20), dp(18) + topInset, dp(20), dp(28) + bottomInset);
            return insets;
        });
        setContentView(scroll);
        scroll.requestApplyInsets();

        TextView back = text("‹  Back to DayFlow", 14, Color.rgb(101, 197, 160), true);
        back.setPadding(0, 0, 0, dp(12));
        back.setOnClickListener(v -> finish());
        content.addView(back);

        content.addView(text("AI ASSISTANT", 10, Color.rgb(101, 197, 160), true));
        TextView title = text("Gemini for DayFlow", 27, Color.rgb(242, 247, 243), true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(5);
        content.addView(title, titleLp);

        TextView intro = text("Use Google's Gemini API to review today's saved routine. DayFlow remains responsible for the schedule and all calculated time totals; Gemini only provides suggestions.", 13, Color.rgb(171, 189, 178), false);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2);
        introLp.topMargin = dp(8);
        introLp.bottomMargin = dp(18);
        content.addView(intro, introLp);

        LinearLayout keyCard = new LinearLayout(this);
        keyCard.setOrientation(LinearLayout.VERTICAL);
        keyCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        keyCard.setBackground(background(Color.rgb(24, 35, 30), 18, Color.rgb(47, 67, 57)));
        content.addView(keyCard);

        keyCard.addView(text("1. Connect your API key", 16, Color.rgb(242, 247, 243), true));
        TextView privacy = text("Your key is encrypted on this device using Android Keystore. It is not included in the APK source or sent to GitHub. Enter it here; do not paste it into chat.", 12, Color.rgb(171, 189, 178), false);
        LinearLayout.LayoutParams privacyLp = new LinearLayout.LayoutParams(-1, -2);
        privacyLp.topMargin = dp(7);
        keyCard.addView(privacy, privacyLp);

        apiKeyInput = new EditText(this);
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setTextSize(14);
        apiKeyInput.setHint("Paste your Gemini API key");
        apiKeyInput.setHintTextColor(Color.rgb(108, 124, 114));
        apiKeyInput.setTextColor(Color.rgb(24, 55, 46));
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyInput.setBackground(background(Color.WHITE, 10, Color.rgb(221, 230, 222)));
        apiKeyInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(52));
        inputLp.topMargin = dp(12);
        keyCard.addView(apiKeyInput, inputLp);

        saveKeyButton = button("Save key securely", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(48));
        saveLp.topMargin = dp(9);
        keyCard.addView(saveKeyButton, saveLp);
        saveKeyButton.setOnClickListener(v -> saveKeyFromField());

        LinearLayout keyActions = new LinearLayout(this);
        keyActions.setOrientation(LinearLayout.HORIZONTAL);
        keyActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams keyActionsLp = new LinearLayout.LayoutParams(-1, dp(48));
        keyActionsLp.topMargin = dp(8);
        keyCard.addView(keyActions, keyActionsLp);
        testButton = button("Test connection", false);
        keyActions.addView(testButton, new LinearLayout.LayoutParams(0, -1, 1f));
        Button removeButton = button("Remove key", false);
        LinearLayout.LayoutParams removeLp = new LinearLayout.LayoutParams(0, -1, 1f);
        removeLp.leftMargin = dp(8);
        keyActions.addView(removeButton, removeLp);
        testButton.setOnClickListener(v -> testConnection());
        removeButton.setOnClickListener(v -> confirmRemoveKey());

        connectionStatus = text("", 12, Color.rgb(101, 197, 160), true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(10);
        keyCard.addView(connectionStatus, statusLp);

        Button diagnosticsButton = button("View / export Gemini diagnostic logs", false);
        LinearLayout.LayoutParams diagnosticsLp = new LinearLayout.LayoutParams(-1, dp(44));
        diagnosticsLp.topMargin = dp(10);
        keyCard.addView(diagnosticsButton, diagnosticsLp);
        diagnosticsButton.setOnClickListener(v -> showGeminiDiagnostics());

        LinearLayout analysisCard = new LinearLayout(this);
        analysisCard.setOrientation(LinearLayout.VERTICAL);
        analysisCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        analysisCard.setBackground(background(Color.rgb(24, 35, 30), 18, Color.rgb(47, 67, 57)));
        LinearLayout.LayoutParams analysisCardLp = new LinearLayout.LayoutParams(-1, -2);
        analysisCardLp.topMargin = dp(14);
        content.addView(analysisCard, analysisCardLp);

        analysisCard.addView(text("2. Choose what to analyse", 16, Color.rgb(242, 247, 243), true));
        TextView details = text("Analyse a saved routine on any date, or review your saved monthly office-presence plan and attendance separately.", 12, Color.rgb(171, 189, 178), false);
        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
        detailsLp.topMargin = dp(7);
        analysisCard.addView(details, detailsLp);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeRowLp = new LinearLayout.LayoutParams(-1, dp(46));
        modeRowLp.topMargin = dp(12);
        analysisCard.addView(modeRow, modeRowLp);
        routineModeButton = button("Daily routine", false);
        modeRow.addView(routineModeButton, new LinearLayout.LayoutParams(0, -1, 1f));
        officeModeButton = button("Office month", false);
        LinearLayout.LayoutParams officeModeLp = new LinearLayout.LayoutParams(0, -1, 1f);
        officeModeLp.leftMargin = dp(8);
        modeRow.addView(officeModeButton, officeModeLp);
        routineModeButton.setOnClickListener(v -> {
            analysisMode = "routine";
            refreshAnalysisControls();
        });
        officeModeButton.setOnClickListener(v -> {
            analysisMode = "office";
            refreshAnalysisControls();
        });

        analysisPeriodButton = button("Select date", false);
        analysisPeriodButton.setTextSize(12);
        LinearLayout.LayoutParams periodLp = new LinearLayout.LayoutParams(-1, dp(46));
        periodLp.topMargin = dp(9);
        analysisCard.addView(analysisPeriodButton, periodLp);
        analysisPeriodButton.setOnClickListener(v -> chooseAnalysisPeriod());

        analyzeButton = button("Analyse selected routine", true);
        LinearLayout.LayoutParams analyzeLp = new LinearLayout.LayoutParams(-1, dp(48));
        analyzeLp.topMargin = dp(12);
        analysisCard.addView(analyzeButton, analyzeLp);
        analyzeButton.setOnClickListener(v -> analyzeRoutine());

        resultView = text("Your analysis will appear here.", 13, Color.rgb(215, 231, 220), false);
        resultView.setTextIsSelectable(true);
        resultView.setPadding(dp(13), dp(13), dp(13), dp(13));
        resultView.setBackground(background(Color.rgb(18, 28, 23), 12, Color.rgb(47, 67, 57)));
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(-1, -2);
        resultLp.topMargin = dp(12);
        analysisCard.addView(resultView, resultLp);

        TextView dataNote = text("Privacy note: free-tier prompts and responses may be used by Google to improve its products. A daily review sends the selected date's task names, times and statuses. A monthly review sends planned office dates, per-date hours and available attendance records. Avoid storing sensitive information in task titles.", 11, Color.rgb(154, 171, 160), false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(16);
        content.addView(dataNote, noteLp);
    }

    private void refreshAnalysisControls() {
        if (analysisPeriodButton == null) return;
        boolean routine = "routine".equals(analysisMode);
        routineModeButton.setBackground(background(
                routine ? Color.rgb(35, 132, 95) : Color.rgb(35, 53, 44), 12, Color.TRANSPARENT));
        routineModeButton.setTextColor(Color.WHITE);
        officeModeButton.setBackground(background(
                routine ? Color.rgb(35, 53, 44) : Color.rgb(35, 132, 95), 12, Color.TRANSPARENT));
        officeModeButton.setTextColor(Color.WHITE);
        if (routine) {
            analysisPeriodButton.setText("Date  ·  " + GeminiScheduleFacts.formatDateLabel(selectedRoutineDate));
            analyzeButton.setText("Review selected day");
        } else {
            analysisPeriodButton.setText("Month  ·  " + GeminiScheduleFacts.formatMonthLabel(selectedOfficeMonth));
            analyzeButton.setText("Review office month");
        }
    }

    private void chooseAnalysisPeriod() {
        if ("routine".equals(analysisMode)) {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            try {
                Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedRoutineDate);
                if (date != null) calendar.setTime(date);
            } catch (Exception ignored) { }
            new android.app.DatePickerDialog(this, (view, year, month, day) -> {
                selectedRoutineDate = String.format(Locale.US, "%04d-%02d-%02d",
                        year, month + 1, day);
                refreshAnalysisControls();
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH)).show();
        } else {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            try {
                Date date = new SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedOfficeMonth);
                if (date != null) calendar.setTime(date);
            } catch (Exception ignored) { }
            new android.app.DatePickerDialog(this, (view, year, month, day) -> {
                selectedOfficeMonth = String.format(Locale.US, "%04d-%02d", year, month + 1);
                refreshAnalysisControls();
            }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), 1).show();
        }
    }

    private void refreshStatus() {
        if (connectionStatus == null) return;
        if (GeminiKeyStore.isConfigured(this)) {
            connectionStatus.setText("API key saved on this device. Test the connection before analyzing.");
            analyzeButton.setEnabled(true);
            analyzeButton.setAlpha(1f);
        } else {
            connectionStatus.setText("No API key saved yet.");
            analyzeButton.setEnabled(false);
            analyzeButton.setAlpha(0.55f);
        }
    }

    private String obtainKey() throws Exception {
        String entered = apiKeyInput.getText().toString().trim();
        if (!entered.isEmpty()) {
            GeminiKeyStore.save(this, entered);
            apiKeyInput.setText("");
            refreshStatus();
        }
        String stored = GeminiKeyStore.read(this);
        if (stored == null || stored.trim().isEmpty()) {
            throw new IllegalStateException("Paste your key and select Save key securely first.");
        }
        return stored;
    }

    private void saveKeyFromField() {
        String entered = apiKeyInput.getText().toString().trim();
        if (entered.isEmpty()) {
            Toast.makeText(this, "Paste your Gemini API key first.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            GeminiKeyStore.save(this, entered);
            apiKeyInput.setText("");
            refreshStatus();
            Toast.makeText(this, "API key saved encrypted on this device.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showError("Couldn't save the API key securely: " + safeMessage(e));
        }
    }

    private void testConnection() {
        final String key;
        try {
            key = obtainKey();
        } catch (Exception e) {
            showError(safeMessage(e));
            return;
        }
        connectionStatus.setText("Contacting Gemini API…");
        runGeminiRequest("Reply with exactly this text and nothing else: DAYFLOW_GEMINI_CONNECTED",
                "Testing Gemini connection…", answer -> {
                    if (answer.contains("DAYFLOW_GEMINI_CONNECTED")) {
                        resultView.setText("Connection successful. Gemini is ready for DayFlow.");
                        connectionStatus.setText(answer.startsWith("[Gemini 3.5 Flash-Lite fallback used")
                                ? "Connected using Gemini 3.5 Flash-Lite fallback."
                                : "Connected successfully using Gemini 3.8 Flash.");
                    } else {
                        resultView.setText("Gemini replied, but the test marker was missing. Response:\n\n" + answer);
                        connectionStatus.setText("Gemini responded; review the test result.");
                    }
                }, key);
    }

    private void analyzeRoutine() {
        if (!GeminiKeyStore.isConfigured(this)) {
            showError("Save your Gemini API key first.");
            return;
        }
        final String prompt;
        final String consentTitle;
        final String consentMessage;
        try {
            if ("routine".equals(analysisMode)) {
                prompt = buildRoutinePrompt(selectedRoutineDate);
                consentTitle = "Send this day's routine to Google?";
                consentMessage = "DayFlow will send the saved blocks for "
                        + GeminiScheduleFacts.formatDateLabel(selectedRoutineDate)
                        + " to Gemini (task names, times, statuses and priorities). "
                        + "Google may use free-tier prompts and responses to improve its products. "
                        + "The routine will not be edited.";
            } else {
                prompt = buildOfficeMonthPrompt(selectedOfficeMonth);
                consentTitle = "Send this monthly office plan to Google?";
                consentMessage = "DayFlow will send the saved planned office dates and per-date hours for "
                        + GeminiScheduleFacts.formatMonthLabel(selectedOfficeMonth)
                        + ", plus any recorded attendance sessions for that month. "
                        + "Google may use free-tier prompts and responses to improve its products. "
                        + "Your office plan and attendance records will not be edited.";
            }
        } catch (Exception e) {
            showError("Couldn't prepare the selected analysis: " + safeMessage(e));
            return;
        }

        SharedPreferences consentPrefs = getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE);
        if (consentPrefs.getBoolean(CONSENT_KEY, false)) {
            runAnalysis(prompt);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(consentTitle)
                .setMessage(consentMessage + " Continue?")
                .setPositiveButton("Continue & analyse", (dialog, which) -> {
                    consentPrefs.edit().putBoolean(CONSENT_KEY, true).apply();
                    runAnalysis(prompt);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runAnalysis(String prompt) {
        final String key;
        try {
            key = GeminiKeyStore.read(this);
            if (key == null || key.isEmpty()) throw new IllegalStateException("Gemini API key is missing.");
        } catch (Exception e) {
            showError("Couldn't read the saved key: " + safeMessage(e));
            return;
        }
        String targetLabel = "routine".equals(analysisMode)
                ? GeminiScheduleFacts.formatDateLabel(selectedRoutineDate)
                : GeminiScheduleFacts.formatMonthLabel(selectedOfficeMonth) + " office plan";
        connectionStatus.setText("Sending " + targetLabel + " to Gemini…");
        runGeminiRequest(prompt, "Reviewing " + targetLabel + "…", answer -> {
            resultView.setText(answer);
            connectionStatus.setText("Analysis complete.");
        }, key);
    }

    private String buildRoutinePrompt(String dateKey) throws Exception {
        SharedPreferences prefs = getSharedPreferences(DAYFLOW_PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString("blocks_" + dateKey, null);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if ((json == null || json.trim().isEmpty()) && dateKey.equals(today)) {
            json = prefs.getString("blocks", null);
        }
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalStateException("No saved routine exists for " + dateKey
                    + ". Open that date in DayFlow, create or apply its routine, save it, then analyse it.");
        }
        JSONArray blocks = new JSONArray(json);
        if (blocks.length() == 0) {
            throw new IllegalStateException("The saved routine for " + dateKey + " has no time blocks.");
        }
        String arrangement = prefs.getString("mode_" + dateKey,
                dateKey.equals(today) ? prefs.getString("mode", "unknown") : "unknown");
        return GeminiScheduleFacts.routinePrompt(dateKey, arrangement, blocks);
    }

    private String buildOfficeMonthPrompt(String monthKey) throws Exception {
        SharedPreferences prefs = getSharedPreferences(DAYFLOW_PREFS, Context.MODE_PRIVATE);
        JSONArray plan = new JSONArray(prefs.getString("officeMonthPlan_" + monthKey, "[]"));
        JSONArray officeLog = new JSONArray(prefs.getString("officeLog", "[]"));
        if (plan.length() == 0) {
            throw new IllegalStateException("No saved office dates exist for " + monthKey
                    + ". Open Office & commute, add planned dates and per-date hours, then analyse the month.");
        }
        return GeminiScheduleFacts.officeMonthPrompt(monthKey, plan, officeLog);
    }

    private int durationMinutes(String start, String end) {
        try {
            String[] a = start.split(":");
            String[] b = end.split(":");
            int startMinute = Integer.parseInt(a[0]) * 60 + Integer.parseInt(a[1]);
            int endMinute = Integer.parseInt(b[0]) * 60 + Integer.parseInt(b[1]);
            return Math.max(0, endMinute - startMinute);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String formatDuration(int minutes) {
        return (minutes / 60) + "h " + String.format(Locale.US, "%02dm", minutes % 60);
    }

    private interface RequestSuccess {
        void onSuccess(String answer);
    }

    private void runGeminiRequest(String prompt, String loadingText, RequestSuccess success, String apiKey) {
        if (requestRunning) return;
        requestRunning = true;
        final long requestId = ++requestGeneration;
        setControlsEnabled(false);
        resultView.setText(loadingText);

        if (requestTimeout != null) requestHandler.removeCallbacks(requestTimeout);
        requestTimeout = () -> {
            if (!requestRunning || requestGeneration != requestId) return;
            requestRunning = false;
            requestGeneration++; // Ignore a late response from this timed-out request.
            setControlsEnabled(true);
            connectionStatus.setText("Gemini request timed out.");
            GeminiDiagnostics.record(getApplicationContext(), "ui_deadline_exceeded", "gemini-3.8-flash",
                    0, 0, 60_000L, "", "UI deadline reached before the bounded retries completed");
            resultView.setText("No response from Gemini within 60 seconds. DayFlow retries transient timeouts and tries Flash-Lite automatically. Check your connection and retry; use View / export Gemini diagnostic logs to see the HTTP status and timing.");
        };
        requestHandler.postDelayed(requestTimeout, 60_000L);

        new Thread(() -> {
            try {
                String answer = GeminiApiClient.generateContent(getApplicationContext(), apiKey, prompt);
                runOnUiThread(() -> {
                    if (!requestRunning || requestGeneration != requestId) return;
                    requestRunning = false;
                    if (requestTimeout != null) requestHandler.removeCallbacks(requestTimeout);
                    requestTimeout = null;
                    setControlsEnabled(true);
                    success.onSuccess(answer);
                });
            } catch (Exception e) {
                String message = safeMessage(e);
                runOnUiThread(() -> {
                    if (!requestRunning || requestGeneration != requestId) return;
                    requestRunning = false;
                    if (requestTimeout != null) requestHandler.removeCallbacks(requestTimeout);
                    requestTimeout = null;
                    setControlsEnabled(true);
                    resultView.setText("Gemini request failed. " + message
                            + "\n\nCheck that the key is valid, this project has Gemini API access, and the free-tier quota has not been exceeded.");
                    connectionStatus.setText("Gemini request failed; see the details below.");
                });
            }
        }, "DayFlow-Gemini-request").start();
    }

    @Override
    protected void onDestroy() {
        if (requestTimeout != null) requestHandler.removeCallbacks(requestTimeout);
        requestGeneration++;
        requestRunning = false;
        super.onDestroy();
    }

    private void showGeminiDiagnostics() {
        String report = GeminiDiagnostics.exportText(this);
        new AlertDialog.Builder(this)
                .setTitle("Gemini diagnostic logs")
                .setMessage(report)
                .setPositiveButton("Export .txt", (dialog, which) -> exportGeminiDiagnostics())
                .setNeutralButton("Copy logs", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("DayFlow Gemini diagnostics", report));
                        Toast.makeText(this, "Diagnostic log copied. Paste it into your ChatGPT conversation.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Clipboard is unavailable. Export the log instead.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void exportGeminiDiagnostics() {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "dayflow-gemini-diagnostics-" + timestamp + ".txt");
        try {
            startActivityForResult(intent, REQUEST_EXPORT_GEMINI_LOGS);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open the file picker. Use Copy logs instead.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_GEMINI_LOGS || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new java.io.IOException("Could not open the selected file.");
            output.write(GeminiDiagnostics.exportText(this).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Gemini diagnostics exported. Attach the .txt file in ChatGPT so I can inspect it.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't export diagnostics: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private void setControlsEnabled(boolean enabled) {
        saveKeyButton.setEnabled(enabled);
        testButton.setEnabled(enabled);
        analyzeButton.setEnabled(enabled && GeminiKeyStore.isConfigured(this));
        saveKeyButton.setAlpha(enabled ? 1f : 0.55f);
        testButton.setAlpha(enabled ? 1f : 0.55f);
        analyzeButton.setAlpha(analyzeButton.isEnabled() ? 1f : 0.55f);
    }

    private void confirmRemoveKey() {
        new AlertDialog.Builder(this)
                .setTitle("Remove Gemini API key?")
                .setMessage("The encrypted key will be removed from this device. You can add it again later.")
                .setPositiveButton("Remove key", (dialog, which) -> {
                    GeminiKeyStore.delete(this);
                    apiKeyInput.setText("");
                    connectionStatus.setText("API key removed.");
                    resultView.setText("No Gemini key is stored.");
                    refreshStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Gemini setup")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message;
    }
}
