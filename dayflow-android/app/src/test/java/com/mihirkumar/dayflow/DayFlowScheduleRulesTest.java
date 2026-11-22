package com.mihirkumar.dayflow;

import org.junit.Test;

import static org.junit.Assert.*;

public class DayFlowScheduleRulesTest {
    @Test public void acceptsValidSameDayIntervals() {
        assertTrue(DayFlowScheduleRules.isValidInterval(0, 60));
        assertTrue(DayFlowScheduleRules.isValidInterval(6 * 60, 6 * 60 + 30));
        assertTrue(DayFlowScheduleRules.isValidInterval(23 * 60 + 58, 23 * 60 + 59));
    }

    @Test public void rejectsZeroLengthReversedAndOutOfDayIntervals() {
        assertFalse(DayFlowScheduleRules.isValidInterval(10, 10));
        assertFalse(DayFlowScheduleRules.isValidInterval(11, 10));
        assertFalse(DayFlowScheduleRules.isValidInterval(-1, 10));
        assertFalse(DayFlowScheduleRules.isValidInterval(0, 24 * 60));
        assertFalse(DayFlowScheduleRules.isValidInterval(23 * 60 + 59, 23 * 60 + 59));
    }

    @Test public void adjacentIntervalsDoNotOverlap() {
        assertFalse(DayFlowScheduleRules.overlaps(9 * 60, 10 * 60, 10 * 60, 11 * 60));
        assertFalse(DayFlowScheduleRules.overlaps(10 * 60, 11 * 60, 9 * 60, 10 * 60));
    }

    @Test public void detectsPartialContainedAndIdenticalOverlaps() {
        assertTrue(DayFlowScheduleRules.overlaps(9 * 60, 11 * 60, 10 * 60, 12 * 60));
        assertTrue(DayFlowScheduleRules.overlaps(9 * 60, 12 * 60, 10 * 60, 11 * 60));
        assertTrue(DayFlowScheduleRules.overlaps(9 * 60, 11 * 60, 9 * 60, 11 * 60));
    }

    @Test public void invalidRangesNeverProduceAnOverlapMatch() {
        assertFalse(DayFlowScheduleRules.overlaps(9 * 60, 9 * 60, 8 * 60, 10 * 60));
        assertFalse(DayFlowScheduleRules.overlaps(9 * 60, 11 * 60, 10 * 60, 10 * 60));
    }
}
