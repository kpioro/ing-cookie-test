package pl.ing.qa.playwright;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Tracing;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import pl.ing.qa.config.BrowserName;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TraceRecorder {

    private static final Path TRACES_DIR = Path.of("target", "traces");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    public static void start(BrowserContext context, String title) {
        context.tracing().start(new Tracing.StartOptions()
                .setTitle(title)
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
    }

    public static void discard(BrowserContext context) {
        context.tracing().stop();
    }

    public static Path save(BrowserContext context, BrowserName browser) {
        Path trace = TRACES_DIR
                .resolve("trace-%s-%s.zip".formatted(browser.getId(), LocalDateTime.now().format(TIMESTAMP)))
                .toAbsolutePath();
        context.tracing().stop(new Tracing.StopOptions().setPath(trace));
        return trace;
    }
}
