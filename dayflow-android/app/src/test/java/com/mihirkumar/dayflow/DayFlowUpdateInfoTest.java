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

    @Test public void rejectsMetadataWithoutVersionIdentity() {
        try {
            DayFlowUpdateInfo.parse("{\"releaseNotes\":\"Missing version\"}");
            fail("Invalid metadata must be rejected.");
        } catch (JSONException expected) {
            assertTrue(expected.getMessage().contains("versionCode"));
        }
    }
}
