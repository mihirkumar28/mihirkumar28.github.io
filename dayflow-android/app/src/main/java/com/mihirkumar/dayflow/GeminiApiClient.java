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
    private static final String MODEL = "gemini-3.8-flash";
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private GeminiApiClient() { }

    public static String generateContent(String apiKey, String prompt) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API key is missing.");
        }
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
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(45_000);
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
            String responseText = readBounded(stream);
            JSONObject response = new JSONObject(responseText);

            if (status < 200 || status >= 300) {
                JSONObject error = response.optJSONObject("error");
                String detail = error == null ? "" : error.optString("message", "");
                String message = "Gemini API returned HTTP " + status;
                if (!detail.trim().isEmpty()) message += ": " + detail.trim();
                throw new IOException(message);
            }

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
                            if (answer.length() > 0) answer.append("\n");
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
