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

    @Test public void defaultEvMixedUsePerquisiteIsCalculatedAsTwoThousandPerMonth() {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(inputs);
        assertEquals(2000.0, result.carPerquisiteMonthly, 0.01);
        assertEquals(24000.0, result.carPerquisiteAnnual, 0.01);
    }

    @Test public void employerPaidRunningCostsAndChauffeurChangePerquisiteAutomatically() {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        inputs.carOperatingCostsPaidByEmployer = true;
        inputs.carChauffeurProvided = true;
        IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(inputs);
        assertEquals(8000.0, result.carPerquisiteMonthly, 0.01);
        assertEquals(96000.0, result.carPerquisiteAnnual, 0.01);
    }

    @Test public void largerEngineUsesHigherRule15TableIIValue() {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        inputs.carEngineAbove16L = true;
        IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(inputs);
        assertEquals(3000.0, result.carPerquisiteMonthly, 0.01);
    }

    @Test public void exclusivelyOfficialUseIsNilOnlyWhenClaimedWithRecords() {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        inputs.carUsedWhollyOfficial = true;
        IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(inputs);
        assertEquals(0.0, result.carPerquisiteMonthly, 0.01);
        assertEquals(0.0, result.carPerquisiteAnnual, 0.01);
    }

    @Test public void leaseEmiIsNotThePerquisiteAndCalculatedPerquisiteIsAddedToTaxableIncome() {
        IndiaSalaryCalculator.Inputs inputs = new IndiaSalaryCalculator.Inputs();
        inputs.eligibleReimbursementAnnual = 0.0;
        inputs.carLeaseMonthly = 20000.0;
        IndiaSalaryCalculator.Result result = IndiaSalaryCalculator.calculate(inputs);
        double expectedTaxable = inputs.annualBase - result.employerNpsDeduction
                - result.standardDeduction - (20000.0 * 12.0) + (2000.0 * 12.0);
        assertEquals(expectedTaxable, result.taxableIncome, 0.01);
        assertEquals(24000.0, result.carPerquisiteAnnual, 0.01);
        assertEquals(240000.0, result.carLeaseAnnual, 0.01);
    }

    @Test public void carPerquisiteAffectsTaxOnlyNotCashAsASeparatePayout() {
        IndiaSalaryCalculator.Inputs mixedUse = new IndiaSalaryCalculator.Inputs();
        mixedUse.eligibleReimbursementAnnual = 0.0;
        mixedUse.carLeaseMonthly = 20000.0;

        IndiaSalaryCalculator.Inputs officialUse = new IndiaSalaryCalculator.Inputs();
        officialUse.eligibleReimbursementAnnual = 0.0;
        officialUse.carLeaseMonthly = 20000.0;
        officialUse.carUsedWhollyOfficial = true;

        IndiaSalaryCalculator.Result mixed = IndiaSalaryCalculator.calculate(mixedUse);
        IndiaSalaryCalculator.Result official = IndiaSalaryCalculator.calculate(officialUse);
        assertEquals(24000.0, mixed.taxableIncome - official.taxableIncome, 0.01);
        assertEquals(240000.0, mixed.carLeaseAnnual, 0.01);
        assertEquals(240000.0, official.carLeaseAnnual, 0.01);
        // The take-home difference is only the additional income tax attributable
        // to the perquisite, not the perquisite amount itself.
        assertTrue(mixed.annualTakeHome < official.annualTakeHome);
        assertTrue(official.annualTakeHome - mixed.annualTakeHome < 24000.0);
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
        IndiaSalaryCalculator.Result result = surchargeResult(5085000.0);
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
