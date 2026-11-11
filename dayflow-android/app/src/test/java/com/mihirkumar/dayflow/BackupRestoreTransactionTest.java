package com.mihirkumar.dayflow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class BackupRestoreTransactionTest {
    @Test public void successfulRestoreReplacesOldValuesAndCopiesStringSets() {
        MemoryStore store = new MemoryStore();
        store.values.put("old-plan", "keep me only in the original snapshot");
        store.values.put("old-tracks", new HashSet<>(Collections.singleton("Legacy")));
        Set<String> requestedTracks = new HashSet<>(Arrays.asList("GS", "Maths"));
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("new-plan", "October");
        replacement.put("tracks", requestedTracks);

        BackupRestoreTransaction.restore(store, replacement);

        assertFalse(store.values.containsKey("old-plan"));
        assertEquals("October", store.values.get("new-plan"));
        assertEquals(new HashSet<>(Arrays.asList("GS", "Maths")), store.values.get("tracks"));
        requestedTracks.add("mutated later");
        assertEquals(new HashSet<>(Arrays.asList("GS", "Maths")), store.values.get("tracks"));
    }

    @Test public void failedReplacementAttemptsToRestorePreviousSnapshot() {
        MemoryStore store = storeWithOriginalData();
        store.failAttempts.add(1);

        try {
            BackupRestoreTransaction.restore(store,
                    Collections.<String, Object>singletonMap("new-plan", "replacement"));
            fail("A failed preference commit must not be reported as successful.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("previous preferences were restored"));
        }

        assertEquals("original", store.values.get("plan"));
        assertEquals(new HashSet<>(Collections.singleton("GS")), store.values.get("tracks"));
        assertFalse(store.values.containsKey("new-plan"));
        assertEquals(2, store.replaceAttempts);
    }

    @Test public void reportsWhenRollbackCommitAlsoFails() {
        MemoryStore store = storeWithOriginalData();
        store.failAttempts.add(1);
        store.failAttempts.add(2);

        try {
            BackupRestoreTransaction.restore(store,
                    Collections.<String, Object>singletonMap("new-plan", "replacement"));
            fail("A failed replacement and failed rollback must not be reported as success.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("could not be fully re-saved"));
        }
        assertEquals(2, store.replaceAttempts);
    }

    @Test public void rejectsUnsupportedReplacementValuesBeforeMutatingStore() {
        MemoryStore store = storeWithOriginalData();
        Map<String, Object> invalid = new HashMap<>();
        invalid.put("unsupported", new Object());

        try {
            BackupRestoreTransaction.restore(store, invalid);
            fail("Unsupported values must be rejected before any write.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unsupported value type"));
        }

        assertEquals(0, store.replaceAttempts);
        assertEquals("original", store.values.get("plan"));
    }

    private MemoryStore storeWithOriginalData() {
        MemoryStore store = new MemoryStore();
        store.values.put("plan", "original");
        store.values.put("tracks", new HashSet<>(Collections.singleton("GS")));
        return store;
    }

    private static final class MemoryStore implements BackupRestoreTransaction.Store {
        final Map<String, Object> values = new LinkedHashMap<>();
        final Set<Integer> failAttempts = new HashSet<>();
        int replaceAttempts;

        @Override public Map<String, Object> readAll() {
            return deepCopy(values);
        }

        @Override public boolean replaceAll(Map<String, Object> replacement) {
            replaceAttempts++;
            values.clear();
            values.putAll(deepCopy(replacement));
            return !failAttempts.contains(replaceAttempts);
        }

        private static Map<String, Object> deepCopy(Map<String, Object> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Set) {
                    result.put(entry.getKey(), new HashSet<>((Set<?>) value));
                } else {
                    result.put(entry.getKey(), value);
                }
            }
            return result;
        }
    }
}
