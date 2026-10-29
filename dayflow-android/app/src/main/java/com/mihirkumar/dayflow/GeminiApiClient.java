package com.mihirkumar.dayflow;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal REST client for Gemini. Call from a worker thread; never logs prompts or API keys. */
public final class GeminiApiClient {
    private static final String PRIMARY_MODEL = "gemini-3.8-flash";
    private static final String FALLBACK_MODEL = "gemini-3.5-flash-lite";
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private GeminiApiClient() { }

    /**
     * Tries the primary model, retries a temporary 503 once with backoff, then
     * switches to a documented lower-cost model if the primary remains overloaded.
     */
    public static String generateContent(String apiKey, String prompt) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API key is missing.");
        }

        try {
            return generateForModel(apiKey, prompt, PRIMARY_MODEL);
        } catch (GeminiApiException first) {
            if (first.getStatusCode() != 503) throw first;
        }

        // Google's REST troubleshooting guide recommends exponential backoff for 503.
        long backoffMs = 800L + java.util.concurrent.ThreadLocalRandom.current().nextLong(401L);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini retry was interrupted.", interrupted);
        }

        try {
            return generateForModel(apiKey, prompt, PRIMARY_MODEL);
        } catch (GeminiApiException second) {
            if (second.getStatusCode() != 503) throw second;
        }

        try {
            String fallbackAnswer = generateForModel(apiKey, prompt, FALLBACK_MODEL);
            return "[Gemini 3.5 Flash-Lite fallback used after high demand on Gemini 3.8 Flash.]\\n\\n"
                    + fallbackAnswer;
        } catch (GeminiApiException fallback) {
            if (fallback.getStatusCode() == 503) {
                throw new IOException("Both Gemini 3.8 Flash and the fallback Gemini 3.5 Flash-Lite are temporarily overloaded (HTTP 503). This is a Gemini service-capacity issue, not a quota error. Wait a little and try again.");
            }
            throw fallback;
        }
    }

    private static String generateForModel(String apiKey, String prompt, String model) throws Exception {
        JSONObject part = new JSONObject().put("text", prompt);
        JSONObject content = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(part));
        JSONObject generationConfig = new JSONObject()
                .put("temperature", 0.2)
                .put("maxOutputTokens", 1200);
        JSONObject request = new JSONObject()
                .put("contents", new JSONArray().put(content))
                .put("generationConfig", generationConfig);

        HttpURLConnection connection = null;
        try {
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent";
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(8_000);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-goog-api-key", apiKey.trim());
            connection.setRequestProperty("User-Agent", "DayFlow-Android");

            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = stream == null ? "" : readBounded(stream);
            JSONObject response = null;
            if (!responseText.trim().isEmpty()) {
                try {
                    response = new JSONObject(responseText);
                } catch (Exception ignored) {
                    // Preserve HTTP status even if an intermediary returns a non-JSON error body.
                }
            }

            if (status < 200 || status >= 300) {
                JSONObject error = response == null ? null : response.optJSONObject("error");
                String detail = error == null ? "" : error.optString("message", "");
                String message = "Gemini API returned HTTP " + status;
                if (!detail.trim().isEmpty()) message += ": " + detail.trim();
                throw new GeminiApiException(status, message);
            }
            if (response == null) throw new IOException("Gemini returned an empty or invalid response.");

            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                JSONObject feedback = response.optJSONObject("promptFeedback");
                String reason = feedback == null ? "" : feedback.optString("blockReason", "");
                throw new IOException(reason.isEmpty()
                        ? "Gemini returned no answer. Try a shorter prompt."
                        : "Gemini could not answer this prompt (" + reason + ").");
            }
            JSONObject candidate = candidates.optJSONObject(0);
            JSONObject answerContent = candidate == null ? null : candidate.optJSONObject("content");
            JSONArray parts = answerContent == null ? null : answerContent.optJSONArray("parts");
            StringBuilder answer = new StringBuilder();
            if (parts != null) {
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject answerPart = parts.optJSONObject(i);
                    if (answerPart != null) {
                        String text = answerPart.optString("text", "");
                        if (!text.isEmpty()) {
                            if (answer.length() > 0) answer.append("\\n");
                            answer.append(text);
                        }
                    }
                }
            }
            if (answer.length() == 0) throw new IOException("Gemini returned an empty answer.");
            return answer.toString().trim();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static final class GeminiApiException extends IOException {
        private final int statusCode;

        GeminiApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        int getStatusCode() {
            return statusCode;
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        if (input == null) throw new IOException("Gemini returned an empty response.");
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            int total = 0;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Gemini response was unexpectedly large.");
                }
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }
}
