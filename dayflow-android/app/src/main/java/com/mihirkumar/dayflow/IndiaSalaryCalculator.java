package com.mihirkumar.dayflow;

/**
 * Salary estimator for an Indian salaried employee under the new tax regime
 * for FY 2026-27. This is a planning model, not a tax-return engine.
 *
 * Base includes employer NPS. Employer PF and gratuity are added to base only
 * when presenting fixed CTC. Reimbursement and car-lease treatment are
 * explicitly configurable because payroll eligibility depends on the employer.
 */
final class IndiaSalaryCalculator {
    static final double STANDARD_DEDUCTION = 75000.0;
    static final double CESS_RATE = 0.04;
    static final double EMPLOYER_NPS_DEDUCTION_CAP = 0.14;

    private IndiaSalaryCalculator() {}

    static final class Inputs {
        double annualBase = 4000000.0;
        double targetMonthlyTakeHome = 250000.0;
        double basicSharePct = 50.0;
        double employerNpsPctOfBasic = 14.0;
        double employeePfPctOfBasic = 12.0;
        double employerPfPctOfBasic = 12.0;
        double gratuityPctOfBasic = 4.81;
        double carLeaseMonthly = 20000.0;
        double eligibleReimbursementAnnual = 360000.0;
        double professionalTaxAnnual = 2500.0;
        double otherPostTaxDeductionsAnnual = 0.0;
        boolean carLeasePreTax = true;
        boolean residentIndividual = true;
    }

    static final class Result {
        double annualBase;
        double basic;
        double employerNps;
        double employerNpsDeduction;
        double employeePf;
        double employerPf;
        double gratuity;
        double eligibleReimbursement;
        double carLeaseAnnual;
        double standardDeduction;
        double taxableIncome;
        double taxBeforeSurcharge;
        double surcharge;
        double cess;
        double annualTax;
        double professionalTax;
        double otherPostTaxDeductions;
        double annualTakeHome;
        double monthlyTakeHome;
        double fixedCtc;
        double targetAnnualBase;
        double targetFixedCtc;
    }

    static Result calculate(Inputs in) {
        validate(in);
        Result out = new Result();
        out.annualBase = in.annualBase;
        out.basic = in.annualBase * in.basicSharePct / 100.0;
        out.employerNps = out.basic * in.employerNpsPctOfBasic / 100.0;
        out.employerNpsDeduction = Math.min(out.employerNps,
                out.basic * EMPLOYER_NPS_DEDUCTION_CAP);
        out.employeePf = out.basic * in.employeePfPctOfBasic / 100.0;
        out.employerPf = out.basic * in.employerPfPctOfBasic / 100.0;
        out.gratuity = out.basic * in.gratuityPctOfBasic / 100.0;
        out.eligibleReimbursement = Math.min(in.eligibleReimbursementAnnual, in.annualBase);
        out.carLeaseAnnual = in.carLeaseMonthly * 12.0;
        out.standardDeduction = Math.min(STANDARD_DEDUCTION, in.annualBase);

        double taxable = in.annualBase - out.employerNpsDeduction
                - out.eligibleReimbursement - out.standardDeduction;
        if (in.carLeasePreTax) taxable -= out.carLeaseAnnual;
        out.taxableIncome = Math.max(0.0, taxable);

        out.taxBeforeSurcharge = taxAfterRebate(out.taxableIncome, in.residentIndividual);
        double taxWithSurcharge = taxWithSurcharge(out.taxBeforeSurcharge,
                out.taxableIncome, in.residentIndividual);
        out.surcharge = Math.max(0.0, taxWithSurcharge - out.taxBeforeSurcharge);
        out.cess = (out.taxBeforeSurcharge + out.surcharge) * CESS_RATE;
        out.annualTax = out.taxBeforeSurcharge + out.surcharge + out.cess;

        out.professionalTax = in.professionalTaxAnnual;
        out.otherPostTaxDeductions = in.otherPostTaxDeductionsAnnual;

        // Employer NPS is part of Base but is not paid as bank cash.
        // The reimbursable component is already included in Base and is not added twice.
        double cash = in.annualBase - out.employerNps - out.employeePf
                - out.annualTax - out.professionalTax
                - out.carLeaseAnnual - out.otherPostTaxDeductions;
        out.annualTakeHome = Math.max(0.0, cash);
        out.monthlyTakeHome = out.annualTakeHome / 12.0;
        out.fixedCtc = in.annualBase + out.employerPf + out.gratuity;
        return out;
    }

    static double solveAnnualBaseForMonthlyTarget(Inputs source) {
        validate(source);
        if (source.targetMonthlyTakeHome <= 0.0) return 0.0;
        double low = 0.0;
        double high = 200000000.0;
        Inputs candidate = copy(source);
        for (int i = 0; i < 90; i++) {
            double mid = (low + high) / 2.0;
            candidate.annualBase = mid;
            double takeHome = calculate(candidate).monthlyTakeHome;
            if (takeHome < source.targetMonthlyTakeHome) low = mid;
            else high = mid;
        }
        return high;
    }

    static double slabTax(double taxableIncome) {
        double income = Math.max(0.0, taxableIncome);
        double tax = 0.0;
        double previous = 0.0;
        double[][] bands = {
                {400000.0, 0.00},
                {800000.0, 0.05},
                {1200000.0, 0.10},
                {1600000.0, 0.15},
                {2000000.0, 0.20},
                {2400000.0, 0.25}
        };
        for (double[] band : bands) {
            double upper = band[0];
            double rate = band[1];
            double amount = Math.max(0.0, Math.min(income, upper) - previous);
            tax += amount * rate;
            previous = upper;
            if (income <= upper) return tax;
        }
        return tax + Math.max(0.0, income - 2400000.0) * 0.30;
    }

    static double taxAfterRebate(double taxableIncome, boolean residentIndividual) {
        double income = Math.max(0.0, taxableIncome);
        double tax = slabTax(income);
        if (!residentIndividual) return tax;
        // Section 87A rebate and marginal relief: tax cannot exceed the income
        // above ₹12 lakh for a resident individual under the new regime.
        if (income <= 1200000.0) return 0.0;
        return Math.min(tax, income - 1200000.0);
    }

    private static double taxWithSurcharge(double baseTax, double income, boolean residentIndividual) {
        if (baseTax <= 0.0 || income <= 5000000.0) return baseTax;
        double rate;
        double threshold;
        double previousRate;
        if (income > 20000000.0) {
            rate = 0.25;
            threshold = 20000000.0;
            previousRate = 0.15;
        } else if (income > 10000000.0) {
            rate = 0.15;
            threshold = 10000000.0;
            previousRate = 0.10;
        } else {
            rate = 0.10;
            threshold = 5000000.0;
            previousRate = 0.0;
        }
        double normalTaxWithSurcharge = baseTax * (1.0 + rate);
        double taxAtThreshold = taxAfterRebate(threshold, residentIndividual);
        double marginalReliefLimit = taxAtThreshold * (1.0 + previousRate)
                + (income - threshold);
        return Math.min(normalTaxWithSurcharge, marginalReliefLimit);
    }

    private static Inputs copy(Inputs in) {
        Inputs c = new Inputs();
        c.annualBase = in.annualBase;
        c.targetMonthlyTakeHome = in.targetMonthlyTakeHome;
        c.basicSharePct = in.basicSharePct;
        c.employerNpsPctOfBasic = in.employerNpsPctOfBasic;
        c.employeePfPctOfBasic = in.employeePfPctOfBasic;
        c.employerPfPctOfBasic = in.employerPfPctOfBasic;
        c.gratuityPctOfBasic = in.gratuityPctOfBasic;
        c.carLeaseMonthly = in.carLeaseMonthly;
        c.eligibleReimbursementAnnual = in.eligibleReimbursementAnnual;
        c.professionalTaxAnnual = in.professionalTaxAnnual;
        c.otherPostTaxDeductionsAnnual = in.otherPostTaxDeductionsAnnual;
        c.carLeasePreTax = in.carLeasePreTax;
        c.residentIndividual = in.residentIndividual;
        return c;
    }

    private static void validate(Inputs in) {
        if (in == null) throw new IllegalArgumentException("Salary inputs are required.");
        requireNonNegative(in.annualBase, "Annual base");
        requireNonNegative(in.targetMonthlyTakeHome, "Monthly target");
        requirePercent(in.basicSharePct, "Basic share");
        requirePercent(in.employerNpsPctOfBasic, "Employer NPS rate");
        requirePercent(in.employeePfPctOfBasic, "Employee PF rate");
        requirePercent(in.employerPfPctOfBasic, "Employer PF rate");
        requirePercent(in.gratuityPctOfBasic, "Gratuity rate");
        requireNonNegative(in.carLeaseMonthly, "Monthly car lease");
        requireNonNegative(in.eligibleReimbursementAnnual, "Eligible reimbursement");
        requireNonNegative(in.professionalTaxAnnual, "Professional tax");
        requireNonNegative(in.otherPostTaxDeductionsAnnual, "Other deductions");
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(label + " must be a valid non-negative number.");
        }
    }

    private static void requirePercent(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(label + " must be between 0% and 100%.");
        }
    }
}
