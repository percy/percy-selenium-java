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

    private static String RESPONSIVE_CAPTURE_SLEEP_TIME = System.getenv().getOrDefault(
        "RESPONSIVE_CAPTURE_SLEEP_TIME",
        System.getenv().getOrDefault("RESONSIVE_CAPTURE_SLEEP_TIME", "")
    );

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
        if (cliConfig != null && cliConfig.has("snapshot") && !cliConfig.isNull("snapshot")) {
            JSONObject snapshotCfg = cliConfig.getJSONObject("snapshot");
            if (snapshotCfg.has("responsiveSnapshotCapture") && !snapshotCfg.isNull("responsiveSnapshotCapture")) {
                responsiveSnapshotCaptureCLI = snapshotCfg.getBoolean("responsiveSnapshotCapture");
            }
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
        } catch (Exception e) {
            log("Snapshot failed: " + e.getMessage(), "debug");
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

    static class FatalIframeException extends RuntimeException {
        FatalIframeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // Signals that the driver lost its frame context mid-recursion. Any iframes
    // captured before the failure are attached as `partialCapture` so the top-
    // level caller can still salvage them instead of throwing away progress.
    static class PercyContextLostException extends RuntimeException {
        public List<Map<String, Object>> partialCapture;
        PercyContextLostException(String message, Throwable cause, List<Map<String, Object>> partialCapture) {
            super(message, cause);
            this.partialCapture = partialCapture;
        }
    }

    // Default maximum nesting depth for cross-origin iframe capture. Mirrors the
    // canonical Percy SDK behaviour — depth 1 is a top-level iframe.
    private static final int DEFAULT_MAX_FRAME_DEPTH = 5;
    private static final int MIN_FRAME_DEPTH = 1;
    private static final int MAX_FRAME_DEPTH_CAP = 10;

    private boolean isUnsupportedIframeSrc(String src) {
        return src == null || src.isEmpty() ||
               src.equals("about:blank") ||
               src.startsWith("javascript:") ||
               src.startsWith("data:") ||
               src.startsWith("vbscript:");
    }

    private static String getOrigin(String url) {
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

    // Clamp the configured frame depth to a sane range. Negative or
    // unreasonably large values fall back to the default.
    private static int clampFrameDepth(int depth) {
        if (depth < MIN_FRAME_DEPTH) return DEFAULT_MAX_FRAME_DEPTH;
        if (depth > MAX_FRAME_DEPTH_CAP) return MAX_FRAME_DEPTH_CAP;
        return depth;
    }

    // Coerce an arbitrary user-provided selector list into a sanitized List<String>.
    private static List<String> normalizeIgnoreSelectors(Object input) {
        List<String> result = new ArrayList<>();
        if (input == null) return result;
        if (input instanceof List<?>) {
            for (Object o : (List<?>) input) {
                if (o instanceof String) {
                    String s = ((String) o).trim();
                    if (!s.isEmpty()) result.add(s);
                }
            }
        } else if (input instanceof String) {
            String s = ((String) input).trim();
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    private List<String> resolveIgnoreSelectors(Map<String, Object> options) {
        if (options != null && options.containsKey("ignoreIframeSelectors")) {
            return normalizeIgnoreSelectors(options.get("ignoreIframeSelectors"));
        }
        if (cliConfig != null && cliConfig.has("snapshot") && !cliConfig.isNull("snapshot")) {
            JSONObject snap = cliConfig.getJSONObject("snapshot");
            if (snap.has("ignoreIframeSelectors") && !snap.isNull("ignoreIframeSelectors")) {
                JSONArray arr = snap.optJSONArray("ignoreIframeSelectors");
                if (arr != null) {
                    List<Object> out = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) out.add(arr.opt(i));
                    return normalizeIgnoreSelectors(out);
                }
            }
        }
        return Collections.emptyList();
    }

    // True if the iframe element matches any of the user-provided ignore selectors.
    // Selector matching is performed in-browser via Element.matches so any CSS
    // selector the browser supports is valid; invalid selectors are tolerated.
    private boolean iframeMatchesIgnoreSelector(WebElement iframe, List<String> selectors) {
        if (selectors == null || selectors.isEmpty()) return false;
        try {
            JavascriptExecutor jse = (JavascriptExecutor) driver;
            JSONArray sel = new JSONArray(selectors);
            String script = "var el = arguments[0]; var selectors = " + sel.toString() + ";"
                + "for (var i = 0; i < selectors.length; i++) {"
                + "  try { if (el.matches(selectors[i])) return true; } catch (e) {}"
                + "} return false;";
            Object res = jse.executeScript(script, iframe);
            return res instanceof Boolean && (Boolean) res;
        } catch (Exception e) {
            return false;
        }
    }

    private int resolveMaxFrameDepth(Map<String, Object> options) {
        Object override = options == null ? null : options.get("maxIframeDepth");
        if (override instanceof Number) {
            return clampFrameDepth(((Number) override).intValue());
        }
        if (override instanceof String) {
            try { return clampFrameDepth(Integer.parseInt((String) override)); } catch (NumberFormatException ignore) {}
        }
        if (cliConfig != null && cliConfig.has("snapshot") && !cliConfig.isNull("snapshot")) {
            JSONObject snap = cliConfig.getJSONObject("snapshot");
            if (snap.has("maxIframeDepth") && !snap.isNull("maxIframeDepth")) {
                return clampFrameDepth(snap.optInt("maxIframeDepth", DEFAULT_MAX_FRAME_DEPTH));
            }
        }
        return DEFAULT_MAX_FRAME_DEPTH;
    }

    // Probe a child iframe element for `data-percy-ignore`. Selenium's
    // getAttribute returns "" for boolean attributes with no value; treat
    // any non-null result as a positive hit.
    private boolean childHasDataPercyIgnore(WebElement iframe) {
        try {
            return iframe.getAttribute("data-percy-ignore") != null;
        } catch (Exception e) {
            return false;
        }
    }

    // Read document.URL inside the current frame context. Used for the post-switch
    // sanity check to confirm the iframe actually resolved to a navigable URL.
    // Only treat a String result as the document URL — otherwise return null so
    // callers fall back to the parent-side `src` value.
    private String readCurrentFrameUrl() {
        try {
            Object u = ((JavascriptExecutor) driver).executeScript("return document.URL");
            return (u instanceof String) ? (String) u : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Serialize the current frame context's DOM using PercyDOM.serialize.
    // enableJavaScript=true is forced so PercyDOM.serialize doesn't recurse into
    // nested iframes itself — we drive that recursion explicitly.
    private Map<String, Object> serializeCurrentFrame(Map<String, Object> options) {
        JavascriptExecutor jse = (JavascriptExecutor) driver;
        Map<String, Object> iframeOptions = new HashMap<>(options == null ? Collections.emptyMap() : options);
        iframeOptions.put("enableJavaScript", true);
        JSONObject optionsJson = new JSONObject(iframeOptions);
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) jse.executeScript(
            "return PercyDOM.serialize(" + optionsJson.toString() + ")"
        );
        return snapshot;
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
            // Post-switch URL re-check: about:blank / data: / javascript: targets
            // can slip through the parent-side `src` check (e.g. when the iframe
            // failed to load, or has been navigated by script after attach).
            String postSwitchUrl = readCurrentFrameUrl();
            if (postSwitchUrl != null && isUnsupportedIframeSrc(postSwitchUrl)) {
                log("Skipping iframe after switch: unsupported document.URL \"" + postSwitchUrl + "\"", "debug");
                return null;
            }
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
                throw new FatalIframeException(
                    "Could not exit iframe context after processing \"" + finalFrameUrl + "\". Driver may be unstable.", err
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

    // Recursively process a cross-origin iframe tree. From the current driver
    // frame context, switch into `frameElement`, capture its DOM, enumerate
    // further cross-origin iframes nested inside it, and recurse. Steps back
    // to the parent frame on exit so the caller can continue iterating siblings.
    //
    // Bounded by `maxFrameDepth` to stop runaway recursion when pages link to
    // each other. `ancestorUrls` tracks parent frame URLs — if the current
    // frame's URL is already in the chain we treat it as a cycle and stop
    // descending. Compares nested-frame origin against the IMMEDIATE PARENT
    // origin, not the top page origin.
    private List<Map<String, Object>> processFrameTree(
        WebElement frameElement,
        int depth,
        Set<String> ancestorUrls,
        Map<String, Object> ctx
    ) {
        @SuppressWarnings("unchecked")
        Map<String, Object> options = (Map<String, Object>) ctx.get("options");
        int maxFrameDepth = (int) ctx.get("maxFrameDepth");
        @SuppressWarnings("unchecked")
        List<String> ignoreSelectors = (List<String>) ctx.get("ignoreSelectors");

        String frameSrc = frameElement.getAttribute("src");
        String percyElementId = frameElement.getAttribute("data-percy-element-id");

        List<Map<String, Object>> collected = new ArrayList<>();
        if (depth > maxFrameDepth) {
            log("Reached max iframe nesting depth (" + maxFrameDepth + "); stopping at " + frameSrc, "debug");
            return collected;
        }
        if (ancestorUrls != null && frameSrc != null && ancestorUrls.contains(frameSrc)) {
            log("Skipping cyclic iframe (" + frameSrc + " appears in ancestor chain)", "debug");
            return collected;
        }
        if (percyElementId == null || percyElementId.isEmpty()) {
            log("Skipping cross-origin iframe without data-percy-element-id: " + frameSrc, "debug");
            return collected;
        }

        boolean switchedIn = false;
        try {
            log("Processing cross-origin iframe (depth " + depth + "): " + frameSrc, "debug");
            driver.switchTo().frame(frameElement);
            switchedIn = true;

            JavascriptExecutor jse = (JavascriptExecutor) driver;
            jse.executeScript(domJs);

            // Post-switch URL re-check: this is the only place we know what the
            // browser actually navigated to. If it's an unsupported scheme,
            // bail before serializing.
            String postSwitchUrl = readCurrentFrameUrl();
            if (postSwitchUrl != null && isUnsupportedIframeSrc(postSwitchUrl)) {
                log("Skipping iframe after switch: unsupported document.URL \"" + postSwitchUrl + "\"", "debug");
                return collected;
            }

            Map<String, Object> iframeSnapshot = serializeCurrentFrame(options);
            String reportedUrl = (postSwitchUrl != null) ? postSwitchUrl : frameSrc;

            Map<String, Object> iframeData = new HashMap<>();
            iframeData.put("percyElementId", percyElementId);
            Map<String, Object> entry = new HashMap<>();
            entry.put("iframeData", iframeData);
            entry.put("iframeSnapshot", iframeSnapshot);
            entry.put("frameUrl", reportedUrl);
            collected.add(entry);

            // Descend into further cross-origin iframes nested inside this one.
            // Same-origin descendants are already inlined as srcdoc by PercyDOM.
            if (depth < maxFrameDepth) {
                String currentOrigin = getOrigin(reportedUrl);
                List<WebElement> childIframes;
                try {
                    childIframes = driver.findElements(By.tagName("iframe"));
                } catch (Exception e) {
                    log("Could not enumerate nested iframes in " + reportedUrl + ": " + e.getMessage(), "debug");
                    childIframes = Collections.emptyList();
                }
                Set<String> nextAncestors = new HashSet<>(ancestorUrls == null ? Collections.emptySet() : ancestorUrls);
                if (frameSrc != null) nextAncestors.add(frameSrc);
                if (reportedUrl != null) nextAncestors.add(reportedUrl);

                for (WebElement child : childIframes) {
                    String childSrc;
                    try { childSrc = child.getAttribute("src"); } catch (Exception e) { continue; }
                    if (isUnsupportedIframeSrc(childSrc)) continue;
                    if (childHasDataPercyIgnore(child)) {
                        log("Skipping iframe marked with data-percy-ignore: " + childSrc, "debug");
                        continue;
                    }
                    if (iframeMatchesIgnoreSelector(child, ignoreSelectors)) {
                        log("Skipping iframe matching ignoreIframeSelectors: " + childSrc, "debug");
                        continue;
                    }
                    String childOrigin;
                    try {
                        URI base = new URI(reportedUrl);
                        URI resolved = base.resolve(childSrc);
                        childOrigin = getOrigin(resolved.toString());
                    } catch (Exception e) {
                        continue;
                    }
                    // Compare to the IMMEDIATE PARENT origin, not the page origin.
                    if (childOrigin.equals(currentOrigin)) continue;

                    try {
                        List<Map<String, Object>> nested = processFrameTree(child, depth + 1, nextAncestors, ctx);
                        if (!nested.isEmpty()) collected.addAll(nested);
                    } catch (PercyContextLostException ctxLost) {
                        // Merge any partial capture from the inner level into ours before
                        // propagating, so the top-level caller can recover everything
                        // that was successfully serialized prior to the failure.
                        if (ctxLost.partialCapture != null && !ctxLost.partialCapture.isEmpty()) {
                            collected.addAll(ctxLost.partialCapture);
                        }
                        ctxLost.partialCapture = collected;
                        throw ctxLost;
                    } catch (FatalIframeException fatal) {
                        throw fatal;
                    } catch (Exception e) {
                        log("Skipping nested iframe \"" + childSrc + "\" due to error: " + e.getMessage(), "debug");
                    }
                }
            }
            return collected;
        } catch (PercyContextLostException ctxLost) {
            throw ctxLost;
        } catch (Exception e) {
            log("Failed to process cross-origin iframe " + frameSrc + ": " + e.getMessage(), "warn");
            return collected;
        } finally {
            if (switchedIn) {
                // Step up exactly one level so an outer recursion continues from
                // its own context. If parentFrame fails we have no reliable way
                // to land in the correct parent — fall back to default content
                // and signal that the rest of the sibling enumeration would be
                // unreliable. Partial capture is propagated via the exception.
                try {
                    driver.switchTo().parentFrame();
                } catch (Exception parentErr) {
                    log("Failed to switch back to parent frame: " + parentErr.getMessage(), "warn");
                    try { driver.switchTo().defaultContent(); } catch (Exception ignore) {}
                    if (depth > 1) {
                        throw new PercyContextLostException(
                            "Lost parent frame context: " + parentErr.getMessage(),
                            parentErr,
                            new ArrayList<>(collected)
                        );
                    }
                }
            }
        }
    }

    private Map<String, Object> getSerializedDOM(JavascriptExecutor jse, Set<Cookie> cookies, Map<String, Object> options) {
        Object raw = jse.executeScript(buildSnapshotJS(options));
        if (!(raw instanceof Map)) {
            throw new RuntimeException("PercyDOM.serialize returned null or non-object; "
                + "the @percy/dom script likely failed to load. Aborting snapshot.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> domSnapshot = (Map<String, Object>) raw;
        Map<String, Object> mutableSnapshot = new HashMap<>(domSnapshot);
        mutableSnapshot.put("cookies", cookies);

        // Expose closed shadow roots via CDP (Chromium only) so PercyDOM.serialize
        // can pierce them through the WeakMap it reads. Non-fatal — skip on errors.
        try { exposeClosedShadowRoots(driver); } catch (Exception ignore) {}

        try {
            String pageUrl = driver.getCurrentUrl();
            String pageOrigin = getOrigin(pageUrl);
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            if (!iframes.isEmpty() && !domJs.trim().isEmpty()) {
                int maxFrameDepth = resolveMaxFrameDepth(options);
                List<String> ignoreSelectors = resolveIgnoreSelectors(options);

                Map<String, Object> ctx = new HashMap<>();
                ctx.put("options", options);
                ctx.put("maxFrameDepth", maxFrameDepth);
                ctx.put("ignoreSelectors", ignoreSelectors);

                List<Map<String, Object>> processedFrames = new ArrayList<>();
                for (WebElement frame : iframes) {
                    String frameSrc;
                    try { frameSrc = frame.getAttribute("src"); } catch (Exception e) { continue; }
                    if (isUnsupportedIframeSrc(frameSrc)) continue;
                    if (childHasDataPercyIgnore(frame)) {
                        log("Skipping iframe marked with data-percy-ignore: " + frameSrc, "debug");
                        continue;
                    }
                    if (iframeMatchesIgnoreSelector(frame, ignoreSelectors)) {
                        log("Skipping iframe matching ignoreIframeSelectors: " + frameSrc, "debug");
                        continue;
                    }
                    String frameOrigin;
                    try {
                        URI base = new URI(pageUrl);
                        URI resolved = base.resolve(frameSrc);
                        frameOrigin = getOrigin(resolved.toString());
                    } catch (Exception e) {
                        log("Skipping iframe \"" + frameSrc + "\": " + e.getMessage(), "debug");
                        continue;
                    }
                    if (frameOrigin.equals(pageOrigin)) continue;

                    Set<String> ancestors = new HashSet<>();
                    if (pageUrl != null) ancestors.add(pageUrl);
                    try {
                        List<Map<String, Object>> nested = processFrameTree(frame, 1, ancestors, ctx);
                        if (!nested.isEmpty()) processedFrames.addAll(nested);
                    } catch (PercyContextLostException ctxLost) {
                        log("Aborting further nested CORS capture due to lost frame context", "warn");
                        if (ctxLost.partialCapture != null && !ctxLost.partialCapture.isEmpty()) {
                            processedFrames.addAll(ctxLost.partialCapture);
                        }
                        // Try to ensure we're back at the top before bailing out of the loop.
                        try { driver.switchTo().defaultContent(); } catch (Exception ignore) {}
                        break;
                    } catch (FatalIframeException e) {
                        throw e;
                    } catch (Exception e) {
                        log("Skipping frame \"" + frameSrc + "\" due to error: " + e.getMessage(), "debug");
                    }
                }
                if (!processedFrames.isEmpty()) {
                    mutableSnapshot.put("corsIframes", processedFrames);
                }
            }
        } catch (FatalIframeException e) {
            throw e;
        } catch (Exception e) {
            log("Failed to process cross-origin iframes: " + e.getMessage(), "debug");
        }
        return mutableSnapshot;
    }

    // Discover closed shadow roots via CDP and expose them on a window-bound
    // WeakMap that PercyDOM.serialize reads to pierce closed shadow DOM. This is
    // Chromium-only — wrapped in try/catch so other browsers (or a missing
    // executeCdpCommand) fall through silently. Three CDP calls per pair:
    // DOM.getDocument (depth=-1, pierce=true) to discover, then DOM.resolveNode
    // for host + shadow, then Runtime.callFunctionOn to write the pair into
    // the WeakMap on the page.
    @SuppressWarnings("unchecked")
    private void exposeClosedShadowRoots(WebDriver driver) {
        if (!(driver instanceof ChromeDriver)) return;
        ChromeDriver chrome;
        try { chrome = (ChromeDriver) driver; } catch (ClassCastException e) { return; }
        boolean domEnabled = false;
        try {
            chrome.executeCdpCommand("DOM.enable", new HashMap<>());
            domEnabled = true;
            Map<String, Object> getDocParams = new HashMap<>();
            getDocParams.put("depth", -1);
            getDocParams.put("pierce", true);
            Map<String, Object> doc = chrome.executeCdpCommand("DOM.getDocument", getDocParams);
            if (doc == null) return;
            Object rootObj = doc.get("root");
            if (!(rootObj instanceof Map)) return;
            List<Map<String, Object>> closedPairs = new ArrayList<>();
            collectClosedShadowPairs((Map<String, Object>) rootObj, closedPairs);
            if (closedPairs.isEmpty()) return;

            log("Found " + closedPairs.size() + " closed shadow root(s), exposing via CDP", "debug");

            ((JavascriptExecutor) chrome).executeScript(
                "window.__percyClosedShadowRoots = window.__percyClosedShadowRoots || new WeakMap();"
            );

            for (Map<String, Object> pair : closedPairs) {
                try {
                    Map<String, Object> hostParams = new HashMap<>();
                    hostParams.put("backendNodeId", pair.get("hostBackendNodeId"));
                    Map<String, Object> hostRes = chrome.executeCdpCommand("DOM.resolveNode", hostParams);
                    Map<String, Object> shadowParams = new HashMap<>();
                    shadowParams.put("backendNodeId", pair.get("shadowBackendNodeId"));
                    Map<String, Object> shadowRes = chrome.executeCdpCommand("DOM.resolveNode", shadowParams);
                    if (hostRes == null || shadowRes == null) continue;
                    Object hostObj = hostRes.get("object");
                    Object shadowObj = shadowRes.get("object");
                    if (!(hostObj instanceof Map) || !(shadowObj instanceof Map)) continue;
                    Object hostObjectId = ((Map<String, Object>) hostObj).get("objectId");
                    Object shadowObjectId = ((Map<String, Object>) shadowObj).get("objectId");
                    if (hostObjectId == null || shadowObjectId == null) continue;
                    Map<String, Object> callParams = new HashMap<>();
                    callParams.put("functionDeclaration",
                        "function(shadowRoot) { window.__percyClosedShadowRoots.set(this, shadowRoot); }");
                    callParams.put("objectId", hostObjectId);
                    List<Map<String, Object>> args = new ArrayList<>();
                    Map<String, Object> a = new HashMap<>();
                    a.put("objectId", shadowObjectId);
                    args.add(a);
                    callParams.put("arguments", args);
                    chrome.executeCdpCommand("Runtime.callFunctionOn", callParams);
                } catch (Exception perPair) {
                    log("Failed to expose a closed shadow root: " + perPair.getMessage(), "debug");
                }
            }
        } catch (Exception ex) {
            log("Could not expose closed shadow roots via CDP: " + ex.getMessage(), "debug");
        } finally {
            // Release the DOM domain so subsequent commands don't keep emitting
            // DOM events for this session. Best-effort — we don't care if this
            // fails (e.g., session already closed).
            if (domEnabled) {
                try { chrome.executeCdpCommand("DOM.disable", new HashMap<>()); }
                catch (Exception ignore) { /* defensive */ }
            }
        }
    }

    // Walk the CDP DOM tree looking for closed shadow roots. Skips nodes that
    // are themselves child-frame documents — cross-frame closed shadow roots
    // are not supported (different execution context, no WeakMap there).
    @SuppressWarnings("unchecked")
    private static void collectClosedShadowPairs(Map<String, Object> node, List<Map<String, Object>> out) {
        if (node.containsKey("contentDocument")) return;
        Object srs = node.get("shadowRoots");
        if (srs instanceof List<?>) {
            for (Object sr : (List<?>) srs) {
                if (!(sr instanceof Map)) continue;
                Map<String, Object> srMap = (Map<String, Object>) sr;
                if ("closed".equals(srMap.get("shadowRootType"))) {
                    Map<String, Object> pair = new HashMap<>();
                    pair.put("hostBackendNodeId", node.get("backendNodeId"));
                    pair.put("shadowBackendNodeId", srMap.get("backendNodeId"));
                    out.add(pair);
                }
                collectClosedShadowPairs(srMap, out);
            }
        }
        Object children = node.get("children");
        if (children instanceof List<?>) {
            for (Object child : (List<?>) children) {
                if (child instanceof Map) collectClosedShadowPairs((Map<String, Object>) child, out);
            }
        }
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
        try {
            for (Map<String, Object> widthMap : widths) {
                Object widthObj = widthMap.get("width");
                if (!(widthObj instanceof Number)) {
                    continue;
                }
                int width = ((Number) widthObj).intValue();
                Object heightObj = widthMap.get("height");
                log("Processing responsive snapshot for width " + width + " with target height " + targetHeight + ", height obj: " + heightObj, "debug");
                int heightForWidth = (heightObj instanceof Number) ? ((Number) heightObj).intValue() : targetHeight;
                log("Final height: " + heightForWidth, "debug");
                if (lastWindowWidth != width || lastWindowHeight != heightForWidth) {
                    resizeCount++;
                    log("Resizing window to width " + width + " and height " + heightForWidth, "debug");
                    changeWindowDimensionAndWait(driver, width, heightForWidth, resizeCount);
                    lastWindowWidth = width;
                    lastWindowHeight = heightForWidth;
                }
                if (PERCY_RESPONSIVE_CAPTURE_RELOAD_PAGE) {
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
        } finally {
            changeWindowDimensionAndWait(driver, currentWidth, currentHeight, resizeCount + 1);
        }
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
