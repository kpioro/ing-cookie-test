package pl.ing.qa.consent;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConsentLevel {

    POSTPONED("0", false, false),
    TECHNICAL_ONLY("1", false, false),
    MARKETING("2", false, true),
    ANALYTICAL("3", true, false),
    ALL("4", true, true);

    private final String cookieValue;
    private final boolean analyticsGranted;
    private final boolean marketingGranted;

    public static ConsentLevel fromCookieValue(String value) {
        return Arrays.stream(values())
                .filter(level -> level.cookieValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cookiePolicyGDPR value '%s'".formatted(value)));
    }
}
