package pl.ing.qa.pages.components;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CookieBannerComponent {

    private static final String CUSTOMIZE = "Dostosuj";
    private static final Pattern ANALYTICAL_CATEGORY = Pattern.compile("analityczn", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCEPT_SELECTED =
            Pattern.compile("^Zaakceptuj (zaznaczone|wybrane)$", Pattern.CASE_INSENSITIVE);

    private final Locator consentDialog;
    private final Locator customizeButton;
    private final Locator settingsDialog;
    private final Locator analyticalSwitch;
    private final Locator acceptSelectedButton;

    public CookieBannerComponent(Page page) {
        Locator dialogs = page.getByRole(AriaRole.DIALOG);

        this.consentDialog = dialogs.filter(new Locator.FilterOptions()
                .setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CUSTOMIZE).setExact(true))));
        this.customizeButton = consentDialog
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CUSTOMIZE).setExact(true));

        this.settingsDialog = dialogs.filter(new Locator.FilterOptions().setHas(page.getByRole(AriaRole.SWITCH)));
        this.analyticalSwitch = settingsDialog
                .getByRole(AriaRole.SWITCH, new Locator.GetByRoleOptions().setName(ANALYTICAL_CATEGORY));
        this.acceptSelectedButton = settingsDialog
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(ACCEPT_SELECTED));
    }

    public CookieBannerComponent shouldBeDisplayed() {
        assertThat(consentDialog).isVisible();
        return this;
    }

    public CookieBannerComponent customize() {
        log.info("Cookie banner: '{}'", CUSTOMIZE);
        customizeButton.click();
        assertThat(settingsDialog).isVisible();
        return this;
    }

    public CookieBannerComponent enableAnalyticalCookies() {
        log.info("Cookie banner: enabling analytical cookies");
        analyticalSwitch.check();
        assertThat(analyticalSwitch).isChecked();
        return this;
    }

    public CookieBannerComponent acceptSelected() {
        log.info("Cookie banner: accepting selected categories");
        acceptSelectedButton.click();
        assertThat(settingsDialog).isHidden();
        return this;
    }
}
