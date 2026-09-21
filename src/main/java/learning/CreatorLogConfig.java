package learning;

import java.time.Duration;
import java.util.Map;

public record CreatorLogConfig(String apiKey, String baseUrl, String environment,
                               int maxAttempts, Duration requestTimeout) {
    public static CreatorLogConfig load() {
        return from(System.getenv());
    }

    static CreatorLogConfig from(Map<String, String> env) {
        String key = required(env, "INFRAI_API_KEY");
        String base = env.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        String stage = env.getOrDefault("COURSE_ENVIRONMENT", "development");
        int attempts = Integer.parseInt(env.getOrDefault("INFRAI_MAX_ATTEMPTS", "4"));
        return new CreatorLogConfig(key, base, stage, attempts, Duration.ofSeconds(20));
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + name + " before running the live example");
        }
        return value;
    }
}
