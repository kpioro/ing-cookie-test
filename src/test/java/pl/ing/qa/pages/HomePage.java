package pl.ing.qa.pages;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.ing.qa.config.ConfigReader;
import pl.ing.qa.pages.components.CookieBannerComponent;

@Slf4j
public final class HomePage {

    private final CookieBannerComponent cookieBanner;

    private HomePage(Page page) {
        this.cookieBanner = new CookieBannerComponent(page);
    }

    public static HomePage open(Page page) {
        String url = ConfigReader.baseUrl();
        log.info("Opening {}", url);
        Response response = page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        assertThat(response.ok()).as("HTTP %d for %s", response.status(), response.url()).isTrue();
        return new HomePage(page);
    }

    public CookieBannerComponent cookieBanner() {
        return cookieBanner;
    }
}
