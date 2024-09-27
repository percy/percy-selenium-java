package io.percy.selenium;

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
import org.json.JSONArray;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.stream.Collectors;

/**
 * Percy client for visual testing.
 */
public class Percy {
    // Selenium WebDriver we'll use for accessing the web pages to snapshot.
    private WebDriver driver;

    // The JavaScript contained in dom.js
    private String domJs = "";

    // Maybe get the CLI server address
    private static String PERCY_SERVER_ADDRESS = System.getenv().getOrDefault("PERCY_SERVER_ADDRESS", "http://localhost:5338");

    // Determine if we're debug logging
    private static boolean PERCY_DEBUG = System.getenv().getOrDefault("PERCY_LOGLEVEL", "info").equals("debug");

    private static String RESONSIVE_CAPTURE_SLEEP_TIME = System.getenv().getOrDefault("RESONSIVE_CAPTURE_SLEEP_TIME", "");

    // for logging
    private static String LABEL = "[\u001b[35m" + (PERCY_DEBUG ? "percy:java" : "percy") + "\u001b[39m]";

    // Type of session automate/web
    protected String sessionType = null;
    protected JSONObject eligibleWidths;
    private JSONObject CLIconfig;

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
        return snapshot(name, null, null, false, null, null, null);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name   The human-readable name of the snapshot. Should be unique.
     * @param widths The browser widths at which you want to take the snapshot. In
     *               pixels.
     */
    public JSONObject snapshot(String name, List<Integer> widths) {
        return snapshot(name, widths, null, false, null, null, null);
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
        return snapshot(name, widths, minHeight, false, null, null, null);
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
        return snapshot(name, widths, minHeight, enableJavaScript, null, null, null);
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
        return snapshot(name, widths, minHeight, enableJavaScript, percyCSS, null, null);
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
        return snapshot(name, widths, minHeight, enableJavaScript, percyCSS, scope);
    }

    public JSONObject snapshot(String name, @Nullable List<Integer> widths, Integer minHeight, boolean enableJavaScript, String percyCSS, String scope, @Nullable Boolean sync) {
        return snapshot(name, widths, minHeight, enableJavaScript, percyCSS, scope, sync, null);
    }

    public JSONObject snapshot(String name, @Nullable List<Integer> widths, Integer minHeight, boolean enableJavaScript, String percyCSS, String scope, @Nullable Boolean sync, Boolean responsiveSnapshotCapture) {
        if (!isPercyEnabled) { return null; }

        Map<String, Object> domSnapshot = null;
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("widths", widths);
        options.put("minHeight", minHeight);
        options.put("enableJavaScript", enableJavaScript);
        options.put("percyCSS", percyCSS);
        options.put("scope", scope);
        options.put("sync", sync);
        options.put("responsiveSnapshotCapture", responsiveSnapshotCapture);

        return snapshot(name, options);
    }

    private boolean isCaptureResponsiveDOM(Map<String, Object> options) {
        if (CLIconfig.has("percy") && !CLIconfig.isNull("percy")) {
            JSONObject percyProperty = CLIconfig.getJSONObject("percy");

            if (percyProperty.has("deferUploads") && !percyProperty.isNull("deferUploads") && percyProperty.getBoolean("deferUploads")) {
                return false;
            }
        }

        boolean responsiveSnapshotCaptureCLI = false;
        if (CLIconfig.getJSONObject("snapshot").has("responsiveSnapshotCapture")) {
            responsiveSnapshotCaptureCLI = CLIconfig.getJSONObject("snapshot").getBoolean("responsiveSnapshotCapture");
        }
        Object responsiveSnapshotCaptureSDK = options.get("responsiveSnapshotCapture");

        return (responsiveSnapshotCaptureSDK != null && (boolean) responsiveSnapshotCaptureSDK) || responsiveSnapshotCaptureCLI;
    }

    public JSONObject snapshot(String name, Map<String, Object> options) {
        if (!isPercyEnabled) { return null; }
        if ("automate".equals(sessionType)) { throw new RuntimeException("Invalid function call - snapshot(). Please use screenshot() function while using Percy with Automate. For more information on usage of PercyScreenshot, refer https://www.browserstack.com/docs/percy/integrate/functional-and-visual"); }

        List<Map<String, Object>> domSnapshot = new ArrayList<>();

        try {
            JavascriptExecutor jse = (JavascriptExecutor) driver;
            jse.executeScript(fetchPercyDOM());
            Set<Cookie> cookies = new HashSet<>();
            try {
                cookies = driver.manage().getCookies();
            } catch(Exception e) {
                log("Cookie collection failed " + e.getMessage(), "debug");
            }
            if (isCaptureResponsiveDOM(options)) {
                domSnapshot = captureResponsiveDom(driver, cookies, options);
            } else {
                domSnapshot.add(getSerializedDOM(jse, cookies, options));
            }
        } catch (WebDriverException e) {
            // For some reason, the execution in the browser failed.
            log(e.getMessage(), "debug");
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
        if (!"automate".equals(sessionType)) { throw new RuntimeException("Invalid function call - screenshot(). Please use snapshot() function for taking screenshot. screenshot() should be used only while using Percy with Automate. For more information on usage of snapshot(), refer doc for your language https://www.browserstack.com/docs/percy/integrate/overview"); }

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
                    "https://www.browserstack.com/docs/percy/migration/migrate-to-cli"
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
            eligibleWidths = responseObject.optJSONObject("widths");
            CLIconfig = responseObject.optJSONObject("config");

            return true;
        } catch (Exception ex) {
            log("Percy is not running, disabling snapshots");
            // bike shed.. single line?
            log(ex.toString(), "debug");

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
            log(ex.toString(), "debug");

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
      List<Map<String, Object>> domSnapshot,
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

            if (jsonResponse.has("data")) {
                return jsonResponse.getJSONObject("data");
            }
        } catch (Exception ex) {
            log(ex.toString(), "debug");
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

    private Map<String, Object> getSerializedDOM(JavascriptExecutor jse, Set<Cookie> cookies, Map<String, Object> options) {
        Map<String, Object> domSnapshot = (Map<String, Object>) jse.executeScript(buildSnapshotJS(options));
        Map<String, Object> mutableSnapshot = new HashMap<>(domSnapshot);
        mutableSnapshot.put("cookies", cookies);

        return mutableSnapshot;
    }

    private List<String> getElementIdFromElement(List<RemoteWebElement> elements) {
        List<String> ignoredElementsArray = new ArrayList<>();
        for (int index = 0; index < elements.size(); index++) {
                String elementId = elements.get(index).getId();
                ignoredElementsArray.add(elementId);
        }
        return ignoredElementsArray;
    }

    // Get widths for multi DOM
    private List<Integer> getWidthsForMultiDom(Map<String, Object> options) {
        List<Integer> widths;
        if (options.containsKey("widths") && options.get("widths") instanceof List<?>) {
            widths = (List<Integer>) options.get("widths");
        } else {
            widths = new ArrayList<>();
        }
        // Create a Set to avoid duplicates
        Set<Integer> allWidths = new HashSet<>();

        JSONArray mobileWidths = eligibleWidths.getJSONArray("mobile");
        for (int i = 0; i < mobileWidths.length(); i++) {
            allWidths.add(mobileWidths.getInt(i));
        }

        // Add input widths if provided
        if (widths.size() != 0) {
            for (int width : widths) {
                allWidths.add(width);
            }
        } else {
            // Add config widths if no input widths are provided
            JSONArray configWidths = eligibleWidths.getJSONArray("config");
            for (int i = 0; i < configWidths.length(); i++) {
                allWidths.add(configWidths.getInt(i));
            }
        }

        // Convert Set back to List
        return allWidths.stream().collect(Collectors.toList());
    }

    // Method to check if ChromeDriver supports CDP by checking the existence of executeCdpCommand
    private static boolean isCdpSupported(ChromeDriver chromeDriver) {
        try {
            chromeDriver.getClass().getMethod("executeCdpCommand", String.class, Map.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    // Change window dimensions and wait for the resize event
    private static void changeWindowDimensionAndWait(WebDriver driver, int width, int height, int resizeCount) {
        try {
            if (driver instanceof ChromeDriver && isCdpSupported((ChromeDriver) driver)) {
                Map<String, Object> commandParams = new HashMap<>();
                commandParams.put("width", width);
                commandParams.put("height", height);
                commandParams.put("deviceScaleFactor", 1);
                commandParams.put("mobile", false);

                ((ChromeDriver) driver).executeCdpCommand("Emulation.setDeviceMetricsOverride", commandParams);
            } else {
                driver.manage().window().setSize(new Dimension(width, height));
            }
        } catch (Exception e) {
            log("Resizing using CDP failed, falling back to driver for width " + width + ": " + e.getMessage(), "debug");
            driver.manage().window().setSize(new Dimension(width, height));
        }

        // Wait for window resize event using WebDriverWait
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(1));
            wait.until((ExpectedCondition<Boolean>) d ->
                    (long) ((JavascriptExecutor) d).executeScript("return window.resizeCount") == resizeCount);
        } catch (WebDriverException e) {
            log("Timed out waiting for window resize event for width " + width, "debug");
        }
    }

    // Capture responsive DOM for different widths
    public List<Map<String, Object>> captureResponsiveDom(WebDriver driver, Set<Cookie> cookies, Map<String, Object> options) {
        List<Integer> widths = getWidthsForMultiDom(options);

        List<Map<String, Object>> domSnapshots = new ArrayList<>();

        Dimension windowSize = driver.manage().window().getSize();
        int currentWidth = windowSize.getWidth();
        int currentHeight = windowSize.getHeight();
        int lastWindowWidth = currentWidth;
        int resizeCount = 0;
        JavascriptExecutor jse = (JavascriptExecutor) driver;

        // Inject JS to count window resize events
        jse.executeScript("PercyDOM.waitForResize()");

        for (int width : widths) {
            if (lastWindowWidth != width) {
                resizeCount++;
                changeWindowDimensionAndWait(driver, width, currentHeight, resizeCount);
                lastWindowWidth = width;
            }

            try {
                int sleepTime = Integer.parseInt(RESONSIVE_CAPTURE_SLEEP_TIME);
                Thread.sleep(sleepTime * 1000); // Sleep if needed
            } catch (InterruptedException | NumberFormatException ignored) {
            }
            Map<String, Object> domSnapshot = getSerializedDOM(jse, cookies, options);
            domSnapshot.put("width", width);
            domSnapshots.add(domSnapshot);
        }

        // Revert to the original window size
        changeWindowDimensionAndWait(driver, currentWidth, currentHeight, resizeCount + 1);

        return domSnapshots;
    }

    protected static void log(String message) {
        log(message, "info");
    }

    protected static void log(String message, String level) {
        message = LABEL + " " + message;
        String logJsonString = "{\"message\": \"" + message + "\", \"level\": \"" + level + "\"}";
        StringEntity entity = new StringEntity(logJsonString, ContentType.APPLICATION_JSON);
        int timeout = 1000; // 1 second

        // Create RequestConfig with timeout
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(timeout)
                .setConnectTimeout(timeout)
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpPost request = new HttpPost(PERCY_SERVER_ADDRESS + "/percy/log");
            request.setEntity(entity);
            httpClient.execute(request);
        } catch (Exception ex) {
            if (PERCY_DEBUG) { System.out.println("Sending log to CLI Failed " + ex.toString()); }
        } finally {
            // Only log if level is not 'debug' or PERCY_DEBUG is true
            if (!"debug".equals(level) || PERCY_DEBUG) {
                System.out.println(message);
            }
        }
    }
}
