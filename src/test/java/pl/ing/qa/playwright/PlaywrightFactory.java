package pl.ing.qa.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.ing.qa.config.BrowserName;
import pl.ing.qa.config.ConfigReader;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlaywrightFactory {

    private static final boolean IS_LINUX = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("linux");

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    static {
        PlaywrightAssertions.setDefaultAssertionTimeout(ConfigReader.timeout().toMillis());
    }

    public static Page startSession(BrowserName browserName) {
        if (PLAYWRIGHT.get() != null) {
            throw new IllegalStateException("Thread '%s' already owns a Playwright session"
                    .formatted(Thread.currentThread().getName()));
        }
        try {
            Playwright playwright = Playwright.create();
            PLAYWRIGHT.set(playwright);

            Browser browser = browserName.browserType(playwright).launch(new BrowserType.LaunchOptions()
                    .setHeadless(ConfigReader.headless())
                    .setChromiumSandbox(!IS_LINUX));
            BROWSER.set(browser);

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale("pl-PL")
                    .setTimezoneId("Europe/Warsaw")
                    .setViewportSize(1920, 1080)
                    .setIgnoreHTTPSErrors(ignoreHttpsErrors()));
            context.setDefaultTimeout(ConfigReader.timeout().toMillis());
            CONTEXT.set(context);

            Page page = context.newPage();
            PAGE.set(page);

            log.info("Session started: {} {} (headless={})", browserName, browser.version(), ConfigReader.headless());
            return page;
        } catch (RuntimeException e) {
            closeSession();
            throw e;
        }
    }

    public static Page page() {
        return current(PAGE, "Page");
    }

    public static void closeSession() {
        closeQuietly(PAGE, "Page");
        closeQuietly(CONTEXT, "BrowserContext");
        closeQuietly(BROWSER, "Browser");
        closeQuietly(PLAYWRIGHT, "Playwright");
    }

    private static boolean ignoreHttpsErrors() {
        boolean ignore = ConfigReader.ignoreHttpsErrors();
        if (ignore) {
            log.warn("TLS certificate validation is disabled (-DignoreHttpsErrors=true)");
        }
        return ignore;
    }

    private static <T> T current(ThreadLocal<T> holder, String type) {
        T value = holder.get();
        if (value == null) {
            throw new IllegalStateException("No %s bound to thread '%s' - does the test class extend BaseTest?"
                    .formatted(type, Thread.currentThread().getName()));
        }
        return value;
    }

    private static <T extends AutoCloseable> void closeQuietly(ThreadLocal<T> holder, String type) {
        T resource = holder.get();
        holder.remove();
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            log.warn("Failed to close {}: {}", type, e.getMessage(), e);
        }
    }
}
