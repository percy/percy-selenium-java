package io.percy.selenium;

import org.apache.commons.exec.util.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.openqa.selenium.*;
import org.openqa.selenium.remote.*;

import java.lang.reflect.Field;

/**
 * Percy client for visual testing.
 */
public class Percy {
    // Selenium WebDriver we'll use for accessing the web pages to snapshot.
    private WebDriver driver;

    // The JavaScript contained in dom.js
    private String domJs = "";

    // Maybe get the CLI server address
    private String PERCY_SERVER_ADDRESS = System.getenv().getOrDefault("PERCY_SERVER_ADDRESS", "http://localhost:5338");

    // Determine if we're debug logging
    private static boolean PERCY_DEBUG = System.getenv().getOrDefault("PERCY_LOGLEVEL", "info").equals("debug");

    // for logging
    private static String LABEL = "[\u001b[35m" + (PERCY_DEBUG ? "percy:java" : "percy") + "\u001b[39m]";

    // Type of session automate/web
    protected String sessionType = null;

    // Is the Percy server running or not
    private boolean isPercyEnabled = healthcheck();

    // Environment information like Java, browser, & SDK versions
    private Environment env;

    // Fetch following properties from capabilities
    private final List<String> capsNeeded = new ArrayList<>(Arrays.asList("browserName", "platform", "platformName", "version", "osVersion", "proxy", "deviceName"));
    private final String ignoreElementKey = "ignore_region_selenium_elements";
    private final String ignoreElementAltKey = "ignoreRegionSeleniumElements";
    private final String considerElementKey = "consider_region_selenium_elements";
    private final String considerElementAltKey = "considerRegionSeleniumElements";
    /**
     * @param driver The Selenium WebDriver object that will hold the browser
     *               session to snapshot.
     */
    public Percy(WebDriver driver) {
        this.driver = driver;
        this.env = new Environment(driver);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name The human-readable name of the snapshot. Should be unique.
     *
     */
    public JSONObject snapshot(String name) {
        return snapshot(name, null, null, false, null, null, false);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name   The human-readable name of the snapshot. Should be unique.
     * @param widths The browser widths at which you want to take the snapshot. In
     *               pixels.
     */
    public JSONObject snapshot(String name, List<Integer> widths) {
        return snapshot(name, widths, null, false, null, null, false);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name   The human-readable name of the snapshot. Should be unique.
     * @param widths The browser widths at which you want to take the snapshot. In
     *               pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     */
    public JSONObject snapshot(String name, List<Integer> widths, Integer minHeight) {
        return snapshot(name, widths, minHeight, false, null, null, false);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name   The human-readable name of the snapshot. Should be unique.
     * @param widths The browser widths at which you want to take the snapshot. In
     *               pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     * @param enableJavaScript Enable JavaScript in the Percy rendering environment
     */
    public JSONObject snapshot(String name, List<Integer> widths, Integer minHeight, boolean enableJavaScript) {
        return snapshot(name, widths, minHeight, enableJavaScript, null, null, false);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     * @param enableJavaScript Enable JavaScript in the Percy rendering environment
     * @param percyCSS Percy specific CSS that is only applied in Percy's browsers
     */
    public JSONObject snapshot(String name, @Nullable List<Integer> widths, Integer minHeight, boolean enableJavaScript, String percyCSS) {
        return snapshot(name, widths, minHeight, enableJavaScript, percyCSS, null, false);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the snapshot. Should be unique.
     * @param widths    The browser widths at which you want to take the snapshot.
     *                  In pixels.
     * @param minHeight The minimum height of the resulting snapshot. In pixels.
     * @param enableJavaScript Enable JavaScript in the Percy rendering environment
     * @param percyCSS Percy specific CSS that is only applied in Percy's browsers
     * @param scope    A CSS selector to scope the screenshot to
     */
    public JSONObject snapshot(String name, @Nullable List<Integer> widths, Integer minHeight, boolean enableJavaScript, String percyCSS, String scope) {
        if (!isPercyEnabled) { return null; }

        Map<String, Object> domSnapshot = null;
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);
        options.put("minHeight", minHeight);
        options.put("enableJavaScript", enableJavaScript);
        options.put("percyCSS", percyCSS);
        options.put("scope", scope);

        return snapshot(name, options);
    }

    public JSONObject snapshot(String name, @Nullable List<Integer> widths, Integer minHeight, boolean enableJavaScript, String percyCSS, String scope, boolean sync) {
        if (!isPercyEnabled) { return null; }

        Map<String, Object> domSnapshot = null;
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);
        options.put("minHeight", minHeight);
        options.put("enableJavaScript", enableJavaScript);
        options.put("percyCSS", percyCSS);
        options.put("scope", scope);
        options.put("sync", sync);

        return snapshot(name, options);
    }

    public JSONObject snapshot(String name, Map<String, Object> options) {
        if (!isPercyEnabled) { return null; }
        if ("automate".equals(sessionType)) { throw new RuntimeException("Invalid function call - snapshot(). Please use screenshot() function while using Percy with Automate. For more information on usage of PercyScreenshot, refer https://docs.percy.io/docs/integrate-functional-testing-with-visual-testing"); }

        Map<String, Object> domSnapshot = null;

        try {
            JavascriptExecutor jse = (JavascriptExecutor) driver;
            jse.executeScript(fetchPercyDOM());
            domSnapshot = (Map<String, Object>) jse.executeScript(buildSnapshotJS(options));
        } catch (WebDriverException e) {
            // For some reason, the execution in the browser failed.
            if (PERCY_DEBUG) { log(e.getMessage()); }
        }

        return postSnapshot(domSnapshot, name, driver.getCurrentUrl(), options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the screenshot. Should be unique.
     */
    public JSONObject screenshot(String name) throws UnsupportedOperationException {
        Map<String, Object> options = new HashMap<String, Object>();
        return screenshot(name, options);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name      The human-readable name of the screenshot. Should be unique.
     * @param options   Extra options
     */
    public JSONObject screenshot(String name, Map<String, Object> options) throws UnsupportedOperationException {
        if (!isPercyEnabled) { return null; }
        if ("web".equals(sessionType)) { throw new RuntimeException("Invalid function call - screenshot(). Please use snapshot() function for taking screenshot. screenshot() should be used only while using Percy with Automate. For more information on usage of snapshot(), refer doc for your language https://docs.percy.io/docs/end-to-end-testing"); }

        List<String> driverArray = Arrays.asList(driver.getClass().toString().split("\\$")); // Added to handle testcase (mocked driver)
        Iterator<String> driverIterator = driverArray.iterator();
        String driverClass = driverIterator.next();

        DriverMetadata driverMetadata = new DriverMetadata(driver);
        String sessionId = driverMetadata.getSessionId();
        String remoteWebAddress = driverMetadata.getCommandExecutorUrl();
        ConcurrentHashMap<String, String> capabilities = driverMetadata.getCapabilities();

        if (options.containsKey(ignoreElementAltKey)) {
            options.put(ignoreElementKey, options.get(ignoreElementAltKey));
            options.remove(ignoreElementAltKey);
        }

        if (options.containsKey(considerElementAltKey)) {
            options.put(considerElementKey, options.get(considerElementAltKey));
            options.remove(considerElementAltKey);
        }

        if (options.containsKey(ignoreElementKey)) {
            List<String> ignoreElementIds =  getElementIdFromElement((List<RemoteWebElement>) options.get(ignoreElementKey));
            options.remove(ignoreElementKey);
            options.put("ignore_region_elements", ignoreElementIds);
        }

        if (options.containsKey(considerElementKey)) {
            List<String> considerElementIds = getElementIdFromElement((List<RemoteWebElement>) options.get(considerElementKey));
            options.remove(considerElementKey);
            options.put("consider_region_elements", considerElementIds);
        }

        // Build a JSON object to POST back to the agent node process
        JSONObject json = new JSONObject();
        json.put("sessionId", sessionId);
        json.put("commandExecutorUrl", remoteWebAddress);
        json.put("capabilities", capabilities);
        json.put("snapshotName", name);
        json.put("clientInfo", env.getClientInfo());
        json.put("environmentInfo", env.getEnvironmentInfo());
        json.put("options", options);

        return request("/percy/automateScreenshot", json, name);
    }

    /**
     * Checks to make sure the local Percy server is running. If not, disable Percy.
     */
    private boolean healthcheck() {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            //Creating a HttpGet object
            HttpGet httpget = new HttpGet(PERCY_SERVER_ADDRESS + "/percy/healthcheck");

            //Executing the Get request
            HttpResponse response = httpClient.execute(httpget);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200){
                throw new RuntimeException("Failed with HTTP error code : " + statusCode);
            }

            String version = response.getFirstHeader("x-percy-core-version").getValue();

            if (version == null) {
                log("You may be using @percy/agent" +
                    "which is no longer supported by this SDK." +
                    "Please uninstall @percy/agent and install @percy/cli instead." +
                    "https://docs.percy.io/docs/migrating-to-percy-cli"
                    );

                return false;
            }

            if (!version.split("\\.")[0].equals("1")) {
                log("Unsupported Percy CLI version, " + version);

                return false;
            }
            HttpEntity entity = response.getEntity();
            String responseString = EntityUtils.toString(entity, "UTF-8");
            JSONObject responseObject = new JSONObject(responseString);
            sessionType = (String) responseObject.optString("type", null);

            return true;
        } catch (Exception ex) {
            log("Percy is not running, disabling snapshots");
            // bike shed.. single line?
            if (PERCY_DEBUG) { log(ex.toString()); }

            return false;
        }
    }

    /**
     * Attempts to load dom.js from the local Percy server. Use cached value in `domJs`,
     * if it exists.
     *
     * This JavaScript is critical for capturing snapshots. It serializes and captures
     * the DOM. Without it, snapshots cannot be captured.
     */
    private String fetchPercyDOM() {
        if (!domJs.trim().isEmpty()) { return domJs; }

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpGet httpget = new HttpGet(PERCY_SERVER_ADDRESS + "/percy/dom.js");
            HttpResponse response = httpClient.execute(httpget);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != 200){
                throw new RuntimeException("Failed with HTTP error code: " + statusCode);
            }
            HttpEntity httpEntity = response.getEntity();
            String domString = EntityUtils.toString(httpEntity);
            domJs = domString;

            return domString;
        } catch (Exception ex) {
            isPercyEnabled = false;
            if (PERCY_DEBUG) { log(ex.toString()); }

            return "";
        }
    }

    /**
     * POST the DOM taken from the test browser to the Percy Agent node process.
     *
     * @param domSnapshot Stringified & serialized version of the site/applications DOM
     * @param name        The human-readable name of the snapshot. Should be unique.
     * @param widths      The browser widths at which you want to take the snapshot.
     *                    In pixels.
     * @param minHeight   The minimum height of the resulting snapshot. In pixels.
     * @param enableJavaScript Enable JavaScript in the Percy rendering environment
     * @param percyCSS Percy specific CSS that is only applied in Percy's browsers
     */
    private JSONObject postSnapshot(
      Map<String, Object> domSnapshot,
      String name,
      String url,
      Map<String, Object> options
    ) {
        if (!isPercyEnabled) { return null; }

        // Build a JSON object to POST back to the agent node process
        JSONObject json = new JSONObject(options);
        json.put("url", url);
        json.put("name", name);
        json.put("domSnapshot", domSnapshot);
        json.put("clientInfo", env.getClientInfo());
        json.put("environmentInfo", env.getEnvironmentInfo());

        return request("/percy/snapshot", json, name);
    }

    /**
     * POST data to the Percy Agent node process.
     *
     * @param url         Endpoint to be called.
     * @param name        The human-readable name of the snapshot. Should be unique.
     * @param json        Json object of all properties.
     */
    protected JSONObject request(String url, JSONObject json, String name) {
        StringEntity entity = new StringEntity(json.toString(), ContentType.APPLICATION_JSON);

        int timeout = 600000; // 600 seconds = 600,000 milliseconds

        // Create RequestConfig with timeout
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpPost request = new HttpPost(PERCY_SERVER_ADDRESS + url);
            request.setEntity(entity);
            HttpResponse response = httpClient.execute(request);
            JSONObject jsonResponse = new JSONObject(EntityUtils.toString(response.getEntity()));
            return jsonResponse.getJSONObject("data");
        } catch (Exception ex) {
            if (PERCY_DEBUG) { log(ex.toString()); }
            log("Could not post snapshot " + name);
        }
        return null;
    }

    /**
     * @return A String containing the JavaScript needed to instantiate a PercyAgent
     *         and take a snapshot.
     */
    private String buildSnapshotJS(Map<String, Object> options) {
        StringBuilder jsBuilder = new StringBuilder();
        JSONObject json = new JSONObject(options);
        jsBuilder.append(String.format("return PercyDOM.serialize(%s)\n", json.toString()));

        return jsBuilder.toString();
    }

    private List<String> getElementIdFromElement(List<RemoteWebElement> elements) {
        List<String> ignoredElementsArray = new ArrayList<>();
        for (int index = 0; index < elements.size(); index++) {
                String elementId = elements.get(index).getId();
                ignoredElementsArray.add(elementId);
        }
        return ignoredElementsArray;
    }

    protected static void log(String message) {
        System.out.println(LABEL + " " + message);
    }
}
