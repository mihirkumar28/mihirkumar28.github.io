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
        } catch (Exception first) {
            if (!isRetryable(first)) {
                logRequestFailure(context, PRIMARY_MODEL, 1, first, "Primary request failed");
                throw first;
            }
            int status = httpStatus(first);
            String event = status == 503 ? "overload_retry_scheduled" : "transport_timeout_retry_scheduled";
            String detail = status == 503 ? "HTTP 503; one retry scheduled"
                    : "Transient network timeout; one retry scheduled";
            GeminiDiagnostics.record(context, event, PRIMARY_MODEL, 1, status, 0,
                    requestId(first), detail);
        }

        long backoffMs = 900L + java.util.concurrent.ThreadLocalRandom.current().nextLong(501L);
        GeminiDiagnostics.record(context, "retry_backoff", PRIMARY_MODEL, 2, 0,
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
                    "Retry after transient error; payload omitted from diagnostics.");
            return generateForModel(context, apiKey, prompt, PRIMARY_MODEL, 2);
        } catch (Exception second) {
            if (!isRetryable(second)) {
                logRequestFailure(context, PRIMARY_MODEL, 2, second, "Primary retry failed");
                throw second;
            }
            int status = httpStatus(second);
            String detail = status == 503 ? "Primary model still overloaded; fallback scheduled"
                    : "Primary request timed out again; fallback scheduled";
            GeminiDiagnostics.record(context, "fallback_scheduled", FALLBACK_MODEL, 3,
                    status, 0, requestId(second), detail);
        }

        try {
            GeminiDiagnostics.record(context, "request_begin", FALLBACK_MODEL, 3, 0, 0, "",
                    "Fallback after repeated transient error; payload omitted from diagnostics.");
            String fallbackAnswer = generateForModel(context, apiKey, prompt, FALLBACK_MODEL, 3);
            GeminiDiagnostics.record(context, "request_complete", FALLBACK_MODEL, 3,
                    200, 0, "", "Fallback model returned an answer");
            return "[Gemini 3.5 Flash-Lite fallback used after high latency or overload on Gemini 3.8 Flash.]\n\n"
                    + fallbackAnswer;
        } catch (Exception fallback) {
            logRequestFailure(context, FALLBACK_MODEL, 3, fallback, "Fallback request failed");
            if (isRetryable(fallback)) {
                if (httpStatus(fallback) == 503) {
                    throw new IOException("Both Gemini models are temporarily overloaded (HTTP 503). Wait a little and try again.");
                }
                throw new IOException("Gemini did not respond before the network timeout on the primary model and the fallback. Check your connection and retry; the log includes the elapsed time and Google request ID if one was returned.");
            }
            throw fallback;
        }
    }

    private static boolean isRetryable(Exception error) {
        if (error instanceof GeminiApiException) {
            int status = ((GeminiApiException) error).getStatusCode();
            return status == 408 || status == 503 || status == 504;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.UnknownHostException
                    || current instanceof java.net.NoRouteToHostException
                    || current instanceof java.net.SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static int httpStatus(Exception error) {
        return error instanceof GeminiApiException ? ((GeminiApiException) error).getStatusCode() : 0;
    }

    private static String requestId(Exception error) {
        return error instanceof GeminiApiException ? ((GeminiApiException) error).getRequestId() : "";
    }

    private static void logRequestFailure(Context context, String model, int attempt,
            Exception error, String label) {
        GeminiDiagnostics.record(context, "request_failed", model, attempt, httpStatus(error),
                0, requestId(error), label + ": " + error.getClass().getSimpleName()
                        + ": " + safeExceptionDetail(error));
    }

    private static String generateForModel(Context context, String apiKey, String prompt,
            String model, int attempt) throws Exception {
        JSONObject part = new JSONObject().put("text", prompt);
        JSONObject content = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(part));
        // Gemini 3.8 Flash defaults to medium thinking; low reduces latency for
        // this bounded schedule review without needing deep multi-step reasoning.
        JSONObject thinkingConfig = new JSONObject().put("thinkingLevel", "LOW");
        JSONObject generationConfig = new JSONObject()
                .put("thinkingConfig", thinkingConfig)
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
            connection.setReadTimeout(12_000);
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
                String apiStatus = error == null ? "" : error.optString("status", "");
                if (!apiStatus.isEmpty()) statusDetail += "; apiStatus=" + apiStatus;
                if (!detail.trim().isEmpty()) statusDetail += "; message=" + detail.trim();
                GeminiDiagnostics.record(context, "http_response", model, attempt, status,
                        System.currentTimeMillis() - startedAt, requestId, statusDetail);
                String message = "Gemini API returned HTTP " + status;
                if (!detail.trim().isEmpty()) message += ": " + detail.trim();
                throw new GeminiApiException(status, message, requestId);
            }
            GeminiDiagnostics.record(context, "http_response", model, attempt, status,
                    System.currentTimeMillis() - startedAt, requestId, statusDetail);
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
