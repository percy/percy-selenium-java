package io.percy.selenium;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

/**
 * Package-private class to compute Environment information.
 */
class Environment {
  private final static String DEFAULT_ARTIFACTID = "percy-java-selenium";
  private final static String UNKNOWN_VERSION = "1.0.0";
  private WebDriver driver;

  Environment(WebDriver driver) {
    this.driver = driver;
  }

  public String getClientInfo() {
    String artifactId = DEFAULT_ARTIFACTID;
    String version = UNKNOWN_VERSION;

    return String.format("%s/%s", artifactId, version);
  }

  public String getEnvironmentInfo() {
    // If this is a wrapped driver, get the actual driver that this one wraps.
    WebDriver innerDriver = this.driver instanceof WrapsDriver ?
      ((WrapsDriver) this.driver).getWrappedDriver()
      : this.driver;

    String[] splitDriverName = innerDriver.getClass().getName().split("\\.");
    String driverName = splitDriverName[splitDriverName.length-1];

    // We don't know this type of driver. Report its classname as environment info.
    return String.format("selenium-java; %s", driverName);
  }
}
