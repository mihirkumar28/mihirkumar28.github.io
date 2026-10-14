package com.mihirkumar.dayflow;

import org.json.JSONException;
import org.json.JSONObject;

final class DayFlowUpdateInfo {
    static final class ReleaseInfo {
        final long versionCode;
        final String versionName;
        final String releaseNotes;
        ReleaseInfo(long versionCode, String versionName, String releaseNotes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.releaseNotes = releaseNotes;
        }
    }

    private DayFlowUpdateInfo() {}

    static ReleaseInfo parse(String raw) throws JSONException {
        JSONObject json = new JSONObject(raw);
        long code = json.optLong("versionCode", 0L);
        String name = json.optString("versionName", "").trim();
        String notes = json.optString("releaseNotes", "").trim();
        if (code <= 0L || name.isEmpty()) {
            throw new JSONException("Update metadata is missing a valid versionCode or versionName.");
        }
        if (notes.length() > 1200) notes = notes.substring(0, 1200);
        return new ReleaseInfo(code, name, notes);
    }

    static boolean isNewerThan(long installedVersionCode, ReleaseInfo candidate) {
        return candidate != null && candidate.versionCode > installedVersionCode;
    }
}
