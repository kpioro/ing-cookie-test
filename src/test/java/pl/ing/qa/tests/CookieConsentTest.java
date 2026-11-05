package pl.ing.qa.tests;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.ing.qa.config.BrowserName;
import pl.ing.qa.consent.ConsentCookieVerifier;
import pl.ing.qa.pages.HomePage;
import pl.ing.qa.pages.components.CookieBannerComponent;

@DisplayName("ing.pl cookie consent")
class CookieConsentTest extends BaseTest {

    @ParameterizedTest(name = "[{0}] analytical consent chosen via \"Dostosuj\" is persisted")
    @MethodSource(BROWSERS)
    void shouldPersistAnalyticalConsentChosenInCookieSettings(BrowserName browser) {
        Instant testStartedAt = Instant.now();
        ConsentCookieVerifier consentCookies = ConsentCookieVerifier.of(context());

        HomePage homePage = HomePage.open(page());
        CookieBannerComponent cookieBanner = homePage.cookieBanner().shouldBeDisplayed();
        consentCookies.assertNoConsentStored();

        cookieBanner.customize()
                .enableAnalyticalCookies()
                .acceptSelected();
        consentCookies.assertAnalyticalConsentPersisted(testStartedAt);

        homePage.reload();
        consentCookies.assertOnlyAnalyticalCookiesStored();
    }
}
