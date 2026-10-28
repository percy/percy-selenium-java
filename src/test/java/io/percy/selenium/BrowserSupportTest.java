package io.percy.selenium;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BrowserSupportTest {
    private Function<WebDriver, WebDriver> originalAugmenter;
    private Function<WebDriver, BrowserSupport.BiDi> originalBiDi;
    private Function<RemoteWebDriver, BrowserSupport.Cdp> originalMsCdp;

    @BeforeEach
    public void saveHooks() {
        originalAugmenter = BrowserSupport.augmenter;
        originalBiDi = BrowserSupport.bidiFactory;
        originalMsCdp = BrowserSupport.msCdpFactory;
        BrowserSupport.augmenter = d -> d;
    }

    @AfterEach
    public void restoreHooks() {
        BrowserSupport.augmenter = originalAugmenter;
        BrowserSupport.bidiFactory = originalBiDi;
        BrowserSupport.msCdpFactory = originalMsCdp;
    }

    private static RemoteWebDriver remote(String browserName) {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setBrowserName(browserName);
        when(driver.getCapabilities()).thenReturn(caps);
        return driver;
    }

    private static Map<String, Object> reply(String key) {
        Map<String, Object> r = new HashMap<>();
        r.put(key, true);
        return r;
    }

    @Test
    public void remoteEdgeUsesTheMsVendorEndpoint() {
        RemoteWebDriver driver = remote("MicrosoftEdge");
        List<String> ms = new ArrayList<>();
        BrowserSupport.msCdpFactory = d -> (cmd, params) -> { ms.add(cmd); return reply("ms"); };
        BrowserSupport.augmenter = d -> { throw new AssertionError("goog path must not be used"); };

        BrowserSupport.Cdp cdp = BrowserSupport.cdp(driver);
        assertEquals(reply("ms"), cdp.execute("DOM.enable", new HashMap<>()));
        assertSame(cdp, BrowserSupport.cdp(driver));
        cdp.execute("DOM.getDocument", new HashMap<>());
        assertEquals(Arrays.asList("DOM.enable", "DOM.getDocument"), ms);
    }

    @Test
    public void remoteChromeAndMsedgeNamesPickTheirVendor() {
        BrowserSupport.msCdpFactory = d -> { throw new AssertionError("ms path must not be used for Chrome"); };
        ChromeDriver augmented = mock(ChromeDriver.class);
        BrowserSupport.augmenter = d -> augmented;
        BrowserSupport.cdp(remote("chrome")).execute("DOM.enable", new HashMap<>());
        verify(augmented).executeCdpCommand(eq("DOM.enable"), anyMap());

        List<String> ms = new ArrayList<>();
        BrowserSupport.msCdpFactory = d -> (cmd, params) -> { ms.add(cmd); return reply("ms"); };
        BrowserSupport.cdp(remote("msedge")).execute("DOM.enable", new HashMap<>());
        assertEquals(Collections.singletonList("DOM.enable"), ms);
    }

    @Test
    public void remoteEdgeFallsBackToGoogOnceMsIsRejected() {
        RemoteWebDriver driver = remote("MicrosoftEdge");
        int[] msCalls = {0};
        BrowserSupport.msCdpFactory = d -> (cmd, params) -> {
            msCalls[0]++;
            throw new UnsupportedCommandException("unknown command: ms/cdp/execute");
        };
        ChromeDriver augmented = mock(ChromeDriver.class);
        when(augmented.executeCdpCommand(anyString(), anyMap())).thenReturn(reply("goog"));
        BrowserSupport.augmenter = d -> augmented;

        BrowserSupport.Cdp cdp = BrowserSupport.cdp(driver);
        assertEquals(reply("goog"), cdp.execute("DOM.enable", new HashMap<>()));
        assertEquals(reply("goog"), cdp.execute("DOM.getDocument", new HashMap<>()));
        assertEquals(1, msCalls[0]);
        verify(augmented, times(2)).executeCdpCommand(anyString(), anyMap());
    }

    @Test
    public void remoteEdgeReportsTheMsErrorWhenGoogFailsToo() {
        RemoteWebDriver driver = remote("MicrosoftEdge");
        UnsupportedCommandException msError = new UnsupportedCommandException("ms rejected");
        BrowserSupport.msCdpFactory = d -> (cmd, params) -> { throw msError; };
        ChromeDriver augmented = mock(ChromeDriver.class);
        when(augmented.executeCdpCommand(anyString(), anyMap())).thenThrow(new UnsupportedCommandException("goog rejected"));
        BrowserSupport.augmenter = d -> augmented;

        BrowserSupport.Cdp cdp = BrowserSupport.cdp(driver);
        assertSame(msError, assertThrows(UnsupportedCommandException.class, () -> cdp.execute("DOM.enable", new HashMap<>())));

        RemoteWebDriver noGoog = remote("MicrosoftEdge");
        BrowserSupport.augmenter = d -> d;
        BrowserSupport.Cdp msOnly = BrowserSupport.cdp(noGoog);
        assertSame(msError, assertThrows(UnsupportedCommandException.class, () -> msOnly.execute("DOM.enable", new HashMap<>())));
    }

    @Test
    public void remoteEdgeWithoutMsEndpointUsesAugmenter() {
        ChromeDriver augmented = mock(ChromeDriver.class);
        BrowserSupport.augmenter = d -> augmented;
        BrowserSupport.msCdpFactory = d -> null;
        BrowserSupport.cdp(remote("MicrosoftEdge")).execute("DOM.enable", new HashMap<>());
        BrowserSupport.msCdpFactory = d -> { throw new IllegalStateException("no executor"); };
        BrowserSupport.cdp(remote("MicrosoftEdge")).execute("DOM.enable", new HashMap<>());
        verify(augmented, times(2)).executeCdpCommand(eq("DOM.enable"), anyMap());
    }

    @Test
    public void msCdpNeedsAnHttpCommandExecutor() {
        RemoteWebDriver driver = remote("MicrosoftEdge");
        when(driver.getCommandExecutor()).thenReturn(mock(CommandExecutor.class));
        assertNull(BrowserSupport.msCdp(driver));
    }

    // Real RemoteWebDriver against a stub hub, so the command really goes out as
    // POST /session/{id}/ms/cdp/execute through Selenium's own executor.
    @Test
    @SuppressWarnings("unchecked")
    public void msCdpPostsToTheMsEndpointOfALiveSession() throws Exception {
        List<String> posts = new ArrayList<>();
        HttpServer hub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        hub.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8);
            String reply;
            if (path.equals("/session")) {
                reply = "{\"value\":{\"sessionId\":\"s-1\",\"capabilities\":{\"browserName\":\"MicrosoftEdge\"}}}";
            } else if (path.endsWith("/ms/cdp/execute")) {
                posts.add(exchange.getRequestMethod() + " " + path + " " + body.replaceAll("\\s", ""));
                reply = "{\"value\":{\"root\":{\"nodeId\":1}}}";
            } else {
                reply = "{\"value\":null}";
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        hub.start();
        RemoteWebDriver driver = null;
        try {
            DesiredCapabilities caps = new DesiredCapabilities();
            caps.setBrowserName("MicrosoftEdge");
            driver = new RemoteWebDriver(new URL("http://127.0.0.1:" + hub.getAddress().getPort()), caps);
            BrowserSupport.augmenter = d -> { throw new AssertionError("goog path must not be used"); };
            Map<String, Object> params = new HashMap<>();
            params.put("depth", -1);
            Map<String, Object> result = BrowserSupport.cdp(driver).execute("DOM.getDocument", params);

            assertEquals(1L, ((Number) ((Map<String, Object>) result.get("root")).get("nodeId")).longValue());
            assertEquals(1, posts.size());
            assertTrue(posts.get(0).startsWith("POST /session/s-1/ms/cdp/execute "), posts.get(0));
            assertTrue(posts.get(0).contains("\"cmd\":\"DOM.getDocument\""), posts.get(0));
            assertTrue(posts.get(0).contains("\"depth\":-1"), posts.get(0));
        } finally {
            if (driver != null) driver.quit();
            hub.stop(0);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static DesiredCapabilities firefoxWithBiDi() {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setBrowserName("firefox");
        caps.setCapability("webSocketUrl", "ws://localhost/session/x");
        return caps;
    }

    // The SDK only uses BiDi from Selenium 4.15 (setViewport), so the Java 8 (4.5.3) job skips these.
    private static Class<?> bidiClass(String name) {
        try {
            Class.forName("org.openqa.selenium.bidi.browsingcontext.BrowsingContext")
                .getMethod("setViewport", int.class, int.class);
            return Class.forName("org.openqa.selenium.bidi." + name);
        } catch (ReflectiveOperationException e) {
            Assumptions.assumeTrue(false, "BiDi not in this Selenium version");
            return null;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void reflectiveBiDiSendsRawCommands() throws Exception {
        Class<?> hasBiDi = bidiClass("HasBiDi");
        Class<?> biDiClass = bidiClass("BiDi");
        Class<?> commandClass = bidiClass("Command");
        RemoteWebDriver driver = (RemoteWebDriver) mock(RemoteWebDriver.class, withSettings().extraInterfaces(hasBiDi));
        when(driver.getCapabilities()).thenReturn(firefoxWithBiDi());
        Object bidi = mock(biDiClass);
        hasBiDi.getMethod("getBiDi").invoke(doReturn(bidi).when(driver));
        Map<String, Object> reply = new HashMap<>();
        reply.put("ok", true);
        List<Object> sent = new ArrayList<>();
        biDiClass.getMethod("send", commandClass).invoke(doAnswer(inv -> {
            sent.add(inv.getArgument(0));
            return reply;
        }).when(bidi), (Object) any());

        BrowserSupport.BiDi transport = BrowserSupport.bidi(driver);
        assertNotNull(transport);
        Map<String, Object> params = new HashMap<>();
        params.put("context", "c");
        assertEquals(reply, transport.send("browsingContext.setViewport", params));
        assertEquals("browsingContext.setViewport", commandClass.getMethod("getMethod").invoke(sent.get(0)));
        assertEquals(params, commandClass.getMethod("getParams").invoke(sent.get(0)));

        biDiClass.getMethod("send", commandClass).invoke(
            doThrow(new IllegalStateException("closed")).when(bidi), (Object) any());
        RuntimeException e = assertThrows(RuntimeException.class, () -> transport.send("x", new HashMap<>()));
        assertEquals("closed", e.getMessage());
    }

    @Test
    public void reflectiveBiDiIsNullWhenDriverCannotSpeakBiDi() {
        bidiClass("HasBiDi");
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getCapabilities()).thenReturn(firefoxWithBiDi());
        assertNull(BrowserSupport.bidi(driver));
    }

    @Test
    public void bidiFactoryFailureIsSwallowed() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getCapabilities()).thenReturn(firefoxWithBiDi());
        BrowserSupport.bidiFactory = d -> { throw new IllegalStateException("no ws"); };
        assertNull(BrowserSupport.bidi(driver));
    }

    @Test
    public void augmentationFailureFallsBackToTheDriver() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setBrowserName("chrome");
        when(driver.getCapabilities()).thenReturn(caps);
        BrowserSupport.augmenter = d -> { throw new IllegalStateException("no augmenter"); };
        assertNull(BrowserSupport.cdp(driver));
        assertSame(driver, BrowserSupport.augmented(driver));
    }

    @Test
    public void capabilitiesFailureMeansUnknownBrowser() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getCapabilities()).thenThrow(new IllegalStateException("gone"));
        assertEquals("", BrowserSupport.browserName(driver));
        assertFalse(BrowserSupport.hasWebSocketUrl(driver));
        assertEquals("", BrowserSupport.browserName(mock(WebDriver.class)));
    }

    private static RemoteWebDriver driverAt(String sessionId, String url) throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getSessionId()).thenReturn(new SessionId(sessionId));
        HttpCommandExecutor executor = mock(HttpCommandExecutor.class);
        when(executor.getAddressOfRemoteServer()).thenReturn(new URL(url));
        when(driver.getCommandExecutor()).thenReturn(executor);
        return driver;
    }

    @Test
    public void localDetectionUsesTheCommandExecutorHost() throws Exception {
        assertTrue(BrowserSupport.isLocal(driverAt("local-1", "http://localhost:9515")));
        assertTrue(BrowserSupport.isLocal(driverAt("local-2", "http://127.0.0.1:4444/wd/hub")));
        assertFalse(BrowserSupport.isLocal(driverAt("remote-1", "https://hub-cloud.browserstack.com/wd/hub")));
        assertFalse(BrowserSupport.isLocal(mock(RemoteWebDriver.class)));
        assertFalse(BrowserSupport.isLocal(mock(WebDriver.class)));
        assertEquals("local-1", BrowserSupport.sessionKey(driverAt("local-1", "http://localhost:1")));
        assertTrue(BrowserSupport.sessionKey(mock(WebDriver.class)).startsWith("driver@"));
    }

    @Test
    public void getSerializedDomSkipsClosedRootsWhenShadowDomDisabled() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = new Percy(driver);
        Map<String, Object> snap = new HashMap<>();
        snap.put("dom", "x");
        when(driver.executeScript(anyString())).thenReturn(snap);
        when(driver.getCurrentUrl()).thenReturn("https://example.com");
        Map<String, Object> options = new HashMap<>();
        options.put("disableShadowDOM", true);
        percy.getSerializedDOM((JavascriptExecutor) driver, new HashSet<Cookie>(), options);
        verify(driver, never()).getCapabilities();
    }
}
