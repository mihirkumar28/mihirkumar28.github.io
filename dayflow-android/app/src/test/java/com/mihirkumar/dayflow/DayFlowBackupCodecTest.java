package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class DayFlowBackupCodecTest {
    private JSONObject envelope(JSONObject preferences) throws Exception {
        return new JSONObject()
                .put("app", "DayFlow")
                .put("formatVersion", DayFlowBackupCodec.FORMAT_VERSION)
                .put("exportedAt", 1792170000000L)
                .put("preferences", preferences);
    }

    @Test public void parsesEverySupportedSharedPreferenceType() throws Exception {
        JSONObject values = new JSONObject()
                .put("title", new JSONObject().put("type", "string").put("value", "GS study"))
                .put("enabled", new JSONObject().put("type", "boolean").put("value", true))
                .put("count", new JSONObject().put("type", "int").put("value", 42))
                .put("timestamp", new JSONObject().put("type", "long").put("value", 1792170000000L))
                .put("ratio", new JSONObject().put("type", "float").put("value", 0.75))
                .put("tracks", new JSONObject().put("type", "stringSet")
                        .put("value", new JSONArray().put("GS").put("Maths")));
        DayFlowBackupCodec.Document doc = DayFlowBackupCodec.parse(envelope(values).toString());

        assertEquals("GS study", doc.preferences.get("title"));
        assertEquals(Boolean.TRUE, doc.preferences.get("enabled"));
        assertEquals(Integer.valueOf(42), doc.preferences.get("count"));
        assertEquals(Long.valueOf(1792170000000L), doc.preferences.get("timestamp"));
        assertEquals(Float.valueOf(0.75f), doc.preferences.get("ratio"));
        assertTrue(doc.preferences.get("tracks") instanceof Set);
        assertEquals(2, ((Set<?>) doc.preferences.get("tracks")).size());
        assertEquals(1792170000000L, doc.exportedAt);
    }

    @Test public void rejectsWrongAppVersionAndMissingPreferences() throws Exception {
        assertInvalid(new JSONObject().put("app", "Other").put("formatVersion", 1)
                .put("preferences", new JSONObject()).toString(), "not a DayFlow");
        assertInvalid(new JSONObject().put("app", "DayFlow").put("formatVersion", 2)
                .put("preferences", new JSONObject()).toString(), "not supported");
        assertInvalid(new JSONObject().put("app", "DayFlow").put("formatVersion", 1).toString(),
                "missing its preferences");
    }

    @Test public void rejectsMalformedEntriesRatherThanSilentlySkippingThem() throws Exception {
        assertInvalid(envelope(new JSONObject().put("broken", "not an object")).toString(),
                "must be a typed object");
        assertInvalid(envelope(new JSONObject().put("broken", new JSONObject()
                .put("type", "unknown").put("value", "x"))).toString(), "unsupported type");
        assertInvalid(envelope(new JSONObject().put("broken", new JSONObject()
                .put("type", "string").put("value", 4))).toString(), "must contain a string");
        assertInvalid(envelope(new JSONObject().put("broken", new JSONObject()
                .put("type", "boolean").put("value", "true"))).toString(), "must contain a boolean");
    }

    @Test public void rejectsNonIntegralAndOutOfRangeNumbers() throws Exception {
        assertInvalid(envelope(new JSONObject().put("broken", new JSONObject()
                .put("type", "int").put("value", 1.5))).toString(), "whole number");
        assertInvalid(envelope(new JSONObject().put("broken", new JSONObject()
                .put("type", "int").put("value", 2147483648L))).toString(), "outside the supported range");
        assertInvalid(envelope(new JSONObject().put("broken", new JSONObject()
                .put("type", "long").put("value", "123"))).toString(), "must contain a number");
    }

    @Test public void rejectsStringSetsContainingNonStringsAndOversizedDocuments() throws Exception {
        assertInvalid(envelope(new JSONObject().put("tracks", new JSONObject().put("type", "stringSet")
                .put("value", new JSONArray().put("GS").put(5)))).toString(), "non-string set item");
        try {
            DayFlowBackupCodec.parse(repeat('x', DayFlowBackupCodec.MAX_DOCUMENT_CHARS + 1));
            fail("Oversized backup should be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("too large"));
        }
    }

    @Test public void rejectsEmptyDocumentAndMissingTypedValues() throws Exception {
        assertInvalid("", "empty");
        assertInvalid(envelope(new JSONObject().put("broken", new JSONObject().put("type", "string"))).toString(),
                "missing its value");
    }

    private void assertInvalid(String raw, String expectedMessage) throws Exception {
        try {
            DayFlowBackupCodec.parse(raw);
            fail("Invalid backup must be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue("Expected message to contain " + expectedMessage + " but was: " + expected.getMessage(),
                    expected.getMessage().contains(expectedMessage));
        } catch (org.json.JSONException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
