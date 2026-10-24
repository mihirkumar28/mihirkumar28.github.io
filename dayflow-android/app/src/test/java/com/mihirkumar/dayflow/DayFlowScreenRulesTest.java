package com.mihirkumar.dayflow;

import org.junit.Test;
import static org.junit.Assert.*;

public class DayFlowScreenRulesTest {
    @Test public void weekendUsesWeekendTemplateRegardlessOfWorkArrangement() {
        assertEquals("weekend", DayFlowScreenRules.defaultTemplateKey(true, "office"));
        assertEquals("weekend", DayFlowScreenRules.defaultTemplateKey(true, "wfh"));
    }

    @Test public void weekdayTemplateFollowsWorkArrangement() {
        assertEquals("office", DayFlowScreenRules.defaultTemplateKey(false, "office"));
        assertEquals("wfh", DayFlowScreenRules.defaultTemplateKey(false, "wfh"));
        assertEquals("wfh", DayFlowScreenRules.defaultTemplateKey(false, "legacy-value"));
    }

    @Test public void legacyWeekendModeNormalizesToLastValidArrangement() {
        assertEquals("office", DayFlowScreenRules.normalizeWorkArrangement("weekend", "office"));
        assertEquals("wfh", DayFlowScreenRules.normalizeWorkArrangement("weekend", "wfh"));
        assertEquals("wfh", DayFlowScreenRules.normalizeWorkArrangement("weekend", "weekend"));
    }

    @Test public void routineProfileIsExactlyWfhWfoOrWeekend() {
        assertEquals("wfh", DayFlowScreenRules.resolveRoutineProfile(null, false, "wfh"));
        assertEquals("office", DayFlowScreenRules.resolveRoutineProfile(null, false, "office"));
        assertEquals("weekend", DayFlowScreenRules.resolveRoutineProfile(null, true, "office"));
        assertEquals("weekend", DayFlowScreenRules.resolveRoutineProfile("weekend", false, "office"));
        assertEquals("office", DayFlowScreenRules.resolveRoutineProfile("office", true, "wfh"));
        assertEquals("wfh", DayFlowScreenRules.resolveRoutineProfile("wfh", true, "office"));
        assertEquals("office", DayFlowScreenRules.resolveRoutineProfile("weekday", false, "office"));
        assertEquals("wfh", DayFlowScreenRules.resolveRoutineProfile("invalid", false, "unknown"));
    }

    @Test public void emptyPlanIsNotMarkedComplete() {
        assertEquals(DayFlowScreenRules.TodayState.EMPTY,
                DayFlowScreenRules.resolveTodayState(0, 0, false, false));
    }

    @Test public void currentAndUpcomingTasksTakePriority() {
        assertEquals(DayFlowScreenRules.TodayState.IN_PROGRESS,
                DayFlowScreenRules.resolveTodayState(4, 1, true, true));
        assertEquals(DayFlowScreenRules.TodayState.UPCOMING,
                DayFlowScreenRules.resolveTodayState(4, 1, false, true));
    }

    @Test public void completeAndUnfinishedPastPlansHaveDifferentStates() {
        assertEquals(DayFlowScreenRules.TodayState.COMPLETE,
                DayFlowScreenRules.resolveTodayState(3, 3, false, false));
        assertEquals(DayFlowScreenRules.TodayState.NEEDS_REVIEW,
                DayFlowScreenRules.resolveTodayState(3, 1, false, false));
        assertEquals(DayFlowScreenRules.TodayState.NEEDS_REVIEW,
                DayFlowScreenRules.resolveTodayState(3, 0, false, false));
    }
}
