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

import java.net.URI;
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

import javax.swing.text.html.CSS;
import javax.xml.xpath.XPath;

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

    private static String RESPONSIVE_CAPTURE_SLEEP_TIME = System.getenv().getOrDefault("RESPONSIVE_CAPTURE_SLEEP_TIME", "");

    private static boolean PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE = Boolean.parseBoolean(System.getenv().getOrDefault("PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE", "false").toLowerCase());
    
    private static boolean PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT = Boolean.parseBoolean(System.getenv().getOrDefault("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT", "false").toLowerCase());
    private static final int WIDTHS_CONFIG_TIMEOUT_MS = 30000;
    // for logging
    private static String LABEL = "[\u001b[35m" + (PERCY_DEBUG ? "percy:java" : "percy") + "\u001b[39m]";

    // Type of session automate/web
    protected String sessionType = null;
    protected JSONObject eligibleWidths;
    private JSONObject cliConfig;

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
     * Creates a region configuration based on the provided parameters.
     *
     * @param params A map containing the region configuration options. Expected keys:
     *               <ul>
     *                  <li>boundingBox - The bounding box of the region, or null.</li>
     *                  <li>elementXpath - The XPath of the element, or null.</li>
     *                  <li>elementCSS - The CSS selector of the element, or null.</li>
     *                  <li>padding - The padding around the region, or null.</li>
     *                  <li>algorithm - The algorithm to be used (default: 'ignore').</li>
     *                  <li>diffSensitivity - The sensitivity for diffing, or null.</li>
     *                  <li>imageIgnoreThreshold - The image ignore threshold, or null.</li>
     *                  <li>carouselsEnabled - Flag for enabling carousels, or null.</li>
     *                  <li>bannersEnabled - Flag for enabling banners, or null.</li>
     *                  <li>adsEnabled - Flag for enabling ads, or null.</li>
     *                  <li>diffIgnoreThreshold - The diff ignore threshold, or null.</li>
     *               </ul>
     * @return A map representing the region configuration.
     */

     public Map<String, Object> createRegion(Map<String, Object> params) {
        Map<String, Object> elementSelector = new HashMap<>();
        if (params.containsKey("boundingBox")) {
            elementSelector.put("boundingBox", params.get("boundingBox"));
        }
        if (params.containsKey("elementXpath")) {
            elementSelector.put("elementXpath", params.get("elementXpath"));
        }
        if (params.containsKey("elementCSS")) {
            elementSelector.put("elementCSS", params.get("elementCSS"));
        }

        Map<String, Object> region = new HashMap<>();
        region.put("algorithm", params.getOrDefault("algorithm", "ignore"));
        region.put("elementSelector", elementSelector);

        if (params.containsKey("padding")) {
            region.put("padding", params.get("padding"));
        }

        Map<String, Object> configuration = new HashMap<>();
        String algorithm = (String) params.getOrDefault("algorithm", "ignore");
        if (algorithm.equals("standard") || algorithm.equals("intelliignore")) {
            List<String> keys = Arrays.asList(
                "diffSensitivity",
                "imageIgnoreThreshold",
                "carouselsEnabled",
                "bannersEnabled",
                "adsEnabled"
            );
        
            for (String key : keys) {
                if (params.containsKey(key)) {
                    configuration.put(key, params.get(key));
                }
            }
        }

        if (!configuration.isEmpty()) {
            region.put("configuration", configuration);
        }

        Map<String, Object> assertion = new HashMap<>();
        if (params.containsKey("diffIgnoreThreshold")) {
            assertion.put("diffIgnoreThreshold", params.get("diffIgnoreThreshold"));
        }

        if (!assertion.isEmpty()) {
            region.put("assertion", assertion);
        }

        return region;
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name The human-readable name of the snapshot. Should be unique.
     *
     */
    public JSONObject snapshot(String name) {
        return snapshot(name, null, null, false, null, null, null, null);
    }

    /**
     * Take a snapshot and upload it to Percy.
     *
     * @param name   The human-readable name of the snapshot. Should be unique.
     * @param widths The browser widths at which you want to take the snapshot. In
     *               pixels.
     */
    public JSONObject snapshot(String name, List<Integer> widths) {
        return snapshot(name, widths, null, false, null, null, null, null);
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
        return snapshot(name, widths, minHeight, false, null, null, null, null);
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
        return snapshot(name, widths, minHeight, enableJavaScript, null, null, null, null);
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
        return snapshot(name, widths, minHeight, enableJavaScript, percyCSS, null, null, null);
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
        return snapshot(name, widths, minHeight, enableJavaScript, percyCSS, scope, null, null);
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

    private List<Map<String, Object>> getResponsiveWidths(List<Integer> widths) {
        String queryParam = buildWidthsQueryParam(widths);
        RequestConfig requestConfig = buildRequestConfig(WIDTHS_CONFIG_TIMEOUT_MS);

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpResponse response = fetchWidthsConfigResponse(httpClient, queryParam);
            return parseWidthsConfigResponse(response);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            log("Update Percy CLI to the latest version to use responsiveSnapshotCapture");
            log("Failed to fetch widths-config: " + ex.getMessage(), "debug");
            throw new RuntimeException(
                    "Failed to fetch widths-config: " + ex.getMessage(), ex);
        }
    }

    // Builds the optional `?widths=` query string from SDK-provided widths.
    private String buildWidthsQueryParam(List<Integer> widths) {
        if (widths == null || widths.isEmpty()) {
            return "";
        }
        String joined = widths.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "?widths=" + joined;
    }

    // Creates HTTP request timeout configuration for the widths-config endpoint.
    private RequestConfig buildRequestConfig(int timeoutMs) {
        return RequestConfig.custom()
                .setSocketTimeout(timeoutMs)
                .setConnectTimeout(timeoutMs)
                .build();
    }

    // Calls Percy CLI widths-config endpoint and validates that the HTTP status is successful.
    private HttpResponse fetchWidthsConfigResponse(CloseableHttpClient httpClient, String queryParam) throws Exception {
        HttpGet httpget = new HttpGet(PERCY_SERVER_ADDRESS + "/percy/widths-config" + queryParam);
        HttpResponse response = httpClient.execute(httpget);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != 200) {
            EntityUtils.consume(response.getEntity());
            log("Update Percy CLI to the latest version to use responsiveSnapshotCapture");
            throw new RuntimeException("Failed to fetch widths-config (HTTP " + statusCode + ")");
        }

        return response;
    }

    // Parses widths-config JSON and converts the payload to SDK width/height maps.
    private List<Map<String, Object>> parseWidthsConfigResponse(HttpResponse response) throws Exception {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        JSONObject json = new JSONObject(responseString);

        if (!json.has("widths") || json.isNull("widths")) {
            log("Update Percy CLI to the latest version to use responsiveSnapshotCapture");
            throw new RuntimeException("Missing \"widths\" in widths-config response");
        }

        JSONArray widthsArray = json.getJSONArray("widths");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < widthsArray.length(); i++) {
            JSONObject entry = widthsArray.getJSONObject(i);
            Map<String, Object> item = new HashMap<>();
            item.put("width", entry.getInt("width"));
            if (entry.has("height") && !entry.isNull("height")) {
                item.put("height", entry.getInt("height"));
            }
            result.add(item);
        }
        return result;
    }
    
    private boolean isCaptureResponsiveDOM(Map<String, Object> options) {
        if (cliConfig.has("percy") && !cliConfig.isNull("percy")) {
            JSONObject percyProperty = cliConfig.getJSONObject("percy");

            if (percyProperty.has("deferUploads") && !percyProperty.isNull("deferUploads") && percyProperty.getBoolean("deferUploads")) {
                return false;
            }
        }

        boolean responsiveSnapshotCaptureCLI = false;
        if (eligibleWidths == null) { return false; }
        if (cliConfig.getJSONObject("snapshot").has("responsiveSnapshotCapture")) {
            responsiveSnapshotCaptureCLI = cliConfig.getJSONObject("snapshot").getBoolean("responsiveSnapshotCapture");
        }
        Object responsiveSnapshotCaptureSDK = options.get("responsiveSnapshotCapture");

        return (responsiveSnapshotCaptureSDK != null && (boolean) responsiveSnapshotCaptureSDK) || responsiveSnapshotCaptureCLI;
    }

    public JSONObject snapshot(String name, Map<String, Object> options) {
        if (!isPercyEnabled) { return null; }
        if ("automate".equals(sessionType)) { throw new RuntimeException("Invalid function call - snapshot(). Please use screenshot() function while using Percy with Automate. For more information on usage of PercyScreenshot, refer https://www.browserstack.com/docs/percy/integrate/functional-and-visual"); }

        Object domSnapshot = null;

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
                domSnapshot = getSerializedDOM(jse, cookies, options);
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
            cliConfig = responseObject.optJSONObject("config");

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
      Object domSnapshot,
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

    private boolean isUnsupportedIframeSrc(String src) {
        return src == null || src.isEmpty() ||
               src.equals("about:blank") ||
               src.startsWith("javascript:") ||
               src.startsWith("data:") ||
               src.startsWith("vbscript:");
    }

    private String getOrigin(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            if (scheme == null || authority == null) return "";
            return scheme + "://" + authority;
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, Object> processFrame(WebElement frameElement, Map<String, Object> options) {
        // Read attributes while still in parent context — these calls will
        // fail if made after switchTo().frame().
        String frameUrl = frameElement.getAttribute("src");
        if (frameUrl == null) frameUrl = "unknown-src";
        final String finalFrameUrl = frameUrl;
        String percyElementId = frameElement.getAttribute("data-percy-element-id");
        log("processFrame: data-percy-element-id=\"" + percyElementId + "\" for src=\"" + finalFrameUrl + "\"", "debug");
        if (percyElementId == null || percyElementId.isEmpty()) {
            log("Skipping frame " + finalFrameUrl + ": no matching percyElementId found", "debug");
            return null;
        }

        Map<String, Object> iframeSnapshot = null;
        try {
            driver.switchTo().frame(frameElement);
            JavascriptExecutor jse = (JavascriptExecutor) driver;
            // Inject Percy DOM into the cross-origin frame context
            jse.executeScript(domJs);
            // Serialize inside the frame; enableJavaScript=true is required for CORS iframes
            Map<String, Object> iframeOptions = new HashMap<>(options);
            iframeOptions.put("enableJavaScript", true);
            JSONObject optionsJson = new JSONObject(iframeOptions);
            iframeSnapshot = (Map<String, Object>) jse.executeScript(
                "return PercyDOM.serialize(" + optionsJson.toString() + ")"
            );
        } catch (Exception e) {
            log("Failed to process cross-origin frame " + finalFrameUrl + ": " + e.getMessage(), "error");
            throw new RuntimeException("Failed to process cross-origin frame " + finalFrameUrl, e);
        } finally {
            try {
                driver.switchTo().defaultContent();
            } catch (Exception err) {
                throw new RuntimeException(
                    "Fatal: could not exit iframe context after processing \"" + finalFrameUrl + "\". Driver may be unstable.",err
                );
            }
        }

        Map<String, Object> iframeData = new HashMap<>();
        iframeData.put("percyElementId", percyElementId);

        Map<String, Object> result = new HashMap<>();
        result.put("iframeData", iframeData);
        result.put("iframeSnapshot", iframeSnapshot);
        result.put("frameUrl", finalFrameUrl);
        return result;
    }

    private Map<String, Object> getSerializedDOM(JavascriptExecutor jse, Set<Cookie> cookies, Map<String, Object> options) {
        Map<String, Object> domSnapshot = (Map<String, Object>) jse.executeScript(buildSnapshotJS(options));
        Map<String, Object> mutableSnapshot = new HashMap<>(domSnapshot);
        mutableSnapshot.put("cookies", cookies);
        try {
            String pageOrigin = getOrigin(driver.getCurrentUrl());
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            if (!iframes.isEmpty() && !domJs.trim().isEmpty()) {
                List<Map<String, Object>> processedFrames = new ArrayList<>();
                for (WebElement frame : iframes) {
                    String frameSrc = frame.getAttribute("src");
                    if (isUnsupportedIframeSrc(frameSrc)) {
                        continue;
                    }
                    String frameOrigin;
                    try {
                        URI base = new URI(driver.getCurrentUrl());
                        URI resolved = base.resolve(frameSrc);
                        frameOrigin = getOrigin(resolved.toString());
                    } catch (Exception e) {
                        log("Skipping iframe \"" + frameSrc + "\": " + e.getMessage(), "debug");
                        continue;
                    }
                    if (frameOrigin.equals(pageOrigin)) {
                        continue;
                    }
                    try {
                        Map<String, Object> result = processFrame(frame, options);
                        if (result != null) {
                            processedFrames.add(result);
                        }
                    } catch (Exception e) {
                        log("Skipping frame \"" + frameSrc + "\" due to error: " + e.getMessage(), "debug");
                        String message = e.getMessage();
                         if (message != null && message.contains("Fatal")) {
                             if (e instanceof RuntimeException) {
                                 throw (RuntimeException) e;
                             } else {
                                 throw new RuntimeException("Fatal error while processing iframe \"" + frameSrc + "\"", e);
                             }
                         }
                    }
                }
                if (!processedFrames.isEmpty()) {
                    mutableSnapshot.put("corsIframes", processedFrames);
                }
            }
        } catch (Exception e) {
            log("Failed to process cross-origin iframes: " + e.getMessage(), "debug");
            String message = e.getMessage();
             if (message != null && message.contains("Fatal")) {
                 // Propagate fatal iframe processing errors to avoid returning a corrupted DOM snapshot
                 if (e instanceof RuntimeException) {
                     throw (RuntimeException) e;
                 } else {
                     throw new RuntimeException("Fatal error while processing cross-origin iframes", e);
                 }
             }
        }
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
            wait.until((ExpectedCondition<Boolean>) d -> {
                Object resizeCountObj = ((JavascriptExecutor) d).executeScript("return window.resizeCount");
                if (resizeCountObj == null) {
                    return false;
                }
                return (resizeCountObj instanceof Number) && ((Number) resizeCountObj).longValue() == resizeCount;
            });
        } catch (WebDriverException e) {
            log("Timed out waiting for window resize event for width " + width, "debug");
        }
    }

    private List<Integer> extractResponsiveWidths(Map<String, Object> options) {
        if (options == null) {
            return null;
        }
        Object widthsOption = options.get("widths");
        if (!(widthsOption instanceof List<?>)) {
            return null;
        }
        List<?> rawWidths = (List<?>) widthsOption;
        List<Integer> coercedWidths = new ArrayList<>();
        for (Object value : rawWidths) {
            if (value instanceof Number) {
                coercedWidths.add(((Number) value).intValue());
            } else if (value instanceof String) {
                try {
                    coercedWidths.add(Integer.parseInt((String) value));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return coercedWidths.isEmpty() ? null : coercedWidths;
    }

    // Resolves final viewport height for responsive capture using minHeight config when enabled.
    private int resolveResponsiveTargetHeight(Map<String, Object> options, int currentHeight) {
        if (!PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT) {
            log("PERCY_RESPONSIVE_CAPTURE_MIN_HEIGHT is disabled, using current window height: " + currentHeight, "debug");
            return currentHeight;
        }

        Integer minHeight = resolveConfiguredMinHeight(options);
        if (minHeight == null) {
            log("minHeight not found in options or cliConfig, using current window height: " + currentHeight, "debug");
            return currentHeight;
        }

        return minHeight;
    }

    // Reads minHeight from snapshot options first, then falls back to CLI snapshot config.
    private Integer resolveConfiguredMinHeight(Map<String, Object> options) {
        Object minHeightObj = options.get("minHeight");
        if (minHeightObj == null && cliConfig != null && cliConfig.has("snapshot")) {
            JSONObject snapshotConfig = cliConfig.getJSONObject("snapshot");
            if (snapshotConfig.has("minHeight")) {
                minHeightObj = snapshotConfig.getInt("minHeight");
            }
        }

        if (minHeightObj == null) {
            return null;
        }

        try {
            return Integer.parseInt(minHeightObj.toString());
        } catch (NumberFormatException e) {
            log("Invalid minHeight value " + minHeightObj + "; expected integer, using current window height instead.", "debug");
            return null;
        }
    }



    public List<Map<String, Object>> captureResponsiveDom(WebDriver driver, Set<Cookie> cookies, Map<String, Object> options) {
        List<Integer> responsiveWidths = extractResponsiveWidths(options);
        List<Map<String, Object>> widths = getResponsiveWidths(responsiveWidths);
        List<Map<String, Object>> domSnapshots = new ArrayList<>();
        Dimension windowSize = driver.manage().window().getSize();
        int currentWidth = windowSize.getWidth();
        int currentHeight = windowSize.getHeight();
        int lastWindowWidth = currentWidth;
        int lastWindowHeight = currentHeight;
        int resizeCount = 0;
        JavascriptExecutor jse = (JavascriptExecutor) driver;
        jse.executeScript("PercyDOM.waitForResize()");
        int targetHeight = resolveResponsiveTargetHeight(options, currentHeight);
        for (Map<String, Object> widthMap : widths) {
            Object widthObj = widthMap.get("width");
            if (!(widthObj instanceof Number)) {
                continue;
            }
            int width = ((Number) widthObj).intValue();
            Object heightObj = widthMap.get("height");
            System.out.println("Processing responsive snapshot for width " + width + " with target height " + targetHeight+ "height obj: " + heightObj);
            int heightForWidth = (heightObj instanceof Number)? ((Number) heightObj).intValue(): targetHeight;
            System.out.println("final height" + heightForWidth);
            if (lastWindowWidth != width || lastWindowHeight != heightForWidth) {       
                resizeCount++;
                System.out.println("Resizing window to width " + width + " and height " + heightForWidth);
                changeWindowDimensionAndWait(driver, width, heightForWidth, resizeCount);
                lastWindowWidth = width;
                lastWindowHeight = heightForWidth;
            }
            if ("true".equals(PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE)) {
                driver.navigate().refresh();
                jse.executeScript(fetchPercyDOM());
                jse.executeScript("PercyDOM.waitForResize()");
                resizeCount = 0; 
            }
            try {
                if (RESPONSIVE_CAPTURE_SLEEP_TIME != null && !RESPONSIVE_CAPTURE_SLEEP_TIME.isEmpty()) {
                    int sleepTime = Integer.parseInt(RESPONSIVE_CAPTURE_SLEEP_TIME);
                    Thread.sleep(sleepTime * 1000L);
                }
            } catch (InterruptedException | NumberFormatException ignored) {
            }
            Map<String, Object> domSnapshot = getSerializedDOM(jse, cookies, options);
            domSnapshot.put("width", width);
            domSnapshots.add(domSnapshot);
        }
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
