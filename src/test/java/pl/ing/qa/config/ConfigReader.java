package pl.ing.qa.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigReader {

    public static String baseUrl() {
        return property("baseUrl").orElse("https://www.ing.pl");
    }

    public static List<BrowserName> browsers() {
        return BrowserName.parseSelection(property("browser").orElse(BrowserName.ALL));
    }

    public static boolean headless() {
        return property("headless").map(Boolean::parseBoolean).orElse(true);
    }

    public static Duration timeout() {
        return Duration.ofMillis(property("timeout").map(Long::parseLong).orElse(15_000L));
    }

    public static boolean ignoreHttpsErrors() {
        return property("ignoreHttpsErrors").map(Boolean::parseBoolean).orElse(false);
    }

    private static Optional<String> property(String key) {
        return Optional.ofNullable(System.getProperty(key))
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }
}
