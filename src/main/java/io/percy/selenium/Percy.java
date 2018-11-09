package io.percy.selenium;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

/**
 * Percy client for visual testing.
 */
public class Percy {

    private static final Logger LOGGER = Logger.getLogger(Percy.class.getName());

    // We'll expect this file to exist at the root of our classpath, as a resource.
    private static final String AGENTJS_FILE = "percy-agent.js";

    // Selenium WebDriver we'll use for accessing the web pages to snapshot.
    private WebDriver driver;

    // The JavaScript contained in percy-agent.js
    private String percyAgentJs;

    // A stringified JavaScript dict containing client and environment information.
    private String environmentDictString;

    /**
     * @param driver The Selenium WebDriver object that will hold the browser
     *               session to snapshot.
     */
    public Percy(WebDriver driver) {
        this.driver = driver;
        this.percyAgentJs = loadPercyAgentJs();
        this.environmentDictString = new Environment(driver).getInfoDict();
    }

    /**
     * Attempts to load percy-agent.js from the resources in this Jar. The file
     * comes from the node module @percy/agent, which is installed and packaged into
     * this Jar as part of the Maven build.
     *
     * Bundling the percy-agent.js file with this library does run the minor risk of
     * a future incompatibility between the bundled percy-agent.js in this library,
     * and the version of @percy/agent being run by the library's client.
     *
     * An alternative to consider would be to try to load percy-agent.js at runtime
     * from a running percy agent server on the standard port.
     */
    @Nullable
    private String loadPercyAgentJs() {
        try {
            return new String(getClass().getClassLoader().getResourceAsStream(AGENTJS_FILE).readAllBytes());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Something went wrong trying to load {}. Snapshotting will not work.",
                    AGENTJS_FILE);
            return null;
        }
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name The human-readable name of the snapshot. Should be unique.
     */
    public void snapshot(String name) {
        snapshot(name, null, null);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name   The human-readable name of the snapshot. Should be unique.
     * @param widths The browser widths at which you want to take the snapshot. In
     *               pixels.
     */
    public void snapshot(String name, @Nullable List<Integer> widths) {
        snapshot(name, widths, null);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     */
    public void snapshot(String name, @Nullable List<Integer> widths, @Nullable Integer minHeight) {
        if (percyAgentJs == null) {
            // This would happen if we couldn't load percy-agent.js in the constructor.
            LOGGER.log(Level.WARNING, "percy-agent.js is not available. Snapshotting is disabled.");
            return;
        }
        try {
            JavascriptExecutor jse = (JavascriptExecutor) driver;
            jse.executeScript(percyAgentJs);
            jse.executeScript(buildSnapshotJS(name, widths, minHeight));
        } catch (WebDriverException e) {
            // For some reason, the execution in the browser failed.
            LOGGER.log(Level.WARNING, "Something went wrong attempting to take a snapshot: {}", e.getMessage());
        }
    }

    /**
     * @return A String containing the JavaScript needed to instantiate a PercyAgent
     *         and take a snapshot.
     */
    private String buildSnapshotJS(String name, List<Integer> widths, Integer minHeight) {
        StringBuilder jsBuilder = new StringBuilder();
        jsBuilder.append(String.format("const percyAgentClient = new PercyAgent(%s)\n", this.environmentDictString));
        List<String> snapshotParams = new ArrayList<>(Arrays.asList(String.format("'%s'", name)));
        String optionalParams = maybeBuildOptionalParamsString(widths, minHeight);
        if (optionalParams != null) {
            snapshotParams.add(optionalParams);
        }
        jsBuilder.append(String.format("percyAgentClient.snapshot(%s)", String.join(",", snapshotParams)));
        return jsBuilder.toString();
    }

    /**
     * Converts our optional snapshot parameters into a JavaScript dictionary.
     *
     * If we ever add more than these optional parameters, we'll probably want to
     * add a SnapshotConfig class that can be used to pass in the configuration and
     * that also knows how to convert itself into a JavaScript dict.
     *
     * @return null if there were no optional parameters, the resulting String
     *         otherwise
     */
    @Nullable
    private String maybeBuildOptionalParamsString(@Nullable List<Integer> widths, @Nullable Integer minHeight) {
        List<String> stringifiedParams = new ArrayList<String>();
        if (widths != null) {
            // Take advantage of the fact that Java stringifies List<Integer> as we need.
            stringifiedParams.add(String.format("widths: %s", widths));
        }
        if (minHeight != null) {
            stringifiedParams.add(String.format("minHeight: %s", minHeight));
        }

        if (stringifiedParams.isEmpty()) {
            // No optional params.
            return null;
        }

        return String.format("{ %s }", String.join(",", stringifiedParams));
    }
}
