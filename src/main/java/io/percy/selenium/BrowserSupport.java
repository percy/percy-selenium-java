package io.percy.selenium;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;
import java.util.function.Function;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Feature detection for the browser protocols the SDK can drive: Chromium CDP, WebDriver BiDi and
 * plain WebDriver classic. Everything newer than the Selenium 4.5.3 compile floor is reached
 * reflectively so the SDK keeps loading on older bindings.
 */
final class BrowserSupport {
    interface Cdp {
        Map<String, Object> execute(String command, Map<String, Object> params);
    }

    interface BiDi {
        Map<String, Object> send(String method, Map<String, Object> params);
    }

    private static final Set<String> CHROMIUM_NAMES = new HashSet<>(Arrays.asList(
        "chrome", "chromium", "msedge", "microsoftedge", "chrome-headless-shell"));
    private static final Set<String> LOCAL_HOSTS = new HashSet<>(Arrays.asList(
        "localhost", "127.0.0.1", "::1", "[::1]"));

    // Augmenting a remote session builds a new proxy class (and, with BiDi, a new WebSocket), so
    // do it once per driver.
    private static final Map<WebDriver, WebDriver> AUGMENTED = Collections.synchronizedMap(new WeakHashMap<>());

    // Test seams.
    static Function<WebDriver, WebDriver> augmenter = d -> new Augmenter().augment(d);
    static Function<WebDriver, BiDi> bidiFactory = BrowserSupport::reflectiveBiDi;

    private BrowserSupport() {}

    static Capabilities capabilities(WebDriver driver) {
        try {
            if (driver instanceof HasCapabilities) {
                return ((HasCapabilities) driver).getCapabilities();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static String browserName(WebDriver driver) {
        Capabilities caps = capabilities(driver);
        String name = caps == null ? null : caps.getBrowserName();
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    static boolean isChromium(WebDriver driver) {
        String name = browserName(driver);
        if (CHROMIUM_NAMES.contains(name)) {
            return true;
        }
        return driver instanceof HasCdp && !name.contains("firefox") && !name.contains("safari");
    }

    static boolean hasWebSocketUrl(WebDriver driver) {
        Capabilities caps = capabilities(driver);
        return caps != null && caps.getCapability("webSocketUrl") instanceof String;
    }

    static WebDriver augmented(WebDriver driver) {
        WebDriver cached = AUGMENTED.get(driver);
        if (cached != null) {
            return cached;
        }
        WebDriver result = driver;
        if (driver instanceof RemoteWebDriver) {
            try {
                WebDriver aug = augmenter.apply(driver);
                if (aug != null) {
                    result = aug;
                }
            } catch (Throwable e) {
                Percy.log("Could not augment remote driver: " + e, "debug");
            }
        }
        AUGMENTED.put(driver, result);
        return result;
    }

    /** CDP for Chromium sessions, local or remote; null when the session can't take CDP. */
    static Cdp cdp(WebDriver driver) {
        if (!isChromium(driver)) {
            return null;
        }
        WebDriver target = driver instanceof HasCdp ? driver : augmented(driver);
        if (!(target instanceof HasCdp)) {
            return null;
        }
        HasCdp hasCdp = (HasCdp) target;
        return hasCdp::executeCdpCommand;
    }

    /** BiDi only when the user already started the session with webSocketUrl; never requested here. */
    static BiDi bidi(WebDriver driver) {
        if (!hasWebSocketUrl(driver)) {
            return null;
        }
        try {
            return bidiFactory.apply(driver);
        } catch (Throwable e) {
            Percy.log("BiDi unavailable: " + e, "debug");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static BiDi reflectiveBiDi(WebDriver driver) {
        try {
            // setViewport landed in Selenium 4.15; older bindings lack a usable raw BiDi command API too.
            Class.forName("org.openqa.selenium.bidi.browsingcontext.BrowsingContext")
                .getMethod("setViewport", int.class, int.class);
            Class<?> hasBiDi = Class.forName("org.openqa.selenium.bidi.HasBiDi");
            Class<?> commandClass = Class.forName("org.openqa.selenium.bidi.Command");
            WebDriver target = hasBiDi.isInstance(driver) ? driver : augmented(driver);
            if (!hasBiDi.isInstance(target)) {
                return null;
            }
            Object bidi = hasBiDi.getMethod("getBiDi").invoke(target);
            Constructor<?> ctor = commandClass.getConstructor(String.class, Map.class, Type.class);
            Method send = bidi.getClass().getMethod("send", commandClass);
            return (method, params) -> {
                try {
                    Object result = send.invoke(bidi, ctor.newInstance(method, params, Map.class));
                    return result instanceof Map ? (Map<String, Object>) result : Collections.<String, Object>emptyMap();
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    throw new RuntimeException(cause.getMessage(), cause);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** True for driver-service sessions and for remote sessions whose hub is on this machine. */
    static boolean isLocal(WebDriver driver) {
        if (!(driver instanceof RemoteWebDriver)) {
            return false;
        }
        try {
            String url = new DriverMetadata(driver).getCommandExecutorUrl();
            String host = new URI(url).getHost();
            return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    static String sessionKey(WebDriver driver) {
        try {
            if (driver instanceof RemoteWebDriver && ((RemoteWebDriver) driver).getSessionId() != null) {
                return ((RemoteWebDriver) driver).getSessionId().toString();
            }
        } catch (Exception ignored) {
        }
        return "driver@" + System.identityHashCode(driver);
    }
}
