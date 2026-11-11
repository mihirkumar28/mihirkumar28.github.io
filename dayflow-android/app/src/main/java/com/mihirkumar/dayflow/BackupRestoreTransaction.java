package com.mihirkumar.dayflow;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Replaces a preference snapshot and attempts to restore the prior snapshot if
 * the replacement cannot be committed. Android-independent so failure behavior
 * can be exercised by unit tests.
 */
final class BackupRestoreTransaction {
    interface Store {
        Map<String, Object> readAll();
        boolean replaceAll(Map<String, Object> values);
    }

    private BackupRestoreTransaction() {}

    static void restore(Store store, Map<String, ?> replacementValues) {
        if (store == null) throw new IllegalArgumentException("Preference store is required.");

        // Copy and type-check both maps before the store is mutated. String sets must
        // be copied because SharedPreferences exposes mutable Set instances.
        Map<String, Object> previous = copySupportedValues(store.readAll());
        Map<String, Object> replacement = copySupportedValues(replacementValues);

        RuntimeException failure;
        try {
            if (store.replaceAll(copySupportedValues(replacement))) return;
            failure = new IllegalStateException("Android could not save the restored data.");
        } catch (RuntimeException e) {
            failure = e;
        }

        boolean rolledBack = false;
        try {
            rolledBack = store.replaceAll(copySupportedValues(previous));
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }

        if (rolledBack) {
            throw new IllegalStateException(
                    "Backup restore failed; previous preferences were restored.", failure);
        }
        throw new IllegalStateException(
                "Backup restore failed and previous preferences could not be fully re-saved.",
                failure);
    }

    private static Map<String, Object> copySupportedValues(Map<String, ?> source) {
        if (source == null) {
            throw new IllegalArgumentException("Preference snapshot is unavailable.");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) {
                throw new IllegalArgumentException("Preference snapshot contains a null key or value.");
            }
            if (value instanceof String || value instanceof Boolean
                    || value instanceof Integer || value instanceof Long
                    || value instanceof Float) {
                copy.put(key, value);
            } else if (value instanceof Set) {
                Set<String> setCopy = new HashSet<>();
                for (Object member : (Set<?>) value) {
                    if (!(member instanceof String)) {
                        throw new IllegalArgumentException(
                                "Preference '" + key + "' contains a non-string set member.");
                    }
                    setCopy.add((String) member);
                }
                copy.put(key, setCopy);
            } else {
                throw new IllegalArgumentException(
                        "Preference '" + key + "' has an unsupported value type.");
            }
        }
        return copy;
    }
}
