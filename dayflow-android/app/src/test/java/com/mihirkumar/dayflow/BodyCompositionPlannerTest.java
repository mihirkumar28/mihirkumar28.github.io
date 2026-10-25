package com.mihirkumar.dayflow;

import org.junit.Test;
import static org.junit.Assert.*;

public class BodyCompositionPlannerTest {
    @Test public void includesDailyStairsAndAveragedGymBurnInTdee() {
        BodyCompositionPlanner.Inputs in = new BodyCompositionPlanner.Inputs();
        BodyCompositionPlanner.Result result = BodyCompositionPlanner.calculate(in);
        assertEquals(2212.0 * 1.2 + 300.0 + 500.0 * 5.0 / 7.0,
                result.estimatedTdeeTodayKcal, 0.01);
    }

    @Test public void targetWeightMatchesSelectedFatPartition() {
        BodyCompositionPlanner.Inputs in = new BodyCompositionPlanner.Inputs();
        in.weightLossFromFatPct = 100.0;
        BodyCompositionPlanner.Result result = BodyCompositionPlanner.calculate(in);
        double startFat = in.startWeightKg * in.startBodyFatPct / 100.0;
        double loss = in.startWeightKg - result.targetWeightKg;
        double endFat = startFat - loss;
        assertEquals(15.0, 100.0 * endFat / result.targetWeightKg, 0.01);
        assertEquals(100.3, result.targetWeightKg, 0.2);
    }

    @Test public void losingLessOfTotalWeightAsFatRequiresLowerTargetWeight() {
        BodyCompositionPlanner.Inputs leanPreservation = new BodyCompositionPlanner.Inputs();
        leanPreservation.weightLossFromFatPct = 100.0;
        BodyCompositionPlanner.Inputs mixedLoss = new BodyCompositionPlanner.Inputs();
        mixedLoss.weightLossFromFatPct = 85.0;
        assertTrue(BodyCompositionPlanner.calculate(mixedLoss).targetWeightKg
                < BodyCompositionPlanner.calculate(leanPreservation).targetWeightKg);
    }

    @Test public void lowerIntakeProjectsLowerEndingWeight() {
        BodyCompositionPlanner.Inputs in = new BodyCompositionPlanner.Inputs();
        assertTrue(BodyCompositionPlanner.simulateWeight(in, 1800)
                < BodyCompositionPlanner.simulateWeight(in, 2200));
    }

    @Test public void invalidGoalIsRejected() {
        BodyCompositionPlanner.Inputs in = new BodyCompositionPlanner.Inputs();
        in.targetBodyFatPct = 40;
        try {
            BodyCompositionPlanner.calculate(in);
            fail("Expected invalid goal to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Goal body fat"));
        }
    }
}
