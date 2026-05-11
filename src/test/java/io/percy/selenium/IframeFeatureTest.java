package io.percy.selenium;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the nested cross-origin iframe capture, data-percy-ignore,
 * ignoreIframeSelectors, post-switch URL re-check, PercyContextLostException,
 * and frame-depth helpers.
 *
 * Kept in a separate class so its tests run even if the Firefox-based
 * @BeforeAll in SdkTest is unavailable in the current environment.
 */
public class IframeFeatureTest {

  @Test
  public void clampFrameDepthBoundsValuesAndDefaults() throws Exception {
    int defDepth = (int) invokeStaticPrivate("clampFrameDepth", new Class[]{int.class}, 0);
    assertEquals(5, defDepth, "Non-positive depth clamps to default");

    int negDepth = (int) invokeStaticPrivate("clampFrameDepth", new Class[]{int.class}, -3);
    assertEquals(5, negDepth, "Negative depth clamps to default");

    int hugeDepth = (int) invokeStaticPrivate("clampFrameDepth", new Class[]{int.class}, 9999);
    assertEquals(10, hugeDepth, "Huge depth clamps to cap (10)");

    int passThrough = (int) invokeStaticPrivate("clampFrameDepth", new Class[]{int.class}, 3);
    assertEquals(3, passThrough, "In-range depth passes through");
  }

  @Test
  public void normalizeIgnoreSelectorsAcceptsListAndStringInputs() throws Exception {
    @SuppressWarnings("unchecked")
    List<String> fromList = (List<String>) invokeStaticPrivate(
      "normalizeIgnoreSelectors", new Class[]{Object.class}, Arrays.asList("iframe.foo", "  ", null, "iframe[data-x]"));
    assertEquals(Arrays.asList("iframe.foo", "iframe[data-x]"), fromList);

    @SuppressWarnings("unchecked")
    List<String> fromString = (List<String>) invokeStaticPrivate(
      "normalizeIgnoreSelectors", new Class[]{Object.class}, "  iframe.single  ");
    assertEquals(Arrays.asList("iframe.single"), fromString);

    @SuppressWarnings("unchecked")
    List<String> fromNull = (List<String>) invokeStaticPrivate(
      "normalizeIgnoreSelectors", new Class[]{Object.class}, new Object[]{null});
    assertTrue(fromNull.isEmpty());
  }

  @Test
  public void resolveMaxFrameDepthPrefersOptionThenCliConfigThenDefault() throws Exception {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy percy = new Percy(mockedDriver);
    setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject().put("maxIframeDepth", 4)));

    Map<String, Object> withOption = new HashMap<>();
    withOption.put("maxIframeDepth", 7);
    int fromOption = (int) invokePrivate(percy, "resolveMaxFrameDepth", new Class[]{Map.class}, withOption);
    assertEquals(7, fromOption);

    int fromCli = (int) invokePrivate(percy, "resolveMaxFrameDepth", new Class[]{Map.class}, new HashMap<>());
    assertEquals(4, fromCli);

    setField(percy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));
    int def = (int) invokePrivate(percy, "resolveMaxFrameDepth", new Class[]{Map.class}, new HashMap<>());
    assertEquals(5, def);
  }

  @Test
  public void resolveIgnoreSelectorsCoercesOptionAndCliConfig() throws Exception {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy percy = new Percy(mockedDriver);
    setField(percy, "cliConfig",
      new JSONObject().put("snapshot",
        new JSONObject().put("ignoreIframeSelectors", new JSONArray(Arrays.asList("iframe.cli-only")))));

    Map<String, Object> withOption = new HashMap<>();
    withOption.put("ignoreIframeSelectors", Arrays.asList("iframe.from-opt"));
    @SuppressWarnings("unchecked")
    List<String> fromOption = (List<String>) invokePrivate(percy, "resolveIgnoreSelectors", new Class[]{Map.class}, withOption);
    assertEquals(Arrays.asList("iframe.from-opt"), fromOption);

    @SuppressWarnings("unchecked")
    List<String> fromCli = (List<String>) invokePrivate(percy, "resolveIgnoreSelectors", new Class[]{Map.class}, new HashMap<>());
    assertEquals(Arrays.asList("iframe.cli-only"), fromCli);
  }

  @Test
  public void skipsIframeMarkedWithDataPercyIgnore() throws Exception {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy percy = spy(new Percy(mockedDriver));
    setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

    WebElement iframe = mock(WebElement.class);
    when(iframe.getAttribute("src")).thenReturn("https://ads.example.com/frame");
    when(iframe.getAttribute("data-percy-ignore")).thenReturn("");

    when(mockedDriver.getCurrentUrl()).thenReturn("https://app.example.com/page");
    when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

    Map<String, Object> mainSnapshot = new HashMap<>();
    mainSnapshot.put("dom", "main");
    when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenReturn(mainSnapshot);

    @SuppressWarnings("unchecked")
    Map<String, Object> serialized = (Map<String, Object>) invokePrivate(
      percy, "getSerializedDOM",
      new Class[]{JavascriptExecutor.class, Set.class, Map.class},
      mockedDriver, new HashSet<Cookie>(), new HashMap<>());

    assertFalse(serialized.containsKey("corsIframes"),
      "Frames flagged with data-percy-ignore must be omitted from corsIframes");
    // Ensure we never switched into the ignored frame.
    verify(mockedDriver, never()).switchTo();
  }

  @Test
  public void skipsIframeMatchingIgnoreIframeSelectorsOption() throws Exception {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy percy = spy(new Percy(mockedDriver));
    setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

    WebElement iframe = mock(WebElement.class);
    when(iframe.getAttribute("src")).thenReturn("https://ads.example.com/banner");
    when(iframe.getAttribute("data-percy-ignore")).thenReturn(null);

    when(mockedDriver.getCurrentUrl()).thenReturn("https://app.example.com/page");
    when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

    Map<String, Object> mainSnapshot = new HashMap<>();
    mainSnapshot.put("dom", "main");
    // Single stub that branches by script content: the selector-match script
    // contains `el.matches(selectors` and must return true so the iframe is
    // recognised as matched and skipped; all others return the main DOM.
    when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class), any())).thenAnswer(inv -> {
      String script = inv.getArgument(0);
      if (script.contains("el.matches(selectors")) return Boolean.TRUE;
      return mainSnapshot;
    });
    when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenAnswer(inv -> {
      String script = inv.getArgument(0);
      if (script.contains("el.matches(selectors")) return Boolean.TRUE;
      return mainSnapshot;
    });

    Map<String, Object> options = new HashMap<>();
    options.put("ignoreIframeSelectors", Arrays.asList("iframe.ads"));

    @SuppressWarnings("unchecked")
    Map<String, Object> serialized = (Map<String, Object>) invokePrivate(
      percy, "getSerializedDOM",
      new Class[]{JavascriptExecutor.class, Set.class, Map.class},
      mockedDriver, new HashSet<Cookie>(), options);

    assertFalse(serialized.containsKey("corsIframes"),
      "Frames matching ignoreIframeSelectors must be omitted from corsIframes");
    verify(mockedDriver, never()).switchTo();
  }

  @Test
  public void processFrameSkipsAfterSwitchWhenDocumentUrlIsUnsupported() throws Exception {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy percy = spy(new Percy(mockedDriver));
    setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

    WebElement iframe = mock(WebElement.class);
    when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
    when(iframe.getAttribute("data-percy-element-id")).thenReturn("frame-xyz");

    TargetLocator targetLocator = mock(TargetLocator.class);
    when(mockedDriver.switchTo()).thenReturn(targetLocator);
    when(targetLocator.frame(iframe)).thenReturn(mockedDriver);
    when(targetLocator.defaultContent()).thenReturn(mockedDriver);

    // First executeScript is the dom.js injection (script string); the second
    // is `return document.URL` which we make report an unsupported scheme.
    when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class)))
      .thenReturn(null)               // domJs inject
      .thenReturn("about:blank");      // document.URL

    Object result = invokePrivate(percy, "processFrame", new Class[]{WebElement.class, Map.class}, iframe, new HashMap<>());
    assertNull(result, "Frame must be skipped when document.URL is unsupported after switch");
    verify(targetLocator).defaultContent();
  }

  @Test
  public void percyContextLostExceptionCarriesPartialCapture() throws Exception {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy percy = spy(new Percy(mockedDriver));

    List<Map<String, Object>> partial = new ArrayList<>();
    Map<String, Object> entry = new HashMap<>();
    entry.put("frameUrl", "https://cdn.example.com/a");
    partial.add(entry);

    Class<?> exceptionClass = Class.forName("io.percy.selenium.Percy$PercyContextLostException");
    java.lang.reflect.Constructor<?> ctor = exceptionClass.getDeclaredConstructor(String.class, Throwable.class, List.class);
    ctor.setAccessible(true);
    RuntimeException ex = (RuntimeException) ctor.newInstance("lost", new RuntimeException("inner"), partial);

    Field f = exceptionClass.getField("partialCapture");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> carried = (List<Map<String, Object>>) f.get(ex);
    assertEquals(1, carried.size());
    assertEquals("https://cdn.example.com/a", carried.get(0).get("frameUrl"));
  }

  @Test
  public void getSerializedDomRecoversPartialCaptureOnContextLost() throws Exception {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy percy = spy(new Percy(mockedDriver));
    setField(percy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

    WebElement iframe = mock(WebElement.class);
    when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
    when(iframe.getAttribute("data-percy-element-id")).thenReturn("frame-a");
    when(iframe.getAttribute("data-percy-ignore")).thenReturn(null);

    when(mockedDriver.getCurrentUrl()).thenReturn("https://app.example.com/page");
    when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

    TargetLocator targetLocator = mock(TargetLocator.class);
    when(mockedDriver.switchTo()).thenReturn(targetLocator);
    when(targetLocator.frame(iframe)).thenReturn(mockedDriver);
    when(targetLocator.defaultContent()).thenReturn(mockedDriver);
    // Simulate driver failing to step back to parent after recursion.
    when(targetLocator.parentFrame()).thenThrow(new NoSuchFrameException("driver lost frame"));

    Map<String, Object> mainSnapshot = new HashMap<>();
    mainSnapshot.put("dom", "main");
    Map<String, Object> iframeSnapshot = new HashMap<>();
    iframeSnapshot.put("dom", "iframe");

    // Three executeScript phases per call:
    //   1. main page serialize (no enableJavaScript:true in payload)
    //   2. domJs inject inside frame
    //   3. document.URL inside frame -> String
    //   4. PercyDOM.serialize({...enableJavaScript:true}) inside frame
    //   5. nested findElements / recursion path triggers parentFrame which throws
    when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenAnswer(invocation -> {
      String script = invocation.getArgument(0);
      if (script.startsWith("return PercyDOM.serialize(")) {
        if (script.contains("\"enableJavaScript\":true")) return iframeSnapshot;
        return mainSnapshot;
      }
      if (script.equals("return document.URL")) return "https://cdn.other.com/frame";
      return null;
    });

    Map<String, Object> options = new HashMap<>();
    // Force at least one recursion attempt by setting maxIframeDepth>1.
    options.put("maxIframeDepth", 2);

    @SuppressWarnings("unchecked")
    Map<String, Object> serialized = (Map<String, Object>) invokePrivate(
      percy, "getSerializedDOM",
      new Class[]{JavascriptExecutor.class, Set.class, Map.class},
      mockedDriver, new HashSet<Cookie>(), options);

    // Even though parentFrame() failed during recursion, the top frame's snapshot
    // should still appear in corsIframes (partial capture recovered).
    assertTrue(serialized.containsKey("corsIframes"),
      "Partial capture from PercyContextLostException must be preserved");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> caps = (List<Map<String, Object>>) serialized.get("corsIframes");
    assertEquals(1, caps.size());
    assertEquals("https://cdn.other.com/frame", caps.get(0).get("frameUrl"));
  }

  @Test
  public void exposeClosedShadowRootsIsNoopForNonChromeDrivers() throws Exception {
    WebDriver firefoxLike = mock(WebDriver.class);
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy percy = new Percy(mockedDriver);

    // Should not throw and should not attempt CDP on a non-Chrome driver.
    invokePrivate(percy, "exposeClosedShadowRoots", new Class[]{WebDriver.class}, firefoxLike);
    verifyNoInteractions(firefoxLike);
  }

  @Test
  public void collectClosedShadowPairsWalksTreeAndSkipsContentDocuments() throws Exception {
    Method m = Percy.class.getDeclaredMethod(
      "collectClosedShadowPairs", Map.class, List.class);
    m.setAccessible(true);

    // host -> shadowRoot (closed)
    Map<String, Object> closedShadow = new HashMap<>();
    closedShadow.put("backendNodeId", 200);
    closedShadow.put("shadowRootType", "closed");

    Map<String, Object> host = new HashMap<>();
    host.put("backendNodeId", 100);
    host.put("shadowRoots", Collections.singletonList(closedShadow));

    // open shadow on a sibling — must NOT be collected
    Map<String, Object> openShadow = new HashMap<>();
    openShadow.put("backendNodeId", 201);
    openShadow.put("shadowRootType", "open");

    Map<String, Object> openHost = new HashMap<>();
    openHost.put("backendNodeId", 101);
    openHost.put("shadowRoots", Collections.singletonList(openShadow));

    // iframe node — has contentDocument so its subtree must be skipped entirely.
    Map<String, Object> nestedClosed = new HashMap<>();
    nestedClosed.put("backendNodeId", 300);
    nestedClosed.put("shadowRootType", "closed");
    Map<String, Object> hostInIframe = new HashMap<>();
    hostInIframe.put("backendNodeId", 301);
    hostInIframe.put("shadowRoots", Collections.singletonList(nestedClosed));
    Map<String, Object> iframeDoc = new HashMap<>();
    iframeDoc.put("children", Collections.singletonList(hostInIframe));
    Map<String, Object> iframeNode = new HashMap<>();
    iframeNode.put("backendNodeId", 99);
    iframeNode.put("contentDocument", iframeDoc);

    Map<String, Object> root = new HashMap<>();
    root.put("children", Arrays.asList(host, openHost, iframeNode));

    List<Map<String, Object>> pairs = new ArrayList<>();
    m.invoke(null, root, pairs);

    assertEquals(1, pairs.size(), "Only the closed shadow root outside any iframe should be collected");
    assertEquals(100, pairs.get(0).get("hostBackendNodeId"));
    assertEquals(200, pairs.get(0).get("shadowBackendNodeId"));
  }

  // ---------- reflection helpers ----------

  private static Object invokePrivate(Object target, String name, Class<?>[] types, Object... args) throws Exception {
    Method m = Percy.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(target, args);
  }

  private static Object invokeStaticPrivate(String name, Class<?>[] types, Object... args) throws Exception {
    Method m = Percy.class.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(null, args);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field f = Percy.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }
}
