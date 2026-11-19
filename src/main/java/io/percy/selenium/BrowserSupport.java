package io.percy.selenium;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.CommandInfo;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteExecuteMethod;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.HttpMethod;

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
    private static final Map<WebDriver, Cdp> EDGE_CDP = Collections.synchronizedMap(new WeakHashMap<>());

    static final String MS_CDP_COMMAND = "percyMsCdpExecute";

    // Test seams.
    static Function<WebDriver, WebDriver> augmenter = d -> new Augmenter().augment(d);
    static Function<WebDriver, BiDi> bidiFactory = BrowserSupport::reflectiveBiDi;
    static Function<RemoteWebDriver, Cdp> msCdpFactory = BrowserSupport::msCdp;

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
        if (driver instanceof HasCdp) {
            return ((HasCdp) driver)::executeCdpCommand;
        }
        if (driver instanceof RemoteWebDriver && browserName(driver).contains("edge")) {
            return EDGE_CDP.computeIfAbsent(driver, d -> edgeCdp((RemoteWebDriver) d));
        }
        return augmentedCdp(driver);
    }

    private static Cdp augmentedCdp(WebDriver driver) {
        WebDriver target = augmented(driver);
        if (!(target instanceof HasCdp)) {
            return null;
        }
        HasCdp hasCdp = (HasCdp) target;
        return hasCdp::executeCdpCommand;
    }

    // Augmenter's HasCdp posts to goog/cdp/execute whatever the browser (selenium-chrome and
    // selenium-edge both register the same command name, and goog wins), and msedgedriver rejects
    // goog. So remote Edge goes to ms/cdp/execute first; goog stays as a fallback for hubs that
    // only proxy goog, and is kept for the session once it works.
    private static Cdp edgeCdp(RemoteWebDriver driver) {
        Cdp ms;
        try {
            ms = msCdpFactory.apply(driver);
        } catch (Throwable e) {
            Percy.log("Could not register the ms/cdp/execute command: " + e, "debug");
            ms = null;
        }
        if (ms == null) {
            return augmentedCdp(driver);
        }
        final Cdp msCdp = ms;
        final Cdp[] googCdp = new Cdp[1];
        return (command, params) -> {
            if (googCdp[0] != null) {
                return googCdp[0].execute(command, params);
            }
            try {
                return msCdp.execute(command, params);
            } catch (RuntimeException msError) {
                Cdp goog = augmentedCdp(driver);
                if (goog == null) {
                    throw msError;
                }
                Map<String, Object> result;
                try {
                    result = goog.execute(command, params);
                } catch (RuntimeException googError) {
                    throw msError;
                }
                googCdp[0] = goog;
                return result;
            }
        };
    }

    @SuppressWarnings("unchecked")
    static Cdp msCdp(RemoteWebDriver driver) {
        CommandExecutor executor = httpExecutor(driver.getCommandExecutor());
        if (!(executor instanceof HttpCommandExecutor)) {
            return null;
        }
        try {
            // defineCommand is protected, but it is the only way to add an endpoint to a live session.
            Method define = HttpCommandExecutor.class.getDeclaredMethod("defineCommand", String.class, CommandInfo.class);
            define.setAccessible(true);
            define.invoke(executor, MS_CDP_COMMAND, new CommandInfo("/session/:sessionId/ms/cdp/execute", HttpMethod.POST));
        } catch (ReflectiveOperationException e) {
            Percy.log("Could not register the ms/cdp/execute command: " + e, "debug");
            return null;
        }
        RemoteExecuteMethod execute = new RemoteExecuteMethod(driver);
        return (command, params) -> {
            Map<String, Object> body = new HashMap<>();
            body.put("cmd", command);
            body.put("params", params);
            Object result = execute.execute(MS_CDP_COMMAND, body);
            return result instanceof Map ? (Map<String, Object>) result : Collections.<String, Object>emptyMap();
        };
    }

    // RemoteWebDriver wraps its HttpCommandExecutor in a TracedCommandExecutor.
    static CommandExecutor httpExecutor(CommandExecutor executor) {
        if (executor != null && executor.getClass().getName().endsWith("TracedCommandExecutor")) {
            try {
                Field field = executor.getClass().getDeclaredField("delegate");
                field.setAccessible(true);
                return (CommandExecutor) field.get(executor);
            } catch (ReflectiveOperationException e) {
                return executor;
            }
        }
        return executor;
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
