package com.mihirkumar.dayflow;

import org.junit.Test;
import static org.junit.Assert.*;

public class IndiaSalaryCalculatorTest {
    @Test public void defaultInputsProducePositiveTakeHomeAndFixedCtc() {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(inputs);
        assertTrue(result.monthlyTakeHome > 0.0);
        assertEquals(4000000.0 + result.employerPf + result.gratuity,
                result.fixedCtc, 0.01);
        assertEquals(result.annualTakeHome / 12.0, result.monthlyTakeHome, 0.01);
    }

    @Test public void employerNpsIsCarvedOutOfBaseAndNotAddedToCtcAgain() {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(inputs);
        assertEquals(result.basic * 0.14, result.employerNps, 0.01);
        assertEquals(result.basic * 0.14, result.employerNpsDeduction, 0.01);
        assertEquals(inputs.annualBase + result.employerPf + result.gratuity,
                result.fixedCtc, 0.01);
    }

    @Test public void reimbursementReducesTaxableIncomeButIsNotAddedToCashTwice() {
        IndiaSalaryCalculator.Inputs withReimbursement = new IndiaSalaryCalculator.Inputs();
        IndiaSalaryCalculator.Inputs withoutReimbursement = new IndiaSalaryCalculator.Inputs();
        withoutReimbursement.eligibleReimbursementAnnual = 0.0;
        IndiaSalaryCalculator.Result with = IndiaSalaryCalculator.calculate(withReimbursement);
        IndiaSalaryCalculator.Result without = IndiaSalaryCalculator.calculate(withoutReimbursement);
        assertEquals(360000.0, without.taxableIncome - with.taxableIncome, 0.01);
        assertTrue(with.monthlyTakeHome > without.monthlyTakeHome);
        assertTrue(with.monthlyTakeHome - without.monthlyTakeHome < 360000.0 / 12.0);
    }

    @Test public void newRegimeResidentRebateAndMarginalReliefAreApplied() {
        assertEquals(0.0, IndiaSalaryCalculator.taxAfterRebate(1200000.0, true), 0.01);
        assertEquals(10000.0, IndiaSalaryCalculator.taxAfterRebate(1210000.0, true), 0.01);
        assertTrue(IndiaSalaryCalculator.taxAfterRebate(1210000.0, false) > 10000.0);
    }

    @Test public void targetSolverFindsBaseThatMeetsRequestedMonthlyCash() {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        inputs.targetMonthlyTakeHome = 250000.0;
        double solvedBase = IndiaSalaryCalculator.solveAnnualBaseForMonthlyTarget(inputs);
        inputs.annualBase = solvedBase;
        IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(inputs);
        assertEquals(250000.0, result.monthlyTakeHome, 1.0);
        assertTrue(result.fixedCtc >= solvedBase);
    }

    @Test public void surchargeMarginalReliefLimitsTaxAtTheThreshold() {
        double atThreshold = IndiaSalaryCalculator.taxAfterRebate(5000000.0, true);
        double justAbove = IndiaSalaryCalculator.taxAfterRebate(5010000.0, true);
        IndiaSalaryCalculator.Result result = surchargeResult(5010000.0);
        // Standard deduction leaves taxable income ₹10,000 above the threshold.
        // Surcharge marginal relief caps tax at threshold tax plus that excess.
        assertTrue(result.taxBeforeSurcharge + result.surcharge
                <= atThreshold + (result.taxableIncome - 5000000.0) + 0.01);
        assertTrue(justAbove > atThreshold);
    }

    private IndiaSalaryCalculator.Result surchargeResult(double income) {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        inputs.annualBase = income;
        inputs.eligibleReimbursementAnnual = 0;
        inputs.carLeaseMonthly = 0;
        inputs.professionalTaxAnnual = 0;
        inputs.basicSharePct = 0;
        return IndiaSalaryCalculator.calculate(inputs);
    }
}
