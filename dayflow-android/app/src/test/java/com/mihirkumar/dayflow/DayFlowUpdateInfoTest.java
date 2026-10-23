package com.mihirkumar.dayflow;

import org.json.JSONException;
import org.junit.Test;

import static org.junit.Assert.*;

public class DayFlowUpdateInfoTest {
    @Test public void parsesPublishedReleaseMetadata() throws Exception {
        DayFlowUpdateInfo.ReleaseInfo info = DayFlowUpdateInfo.parse(
                "{\"versionCode\":42,\"versionName\":\"1.0.42\",\"releaseNotes\":\"Fixes\"}");
        assertEquals(42L, info.versionCode);
        assertEquals("1.0.42", info.versionName);
        assertEquals("Fixes", info.releaseNotes);
    }

    @Test public void onlyPromptsForANewerVersionCode() throws Exception {
        DayFlowUpdateInfo.ReleaseInfo info = DayFlowUpdateInfo.parse(
                "{\"versionCode\":42,\"versionName\":\"1.0.42\"}");
        assertTrue(DayFlowUpdateInfo.isNewerThan(41L, info));
        assertFalse(DayFlowUpdateInfo.isNewerThan(42L, info));
        assertFalse(DayFlowUpdateInfo.isNewerThan(43L, info));
    }

    @Test public void deferredUpdateIsOfferedAgainAfterSnoozeExpires() throws Exception {
        DayFlowUpdateInfo.ReleaseInfo info = DayFlowUpdateInfo.parse(
                "{\\"versionCode\\":42,\\"versionName\\":\\"1.0.42\\"}");
        assertFalse(DayFlowUpdateInfo.shouldPromptForUpdate(false, 41L, info, 42L, 5000L, 4999L));
        assertTrue(DayFlowUpdateInfo.shouldPromptForUpdate(false, 41L, info, 42L, 5000L, 5000L));
    }

    @Test public void newerReleaseBypassesSnoozeForOlderVersion() throws Exception {
        DayFlowUpdateInfo.ReleaseInfo info = DayFlowUpdateInfo.parse(
                "{\\"versionCode\\":43,\\"versionName\\":\\"1.0.43\\"}");
        assertTrue(DayFlowUpdateInfo.shouldPromptForUpdate(false, 41L, info, 42L, Long.MAX_VALUE, 1000L));
    }

    @Test public void manualCheckBypassesSnooze() throws Exception {
        DayFlowUpdateInfo.ReleaseInfo info = DayFlowUpdateInfo.parse(
                "{\\"versionCode\\":42,\\"versionName\\":\\"1.0.42\\"}");
        assertTrue(DayFlowUpdateInfo.shouldPromptForUpdate(true, 41L, info, 42L, Long.MAX_VALUE, 1000L));
    }

    @Test public void rejectsMetadataWithoutVersionIdentity() {
        try {
            DayFlowUpdateInfo.parse("{\"releaseNotes\":\"Missing version\"}");
            fail("Invalid metadata must be rejected.");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("versionCode"));
        }
    }
}
