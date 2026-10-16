package com.mihirkumar.dayflow;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.*;

public class ScheduleRulesTest {
    @Test public void rejectsInvalidOrCrossDayIntervals() {
        assertTrue(ScheduleRules.isValidInterval(8 * 60, 9 * 60));
        assertFalse(ScheduleRules.isValidInterval(-1, 60));
        assertFalse(ScheduleRules.isValidInterval(24 * 60, 24 * 60 + 30));
        assertFalse(ScheduleRules.isValidInterval(10 * 60, 10 * 60));
        assertFalse(ScheduleRules.isValidInterval(11 * 60, 10 * 60));
        assertFalse(ScheduleRules.isValidInterval(23 * 60, 24 * 60));
    }

    @Test public void overlappingIntervalsAreRejectedButAdjacentIntervalsAreAllowed() {
        assertTrue(ScheduleRules.overlaps(9 * 60, 10 * 60, 9 * 60 + 30, 10 * 60 + 30));
        assertTrue(ScheduleRules.overlaps(8 * 60, 9 * 60, 8 * 60 + 45, 9 * 60 + 15));
        assertFalse(ScheduleRules.overlaps(8 * 60, 9 * 60, 9 * 60, 10 * 60));
        assertFalse(ScheduleRules.overlaps(9 * 60, 10 * 60, 8 * 60, 9 * 60));
        assertFalse(ScheduleRules.overlaps(10 * 60, 11 * 60, 8 * 60, 9 * 60));
    }

    @Test public void invalidExistingIntervalsDoNotCreateFalseConflicts() {
        assertFalse(ScheduleRules.overlaps(9 * 60, 10 * 60, 11 * 60, 10 * 60));
        assertFalse(ScheduleRules.overlaps(9 * 60, 10 * 60, -10, 30));
        assertFalse(ScheduleRules.overlaps(10 * 60, 9 * 60, 8 * 60, 11 * 60));
    }

    @Test public void dailyRecurrenceIncludesEveryDay() {
        for (int day = Calendar.SUNDAY; day <= Calendar.SATURDAY; day++) {
            assertTrue(ScheduleRules.shouldRepeatOnDay("daily", day));
        }
        assertFalse(ScheduleRules.shouldRepeatOnDay("never", Calendar.MONDAY));
        assertFalse(ScheduleRules.shouldRepeatOnDay("unknown", Calendar.MONDAY));
    }

    @Test public void weekdayRecurrenceExcludesSaturdayAndSunday() {
        assertTrue(ScheduleRules.shouldRepeatOnDay("weekdays", Calendar.MONDAY));
        assertTrue(ScheduleRules.shouldRepeatOnDay("weekdays", Calendar.TUESDAY));
        assertTrue(ScheduleRules.shouldRepeatOnDay("weekdays", Calendar.WEDNESDAY));
        assertTrue(ScheduleRules.shouldRepeatOnDay("weekdays", Calendar.THURSDAY));
        assertTrue(ScheduleRules.shouldRepeatOnDay("weekdays", Calendar.FRIDAY));
        assertFalse(ScheduleRules.shouldRepeatOnDay("weekdays", Calendar.SATURDAY));
        assertFalse(ScheduleRules.shouldRepeatOnDay("weekdays", Calendar.SUNDAY));
    }
}
