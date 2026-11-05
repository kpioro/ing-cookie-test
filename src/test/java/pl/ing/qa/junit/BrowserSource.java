package pl.ing.qa.junit;

import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.ing.qa.config.BrowserName;
import pl.ing.qa.config.ConfigReader;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BrowserSource {

    public static Stream<BrowserName> browsers() {
        List<BrowserName> browsers = ConfigReader.browsers();
        log.info("Browser selection: {}", browsers);
        return browsers.stream();
    }
}
