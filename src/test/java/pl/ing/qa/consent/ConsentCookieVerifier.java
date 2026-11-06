package pl.ing.qa.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.ThrowingRunnable;
import pl.ing.qa.config.ConfigReader;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public final class ConsentCookieVerifier {

    public static final String GDPR_COOKIE = "cookiePolicyGDPR";
    public static final String GDPR_DETAILS_COOKIE = GDPR_COOKIE + "__details";

    private static final List<Pattern> ANALYTICAL_COOKIES = List.of(
            Pattern.compile("_ga(_\\w+)?"),
            Pattern.compile("AMCV_.+"));
    private static final Set<String> MARKETING_COOKIES = Set.of("_fbp", "_gcl_au", "_uetsid", "_uetvid");

    private static final String COOKIE_POLICY_MANAGER = "window.CookiePolicyManager";
    private static final String READ_BANK_CONSENT = """
            () => {
              const manager = window.CookiePolicyManager;
              if (typeof manager?.isCookiePolicyAnalyticalAccepted !== 'function'
                  || typeof manager?.isCookiePolicyMarketingAccepted !== 'function') {
                return null;
              }
              return {
                analytical: manager.isCookiePolicyAnalyticalAccepted(),
                marketing: manager.isCookiePolicyMarketingAccepted()
              };
            }""";
    private static final Map<String, Boolean> ANALYTICAL_ONLY = Map.of("analytical", true, "marketing", false);

    private static final Pattern CREATE_TIMESTAMP = Pattern.compile("\"cookieCreateTimestamp\"\\s*:\\s*(\\d+)");
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(5);
    private static final Duration MARKETING_QUIET_PERIOD = Duration.ofSeconds(3);

    private final Page page;

    public void assertNoConsentStored() {
        assertThat(cookiesByName())
                .as("consent cookies before any choice in the banner")
                .doesNotContainKeys(GDPR_COOKIE, GDPR_DETAILS_COOKIE);
    }

    public void assertAnalyticalConsentPersisted(Instant givenNotBefore) {
        untilAsserted(
                polling("%s and %s persisted with analytical consent".formatted(GDPR_COOKIE, GDPR_DETAILS_COOKIE),
                        ConfigReader.timeout()),
                () -> verifyAnalyticalConsent(givenNotBefore));
    }

    public void assertBankReadsAnalyticalOnlyConsent() {
        untilAsserted(
                polling("%s reads the stored consent as analytical only".formatted(COOKIE_POLICY_MANAGER),
                        ConfigReader.timeout()),
                this::verifyBankReadsAnalyticalOnlyConsent);
    }

    public void assertOnlyAnalyticalCookiesStored() {
        try {
            untilAsserted(
                    polling("analytical cookies %s stored".formatted(ANALYTICAL_COOKIES), ConfigReader.timeout()),
                    this::verifyAnalyticalCookiesStored);
            untilAsserted(
                    polling("no marketing cookies %s for %s".formatted(MARKETING_COOKIES, MARKETING_QUIET_PERIOD),
                            MARKETING_QUIET_PERIOD.plusSeconds(1))
                            .during(MARKETING_QUIET_PERIOD),
                    this::verifyNoMarketingCookies);
        } catch (AssertionError e) {
            throw new AssertionError(("Consent is stored (%s, %s) and %s reads it as analytical-only, "
                    + "but the expected analytics-vendor cookies did not appear, or a marketing cookie did. %s")
                    .formatted(GDPR_COOKIE, GDPR_DETAILS_COOKIE, COOKIE_POLICY_MANAGER, e.getMessage()), e);
        }
    }

    private void verifyBankReadsAnalyticalOnlyConsent() {
        Object bankConsent = page.evaluate(READ_BANK_CONSENT);
        Cookie gdpr = cookiesByName().get(GDPR_COOKIE);
        assertThat(bankConsent)
                .as("consent reported by %s on %s for %s=%s", COOKIE_POLICY_MANAGER, page.url(), GDPR_COOKIE,
                        gdpr == null ? null : gdpr.value)
                .isEqualTo(ANALYTICAL_ONLY);
        log.info("Bank reads stored consent as {}", bankConsent);
    }

    private void verifyAnalyticalCookiesStored() {
        List<String> names = cookieNames();
        SoftAssertions.assertSoftly(softly -> ANALYTICAL_COOKIES.forEach(pattern -> softly.assertThat(names)
                .as("analytical cookie %s in %s", pattern, names)
                .anyMatch(name -> pattern.matcher(name).matches())));
        log.info("Analytical cookies stored: {}", names.stream().filter(ConsentCookieVerifier::isAnalytical).toList());
    }

    private void verifyNoMarketingCookies() {
        List<String> names = cookieNames();
        assertThat(names).as("marketing cookies %s in %s", MARKETING_COOKIES, names).noneMatch(MARKETING_COOKIES::contains);
    }

    private static boolean isAnalytical(String name) {
        return ANALYTICAL_COOKIES.stream().anyMatch(pattern -> pattern.matcher(name).matches());
    }

    private void verifyAnalyticalConsent(Instant givenNotBefore) {
        Map<String, Cookie> cookies = cookiesByName();
        assertThat(cookies).as("cookies of %s", ConfigReader.baseUrl()).containsKeys(GDPR_COOKIE, GDPR_DETAILS_COOKIE);

        Cookie gdpr = cookies.get(GDPR_COOKIE);
        Cookie details = cookies.get(GDPR_DETAILS_COOKIE);
        ConsentLevel level = ConsentLevel.fromCookieValue(gdpr.value);
        Instant recordedAt = consentRecordedAt(details.value);
        Instant now = Instant.now();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(level.isAnalyticsGranted())
                    .as("%s=%s (%s) grants analytical cookies", GDPR_COOKIE, gdpr.value, level)
                    .isTrue();
            softly.assertThat(level.isMarketingGranted())
                    .as("%s=%s (%s) grants marketing cookies", GDPR_COOKIE, gdpr.value, level)
                    .isFalse();
            softly.assertThat(gdpr.expires)
                    .as("%s is a persistent cookie", GDPR_COOKIE)
                    .isGreaterThan((double) now.getEpochSecond());
            softly.assertThat(recordedAt)
                    .as("%s=%s records consent given during this test", GDPR_DETAILS_COOKIE, details.value)
                    .isBetween(givenNotBefore.minus(CLOCK_TOLERANCE), now.plus(CLOCK_TOLERANCE));
        });

        log.info("Consent persisted: {}={} ({}), {}={}", GDPR_COOKIE, gdpr.value, level, GDPR_DETAILS_COOKIE, details.value);
    }

    private static Instant consentRecordedAt(String detailsValue) {
        Matcher matcher = CREATE_TIMESTAMP.matcher(detailsValue);
        assertThat(matcher.find()).as("%s=%s contains cookieCreateTimestamp", GDPR_DETAILS_COOKIE, detailsValue).isTrue();
        return Instant.ofEpochMilli(Long.parseLong(matcher.group(1)));
    }

    private static ConditionFactory polling(String alias, Duration atMost) {
        return await(alias)
                .atMost(atMost)
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .pollInSameThread();
    }

    private static void untilAsserted(ConditionFactory condition, ThrowingRunnable assertion) {
        try {
            condition.untilAsserted(assertion);
        } catch (ConditionTimeoutException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    private List<String> cookieNames() {
        return cookiesByName().keySet().stream().sorted().toList();
    }

    private Map<String, Cookie> cookiesByName() {
        return page.context().cookies(ConfigReader.baseUrl()).stream()
                .collect(Collectors.toMap(cookie -> cookie.name, Function.identity(), (first, duplicate) -> first));
    }
}
