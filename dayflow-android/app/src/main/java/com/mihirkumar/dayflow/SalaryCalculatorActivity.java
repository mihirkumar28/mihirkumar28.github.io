package com.mihirkumar.dayflow;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

/** Standalone, locally saved salary and take-home planning screen. */
public final class SalaryCalculatorActivity extends Activity {
    private static final String PREFS = "dayflow";
    private static final String PREFIX = "salaryCalc.";
    private static final int INK = Color.rgb(24, 55, 46);
    private static final int MUTED = Color.rgb(104, 122, 112);
    private static final int ACCENT = Color.rgb(40, 107, 85);
    private static final int PANEL = Color.rgb(234, 240, 233);
    private static final int LINE = Color.rgb(222, 230, 222);
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    private android.content.SharedPreferences preferences;
    private LinearLayout resultRows;
    private TextView monthlyTakeHome, annualTax, fixedCtc, requiredBase, requiredCtc, status;
    private final java.util.ArrayList<TextWatcher> watchers = new java.util.ArrayList<>();
    private final java.util.LinkedHashMap<String, EditText> fields = new java.util.LinkedHashMap<>();
    private CheckBox carLeasePreTax;
    private CheckBox residentIndividual;
    private boolean rendering;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int background, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(radiusDp));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private String key(String name) { return PREFIX + name; }

    private double stored(String name, double fallback) {
        return Double.longBitsToDouble(preferences.getLong(key(name), Double.doubleToLongBits(fallback)));
    }

    private void save(String name, double value) {
        preferences.edit().putLong(key(name), Double.doubleToLongBits(value)).apply();
    }

    private boolean storedBoolean(String name, boolean fallback) {
        return preferences.getBoolean(key(name), fallback);
    }

    private void saveBoolean(String name, boolean value) {
        preferences.edit().putBoolean(key(name), value).apply();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        money.setMaximumFractionDigits(0);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(13, 21, 18));
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        buildScreen();
        calculateAndRender();
    }

    private void buildScreen() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(4), dp(16), dp(4));
        toolbar.setBackgroundColor(Color.rgb(13, 21, 18));
        TextView back = label("‹", 30, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setMinWidth(dp(48));
        back.setMinHeight(dp(48));
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label("Salary calculator", 17, Color.WHITE, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.leftMargin = dp(6);
        toolbar.addView(title, titleLp);
        outer.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        content.setBackgroundColor(Color.WHITE);

        TextView intro = label("INDIA · NEW TAX REGIME · FY 2026–27", 10, ACCENT, true);
        intro.setLetterSpacing(0.08f);
        content.addView(intro);
        TextView description = label(
                "Estimate monthly bank take-home and the fixed base / CTC required to reach your target. Bonus and ESOPs are excluded.",
                13, MUTED, false);
        description.setPadding(0, dp(6), 0, dp(14));
        description.setLineSpacing(dp(2), 1f);
        content.addView(description);

        LinearLayout metrics = horizontal();
        monthlyTakeHome = metric(metrics, "MONTHLY BANK TAKE-HOME");
        annualTax = metric(metrics, "ANNUAL TAX");
        fixedCtc = metric(metrics, "FIXED CTC");
        content.addView(metrics, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout currentInputs = card(content, "CURRENT COMPENSATION");
        addField(currentInputs, "annualBase", "Annual fixed base (₹ / year)", 4000000.0, "Employer NPS is included inside this base.");
        addField(currentInputs, "targetMonthlyTakeHome", "Desired bank take-home (₹ / month)", 250000.0, "Adjust this to compare ₹2.5L, ₹2.75L, or another target.");

        LinearLayout structure = card(content, "PAYROLL STRUCTURE");
        addField(structure, "basicSharePct", "Basic as % of base", 50.0, "");
        addField(structure, "employerNpsPctOfBasic", "Employer NPS as % of Basic", 14.0, "Deduction under the new regime is capped at 14% of salary (Basic + eligible DA).");
        addField(structure, "employeePfPctOfBasic", "Employee PF as % of Basic", 12.0, "Reduces bank cash; no employee PF deduction is assumed under the new regime.");
        addField(structure, "employerPfPctOfBasic", "Employer PF as % of Basic", 12.0, "Added to fixed CTC; not deducted again from bank cash.");
        addField(structure, "gratuityPctOfBasic", "Gratuity provision as % of Basic", 4.81, "Added to fixed CTC as an estimate.");

        LinearLayout payroll = card(content, "REIMBURSEMENTS & DEDUCTIONS");
        addField(payroll, "eligibleReimbursementAnnual", "Payroll-confirmed tax-exempt reimbursements (₹ / year)", 360000.0,
                "Enter only bill-backed amounts payroll confirms are non-taxable. This is assumed to be inside Base; it is not added twice.");
        addField(payroll, "carLeaseMonthly", "Car lease deducted from pay (₹ / month)", 20000.0,
                "The ₹2.4L/year lease assumption is editable. It reduces bank cash.");
        carLeasePreTax = new CheckBox(this);
        carLeasePreTax.setText("Payroll confirms car lease is a pre-tax salary-structure item");
        carLeasePreTax.setTextSize(13);
        carLeasePreTax.setTextColor(INK);
        carLeasePreTax.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        carLeasePreTax.setChecked(storedBoolean("carLeasePreTax", true));
        payroll.addView(carLeasePreTax);
        carLeasePreTax.setOnCheckedChangeListener((button, checked) -> {
            saveBoolean("carLeasePreTax", checked);
            calculateAndRender();
        });
        addField(payroll, "professionalTaxAnnual", "Professional tax (₹ / year)", 2500.0, "Maharashtra estimate; edit to match your payslip.");
        addField(payroll, "otherPostTaxDeductionsAnnual", "Other post-tax payroll deductions (₹ / year)", 0.0, "Loans, insurance, employee NPS, or any other deductions not already entered.");

        residentIndividual = new CheckBox(this);
        residentIndividual.setText("Resident individual (apply Section 87A rebate / marginal relief)");
        residentIndividual.setTextSize(13);
        residentIndividual.setTextColor(INK);
        residentIndividual.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        residentIndividual.setChecked(storedBoolean("residentIndividual", true));
        content.addView(residentIndividual);
        residentIndividual.setOnCheckedChangeListener((button, checked) -> {
            saveBoolean("residentIndividual", checked);
            calculateAndRender();
        });

        LinearLayout targetCard = card(content, "TARGET SALARY");
        requiredBase = label("Required annual base: —", 17, INK, true);
        targetCard.addView(requiredBase);
        requiredCtc = label("Required fixed CTC: —", 15, ACCENT, true);
        requiredCtc.setPadding(0, dp(6), 0, 0);
        targetCard.addView(requiredCtc);
        status = label("", 12, MUTED, false);
        status.setPadding(0, dp(8), 0, 0);
        targetCard.addView(status);
        Button solve = new Button(this);
        solve.setText("Calculate required base");
        solve.setAllCaps(false);
        solve.setTextColor(Color.WHITE);
        solve.setBackground(rounded(ACCENT, 0, 12));
        solve.setOnClickListener(v -> calculateTarget());
        LinearLayout.LayoutParams solveLp = new LinearLayout.LayoutParams(-1, dp(48));
        solveLp.topMargin = dp(12);
        targetCard.addView(solve, solveLp);

        LinearLayout breakdown = card(content, "ANNUAL TAKE-HOME BREAKDOWN");
        resultRows = breakdown;

        LinearLayout notes = card(content, "ASSUMPTIONS & LIMITS");
        TextView caveat = label(
                "Planning estimate for FY 2026–27. Uses the new-regime slabs, ₹75,000 standard deduction, 4% cess, resident-individual Section 87A rebate / marginal relief, and surcharge marginal relief. Employer NPS is carved out of Base and not added again to CTC. Reimbursement is tax-exempt only when your employer's payroll confirms eligibility; calling HRA a reimbursement does not itself make it exempt under the new regime. Car-lease tax treatment must also match your employer's policy. Check the final structure against payroll / Form 16 before relying on it.",
                11, MUTED, false);
        caveat.setLineSpacing(dp(3), 1f);
        notes.addView(caveat);

        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(outer);
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView metric(LinearLayout parent, String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(9), dp(10), dp(9), dp(10));
        box.setBackground(rounded(PANEL, LINE, 12));
        TextView key = label(title, 9, MUTED, true);
        box.addView(key);
        TextView value = label("—", 16, INK, true);
        value.setPadding(0, dp(5), 0, 0);
        box.addView(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(5);
        parent.addView(box, lp);
        if (title.startsWith("MONTHLY")) return monthlyTakeHome = value;
        if (title.startsWith("ANNUAL")) return annualTax = value;
        return fixedCtc = value;
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(13), dp(12), dp(13), dp(13));
        panel.setBackground(rounded(Color.rgb(247, 249, 246), LINE, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(11);
        parent.addView(panel, lp);
        TextView heading = label(title, 10, ACCENT, true);
        heading.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.bottomMargin = dp(7);
        panel.addView(heading, headingLp);
        return panel;
    }

    private void addField(LinearLayout panel, String name, String title, double defaultValue, String help) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView caption = label(title, 12, INK, true);
        caption.setLineSpacing(dp(1), 1f);
        details.addView(caption);
        if (!help.isEmpty()) {
            TextView hint = label(help, 10, MUTED, false);
            hint.setPadding(0, dp(3), dp(8), dp(2));
            hint.setLineSpacing(dp(1), 1f);
            details.addView(hint);
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1f));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setTextColor(INK);
        input.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        input.setPadding(dp(8), dp(5), dp(8), dp(5));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setBackground(rounded(Color.WHITE, LINE, 9));
        input.setText(number(stored(name, defaultValue)));
        row.addView(input, new LinearLayout.LayoutParams(dp(132), dp(46)));
        fields.put(name, input);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!rendering) {
                    save(name, parse(s.toString(), defaultValue));
                    calculateAndRender();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        input.addTextChangedListener(watcher);
        watchers.add(watcher);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(5);
        panel.addView(row, rowLp);
        View line = new View(this);
        line.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(-1, dp(1));
        lineLp.topMargin = dp(6);
        panel.addView(line, lineLp);
    }

    private String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001) return String.format(Locale.US, "%.0f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    private double parse(String value, double fallback) {
        try {
            String cleaned = value.replace(",", "").replace("₹", "").trim();
            return cleaned.isEmpty() ? 0.0 : Math.max(0.0, Double.parseDouble(cleaned));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private IndiaSalaryCalculator.Inputs readInputs() {
        IndiaSalaryCalculator.Inputs in = new IndiaSalaryCalculator.Inputs();
        in.annualBase = value("annualBase", 4000000.0);
        in.targetMonthlyTakeHome = value("targetMonthlyTakeHome", 250000.0);
        in.basicSharePct = value("basicSharePct", 50.0);
        in.employerNpsPctOfBasic = value("employerNpsPctOfBasic", 14.0);
        in.employeePfPctOfBasic = value("employeePfPctOfBasic", 12.0);
        in.employerPfPctOfBasic = value("employerPfPctOfBasic", 12.0);
        in.gratuityPctOfBasic = value("gratuityPctOfBasic", 4.81);
        in.carLeaseMonthly = value("carLeaseMonthly", 20000.0);
        in.eligibleReimbursementAnnual = value("eligibleReimbursementAnnual", 360000.0);
        in.professionalTaxAnnual = value("professionalTaxAnnual", 2500.0);
        in.otherPostTaxDeductionsAnnual = value("otherPostTaxDeductionsAnnual", 0.0);
        in.carLeasePreTax = carLeasePreTax != null && carLeasePreTax.isChecked();
        in.residentIndividual = residentIndividual != null && residentIndividual.isChecked();
        return in;
    }

    private double value(String name, double fallback) {
        EditText edit = fields.get(name);
        return edit == null ? stored(name, fallback) : parse(edit.getText().toString(), fallback);
    }

    private void calculateAndRender() {
        if (fields.isEmpty()) return;
        try {
            IndiaSalaryCalculator.Inputs in = readInputs();
            IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(in);
            monthlyTakeHome.setText(fmt(result.monthlyTakeHome));
            annualTax.setText(fmt(result.annualTax));
            fixedCtc.setText(fmt(result.fixedCtc));
            if (resultRows != null) renderBreakdown(in, result);
            status.setText(result.monthlyTakeHome >= in.targetMonthlyTakeHome
                    ? "Current base is " + fmt(result.monthlyTakeHome - in.targetMonthlyTakeHome)
                    + " per month above the target."
                    : "Current base is " + fmt(in.targetMonthlyTakeHome - result.monthlyTakeHome)
                    + " per month below the target.");
            calculateTarget();
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void calculateTarget() {
        if (fields.isEmpty() || status == null) return;
        try {
            IndiaSalaryCalculator.Inputs in = readInputs();
            double solved = IndiaSalaryCalculator.solveAnnualBaseForMonthlyTarget(in);
            IndiaSalaryCalculator.Inputs scenario = readInputs();
            scenario.annualBase = solved;
            IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(scenario);
            requiredBase.setText("Required annual base: " + fmt(solved));
            requiredCtc.setText("Required fixed CTC: " + fmt(result.fixedCtc));
        } catch (IllegalArgumentException error) {
            requiredBase.setText("Required annual base: —");
            requiredCtc.setText("Required fixed CTC: —");
        }
    }

    private void renderBreakdown(IndiaSalaryCalculator.Inputs in, IndiaSalaryCalculator.Result r) {
        if (resultRows == null) return;
        resultRows.removeViews(1, Math.max(0, resultRows.getChildCount() - 1));
        row("Annual fixed base (includes NPS)", r.annualBase);
        row("Basic (" + number(in.basicSharePct) + "% of base)", r.basic);
        row("Employer NPS — inside base", -r.employerNps);
        row("Cash salary after NPS", r.annualBase - r.employerNps);
        row("Employee PF", -r.employeePf);
        row("Eligible bill-backed reimbursement (taxable-income relief)", -r.eligibleReimbursement);
        row("Standard deduction", -r.standardDeduction);
        if (in.carLeasePreTax) row("Pre-tax car lease — taxable income", -r.carLeaseAnnual);
        row("Taxable income", r.taxableIncome);
        row("Income tax after rebate / marginal relief", r.taxBeforeSurcharge);
        row("Surcharge", r.surcharge);
        row("Health & education cess (4%)", r.cess);
        row("Professional tax", -r.professionalTax);
        row("Car lease paid from salary", -r.carLeaseAnnual);
        row("Other post-tax deductions", -r.otherPostTaxDeductions);
        row("Annual bank take-home", r.annualTakeHome);
        row("Employer PF added to CTC", r.employerPf);
        row("Gratuity provision added to CTC", r.gratuity);
        row("Fixed CTC (excludes bonus / ESOPs)", r.fixedCtc);
    }

    private void row(String name, double amount) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(6), 0, dp(6));
        TextView left = label(name, 11, MUTED, false);
        line.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView right = label(fmt(amount), 11, INK, true);
        right.setGravity(Gravity.END);
        line.addView(right, new LinearLayout.LayoutParams(dp(120), -2));
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        resultRows.addView(line);
        resultRows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private String fmt(double value) {
        return money.format(Math.round(value));
    }

    @Override protected void onDestroy() {
        for (TextWatcher watcher : watchers) {
            // Views own their watchers; no asynchronous callbacks are retained by the activity.
        }
        super.onDestroy();
    }
}
