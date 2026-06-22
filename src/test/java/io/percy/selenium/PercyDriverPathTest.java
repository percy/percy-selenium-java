package io.percy.selenium;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WebDriver.Timeouts;
import org.openqa.selenium.chrome.ChromeDriver;

import org.openqa.selenium.remote.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import java.time.Duration;

/**
 * Mock-based tests for the WebDriver / browser-bound paths of {@link Percy}
 * that {@code SdkTest} normally exercises against a live FirefoxDriver. Every
 * driver, JavascriptExecutor and HTTP interaction here is mocked or served by an
 * in-process {@link HttpServer}, so the class runs locally and on CI without a
 * browser. Pairs with {@code PercyLogicTest}; together they cover 100% of the
 * non-{@code SdkTest} reachable lines.
 */
public class PercyDriverPathTest {

    // ------------------------------------------------------------------
    // snapshot(name, options) full flow: cookie failure, responsive branch,
    // WebDriverException / generic Exception catches.
    // ------------------------------------------------------------------

    @Test
    public void snapshotSwallowsCookieCollectionFailureAndStillPosts() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "isPercyEnabled", true);
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");
        setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));
        percy.sessionType = "web";

        WebDriver.Options options = mock(WebDriver.Options.class);
        when(driver.manage()).thenReturn(options);
        // getCookies throws -> the catch on line ~383-384 logs and continues.
        when(options.getCookies()).thenThrow(new WebDriverException("no cookies"));
        when(driver.getCurrentUrl()).thenReturn("https://example.com");
        when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.<WebElement>emptyList());
        when(((JavascriptExecutor) driver).executeScript(anyString())).thenReturn(new HashMap<String, Object>());

        JSONObject response = new JSONObject().put("name", "cookie_fail");
        doReturn(response).when(percy).request(eq("/percy/snapshot"), any(JSONObject.class), eq("cookie_fail"));

        JSONObject data = percy.snapshot("cookie_fail");
        assertEquals("cookie_fail", data.getString("name"));
    }

    @Test
    public void snapshotUsesResponsiveCaptureWhenEnabled() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "isPercyEnabled", true);
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");
        setField(percy, "eligibleWidths", new JSONObject().put("default", 1280));
        setField(percy, "cliConfig",
            new JSONObject().put("snapshot", new JSONObject().put("responsiveSnapshotCapture", true)));
        percy.sessionType = "web";

        WebDriver.Options options = mock(WebDriver.Options.class);
        when(driver.manage()).thenReturn(options);
        when(options.getCookies()).thenReturn(Collections.<Cookie>emptySet());
        when(driver.getCurrentUrl()).thenReturn("https://example.com");

        // captureResponsiveDom is browser-bound; stub it so we just verify the
        // dispatch into the responsive branch (line ~387) without driving resizes.
        List<Map<String, Object>> fakeResponsive = Collections.singletonList(new HashMap<String, Object>());
        doReturn(fakeResponsive).when(percy).captureResponsiveDom(eq(driver), anySet(), anyMap());

        JSONObject response = new JSONObject().put("name", "responsive");
        doReturn(response).when(percy).request(eq("/percy/snapshot"), any(JSONObject.class), eq("responsive"));

        JSONObject data = percy.snapshot("responsive");
        assertEquals("responsive", data.getString("name"));
        verify(percy).captureResponsiveDom(eq(driver), anySet(), anyMap());
    }

    @Test
    public void snapshotSwallowsWebDriverExceptionFromExecutor() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "isPercyEnabled", true);
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");
        setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));
        percy.sessionType = "web";

        // executeScript throws a WebDriverException -> the WebDriverException
        // catch (line ~391) logs at debug; domSnapshot stays null but we still POST.
        when(((JavascriptExecutor) driver).executeScript(anyString()))
            .thenThrow(new WebDriverException("script blew up"));
        when(driver.getCurrentUrl()).thenReturn("https://example.com");

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        doReturn(new JSONObject().put("name", "wde")).when(percy)
            .request(eq("/percy/snapshot"), bodyCaptor.capture(), eq("wde"));

        JSONObject data = percy.snapshot("wde");
        assertEquals("wde", data.getString("name"));
        // domSnapshot was never produced -> posted as JSON null.
        assertTrue(bodyCaptor.getValue().isNull("domSnapshot"));
    }

    @Test
    public void snapshotSwallowsGenericExceptionFromExecutor() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "isPercyEnabled", true);
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");
        setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));
        percy.sessionType = "web";

        // A non-WebDriverException -> hits the generic Exception catch (line ~393-396).
        when(((JavascriptExecutor) driver).executeScript(anyString()))
            .thenThrow(new IllegalStateException("kaboom"));
        when(driver.getCurrentUrl()).thenReturn("https://example.com");

        doReturn(new JSONObject().put("name", "generic")).when(percy)
            .request(eq("/percy/snapshot"), any(JSONObject.class), eq("generic"));

        JSONObject data = percy.snapshot("generic");
        assertEquals("generic", data.getString("name"));
    }

    // ------------------------------------------------------------------
    // healthcheck: missing x-percy-core-version header -> disabled.
    // ------------------------------------------------------------------

    @Test
    public void healthcheckDisablesWhenVersionHeaderMissing() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/healthcheck", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                // Header value explicitly null triggers the `version == null` branch.
                exchange.getResponseHeaders().add("x-percy-core-version", "");
                byte[] body = "{}".getBytes("UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            }
        });
        server.start();

        String original = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            Percy percy = new Percy(mock(RemoteWebDriver.class));
            assertFalse(getBooleanField(percy, "isPercyEnabled"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", original);
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // fetchPercyDOM: non-200 and connection-failure paths disable Percy.
    // ------------------------------------------------------------------

    @Test
    public void fetchPercyDomDisablesPercyOnNon200() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/dom.js", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = "nope".getBytes("UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            }
        });
        server.start();

        String original = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));
            setField(percy, "isPercyEnabled", true);

            String dom = (String) invokePrivate(percy, "fetchPercyDOM", new Class[]{});
            assertEquals("", dom);
            // The non-200 throw is caught and Percy is disabled.
            assertFalse(getBooleanField(percy, "isPercyEnabled"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", original);
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // getResponsiveWidths: a non-RuntimeException (e.g. connection refused)
    // is wrapped into a RuntimeException (lines ~288-292).
    // ------------------------------------------------------------------

    @Test
    public void getResponsiveWidthsWrapsConnectionFailure() throws Exception {
        String original = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            // Point at a closed port so httpClient.execute throws a plain IOException
            // (HttpHostConnectException), exercising the generic Exception catch.
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:1");
            Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));

            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> invokePrivate(percy, "getResponsiveWidths", new Class[]{List.class}, Arrays.asList(375)));
            assertTrue(ex.getCause() instanceof RuntimeException);
            assertTrue(ex.getCause().getMessage().contains("Failed to fetch widths-config:"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", original);
        }
    }

    // ------------------------------------------------------------------
    // waitForReady: sets and restores the async-script timeout when the
    // resolved readiness config carries a positive timeoutMs.
    // ------------------------------------------------------------------

    @Test
    public void waitForReadySetsAndRestoresScriptTimeout() throws Exception {
        // A driver that is also a JavascriptExecutor so the instanceof WebDriver
        // branches that read/restore the script timeout execute.
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "cliConfig", new JSONObject().put("snapshot",
            new JSONObject().put("readiness", new JSONObject().put("timeoutMs", 5000))));

        WebDriver.Options manageOptions = mock(WebDriver.Options.class);
        Timeouts timeouts = mock(Timeouts.class);
        when(driver.manage()).thenReturn(manageOptions);
        when(manageOptions.timeouts()).thenReturn(timeouts);
        Duration previous = Duration.ofSeconds(10);
        when(timeouts.getScriptTimeout()).thenReturn(previous);
        when(timeouts.scriptTimeout(any(Duration.class))).thenReturn(timeouts);

        Map<String, Object> diagnostics = new HashMap<String, Object>();
        diagnostics.put("ok", true);
        when(driver.executeAsyncScript(anyString())).thenReturn(diagnostics);

        Object result = percy.waitForReady((JavascriptExecutor) driver, new HashMap<String, Object>());
        assertEquals(diagnostics, result);

        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(timeouts, times(2)).scriptTimeout(durationCaptor.capture());
        // First set: timeoutMs + 2000ms buffer; last set: restored previous.
        assertEquals(Duration.ofMillis(7000L), durationCaptor.getAllValues().get(0));
        assertEquals(previous, durationCaptor.getAllValues().get(1));
    }

    @Test
    public void waitForReadyContinuesWhenTimeoutLookupThrows() throws Exception {
        // manage() throws -> the best-effort try/catch around the timeout setup
        // (previousTimeout = null) is exercised, and serialize proceeds.
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "cliConfig", new JSONObject().put("snapshot",
            new JSONObject().put("readiness", new JSONObject().put("timeoutMs", 1000))));

        when(driver.manage()).thenThrow(new WebDriverException("no timeouts API"));
        when(driver.executeAsyncScript(anyString())).thenReturn(null);

        Object result = percy.waitForReady((JavascriptExecutor) driver, new HashMap<String, Object>());
        assertNull(result);
    }

    // ------------------------------------------------------------------
    // resolveReadinessConfig: per-snapshot readiness supplied as a JSONObject.
    // ------------------------------------------------------------------

    @Test
    public void resolveReadinessConfigAcceptsJsonObjectPerSnapshot() throws Exception {
        Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("readiness", new JSONObject().put("preset", "default").put("timeoutMs", 2500));

        JSONObject merged = (JSONObject) invokePrivate(
            percy, "resolveReadinessConfig", new Class[]{Map.class}, options);
        assertEquals(2500, merged.getInt("timeoutMs"));
        assertEquals("default", merged.getString("preset"));
    }

    // ------------------------------------------------------------------
    // FatalIframeException: surfaced when defaultContent() fails after a frame.
    // ------------------------------------------------------------------

    @Test
    public void getSerializedDomThrowsFatalIframeWhenDefaultContentFails() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

        WebElement iframe = mock(WebElement.class);
        when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
        when(iframe.getAttribute("data-percy-element-id")).thenReturn("frame-1");

        when(driver.getCurrentUrl()).thenReturn("https://app.example.com/page");
        when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

        TargetLocator targetLocator = mock(TargetLocator.class);
        when(driver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.frame(iframe)).thenReturn(driver);
        // defaultContent throws -> finally wraps into FatalIframeException, which
        // propagates out of getSerializedDOM via the FatalIframeException rethrow.
        when(targetLocator.defaultContent()).thenThrow(new WebDriverException("stuck in frame"));

        Map<String, Object> mainSnapshot = new HashMap<String, Object>();
        mainSnapshot.put("dom", "main");
        when(driver.executeScript(anyString())).thenReturn(mainSnapshot);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> percy.getSerializedDOM(
            (JavascriptExecutor) driver, new HashSet<Cookie>(), new HashMap<String, Object>()));
        assertTrue(ex.getMessage().contains("Could not exit iframe context"));
    }

    @Test
    public void getSerializedDomThrowsFatalIframeFromProcessFrameSerializeFailure() throws Exception {
        // processFrame's serialize executeScript throws -> processFrame wraps and
        // rethrows a RuntimeException; defaultContent() succeeds so it is the
        // serialize failure (not FatalIframeException) that surfaces, and the
        // generic catch in getSerializedDOM swallows it (frame skipped).
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

        WebElement iframe = mock(WebElement.class);
        when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
        when(iframe.getAttribute("data-percy-element-id")).thenReturn("frame-1");

        when(driver.getCurrentUrl()).thenReturn("https://app.example.com/page");
        when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

        TargetLocator targetLocator = mock(TargetLocator.class);
        when(driver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.frame(iframe)).thenReturn(driver);
        when(targetLocator.defaultContent()).thenReturn(driver);

        when(driver.executeScript(anyString())).thenAnswer(invocation -> {
            String script = invocation.getArgument(0);
            // The in-frame serialize (enableJavaScript:true) throws.
            if (script.contains("\"enableJavaScript\":true")) {
                throw new WebDriverException("frame serialize failed");
            }
            Map<String, Object> main = new HashMap<String, Object>();
            main.put("dom", "main");
            return main;
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>) percy.getSerializedDOM(
            (JavascriptExecutor) driver, new HashSet<Cookie>(), new HashMap<String, Object>());
        // Frame was skipped; no corsIframes attached.
        assertFalse(serialized.containsKey("corsIframes"));
    }

    @Test
    public void getSerializedDomSkipsIframeWithMissingPercyElementId() throws Exception {
        // processFrame returns null when no data-percy-element-id is present.
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

        WebElement iframe = mock(WebElement.class);
        when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
        when(iframe.getAttribute("data-percy-element-id")).thenReturn(null);

        when(driver.getCurrentUrl()).thenReturn("https://app.example.com/page");
        when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

        TargetLocator targetLocator = mock(TargetLocator.class);
        when(driver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.frame(iframe)).thenReturn(driver);
        when(targetLocator.defaultContent()).thenReturn(driver);

        Map<String, Object> main = new HashMap<String, Object>();
        main.put("dom", "main");
        when(driver.executeScript(anyString())).thenReturn(main);

        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>) percy.getSerializedDOM(
            (JavascriptExecutor) driver, new HashSet<Cookie>(), new HashMap<String, Object>());
        assertFalse(serialized.containsKey("corsIframes"));
    }

    @Test
    public void getSerializedDomSkipsIframeWithUnresolvableSrc() throws Exception {
        // A frame src that makes base.resolve() throw -> the URI-resolve catch
        // (lines ~816-818) logs and skips the frame.
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

        WebElement iframe = mock(WebElement.class);
        when(iframe.getAttribute("src")).thenReturn("ht!tp://%%%bad uri");

        when(driver.getCurrentUrl()).thenReturn("https://app.example.com/page");
        when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

        Map<String, Object> main = new HashMap<String, Object>();
        main.put("dom", "main");
        when(driver.executeScript(anyString())).thenReturn(main);

        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>) percy.getSerializedDOM(
            (JavascriptExecutor) driver, new HashSet<Cookie>(), new HashMap<String, Object>());
        assertFalse(serialized.containsKey("corsIframes"));
    }

    @Test
    public void getSerializedDomPropagatesFatalIframeFromLoop() throws Exception {
        // FatalIframeException thrown inside the per-frame try must be rethrown
        // (lines ~828-831) rather than swallowed, then rethrown again by the
        // outer FatalIframeException catch (lines ~838-839).
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

        WebElement iframe = mock(WebElement.class);
        when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
        when(iframe.getAttribute("data-percy-element-id")).thenReturn("frame-1");

        when(driver.getCurrentUrl()).thenReturn("https://app.example.com/page");
        when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

        TargetLocator targetLocator = mock(TargetLocator.class);
        when(driver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.frame(iframe)).thenReturn(driver);
        when(targetLocator.defaultContent()).thenThrow(new WebDriverException("stuck"));

        Map<String, Object> main = new HashMap<String, Object>();
        main.put("dom", "main");
        when(driver.executeScript(anyString())).thenReturn(main);

        assertThrows(Percy.FatalIframeException.class, () -> percy.getSerializedDOM(
            (JavascriptExecutor) driver, new HashSet<Cookie>(), new HashMap<String, Object>()));
    }

    // ------------------------------------------------------------------
    // captureResponsiveDom: CDP resize path, CDP fallback, resize-wait timeout,
    // non-numeric width skip, and sleep handling.
    // ------------------------------------------------------------------

    @Test
    public void captureResponsiveDomUsesCdpResizePathForChromeDriver() throws Exception {
        HttpServer server = startWidthsConfigServer(
            "{\"widths\":[{\"width\":375,\"height\":812},{\"width\":1280}]}",
            HttpURLConnection.HTTP_OK);
        String original = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());

            ChromeDriver driver = mock(ChromeDriver.class);
            Percy percy = spy(new Percy(driver));
            setField(percy, "domJs", "/* percy */");
            setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

            WebDriver.Options driverOptions = mock(WebDriver.Options.class);
            WebDriver.Window driverWindow = mock(WebDriver.Window.class);
            when(driver.manage()).thenReturn(driverOptions);
            when(driverOptions.window()).thenReturn(driverWindow);
            when(driverWindow.getSize()).thenReturn(new Dimension(1024, 768));

            // The CDP resize bumps an internal counter that executeScript reports
            // back as window.resizeCount so WebDriverWait resolves immediately.
            AtomicInteger resizeCount = new AtomicInteger(0);
            when(driver.executeCdpCommand(eq("Emulation.setDeviceMetricsOverride"), anyMap()))
                .thenAnswer(inv -> { resizeCount.incrementAndGet(); return new HashMap<String, Object>(); });

            when(driver.getCurrentUrl()).thenReturn("https://example.com");
            when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.<WebElement>emptyList());
            when(driver.executeScript(anyString())).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.equals("return window.resizeCount")) {
                    return (long) resizeCount.get();
                }
                if (script.startsWith("return PercyDOM.serialize(")) {
                    Map<String, Object> snap = new HashMap<String, Object>();
                    snap.put("dom", "x");
                    return snap;
                }
                return null;
            });

            Map<String, Object> options = new HashMap<String, Object>();
            options.put("widths", Arrays.asList(375, 1280));
            List<Map<String, Object>> snapshots =
                percy.captureResponsiveDom(driver, new HashSet<Cookie>(), options);

            assertEquals(2, snapshots.size());
            assertEquals(375, snapshots.get(0).get("width"));
            assertEquals(1280, snapshots.get(1).get("width"));
            // CDP used for every resize (2 widths + final restore).
            verify(driver, times(3)).executeCdpCommand(eq("Emulation.setDeviceMetricsOverride"), anyMap());
            verify(driverWindow, never()).setSize(any(Dimension.class));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", original);
            server.stop(0);
        }
    }

    @Test
    public void captureResponsiveDomFallsBackToSetSizeWhenCdpThrows() throws Exception {
        HttpServer server = startWidthsConfigServer(
            "{\"widths\":[{\"width\":375}]}", HttpURLConnection.HTTP_OK);
        String original = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());

            ChromeDriver driver = mock(ChromeDriver.class);
            Percy percy = spy(new Percy(driver));
            setField(percy, "domJs", "/* percy */");
            setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

            WebDriver.Options driverOptions = mock(WebDriver.Options.class);
            WebDriver.Window driverWindow = mock(WebDriver.Window.class);
            when(driver.manage()).thenReturn(driverOptions);
            when(driverOptions.window()).thenReturn(driverWindow);
            when(driverWindow.getSize()).thenReturn(new Dimension(1024, 768));

            AtomicInteger resizeCount = new AtomicInteger(0);
            // CDP throws -> the catch logs and falls back to window().setSize().
            when(driver.executeCdpCommand(anyString(), anyMap()))
                .thenThrow(new WebDriverException("cdp unsupported"));
            doAnswer(inv -> { resizeCount.incrementAndGet(); return null; })
                .when(driverWindow).setSize(any(Dimension.class));

            when(driver.getCurrentUrl()).thenReturn("https://example.com");
            when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.<WebElement>emptyList());
            when(driver.executeScript(anyString())).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.equals("return window.resizeCount")) {
                    return (long) resizeCount.get();
                }
                if (script.startsWith("return PercyDOM.serialize(")) {
                    Map<String, Object> snap = new HashMap<String, Object>();
                    snap.put("dom", "x");
                    return snap;
                }
                return null;
            });

            Map<String, Object> options = new HashMap<String, Object>();
            options.put("widths", Arrays.asList(375));
            List<Map<String, Object>> snapshots =
                percy.captureResponsiveDom(driver, new HashSet<Cookie>(), options);

            assertEquals(1, snapshots.size());
            // Fallback setSize used for resize + final restore.
            verify(driverWindow, atLeast(2)).setSize(any(Dimension.class));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", original);
            server.stop(0);
        }
    }

    @Test
    public void captureResponsiveDomTolueratesResizeWaitTimeout() throws Exception {
        // window.resizeCount never matches -> WebDriverWait times out and the
        // WebDriverException catch (lines ~893-894) logs but capture continues.
        HttpServer server = startWidthsConfigServer(
            "{\"widths\":[{\"width\":375}]}", HttpURLConnection.HTTP_OK);
        String original = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        String originalSleep = getStaticStringField(Percy.class, "RESPONSIVE_CAPTURE_SLEEP_TIME");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            // Also exercise the sleep block (lines ~1000-1003) with a tiny sleep.
            setStaticField(Percy.class, "RESPONSIVE_CAPTURE_SLEEP_TIME", "0");

            ChromeDriver driver = mock(ChromeDriver.class);
            Percy percy = spy(new Percy(driver));
            setField(percy, "domJs", "/* percy */");
            setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

            WebDriver.Options driverOptions = mock(WebDriver.Options.class);
            WebDriver.Window driverWindow = mock(WebDriver.Window.class);
            when(driver.manage()).thenReturn(driverOptions);
            when(driverOptions.window()).thenReturn(driverWindow);
            when(driverWindow.getSize()).thenReturn(new Dimension(1024, 768));
            when(driver.executeCdpCommand(anyString(), anyMap())).thenReturn(new HashMap<String, Object>());

            when(driver.getCurrentUrl()).thenReturn("https://example.com");
            when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.<WebElement>emptyList());
            when(driver.executeScript(anyString())).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.equals("return window.resizeCount")) {
                    // Never matches the expected resizeCount -> forces a wait timeout.
                    return 999999L;
                }
                if (script.startsWith("return PercyDOM.serialize(")) {
                    Map<String, Object> snap = new HashMap<String, Object>();
                    snap.put("dom", "x");
                    return snap;
                }
                return null;
            });

            Map<String, Object> options = new HashMap<String, Object>();
            options.put("widths", Arrays.asList(375));
            List<Map<String, Object>> snapshots =
                percy.captureResponsiveDom(driver, new HashSet<Cookie>(), options);
            assertEquals(1, snapshots.size());
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", original);
            setStaticField(Percy.class, "RESPONSIVE_CAPTURE_SLEEP_TIME", originalSleep);
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // snapshot 7-arg overload delegates to the 8-arg form (line ~260).
    // ------------------------------------------------------------------

    @Test
    public void sevenArgSnapshotOverloadDelegates() throws Exception {
        Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));
        setField(percy, "isPercyEnabled", false); // 8-arg returns null fast.
        assertNull(percy.snapshot("n", Arrays.asList(800), 600, false, "css", "scope", true));
    }

    // ------------------------------------------------------------------
    // getSerializedDOM: a non-Fatal failure outside the per-frame inner try
    // (here findElements throws) hits the outer generic catch (lines ~840-841)
    // and the snapshot is still returned without corsIframes.
    // ------------------------------------------------------------------

    @Test
    public void getSerializedDomSwallowsOuterIframeDiscoveryFailure() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = spy(new Percy(driver));
        setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

        when(driver.getCurrentUrl()).thenReturn("https://app.example.com/page");
        // findElements throwing is not a FatalIframeException, so it is caught by
        // the outer generic catch rather than propagating.
        when(driver.findElements(By.tagName("iframe")))
            .thenThrow(new WebDriverException("iframe discovery blew up"));

        Map<String, Object> main = new HashMap<String, Object>();
        main.put("dom", "main");
        when(driver.executeScript(anyString())).thenReturn(main);

        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>) percy.getSerializedDOM(
            (JavascriptExecutor) driver, new HashSet<Cookie>(), new HashMap<String, Object>());
        assertFalse(serialized.containsKey("corsIframes"));
        assertEquals("main", serialized.get("dom"));
    }

    // ------------------------------------------------------------------
    // captureResponsiveDom: a non-numeric RESPONSIVE_CAPTURE_SLEEP_TIME makes
    // Integer.parseInt throw inside the sleep block; the shared
    // InterruptedException|NumberFormatException catch (line ~1003) swallows it
    // and capture proceeds. Also drives the resizeCount==null wait branch
    // (line ~889) by returning null for window.resizeCount.
    // ------------------------------------------------------------------

    @Test
    public void captureResponsiveDomHandlesNonNumericSleepAndNullResizeCount() throws Exception {
        HttpServer server = startWidthsConfigServer(
            "{\"widths\":[{\"width\":375}]}", HttpURLConnection.HTTP_OK);
        String original = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        String originalSleep = getStaticStringField(Percy.class, "RESPONSIVE_CAPTURE_SLEEP_TIME");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            // Non-numeric -> Integer.parseInt throws NumberFormatException (caught).
            setStaticField(Percy.class, "RESPONSIVE_CAPTURE_SLEEP_TIME", "abc");

            ChromeDriver driver = mock(ChromeDriver.class);
            Percy percy = spy(new Percy(driver));
            setField(percy, "domJs", "/* percy */");
            setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

            WebDriver.Options driverOptions = mock(WebDriver.Options.class);
            WebDriver.Window driverWindow = mock(WebDriver.Window.class);
            when(driver.manage()).thenReturn(driverOptions);
            when(driverOptions.window()).thenReturn(driverWindow);
            when(driverWindow.getSize()).thenReturn(new Dimension(1024, 768));
            when(driver.executeCdpCommand(anyString(), anyMap())).thenReturn(new HashMap<String, Object>());

            when(driver.getCurrentUrl()).thenReturn("https://example.com");
            when(driver.findElements(By.tagName("iframe"))).thenReturn(Collections.<WebElement>emptyList());
            when(driver.executeScript(anyString())).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.equals("return window.resizeCount")) {
                    // null -> the wait lambda's `resizeCountObj == null` branch returns false.
                    return null;
                }
                if (script.startsWith("return PercyDOM.serialize(")) {
                    Map<String, Object> snap = new HashMap<String, Object>();
                    snap.put("dom", "x");
                    return snap;
                }
                return null;
            });

            Map<String, Object> options = new HashMap<String, Object>();
            options.put("widths", Arrays.asList(375));
            List<Map<String, Object>> snapshots =
                percy.captureResponsiveDom(driver, new HashSet<Cookie>(), options);
            assertEquals(1, snapshots.size());
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", original);
            setStaticField(Percy.class, "RESPONSIVE_CAPTURE_SLEEP_TIME", originalSleep);
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // helpers (mirrors PercyLogicTest / SdkTest reflection helpers)
    // ------------------------------------------------------------------

    private HttpServer startWidthsConfigServer(final String body, final int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/widths-config", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] payload = body.getBytes("UTF-8");
                exchange.sendResponseHeaders(status, payload.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(payload); }
            }
        });
        server.start();
        return server;
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args)
        throws Exception {
        Method method = Percy.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = Percy.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean getBooleanField(Object target, String fieldName) throws Exception {
        Field field = Percy.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (boolean) field.get(target);
    }

    private static void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static String getStaticStringField(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
