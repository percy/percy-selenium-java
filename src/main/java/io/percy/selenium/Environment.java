package io.percy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Package-private class to compute Environment information.
 */
class Environment {

  private static final String PROPS_PATH = "META-INF/maven/io.percy/percy-java-selenium/pom.properties";
  private final static String DEFAULT_ARTIFACTID = "percy-java-selenium";
  private final static String UNKNOWN_VERSION = "unknown";
  private WebDriver driver;

  Environment(WebDriver driver) {
    this.driver = driver;
  }

  String getInfoDict() {
    StringBuilder info = new StringBuilder();
    info.append("{ ");
    info.append(String.format("clientInfo: '%s'", getClientInfo()));
    info.append(",");
    info.append(String.format("environmentInfo: '%s'", getEnvironmentInfo()));
    info.append(" }");
    return info.toString();
  }

  private String getClientInfo() {
    String artifactId = DEFAULT_ARTIFACTID;
    String version = UNKNOWN_VERSION;

    // Try to read the artifactId and version from the Jar's properties file.
    InputStream propsStream = getClass().getClassLoader().getResourceAsStream(PROPS_PATH);
    if (propsStream != null) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(propsStream));
      try {
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

  private String getEnvironmentInfo() {
    Capabilities cap = ((RemoteWebDriver) this.driver).getCapabilities();
    String os = cap.getPlatform().toString();
    String browserName = cap.getBrowserName().toLowerCase();
    String version = cap.getVersion().toString();
    return String.format("selenium-java; %s; %s/%s", os, browserName, version);
  }
}
