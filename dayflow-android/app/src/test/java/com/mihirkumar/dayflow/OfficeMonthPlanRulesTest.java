package com.mihirkumar.dayflow;

import org.junit.Test;
import static org.junit.Assert.*;

public class OfficeMonthPlanRulesTest {
    @Test public void eightDaysRequireAtLeast52CumulativeHours() {
        assertTrue(OfficeMonthPlanRules.isCompliant(8, 52.0d));
        assertTrue(OfficeMonthPlanRules.isCompliant(8, 53.5d));
        assertFalse(OfficeMonthPlanRules.isCompliant(8, 51.5d));
        assertFalse(OfficeMonthPlanRules.isCompliant(8, 0d));
    }

    @Test public void fewerThanEightDaysNeverQualifyEvenWithExtraHours() {
        assertFalse(OfficeMonthPlanRules.isCompliant(7, 70d));
        assertFalse(OfficeMonthPlanRules.isCompliant(1, 100d));
        assertFalse(OfficeMonthPlanRules.isCompliant(0, 0d));
    }

    @Test public void nineOrMoreDaysWaiveTheHourMinimum() {
        assertTrue(OfficeMonthPlanRules.isHoursMinimumWaived(9));
        assertTrue(OfficeMonthPlanRules.isCompliant(9, 0d));
        assertTrue(OfficeMonthPlanRules.isCompliant(9, 12d));
        assertTrue(OfficeMonthPlanRules.isCompliant(10, 0d));
        assertFalse(OfficeMonthPlanRules.isHoursMinimumWaived(8));
    }

    @Test public void defaultHoursForEightPlannedDaysMeetTheThreshold() {
        assertEquals(6.5d, OfficeMonthPlanRules.DEFAULT_HOURS_PER_DAY, 0.0001d);
        assertTrue(OfficeMonthPlanRules.isCompliant(8,
                OfficeMonthPlanRules.DEFAULT_HOURS_PER_DAY * OfficeMonthPlanRules.MINIMUM_DAYS));
    }

    @Test public void commuteIsTwoHoursThirtyMinutesPerOfficeDay() {
        assertEquals(75, OfficeMonthPlanRules.COMMUTE_MINUTES_EACH_WAY);
        assertEquals(150, OfficeMonthPlanRules.DAILY_COMMUTE_MINUTES);
        assertEquals(2.5d, OfficeMonthPlanRules.dailyCommuteHours(), 0.0001d);
        assertEquals(20.0d, OfficeMonthPlanRules.totalCommuteHours(8), 0.0001d);
        assertEquals(22.5d, OfficeMonthPlanRules.totalCommuteHours(9), 0.0001d);
    }

    @Test public void commuteAddsToTotalCommitmentButNeverToRequiredOfficeHours() {
        double officeHours = OfficeMonthPlanRules.DEFAULT_HOURS_PER_DAY * 8;
        assertTrue(OfficeMonthPlanRules.isCompliant(8, officeHours));
        assertEquals(72.0d, OfficeMonthPlanRules.totalCommitmentHours(officeHours, 8), 0.0001d);

        assertFalse(OfficeMonthPlanRules.isCompliant(8, 32.0d));
        assertEquals(52.0d, OfficeMonthPlanRules.totalCommitmentHours(32.0d, 8) - 20.0d, 0.0001d);
    }
}
