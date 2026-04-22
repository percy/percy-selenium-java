package io.percy.selenium.cucumber;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.percy.selenium.Percy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

class PercyStepsTest {

    private WebDriver mockDriver;

    @BeforeEach
    void setUp() {
        mockDriver = mock(WebDriver.class);
    }

    @AfterEach
    void tearDown() {
        PercySteps.reset();
    }

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
        PercySteps steps = new PercySteps();
        assertThrows(IllegalStateException.class, steps::iHaveAPercyInstance);
    }

    @Test
    void testIHaveAPercyInstanceSucceedsWithDriver() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        assertDoesNotThrow(steps::iHaveAPercyInstance);
    }

    @Test
    void testCreateIgnoreRegionCSS() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        // Should not throw
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionCSS(".ad-banner"));
    }

    @Test
    void testCreateIgnoreRegionXPath() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionXPath("//div[@id='header']"));
    }

    @Test
    void testCreateIgnoreRegionBoundingBox() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIgnoreRegionBoundingBox(0, 0, 200, 100));
    }

    @Test
    void testCreateConsiderRegionCSS() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateConsiderRegionCSS(".content"));
    }

    @Test
    void testCreateConsiderRegionWithSensitivity() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(
            () -> steps.iCreateConsiderRegionCSSWithSensitivity(".content", 3));
    }

    @Test
    void testCreateIntelliIgnoreRegion() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        assertDoesNotThrow(() -> steps.iCreateIntelliIgnoreRegionCSS(".dynamic"));
    }

    @Test
    void testClearRegions() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        steps.iHaveAPercyInstance();
        steps.iCreateIgnoreRegionCSS(".ad");
        assertDoesNotThrow(steps::iClearPercyRegions);
    }

    @Test
    void testPercyShouldBeEnabledThrowsWithoutInit() {
        PercySteps steps = new PercySteps();
        assertThrows(IllegalStateException.class, steps::percyShouldBeEnabled);
    }

    @Test
    void testPercyShouldBeEnabledSucceeds() {
        PercySteps.setDriver(mockDriver);
        PercySteps steps = new PercySteps();
        assertDoesNotThrow(steps::percyShouldBeEnabled);
    }
}
