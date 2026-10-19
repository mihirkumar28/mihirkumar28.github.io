package com.mihirkumar.dayflow;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single bundled structured source for the Roadmap and Plans & Documents screens.
 *
 * Update plans_catalog.json when the authoritative plans change and increment
 * sourcePlanVersion. The original PDFs are separate reading assets; this catalog
 * is deliberately not a runtime PDF parser.
 */
final class PlanCatalog {
    static final String ASSET_NAME = "plans_catalog.json";

    private static volatile boolean initialized;
    private static int schemaVersion;
    private static int sourcePlanVersion;
    private static String authorityNote = "";
    private static List<Month> months = Collections.emptyList();
    private static List<Document> documents = Collections.emptyList();

    private PlanCatalog() {}

    static synchronized void initialize(Context context) {
        if (initialized) return;
        try (InputStream in = context.getAssets().open(ASSET_NAME);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            initializeFromJson(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load bundled plan catalog: " + ASSET_NAME, e);
        }
    }

    /** Parses a candidate catalog before publishing any of it to the app. */
    static synchronized void initializeFromJson(String jsonText) {
        try {
            JSONObject root = new JSONObject(jsonText);
            int parsedSchemaVersion = root.optInt("schemaVersion", -1);
            int parsedSourcePlanVersion = root.optInt("sourcePlanVersion", -1);
            if (parsedSchemaVersion != 1) {
                throw new IllegalArgumentException("Unsupported plan catalog schema: " + parsedSchemaVersion);
            }
            if (parsedSourcePlanVersion < 1) {
                throw new IllegalArgumentException("sourcePlanVersion must be a positive integer.");
            }

            ArrayList<Month> parsedMonths = new ArrayList<>();
            JSONArray monthArray = root.optJSONArray("months");
            if (monthArray == null || monthArray.length() == 0) {
                throw new IllegalArgumentException("Plan catalog has no months.");
            }
            for (int i = 0; i < monthArray.length(); i++) {
                JSONObject monthJson = monthArray.optJSONObject(i);
                if (monthJson == null) throw new IllegalArgumentException("Invalid month at index " + i);
                String key = requiredString(monthJson, "key");
                JSONArray goalsJson = monthJson.optJSONArray("goals");
                if (goalsJson == null) throw new IllegalArgumentException("Missing goals for " + key);
                ArrayList<Goal> goals = new ArrayList<>();
                for (int j = 0; j < goalsJson.length(); j++) {
                    JSONObject goalJson = goalsJson.optJSONObject(j);
                    if (goalJson == null) throw new IllegalArgumentException("Invalid goal in " + key);
                    String id = requiredString(goalJson, "id");
                    String track = requiredString(goalJson, "track");
                    String title = requiredString(goalJson, "title");
                    String detail = requiredString(goalJson, "detail");
                    JSONArray sourceJson = goalJson.optJSONArray("sourceIds");
                    ArrayList<String> sourceIds = new ArrayList<>();
                    if (sourceJson != null) {
                        for (int k = 0; k < sourceJson.length(); k++) {
                            String sourceId = sourceJson.optString(k, "").trim();
                            if (!sourceId.isEmpty()) sourceIds.add(sourceId);
                        }
                    }
                    goals.add(new Goal(id, track, title, detail, sourceIds,
                            goalJson.optString("provenance", "curated"),
                            goalJson.optBoolean("editableByUser", true)));
                }
                parsedMonths.add(new Month(key, goals));
            }

            ArrayList<Document> parsedDocuments = new ArrayList<>();
            JSONArray docsJson = root.optJSONArray("documents");
            if (docsJson == null || docsJson.length() == 0) {
                throw new IllegalArgumentException("Plan catalog has no document summaries.");
            }
            for (int i = 0; i < docsJson.length(); i++) {
                JSONObject docJson = docsJson.optJSONObject(i);
                if (docJson == null) throw new IllegalArgumentException("Invalid document at index " + i);
                parsedDocuments.add(new Document(
                        requiredString(docJson, "id"),
                        requiredString(docJson, "title"),
                        requiredString(docJson, "subtitle"),
                        requiredString(docJson, "filename"),
                        requiredString(docJson, "content"),
                        docJson.optString("sourceKind", "condensed_reading_copy"),
                        docJson.optBoolean("originalPdfBundled", false)));
            }
            if (parsedMonths.size() != 16) {
                throw new IllegalArgumentException("Expected the October 2026–January 2028 roadmap (16 months); found "
                        + parsedMonths.size());
            }

            // Publish only after all records pass validation.
            schemaVersion = parsedSchemaVersion;
            sourcePlanVersion = parsedSourcePlanVersion;
            authorityNote = root.optString("authorityNote", "");
            months = Collections.unmodifiableList(parsedMonths);
            documents = Collections.unmodifiableList(parsedDocuments);
            initialized = true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid bundled plan catalog JSON.", e);
        }
    }

    private static String requiredString(JSONObject object, String key) {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing required field: " + key);
        return value;
    }

    static boolean isInitialized() { return initialized; }
    static int schemaVersion() { ensureInitialized(); return schemaVersion; }
    static int sourcePlanVersion() { ensureInitialized(); return sourcePlanVersion; }
    static String authorityNote() { ensureInitialized(); return authorityNote; }

    static int monthCount() {
        ensureInitialized();
        return months.size();
    }

    static String monthKeyAt(int index) {
        ensureInitialized();
        return index < 0 || index >= months.size() ? null : months.get(index).key;
    }

    static String[][] rowsForMonth(String monthKey) {
        ensureInitialized();
        Month month = findMonth(monthKey);
        if (month == null) return new String[0][];
        String[][] rows = new String[month.goals.size()][3];
        for (int i = 0; i < month.goals.size(); i++) {
            Goal g = month.goals.get(i);
            rows[i] = new String[]{g.track, g.title, g.detail};
        }
        return rows;
    }

    static String[] findGoal(String monthKey, String track) {
        for (String[] row : rowsForMonth(monthKey)) {
            if (row.length >= 3 && row[0].equals(track)) return row.clone();
        }
        return null;
    }

    static Goal findGoalById(String id) {
        ensureInitialized();
        if (id == null) return null;
        for (Month month : months) {
            for (Goal goal : month.goals) if (id.equals(goal.id)) return goal;
        }
        return null;
    }

    static List<Month> months() {
        ensureInitialized();
        return months;
    }

    static List<Document> documents() {
        ensureInitialized();
        return documents;
    }

    static String expectedSnapshot(Month month, Goal goal) {
        return snapshot(month.key, goal.track, goal.title, goal.detail);
    }

    static String snapshot(String month, String track, String title, String detail) {
        return safe(month) + "|#DAYFLOW#|" + safe(track) + "|#DAYFLOW#|" + safe(title) + "|#DAYFLOW#|" + safe(detail);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static Month findMonth(String monthKey) {
        if (monthKey == null) return null;
        for (Month month : months) if (monthKey.equals(month.key)) return month;
        return null;
    }

    private static void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("PlanCatalog.initialize(context) must be called before use.");
    }

    static final class Month {
        final String key;
        final List<Goal> goals;
        Month(String key, List<Goal> goals) {
            this.key = key;
            this.goals = Collections.unmodifiableList(new ArrayList<>(goals));
        }
    }

    static final class Goal {
        final String id, track, title, detail, provenance;
        final List<String> sourceIds;
        final boolean editableByUser;
        Goal(String id, String track, String title, String detail, List<String> sourceIds,
             String provenance, boolean editableByUser) {
            this.id = id; this.track = track; this.title = title; this.detail = detail;
            this.sourceIds = Collections.unmodifiableList(new ArrayList<>(sourceIds));
            this.provenance = provenance; this.editableByUser = editableByUser;
        }
    }

    static final class Document {
        final String id, title, subtitle, filename, content, sourceKind;
        final boolean originalPdfBundled;
        Document(String id, String title, String subtitle, String filename, String content,
                 String sourceKind, boolean originalPdfBundled) {
            this.id = id; this.title = title; this.subtitle = subtitle; this.filename = filename;
            this.content = content; this.sourceKind = sourceKind; this.originalPdfBundled = originalPdfBundled;
        }
    }
}
