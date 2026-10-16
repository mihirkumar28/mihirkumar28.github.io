package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Strict, Android-independent parser for DayFlow's typed SharedPreferences backup format.
 * All entries are validated before the UI is allowed to clear or replace persisted data.
 */
final class DayFlowBackupCodec {
    static final int FORMAT_VERSION = 1;
    static final int MAX_DOCUMENT_CHARS = 20 * 1024 * 1024;
    private static final int MAX_ENTRIES = 50000;
    private static final int MAX_KEY_LENGTH = 512;
    private static final int MAX_SET_ITEMS = 50000;

    static final class Document {
        final long exportedAt;
        final Map<String, Object> preferences;

        Document(long exportedAt, Map<String, Object> preferences) {
            this.exportedAt = exportedAt;
            this.preferences = Collections.unmodifiableMap(preferences);
        }
    }

    private DayFlowBackupCodec() {}

    static Document parse(String raw) throws JSONException {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Backup file is empty.");
        }
        if (raw.length() > MAX_DOCUMENT_CHARS) {
            throw new IllegalArgumentException("Backup is too large to restore safely.");
        }

        JSONObject root = new JSONObject(raw);
        if (!"DayFlow".equals(root.optString("app", ""))) {
            throw new IllegalArgumentException("This file is not a DayFlow backup.");
        }
        Object versionValue = root.opt("formatVersion");
        if (!(versionValue instanceof Number)
                || exactLong(versionValue, "formatVersion") != FORMAT_VERSION) {
            throw new IllegalArgumentException("This backup format is not supported.");
        }
        Object preferencesValue = root.opt("preferences");
        if (!(preferencesValue instanceof JSONObject)) {
            throw new IllegalArgumentException("Backup is missing its preferences object.");
        }

        JSONObject source = (JSONObject) preferencesValue;
        if (source.length() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Backup contains too many preference entries.");
        }
        Map<String, Object> decoded = new LinkedHashMap<>();
        java.util.Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key == null || key.trim().isEmpty() || key.length() > MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("Backup contains an invalid preference key.");
            }

            Object rawEntry = source.opt(key);
            if (!(rawEntry instanceof JSONObject)) {
                throw invalidEntry(key, "must be a typed object");
            }
            JSONObject entry = (JSONObject) rawEntry;
            Object rawType = entry.opt("type");
            if (!(rawType instanceof String)) {
                throw invalidEntry(key, "is missing its type");
            }
            String type = (String) rawType;
            Object value = entry.opt("value");
            if (value == null || value == JSONObject.NULL) {
                throw invalidEntry(key, "is missing its value");
            }

            switch (type) {
                case "string":
                    if (!(value instanceof String)) throw invalidEntry(key, "must contain a string");
                    decoded.put(key, value);
                    break;
                case "boolean":
                    if (!(value instanceof Boolean)) throw invalidEntry(key, "must contain a boolean");
                    decoded.put(key, value);
                    break;
                case "int":
                    long integer = exactLong(value, key);
                    if (integer < Integer.MIN_VALUE || integer > Integer.MAX_VALUE) {
                        throw invalidEntry(key, "contains an integer outside the supported range");
                    }
                    decoded.put(key, (int) integer);
                    break;
                case "long":
                    decoded.put(key, exactLong(value, key));
                    break;
                case "float":
                    double doubleValue = exactDouble(value, key);
                    float floatValue = (float) doubleValue;
                    if (!Float.isFinite(floatValue)) {
                        throw invalidEntry(key, "contains a number outside the supported float range");
                    }
                    decoded.put(key, floatValue);
                    break;
                case "stringSet":
                    if (!(value instanceof JSONArray)) throw invalidEntry(key, "must contain a string array");
                    JSONArray array = (JSONArray) value;
                    if (array.length() > MAX_SET_ITEMS) throw invalidEntry(key, "contains too many set items");
                    Set<String> set = new LinkedHashSet<>();
                    for (int i = 0; i < array.length(); i++) {
                        Object item = array.opt(i);
                        if (!(item instanceof String)) {
                            throw invalidEntry(key, "contains a non-string set item");
                        }
                        set.add((String) item);
                    }
                    decoded.put(key, Collections.unmodifiableSet(set));
                    break;
                default:
                    throw invalidEntry(key, "uses unsupported type '" + type + "'");
            }
        }

        long exportedAt = 0L;
        Object exportedValue = root.opt("exportedAt");
        if (exportedValue instanceof Number) {
            exportedAt = exactLong(exportedValue, "exportedAt");
        }
        return new Document(exportedAt, decoded);
    }

    private static long exactLong(Object value, String key) {
        if (!(value instanceof Number)) {
            throw invalidEntry(key, "must contain a number");
        }
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw invalidEntry(key, "must contain a whole number in the supported range");
        }
    }

    private static double exactDouble(Object value, String key) {
        if (!(value instanceof Number)) throw invalidEntry(key, "must contain a number");
        double result;
        try {
            result = new BigDecimal(value.toString()).doubleValue();
        } catch (NumberFormatException e) {
            throw invalidEntry(key, "contains an invalid number");
        }
        if (!Double.isFinite(result)) throw invalidEntry(key, "contains a non-finite number");
        return result;
    }

    private static IllegalArgumentException invalidEntry(String key, String reason) {
        return new IllegalArgumentException("Backup entry '" + key + "' " + reason + ".");
    }
}
