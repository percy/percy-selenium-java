package io.percy.selenium;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.BeforeAll;
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
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.firefox.FirefoxDriver;

import io.github.bonigarcia.wdm.WebDriverManager;

import org.openqa.selenium.remote.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import java.net.URL;
  public class SdkTest {
  private static final String TEST_URL = "http://localhost:8000";
  private static WebDriver driver;
  private static Percy percy;

  @BeforeAll
  public static void testSetup() throws IOException {
    // Disable browser logs from being logged to stdout
    System.setProperty("webdriver.firefox.logfile","/dev/null");

    WebDriverManager.firefoxdriver().setup();
    TestServer.startServer();
    driver = new FirefoxDriver();
    percy = new Percy(driver);
  }

  @AfterAll
  public static void testTeardown() {
    // Close our test browser.
    driver.quit();
    // Shutdown our server and make sure the threadpool also terminates.
    TestServer.shutdown();
  }

  @BeforeEach
  public void setSessionType() {
    percy.sessionType = "web";
  }

  @Test
  public void takesLocalAppSnapshotWithProvidedName() {
    driver.get(TEST_URL);
    percy.snapshot("Snapshot with provided name");
  }

  @Test
  public void takesLocalAppSnapshotWithProvidedNameAndWidths() {
    driver.get(TEST_URL);
    percy.snapshot("Snapshot with provided name and widths", Arrays.asList(768, 992, 1200));
  }

  @Test
  public void takesLocalAppSnapshotWithProvidedNameAndMinHeight() {
    driver.get(TEST_URL);
    percy.snapshot("Snapshot with provided name and min height", null, 2000);
  }

  @Test
  public void takesMultipleSnapshotsInOneTestCase() {
    driver.get(TEST_URL);

    WebElement newTodoEl = driver.findElement(By.className("new-todo"));
    newTodoEl.sendKeys("A new todo to check off");
    newTodoEl.sendKeys(Keys.RETURN);
    percy.snapshot("Multiple snapshots in one test case -- #1", Arrays.asList(768, 992, 1200));

    driver.findElement(By.cssSelector("input.toggle")).click();
    percy.snapshot("Multiple snapshots in one test case -- #2", Arrays.asList(768, 992, 1200));
  }

  @Test
  public void takesSnapshotWithCrossOriginIframe() {
    driver.get(TEST_URL + "/cors-iframe.html");
    percy.snapshot("Snapshot with cross-origin iframe");
  }

  @Test
  public void snapshotALiveHTTPSite() {
    driver.get("http://example.com");
    percy.snapshot("Site served with HTTP");
  }

  @Test
  public void snapshotsWithJavaScriptEnabled() {
    driver.get("https://example.com");
    percy.snapshot("Site with JS enabled", null, null, true);
  }

  @Test
  public void snapshotsWithPercyCSS() {
    driver.get("https://example.com");
    percy.snapshot("Site with Percy CSS", null, null, false, "body { background-color: purple; }");
  }

  @Test
  public void snapshotsWithScope() {
    driver.get("https://example.com");
    percy.snapshot("Site with scope", null, null, false, "", "div");
  }

  @Test
  public void snapshotWithOptions() {
    driver.get("https://example.com");
    Map<String, Object> options = new HashMap<String, Object>();
    options.put("percyCSS", "body { background-color: purple }");
    options.put("domTransformation", "(documentElement) => documentElement.querySelector('body').style.color = 'green';");
    options.put("scope", "div");
    options.put("widths", Arrays.asList(768, 992, 1200));
    percy.snapshot("Site with options", options);
  }

  @Test
  public void takeSnapshotWithSyncCLI(){
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    Percy mockedPercy = spy(new Percy(mockedDriver));

    try {
      setField(mockedPercy, "isPercyEnabled", true);
      setField(mockedPercy, "domJs", "window.PercyDOM = window.PercyDOM || {}; window.PercyDOM.serialize = function(){ return {}; };");
      setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));
    } catch (Exception e) {
      fail("Failed to setup test state: " + e.getMessage());
    }

    when(mockedDriver.getCurrentUrl()).thenReturn("https://example.com");
    WebDriver.Options mockedOptions = mock(WebDriver.Options.class);
    when(mockedDriver.manage()).thenReturn(mockedOptions);
    when(mockedOptions.getCookies()).thenReturn(Collections.emptySet());
    when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenReturn(new HashMap<String, Object>());

    JSONObject mockedResponse = new JSONObject();
    mockedResponse.put("snapshot-name", "test_sync_cli_snapshot");
    mockedResponse.put("status", "success");
    mockedResponse.put("screenshots", new JSONArray());
    doReturn(mockedResponse).when(mockedPercy).request(eq("/percy/snapshot"), any(JSONObject.class), eq("test_sync_cli_snapshot"));

    Map<String, Object> options = new HashMap<String, Object>();
    options.put("sync", true);

    JSONObject data = mockedPercy.snapshot("test_sync_cli_snapshot", options);
    assertEquals(data.getString("snapshot-name"), "test_sync_cli_snapshot");
    assertEquals(data.getString("status"), "success");
    assertEquals(data.get("screenshots").getClass().isAssignableFrom(JSONArray.class), true);
  }

    @Test
    public void snapshotWithResponsiveSnapshotCapture() {
      // To run via test via chrome CDP uncomment below lines and replace chromedriver path
//      System.setProperty("webdriver.chrome.driver", "<chromedriver_path>");
//      ChromeOptions chromeOptions = new ChromeOptions();
//      chromeOptions.addArguments("--remote-allow-origins=*");
//      driver = new ChromeDriver(chromeOptions);

      driver.get("https://www.webfx.com/tools/whats-my-browser-size/");
      Map<String, Object> options = new HashMap<String, Object>();
      options.put("widths", Arrays.asList(768, 992, 1200));
      options.put("responsiveSnapshotCapture", true);
      percy.snapshot("Site with snapshotWithResponsiveSnapshotCapture", options);
    }

  @Test
  public void takeScreenshot() {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
    try {
      when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));
    } catch (Exception e) {
    }
    Percy mockedPercy = spy(new Percy(mockedDriver));
    try {
      setField(mockedPercy, "isPercyEnabled", true);
    } catch (Exception e) {
      fail("Failed to setup test state: " + e.getMessage());
    }
    mockedPercy.sessionType = "automate";
    when(mockedDriver.getSessionId()).thenReturn(new SessionId("123"));
    when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
    DesiredCapabilities capabilities = new DesiredCapabilities();
    capabilities.setCapability("browserName", "Chrome");
    when(mockedDriver.getCapabilities()).thenReturn(capabilities);
    mockedPercy.screenshot("Test");
    verify(mockedPercy).request(eq("/percy/automateScreenshot"), any(), eq("Test"));
  }

    @Test
    public void takeScreenshotWithOptions() {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
      try {
        when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));
      } catch (Exception e) {
      }
      Percy mockedPercy = spy(new Percy(mockedDriver));
      try {
        setField(mockedPercy, "isPercyEnabled", true);
      } catch (Exception e) {
        fail("Failed to setup test state: " + e.getMessage());
      }
      mockedPercy.sessionType = "automate";
      when(mockedDriver.getSessionId()).thenReturn(new SessionId("123"));
      when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
      DesiredCapabilities capabilities = new DesiredCapabilities();
      capabilities.setCapability("browserName", "Chrome");
      when(mockedDriver.getCapabilities()).thenReturn(capabilities);
      Map<String, Object> options = new HashMap<String, Object>();
      RemoteWebElement mockedElement = mock(RemoteWebElement.class);
      RemoteWebElement mockedConsiderElement = mock(RemoteWebElement.class);
      when(mockedElement.getId()).thenReturn("1234");
      when(mockedConsiderElement.getId()).thenReturn("5678");
      options.put("ignore_region_selenium_elements", Arrays.asList(mockedElement));
      mockedPercy.screenshot("Test", options);
      verify(mockedPercy).request(eq("/percy/automateScreenshot"), any() , eq("Test"));
    }

    @Test
    public void takeSnapshotThrowErrorForPOA() {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));
      mockedPercy.sessionType = "automate";
      try {
        setField(mockedPercy, "isPercyEnabled", true);
      } catch (Exception e) {
        fail("Failed to setup test state: " + e.getMessage());
      }
      Throwable exception = assertThrows(RuntimeException.class, () -> mockedPercy.snapshot("Test"));
      assertEquals("Invalid function call - snapshot(). Please use screenshot() function while using Percy with Automate. For more information on usage of PercyScreenshot, refer https://www.browserstack.com/docs/percy/integrate/functional-and-visual", exception.getMessage());
    }

    @Test
    public void takeScreenshotThrowErrorForWeb() {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));
      try {
        setField(mockedPercy, "isPercyEnabled", true);
      } catch (Exception e) {
        fail("Failed to setup test state: " + e.getMessage());
      }
      Throwable exception = assertThrows(RuntimeException.class, () -> mockedPercy.screenshot("Test"));
      assertEquals("Invalid function call - screenshot(). Please use snapshot() function for taking screenshot. screenshot() should be used only while using Percy with Automate. For more information on usage of snapshot(), refer doc for your language https://www.browserstack.com/docs/percy/integrate/overview", exception.getMessage());
    }

    @Test
    public void responsiveSnapshotCaptureUsesSdkOptionWhenEligible() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      setField(mockedPercy, "eligibleWidths", new JSONObject().put("default", 1280));
      setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject().put("responsiveSnapshotCapture", false)));

      Map<String, Object> options = new HashMap<String, Object>();
      options.put("responsiveSnapshotCapture", true);

      boolean result = (boolean) invokePrivate(mockedPercy, "isCaptureResponsiveDOM", new Class[]{Map.class}, options);

      assertTrue(result);
    }

    @Test
    public void responsiveSnapshotCaptureDisabledForDeferUploads() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      setField(mockedPercy, "eligibleWidths", new JSONObject().put("default", 1280));
      setField(
        mockedPercy,
        "cliConfig",
        new JSONObject()
          .put("percy", new JSONObject().put("deferUploads", true))
          .put("snapshot", new JSONObject().put("responsiveSnapshotCapture", true))
      );

      Map<String, Object> options = new HashMap<String, Object>();
      options.put("responsiveSnapshotCapture", true);

      boolean result = (boolean) invokePrivate(mockedPercy, "isCaptureResponsiveDOM", new Class[]{Map.class}, options);

      assertFalse(result);
    }

    @Test
    public void buildWidthsQueryParamReturnsJoinedValues() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      String result = (String) invokePrivate(
        mockedPercy,
        "buildWidthsQueryParam",
        new Class[]{List.class},
        Arrays.asList(375, 1280)
      );

      assertEquals("?widths=375,1280", result);
    }

    @Test
    public void buildWidthsQueryParamReturnsEmptyForNullOrEmptyInput() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      String nullResult = (String) invokePrivate(
        mockedPercy,
        "buildWidthsQueryParam",
        new Class[]{List.class},
        new Object[]{null}
      );
      String emptyResult = (String) invokePrivate(
        mockedPercy,
        "buildWidthsQueryParam",
        new Class[]{List.class},
        Collections.emptyList()
      );

      assertEquals("", nullResult);
      assertEquals("", emptyResult);
    }

    @Test
    public void buildRequestConfigUsesProvidedTimeout() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      RequestConfig requestConfig = (RequestConfig) invokePrivate(
        mockedPercy,
        "buildRequestConfig",
        new Class[]{int.class},
        12345
      );

      assertEquals(12345, requestConfig.getSocketTimeout());
      assertEquals(12345, requestConfig.getConnectTimeout());
    }

    @Test
    public void fetchWidthsConfigResponseReturnsHttp200Response() throws Exception {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/percy/widths-config", new HttpHandler() {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
          byte[] body = "{\"widths\":[{\"width\":375}]}".getBytes("UTF-8");
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        }
      });
      server.start();

      String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
      try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));

        HttpResponse response = (HttpResponse) invokePrivate(
          mockedPercy,
          "fetchWidthsConfigResponse",
          new Class[]{CloseableHttpClient.class, String.class},
          httpClient,
          "?widths=375"
        );

        assertEquals(HttpURLConnection.HTTP_OK, response.getStatusLine().getStatusCode());
      } finally {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
        server.stop(0);
      }
    }

    @Test
    public void parseWidthsConfigResponseParsesWidthAndHeightValues() throws Exception {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/percy/widths-config", new HttpHandler() {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
          byte[] body = "{\"widths\":[{\"width\":375},{\"width\":1280,\"height\":900}]}".getBytes("UTF-8");
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        }
      });
      server.start();

      String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
      try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));

        HttpResponse response = (HttpResponse) invokePrivate(
          mockedPercy,
          "fetchWidthsConfigResponse",
          new Class[]{CloseableHttpClient.class, String.class},
          httpClient,
          ""
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parsed = (List<Map<String, Object>>) invokePrivate(
          mockedPercy,
          "parseWidthsConfigResponse",
          new Class[]{HttpResponse.class},
          response
        );

        assertEquals(2, parsed.size());
        assertEquals(375, parsed.get(0).get("width"));
        assertEquals(1280, parsed.get(1).get("width"));
        assertEquals(900, parsed.get(1).get("height"));
      } finally {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
        server.stop(0);
      }
    }

    @Test
    public void resolveConfiguredMinHeightUsesOptionsAndCliFallback() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      Map<String, Object> optionsWithValue = new HashMap<String, Object>();
      optionsWithValue.put("minHeight", "1200");
      Integer fromOptions = (Integer) invokePrivate(
        mockedPercy,
        "resolveConfiguredMinHeight",
        new Class[]{Map.class},
        optionsWithValue
      );
      assertEquals(1200, fromOptions);

      setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject().put("minHeight", 900)));
      Map<String, Object> optionsWithoutValue = new HashMap<String, Object>();
      Integer fromCliConfig = (Integer) invokePrivate(
        mockedPercy,
        "resolveConfiguredMinHeight",
        new Class[]{Map.class},
        optionsWithoutValue
      );
      assertEquals(900, fromCliConfig);
    }

    @Test
    public void resolveConfiguredMinHeightReturnsNullForInvalidValue() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      Map<String, Object> options = new HashMap<String, Object>();
      options.put("minHeight", "invalid");

      Integer result = (Integer) invokePrivate(
        mockedPercy,
        "resolveConfiguredMinHeight",
        new Class[]{Map.class},
        options
      );

      assertNull(result);
    }

    @Test
    public void resolveResponsiveTargetHeightRespectsFeatureFlagAndMinHeight() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      boolean originalFlag = getStaticBooleanField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT");
      try {
        setStaticField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", false);
        int disabledResult = (int) invokePrivate(
          mockedPercy,
          "resolveResponsiveTargetHeight",
          new Class[]{Map.class, int.class},
          new HashMap<String, Object>(),
          800
        );
        assertEquals(800, disabledResult);

        setStaticField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", true);
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("minHeight", 1200);
        int enabledResult = (int) invokePrivate(
          mockedPercy,
          "resolveResponsiveTargetHeight",
          new Class[]{Map.class, int.class},
          options,
          800
        );
        assertEquals(1200, enabledResult);
      } finally {
        setStaticField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", originalFlag);
      }
    }

    @Test
    public void getResponsiveWidthsParsesQueryAndResponse() throws Exception {
      AtomicReference<String> queryRef = new AtomicReference<String>(null);
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/percy/widths-config", new HttpHandler() {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
          queryRef.set(exchange.getRequestURI().getQuery());
          byte[] body = "{\"widths\":[{\"width\":375},{\"width\":1280,\"height\":900}]}".getBytes("UTF-8");
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        }
      });
      server.start();

      String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
      try {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widths = (List<Map<String, Object>>) invokePrivate(
          mockedPercy,
          "getResponsiveWidths",
          new Class[]{List.class},
          Arrays.asList(375, 1280)
        );

        assertEquals("widths=375,1280", queryRef.get());
        assertEquals(2, widths.size());
        assertEquals(375, widths.get(0).get("width"));
        assertEquals(1280, widths.get(1).get("width"));
        assertEquals(900, widths.get(1).get("height"));
      } finally {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
        server.stop(0);
      }
    }

    @Test
    public void capturesCrossOriginIframeDataInSerializedDom() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      setField(mockedPercy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

      WebElement iframe = mock(WebElement.class);
      when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
      when(iframe.getAttribute("data-percy-element-id")).thenReturn("frame-123");

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

      when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenAnswer(invocation -> {
        String script = invocation.getArgument(0);
        if (script.startsWith("return PercyDOM.serialize(")) {
          if (script.contains("\"enableJavaScript\":true")) {
            return iframeSnapshot;
          }
          return mainSnapshot;
        }
        return null;
      });

      @SuppressWarnings("unchecked")
      Map<String, Object> serialized = (Map<String, Object>) invokePrivate(
        mockedPercy,
        "getSerializedDOM",
        new Class[]{JavascriptExecutor.class, Set.class, Map.class},
        mockedDriver,
        new HashSet<Cookie>(),
        new HashMap<String, Object>()
      );

      assertTrue(serialized.containsKey("cookies"));
      assertTrue(serialized.containsKey("corsIframes"));

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> corsIframes = (List<Map<String, Object>>) serialized.get("corsIframes");
      assertEquals(1, corsIframes.size());

      Map<String, Object> frameData = corsIframes.get(0);
      assertEquals("https://cdn.other.com/frame", frameData.get("frameUrl"));

      @SuppressWarnings("unchecked")
      Map<String, Object> iframeData = (Map<String, Object>) frameData.get("iframeData");
      assertEquals("frame-123", iframeData.get("percyElementId"));
      assertEquals("iframe", ((Map<?, ?>) frameData.get("iframeSnapshot")).get("dom"));
    }

    @Test
    public void getResponsiveWidthsThrowsForNon200Response() throws Exception {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/percy/widths-config", new HttpHandler() {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
          byte[] body = "{}".getBytes("UTF-8");
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        }
      });
      server.start();

      String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
      try {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));

        InvocationTargetException exception = assertThrows(
          InvocationTargetException.class,
          () -> invokePrivate(mockedPercy, "getResponsiveWidths", new Class[]{List.class}, Arrays.asList(375, 1280))
        );
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("Failed to fetch widths-config (HTTP 500)"));
      } finally {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
        server.stop(0);
      }
    }

    @Test
    public void getResponsiveWidthsThrowsWhenWidthsKeyMissing() throws Exception {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext("/percy/widths-config", new HttpHandler() {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
          byte[] body = "{}".getBytes("UTF-8");
          exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        }
      });
      server.start();

      String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
      try {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
        RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
        Percy mockedPercy = spy(new Percy(mockedDriver));

        InvocationTargetException exception = assertThrows(
          InvocationTargetException.class,
          () -> invokePrivate(mockedPercy, "getResponsiveWidths", new Class[]{List.class}, Arrays.asList(375, 1280))
        );
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("Missing \"widths\" in widths-config response"));
      } finally {
        setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
        server.stop(0);
      }
    }

    @Test
    public void responsiveSnapshotCaptureIsFalseWhenEligibleWidthsMissing() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      setField(mockedPercy, "eligibleWidths", null);
      setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject().put("responsiveSnapshotCapture", true)));

      Map<String, Object> options = new HashMap<String, Object>();
      options.put("responsiveSnapshotCapture", true);

      boolean result = (boolean) invokePrivate(mockedPercy, "isCaptureResponsiveDOM", new Class[]{Map.class}, options);
      assertFalse(result);
    }

    @Test
    public void skipsUnsupportedIframeSrcInSerializedDom() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      setField(mockedPercy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

      WebElement iframe = mock(WebElement.class);
      when(iframe.getAttribute("src")).thenReturn("about:blank");

      when(mockedDriver.getCurrentUrl()).thenReturn("https://app.example.com/page");
      when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

      Map<String, Object> mainSnapshot = new HashMap<String, Object>();
      mainSnapshot.put("dom", "main");

      when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenReturn(mainSnapshot);

      @SuppressWarnings("unchecked")
      Map<String, Object> serialized = (Map<String, Object>) invokePrivate(
        mockedPercy,
        "getSerializedDOM",
        new Class[]{JavascriptExecutor.class, Set.class, Map.class},
        mockedDriver,
        new HashSet<Cookie>(),
        new HashMap<String, Object>()
      );

      assertFalse(serialized.containsKey("corsIframes"));
    }

    @Test
    public void skipsSameOriginIframeInSerializedDom() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      setField(mockedPercy, "domJs", "window.PercyDOM = window.PercyDOM || {};");

      WebElement iframe = mock(WebElement.class);
      when(iframe.getAttribute("src")).thenReturn("https://app.example.com/frame");

      when(mockedDriver.getCurrentUrl()).thenReturn("https://app.example.com/page");
      when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.singletonList(iframe));

      Map<String, Object> mainSnapshot = new HashMap<String, Object>();
      mainSnapshot.put("dom", "main");

      when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenReturn(mainSnapshot);

      @SuppressWarnings("unchecked")
      Map<String, Object> serialized = (Map<String, Object>) invokePrivate(
        mockedPercy,
        "getSerializedDOM",
        new Class[]{JavascriptExecutor.class, Set.class, Map.class},
        mockedDriver,
        new HashSet<Cookie>(),
        new HashMap<String, Object>()
      );

      assertFalse(serialized.containsKey("corsIframes"));
    }

    @Test
    public void processFrameReturnsNullWhenPercyElementIdMissing() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = spy(new Percy(mockedDriver));

      WebElement iframe = mock(WebElement.class);
      when(iframe.getAttribute("src")).thenReturn("https://cdn.other.com/frame");
      when(iframe.getAttribute("data-percy-element-id")).thenReturn(null);

      Object result = invokePrivate(mockedPercy, "processFrame", new Class[]{WebElement.class, Map.class}, iframe, new HashMap<String, Object>());
      assertNull(result);
      verify(mockedDriver, never()).switchTo();
    }

    @Test
    public void takeScreenshotWithCamelCaseAliasOptions() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
      when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));

      Percy mockedPercy = spy(new Percy(mockedDriver));
      setField(mockedPercy, "isPercyEnabled", true);
      mockedPercy.sessionType = "automate";

      when(mockedDriver.getSessionId()).thenReturn(new SessionId("123"));
      when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
      DesiredCapabilities capabilities = new DesiredCapabilities();
      capabilities.setCapability("browserName", "Chrome");
      when(mockedDriver.getCapabilities()).thenReturn(capabilities);

      RemoteWebElement mockedIgnoreElement = mock(RemoteWebElement.class);
      RemoteWebElement mockedConsiderElement = mock(RemoteWebElement.class);
      when(mockedIgnoreElement.getId()).thenReturn("ignore-123");
      when(mockedConsiderElement.getId()).thenReturn("consider-456");

      Map<String, Object> options = new HashMap<String, Object>();
      options.put("ignoreRegionSeleniumElements", Arrays.asList(mockedIgnoreElement));
      options.put("considerRegionSeleniumElements", Arrays.asList(mockedConsiderElement));

      mockedPercy.screenshot("Test", options);

      ArgumentCaptor<JSONObject> requestBodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
      verify(mockedPercy).request(eq("/percy/automateScreenshot"), requestBodyCaptor.capture(), eq("Test"));

      JSONObject requestBody = requestBodyCaptor.getValue();
      JSONObject capturedOptions = requestBody.getJSONObject("options");
      JSONArray ignoreElements = capturedOptions.getJSONArray("ignore_region_elements");
      JSONArray considerElements = capturedOptions.getJSONArray("consider_region_elements");

      assertEquals("ignore-123", ignoreElements.getString(0));
      assertEquals("consider-456", considerElements.getString(0));
      assertFalse(capturedOptions.has("ignoreRegionSeleniumElements"));
      assertFalse(capturedOptions.has("considerRegionSeleniumElements"));
    }

    @Test
    public void createRegionWithIntelliignoreIncludesConfiguration() {
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("algorithm", "intelliignore");
      params.put("diffSensitivity", 0.3);
      params.put("carouselsEnabled", true);

      Map<String, Object> region = percy.createRegion(params);

      assertEquals("intelliignore", region.get("algorithm"));
      @SuppressWarnings("unchecked")
      Map<String, Object> configuration = (Map<String, Object>) region.get("configuration");
      assertNotNull(configuration);
      assertEquals(0.3, configuration.get("diffSensitivity"));
      assertTrue((Boolean) configuration.get("carouselsEnabled"));
    }

    @Test
    public void createRegionWithIgnoreAlgorithmOmitsConfiguration() {
      Map<String, Object> params = new HashMap<String, Object>();
      params.put("algorithm", "ignore");
      params.put("diffSensitivity", 0.3);

      Map<String, Object> region = percy.createRegion(params);

      assertEquals("ignore", region.get("algorithm"));
      assertFalse(region.containsKey("configuration"));
    }

    @Test
    public void createRegionTest() {
        // Setup the parameters for the region
        Map<String, Object> params = new HashMap<>();
        params.put("boundingBox", "100,100,200,200");
        params.put("elementXpath", "//div[@id='test']");
        params.put("elementCSS", ".test-class");
        params.put("padding", 10);
        params.put("algorithm", "standard");
        params.put("diffSensitivity", 0.5);
        params.put("imageIgnoreThreshold", 0.2);
        params.put("carouselsEnabled", true);
        params.put("bannersEnabled", false);
        params.put("adsEnabled", true);
        params.put("diffIgnoreThreshold", 0.1);

        // Call the method to create the region
        Map<String, Object> region = percy.createRegion(params);

        // Validate the returned region
        assertNotNull(region);

        // Check if elementSelector was added correctly
        Map<String, Object> elementSelector = (Map<String, Object>) region.get("elementSelector");
        assertNotNull(elementSelector);
        assertEquals("100,100,200,200", elementSelector.get("boundingBox"));
        assertEquals("//div[@id='test']", elementSelector.get("elementXpath"));
        assertEquals(".test-class", elementSelector.get("elementCSS"));

        // Validate algorithm and configuration
        assertEquals("standard", region.get("algorithm"));

        Map<String, Object> configuration = (Map<String, Object>) region.get("configuration");
        assertNotNull(configuration);
        assertEquals(0.5, configuration.get("diffSensitivity"));
        assertEquals(0.2, configuration.get("imageIgnoreThreshold"));
        assertTrue((Boolean) configuration.get("carouselsEnabled"));
        assertFalse((Boolean) configuration.get("bannersEnabled"));
        assertTrue((Boolean) configuration.get("adsEnabled"));

        // Validate assertion
        Map<String, Object> assertion = (Map<String, Object>) region.get("assertion");
        assertNotNull(assertion);
        assertEquals(0.1, assertion.get("diffIgnoreThreshold"));
    }

    @Test
    public void captureResponsiveDomResizesToCorrectWidthAndHeight() throws Exception {
        // Serve two widths: one with an explicit height and one without.
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/widths-config", exchange -> {
            byte[] body = "{\"widths\":[{\"width\":375,\"height\":812},{\"width\":1280}]}".getBytes("UTF-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());

            RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
            Percy mockedPercy = spy(new Percy(mockedDriver));
            setField(mockedPercy, "domJs", "/* percy dom */");
            setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

            // Track actual setSize calls so window.resizeCount can echo the right value.
            AtomicInteger dimensionChangeCount = new AtomicInteger(0);
            WebDriver.Options driverOptions = mock(WebDriver.Options.class);
            WebDriver.Window driverWindow = mock(WebDriver.Window.class);
            when(mockedDriver.manage()).thenReturn(driverOptions);
            when(driverOptions.window()).thenReturn(driverWindow);
            when(driverWindow.getSize()).thenReturn(new Dimension(1024, 768));
            doAnswer(inv -> { dimensionChangeCount.incrementAndGet(); return null; })
                .when(driverWindow).setSize(any(Dimension.class));

            when(mockedDriver.getCurrentUrl()).thenReturn("https://example.com");
            when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.emptyList());
            when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.equals("return window.resizeCount")) {
                    return (long) dimensionChangeCount.get();
                }
                if (script.startsWith("return PercyDOM.serialize(")) {
                    Map<String, Object> snap = new HashMap<>();
                    snap.put("dom", "test");
                    return snap;
                }
                return null;
            });

            Map<String, Object> options = new HashMap<>();
            options.put("widths", Arrays.asList(375, 1280));
            List<Map<String, Object>> snapshots =
                mockedPercy.captureResponsiveDom(mockedDriver, new HashSet<>(), options);

            ArgumentCaptor<Dimension> sizeCaptor = ArgumentCaptor.forClass(Dimension.class);
            // 3 calls expected: resize to 375x812, resize to 1280x768, restore 1024x768.
            verify(driverWindow, times(3)).setSize(sizeCaptor.capture());
            List<Dimension> sizes = sizeCaptor.getAllValues();

            // First width uses the explicit height returned by the server.
            assertEquals(375,  sizes.get(0).getWidth());
            assertEquals(812,  sizes.get(0).getHeight());

            // Second width has no explicit height: falls back to currentHeight (768).
            assertEquals(1280, sizes.get(1).getWidth());
            assertEquals(768,  sizes.get(1).getHeight());

            // Final call restores the original window size.
            assertEquals(1024, sizes.get(2).getWidth());
            assertEquals(768,  sizes.get(2).getHeight());

            // Each snapshot must carry the width it was captured at.
            assertEquals(2,   snapshots.size());
            assertEquals(375, snapshots.get(0).get("width"));
            assertEquals(1280, snapshots.get(1).get("width"));
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    @Test
    public void captureResponsiveDomSkipsResizeWhenDimensionsUnchanged() throws Exception {
        // Return the exact same width as the initial window — no per-width resize should occur.
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/widths-config", exchange -> {
            byte[] body = "{\"widths\":[{\"width\":1024}]}".getBytes("UTF-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());

            RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
            Percy mockedPercy = spy(new Percy(mockedDriver));
            setField(mockedPercy, "domJs", "/* percy dom */");
            setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

            AtomicInteger dimensionChangeCount = new AtomicInteger(0);
            WebDriver.Options driverOptions = mock(WebDriver.Options.class);
            WebDriver.Window driverWindow = mock(WebDriver.Window.class);
            when(mockedDriver.manage()).thenReturn(driverOptions);
            when(driverOptions.window()).thenReturn(driverWindow);
            when(driverWindow.getSize()).thenReturn(new Dimension(1024, 768));
            doAnswer(inv -> { dimensionChangeCount.incrementAndGet(); return null; })
                .when(driverWindow).setSize(any(Dimension.class));

            when(mockedDriver.getCurrentUrl()).thenReturn("https://example.com");
            when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.emptyList());
            when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.equals("return window.resizeCount")) {
                    return (long) dimensionChangeCount.get();
                }
                if (script.startsWith("return PercyDOM.serialize(")) {
                    Map<String, Object> snap = new HashMap<>();
                    snap.put("dom", "test");
                    return snap;
                }
                return null;
            });

            Map<String, Object> options = new HashMap<>();
            options.put("widths", Arrays.asList(1024));
            mockedPercy.captureResponsiveDom(mockedDriver, new HashSet<>(), options);

            // Only the final restore call should fire; no per-width resize.
            ArgumentCaptor<Dimension> sizeCaptor = ArgumentCaptor.forClass(Dimension.class);
            verify(driverWindow, times(1)).setSize(sizeCaptor.capture());
            Dimension restoreSize = sizeCaptor.getValue();
            assertEquals(1024, restoreSize.getWidth());
            assertEquals(768,  restoreSize.getHeight());
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            server.stop(0);
        }
    }

    @Test
    public void captureResponsiveDomRefreshesDriverForEachWidthWhenReloadFlagSet() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/percy/widths-config", exchange -> {
            byte[] body = "{\"widths\":[{\"width\":375},{\"width\":1280}]}".getBytes("UTF-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        String originalAddress = getStaticStringField(Percy.class, "PERCY_SERVER_ADDRESS");
        boolean originalReloadFlag = getStaticBooleanField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE");
        try {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", "http://localhost:" + server.getAddress().getPort());
            setStaticField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE", true);

            RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
            Percy mockedPercy = spy(new Percy(mockedDriver));
            setField(mockedPercy, "domJs", "/* percy dom */");
            setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

            WebDriver.Options driverOptions = mock(WebDriver.Options.class);
            WebDriver.Window driverWindow = mock(WebDriver.Window.class);
            WebDriver.Navigation navigation = mock(WebDriver.Navigation.class);
            when(mockedDriver.manage()).thenReturn(driverOptions);
            when(driverOptions.window()).thenReturn(driverWindow);
            when(driverWindow.getSize()).thenReturn(new Dimension(1024, 768));
            when(mockedDriver.navigate()).thenReturn(navigation);
            when(mockedDriver.getCurrentUrl()).thenReturn("https://example.com");
            when(mockedDriver.findElements(By.tagName("iframe"))).thenReturn(Collections.emptyList());

            // After each reload resizeCount resets to 0, so the next changeWindowDimensionAndWait
            // call uses resizeCount=1. Return 1L so the wait resolves without timing out.
            when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenAnswer(invocation -> {
                String script = invocation.getArgument(0);
                if (script.equals("return window.resizeCount")) {
                    return 1L;
                }
                if (script.startsWith("return PercyDOM.serialize(")) {
                    Map<String, Object> snap = new HashMap<>();
                    snap.put("dom", "test");
                    return snap;
                }
                return null;
            });

            Map<String, Object> options = new HashMap<>();
            options.put("widths", Arrays.asList(375, 1280));
            mockedPercy.captureResponsiveDom(mockedDriver, new HashSet<>(), options);

            // driver.navigate().refresh() must be called once per captured width.
            verify(navigation, times(2)).refresh();
        } finally {
            setStaticField(Percy.class, "PERCY_SERVER_ADDRESS", originalAddress);
            setStaticField(Percy.class, "PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE", originalReloadFlag);
            server.stop(0);
        }
    }

    // --- Readiness gate (PER-7348) -----------------------------------------

    @Test
    public void readinessRunsBeforeSerializeAndAttachesDiagnostics() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = new Percy(mockedDriver);
      setField(mockedPercy, "isPercyEnabled", true);
      setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

      Map<String, Object> diagnostics = new HashMap<>();
      diagnostics.put("ok", true);
      diagnostics.put("timed_out", false);
      // executeAsyncScript (readiness)
      when(((JavascriptExecutor) mockedDriver).executeAsyncScript(any(String.class))).thenReturn(diagnostics);
      // executeScript (serialize + any other sync scripts)
      Map<String, Object> domSnap = new HashMap<>();
      domSnap.put("html", "<html></html>");
      when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenReturn(domSnap);

      Map<String, Object> result = mockedPercy.getSerializedDOM(
          (JavascriptExecutor) mockedDriver, new HashSet<>(), new HashMap<>());

      // Readiness script was sent via executeAsyncScript
      ArgumentCaptor<String> scriptCap = ArgumentCaptor.forClass(String.class);
      verify((JavascriptExecutor) mockedDriver, atLeastOnce()).executeAsyncScript(scriptCap.capture());
      assertTrue(scriptCap.getValue().contains("waitForReady"),
          "readiness script should mention waitForReady");
      // Diagnostics propagated to the snapshot
      assertEquals(diagnostics, result.get("readiness_diagnostics"));
    }

    @Test
    public void readinessSkippedWhenPresetDisabled() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = new Percy(mockedDriver);
      setField(mockedPercy, "isPercyEnabled", true);

      Map<String, Object> domSnap = new HashMap<>();
      domSnap.put("html", "<html></html>");
      when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenReturn(domSnap);

      Map<String, Object> disabled = new HashMap<>();
      disabled.put("preset", "disabled");
      Map<String, Object> options = new HashMap<>();
      options.put("readiness", disabled);

      Map<String, Object> result = mockedPercy.getSerializedDOM(
          (JavascriptExecutor) mockedDriver, new HashSet<>(), options);

      // executeAsyncScript must NOT have been called
      verify((JavascriptExecutor) mockedDriver, never()).executeAsyncScript(any(String.class));
      // serialize still ran; no diagnostics attached
      assertNull(result.get("readiness_diagnostics"));
    }

    @Test
    public void snapshotSurvivesReadinessThrow() throws Exception {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      Percy mockedPercy = new Percy(mockedDriver);
      setField(mockedPercy, "isPercyEnabled", true);
      setField(mockedPercy, "cliConfig", new JSONObject().put("snapshot", new JSONObject()));

      when(((JavascriptExecutor) mockedDriver).executeAsyncScript(any(String.class)))
          .thenThrow(new RuntimeException("readiness boom"));
      Map<String, Object> domSnap = new HashMap<>();
      domSnap.put("html", "<html></html>");
      when(((JavascriptExecutor) mockedDriver).executeScript(any(String.class))).thenReturn(domSnap);

      Map<String, Object> result = mockedPercy.getSerializedDOM(
          (JavascriptExecutor) mockedDriver, new HashSet<>(), new HashMap<>());

      // Serialize still ran; no diagnostics attached
      assertNull(result.get("readiness_diagnostics"));
      assertEquals("<html></html>", result.get("html"));
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
