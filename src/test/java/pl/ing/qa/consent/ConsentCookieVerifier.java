package pl.ing.qa.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import pl.ing.qa.config.ConfigReader;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public final class ConsentCookieVerifier {

    public static final String GDPR_COOKIE = "cookiePolicyGDPR";
    public static final String GDPR_DETAILS_COOKIE = GDPR_COOKIE + "__details";

    private static final List<String> ANALYTICAL_COOKIE_PREFIXES = List.of("_ga", "AMCV_");
    private static final List<String> MARKETING_COOKIE_PREFIXES = List.of("_fbp", "_gcl_au", "_uetsid", "_uetvid");

    private static final Pattern CREATE_TIMESTAMP = Pattern.compile("\"cookieCreateTimestamp\"\\s*:\\s*(\\d+)");
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(5);

    private final BrowserContext context;

    public void assertNoConsentStored() {
        assertThat(cookiesByName())
                .as("consent cookies in a fresh browser context")
                .doesNotContainKeys(GDPR_COOKIE, GDPR_DETAILS_COOKIE);
    }

    public void assertAnalyticalConsentPersisted(Instant givenNotBefore) {
        await("%s and %s persisted with analytical consent".formatted(GDPR_COOKIE, GDPR_DETAILS_COOKIE))
                .atMost(ConfigReader.timeout())
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .pollInSameThread()
                .untilAsserted(() -> verifyAnalyticalConsent(givenNotBefore));
    }

    public void assertOnlyAnalyticalCookiesStored() {
        await("analytical cookies %s* stored without marketing cookies %s*"
                        .formatted(ANALYTICAL_COOKIE_PREFIXES, MARKETING_COOKIE_PREFIXES))
                .atMost(ConfigReader.timeout())
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .pollInSameThread()
                .untilAsserted(this::verifyOnlyAnalyticalCookies);
    }

    private void verifyOnlyAnalyticalCookies() {
        List<String> names = cookiesByName().keySet().stream().sorted().toList();

        SoftAssertions.assertSoftly(softly -> {
            ANALYTICAL_COOKIE_PREFIXES.forEach(prefix -> softly.assertThat(names)
                    .as("analytical cookie %s* in %s", prefix, names)
                    .anyMatch(name -> name.startsWith(prefix)));
            softly.assertThat(names)
                    .as("marketing cookies %s* in %s", MARKETING_COOKIE_PREFIXES, names)
                    .noneMatch(name -> startsWithAny(name, MARKETING_COOKIE_PREFIXES));
        });

        log.info("Analytical cookies stored: {}",
                names.stream().filter(name -> startsWithAny(name, ANALYTICAL_COOKIE_PREFIXES)).toList());
    }

    private static boolean startsWithAny(String name, List<String> prefixes) {
        return prefixes.stream().anyMatch(name::startsWith);
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

    private Map<String, Cookie> cookiesByName() {
        return context.cookies(ConfigReader.baseUrl()).stream()
                .collect(Collectors.toMap(cookie -> cookie.name, Function.identity(), (first, duplicate) -> first));
    }
}
