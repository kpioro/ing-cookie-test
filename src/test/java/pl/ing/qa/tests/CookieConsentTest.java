package pl.ing.qa.tests;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.ing.qa.config.BrowserName;
import pl.ing.qa.consent.ConsentCookieVerifier;
import pl.ing.qa.pages.HomePage;

@DisplayName("ing.pl cookie consent")
class CookieConsentTest extends BaseTest {

    @ParameterizedTest(name = "[{0}] analytical consent chosen via \"Dostosuj\" is persisted")
    @MethodSource(BROWSERS)
    void shouldPersistAnalyticalConsentChosenInCookieSettings(BrowserName browser) {
        Instant testStartedAt = Instant.now();
        ConsentCookieVerifier consentCookies = ConsentCookieVerifier.of(context());
        consentCookies.assertNoConsentStored();

        HomePage.open(page())
                .cookieBanner()
                .shouldBeDisplayed()
                .customize()
                .enableAnalyticalCookies()
                .acceptSelected();

        consentCookies.assertAnalyticalConsentPersisted(testStartedAt);
    }
}
