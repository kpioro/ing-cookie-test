package pl.ing.qa.pages;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
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

    private final CookieBannerComponent cookieBanner;

    private HomePage(Page page) {
        this.cookieBanner = new CookieBannerComponent(page);
    }

    public static HomePage open(Page page) {
        String url = ConfigReader.baseUrl();
        log.info("Opening {}", url);
        Response response = page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        assertThat(response.ok()).as("HTTP %d for %s", response.status(), response.url()).isTrue();
        assertNotBlockedByWaf(page);
        return new HomePage(page);
    }

    public CookieBannerComponent cookieBanner() {
        return cookieBanner;
    }

    private static void assertNotBlockedByWaf(Page page) {
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
