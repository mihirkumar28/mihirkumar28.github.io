package com.mihirkumar.dayflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
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
import java.util.List;
import java.util.Locale;

/** Read-only Gemini analysis of saved commute history, home/work points, and existing routines. */
public class GeminiCommuteAssistantActivity extends Activity {
    private static final String DAYFLOW_PREFS = "dayflow";
    private static final String POINTS_PREFS = "dayflow_commute_points";
    private static final String CONSENT_PREFS = "dayflow_gemini_preferences";
    private static final String CONSENT_KEY = "commute_analysis_consent_v1";

    private EditText homeLabelInput;
    private EditText homeLatInput;
    private EditText homeLngInput;
    private EditText workLabelInput;
    private EditText workLatInput;
    private EditText workLngInput;
    private TextView pointsStatus;
    private TextView resultView;
    private TextView requestStatus;
    private Button savePointsButton;
    private Button mapButton;
    private Button analyseButton;
    private Button monthButton;
    private String selectedMonth;
    private boolean requestRunning;
    private boolean resolvingPoints;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable requestTimeout;
    private long requestGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(13, 21, 18));
        getWindow().setNavigationBarColor(Color.rgb(13, 21, 18));
        getWindow().getDecorView().setSystemUiVisibility(0);
        selectedMonth = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        buildUi();
        loadSavedPoints();
        refreshMonthLabel();
        refreshButtons();
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
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(13);
        view.setMinHeight(dp(46));
        view.setPadding(dp(12), dp(4), dp(12), dp(4));
        view.setTextColor(primary ? Color.WHITE : Color.rgb(215, 231, 220));
        view.setBackground(background(primary ? Color.rgb(35, 132, 95) : Color.rgb(35, 53, 44),
                12, Color.TRANSPARENT));
        return view;
    }

    private EditText field(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setHint(hint);
        field.setHintTextColor(Color.rgb(108, 124, 114));
        field.setTextColor(Color.rgb(24, 55, 46));
        field.setInputType(inputType);
        field.setBackground(background(Color.WHITE, 10, Color.rgb(221, 230, 222)));
        field.setPadding(dp(12), dp(9), dp(12), dp(9));
        return field;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 21, 18));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
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

        TextView back = text("‹  Back to Gemini", 14, Color.rgb(101, 197, 160), true);
        back.setPadding(0, 0, 0, dp(12));
        back.setOnClickListener(v -> finish());
        content.addView(back);
        content.addView(text("COMMUTE INTELLIGENCE", 10, Color.rgb(101, 197, 160), true));
        TextView title = text("Commute analysis", 27, Color.rgb(242, 247, 243), true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(5);
        content.addView(title, titleParams);
        TextView intro = text("Gemini reviews your saved commute times, home/work points, planned office dates and the routine already saved for those dates. It can recommend ways to reduce travel time, but this screen is strictly read-only: it cannot edit your timetable.", 13, Color.rgb(171, 189, 178), false);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(8);
        introParams.bottomMargin = dp(16);
        content.addView(intro, introParams);

        LinearLayout pointsCard = new LinearLayout(this);
        pointsCard.setOrientation(LinearLayout.VERTICAL);
        pointsCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        pointsCard.setBackground(background(Color.rgb(24, 35, 30), 18, Color.rgb(47, 67, 57)));
        content.addView(pointsCard);
        pointsCard.addView(text("1. Home and work points", 16, Color.rgb(242, 247, 243), true));
        TextView pointHelp = text("Enter a place/address for each point. Android's geocoding service may receive the address to resolve coordinates; you can instead paste latitude and longitude manually. Resolved points are stored on this device. Opening the route preview shares the coordinates with your map app.", 12, Color.rgb(171, 189, 178), false);
        LinearLayout.LayoutParams pointHelpParams = new LinearLayout.LayoutParams(-1, -2);
        pointHelpParams.topMargin = dp(7);
        pointsCard.addView(pointHelp, pointHelpParams);

        pointsCard.addView(fieldLabel("HOME"));
        homeLabelInput = field("Home address or place name", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        pointsCard.addView(homeLabelInput, new LinearLayout.LayoutParams(-1, dp(48)));
        pointsCard.addView(coordinateRow("Home latitude", "Home longitude", true));

        LinearLayout.LayoutParams workHeadingParams = new LinearLayout.LayoutParams(-1, -2);
        workHeadingParams.topMargin = dp(12);
        TextView workHeading = fieldLabel("WORK");
        workHeading.setLayoutParams(workHeadingParams);
        pointsCard.addView(workHeading);
        workLabelInput = field("Work address or place name", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        pointsCard.addView(workLabelInput, new LinearLayout.LayoutParams(-1, dp(48)));
        pointsCard.addView(coordinateRow("Work latitude", "Work longitude", false));

        pointsStatus = text("Points have not been saved yet.", 11, Color.rgb(154, 171, 160), false);
        LinearLayout.LayoutParams pointsStatusParams = new LinearLayout.LayoutParams(-1, -2);
        pointsStatusParams.topMargin = dp(9);
        pointsCard.addView(pointsStatus, pointsStatusParams);

        savePointsButton = button("Save / resolve home and work points", true);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(46));
        saveParams.topMargin = dp(10);
        pointsCard.addView(savePointsButton, saveParams);
        savePointsButton.setOnClickListener(v -> resolveAndSavePoints(null));

        mapButton = button("Preview home-to-work route in Maps", false);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, dp(44));
        mapParams.topMargin = dp(8);
        pointsCard.addView(mapButton, mapParams);
        mapButton.setOnClickListener(v -> openRouteInMaps());

        Button clearPointsButton = button("Clear saved home/work points", false);
        LinearLayout.LayoutParams clearPointsParams = new LinearLayout.LayoutParams(-1, dp(42));
        clearPointsParams.topMargin = dp(6);
        pointsCard.addView(clearPointsButton, clearPointsParams);
        clearPointsButton.setOnClickListener(v -> confirmClearPoints());

        LinearLayout analysisCard = new LinearLayout(this);
        analysisCard.setOrientation(LinearLayout.VERTICAL);
        analysisCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        analysisCard.setBackground(background(Color.rgb(24, 35, 30), 18, Color.rgb(47, 67, 57)));
        LinearLayout.LayoutParams analysisCardParams = new LinearLayout.LayoutParams(-1, -2);
        analysisCardParams.topMargin = dp(14);
        content.addView(analysisCard, analysisCardParams);
        analysisCard.addView(text("2. Analyse and project", 16, Color.rgb(242, 247, 243), true));
        TextView analysisHelp = text("Choose the office-plan month. DayFlow calculates historical medians and an 80th-percentile projection; Gemini interprets the evidence against your saved routine.", 12, Color.rgb(171, 189, 178), false);
        LinearLayout.LayoutParams analysisHelpParams = new LinearLayout.LayoutParams(-1, -2);
        analysisHelpParams.topMargin = dp(7);
        analysisCard.addView(analysisHelp, analysisHelpParams);

        monthButton = button("Select office-plan month", false);
        LinearLayout.LayoutParams monthParams = new LinearLayout.LayoutParams(-1, dp(44));
        monthParams.topMargin = dp(10);
        analysisCard.addView(monthButton, monthParams);
        monthButton.setOnClickListener(v -> chooseMonth());

        analyseButton = button("Analyse commute and project travel time", true);
        LinearLayout.LayoutParams analyseParams = new LinearLayout.LayoutParams(-1, dp(48));
        analyseParams.topMargin = dp(10);
        analysisCard.addView(analyseButton, analyseParams);
        analyseButton.setOnClickListener(v -> resolveAndSavePoints(this::requestAnalysis));

        requestStatus = text("Save both points, then analyse your commute.", 11, Color.rgb(101, 197, 160), true);
        LinearLayout.LayoutParams requestStatusParams = new LinearLayout.LayoutParams(-1, -2);
        requestStatusParams.topMargin = dp(10);
        analysisCard.addView(requestStatus, requestStatusParams);

        resultView = text("Your commute analysis will appear here.", 13, Color.rgb(215, 231, 220), false);
        resultView.setTextIsSelectable(true);
        resultView.setPadding(dp(13), dp(13), dp(13), dp(13));
        resultView.setBackground(background(Color.rgb(18, 28, 23), 12, Color.rgb(47, 67, 57)));
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2);
        resultParams.topMargin = dp(12);
        analysisCard.addView(resultView, resultParams);

        TextView privacyNote = text("Privacy: address geocoding may contact the Android geocoding provider, and route preview opens an external map app with your two points. Home/work labels and coordinates, selected office dates, available routine blocks, and recorded commute timestamps are sent to Google Gemini only when you press Analyse and confirm the separate commute-data consent. The app does not track your live location. Home/work points are excluded from Android backup. Google's handling of Gemini prompts is subject to your account and plan; free-tier content may be used to improve Google's products.", 11, Color.rgb(154, 171, 160), false);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(-1, -2);
        privacyParams.topMargin = dp(16);
        content.addView(privacyNote, privacyParams);
    }

    private TextView fieldLabel(String label) {
        TextView view = text(label, 10, Color.rgb(101, 197, 160), true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        params.bottomMargin = dp(5);
        view.setLayoutParams(params);
        return view;
    }

    private View coordinateRow(String latHint, String lngHint, boolean home) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        EditText lat = field(latHint, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText lng = field(lngHint, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        row.addView(lat, new LinearLayout.LayoutParams(0, dp(46), 1f));
        LinearLayout.LayoutParams lngParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lngParams.leftMargin = dp(8);
        row.addView(lng, lngParams);
        if (home) {
            homeLatInput = lat;
            homeLngInput = lng;
        } else {
            workLatInput = lat;
            workLngInput = lng;
        }
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(46));
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);
        return row;
    }

    private void loadSavedPoints() {
        SharedPreferences prefs = getSharedPreferences(POINTS_PREFS, Context.MODE_PRIVATE);
        homeLabelInput.setText(prefs.getString("home_label", ""));
        homeLatInput.setText(prefs.getString("home_lat", ""));
        homeLngInput.setText(prefs.getString("home_lng", ""));
        workLabelInput.setText(prefs.getString("work_label", ""));
        workLatInput.setText(prefs.getString("work_lat", ""));
        workLngInput.setText(prefs.getString("work_lng", ""));
        refreshPointStatus();
    }

    private void refreshPointStatus() {
        if (pointsStatus == null) return;
        SharedPreferences prefs = getSharedPreferences(POINTS_PREFS, Context.MODE_PRIVATE);
        if (hasSavedPoints(prefs)) {
            pointsStatus.setText("Saved locally: " + prefs.getString("home_label", "Home")
                    + " → " + prefs.getString("work_label", "Work")
                    + "\nCoordinates are stored on this device until you choose to send them.");
        } else {
            pointsStatus.setText("Points have not been saved yet. Enter both locations and resolve them.");
        }
        refreshButtons();
    }

    private boolean hasSavedPoints(SharedPreferences prefs) {
        try {
            double homeLat = Double.parseDouble(prefs.getString("home_lat", ""));
            double homeLng = Double.parseDouble(prefs.getString("home_lng", ""));
            double workLat = Double.parseDouble(prefs.getString("work_lat", ""));
            double workLng = Double.parseDouble(prefs.getString("work_lng", ""));
            return validCoordinate(homeLat, -90d, 90d)
                    && validCoordinate(homeLng, -180d, 180d)
                    && validCoordinate(workLat, -90d, 90d)
                    && validCoordinate(workLng, -180d, 180d);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void refreshButtons() {
        if (mapButton == null || analyseButton == null) return;
        boolean configured = hasSavedPoints(getSharedPreferences(POINTS_PREFS, Context.MODE_PRIVATE));
        mapButton.setEnabled(configured && !requestRunning && !resolvingPoints);
        mapButton.setAlpha(mapButton.isEnabled() ? 1f : 0.55f);
        boolean ready = GeminiKeyStore.isConfigured(this);
        analyseButton.setEnabled(ready && !requestRunning && !resolvingPoints);
        analyseButton.setAlpha(analyseButton.isEnabled() ? 1f : 0.55f);
    }

    private void refreshMonthLabel() {
        try {
            Date date = new SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth);
            monthButton.setText("Office-plan month  ·  "
                    + new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date));
        } catch (Exception ignored) {
            monthButton.setText("Office-plan month  ·  " + selectedMonth);
        }
    }

    private void chooseMonth() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        try {
            Date date = new SimpleDateFormat("yyyy-MM", Locale.US).parse(selectedMonth);
            if (date != null) calendar.setTime(date);
        } catch (Exception ignored) { }
        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedMonth = String.format(Locale.US, "%04d-%02d", year, month + 1);
            refreshMonthLabel();
        }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), 1).show();
    }

    private void resolveAndSavePoints(Runnable onSuccess) {
        if (resolvingPoints || requestRunning) return;
        final String homeLabel = homeLabelInput.getText().toString().trim();
        final String workLabel = workLabelInput.getText().toString().trim();
        final String homeLatText = homeLatInput.getText().toString().trim();
        final String homeLngText = homeLngInput.getText().toString().trim();
        final String workLatText = workLatInput.getText().toString().trim();
        final String workLngText = workLngInput.getText().toString().trim();

        if (homeLabel.isEmpty() || workLabel.isEmpty()) {
            showError("Enter both a home label/address and a work label/address.");
            return;
        }
        resolvingPoints = true;
        refreshButtons();
        pointsStatus.setText("Resolving the home and work points…");

        new Thread(() -> {
            try {
                double[] home = resolveCoordinates(homeLabel, homeLatText, homeLngText);
                double[] work = resolveCoordinates(workLabel, workLatText, workLngText);
                SharedPreferences prefs = getSharedPreferences(POINTS_PREFS, Context.MODE_PRIVATE);
                prefs.edit()
                        .putString("home_label", homeLabel)
                        .putString("home_lat", Double.toString(home[0]))
                        .putString("home_lng", Double.toString(home[1]))
                        .putString("work_label", workLabel)
                        .putString("work_lat", Double.toString(work[0]))
                        .putString("work_lng", Double.toString(work[1]))
                        .apply();
                runOnUiThread(() -> {
                    homeLatInput.setText(Double.toString(home[0]));
                    homeLngInput.setText(Double.toString(home[1]));
                    workLatInput.setText(Double.toString(work[0]));
                    workLngInput.setText(Double.toString(work[1]));
                    resolvingPoints = false;
                    pointsStatus.setText("Home and work points saved locally. No live location tracking is used.");
                    refreshPointStatus();
                    if (onSuccess != null) onSuccess.run();
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> {
                    resolvingPoints = false;
                    pointsStatus.setText("Couldn't resolve both points. Enter valid coordinates manually or correct the place names.");
                    refreshButtons();
                    showError(message);
                });
            }
        }, "DayFlow-commute-geocoder").start();
    }

    @SuppressWarnings("deprecation")
    private double[] resolveCoordinates(String label, String latitude, String longitude) throws Exception {
        boolean hasLatitude = latitude != null && !latitude.isEmpty();
        boolean hasLongitude = longitude != null && !longitude.isEmpty();
        if (hasLatitude != hasLongitude) {
            throw new IllegalArgumentException("Enter both latitude and longitude for a point, or leave both blank to resolve its address.");
        }
        if (hasLatitude) {
            double lat = Double.parseDouble(latitude);
            double lng = Double.parseDouble(longitude);
            if (!validCoordinate(lat, -90d, 90d) || !validCoordinate(lng, -180d, 180d)) {
                throw new IllegalArgumentException("Coordinates are outside the valid latitude/longitude ranges.");
            }
            return new double[]{lat, lng};
        }
        if (!Geocoder.isPresent()) {
            throw new IllegalStateException("This Android device has no geocoding service. Paste latitude and longitude from Google Maps.");
        }
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> matches = geocoder.getFromLocationName(label, 1);
        if (matches == null || matches.isEmpty()
                || !matches.get(0).hasLatitude() || !matches.get(0).hasLongitude()) {
            throw new IllegalArgumentException("Couldn't find coordinates for: " + label
                    + ". Try a more specific address or paste coordinates from Google Maps.");
        }
        double lat = matches.get(0).getLatitude();
        double lng = matches.get(0).getLongitude();
        if (!validCoordinate(lat, -90d, 90d) || !validCoordinate(lng, -180d, 180d)) {
            throw new IllegalArgumentException("The geocoder returned invalid coordinates. Enter them manually.");
        }
        return new double[]{lat, lng};
    }

    private boolean validCoordinate(double value, double min, double max) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= min && value <= max;
    }

    private void confirmClearPoints() {
        new AlertDialog.Builder(this)
                .setTitle("Clear saved home/work points?")
                .setMessage("This removes both saved locations and coordinates from this device. Commute history and your timetable will not be affected.")
                .setPositiveButton("Clear points", (dialog, which) -> {
                    getSharedPreferences(POINTS_PREFS, Context.MODE_PRIVATE)
                            .edit().clear().apply();
                    homeLabelInput.setText("");
                    homeLatInput.setText("");
                    homeLngInput.setText("");
                    workLabelInput.setText("");
                    workLatInput.setText("");
                    workLngInput.setText("");
                    pointsStatus.setText("Saved home/work points cleared.");
                    requestStatus.setText("Save both points again before the next commute analysis.");
                    refreshPointStatus();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openRouteInMaps() {
        SharedPreferences prefs = getSharedPreferences(POINTS_PREFS, Context.MODE_PRIVATE);
        if (!hasSavedPoints(prefs)) {
            showError("Save valid home and work points first.");
            return;
        }
        String origin = prefs.getString("home_lat", "") + "," + prefs.getString("home_lng", "");
        String destination = prefs.getString("work_lat", "") + "," + prefs.getString("work_lng", "");
        String url = "https://www.google.com/maps/dir/?api=1&origin="
                + Uri.encode(origin) + "&destination=" + Uri.encode(destination)
                + "&travelmode=driving";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            showError("Couldn't open a map app: " + safeMessage(error));
        }
    }

    private void requestAnalysis() {
        if (!GeminiKeyStore.isConfigured(this)) {
            showError("Save a Gemini API key in the Gemini assistant screen first.");
            return;
        }
        final String prompt;
        try {
            prompt = buildCommutePrompt(selectedMonth);
        } catch (Exception error) {
            showError("Couldn't prepare the commute analysis: " + safeMessage(error));
            return;
        }

        SharedPreferences consent = getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE);
        if (consent.getBoolean(CONSENT_KEY, false)) {
            runAnalysis(prompt);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Send commute and location data to Google?")
                .setMessage("DayFlow will send your saved home/work labels and coordinates, the selected office-plan dates, available routine blocks, and recorded commute departure/end timestamps to Gemini. Google may use free-tier prompts and responses to improve its products. This is a separate consent from daily routine analysis. The app will not modify your routine or track your live location. Continue?")
                .setPositiveButton("Continue & analyse", (dialog, which) -> {
                    consent.edit().putBoolean(CONSENT_KEY, true).apply();
                    runAnalysis(prompt);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String buildCommutePrompt(String monthKey) throws Exception {
        SharedPreferences prefs = getSharedPreferences(DAYFLOW_PREFS, Context.MODE_PRIVATE);
        SharedPreferences points = getSharedPreferences(POINTS_PREFS, Context.MODE_PRIVATE);
        JSONArray plan = new JSONArray(prefs.getString("officeMonthPlan_" + monthKey, "[]"));
        JSONArray officeLog = new JSONArray(prefs.getString("officeLog", "[]"));
        JSONArray routines = new JSONArray();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        for (int i = 0; i < plan.length(); i++) {
            JSONObject item = plan.optJSONObject(i);
            if (item == null) continue;
            String date = item.optString("date", "");
            String routineJson = prefs.getString("blocks_" + date, null);
            if ((routineJson == null || routineJson.trim().isEmpty()) && date.equals(today)) {
                routineJson = prefs.getString("blocks", null);
            }
            if (routineJson == null || routineJson.trim().isEmpty()) continue;
            JSONArray blocks;
            try {
                blocks = new JSONArray(routineJson);
            } catch (Exception ignored) {
                continue;
            }
            JSONObject routine = new JSONObject()
                    .put("date", date)
                    .put("arrangement", prefs.getString("mode_" + date,
                            date.equals(today) ? prefs.getString("mode", "unknown") : "unknown"))
                    .put("blocks", blocks);
            routines.put(routine);
        }
        return GeminiCommuteFacts.buildPrompt(monthKey, today, System.currentTimeMillis(),
                plan, officeLog, routines,
                points.getString("home_label", ""),
                Double.parseDouble(points.getString("home_lat", "NaN")),
                Double.parseDouble(points.getString("home_lng", "NaN")),
                points.getString("work_label", ""),
                Double.parseDouble(points.getString("work_lat", "NaN")),
                Double.parseDouble(points.getString("work_lng", "NaN")));
    }

    private void runAnalysis(String prompt) {
        final String key;
        try {
            key = GeminiKeyStore.read(this);
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalStateException("Gemini API key is missing.");
            }
        } catch (Exception error) {
            showError("Couldn't read the saved Gemini key: " + safeMessage(error));
            return;
        }

        runGeminiRequest(prompt, key);
    }

    private void runGeminiRequest(String prompt, String key) {
        if (requestRunning) return;
        requestRunning = true;
        final long requestId = ++requestGeneration;
        refreshButtons();
        requestStatus.setText("Sending commute history and saved points to Gemini…");
        resultView.setText("Analysing your recorded commute pattern and projecting the selected month…");
        if (requestTimeout != null) handler.removeCallbacks(requestTimeout);
        requestTimeout = () -> {
            if (!requestRunning || requestGeneration != requestId) return;
            requestRunning = false;
            requestGeneration++;
            requestTimeout = null;
            refreshButtons();
            requestStatus.setText("Gemini request timed out.");
            resultView.setText("No response from Gemini within 60 seconds. Check your connection and retry. Your routine remains unchanged.");
        };
        handler.postDelayed(requestTimeout, 60_000L);

        new Thread(() -> {
            try {
                String answer = GeminiApiClient.generateContent(getApplicationContext(), key, prompt);
                runOnUiThread(() -> {
                    if (!requestRunning || requestGeneration != requestId) return;
                    requestRunning = false;
                    if (requestTimeout != null) handler.removeCallbacks(requestTimeout);
                    requestTimeout = null;
                    refreshButtons();
                    requestStatus.setText("Commute analysis complete. Your timetable was not changed.");
                    resultView.setText(answer);
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> {
                    if (!requestRunning || requestGeneration != requestId) return;
                    requestRunning = false;
                    if (requestTimeout != null) handler.removeCallbacks(requestTimeout);
                    requestTimeout = null;
                    refreshButtons();
                    requestStatus.setText("Gemini request failed.");
                    resultView.setText("Couldn't complete the commute analysis. " + message
                            + "\n\nCheck the Gemini connection and available API quota. Your routine remains unchanged.");
                });
            }
        }, "DayFlow-Gemini-commute").start();
    }

    @Override
    protected void onDestroy() {
        if (requestTimeout != null) handler.removeCallbacks(requestTimeout);
        requestGeneration++;
        requestRunning = false;
        super.onDestroy();
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Commute analysis")
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
