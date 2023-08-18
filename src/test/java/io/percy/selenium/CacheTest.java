package io.percy.selenium;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
//import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import io.github.bonigarcia.wdm.WebDriverManager;

import org.openqa.selenium.remote.*;
import static org.mockito.Mockito.*;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

public class CacheTest {
    private static RemoteWebDriver mockedDriver;

    private static Percy percy;

    @BeforeAll
    public static void testSetup() throws IOException {
        mockedDriver = mock(RemoteWebDriver.class);
        HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
        try {
            when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));
        } catch (Exception e) {
        }
        percy = spy(new Percy(mockedDriver));
        when(mockedDriver.getSessionId()).thenReturn(new SessionId("123"));
        when(mockedDriver.getCommandExecutor()).thenReturn(commandExecutor);
        DesiredCapabilities capabilities = new DesiredCapabilities();
        capabilities.setCapability("browserName", "Chrome");
        when(mockedDriver.getCapabilities()).thenReturn(capabilities);
    }

    @Test
    public void testSessionId() {
        Cache.CACHE_MAP.clear();
        DriverMetadata driverMetadata = new DriverMetadata((WebDriver) mockedDriver);
        assertEquals(driverMetadata.getSessionId(), "123");
    }

    @Test
    public void testCapabilities() {
        Cache.CACHE_MAP.clear();
        DriverMetadata driverMetadata = new DriverMetadata((WebDriver) mockedDriver);
        String key = "capabilities_"+driverMetadata.getSessionId();
        assertNull(Cache.CACHE_MAP.get(key));
        ConcurrentHashMap<String, String> caps = driverMetadata.getCapabilities();
        assertEquals(Cache.CACHE_MAP.get(key), caps);
    }

    @Test
    public void testCommandExecutorUrl() {
        Cache.CACHE_MAP.clear();
        DriverMetadata driverMetadata = new DriverMetadata(mockedDriver);
        String key = "commandExecutorUrl_"+driverMetadata.getSessionId();
        assertNull(Cache.CACHE_MAP.get(key));
        String commandExecutorUrl = driverMetadata.getCommandExecutorUrl();
        assertEquals(Cache.CACHE_MAP.get(key), commandExecutorUrl);
    }
}
