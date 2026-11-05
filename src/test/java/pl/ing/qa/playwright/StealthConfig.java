package pl.ing.qa.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.options.Geolocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.ing.qa.config.BrowserName;
import pl.ing.qa.config.ConfigReader;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StealthConfig {

    private static final List<String> CHROMIUM_ARGS = List.of(
            "--disable-blink-features=AutomationControlled",
            "--no-sandbox",
            "--disable-infobars",
            "--disable-dev-shm-usage");

    private static final Map<String, Object> FIREFOX_PREFS = Map.of(
            "dom.webdriver.enabled", false,
            "useAutomationExtension", false);

    private static final Map<String, String> HTTP_HEADERS = Map.of(
            "Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7",
            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

    private static final String INIT_SCRIPT = readResource("/stealth/stealth.js");
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    public static BrowserType.LaunchOptions launchOptions(BrowserName browser) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(ConfigReader.headless());
        return switch (browser) {
            case CHROMIUM -> options.setChannel("chromium").setArgs(CHROMIUM_ARGS);
            case FIREFOX -> options.setFirefoxUserPrefs(FIREFOX_PREFS);
            case WEBKIT -> options;
        };
    }

    public static BrowserContext newContext(Browser browser, BrowserName browserName) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(userAgent(browserName, browser.version()))
                .setLocale("pl-PL")
                .setTimezoneId("Europe/Warsaw")
                .setGeolocation(new Geolocation(52.2297, 21.0122))
                .setPermissions(List.of("geolocation"))
                .setViewportSize(1920, 1080)
                .setExtraHTTPHeaders(HTTP_HEADERS)
                .setIgnoreHTTPSErrors(ignoreHttpsErrors(browserName)));
        context.addInitScript(INIT_SCRIPT);
        return context;
    }

    private static String userAgent(BrowserName browser, String version) {
        String major = version.split("\\.")[0];
        return switch (browser) {
            case CHROMIUM -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/%s.0.0.0 Safari/537.36".formatted(major);
            case FIREFOX -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:%1$s.0) Gecko/20100101 Firefox/%1$s.0"
                    .formatted(major);
            case WEBKIT -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/%s Safari/605.1.15".formatted(version);
        };
    }

    private static boolean ignoreHttpsErrors(BrowserName browser) {
        boolean ignore = ConfigReader.ignoreHttpsErrors().orElse(browser == BrowserName.WEBKIT && IS_WINDOWS);
        if (ignore) {
            log.warn("TLS certificate validation is disabled for {}", browser);
        }
        return ignore;
    }

    private static String readResource(String name) {
        try (InputStream stream = StealthConfig.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing classpath resource " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read classpath resource " + name, e);
        }
    }
}
