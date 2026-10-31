package com.mihirkumar.dayflow;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlotReminderRulesTest {
    @Test
    public void alertsForPendingSlotsAtLeastTenMinutesLong() {
        assertTrue(SlotReminderRules.shouldAlert("GS study", "pending", 360, 480));
        assertTrue(SlotReminderRules.shouldAlert("Work", "PENDING", 540, 600));
        assertFalse(SlotReminderRules.shouldAlert("Quick break", "pending", 600, 605));
        assertFalse(SlotReminderRules.shouldAlert("Ten-minute slot", "pending", 600, 610));
    }

    @Test
    public void excludesMorningAlarmAndWakeUpBlocks() {
        assertFalse(SlotReminderRules.shouldAlert("Morning Alarm", "pending", 300, 315));
        assertFalse(SlotReminderRules.shouldAlert("Wake up + routine", "pending", 360, 390));
        assertFalse(SlotReminderRules.shouldAlert("Morning alarm / wake-up", "pending", 300, 320));
    }

    @Test
    public void skipsCompletedOrMalformedSlots() {
        assertFalse(SlotReminderRules.shouldAlert("Maths", "done", 600, 720));
        assertFalse(SlotReminderRules.shouldAlert("Maths", "pending", 720, 600));
    }
}
