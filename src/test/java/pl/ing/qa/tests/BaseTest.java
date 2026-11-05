package pl.ing.qa.tests;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.extension.ExtendWith;
import pl.ing.qa.junit.PlaywrightExtension;
import pl.ing.qa.playwright.PlaywrightFactory;

@ExtendWith(PlaywrightExtension.class)
public abstract class BaseTest {

    protected static final String BROWSERS = "pl.ing.qa.junit.BrowserSource#browsers";

    protected Page page() {
        return PlaywrightFactory.page();
    }

    protected BrowserContext context() {
        return PlaywrightFactory.context();
    }
}
