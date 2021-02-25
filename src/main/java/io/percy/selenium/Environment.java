package io.percy.selenium;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Package-private class to compute Environment information.
 */
class Environment {

  private static final String PROPS_PATH = "META-INF/maven/io.percy.selenium/percy-java-selenium/pom.properties";
  private final static String DEFAULT_ARTIFACTID = "percy-java-selenium";
  private final static String UNKNOWN_VERSION = "unknown";
  private WebDriver driver;

  Environment(WebDriver driver) {
    this.driver = driver;
  }

  public String getClientInfo() {
    String artifactId = DEFAULT_ARTIFACTID;
    String version = UNKNOWN_VERSION;

    // Try to read the artifactId and version from the Jar's properties file.
    InputStream propsStream = getClass().getClassLoader().getResourceAsStream(PROPS_PATH);
    if (propsStream != null) {
      try (InputStreamReader streamReader = new InputStreamReader(propsStream);
          BufferedReader reader = new BufferedReader(streamReader)) {
        while (reader.ready()) {
          String line = reader.readLine();
          String[] lineParts = line.split("=");
          if (lineParts.length == 2) {
            if (lineParts[0].equals("version")) {
              version = lineParts[1];
            } else if (lineParts[0].equals("artifactId")) {
              artifactId = lineParts[1];
            }
          }
        }
      } catch (IOException e) {
        // Something went wrong trying to read the properties file. Don't log any
        // warnings, since this is not something that users are likely to care
        // about. We'll use our defaults instead.
      }
    }

    return String.format("%s/%s", artifactId, version);
  }

  public String getEnvironmentInfo() {
    // If this is a wrapped driver, get the actual driver that this one wraps.
    WebDriver innerDriver = this.driver instanceof WrapsDriver ?
      ((WrapsDriver) this.driver).getWrappedDriver()
      : this.driver;

    // If this is a driver with Capabilities, use those to report on our environment info.
    if (innerDriver instanceof HasCapabilities) {
      Capabilities cap = ((HasCapabilities) this.driver).getCapabilities();
      String os = cap.getPlatform().toString();
      String browserName = cap.getBrowserName().toLowerCase();
      String version = cap.getVersion().toString();
      return String.format("selenium-java; %s; %s/%s", os, browserName, version);
    }

    // We don't know this type of driver. Report its classname as environment info.
    return String.format("selenium-java; unknownDriver; %s", innerDriver.getClass().getName());
  }
}
