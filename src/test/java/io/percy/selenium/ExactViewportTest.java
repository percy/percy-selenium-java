package io.percy.selenium;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ExactViewportTest {
    private Function<WebDriver, WebDriver> originalAugmenter;
    private Function<WebDriver, BrowserSupport.BiDi> originalBiDi;

    @BeforeEach
    public void saveHooks() {
        originalAugmenter = BrowserSupport.augmenter;
        originalBiDi = BrowserSupport.bidiFactory;
        BrowserSupport.augmenter = d -> d;
        BrowserSupport.bidiFactory = d -> null;
        Percy.RESIZE_WAIT_MS = 0;
    }

    @AfterEach
    public void restoreHooks() {
        BrowserSupport.augmenter = originalAugmenter;
        BrowserSupport.bidiFactory = originalBiDi;
        Percy.RESIZE_WAIT_MS = 1000;
    }

    static DesiredCapabilities caps(String browserName, boolean webSocket) {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setBrowserName(browserName);
        if (webSocket) {
            caps.setCapability("webSocketUrl", "ws://localhost:1234/session/abc");
        }
        return caps;
    }

    /** A remote driver whose window drives innerWidth, minus a fixed browser border. */
    static class FakeWindow {
        final WebDriver.Window window = mock(WebDriver.Window.class);
        final AtomicInteger innerWidth = new AtomicInteger(1024);

        FakeWindow(RemoteWebDriver driver, int border, int minWidth) {
            WebDriver.Options options = mock(WebDriver.Options.class);
            when(driver.manage()).thenReturn(options);
            when(options.window()).thenReturn(window);
            doAnswer(inv -> {
                int outer = Math.max(((Dimension) inv.getArgument(0)).getWidth(), minWidth);
                innerWidth.set(outer - border);
                return null;
            }).when(window).setSize(any(Dimension.class));
            when(driver.executeScript("return window.innerWidth")).thenAnswer(inv -> (long) innerWidth.get());
        }
    }

    private static Percy percy(WebDriver driver, JSONObject snapshotConfig) throws Exception {
        Percy percy = new Percy(driver);
        Field field = Percy.class.getDeclaredField("cliConfig");
        field.setAccessible(true);
        field.set(percy, new JSONObject().put("snapshot", snapshotConfig));
        return percy;
    }

    @Test
    public void chromiumDetectionMatrix() {
        String[][] cases = {
            {"chrome", "true"}, {"MicrosoftEdge", "true"}, {"msedge", "true"}, {"chromium", "true"},
            {"chrome-headless-shell", "true"}, {"firefox", "false"}, {"safari", "false"}, {"", "false"}
        };
        for (String[] c : cases) {
            RemoteWebDriver driver = mock(RemoteWebDriver.class);
            when(driver.getCapabilities()).thenReturn(caps(c[0], false));
            assertEquals(Boolean.parseBoolean(c[1]), BrowserSupport.isChromium(driver), c[0]);
        }
        // A local Chromium driver is CDP-capable even without a recognised browserName.
        assertTrue(BrowserSupport.isChromium(mock(ChromeDriver.class)));
    }

    @Test
    public void bidiRequiresWebSocketUrlCapability() {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        BrowserSupport.BiDi fake = (m, p) -> Collections.emptyMap();
        BrowserSupport.bidiFactory = d -> fake;
        when(driver.getCapabilities()).thenReturn(caps("firefox", false));
        assertNull(BrowserSupport.bidi(driver));
        when(driver.getCapabilities()).thenReturn(caps("firefox", true));
        assertSame(fake, BrowserSupport.bidi(driver));
    }

    @Test
    public void remoteChromeUsesAugmentedCdpAndClearsOnRestore() throws Exception {
        RemoteWebDriver remote = mock(RemoteWebDriver.class);
        when(remote.getCapabilities()).thenReturn(caps("chrome", false));
        ChromeDriver augmented = mock(ChromeDriver.class);
        BrowserSupport.augmenter = d -> augmented;
        FakeWindow fw = new FakeWindow(remote, 0, 500);
        when(augmented.executeCdpCommand(eq("Emulation.setDeviceMetricsOverride"), anyMap())).thenAnswer(inv -> {
            fw.innerWidth.set((Integer) ((Map<String, Object>) inv.getArgument(1)).get("width"));
            return new HashMap<String, Object>();
        });

        Percy percy = percy(remote, new JSONObject());
        assertEquals(Percy.ViewportMechanism.CDP, percy.setViewport(remote, 375, 800, true));
        assertEquals(375, fw.innerWidth.get());

        Map<String, Object> expected = new HashMap<>();
        expected.put("width", 375);
        expected.put("height", 800);
        expected.put("deviceScaleFactor", 1);
        expected.put("mobile", false);
        verify(augmented).executeCdpCommand("Emulation.setDeviceMetricsOverride", expected);

        percy.clearViewport(remote, EnumSet.of(Percy.ViewportMechanism.CDP), 1024, 768);
        verify(augmented).executeCdpCommand(eq("Emulation.clearDeviceMetricsOverride"), anyMap());
        verify(fw.window, never()).setSize(any(Dimension.class));
    }

    @Test
    public void firefoxWithBiDiUsesSetViewportAndResetsWithNullViewport() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getCapabilities()).thenReturn(caps("firefox", true));
        when(driver.getWindowHandle()).thenReturn("ctx-1");
        FakeWindow fw = new FakeWindow(driver, 0, 500);
        List<Map<String, Object>> sent = new ArrayList<>();
        BrowserSupport.bidiFactory = d -> (method, params) -> {
            assertEquals("browsingContext.setViewport", method);
            sent.add(params);
            Object viewport = params.get("viewport");
            if (viewport instanceof Map) fw.innerWidth.set((Integer) ((Map<?, ?>) viewport).get("width"));
            return Collections.emptyMap();
        };

        Percy percy = percy(driver, new JSONObject());
        assertEquals(Percy.ViewportMechanism.BIDI, percy.setViewport(driver, 375, 800, true));
        assertEquals("ctx-1", sent.get(0).get("context"));
        assertEquals(1, sent.get(0).get("devicePixelRatio"));
        assertEquals(375, ((Map<?, ?>) sent.get(0).get("viewport")).get("width"));

        percy.clearViewport(driver, EnumSet.of(Percy.ViewportMechanism.BIDI), 1024, 768);
        Map<String, Object> reset = sent.get(1);
        assertTrue(reset.containsKey("viewport"));
        assertNull(reset.get("viewport"));
        assertNull(reset.get("devicePixelRatio"));
        verify(fw.window, never()).setSize(any(Dimension.class));
    }

    @Test
    public void windowFallbackCorrectsBrowserBorderOnce() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getCapabilities()).thenReturn(caps("MicrosoftEdge", false));
        // Edge without CDP (augmentation unavailable) and without BiDi: 8px border.
        FakeWindow fw = new FakeWindow(driver, 8, 500);

        Percy percy = percy(driver, new JSONObject());
        assertEquals(Percy.ViewportMechanism.WINDOW, percy.setViewport(driver, 1280, 800, true));
        verify(fw.window).setSize(new Dimension(1280, 800));
        verify(fw.window).setSize(new Dimension(1288, 800));
        assertEquals(1280, fw.innerWidth.get());

        percy.clearViewport(driver, EnumSet.of(Percy.ViewportMechanism.WINDOW), 1024, 768);
        verify(fw.window).setSize(new Dimension(1024, 768));
    }

    @Test
    public void windowFallbackGivesUpBelowMinimumWindowWidth() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getCapabilities()).thenReturn(caps("firefox", false));
        FakeWindow fw = new FakeWindow(driver, 0, 500);

        Percy percy = percy(driver, new JSONObject());
        assertEquals(Percy.ViewportMechanism.WINDOW, percy.setViewport(driver, 375, 800, true));
        // One correction attempt only, then it logs and moves on.
        verify(fw.window, times(2)).setSize(any(Dimension.class));
        assertEquals(500, fw.innerWidth.get());
    }

    @Test
    public void bidiFailureFallsBackToWindowAndRestoreFailuresAreSwallowed() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getCapabilities()).thenReturn(caps("firefox", true));
        when(driver.getWindowHandle()).thenReturn("ctx-1");
        FakeWindow fw = new FakeWindow(driver, 0, 100);
        BrowserSupport.bidiFactory = d -> (method, params) -> { throw new IllegalStateException("unsupported"); };

        Percy percy = percy(driver, new JSONObject());
        assertEquals(Percy.ViewportMechanism.WINDOW, percy.setViewport(driver, 375, 800, true));
        assertEquals(375, fw.innerWidth.get());
        assertDoesNotThrow(() -> percy.clearViewport(driver, EnumSet.of(Percy.ViewportMechanism.BIDI), 1024, 768));

        ChromeDriver chrome = mock(ChromeDriver.class);
        when(chrome.executeCdpCommand(anyString(), anyMap())).thenThrow(new IllegalStateException("closed"));
        assertDoesNotThrow(() -> percy.clearViewport(chrome, EnumSet.of(Percy.ViewportMechanism.CDP), 1024, 768));
    }

    @Test
    public void legacyCdpFailureFallsBackToWindow() throws Exception {
        ChromeDriver chrome = mock(ChromeDriver.class);
        WebDriver.Options options = mock(WebDriver.Options.class);
        WebDriver.Window window = mock(WebDriver.Window.class);
        when(chrome.manage()).thenReturn(options);
        when(options.window()).thenReturn(window);
        when(chrome.executeCdpCommand(anyString(), anyMap())).thenThrow(new IllegalStateException("no cdp"));
        when(chrome.executeScript("return window.innerWidth")).thenThrow(new IllegalStateException("no js"));

        Percy percy = percy(chrome, new JSONObject());
        assertEquals(Percy.ViewportMechanism.WINDOW, percy.setViewport(chrome, 1280, 800, false));
        verify(window).setSize(new Dimension(1280, 800));
    }

    @Test
    public void exactResponsiveWidthPrecedence() throws Exception {
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        Percy percy = percy(driver, new JSONObject());
        assertTrue(percy.resolveExactResponsiveWidth(new HashMap<>()));

        Percy configOff = percy(driver, new JSONObject().put("exactResponsiveWidth", false));
        assertFalse(configOff.resolveExactResponsiveWidth(new HashMap<>()));

        Map<String, Object> perSnapshotOn = new HashMap<>();
        perSnapshotOn.put("exactResponsiveWidth", true);
        assertTrue(configOff.resolveExactResponsiveWidth(perSnapshotOn));

        Map<String, Object> perSnapshotOff = new HashMap<>();
        perSnapshotOff.put("exactResponsiveWidth", "false");
        assertFalse(percy.resolveExactResponsiveWidth(perSnapshotOff));
    }

    @Test
    public void legacyModeKeepsWindowResizeForEdgeAndCdpForLocalChrome() throws Exception {
        RemoteWebDriver edge = mock(RemoteWebDriver.class);
        when(edge.getCapabilities()).thenReturn(caps("MicrosoftEdge", false));
        ChromeDriver augmented = mock(ChromeDriver.class);
        BrowserSupport.augmenter = d -> augmented;
        FakeWindow fw = new FakeWindow(edge, 8, 500);

        Percy percy = percy(edge, new JSONObject());
        assertEquals(Percy.ViewportMechanism.WINDOW, percy.setViewport(edge, 1280, 800, false));
        verify(fw.window, times(1)).setSize(new Dimension(1280, 800));
        verifyNoInteractions(augmented);

        ChromeDriver chrome = mock(ChromeDriver.class);
        when(chrome.executeScript("return window.innerWidth")).thenReturn(375L);
        Percy chromePercy = percy(chrome, new JSONObject());
        assertEquals(Percy.ViewportMechanism.CDP, chromePercy.setViewport(chrome, 375, 800, false));
        chromePercy.clearViewport(chrome, EnumSet.of(Percy.ViewportMechanism.CDP), 1024, 768);
        verify(chrome).executeCdpCommand(eq("Emulation.clearDeviceMetricsOverride"), anyMap());
    }
}
