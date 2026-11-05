package pl.ing.qa.config;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BrowserName {

    CHROMIUM("chromium"),
    FIREFOX("firefox"),
    WEBKIT("webkit");

    public static final String ALL = "ALL";

    private final String id;

    public BrowserType browserType(Playwright playwright) {
        return switch (this) {
            case CHROMIUM -> playwright.chromium();
            case FIREFOX -> playwright.firefox();
            case WEBKIT -> playwright.webkit();
        };
    }

    public static List<BrowserName> parseSelection(String selection) {
        if (selection == null || selection.isBlank() || ALL.equalsIgnoreCase(selection.strip())) {
            return List.of(values());
        }
        return List.of(fromId(selection));
    }

    public static BrowserName fromId(String id) {
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(browser -> browser.id.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported browser '%s', expected %s or one of %s".formatted(id, ALL, Arrays.toString(values()))));
    }

    @Override
    public String toString() {
        return id;
    }
}
