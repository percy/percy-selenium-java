package io.percy.selenium;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

/**
 * Package-private class to compute Environment information.
 */
class Environment {
  private WebDriver driver;
  private final static String SDK_VERSION = "1.0.0";
  private final static String SDK_NAME = "percy-java-selenium";

  Environment(WebDriver driver) {
    this.driver = driver;
  }

  public String getClientInfo() {
    return SDK_NAME + "/" + SDK_VERSION;
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
