package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json;
import com.kiu.digitalq.json.Json.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin HTTP client for the KIU digitalQ server. All methods are blocking and
 * should be called through {@link Async} or {@link Poller}.
 */
public final class ApiClient {

    private final String baseUrl;
    private final HttpClient http;

    public ApiClient(String baseUrl) {
        this.baseUrl = normalise(baseUrl);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String host() {
        try {
            return URI.create(baseUrl).getHost();
        } catch (Exception ex) {
            return "localhost";
        }
    }

    public int port() {
        try {
            URI uri = URI.create(baseUrl);
            return uri.getPort() > 0 ? uri.getPort() : 80;
        } catch (Exception ex) {
            return 8765;
        }
    }

    public boolean isLocalHost() {
        String host = host();
        return host == null
                || host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1");
    }

    public JsonObject get(String path, String... parameters) throws ApiException {
        return request("GET", path, parameters);
    }

    /** Quick connectivity and status check used by the launcher. */
    public JsonObject ping() throws ApiException {
        return get("ping");
    }

    public JsonObject post(String path, String... parameters) throws ApiException {
        return request("POST", path, parameters);
    }

    private JsonObject request(String method, String path, String... parameters) throws ApiException {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < parameters.length; i += 2) {
            if (parameters[i + 1] != null) {
                values.put(parameters[i], parameters[i + 1]);
            }
        }
        String encoded = encode(values);
        String url = baseUrl + "/" + path + ("GET".equals(method) ? "?" + encoded : "");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json");
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(encoded, StandardCharsets.UTF_8));
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ApiException("Cannot reach the queue server at " + baseUrl
                    + ". Check that it is running and that this computer is on the same network.", ex, true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException("The request was interrupted.", ex, true);
        }

        if (response.statusCode() >= 500) {
            throw new ApiException("The queue server had a problem. Please try again.", null, true);
        }
        JsonObject json;
        try {
            json = Json.parseObject(response.body());
        } catch (RuntimeException ex) {
            throw new ApiException("The server returned an unexpected response.", ex, true);
        }
        if (!json.ok()) {
            String message = json.str("error", "The request could not be completed.");
            throw new ApiException(message, null, false);
        }
        return json;
    }

    private static String encode(Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (out.length() > 0) {
                out.append('&');
            }
            out.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    public static String normalise(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            value = "http://localhost:8765";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        int scheme = value.indexOf("://");
        int pathStart = value.indexOf('/', scheme < 0 ? 0 : scheme + 3);
        if (pathStart < 0) {
            value = value + "/api";
        }
        return value;
    }

    /** A user-presentable client error. */
    public static final class ApiException extends Exception {

        private final boolean connectionProblem;

        public ApiException(String message, Throwable cause, boolean connectionProblem) {
            super(message, cause);
            this.connectionProblem = connectionProblem;
        }

        public boolean isConnectionProblem() {
            return connectionProblem;
        }

        public boolean isSessionProblem() {
            String message = getMessage();
            return message != null && (message.contains("session") || message.contains("sign in"));
        }
    }
}
