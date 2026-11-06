package pl.ing.qa.pages;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import pl.ing.qa.config.ConfigReader;
import pl.ing.qa.pages.components.CookieBannerComponent;

@Slf4j
public final class HomePage {

    private static final Pattern WAF_INCIDENT = Pattern.compile("incident_id=([\\w-]+)");
    private static final String WEBKIT_UNTRUSTED_CERTIFICATE = "SSL peer certificate or SSH remote key was not OK";

    private final Page page;
    private final CookieBannerComponent cookieBanner;

    private HomePage(Page page) {
        this.page = page;
        this.cookieBanner = new CookieBannerComponent(page);
    }

    public static HomePage open(Page page) {
        String url = ConfigReader.baseUrl();
        log.info("Opening {}", url);
        HomePage homePage = new HomePage(page);
        homePage.assertLoaded(homePage.navigate(url));
        return homePage;
    }

    public HomePage reload() {
        log.info("Reloading {}", page.url());
        assertLoaded(page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)));
        return this;
    }

    public CookieBannerComponent cookieBanner() {
        return cookieBanner;
    }

    private Response navigate(String url) {
        try {
            return page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        } catch (PlaywrightException e) {
            if (!String.valueOf(e.getMessage()).contains(WEBKIT_UNTRUSTED_CERTIFICATE)) {
                throw e;
            }
            throw new AssertionError(("WebKit rejected the TLS certificate of %s. On Windows WebKit trusts only root "
                    + "certificates already present in the Windows store, which downloads missing roots on demand - "
                    + "see README, \"Znane ograniczenia\", for the one-time fix.").formatted(url), e);
        }
    }

    private void assertLoaded(Response response) {
        assertThat(response).as("main document response for %s", page.url()).isNotNull();
        assertThat(response.ok()).as("HTTP %d for %s", response.status(), response.url()).isTrue();
        Locator challenge = page.locator("iframe[src*='_Incapsula_Resource']");
        if (challenge.count() == 0) {
            return;
        }
        Matcher incident = WAF_INCIDENT.matcher(String.valueOf(challenge.first().getAttribute("src")));
        throw new AssertionError(("ing.pl WAF (Imperva) answered with a CAPTCHA challenge instead of the home page, "
                + "incident %s. The bank blocks traffic from outside Poland - run from a Polish IP.")
                .formatted(incident.find() ? incident.group(1) : "unknown"));
    }
}
