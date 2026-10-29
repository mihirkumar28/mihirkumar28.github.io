package com.mihirkumar.dayflow;

import android.content.Context;

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
    public static String generateContent(Context context, String apiKey, String prompt) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            GeminiDiagnostics.record(context, "client_rejected", PRIMARY_MODEL, 0, 0, 0, "",
                    "API key missing");
            throw new IllegalArgumentException("Gemini API key is missing.");
        }

        GeminiDiagnostics.record(context, "request_begin", PRIMARY_MODEL, 1, 0, 0, "",
                "Routine analysis request started; payload omitted from diagnostics.");
        try {
            return generateForModel(context, apiKey, prompt, PRIMARY_MODEL, 1);
        } catch (GeminiApiException first) {
            if (first.getStatusCode() != 503) {
                GeminiDiagnostics.record(context, "request_failed", PRIMARY_MODEL, 1,
                        first.getStatusCode(), 0, first.getRequestId(), "API request failed");
                throw first;
            }
            GeminiDiagnostics.record(context, "overload_retry_scheduled", PRIMARY_MODEL, 1,
                    503, 0, first.getRequestId(), "HTTP 503; one retry scheduled");
        }

        long backoffMs = 800L + java.util.concurrent.ThreadLocalRandom.current().nextLong(401L);
        GeminiDiagnostics.record(context, "retry_backoff", PRIMARY_MODEL, 2, 503,
                backoffMs, "", "Waiting briefly before retry");
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            GeminiDiagnostics.record(context, "request_interrupted", PRIMARY_MODEL, 2,
                    0, backoffMs, "", "Retry interrupted");
            throw new IOException("Gemini retry was interrupted.", interrupted);
        }

        try {
            GeminiDiagnostics.record(context, "request_begin", PRIMARY_MODEL, 2, 0, 0, "",
                    "Retry after HTTP 503; payload omitted from diagnostics.");
            return generateForModel(context, apiKey, prompt, PRIMARY_MODEL, 2);
        } catch (GeminiApiException second) {
            if (second.getStatusCode() != 503) {
                GeminiDiagnostics.record(context, "request_failed", PRIMARY_MODEL, 2,
                        second.getStatusCode(), 0, second.getRequestId(), "API retry failed");
                throw second;
            }
            GeminiDiagnostics.record(context, "fallback_scheduled", FALLBACK_MODEL, 3,
                    503, 0, second.getRequestId(), "Primary model still overloaded; fallback scheduled");
        }

        try {
            GeminiDiagnostics.record(context, "request_begin", FALLBACK_MODEL, 3, 0, 0, "",
                    "Fallback after repeated HTTP 503; payload omitted from diagnostics.");
            String fallbackAnswer = generateForModel(context, apiKey, prompt, FALLBACK_MODEL, 3);
            GeminiDiagnostics.record(context, "request_complete", FALLBACK_MODEL, 3,
                    200, 0, "", "Fallback model returned an answer");
            return "[Gemini 3.5 Flash-Lite fallback used after high demand on Gemini 3.8 Flash.]\n\n"
                    + fallbackAnswer;
        } catch (GeminiApiException fallback) {
            GeminiDiagnostics.record(context, "fallback_failed", FALLBACK_MODEL, 3,
                    fallback.getStatusCode(), 0, fallback.getRequestId(), "Fallback API request failed");
            if (fallback.getStatusCode() == 503) {
                throw new IOException("Both Gemini 3.8 Flash and the fallback Gemini 3.5 Flash-Lite are temporarily overloaded (HTTP 503). This is a Gemini service-capacity issue, not a quota error. Wait a little and try again.");
            }
            throw fallback;
        } catch (Exception error) {
            GeminiDiagnostics.record(context, "fallback_client_error", FALLBACK_MODEL, 3,
                    0, 0, "", error.getClass().getSimpleName() + ": " + safeExceptionDetail(error));
            throw error;
        }
    }

    private static String generateForModel(Context context, String apiKey, String prompt,
            String model, int attempt) throws Exception {
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
        long startedAt = System.currentTimeMillis();
        String requestId = "";
        int status = 0;
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

            status = connection.getResponseCode();
            requestId = firstNonEmpty(
                    connection.getHeaderField("x-goog-request-id"),
                    connection.getHeaderField("x-request-id"),
                    connection.getHeaderField("x-guploader-uploadid"));
            String retryAfter = connection.getHeaderField("Retry-After");
            String statusDetail = status >= 200 && status < 300
                    ? "HTTP success response" : "HTTP error response";
            if (retryAfter != null && !retryAfter.trim().isEmpty()) {
                statusDetail += "; Retry-After=" + retryAfter.trim();
            }
            GeminiDiagnostics.record(context, "http_response", model, attempt, status,
                    System.currentTimeMillis() - startedAt, requestId, statusDetail);

            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = stream == null ? "" : readBounded(stream);
            JSONObject response = null;
            if (!responseText.trim().isEmpty()) {
                try {
                    response = new JSONObject(responseText);
                } catch (Exception ignored) {
                    // Preserve HTTP status if an intermediary returns a non-JSON error body.
                }
            }

            if (status < 200 || status >= 300) {
                JSONObject error = response == null ? null : response.optJSONObject("error");
                String detail = error == null ? "" : error.optString("message", "");
                String message = "Gemini API returned HTTP " + status;
                if (!detail.trim().isEmpty()) message += ": " + detail.trim();
                throw new GeminiApiException(status, message, requestId);
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
                        String answerText = answerPart.optString("text", "");
                        if (!answerText.isEmpty()) {
                            if (answer.length() > 0) answer.append("\\n");
                            answer.append(answerText);
                        }
                    }
                }
            }
            if (answer.length() == 0) throw new IOException("Gemini returned an empty answer.");
            GeminiDiagnostics.record(context, "response_parsed", model, attempt, status,
                    System.currentTimeMillis() - startedAt, requestId, "Text response parsed successfully");
            return answer.toString().trim();
        } catch (GeminiApiException e) {
            throw e;
        } catch (Exception e) {
            GeminiDiagnostics.record(context, "transport_or_parse_error", model, attempt,
                    status, System.currentTimeMillis() - startedAt, requestId,
                    e.getClass().getSimpleName() + ": " + safeExceptionDetail(e));
            throw e;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String safeExceptionDetail(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "";
        return message;
    }

    private static final class GeminiApiException extends IOException {
        private final int statusCode;
        private final String requestId;

        GeminiApiException(int statusCode, String message, String requestId) {
            super(message);
            this.statusCode = statusCode;
            this.requestId = requestId == null ? "" : requestId;
        }

        int getStatusCode() {
            return statusCode;
        }

        String getRequestId() {
            return requestId;
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
