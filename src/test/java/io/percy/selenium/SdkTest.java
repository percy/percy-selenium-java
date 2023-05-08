package io.percy.selenium;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import io.github.bonigarcia.wdm.WebDriverManager;

import org.openqa.selenium.remote.*;
import static org.mockito.Mockito.*;
import java.net.URL;
  public class SdkTest {
  private static final String TEST_URL = "http://localhost:8000";
  private static WebDriver driver;
  private static Percy percy;

  @BeforeAll
  public static void testSetup() throws IOException {
    // Disable browser logs from being logged to stdout
    System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE,"/dev/null");

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
  public void takeScreenshot() {
    RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
    HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
    try {
      when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));
    } catch (Exception e) {
    }
    percy = new Percy(mockedDriver);
    when(mockedDriver.getSessionId()).thenReturn(new SessionId("123"));
    when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
    DesiredCapabilities capabilities = new DesiredCapabilities();
    capabilities.setCapability("browserName", "Chrome");
    when(mockedDriver.getCapabilities()).thenReturn(capabilities);
    percy.screenshot("Test");
  }

    @Test
    public void takeScreenshotWithOptions() {
      RemoteWebDriver mockedDriver = mock(RemoteWebDriver.class);
      HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
      try {
        when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));
      } catch (Exception e) {
      }
      percy = new Percy(mockedDriver);
      when(mockedDriver.getSessionId()).thenReturn(new SessionId("123"));
      when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
      DesiredCapabilities capabilities = new DesiredCapabilities();
      capabilities.setCapability("browserName", "Chrome");
      when(mockedDriver.getCapabilities()).thenReturn(capabilities);
      Map<String, Object> options = new HashMap<String, Object>();
      options.put("scope", "div");
      percy.screenshot("Test", options);
    }
}
