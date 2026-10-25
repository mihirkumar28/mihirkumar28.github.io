package com.mihirkumar.dayflow;

/**
 * Transparent scenario calculator for DayFlow's body-composition planning tab.
 *
 * This is deliberately a simplified energy-balance model, not a clinical prediction
 * or a replacement for the NIDDK Body Weight Planner. All parameters are user editable.
 */
final class BodyCompositionPlanner {
    static final double KCAL_PER_KG = 7700.0;

    private BodyCompositionPlanner() {}

    static final class Inputs {
        double startWeightKg = 135.8;
        double heightCm = 187.1;
        double startBodyFatPct = 37.2;
        double bmrKcal = 2212.0;
        double intakeKcal = 1800.0;
        double dailyStairsKcal = 300.0;
        double gymCaloriesPerSession = 500.0;
        double gymDaysPerWeek = 5.0;
        double baselineActivityMultiplier = 1.2;
        double targetBodyFatPct = 15.0;
        double weightLossFromFatPct = 85.0;
        double exerciseCalorieCreditPct = 100.0;
        double extraExpenditureReductionKcal = 100.0;
        int daysToTarget = 212;
    }

    static final class Result {
        double estimatedTdeeTodayKcal;
        double initialDailyDeficitKcal;
        double projectedWeightKg;
        double projectedWeightChangeKg;
        double projectedFatMassKg;
        double projectedBodyFatPct;
        double estimatedTdeeAtTargetKcal;
        double targetWeightKg;
        double totalWeightLossRequiredKg;
        double requiredWeightLossPerWeekKg;
        double requiredIntakeKcal;
        double startingBmi;
        double projectedBmi;
        String requiredIntakeStatus;
    }

    static Result calculate(Inputs in) {
        if (in == null) throw new IllegalArgumentException("Inputs are required.");
        requireRange(in.startWeightKg, 30, 350, "Weight");
        requireRange(in.heightCm, 100, 240, "Height");
        requireRange(in.startBodyFatPct, 2, 70, "Body fat");
        requireRange(in.bmrKcal, 500, 5000, "BMR");
        requireRange(in.intakeKcal, 800, 5000, "Food intake");
        requireRange(in.dailyStairsKcal, 0, 2000, "Stair calories");
        requireRange(in.gymCaloriesPerSession, 0, 2000, "Gym calories");
        requireRange(in.gymDaysPerWeek, 0, 7, "Gym sessions/week");
        requireRange(in.baselineActivityMultiplier, 1, 2.2, "Baseline multiplier");
        requireRange(in.targetBodyFatPct, 5, 35, "Goal body fat");
        requireRange(in.weightLossFromFatPct, 50, 100, "Weight loss from fat");
        requireRange(in.exerciseCalorieCreditPct, 0, 100, "Exercise calorie credit");
        requireRange(in.extraExpenditureReductionKcal, 0, 500, "Extra expenditure reduction");
        if (in.daysToTarget < 1 || in.daysToTarget > 1095) {
            throw new IllegalArgumentException("Target date must be within the next 1–1,095 days.");
        }
        if (in.targetBodyFatPct >= in.startBodyFatPct) {
            throw new IllegalArgumentException("Goal body fat must be below your starting body-fat percentage.");
        }
        if (in.weightLossFromFatPct <= in.targetBodyFatPct) {
            throw new IllegalArgumentException("The assumed fat share of weight loss must exceed your goal body-fat percentage.");
        }

        double exerciseDaily = (in.dailyStairsKcal
                + in.gymCaloriesPerSession * in.gymDaysPerWeek / 7.0)
                * in.exerciseCalorieCreditPct / 100.0;
        double tdeeToday = in.bmrKcal * in.baselineActivityMultiplier + exerciseDaily;
        double projectedWeight = simulateWeight(in, in.intakeKcal);
        double change = in.startWeightKg - projectedWeight;
        double startingFatMass = in.startWeightKg * in.startBodyFatPct / 100.0;
        double share = in.weightLossFromFatPct / 100.0;
        double projectedFatMass = clamp(startingFatMass - change * share, 0, projectedWeight);
        double projectedBodyFat = 100.0 * projectedFatMass / projectedWeight;
        double scale = Math.pow(projectedWeight / in.startWeightKg, 0.75);
        double exerciseScale = Math.pow(projectedWeight / in.startWeightKg, 0.75);
        double tdeeAtTarget = Math.max(800, in.bmrKcal * in.baselineActivityMultiplier * scale
                + exerciseDaily * exerciseScale - in.extraExpenditureReductionKcal);

        double targetPct = in.targetBodyFatPct / 100.0;
        double targetLoss = (startingFatMass - targetPct * in.startWeightKg) / (share - targetPct);
        double targetWeight = Math.max(40, in.startWeightKg - targetLoss);
        String status = "within";
        double targetIntake;
        double weightAtLow = simulateWeight(in, 500);
        double weightAtHigh = simulateWeight(in, 4000);
        if (weightAtLow > targetWeight) {
            status = "below";
            targetIntake = 500;
        } else if (weightAtHigh < targetWeight) {
            status = "above";
            targetIntake = 4000;
        } else {
            double low = 500;
            double high = 4000;
            for (int i = 0; i < 50; i++) {
                double middle = (low + high) / 2.0;
                double predicted = simulateWeight(in, middle);
                if (predicted < targetWeight) low = middle;
                else high = middle;
            }
            targetIntake = (low + high) / 2.0;
        }

        Result out = new Result();
        out.estimatedTdeeTodayKcal = tdeeToday;
        out.initialDailyDeficitKcal = tdeeToday - in.intakeKcal;
        out.projectedWeightKg = projectedWeight;
        out.projectedWeightChangeKg = projectedWeight - in.startWeightKg;
        out.projectedFatMassKg = projectedFatMass;
        out.projectedBodyFatPct = projectedBodyFat;
        double heightMeters = in.heightCm / 100.0;
        out.startingBmi = in.startWeightKg / (heightMeters * heightMeters);
        out.projectedBmi = projectedWeight / (heightMeters * heightMeters);
        out.estimatedTdeeAtTargetKcal = tdeeAtTarget;
        out.targetWeightKg = targetWeight;
        out.totalWeightLossRequiredKg = targetLoss;
        out.requiredWeightLossPerWeekKg = targetLoss / (in.daysToTarget / 7.0);
        out.requiredIntakeKcal = targetIntake;
        out.requiredIntakeStatus = status;
        return out;
    }

    static double simulateWeight(Inputs in, double dailyIntakeKcal) {
        double weight = in.startWeightKg;
        double exerciseDaily = (in.dailyStairsKcal
                + in.gymCaloriesPerSession * in.gymDaysPerWeek / 7.0)
                * in.exerciseCalorieCreditPct / 100.0;
        int days = Math.max(1, in.daysToTarget);
        for (int day = 0; day < days; day++) {
            double ratio = Math.max(0.1, weight / in.startWeightKg);
            double scale = Math.pow(ratio, 0.75);
            double adaptationProgress = days <= 1 ? 1.0 : day / (double) (days - 1);
            double tdee = in.bmrKcal * in.baselineActivityMultiplier * scale
                    + exerciseDaily * scale
                    - in.extraExpenditureReductionKcal * adaptationProgress;
            tdee = Math.max(800, tdee);
            weight -= (tdee - dailyIntakeKcal) / KCAL_PER_KG;
            weight = Math.max(40, weight);
        }
        return weight;
    }

    private static void requireRange(double value, double min, double max, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max + ".");
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
