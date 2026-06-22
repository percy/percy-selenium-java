package io.percy.selenium;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
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

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WrapsDriver;

import org.openqa.selenium.remote.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

/**
 * Non-driver logic tests for {@link Percy}, {@link Environment}, {@link DriverMetadata}
 * and {@link Cache}. Unlike {@code SdkTest} these never instantiate a live
 * {@code FirefoxDriver}; every WebDriver interaction is mocked and HTTP is served
 * by an in-process {@link HttpServer}, so the whole class runs locally and on CI.
 *
 * <p>Mirrors the mock / reflection style used in {@code SdkTest}: {@code spy(new Percy(...))},
 * {@code setField} / {@code setStaticField} to seed private state, and {@code invokePrivate}
 * to exercise package-private helpers.</p>
 */
public class PercyLogicTest {

    // ------------------------------------------------------------------
    // createRegion
    // ------------------------------------------------------------------

    @Test
    public void createRegionStandardIncludesConfigurationAndAssertion() {
        Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("algorithm", "standard");
        params.put("imageIgnoreThreshold", 0.2);
        params.put("bannersEnabled", false);
        params.put("adsEnabled", true);
        params.put("diffIgnoreThreshold", 0.1);

        Map<String, Object> region = percy.createRegion(params);

        @SuppressWarnings("unchecked")
        Map<String, Object> configuration = (Map<String, Object>) region.get("configuration");
        assertNotNull(configuration);
        assertEquals(0.2, configuration.get("imageIgnoreThreshold"));
        assertFalse((Boolean) configuration.get("bannersEnabled"));
        assertTrue((Boolean) configuration.get("adsEnabled"));

        @SuppressWarnings("unchecked")
        Map<String, Object> assertion = (Map<String, Object>) region.get("assertion");
        assertNotNull(assertion);
        assertEquals(0.1, assertion.get("diffIgnoreThreshold"));
    }

    @Test
    public void createRegionDefaultsAlgorithmToIgnoreAndOmitsOptionalSections() {
        Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("elementCSS", ".thing");

        Map<String, Object> region = percy.createRegion(params);

        assertEquals("ignore", region.get("algorithm"));
        @SuppressWarnings("unchecked")
        Map<String, Object> elementSelector = (Map<String, Object>) region.get("elementSelector");
        assertEquals(".thing", elementSelector.get("elementCSS"));
        assertFalse(region.containsKey("configuration"));
        assertFalse(region.containsKey("assertion"));
        assertFalse(region.containsKey("padding"));
    }

    @Test
    public void createRegionWithBoundingBoxAndPadding() {
        Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("boundingBox", "1,2,3,4");
        params.put("padding", 7);

        Map<String, Object> region = percy.createRegion(params);

        @SuppressWarnings("unchecked")
        Map<String, Object> elementSelector = (Map<String, Object>) region.get("elementSelector");
        assertEquals("1,2,3,4", elementSelector.get("boundingBox"));
        assertEquals(7, region.get("padding"));
    }

    // ------------------------------------------------------------------
    // snapshot / screenshot dispatch
    // ------------------------------------------------------------------

    @Test
    public void snapshotReturnsNullWhenPercyDisabled() throws Exception {
        Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));
        // Force the disabled state so the assertion holds regardless of whether a
        // live Percy CLI is running (e.g. under `percy exec` on CI).
        setField(percy, "isPercyEnabled", false);
        assertNull(percy.snapshot("disabled"));
        assertNull(percy.snapshot("disabled", Arrays.asList(800)));
        assertNull(percy.snapshot("disabled", Arrays.asList(800), 600));
        assertNull(percy.snapshot("disabled", new HashMap<String, Object>()));
    }

    @Test
    public void screenshotReturnsNullWhenPercyDisabled() throws Exception {
        Percy percy = spy(new Percy(mock(RemoteWebDriver.class)));
        // Force the disabled state so the assertion holds regardless of whether a
        // live Percy CLI is running (e.g. under `percy exec` on CI).
        setField(percy, "isPercyEnabled", false);
        assertNull(percy.screenshot("disabled"));
    }

    @Test
    public void snapshotPostsDomForWebSession() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "isPercyEnabled", true);
        setField(mockedPercy, "domJs", "window.PercyDOM = window.PercyDOM || {};");
        setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));
        mockedPercy.sessionType = "web";

        when(mockedDriver.getCurrentUrl()).thenReturn("https://example.com");
        WebDriver.Options mockedOptions = mock(WebDriver.Options.class);
        when(mockedDriver.manage()).thenReturn(mockedOptions);
        when(mockedOptions.getCookies()).thenReturn(Collections.<Cookie>emptySet());
        when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.<WebElement>emptyList());
        when(((JavascriptExecutor) mockedDriver).executeScript(anyString())).thenReturn(new HashMap<String, Object>());

        JSONObject mockedResponse = new JSONObject().put("name", "web_snap");
        doReturn(mockedResponse).when(mockedPercy).request(eq("/percy/snapshot"), any(JSONObject.class), eq("web_snap"));

        JSONObject data = mockedPercy.snapshot("web_snap");
        assertEquals("web_snap", data.getString("name"));
        verify(mockedPercy).request(eq("/percy/snapshot"), any(JSONObject.class), eq("web_snap"));
    }

    @Test
    public void snapshotThrowsForAutomateSession() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        setField(mockedPercy, "isPercyEnabled", true);
        mockedPercy.sessionType = "automate";

        Throwable exception = assertThrows(RuntimeException.class, () -> mockedPercy.snapshot("x"));
        assertTrue(exception.getMessage().contains("Invalid function call - snapshot()"));
    }

    @Test
    public void screenshotThrowsForWebSession() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        setField(mockedPercy, "isPercyEnabled", true);

        Throwable exception = assertThrows(RuntimeException.class, () -> mockedPercy.screenshot("x"));
        assertTrue(exception.getMessage().contains("Invalid function call - screenshot()"));
    }

    @Test
    public void screenshotSendsSessionMetadataAndCapabilities() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
        when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));

        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "isPercyEnabled", true);
        mockedPercy.sessionType = "automate";

        when(mockedDriver.getSessionId()).thenReturn(new SessionId("session-789"));
        when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability("browserName", "Chrome");
        capabilities.setCapability("platformName", "Windows");
        when(mockedDriver.getCapabilities()).thenReturn(capabilities);

        Cache.CACHE_MAP.clear();
        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        doReturn(new JSONObject()).when(mockedPercy).request(eq("/percy/automateScreenshot"), bodyCaptor.capture(), eq("Automate Snap"));

        mockedPercy.screenshot("Automate Snap");

        JSONObject body = bodyCaptor.getValue();
        assertEquals("session-789", body.getString("sessionId"));
        assertEquals("https://hub-cloud.browserstack.com/wd/hub", body.getString("commandExecutorUrl"));
        assertEquals("Automate Snap", body.getString("snapshotName"));
        assertTrue(body.getString("clientInfo").startsWith("percy-java-selenium/"));
        assertEquals("Chrome", body.getJSONObject("capabilities").getString("browserName"));
    }

    @Test
    public void screenshotConvertsSnakeCaseRegionElementsToIds() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
        when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));

        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "isPercyEnabled", true);
        mockedPercy.sessionType = "automate";

        when(mockedDriver.getSessionId()).thenReturn(new SessionId("123"));
        when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
        when(mockedDriver.getCapabilities()).thenReturn(new DesiredCapabilities());
        Cache.CACHE_MAP.clear();

        RemoteWebElement ignoreEl = mock(RemoteWebElement.class);
        RemoteWebElement considerEl = mock(RemoteWebElement.class);
        when(ignoreEl.getId()).thenReturn("ig-1");
        when(considerEl.getId()).thenReturn("co-2");

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("ignore_region_selenium_elements", Arrays.asList(ignoreEl));
        options.put("consider_region_selenium_elements", Arrays.asList(considerEl));

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        doReturn(new JSONObject()).when(mockedPercy).request(eq("/percy/automateScreenshot"), bodyCaptor.capture(), eq("Regions"));

        mockedPercy.screenshot("Regions", options);

        JSONObject capturedOptions = bodyCaptor.getValue().getJSONObject("options");
        assertEquals("ig-1", capturedOptions.getJSONArray("ignore_region_elements").getString(0));
        assertEquals("co-2", capturedOptions.getJSONArray("consider_region_elements").getString(0));
        assertFalse(capturedOptions.has("ignore_region_selenium_elements"));
        assertFalse(capturedOptions.has("consider_region_selenium_elements"));
    }

    // ------------------------------------------------------------------
    // isCaptureResponsiveDOM
    // ------------------------------------------------------------------

    @Test
    public void isCaptureResponsiveDomTrueWhenCliConfigEnablesIt() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        setField(mockedPercy, "eligibleWidths", new JSONObject().put("default", 1280));
        setField(mockedPercy, "cliConfig",
            new JSONObject().put("snapshot", new JSONObject().put("responsiveSnapshotCapture", true)));

        boolean result = (boolean) invokePrivate(
            mockedPercy, "isCaptureResponsiveDOM", new Class[]{Map.class}, new HashMap<String, Object>());
        assertTrue(result);
    }

    @Test
    public void isCaptureResponsiveDomFalseWhenNeitherSdkNorCliEnableIt() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        setField(mockedPercy, "eligibleWidths", new JSONObject().put("default", 1280));
        setField(mockedPercy, "cliConfig",
            new JSONObject().put("snapshot", new JSONObject()));

        boolean result = (boolean) invokePrivate(
            mockedPercy, "isCaptureResponsiveDOM", new Class[]{Map.class}, new HashMap<String, Object>());
        assertFalse(result);
    }

    // ------------------------------------------------------------------
    // width-config HTTP helpers
    // ------------------------------------------------------------------

    @Test
    public void buildWidthsQueryParamJoinsAndHandlesEmpty() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));

        assertEquals("?widths=320,640",
            invokePrivate(mockedPercy, "buildWidthsQueryParam", new Class[]{List.class}, Arrays.asList(320, 640)));
        assertEquals("",
            invokePrivate(mockedPercy, "buildWidthsQueryParam", new Class[]{List.class}, new Object[]{null}));
        assertEquals("",
            invokePrivate(mockedPercy, "buildWidthsQueryParam", new Class[]{List.class}, Collections.emptyList()));
    }

    @Test
    public void getResponsiveWidthsParsesWidthsAndOptionalHeights() throws Exception {
        HttpServer server = startWidthsConfigServer(
            "{\"widths\":[{\"width\":375},{\"width\":1280,\"height\":900}]}", HttpURLConnection.HTTP_OK);
        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> widths = (List<Map<String, Object>>) invokePrivate(
                mockedPercy, "getResponsiveWidths", new Class[]{List.class}, Arrays.asList(375, 1280));

            assertEquals(2, widths.size());
            assertEquals(375, widths.get(0).get("width"));
            assertFalse(widths.get(0).containsKey("height"));
            assertEquals(1280, widths.get(1).get("width"));
            assertEquals(900, widths.get(1).get("height"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    @Test
    public void getResponsiveWidthsThrowsOnNon200() throws Exception {
        HttpServer server = startWidthsConfigServer("{}", HttpURLConnection.HTTP_INTERNAL_ERROR);
        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));

            InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                () -> invokePrivate(mockedPercy, "getResponsiveWidths", new Class[]{List.class}, Arrays.asList(375)));
            assertTrue(exception.getCause().getMessage().contains("Failed to fetch widths-config (HTTP 500)"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    @Test
    public void buildRequestConfigSetsTimeouts() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        RequestConfig requestConfig = (RequestConfig) invokePrivate(
            mockedPercy, "buildRequestConfig", new Class[]{int.class}, 4321);
        assertEquals(4321, requestConfig.getSocketTimeout());
        assertEquals(4321, requestConfig.getConnectTimeout());
    }

    // ------------------------------------------------------------------
    // minHeight / responsive target height helpers
    // ------------------------------------------------------------------

    @Test
    public void resolveConfiguredMinHeightFromOptionsThenCliFallbackThenInvalid() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));

        Map<String, Object> opts = new HashMap<String, Object>();
        opts.put("minHeight", "1500");
        assertEquals(1500,
            invokePrivate(mockedPercy, "resolveConfiguredMinHeight", new Class[]{Map.class}, opts));

        setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject().put("minHeight", 700)));
        assertEquals(700,
            invokePrivate(mockedPercy, "resolveConfiguredMinHeight", new Class[]{Map.class}, new HashMap<String, Object>()));

        Map<String, Object> badOpts = new HashMap<String, Object>();
        badOpts.put("minHeight", "not-a-number");
        assertNull(invokePrivate(mockedPercy, "resolveConfiguredMinHeight", new Class[]{Map.class}, badOpts));
    }

    @Test
    public void resolveResponsiveTargetHeightHonoursFeatureFlag() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        // Clear any cliConfig that a live Percy CLI (e.g. `percy exec` on CI) may
        // have populated, so the "no minHeight anywhere" branch deterministically
        // falls back to currentHeight instead of reading cliConfig.snapshot.minHeight.
        setField(mockedPercy, "cliConfig", null);
        boolean originalFlag = getStaticBooleanField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT");
        try {
            setStaticField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", false);
            assertEquals(640, invokePrivate(mockedPercy, "resolveResponsiveTargetHeight",
                new Class[]{Map.class, int.class}, new HashMap<String, Object>(), 640));

            setStaticField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", true);
            Map<String, Object> opts = new HashMap<String, Object>();
            opts.put("minHeight", 999);
            assertEquals(999, invokePrivate(mockedPercy, "resolveResponsiveTargetHeight",
                new Class[]{Map.class, int.class}, opts, 640));

            // No minHeight anywhere -> falls back to currentHeight.
            assertEquals(640, invokePrivate(mockedPercy, "resolveResponsiveTargetHeight",
                new Class[]{Map.class, int.class}, new HashMap<String, Object>(), 640));
        } finally {
            setStaticField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", originalFlag);
        }
    }

    @Test
    public void extractResponsiveWidthsCoercesNumbersAndStrings() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));

        Map<String, Object> opts = new HashMap<String, Object>();
        opts.put("widths", Arrays.asList(320, "640", "bad", 1024));
        @SuppressWarnings("unchecked")
        List<Integer> result = (List<Integer>) invokePrivate(
            mockedPercy, "extractResponsiveWidths", new Class[]{Map.class}, opts);
        assertEquals(Arrays.asList(320, 640, 1024), result);

        // Null options and non-list widths return null.
        assertNull(invokePrivate(mockedPercy, "extractResponsiveWidths", new Class[]{Map.class}, new Object[]{null}));
        Map<String, Object> noList = new HashMap<String, Object>();
        noList.put("widths", "1280");
        assertNull(invokePrivate(mockedPercy, "extractResponsiveWidths", new Class[]{Map.class}, noList));
    }

    // ------------------------------------------------------------------
    // getOrigin / isUnsupportedIframeSrc / buildSnapshotJS
    // ------------------------------------------------------------------

    @Test
    public void getOriginReturnsSchemeAndAuthorityOrEmpty() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        assertEquals("https://example.com",
            invokePrivate(mockedPercy, "getOrigin", new Class[]{String.class}, "https://example.com/path?q=1"));
        assertEquals("",
            invokePrivate(mockedPercy, "getOrigin", new Class[]{String.class}, "not a url"));
        assertEquals("",
            invokePrivate(mockedPercy, "getOrigin", new Class[]{String.class}, "/relative/path"));
    }

    @Test
    public void isUnsupportedIframeSrcDetectsNonHttpSources() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        for (String unsupported : new String[]{null, "", "about:blank", "javascript:void(0)", "data:text/html,x", "vbscript:foo"}) {
            assertTrue((boolean) invokePrivate(mockedPercy, "isUnsupportedIframeSrc", new Class[]{String.class}, unsupported),
                "expected unsupported for: " + unsupported);
        }
        assertFalse((boolean) invokePrivate(mockedPercy, "isUnsupportedIframeSrc", new Class[]{String.class}, "https://cdn.example.com/x"));
    }

    @Test
    public void buildSnapshotJSStripsReadinessAndWrapsSerialize() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("scope", "div");
        options.put("readiness", new HashMap<String, Object>());

        String js = (String) invokePrivate(mockedPercy, "buildSnapshotJS", new Class[]{Map.class}, options);
        assertTrue(js.startsWith("return PercyDOM.serialize("));
        assertTrue(js.contains("\"scope\":\"div\""));
        assertFalse(js.contains("readiness"));
    }

    // ------------------------------------------------------------------
    // getSerializedDOM (cookies / iframes / readiness)
    // ------------------------------------------------------------------

    @Test
    public void getSerializedDomAttachesCookiesAndSkipsWhenNoIframes() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

        when(mockedDriver.getCurrentUrl()).thenReturn("https://app.example.com");
        when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.<WebElement>emptyList());
        Map<String, Object> snap = new HashMap<String, Object>();
        snap.put("dom", "main");
        when(((JavascriptExecutor) mockedDriver).executeScript(anyString())).thenReturn(snap);

        Set<Cookie> cookies = new HashSet<Cookie>();
        cookies.add(new Cookie("a", "b"));

        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>) invokePrivate(
            mockedPercy, "getSerializedDOM",
            new Class[]{JavascriptExecutor.class, Set.class, Map.class},
            mockedDriver, cookies, new HashMap<String, Object>());

        assertEquals(cookies, serialized.get("cookies"));
        assertFalse(serialized.containsKey("corsIframes"));
    }

    @Test
    public void getSerializedDomCapturesCrossOriginIframe() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

        WebElement iframe = mock(WebElement.class);
        when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
        when(iframe.getAttribute("data-percy-element-id")).thenReturn("frame-1");

        when(mockedDriver.getCurrentUrl()).thenReturn("https://app.example.com/page");
        when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

        TargetLocator targetLocator = mock(TargetLocator.class);
        when(mockedDriver.switchTo()).thenReturn(targetLocator);
        when(targetLocator.frame(iframe)).thenReturn(mockedDriver);
        when(targetLocator.defaultContent()).thenReturn(mockedDriver);

        Map<String, Object> mainSnapshot = new HashMap<String, Object>();
        mainSnapshot.put("dom", "main");
        Map<String, Object> iframeSnapshot = new HashMap<String, Object>();
        iframeSnapshot.put("dom", "iframe");

        when(((JavascriptExecutor) mockedDriver).executeScript(anyString())).thenAnswer(invocation -> {
            String script = invocation.getArgument(0);
            if (script.startsWith("return PercyDOM.serialize(") && script.contains("\"enableJavaScript\":true")) {
                return iframeSnapshot;
            }
            return mainSnapshot;
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>) invokePrivate(
            mockedPercy, "getSerializedDOM",
            new Class[]{JavascriptExecutor.class, Set.class, Map.class},
            mockedDriver, new HashSet<Cookie>(), new HashMap<String, Object>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> corsIframes = (List<Map<String, Object>>) serialized.get("corsIframes");
        assertEquals(1, corsIframes.size());
        assertEquals("https://cdn.other.com/frame", corsIframes.get(0).get("frameUrl"));
    }

    @Test
    public void getSerializedDomAttachesReadinessDiagnostics() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

        Map<String, Object> diagnostics = new HashMap<String, Object>();
        diagnostics.put("ok", true);
        when(((JavascriptExecutor) mockedDriver).executeAsyncScript(anyString())).thenReturn(diagnostics);
        Map<String, Object> domSnap = new HashMap<String, Object>();
        domSnap.put("html", "<html></html>");
        when(((JavascriptExecutor) mockedDriver).executeScript(anyString())).thenReturn(domSnap);

        Map<String, Object> result = mockedPercy.getSerializedDOM(
            (JavascriptExecutor) mockedDriver, new HashSet<Cookie>(), new HashMap<String, Object>());

        assertEquals(diagnostics, result.get("readiness_diagnostics"));
    }

    @Test
    public void getSerializedDomSkipsReadinessWhenPresetDisabled() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));

        Map<String, Object> domSnap = new HashMap<String, Object>();
        domSnap.put("html", "<html></html>");
        when(((JavascriptExecutor) mockedDriver).executeScript(anyString())).thenReturn(domSnap);

        Map<String, Object> disabled = new HashMap<String, Object>();
        disabled.put("preset", "disabled");
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("readiness", disabled);

        Map<String, Object> result = mockedPercy.getSerializedDOM(
            (JavascriptExecutor) mockedDriver, new HashSet<Cookie>(), options);

        verify((JavascriptExecutor) mockedDriver, never()).executeAsyncScript(anyString());
        assertNull(result.get("readiness_diagnostics"));
    }

    // ------------------------------------------------------------------
    // resolveReadinessConfig / waitForReady
    // ------------------------------------------------------------------

    @Test
    public void resolveReadinessConfigMergesGlobalAndPerSnapshot() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot",
            new JSONObject().put("readiness", new JSONObject().put("preset", "default").put("timeoutMs", 1000))));

        Map<String, Object> perSnapshot = new HashMap<String, Object>();
        perSnapshot.put("timeoutMs", 5000);
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("readiness", perSnapshot);

        JSONObject merged = (JSONObject) invokePrivate(
            mockedPercy, "resolveReadinessConfig", new Class[]{Map.class}, options);

        // Per-snapshot timeout wins, global preset inherited.
        assertEquals(5000, merged.getInt("timeoutMs"));
        assertEquals("default", merged.getString("preset"));
    }

    @Test
    public void waitForReadyReturnsNullWhenPresetDisabled() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));

        Map<String, Object> disabled = new HashMap<String, Object>();
        disabled.put("preset", "disabled");
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("readiness", disabled);

        Object result = mockedPercy.waitForReady((JavascriptExecutor) mockedDriver, options);
        assertNull(result);
        verify((JavascriptExecutor) mockedDriver, never()).executeAsyncScript(anyString());
    }

    @Test
    public void waitForReadyReturnsNullWhenAsyncScriptThrows() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

        when(((JavascriptExecutor) mockedDriver).executeAsyncScript(anyString()))
            .thenThrow(new RuntimeException("boom"));

        Object result = mockedPercy.waitForReady((JavascriptExecutor) mockedDriver, new HashMap<String, Object>());
        assertNull(result);
    }

    // ------------------------------------------------------------------
    // captureResponsiveDom (resize logic)
    // ------------------------------------------------------------------

    @Test
    public void captureResponsiveDomResizesPerWidthAndRestores() throws Exception {
        HttpServer server = startWidthsConfigServer(
            "{\"widths\":[{\"width\":375,\"height\":812},{\"width\":1280}]}", HttpURLConnection.HTTP_OK);
        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());

            RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
            Percy mockedPercy = spy(new Percy(mockedDriver));
            setField(mockedPercy, "domJs", "/* percy */");
            setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

            AtomicInteger changes = new AtomicInteger(0);
            WebDriver.Options driverOptions = mock(WebDriver.Options.class);
            WebDriver.Window driverWindow = mock(WebDriver.Window.class);
            when(mockedDriver.manage()).thenReturn(driverOptions);
            when(driverOptions.window()).thenReturn(driverWindow);
            when(driverWindow.getSize()).thenReturn(new Dimension(1024, 768));
            doAnswer(inv -> { changes.incrementAndGet(); return null; }).when(driverWindow).setSize(any(Dimension.class));

            when(mockedDriver.getCurrentUrl()).thenReturn("https://example.com");
            when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.<WebElement>emptyList());
            when(((JavascriptExecutor) mockedDriver).executeScript(anyString())).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.equals("return window.resizeCount")) {
                    return (long) changes.get();
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
                mockedPercy.captureResponsiveDom(mockedDriver, new HashSet<Cookie>(), options);

            ArgumentCaptor<Dimension> sizeCaptor = ArgumentCaptor.forClass(Dimension.class);
            verify(driverWindow, times(3)).setSize(sizeCaptor.capture());
            List<Dimension> sizes = sizeCaptor.getAllValues();
            assertEquals(375, sizes.get(0).getWidth());
            assertEquals(812, sizes.get(0).getHeight());
            assertEquals(1280, sizes.get(1).getWidth());
            assertEquals(768, sizes.get(1).getHeight());
            assertEquals(1024, sizes.get(2).getWidth());

            assertEquals(2, snapshots.size());
            assertEquals(375, snapshots.get(0).get("width"));
            assertEquals(1280, snapshots.get(1).get("width"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // request / postSnapshot via in-process HTTP server
    // ------------------------------------------------------------------

    @Test
    public void requestReturnsDataObjectOnSuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/snapshot", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = "{\"data\":{\"name\":\"posted\"}}".getBytes("UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));

            JSONObject result = mockedPercy.request("/percy/snapshot", new JSONObject().put("x", 1), "posted");
            assertNotNull(result);
            assertEquals("posted", result.getString("name"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    @Test
    public void requestReturnsNullWhenNoDataKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/snapshot", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = "{\"success\":true}".getBytes("UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));

            assertNull(mockedPercy.request("/percy/snapshot", new JSONObject(), "no-data"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    @Test
    public void postSnapshotStripsReadinessAndPostsUrlAndName() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "isPercyEnabled", true);

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        doReturn(new JSONObject()).when(mockedPercy).request(eq("/percy/snapshot"), bodyCaptor.capture(), eq("snap"));

        Map<String, Object> options = new HashMap<String, Object>();
        options.put("readiness", new HashMap<String, Object>());
        options.put("scope", "main");

        invokePrivate(mockedPercy, "postSnapshot",
            new Class[]{Object.class, String.class, String.class, Map.class},
            new HashMap<String, Object>(), "snap", "https://example.com", options);

        JSONObject body = bodyCaptor.getValue();
        assertEquals("https://example.com", body.getString("url"));
        assertEquals("snap", body.getString("name"));
        assertEquals("main", body.getString("scope"));
        assertFalse(body.has("readiness"));
    }

    @Test
    public void postSnapshotReturnsNullWhenDisabled() throws Exception {
        Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));
        // isPercyEnabled stays false.
        Object result = invokePrivate(mockedPercy, "postSnapshot",
            new Class[]{Object.class, String.class, String.class, Map.class},
            new HashMap<String, Object>(), "snap", "https://example.com", new HashMap<String, Object>());
        assertNull(result);
    }

    // ------------------------------------------------------------------
    // fetchPercyDOM / healthcheck (HTTP)
    // ------------------------------------------------------------------

    @Test
    public void fetchPercyDomCachesDownloadedScript() throws Exception {
        final AtomicInteger hits = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/dom.js", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                hits.incrementAndGet();
                byte[] body = "window.PercyDOM = {};".getBytes("UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            Percy mockedPercy = spy(new Percy(mock(RemoteWebDriver.class)));

            String first = (String) invokePrivate(mockedPercy, "fetchPercyDOM", new Class[]{});
            String second = (String) invokePrivate(mockedPercy, "fetchPercyDOM", new Class[]{});

            assertEquals("window.PercyDOM = {};", first);
            assertEquals(first, second);
            // Second call uses the cached value, so only one HTTP hit.
            assertEquals(1, hits.get());
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    @Test
    public void healthcheckParsesTypeWidthsAndConfig() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/healthcheck", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().add("x-percy-core-version", "1.2.3");
                byte[] body = ("{\"type\":\"web\",\"widths\":{\"default\":1280},"
                    + "\"config\":{\"snapshot\":{\"minHeight\":900}}}").getBytes("UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            // Constructor runs healthcheck against our fake CLI.
            Percy percy = new Percy(mock(RemoteWebDriver.class));

            assertEquals("web", percy.sessionType);
            assertNotNull(percy.eligibleWidths);
            assertEquals(1280, percy.eligibleWidths.getInt("default"));

            boolean enabled = getBooleanField(percy, "isPercyEnabled");
            assertTrue(enabled);
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    @Test
    public void healthcheckDisablesForUnsupportedCliMajorVersion() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/healthcheck", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().add("x-percy-core-version", "2.0.0");
                byte[] body = "{}".getBytes("UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
            }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            Percy percy = new Percy(mock(RemoteWebDriver.class));
            assertFalse(getBooleanField(percy, "isPercyEnabled"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // log
    // ------------------------------------------------------------------

    @Test
    public void logSendsToCliAndDoesNotThrow() throws Exception {
        final AtomicInteger hits = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/log", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                hits.incrementAndGet();
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
                exchange.close();
            }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            assertDoesNotThrow(() -> Percy.log("hello from test"));
            assertEquals(1, hits.get());
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // Environment
    // ------------------------------------------------------------------

    @Test
    public void environmentReportsDefaultClientAndEnvironmentInfo() {
        WebDriver driver = mock(RemoteWebDriver.class);
        Environment env = new Environment(driver);

        assertTrue(env.getClientInfo().startsWith("percy-java-selenium/"));
        assertTrue(env.getEnvironmentInfo().startsWith("selenium-java; "));
        assertEquals(Percy.getSdkVersion(), Environment.getSdkVersion());
    }

    @Test
    public void environmentHonoursOverrides() {
        Environment env = new Environment(mock(RemoteWebDriver.class));
        env.setClientInfo("percy-cucumber-java-selenium/9.9.9");
        env.setEnvironmentInfo("cucumber-java/7.0; selenium-java");

        assertEquals("percy-cucumber-java-selenium/9.9.9", env.getClientInfo());
        assertEquals("cucumber-java/7.0; selenium-java", env.getEnvironmentInfo());
    }

    @Test
    public void environmentUnwrapsWrappedDriver() {
        RemoteWebDriver inner = mock(RemoteWebDriver.class);
        WrappingDriver wrapper = mock(WrappingDriver.class);
        when(wrapper.getWrappedDriver()).thenReturn(inner);

        Environment env = new Environment(wrapper);
        // Reports the inner driver's simple class name, not the wrapper.
        assertTrue(env.getEnvironmentInfo().startsWith("selenium-java; "));
    }

    @Test
    public void percySetClientInfoOverridesEnvironment() throws Exception {
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
        when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));

        Percy mockedPercy = spy(new Percy(mockedDriver));
        setField(mockedPercy, "isPercyEnabled", true);
        mockedPercy.sessionType = "automate";
        mockedPercy.setClientInfo("percy-cucumber-java-selenium/1.0.0", "cucumber-java/7.15.0; selenium-java");

        when(mockedDriver.getSessionId()).thenReturn(new SessionId("321"));
        when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
        when(mockedDriver.getCapabilities()).thenReturn(new DesiredCapabilities());
        Cache.CACHE_MAP.clear();

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        doReturn(new JSONObject()).when(mockedPercy).request(eq("/percy/automateScreenshot"), bodyCaptor.capture(), eq("ci"));

        mockedPercy.screenshot("ci");

        assertEquals("percy-cucumber-java-selenium/1.0.0", bodyCaptor.getValue().getString("clientInfo"));
        assertEquals("cucumber-java/7.15.0; selenium-java", bodyCaptor.getValue().getString("environmentInfo"));
    }

    // ------------------------------------------------------------------
    // DriverMetadata / Cache (TracedCommandExecutor delegate path)
    // ------------------------------------------------------------------

    @Test
    public void driverMetadataCachesCapabilitiesAndExecutorUrl() throws Exception {
        Cache.CACHE_MAP.clear();
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
        when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));
        when(mockedDriver.getSessionId()).thenReturn(new SessionId("meta-1"));
        when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability("browserName", "Chrome");
        capabilities.setCapability("deviceName", "iPhone");
        when(mockedDriver.getCapabilities()).thenReturn(capabilities);

        DriverMetadata metadata = new DriverMetadata(mockedDriver);
        assertEquals("meta-1", metadata.getSessionId());

        Map<String, String> caps = metadata.getCapabilities();
        assertEquals("Chrome", caps.get("browserName"));
        assertEquals("iPhone", caps.get("deviceName"));
        // Cached: a second call returns the same instance from CACHE_MAP.
        assertSame(caps, metadata.getCapabilities());

        String url = metadata.getCommandExecutorUrl();
        assertEquals("https://hub-cloud.browserstack.com/wd/hub", url);
        assertEquals(url, Cache.CACHE_MAP.get("commandExecutorUrl_meta-1"));
    }

    // ------------------------------------------------------------------
    // helpers (mirrors SdkTest reflection helpers)
    // ------------------------------------------------------------------

    /** Abstract WebDriver that also implements WrapsDriver so it can be mocked. */
    interface WrappingDriver extends WebDriver, WrapsDriver { }

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

    private static boolean getStaticBooleanField(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (boolean) field.get(null);
    }
}
