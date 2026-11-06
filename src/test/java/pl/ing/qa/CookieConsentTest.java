package pl.ing.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CookieConsentTest {

    private static final String BASE_URL = System.getProperty("baseUrl", "https://www.ing.pl");
    private static final Duration TIMEOUT = Duration.ofMillis(Long.parseLong(System.getProperty("timeout", "15000")));
    private static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("headless", "true"));
    private static final boolean IGNORE_HTTPS_ERRORS = Boolean.parseBoolean(System.getProperty("ignoreHttpsErrors", "false"));
    private static final boolean LINUX = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");

    private static final Pattern ANALYTICAL = Pattern.compile("analityczn", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCEPT = Pattern.compile("^Zaakceptuj (zaznaczone|wybrane)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATED_AT = Pattern.compile("\"cookieCreateTimestamp\"\\s*:\\s*(\\d+)");
    private static final Pattern INCIDENT = Pattern.compile("incident_id=([\\w-]+)");
    private static final Set<String> MARKETING_COOKIES = Set.of("_fbp", "_gcl_au", "_uetsid", "_uetvid");

    static Stream<String> browsers() {
        String selected = System.getProperty("browser", "ALL");
        if (selected == null || selected.isBlank() || selected.equalsIgnoreCase("ALL")) {
            return Stream.of("chromium", "firefox", "webkit");
        }
        String browser = selected.strip().toLowerCase(Locale.ROOT);
        if (!List.of("chromium", "firefox", "webkit").contains(browser)) {
            throw new IllegalArgumentException("Unsupported browser '%s'".formatted(selected));
        }
        return Stream.of(browser);
    }

    @ParameterizedTest(name = "[{0}] analytical consent is stored")
    @MethodSource("browsers")
    void analyticalConsentIsStored(String browserName) {
        PlaywrightAssertions.setDefaultAssertionTimeout(TIMEOUT.toMillis());
        Instant started = Instant.now();

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions().setHeadless(HEADLESS);
            if ("chromium".equals(browserName)) {
                launch.setChromiumSandbox(!LINUX);
            }
            try (Browser browser = browserType(playwright, browserName).launch(launch);
                    BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                            .setLocale("pl-PL")
                            .setTimezoneId("Europe/Warsaw")
                            .setViewportSize(1920, 1080)
                            .setIgnoreHTTPSErrors(IGNORE_HTTPS_ERRORS))) {
                context.setDefaultTimeout(TIMEOUT.toMillis());
                Page page = context.newPage();

                open(page);
                assertThat(cookie(context, "cookiePolicyGDPR")).isNull();
                assertThat(cookie(context, "cookiePolicyGDPR__details")).isNull();

                chooseAnalyticalCookies(page);
                awaitConsent(context, started);

                reload(page);
                awaitVendorCookies(context);
            }
        }
    }

    private static void chooseAnalyticalCookies(Page page) {
        Locator dialogs = page.getByRole(AriaRole.DIALOG);
        Locator consent = dialogs.filter(new Locator.FilterOptions()
                .setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Dostosuj").setExact(true))));
        PlaywrightAssertions.assertThat(consent).isVisible();
        consent.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Dostosuj").setExact(true)).click();

        Locator settings = dialogs.filter(new Locator.FilterOptions().setHas(page.getByRole(AriaRole.SWITCH)));
        PlaywrightAssertions.assertThat(settings).isVisible();
        Locator analytical = settings.getByRole(AriaRole.SWITCH, new Locator.GetByRoleOptions().setName(ANALYTICAL));
        analytical.check();
        PlaywrightAssertions.assertThat(analytical).isChecked();
        settings.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ACCEPT)).click();
        PlaywrightAssertions.assertThat(settings).isHidden();
    }

    private static void awaitConsent(BrowserContext context, Instant started) {
        until("cookiePolicyGDPR=3", () -> {
            Cookie gdpr = cookie(context, "cookiePolicyGDPR");
            Cookie details = cookie(context, "cookiePolicyGDPR__details");
            assertThat(gdpr).isNotNull();
            assertThat(details).isNotNull();
            assertThat(gdpr.value).isEqualTo("3");
            assertThat(gdpr.expires).isGreaterThan((double) Instant.now().getEpochSecond());
            Matcher createdAt = CREATED_AT.matcher(details.value);
            assertThat(createdAt.find()).isTrue();
            assertThat(Instant.ofEpochMilli(Long.parseLong(createdAt.group(1))))
                    .isBetween(started.minusSeconds(5), Instant.now().plusSeconds(5));
        });
    }

    private static void awaitVendorCookies(BrowserContext context) {
        until("analytical cookies", () -> {
            List<String> names = cookieNames(context);
            assertThat(names).anyMatch(name -> name.equals("_ga") || name.startsWith("_ga_"));
            assertThat(names).anyMatch(name -> name.startsWith("AMCV_"));
        });
        try {
            await("no marketing cookies")
                    .atMost(TIMEOUT)
                    .pollDelay(Duration.ZERO)
                    .pollInterval(Duration.ofMillis(200))
                    .pollInSameThread()
                    .during(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(cookieNames(context)).noneMatch(MARKETING_COOKIES::contains));
        } catch (ConditionTimeoutException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    private static void open(Page page) {
        Response response;
        try {
            response = page.navigate(BASE_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        } catch (PlaywrightException e) {
            if (String.valueOf(e.getMessage()).contains("SSL peer certificate or SSH remote key was not OK")) {
                throw new AssertionError("WebKit rejected the TLS certificate of " + BASE_URL
                        + ". On Windows, open the site once in Edge so the missing root is downloaded, then rerun.", e);
            }
            throw e;
        }
        assertLoaded(page, response);
    }

    private static void reload(Page page) {
        assertLoaded(page, page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)));
    }

    private static void assertLoaded(Page page, Response response) {
        assertThat(response).isNotNull();
        assertThat(response.ok()).isTrue();
        Locator challenge = page.locator("iframe[src*='_Incapsula_Resource']");
        if (challenge.count() == 0) {
            return;
        }
        Matcher incident = INCIDENT.matcher(String.valueOf(challenge.first().getAttribute("src")));
        throw new AssertionError("ing.pl showed an Imperva challenge instead of the home page, incident "
                + (incident.find() ? incident.group(1) : "unknown")
                + ". The bank blocks traffic from outside Poland.");
    }

    private static void until(String what, Runnable check) {
        try {
            await(what)
                    .atMost(TIMEOUT)
                    .pollDelay(Duration.ZERO)
                    .pollInterval(Duration.ofMillis(200))
                    .pollInSameThread()
                    .untilAsserted(check::run);
        } catch (ConditionTimeoutException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    private static List<String> cookieNames(BrowserContext context) {
        return context.cookies(BASE_URL).stream().map(cookie -> cookie.name).toList();
    }

    private static Cookie cookie(BrowserContext context, String name) {
        return context.cookies(BASE_URL).stream()
                .collect(java.util.stream.Collectors.toMap(cookie -> cookie.name, Function.identity(), (first, duplicate) -> first))
                .get(name);
    }

    private static BrowserType browserType(Playwright playwright, String browserName) {
        return switch (browserName) {
            case "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException(browserName);
        };
    }
}
