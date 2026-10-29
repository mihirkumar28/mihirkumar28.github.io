package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Optional Gemini-powered analysis. The schedule is never changed by model output. */
public class GeminiAssistantActivity extends Activity {
    private static final String DAYFLOW_PREFS = "dayflow";
    private static final String CONSENT_PREFS = "dayflow_gemini_preferences";
    private static final String CONSENT_KEY = "schedule_analysis_consent_v1";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(13, 21, 18));
        getWindow().setNavigationBarColor(Color.rgb(13, 21, 18));
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUi();
        refreshStatus();
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

        LinearLayout analysisCard = new LinearLayout(this);
        analysisCard.setOrientation(LinearLayout.VERTICAL);
        analysisCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        analysisCard.setBackground(background(Color.rgb(24, 35, 30), 18, Color.rgb(47, 67, 57)));
        LinearLayout.LayoutParams analysisCardLp = new LinearLayout.LayoutParams(-1, -2);
        analysisCardLp.topMargin = dp(14);
        content.addView(analysisCard, analysisCardLp);

        analysisCard.addView(text("2. Analyze today's routine", 16, Color.rgb(242, 247, 243), true));
        TextView details = text("Gemini checks your actual saved task titles and times, flags potential overload or gaps, and suggests changes without editing the plan.", 12, Color.rgb(171, 189, 178), false);
        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
        detailsLp.topMargin = dp(7);
        analysisCard.addView(details, detailsLp);

        analyzeButton = button("Analyze today's routine", true);
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

        TextView dataNote = text("Privacy note: using the free tier means prompts and responses may be used by Google to improve its products. Schedule analysis sends today's task names, time ranges, and statuses to Google. Avoid storing sensitive information in task titles.", 11, Color.rgb(154, 171, 160), false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(16);
        content.addView(dataNote, noteLp);
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
        String prompt;
        try {
            prompt = buildTodayPrompt();
        } catch (Exception e) {
            showError("Couldn't read today's schedule: " + safeMessage(e));
            return;
        }

        SharedPreferences consentPrefs = getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE);
        if (consentPrefs.getBoolean(CONSENT_KEY, false)) {
            runAnalysis(prompt);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Send today's schedule to Google?")
                .setMessage("To analyze the routine, DayFlow will send today's task titles, time ranges, and statuses to Gemini. Google may use free-tier prompts and responses to improve its products. Your plan will not be edited. Continue?")
                .setPositiveButton("Continue & analyze", (dialog, which) -> {
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
        connectionStatus.setText("Sending today's schedule for analysis…");
        runGeminiRequest(prompt, "Reviewing today's routine…", answer -> {
            resultView.setText(answer);
            connectionStatus.setText("Analysis complete.");
        }, key);
    }

    private String buildTodayPrompt() throws Exception {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        SharedPreferences prefs = getSharedPreferences(DAYFLOW_PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString("blocks_" + date, null);
        if ((json == null || json.trim().isEmpty()) && date.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()))) {
            json = prefs.getString("blocks", null);
        }
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalStateException("There is no saved schedule for today. Open Today in DayFlow and save a routine, then try again.");
        }
        String arrangement = prefs.getString("mode_" + date, prefs.getString("mode", "wfh"));
        String arrangementLabel = "office".equals(arrangement) ? "Work from office"
                : "weekend".equals(arrangement) ? "Weekend" : "Work from home";
        JSONArray blocks = new JSONArray(json);
        if (blocks.length() == 0) throw new IllegalStateException("Today's saved schedule has no blocks yet.");

        StringBuilder schedule = new StringBuilder();
        int totalMinutes = 0;
        int completedMinutes = 0;
        schedule.append("Date: ").append(date).append("\nWork arrangement: ").append(arrangementLabel).append("\n");
        schedule.append("Saved task blocks (status is from the app; duration is calculated from start/end):\n");
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null) continue;
            String start = block.optString("start", "");
            String end = block.optString("end", "");
            String title = block.optString("title", "Untitled block");
            String status = block.optString("status", "pending");
            int duration = durationMinutes(start, end);
            totalMinutes += duration;
            if ("done".equals(status)) completedMinutes += duration;
            schedule.append("- ").append(start).append("–").append(end).append(" (")
                    .append(formatDuration(duration)).append("): ").append(title)
                    .append(" | status=").append(status)
                    .append(" | rigidity=").append(block.optString("rigidity", "flexible"))
                    .append(" | priority=").append(block.optString("priority", "normal")).append("\n");
        }
        schedule.append("Total scheduled: ").append(formatDuration(totalMinutes)).append("\n");
        schedule.append("Marked done: ").append(formatDuration(completedMinutes)).append("\n\n");
        schedule.append("You are DayFlow's schedule reviewer. Analyze only this provided data. Do not invent tasks, obligations, durations, or facts.\n");
        schedule.append("Return: (1) brief verdict on feasibility, (2) time conflicts or unrealistic transitions that are evident from the given times, (3) likely overload or underused time, (4) at most three concrete schedule changes.\n");
        schedule.append("Preserve fixed blocks and critical priorities unless the data clearly implies otherwise. Distinguish an observed fact from an assumption. Do not claim to edit or save anything; suggestions only. Keep the answer concise and practical.");
        return schedule.toString();
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
            resultView.setText("No response from Gemini within 45 seconds. Check your mobile data/Wi-Fi and API key, then try Test connection again. If this repeats, open Google AI Studio and confirm the key's project still has Gemini API access.");
        };
        requestHandler.postDelayed(requestTimeout, 45_000L);

        new Thread(() -> {
            try {
                String answer = GeminiApiClient.generateContent(apiKey, prompt);
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
