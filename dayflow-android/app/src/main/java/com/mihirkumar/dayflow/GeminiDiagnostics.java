package com.mihirkumar.dayflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Local-only, privacy-preserving Gemini diagnostics. Never records API keys,
 * prompts, response text, or task titles.
 */
public final class GeminiDiagnostics {
    private static final String PREFS = "dayflow_gemini_diagnostics";
    private static final String ENTRIES = "entries";
    private static final int MAX_ENTRIES = 120;

    private GeminiDiagnostics() { }

    public static synchronized void record(Context context, String event, String model,
            int attempt, int httpStatus, long elapsedMs, String requestId, String detail) {
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray current = new JSONArray(prefs.getString(ENTRIES, "[]"));
            JSONArray next = new JSONArray();
            int start = Math.max(0, current.length() - (MAX_ENTRIES - 1));
            for (int i = start; i < current.length(); i++) {
                JSONObject old = current.optJSONObject(i);
                if (old != null) next.put(old);
            }

            JSONObject entry = new JSONObject();
            entry.put("time", new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date()));
            entry.put("event", safe(event, 60));
            entry.put("model", safe(model, 100));
            entry.put("attempt", Math.max(0, attempt));
            entry.put("httpStatus", Math.max(0, httpStatus));
            entry.put("elapsedMs", Math.max(0L, elapsedMs));
            entry.put("requestId", safe(requestId, 120));
            entry.put("detail", safe(detail, 240));
            next.put(entry);
            prefs.edit().putString(ENTRIES, next.toString()).apply();
        } catch (Exception ignored) {
            // Diagnostics must never break the request itself.
        }
    }

    public static synchronized String exportText(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(ENTRIES, "[]");
        StringBuilder out = new StringBuilder();
        out.append("DayFlow Gemini diagnostics\n");
        out.append("Generated: ").append(new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date())).append("\n");
        out.append("Privacy: local request metadata only; no API key, prompt, task title, or model response text.\n");
        out.append("Entries are stored locally and are not automatically uploaded.\n\n");
        try {
            JSONArray entries = new JSONArray(raw);
            if (entries.length() == 0) return out.append("No Gemini requests recorded yet.\n").toString();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.optJSONObject(i);
                if (e == null) continue;
                out.append(e.optString("time", "?"))
                        .append(" | ").append(e.optString("event", "?"))
                        .append(" | model=").append(e.optString("model", "?"))
                        .append(" | attempt=").append(e.optInt("attempt", 0))
                        .append(" | HTTP=").append(e.optInt("httpStatus", 0))
                        .append(" | elapsedMs=").append(e.optLong("elapsedMs", 0));
                String requestId = e.optString("requestId", "");
                if (!requestId.isEmpty()) out.append(" | requestId=").append(requestId);
                String detail = e.optString("detail", "");
                if (!detail.isEmpty()) out.append(" | detail=").append(detail);
                out.append("\n");
            }
        } catch (Exception ignored) {
            out.append("Could not parse the local diagnostic entries.\n");
        }
        return out.toString();
    }

    public static synchronized void clear(Context context) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(ENTRIES).apply();
    }

    private static String safe(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        cleaned = cleaned.replaceAll("(?i)AIza[0-9A-Za-z_\\-]{20,}", "[REDACTED_API_KEY]");
        cleaned = cleaned.replaceAll("(?i)(api[_ -]?key|key)\\s*[:=]\\s*[^ ,;]+", "$1=[REDACTED]");
        if (cleaned.length() > maxLength) cleaned = cleaned.substring(0, maxLength);
        return cleaned;
    }
}
