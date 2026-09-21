package learning;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class InfraiLogs {
    private final CreatorLogConfig config;
    private final HttpClient http;

    public InfraiLogs(CreatorLogConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiLogs(CreatorLogConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public String ingest(String jobId, List<CourseRelease.LogEvent> events)
            throws IOException, InterruptedException {
        String entries = events.stream().map(InfraiLogs::entryJson).reduce((a, b) -> a + "," + b).orElse("");
        String body = "{\"entries\":[" + entries + "],\"idempotency_key\":\"course-release-"
                + json(jobId) + "\"}";
        return call("POST", "/v1/logs/ingest", body);
    }

    public String search(String phrase, String jobId) throws IOException, InterruptedException {
        String query = "?q=" + url(phrase) + "&service=" + url("creator-course-release")
                + "&trace_id=" + url(jobId) + "&limit=20";
        return call("GET", "/v1/logs/search", null, query);
    }

    private String call(String method, String path, String body) throws IOException, InterruptedException {
        return call(method, path, body, "");
    }

    private String call(String method, String path, String body, String query)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path + query))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            HttpRequest request = body == null
                    ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
                    : builder.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            Envelope envelope = Envelope.parse(response.body());
            if (response.statusCode() == 429) {
                Thread.sleep(retryDelay(response, attempt).toMillis());
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(response.statusCode(), envelope.error());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport status " + response.statusCode());
            }
            return envelope.data();
        }
        throw new IOException("Infrai retry budget exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        Optional<String> value = response.headers().firstValue("Retry-After");
        if (value.isPresent()) {
            try {
                return Duration.ofSeconds(Math.max(1, Long.parseLong(value.get())));
            } catch (NumberFormatException ignored) {
                // Fall through to bounded exponential delay.
            }
        }
        return Duration.ofSeconds(1L << attempt);
    }

    private static String entryJson(CourseRelease.LogEvent event) {
        return "{\"message\":\"" + json(event.message()) + "\",\"level\":\"" + json(event.level())
                + "\",\"service\":\"" + json(event.service()) + "\",\"environment\":\""
                + json(event.environment()) + "\",\"trace_id\":\"" + json(event.traceId()) + "\"}";
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record Envelope(boolean ok, String data, String error) {
        static Envelope parse(String json) throws IOException {
            Object root = new JsonReader(json).read();
            if (!(root instanceof java.util.Map<?, ?> map) || !(map.get("ok") instanceof Boolean ok)) {
                throw new IOException("Response is not an Infrai envelope");
            }
            return new Envelope(ok, String.valueOf(map.get("data")), String.valueOf(map.get("error")));
        }
    }

    public static final class InfraiException extends IOException {
        private final int statusCode;
        InfraiException(int statusCode, String error) {
            super("Infrai request rejected: " + error);
            this.statusCode = statusCode;
        }
        public int statusCode() { return statusCode; }
    }

    private static final class JsonReader {
        private final String text;
        private int pos;
        JsonReader(String text) { this.text = text; }

        Object read() throws IOException {
            Object value = value();
            whitespace();
            if (pos != text.length()) fail();
            return value;
        }

        private Object value() throws IOException {
            whitespace();
            if (pos >= text.length()) return fail();
            return switch (text.charAt(pos)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private java.util.Map<String, Object> object() throws IOException {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            pos++;
            whitespace();
            if (take('}')) return map;
            do {
                whitespace();
                String key = string();
                whitespace();
                if (!take(':')) fail();
                map.put(key, value());
                whitespace();
            } while (take(','));
            if (!take('}')) fail();
            return map;
        }

        private java.util.List<Object> array() throws IOException {
            java.util.List<Object> list = new java.util.ArrayList<>();
            pos++;
            whitespace();
            if (take(']')) return list;
            do {
                list.add(value());
                whitespace();
            } while (take(','));
            if (!take(']')) fail();
            return list;
        }

        private String string() throws IOException {
            if (!take('"')) return fail();
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (pos >= text.length()) return fail();
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) return fail();
                        try { out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); }
                        catch (NumberFormatException e) { return fail(); }
                        pos += 4;
                    }
                    default -> { return fail(); }
                }
            }
            return fail();
        }

        private Object number() throws IOException {
            int start = pos;
            while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
            if (start == pos) return fail();
            try { return Double.valueOf(text.substring(start, pos)); }
            catch (NumberFormatException e) { return fail(); }
        }

        private Object literal(String token, Object value) throws IOException {
            if (!text.startsWith(token, pos)) return fail();
            pos += token.length();
            return value;
        }

        private void whitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }
        private boolean take(char expected) {
            if (pos < text.length() && text.charAt(pos) == expected) { pos++; return true; }
            return false;
        }
        private <T> T fail() throws IOException { throw new IOException("Invalid JSON response at offset " + pos); }
    }
}
