package io.percy.selenium.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.percy.selenium.Percy;

import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.*;

/**
 * Cucumber step definitions for Percy visual testing with Selenium.
 *
 * <p>Provides Gherkin steps to capture Percy snapshots, screenshots (Automate),
 * and define ignore/consider regions from Cucumber feature files.</p>
 *
 * <h3>Usage in feature files:</h3>
 * <pre>
 * Feature: Visual Testing
 *   Scenario: Homepage visual test
 *     Given I have a Percy instance
 *     When I take a Percy snapshot named "Homepage"
 *
 *   Scenario: Snapshot with options
 *     Given I have a Percy instance
 *     When I take a Percy snapshot named "Responsive" with widths "375,768,1280"
 *
 *   Scenario: Ignore region
 *     Given I have a Percy instance
 *     And I create a Percy ignore region with CSS selector ".ad-banner"
 *     When I take a Percy snapshot named "No Ads" with regions
 *
 *   Scenario: Automate screenshot
 *     Given I have a Percy instance
 *     When I take a Percy screenshot named "Login Page"
 * </pre>
 *
 * <h3>Setup in step definition glue:</h3>
 * <pre>
 * public class Hooks {
 *     private static WebDriver driver;
 *
 *     {@literal @}Before
 *     public void setUp() {
 *         driver = new ChromeDriver();
 *         PercySteps.setDriver(driver);
 *     }
 *
 *     {@literal @}After
 *     public void tearDown() {
 *         if (driver != null) driver.quit();
 *         PercySteps.reset();
 *     }
 * }
 * </pre>
 */
public class PercySteps {

    private static WebDriver driver;
    private static Percy percy;
    private static List<Map<String, Object>> regions = new ArrayList<>();

    /**
     * Set the WebDriver instance for Percy to use.
     * Call this from your Cucumber hooks before using any Percy steps.
     *
     * @param webDriver the Selenium WebDriver instance
     */
    public static void setDriver(WebDriver webDriver) {
        driver = webDriver;
        percy = new Percy(driver);
    }

    /**
     * Get the current Percy instance.
     *
     * @return the Percy instance, or null if not initialized
     */
    public static Percy getPercy() {
        return percy;
    }

    /**
     * Reset the Percy instance and clear stored regions.
     * Call this from your Cucumber hooks in teardown.
     */
    public static void reset() {
        percy = null;
        driver = null;
        regions.clear();
    }

    // ------------------------------------------------------------------
    // Given steps
    // ------------------------------------------------------------------

    @Given("I have a Percy instance")
    public void iHaveAPercyInstance() {
        if (driver == null) {
            throw new IllegalStateException(
                "WebDriver not set. Call PercySteps.setDriver(driver) in your @Before hook.");
        }
        if (percy == null) {
            percy = new Percy(driver);
        }
    }

    @Given("I create a Percy ignore region with CSS selector {string}")
    public void iCreateIgnoreRegionCSS(String cssSelector) {
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "ignore");
        params.put("elementCSS", cssSelector);
        regions.add(percy.createRegion(params));
    }

    @Given("I create a Percy ignore region with XPath {string}")
    public void iCreateIgnoreRegionXPath(String xpath) {
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "ignore");
        params.put("elementXpath", xpath);
        regions.add(percy.createRegion(params));
    }

    @Given("I create a Percy ignore region with bounding box {int}, {int}, {int}, {int}")
    public void iCreateIgnoreRegionBoundingBox(int x, int y, int width, int height) {
        Map<String, Object> boundingBox = new HashMap<>();
        boundingBox.put("x", x);
        boundingBox.put("y", y);
        boundingBox.put("width", width);
        boundingBox.put("height", height);

        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "ignore");
        params.put("boundingBox", boundingBox);
        regions.add(percy.createRegion(params));
    }

    @Given("I create a Percy consider region with CSS selector {string}")
    public void iCreateConsiderRegionCSS(String cssSelector) {
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "standard");
        params.put("elementCSS", cssSelector);
        regions.add(percy.createRegion(params));
    }

    @Given("I create a Percy consider region with CSS selector {string} and diff sensitivity {int}")
    public void iCreateConsiderRegionCSSWithSensitivity(String cssSelector, int sensitivity) {
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "standard");
        params.put("elementCSS", cssSelector);
        params.put("diffSensitivity", sensitivity);
        regions.add(percy.createRegion(params));
    }

    @Given("I create a Percy intelliignore region with CSS selector {string}")
    public void iCreateIntelliIgnoreRegionCSS(String cssSelector) {
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "intelliignore");
        params.put("elementCSS", cssSelector);
        regions.add(percy.createRegion(params));
    }

    @Given("I clear Percy regions")
    public void iClearPercyRegions() {
        regions.clear();
    }

    // ------------------------------------------------------------------
    // When steps - Snapshot (DOM)
    // ------------------------------------------------------------------

    @When("I take a Percy snapshot named {string}")
    public void iTakeSnapshot(String name) {
        percy.snapshot(name);
    }

    @When("I take a Percy snapshot named {string} with widths {string}")
    public void iTakeSnapshotWithWidths(String name, String widths) {
        Map<String, Object> options = new HashMap<>();
        options.put("widths", parseWidths(widths));
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with min height {int}")
    public void iTakeSnapshotWithMinHeight(String name, int minHeight) {
        Map<String, Object> options = new HashMap<>();
        options.put("minHeight", minHeight);
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with Percy CSS {string}")
    public void iTakeSnapshotWithCSS(String name, String percyCSS) {
        Map<String, Object> options = new HashMap<>();
        options.put("percyCSS", percyCSS);
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with scope {string}")
    public void iTakeSnapshotWithScope(String name, String scope) {
        Map<String, Object> options = new HashMap<>();
        options.put("scope", scope);
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with layout mode")
    public void iTakeSnapshotWithLayout(String name) {
        Map<String, Object> options = new HashMap<>();
        options.put("enableLayout", true);
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with JavaScript enabled")
    public void iTakeSnapshotWithJS(String name) {
        Map<String, Object> options = new HashMap<>();
        options.put("enableJavaScript", true);
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with labels {string}")
    public void iTakeSnapshotWithLabels(String name, String labels) {
        Map<String, Object> options = new HashMap<>();
        options.put("labels", labels);
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with test case {string}")
    public void iTakeSnapshotWithTestCase(String name, String testCase) {
        Map<String, Object> options = new HashMap<>();
        options.put("testCase", testCase);
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with regions")
    public void iTakeSnapshotWithRegions(String name) {
        Map<String, Object> options = new HashMap<>();
        if (!regions.isEmpty()) {
            options.put("regions", new ArrayList<>(regions));
            regions.clear();
        }
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with widths {string} and regions")
    public void iTakeSnapshotWithWidthsAndRegions(String name, String widths) {
        Map<String, Object> options = new HashMap<>();
        options.put("widths", parseWidths(widths));
        if (!regions.isEmpty()) {
            options.put("regions", new ArrayList<>(regions));
            regions.clear();
        }
        percy.snapshot(name, options);
    }

    @When("I take a Percy snapshot named {string} with options:")
    public void iTakeSnapshotWithOptions(String name, Map<String, String> optionsTable) {
        Map<String, Object> options = buildOptions(optionsTable);
        if (!regions.isEmpty()) {
            options.put("regions", new ArrayList<>(regions));
            regions.clear();
        }
        percy.snapshot(name, options);
    }

    // ------------------------------------------------------------------
    // When steps - Screenshot (Automate)
    // ------------------------------------------------------------------

    @When("I take a Percy screenshot named {string}")
    public void iTakeScreenshot(String name) {
        percy.screenshot(name);
    }

    @When("I take a Percy screenshot named {string} with regions")
    public void iTakeScreenshotWithRegions(String name) {
        Map<String, Object> options = new HashMap<>();
        if (!regions.isEmpty()) {
            options.put("regions", new ArrayList<>(regions));
            regions.clear();
        }
        percy.screenshot(name, options);
    }

    @When("I take a Percy screenshot named {string} with options:")
    public void iTakeScreenshotWithOptions(String name, Map<String, String> optionsTable) {
        Map<String, Object> options = buildOptions(optionsTable);
        if (!regions.isEmpty()) {
            options.put("regions", new ArrayList<>(regions));
            regions.clear();
        }
        percy.screenshot(name, options);
    }

    // ------------------------------------------------------------------
    // Then steps
    // ------------------------------------------------------------------

    @Then("Percy should be enabled")
    public void percyShouldBeEnabled() {
        if (percy == null) {
            throw new IllegalStateException("Percy instance not initialized.");
        }
        // Percy constructor calls healthcheck; if it fails, snapshots silently no-op.
        // This step validates the instance exists.
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static List<Integer> parseWidths(String widths) {
        List<Integer> result = new ArrayList<>();
        for (String w : widths.split(",")) {
            result.add(Integer.parseInt(w.trim()));
        }
        return result;
    }

    private static Map<String, Object> buildOptions(Map<String, String> table) {
        Map<String, Object> options = new HashMap<>();
        for (Map.Entry<String, String> entry : table.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            switch (key) {
                case "widths":
                    options.put("widths", parseWidths(value));
                    break;
                case "minHeight":
                    options.put("minHeight", Integer.parseInt(value));
                    break;
                case "enableJavaScript":
                case "enableLayout":
                case "disableShadowDom":
                case "responsiveSnapshotCapture":
                    options.put(key, Boolean.parseBoolean(value));
                    break;
                default:
                    options.put(key, value);
                    break;
            }
        }
        return options;
    }
}
