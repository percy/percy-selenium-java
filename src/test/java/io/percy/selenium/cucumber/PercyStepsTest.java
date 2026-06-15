package io.percy.selenium.cucumber;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.percy.selenium.Percy;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Field;
import java.util.*;

class PercyStepsTest {

    private WebDriver mockDriver;
    private Percy mockPercy;
    private PercySteps steps;

    @BeforeEach
    void setUp() {
        mockDriver = mock(WebDriver.class);
        mockPercy = mock(Percy.class);
        steps = new PercySteps();
    }

    @AfterEach
    void tearDown() {
        PercySteps.reset();
    }

    private void initWithMockPercy() {
        PercySteps.setDriver(mockDriver);
        setPercyField(mockPercy);
    }

    private void setPercyField(Percy percy) {
        try {
            Field field = PercySteps.class.getDeclaredField("percy");
            field.setAccessible(true);
            field.set(null, percy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle tests
    // ------------------------------------------------------------------

    @Test
    void testSetDriverAndGetPercy() {
        PercySteps.setDriver(mockDriver);
        assertNotNull(PercySteps.getPercy());
    }

    @Test
    void testResetClearsState() {
        PercySteps.setDriver(mockDriver);
        assertNotNull(PercySteps.getPercy());

        PercySteps.reset();
        assertNull(PercySteps.getPercy());
    }

    @Test
    void testIHaveAPercyInstanceThrowsWithoutDriver() {
        assertThrows(IllegalStateException.class, steps::iHaveAPercyInstance);
    }

    @Test
    void testIHaveAPercyInstanceSucceedsWithDriver() {
        PercySteps.setDriver(mockDriver);
        assertDoesNotThrow(steps::iHaveAPercyInstance);
    }

    // ------------------------------------------------------------------
    // Region creation tests
    // ------------------------------------------------------------------

    @Test
    void testCreateIgnoreRegionCSS() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionCSS(".ad-banner"));
        verify(mockPercy).createRegion(argThat(map ->
            "ignore".equals(map.get("algorithm")) && ".ad-banner".equals(map.get("elementCSS"))
        ));
    }

    @Test
    void testCreateIgnoreRegionXPath() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionXPath("//div[@id='header']"));
        verify(mockPercy).createRegion(argThat(map ->
            "ignore".equals(map.get("algorithm")) && "//div[@id='header']".equals(map.get("elementXpath"))
        ));
    }

    @Test
    void testCreateIgnoreRegionBoundingBox() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionBoundingBox(0, 0, 200, 100));
        verify(mockPercy).createRegion(argThat(map ->
            "ignore".equals(map.get("algorithm")) && map.containsKey("boundingBox")
        ));
    }

    @Test
    void testCreateConsiderRegionCSS() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateConsiderRegionCSS(".content"));
        verify(mockPercy).createRegion(argThat(map ->
            "standard".equals(map.get("algorithm")) && ".content".equals(map.get("elementCSS"))
        ));
    }

    @Test
    void testCreateConsiderRegionWithSensitivity() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(
            () -> steps.iCreateConsiderRegionCSSWithSensitivity(".content", 3));
        verify(mockPercy).createRegion(argThat(map ->
            "standard".equals(map.get("algorithm")) && Integer.valueOf(3).equals(map.get("diffSensitivity"))
        ));
    }

    @Test
    void testCreateIntelliIgnoreRegion() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIntelliIgnoreRegionCSS(".dynamic"));
        verify(mockPercy).createRegion(argThat(map ->
            "intelliignore".equals(map.get("algorithm")) && ".dynamic".equals(map.get("elementCSS"))
        ));
    }

    @Test
    void testCreateIgnoreRegionCSSWithPadding() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSSWithPadding(".ad", 10);
        verify(mockPercy).createRegion(argThat(map ->
            "ignore".equals(map.get("algorithm"))
                && ".ad".equals(map.get("elementCSS"))
                && Integer.valueOf(10).equals(map.get("padding"))
        ));
    }

    @Test
    void testCreateIgnoreRegionXPathWithPadding() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionXPathWithPadding("//div", 5);
        verify(mockPercy).createRegion(argThat(map ->
            "ignore".equals(map.get("algorithm"))
                && "//div".equals(map.get("elementXpath"))
                && Integer.valueOf(5).equals(map.get("padding"))
        ));
    }

    @Test
    void testCreateConsiderRegionXPath() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        steps.iCreateConsiderRegionXPath("//main");
        verify(mockPercy).createRegion(argThat(map ->
            "standard".equals(map.get("algorithm")) && "//main".equals(map.get("elementXpath"))
        ));
    }

    @Test
    void testCreateConsiderRegionXPathWithSensitivity() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        steps.iCreateConsiderRegionXPathWithSensitivity("//main", 5);
        verify(mockPercy).createRegion(argThat(map ->
            "standard".equals(map.get("algorithm"))
                && "//main".equals(map.get("elementXpath"))
                && Integer.valueOf(5).equals(map.get("diffSensitivity"))
        ));
    }

    @Test
    void testCreateIntelliIgnoreRegionXPath() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        steps.iCreateIntelliIgnoreRegionXPath("//aside");
        verify(mockPercy).createRegion(argThat(map ->
            "intelliignore".equals(map.get("algorithm")) && "//aside".equals(map.get("elementXpath"))
        ));
    }

    @Test
    void testClearRegions() {
        initWithMockPercy();
        when(mockPercy.createRegion(anyMap())).thenReturn(new HashMap<>());
        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSS(".ad");
        assertDoesNotThrow(steps::iClearPercyRegions);
    }

    // ------------------------------------------------------------------
    // Snapshot tests
    // ------------------------------------------------------------------

    @Test
    void testTakeSnapshot() {
        initWithMockPercy();
        steps.iTakeSnapshot("Homepage");
        verify(mockPercy).snapshot("Homepage");
    }

    @Test
    void testTakeSnapshotWithWidths() {
        initWithMockPercy();
        steps.iTakeSnapshotWithWidths("Responsive", "375,768,1280");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Responsive"), captor.capture());
        List<Integer> widths = (List<Integer>) captor.getValue().get("widths");
        assertEquals(Arrays.asList(375, 768, 1280), widths);
    }

    @Test
    void testTakeSnapshotWithMinHeight() {
        initWithMockPercy();
        steps.iTakeSnapshotWithMinHeight("Tall Page", 2000);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Tall Page"), captor.capture());
        assertEquals(2000, captor.getValue().get("minHeight"));
    }

    @Test
    void testTakeSnapshotWithCSS() {
        initWithMockPercy();
        steps.iTakeSnapshotWithCSS("Styled", "body { background: purple; }");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Styled"), captor.capture());
        assertEquals("body { background: purple; }", captor.getValue().get("percyCSS"));
    }

    @Test
    void testTakeSnapshotWithScope() {
        initWithMockPercy();
        steps.iTakeSnapshotWithScope("Scoped", "#main");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Scoped"), captor.capture());
        assertEquals("#main", captor.getValue().get("scope"));
    }

    @Test
    void testTakeSnapshotWithLayout() {
        initWithMockPercy();
        steps.iTakeSnapshotWithLayout("Layout Mode");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Layout Mode"), captor.capture());
        assertEquals(true, captor.getValue().get("enableLayout"));
    }

    @Test
    void testTakeSnapshotWithJS() {
        initWithMockPercy();
        steps.iTakeSnapshotWithJS("JS Enabled");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("JS Enabled"), captor.capture());
        assertEquals(true, captor.getValue().get("enableJavaScript"));
    }

    @Test
    void testTakeSnapshotWithLabels() {
        initWithMockPercy();
        steps.iTakeSnapshotWithLabels("Labeled", "regression,smoke");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Labeled"), captor.capture());
        assertEquals("regression,smoke", captor.getValue().get("labels"));
    }

    @Test
    void testTakeSnapshotWithTestCase() {
        initWithMockPercy();
        steps.iTakeSnapshotWithTestCase("TC Snap", "TC-001");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("TC Snap"), captor.capture());
        assertEquals("TC-001", captor.getValue().get("testCase"));
    }

    @Test
    void testTakeSnapshotWithShadowDomDisabled() {
        initWithMockPercy();
        steps.iTakeSnapshotWithShadowDomDisabled("No Shadow");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("No Shadow"), captor.capture());
        assertEquals(true, captor.getValue().get("disableShadowDom"));
    }

    @Test
    void testTakeSnapshotWithResponsiveCapture() {
        initWithMockPercy();
        steps.iTakeSnapshotWithResponsiveCapture("Responsive Capture");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Responsive Capture"), captor.capture());
        assertEquals(true, captor.getValue().get("responsiveSnapshotCapture"));
    }

    @Test
    void testTakeSnapshotWithSync() {
        initWithMockPercy();
        steps.iTakeSnapshotWithSync("Sync Snap");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Sync Snap"), captor.capture());
        assertEquals(true, captor.getValue().get("sync"));
    }

    @Test
    void testTakeSnapshotWithRegions() {
        initWithMockPercy();
        Map<String, Object> fakeRegion = new HashMap<>();
        fakeRegion.put("algorithm", "ignore");
        when(mockPercy.createRegion(anyMap())).thenReturn(fakeRegion);

        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSS(".ad-banner");
        steps.iTakeSnapshotWithRegions("No Ads");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("No Ads"), captor.capture());
        List<Map<String, Object>> regions = (List<Map<String, Object>>) captor.getValue().get("regions");
        assertNotNull(regions);
        assertEquals(1, regions.size());
        assertEquals("ignore", regions.get(0).get("algorithm"));
    }

    @Test
    void testTakeSnapshotWithRegionsClearsAfterSnapshot() {
        initWithMockPercy();
        Map<String, Object> fakeRegion = new HashMap<>();
        fakeRegion.put("algorithm", "ignore");
        when(mockPercy.createRegion(anyMap())).thenReturn(fakeRegion);

        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSS(".ad");
        steps.iTakeSnapshotWithRegions("First");

        steps.iTakeSnapshotWithRegions("Second");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Second"), captor.capture());
        Map<String, Object> opts = captor.getValue();
        assertFalse(opts.containsKey("regions"));
    }

    @Test
    void testTakeSnapshotWithWidthsAndRegions() {
        initWithMockPercy();
        Map<String, Object> fakeRegion = new HashMap<>();
        fakeRegion.put("algorithm", "standard");
        when(mockPercy.createRegion(anyMap())).thenReturn(fakeRegion);

        steps.iHaveAPercyInstance();
        steps.iCreateConsiderRegionCSS(".main");
        steps.iTakeSnapshotWithWidthsAndRegions("Wide+Regions", "768,1280");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Wide+Regions"), captor.capture());
        Map<String, Object> opts = captor.getValue();
        assertEquals(Arrays.asList(768, 1280), opts.get("widths"));
        assertNotNull(opts.get("regions"));
    }

    @Test
    void testTakeSnapshotWithOptionsTable() {
        initWithMockPercy();
        Map<String, String> optionsTable = new LinkedHashMap<>();
        optionsTable.put("widths", "375,1280");
        optionsTable.put("minHeight", "2000");
        optionsTable.put("percyCSS", "body { color: red; }");
        optionsTable.put("enableJavaScript", "true");
        optionsTable.put("sync", "true");

        steps.iTakeSnapshotWithOptions("With Options", optionsTable);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("With Options"), captor.capture());
        Map<String, Object> opts = captor.getValue();
        assertEquals(Arrays.asList(375, 1280), opts.get("widths"));
        assertEquals(2000, opts.get("minHeight"));
        assertEquals("body { color: red; }", opts.get("percyCSS"));
        assertEquals(true, opts.get("enableJavaScript"));
        assertEquals(true, opts.get("sync"));
    }

    // ------------------------------------------------------------------
    // Screenshot (Automate) tests
    // ------------------------------------------------------------------

    @Test
    void testTakeScreenshot() {
        initWithMockPercy();
        steps.iTakeScreenshot("Login Page");
        verify(mockPercy).screenshot("Login Page");
    }

    @Test
    void testTakeScreenshotWithRegions() {
        initWithMockPercy();
        Map<String, Object> fakeRegion = new HashMap<>();
        fakeRegion.put("algorithm", "ignore");
        when(mockPercy.createRegion(anyMap())).thenReturn(fakeRegion);

        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSS(".banner");
        steps.iTakeScreenshotWithRegions("No Banner");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).screenshot(eq("No Banner"), captor.capture());
        List<Map<String, Object>> regions = (List<Map<String, Object>>) captor.getValue().get("regions");
        assertNotNull(regions);
        assertEquals(1, regions.size());
    }

    @Test
    void testTakeScreenshotWithOptions() {
        initWithMockPercy();
        Map<String, String> optionsTable = new LinkedHashMap<>();
        optionsTable.put("percyCSS", "body { color: blue; }");

        steps.iTakeScreenshotWithOptions("Styled Screenshot", optionsTable);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).screenshot(eq("Styled Screenshot"), captor.capture());
        assertEquals("body { color: blue; }", captor.getValue().get("percyCSS"));
    }

    // ------------------------------------------------------------------
    // Then step tests
    // ------------------------------------------------------------------

    @Test
    void testPercyShouldBeEnabledThrowsWithoutInit() {
        assertThrows(IllegalStateException.class, steps::percyShouldBeEnabled);
    }

    @Test
    void testPercyShouldBeEnabledSucceeds() {
        PercySteps.setDriver(mockDriver);
        assertDoesNotThrow(steps::percyShouldBeEnabled);
    }

    // ------------------------------------------------------------------
    // Additional coverage: lazy Percy creation, options-table regions,
    // scopeOptions parsing, and getCucumberVersion fallback paths.
    // ------------------------------------------------------------------

    @Test
    void testIHaveAPercyInstanceCreatesPercyWhenDriverSetButPercyNull() throws Exception {
        // driver set, percy field forced to null -> the `percy == null` branch
        // inside iHaveAPercyInstance() constructs a fresh Percy instance.
        PercySteps.setDriver(mockDriver);
        setPercyField(null);
        assertNull(PercySteps.getPercy());

        steps.iHaveAPercyInstance();
        assertNotNull(PercySteps.getPercy());
    }

    @Test
    void testTakeSnapshotWithOptionsTableIncludesRegions() {
        initWithMockPercy();
        Map<String, Object> fakeRegion = new HashMap<>();
        fakeRegion.put("algorithm", "ignore");
        when(mockPercy.createRegion(anyMap())).thenReturn(fakeRegion);

        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSS(".ad-banner");

        Map<String, String> optionsTable = new LinkedHashMap<>();
        optionsTable.put("percyCSS", "body { color: green; }");
        steps.iTakeSnapshotWithOptions("Options+Regions", optionsTable);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Options+Regions"), captor.capture());
        List<Map<String, Object>> regions = (List<Map<String, Object>>) captor.getValue().get("regions");
        assertNotNull(regions);
        assertEquals(1, regions.size());
        assertEquals("body { color: green; }", captor.getValue().get("percyCSS"));
    }

    @Test
    void testTakeScreenshotWithOptionsTableIncludesRegions() {
        initWithMockPercy();
        Map<String, Object> fakeRegion = new HashMap<>();
        fakeRegion.put("algorithm", "ignore");
        when(mockPercy.createRegion(anyMap())).thenReturn(fakeRegion);

        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSS(".banner");

        Map<String, String> optionsTable = new LinkedHashMap<>();
        optionsTable.put("percyCSS", "body { color: red; }");
        steps.iTakeScreenshotWithOptions("Shot+Regions", optionsTable);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).screenshot(eq("Shot+Regions"), captor.capture());
        List<Map<String, Object>> regions = (List<Map<String, Object>>) captor.getValue().get("regions");
        assertNotNull(regions);
        assertEquals(1, regions.size());
    }

    @Test
    void testBuildOptionsParsesScopeOptions() {
        initWithMockPercy();
        Map<String, String> optionsTable = new LinkedHashMap<>();
        optionsTable.put("scopeOptions", "{\"scroll\":true}");

        steps.iTakeSnapshotWithOptions("Scoped", optionsTable);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mockPercy).snapshot(eq("Scoped"), captor.capture());
        Map<String, Object> scopeOptions = (Map<String, Object>) captor.getValue().get("scopeOptions");
        assertNotNull(scopeOptions);
        assertEquals(true, scopeOptions.get("scroll"));
    }

    @Test
    void testGetCucumberVersionReturnsValueWhenManifestPresent() throws Exception {
        // Drives the happy path of the version-resolution seam: a non-null
        // implementation version is returned verbatim.
        String version = invokeGetCucumberVersionWithResolver(() -> "9.9.9");
        assertEquals("9.9.9", version);
    }

    @Test
    void testGetCucumberVersionFallsBackToUnknownWhenManifestMissing() throws Exception {
        // resolver returns null (no manifest) -> "unknown".
        String version = invokeGetCucumberVersionWithResolver(() -> null);
        assertEquals("unknown", version);
    }

    @Test
    void testGetCucumberVersionFallsBackToUnknownWhenResolverThrows() throws Exception {
        // resolver throws -> the catch block returns "unknown".
        String version = invokeGetCucumberVersionWithResolver(() -> { throw new RuntimeException("boom"); });
        assertEquals("unknown", version);
    }

    /**
     * Invokes the package-private {@code resolveCucumberVersion} seam so the
     * happy/null/throwing branches of {@link PercySteps#getCucumberVersion()}
     * can be exercised deterministically without depending on the cucumber jar
     * manifest (which is absent under test).
     */
    private static String invokeGetCucumberVersionWithResolver(
            java.util.concurrent.Callable<String> resolver) throws Exception {
        java.lang.reflect.Method m = PercySteps.class.getDeclaredMethod(
            "resolveCucumberVersion", java.util.concurrent.Callable.class);
        m.setAccessible(true);
        return (String) m.invoke(null, resolver);
    }
}
