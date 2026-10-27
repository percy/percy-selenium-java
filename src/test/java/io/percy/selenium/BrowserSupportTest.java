package io.percy.selenium;

import java.net.URL;
import java.util.*;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BrowserSupportTest {
    private Function<WebDriver, WebDriver> originalAugmenter;
    private Function<WebDriver, BrowserSupport.BiDi> originalBiDi;

    @BeforeEach
    public void saveHooks() {
        originalAugmenter = BrowserSupport.augmenter;
        originalBiDi = BrowserSupport.bidiFactory;
        BrowserSupport.augmenter = d -> d;
    }

    @AfterEach
    public void restoreHooks() {
        BrowserSupport.augmenter = originalAugmenter;
        BrowserSupport.bidiFactory = originalBiDi;
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
