package io.percy.selenium;

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class SdkTest {
  private static final String TEST_URL = "http://localhost:8000";
  private static WebDriver driver;
  private static Percy percy;

  @BeforeAll
  public static void openServerAndBrowser() throws IOException {
    TestServer.startServer();
    ChromeOptions options = new ChromeOptions();
    options.addArguments(
      "--headless",
      "--disable-web-security",
      "--allow-running-insecure-content",
      "--ignore-certificate-errors");
    driver = new ChromeDriver(options);
    percy = new Percy(driver);
  }

  @AfterAll
  public static void closeServerAndBrowser() {
    // Close our test browser.
    driver.quit();
    // Shutdown our server and make sure the threadpool also terminates.
    TestServer.shutdown();
  }

  @AfterEach
  public void clearLocalStorage() {
    ((JavascriptExecutor) driver).executeScript("window.localStorage.clear()");
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
  public void snapshotsLiveHTTPSite() {
    driver.get("http://example.com/");
    percy.snapshot("http://example.com/");
  }

  @Test
  public void snapshotsLiveHTTPSSite() {
    driver.get("https://sdk-test.percy.dev");
    percy.snapshot("https://sdk-test.percy.dev");
  }
}
