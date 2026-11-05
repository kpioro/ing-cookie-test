package pl.ing.qa.junit;

import com.microsoft.playwright.BrowserContext;
import java.lang.reflect.Method;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.slf4j.MDC;
import pl.ing.qa.config.BrowserName;
import pl.ing.qa.playwright.PlaywrightFactory;
import pl.ing.qa.playwright.TraceRecorder;

@Slf4j
public final class PlaywrightExtension implements InvocationInterceptor {

    private static final String MDC_BROWSER = "browser";

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
                                    ReflectiveInvocationContext<Method> invocationContext,
                                    ExtensionContext extensionContext) {
        invocation.skip();
        throw new ExtensionConfigurationException("%s must be a @ParameterizedTest with @MethodSource(BROWSERS)"
                .formatted(invocationContext.getExecutable()));
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                            ReflectiveInvocationContext<Method> invocationContext,
                                            ExtensionContext extensionContext) throws Throwable {
        BrowserName browser = browserArgument(invocationContext);
        MDC.put(MDC_BROWSER, browser.getId());
        try {
            BrowserContext context = PlaywrightFactory.startSession(browser).context();
            TraceRecorder.start(context, extensionContext.getDisplayName());
            try {
                invocation.proceed();
            } catch (Throwable failure) {
                saveTrace(context, browser, failure);
                throw failure;
            }
            TraceRecorder.discard(context);
        } finally {
            PlaywrightFactory.closeSession();
            MDC.remove(MDC_BROWSER);
        }
    }

    private static void saveTrace(BrowserContext context, BrowserName browser, Throwable failure) {
        try {
            Path trace = TraceRecorder.save(context, browser);
            log.error("Test failed, trace saved: {}", trace);
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    private static BrowserName browserArgument(ReflectiveInvocationContext<Method> invocationContext) {
        return invocationContext.getArguments().stream()
                .filter(BrowserName.class::isInstance)
                .map(BrowserName.class::cast)
                .findFirst()
                .orElseThrow(() -> new ExtensionConfigurationException(
                        "%s requires a BrowserName argument".formatted(invocationContext.getExecutable())));
    }
}
