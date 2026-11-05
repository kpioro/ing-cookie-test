package pl.ing.qa.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.Geolocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
            "--disable-infobars",
            "--disable-dev-shm-usage");
    private static final String NO_SANDBOX = "--no-sandbox";

    private static final String ACCEPT_LANGUAGE = "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7";
    private static final Map<String, String> NAVIGATION_HEADERS = Map.of(
            "accept-language", ACCEPT_LANGUAGE,
            "accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

    private static final String WEBKIT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/%s Safari/605.1.15";
    private static final String WEBKIT_PLATFORM_SCRIPT =
            "Object.defineProperty(Navigator.prototype, 'platform', { get: () => 'MacIntel', configurable: true });";

    private static final String INIT_SCRIPT = readResource("/stealth/stealth.js");
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean IS_WINDOWS = OS_NAME.startsWith("windows");
    private static final boolean IS_LINUX = OS_NAME.startsWith("linux");

    public static BrowserType.LaunchOptions launchOptions(BrowserName browser) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(ConfigReader.headless());
        if (browser != BrowserName.CHROMIUM) {
            return options;
        }
        List<String> args = new ArrayList<>(CHROMIUM_ARGS);
        if (IS_LINUX) {
            args.add(NO_SANDBOX);
        }
        return options.setChannel("chromium").setChromiumSandbox(!IS_LINUX).setArgs(args);
    }

    public static BrowserContext newContext(Browser browser, BrowserName browserName) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(userAgent(browser, browserName))
                .setLocale("pl-PL")
                .setTimezoneId("Europe/Warsaw")
                .setGeolocation(new Geolocation(52.2297, 21.0122))
                .setPermissions(List.of("geolocation"))
                .setViewportSize(1920, 1080)
                .setExtraHTTPHeaders(Map.of("Accept-Language", ACCEPT_LANGUAGE))
                .setIgnoreHTTPSErrors(ignoreHttpsErrors(browserName)));
        context.addInitScript(INIT_SCRIPT);
        if (browserName == BrowserName.WEBKIT) {
            context.addInitScript(WEBKIT_PLATFORM_SCRIPT);
        }
        context.route("**/*", StealthConfig::withNavigationHeaders);
        return context;
    }

    private static void withNavigationHeaders(Route route) {
        Request request = route.request();
        if (!request.isNavigationRequest()) {
            route.fallback();
            return;
        }
        Map<String, String> headers = new HashMap<>(request.headers());
        headers.putAll(NAVIGATION_HEADERS);
        route.fallback(new Route.FallbackOptions().setHeaders(headers));
    }

    private static String userAgent(Browser browser, BrowserName browserName) {
        if (browserName == BrowserName.WEBKIT) {
            return WEBKIT_USER_AGENT.formatted(browser.version());
        }
        try (BrowserContext probe = browser.newContext()) {
            String engineUserAgent = (String) probe.newPage().evaluate("() => navigator.userAgent");
            return engineUserAgent.replace("HeadlessChrome/", "Chrome/");
        }
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
