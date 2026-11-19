package io.percy.selenium;

import java.util.*;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoSuchShadowRootException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ClosedShadowRootsTest {
    private Function<WebDriver, WebDriver> originalAugmenter;
    private Function<WebDriver, BrowserSupport.BiDi> originalBiDi;
    private Function<RemoteWebDriver, BrowserSupport.Cdp> originalMsCdp;
    private boolean originalForce;
    private int originalMax;

    @BeforeEach
    public void saveHooks() {
        originalAugmenter = BrowserSupport.augmenter;
        originalBiDi = BrowserSupport.bidiFactory;
        originalMsCdp = BrowserSupport.msCdpFactory;
        originalForce = ClosedShadowRoots.forceClassic;
        originalMax = ClosedShadowRoots.maxHosts;
        BrowserSupport.augmenter = d -> d;
        BrowserSupport.bidiFactory = d -> null;
    }

    @AfterEach
    public void restoreHooks() {
        BrowserSupport.augmenter = originalAugmenter;
        BrowserSupport.bidiFactory = originalBiDi;
        BrowserSupport.msCdpFactory = originalMsCdp;
        ClosedShadowRoots.forceClassic = originalForce;
        ClosedShadowRoots.maxHosts = originalMax;
    }

    private static Map<String, Object> node(Object backendNodeId, Object... kv) {
        Map<String, Object> n = new HashMap<>();
        n.put("backendNodeId", backendNodeId);
        for (int i = 0; i < kv.length; i += 2) n.put((String) kv[i], kv[i + 1]);
        return n;
    }

    @Test
    public void cdpExposesClosedAndNestedRootsButSkipsFrames() {
        ChromeDriver driver = mock(ChromeDriver.class);
        Map<String, Object> nestedClosed = node(31L, "shadowRootType", "closed");
        Map<String, Object> innerHost = node(30L, "shadowRoots", Collections.singletonList(nestedClosed));
        Map<String, Object> closedRoot = node(21L, "shadowRootType", "closed", "children", Collections.singletonList(innerHost));
        Map<String, Object> openRoot = node(11L, "shadowRootType", "open");
        Map<String, Object> host = node(20L, "shadowRoots", Collections.singletonList(closedRoot));
        Map<String, Object> openHost = node(10L, "shadowRoots", Collections.singletonList(openRoot));
        Map<String, Object> frameClosed = node(41L, "shadowRootType", "closed");
        Map<String, Object> frame = node(40L, "contentDocument", node(42L), "shadowRoots", Collections.singletonList(frameClosed));
        Map<String, Object> root = node(1L, "children", Arrays.asList(openHost, host, frame));
        Map<String, Object> doc = new HashMap<>();
        doc.put("root", root);

        when(driver.executeCdpCommand(eq("DOM.getDocument"), anyMap())).thenReturn(doc);
        when(driver.executeCdpCommand(eq("DOM.resolveNode"), anyMap())).thenAnswer(inv -> {
            Object id = ((Map<?, ?>) inv.getArgument(1)).get("backendNodeId");
            Map<String, Object> obj = new HashMap<>();
            obj.put("objectId", "obj-" + id);
            Map<String, Object> resp = new HashMap<>();
            resp.put("object", obj);
            return resp;
        });

        ClosedShadowRoots.expose(driver);

        List<String> calledOn = new ArrayList<>();
        verify(driver, times(2)).executeCdpCommand(eq("Runtime.callFunctionOn"), argThat(m -> {
            calledOn.add((String) m.get("objectId"));
            return true;
        }));
        assertTrue(calledOn.contains("obj-20"));
        assertTrue(calledOn.contains("obj-30"));
        assertFalse(calledOn.contains("obj-40"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void bidiExposesRootsForFirefoxWithWebSocketUrl() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setBrowserName("firefox");
        caps.setCapability("webSocketUrl", "ws://localhost/x");
        when(driver.getCapabilities()).thenReturn(caps);
        when(driver.getWindowHandle()).thenReturn("ctx");

        List<Map<String, Object>> calls = new ArrayList<>();
        BrowserSupport.bidiFactory = d -> (method, params) -> {
            calls.add(params);
            Map<String, Object> resp = new HashMap<>();
            if (calls.size() == 1) {
                Map<String, Object> shadowValue = new HashMap<>();
                shadowValue.put("mode", "closed");
                Map<String, Object> shadow = new HashMap<>();
                shadow.put("sharedId", "root-1");
                shadow.put("value", shadowValue);
                Map<String, Object> value = new HashMap<>();
                value.put("shadowRoot", shadow);
                Map<String, Object> host = new HashMap<>();
                host.put("sharedId", "host-1");
                host.put("value", value);
                Map<String, Object> result = new HashMap<>();
                result.put("value", Collections.singletonList(host));
                resp.put("result", result);
            } else {
                Map<String, Object> result = new HashMap<>();
                result.put("value", Collections.emptyList());
                resp.put("result", result);
            }
            return resp;
        };

        ClosedShadowRoots.expose(driver);

        // list, set, then a second list scoped to the new closed root that finds nothing.
        assertEquals(3, calls.size());
        Map<String, Object> serialization = (Map<String, Object>) calls.get(0).get("serializationOptions");
        assertEquals("all", serialization.get("includeShadowTree"));
        assertEquals("ctx", ((Map<String, Object>) calls.get(0).get("target")).get("context"));
        List<Object> setArgs = (List<Object>) calls.get(1).get("arguments");
        assertEquals("array", ((Map<String, Object>) setArgs.get(0)).get("type"));
        List<Object> scopedRoots = (List<Object>) calls.get(2).get("arguments");
        assertEquals("root-1", ((Map<String, Object>) scopedRoots.get(0)).get("sharedId"));
        verify(driver, never()).executeScript(anyString(), any());
    }

    @Test
    public void classicSkipsNullAndMissingRootsAndRecurses() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        ClosedShadowRoots.forceClassic = true;
        WebElement closedHost = mock(WebElement.class);
        WebElement safariLike = mock(WebElement.class);
        WebElement noRoot = mock(WebElement.class);
        SearchContext root = mock(SearchContext.class);
        when(closedHost.getShadowRoot()).thenReturn(root);
        when(safariLike.getShadowRoot()).thenReturn(null);
        when(noRoot.getShadowRoot()).thenThrow(new NoSuchShadowRootException("none"));
        when(driver.executeScript(contains("querySelectorAll"))).thenReturn(Arrays.asList(closedHost, safariLike, noRoot));
        when(driver.executeScript(contains("querySelectorAll"), eq(root))).thenReturn(Collections.emptyList());

        ClosedShadowRoots.expose(driver);

        verify(driver).executeScript(contains("__percyClosedShadowRoots"), eq(closedHost), eq(root));
        verify(driver, times(1)).executeScript(contains("__percyClosedShadowRoots"), any(), any());
        verify(driver).executeScript(contains("querySelectorAll"), eq(root));
    }

    @Test
    public void classicStopsAtHostCap() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        ClosedShadowRoots.forceClassic = true;
        ClosedShadowRoots.maxHosts = 2;
        List<WebElement> hosts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            WebElement el = mock(WebElement.class);
            when(el.getShadowRoot()).thenThrow(new NoSuchShadowRootException("none"));
            hosts.add(el);
        }
        when(driver.executeScript(contains("querySelectorAll"))).thenReturn(hosts);

        ClosedShadowRoots.expose(driver);

        verify(hosts.get(0)).getShadowRoot();
        verify(hosts.get(1)).getShadowRoot();
        verify(hosts.get(2), never()).getShadowRoot();
    }

    @Test
    public void remoteSessionWithoutCdpOrBiDiSkipsClassic() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setBrowserName("firefox");
        when(driver.getCapabilities()).thenReturn(caps);
        ClosedShadowRoots.forceClassic = false;

        ClosedShadowRoots.expose(driver);

        verify(driver, never()).executeScript(anyString());
    }

    @Test
    public void failuresNeverEscape() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        ClosedShadowRoots.forceClassic = true;
        when(driver.executeScript(anyString())).thenThrow(new RuntimeException("boom"));
        assertDoesNotThrow(() -> ClosedShadowRoots.expose(driver));
    }

    @Test
    public void cdpWithoutClosedRootsOrUnresolvableNodesDoesNothing() {
        ChromeDriver driver = mock(ChromeDriver.class);
        Map<String, Object> doc = new HashMap<>();
        doc.put("root", node(1L, "children", Collections.singletonList(node(2L))));
        when(driver.executeCdpCommand(eq("DOM.getDocument"), anyMap())).thenReturn(doc);
        ClosedShadowRoots.expose(driver);
        verify(driver, never()).executeCdpCommand(eq("DOM.resolveNode"), anyMap());

        Map<String, Object> host = node(20L, "shadowRoots",
            Collections.singletonList(node(21L, "shadowRootType", "closed")));
        doc.put("root", node(1L, "children", Collections.singletonList(host)));
        when(driver.executeCdpCommand(eq("DOM.resolveNode"), anyMap())).thenReturn(new HashMap<String, Object>());
        ClosedShadowRoots.expose(driver);
        verify(driver, never()).executeCdpCommand(eq("Runtime.callFunctionOn"), anyMap());
    }

    @Test
    public void bidiFailureFallsThroughToClassic() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setBrowserName("firefox");
        caps.setCapability("webSocketUrl", "ws://localhost/x");
        when(driver.getCapabilities()).thenReturn(caps);
        BrowserSupport.bidiFactory = d -> (method, params) -> { throw new IllegalStateException("no script module"); };
        ClosedShadowRoots.forceClassic = true;
        when(driver.executeScript(contains("querySelectorAll"))).thenReturn("not a list");

        ClosedShadowRoots.expose(driver);

        verify(driver).executeScript(contains("querySelectorAll"));
    }

    // Remote Edge on a hub that rejects CDP (the Automate failure mode): CDP is available but fails.
    private static RemoteWebDriver remoteEdgeWithFailingCdp(boolean webSocketUrl) {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setBrowserName("MicrosoftEdge");
        if (webSocketUrl) caps.setCapability("webSocketUrl", "wss://hub/session/x/se/bidi");
        when(driver.getCapabilities()).thenReturn(caps);
        when(driver.getWindowHandle()).thenReturn("ctx");
        BrowserSupport.msCdpFactory = d -> (cmd, params) -> { throw new UnsupportedCommandException("unknown command"); };
        return driver;
    }

    @Test
    public void cdpFailureFallsThroughToBiDi() {
        RemoteWebDriver driver = remoteEdgeWithFailingCdp(true);
        List<String> sent = new ArrayList<>();
        BrowserSupport.bidiFactory = d -> (method, params) -> {
            sent.add(method);
            return new HashMap<>();
        };
        ClosedShadowRoots.forceClassic = true;

        ClosedShadowRoots.expose(driver);

        assertEquals(Collections.singletonList("script.callFunction"), sent);
        verify(driver, never()).executeScript(anyString());
    }

    @Test
    public void cdpFailureWithoutBiDiSkipsClassicOnRemoteSessions() {
        RemoteWebDriver driver = remoteEdgeWithFailingCdp(false);
        ClosedShadowRoots.forceClassic = false;

        ClosedShadowRoots.expose(driver);

        verify(driver, never()).executeScript(anyString());
    }

    @Test
    public void cdpFailureWithoutBiDiRunsClassicWhenAllowed() {
        RemoteWebDriver driver = remoteEdgeWithFailingCdp(false);
        ClosedShadowRoots.forceClassic = true;
        when(driver.executeScript(contains("querySelectorAll"))).thenReturn(new ArrayList<>());

        ClosedShadowRoots.expose(driver);

        verify(driver).executeScript(contains("querySelectorAll"));
    }

    @Test
    public void maxHostsParsing() {
        assertEquals(200, ClosedShadowRoots.parseMaxHosts(null));
        assertEquals(200, ClosedShadowRoots.parseMaxHosts("abc"));
        assertEquals(200, ClosedShadowRoots.parseMaxHosts("0"));
        assertEquals(50, ClosedShadowRoots.parseMaxHosts(" 50 "));
    }
}
