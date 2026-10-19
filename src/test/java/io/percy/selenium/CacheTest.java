package io.percy.selenium;

import java.io.IOException;

//import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.openqa.selenium.WebDriver;

import org.openqa.selenium.remote.*;
import org.openqa.selenium.remote.http.ClientConfig;
import static org.mockito.Mockito.*;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;

public class CacheTest {
    private static RemoteWebDriver mockedDriver;

    @BeforeAll
    public static void testSetup() throws IOException {
        mockedDriver = mock(RemoteWebDriver.class);
        HttpCommandExecutor commandExecutor = mock(HttpCommandExecutor.class);
        try {
            when(commandExecutor.getAddressOfRemoteServer()).thenReturn(new URL("https://hub-cloud.browserstack.com/wd/hub"));
        } catch (Exception e) {
        }
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

    @Test
    public void testCacheInstantiable() {
        // Exercises the implicit default constructor of Cache (its only line).
        assertNotNull(new Cache());
    }

    // ------------------------------------------------------------------
    // getCommandExecutorUrl: TracedCommandExecutor unwrap branch.
    //
    // When the executor's class name contains "TracedCommandExecutor",
    // DriverMetadata reflectively reads its private `delegate` field and
    // unwraps to the underlying HttpCommandExecutor. These fixtures let us
    // drive both the successful unwrap and the reflective-failure fallback
    // without a live Selenium tracing executor.
    // ------------------------------------------------------------------

    /** Mirrors Selenium's internal wrapper: a delegate field holding the real executor. */
    static class TracedCommandExecutor implements CommandExecutor {
        @SuppressWarnings("unused")
        private final CommandExecutor delegate;
        TracedCommandExecutor(CommandExecutor delegate) { this.delegate = delegate; }
        @Override
        public org.openqa.selenium.remote.Response execute(org.openqa.selenium.remote.Command command) {
            throw new UnsupportedOperationException();
        }
    }

    /** Same name suffix but without a `delegate` field, to drive the catch fallback. */
    static class BrokenTracedCommandExecutor implements CommandExecutor {
        @Override
        public org.openqa.selenium.remote.Response execute(org.openqa.selenium.remote.Command command) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void testCommandExecutorUrlUnwrapsTracedExecutor() throws Exception {
        Cache.CACHE_MAP.clear();
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getSessionId()).thenReturn(new SessionId("traced-1"));

        HttpCommandExecutor inner = mock(HttpCommandExecutor.class);
        when(inner.getAddressOfRemoteServer()).thenReturn(new URL("https://hub.example.com/wd/hub"));
        TracedCommandExecutor traced = new TracedCommandExecutor(inner);
        when(driver.getCommandExecutor()).thenReturn(traced);

        DriverMetadata driverMetadata = new DriverMetadata(driver);
        String url = driverMetadata.getCommandExecutorUrl();
        assertEquals("https://hub.example.com/wd/hub", url);
    }

    @Test
    public void testCommandExecutorUrlReturnsErrorWhenDelegateFieldMissing() {
        Cache.CACHE_MAP.clear();
        RemoteWebDriver driver = mock(RemoteWebDriver.class);
        when(driver.getSessionId()).thenReturn(new SessionId("traced-2"));
        when(driver.getCommandExecutor()).thenReturn(new BrokenTracedCommandExecutor());

        DriverMetadata driverMetadata = new DriverMetadata(driver);
        // No `delegate` field -> reflective lookup throws and the catch returns
        // the exception's string form rather than a URL.
        String result = driverMetadata.getCommandExecutorUrl();
        assertNotNull(result);
        assertTrue(result.contains("NoSuchFieldException") || result.contains("delegate"));
    }

    // ------------------------------------------------------------------
    // getCommandExecutorUrl: ClientConfig path (Selenium 4.50+) and the
    // reflective getAddressOfRemoteServer fallbacks.
    // ------------------------------------------------------------------

    private static boolean hasClientConfigAccessor() {
        try {
            RemoteWebDriver.class.getMethod("getClientConfig");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    // getClientConfig() only exists on Selenium 4.50+, so it is stubbed through a default answer
    // to keep this file compiling against the Java 8 profile's older Selenium.
    private static RemoteWebDriver driverWithClientConfig(String sessionId, ClientConfig config) {
        RemoteWebDriver driver = mock(RemoteWebDriver.class, invocation ->
            invocation.getMethod().getName().equals("getClientConfig")
                ? config
                : RETURNS_DEFAULTS.answer(invocation));
        when(driver.getSessionId()).thenReturn(new SessionId(sessionId));
        return driver;
    }

    @Test
    public void testCommandExecutorUrlPrefersClientConfigBaseUri() throws Exception {
        assumeTrue(hasClientConfigAccessor());
        Cache.CACHE_MAP.clear();
        ClientConfig config = ClientConfig.defaultConfig().baseUrl(new URL("https://hub-cloud.browserstack.com/wd/hub"));
        RemoteWebDriver driver = driverWithClientConfig("client-config-1", config);
        HttpCommandExecutor executor = mock(HttpCommandExecutor.class);
        when(driver.getCommandExecutor()).thenReturn(executor);

        String url = new DriverMetadata(driver).getCommandExecutorUrl();

        assertEquals("https://hub-cloud.browserstack.com/wd/hub", url);
        verifyNoInteractions(executor);
    }

    @Test
    public void testCommandExecutorUrlFallsBackToExecutorWithoutBaseUri() throws Exception {
        assumeTrue(hasClientConfigAccessor());
        Cache.CACHE_MAP.clear();
        RemoteWebDriver driver = driverWithClientConfig("client-config-2", ClientConfig.defaultConfig());
        HttpCommandExecutor executor = new HttpCommandExecutor(new URL("http://localhost:9515"));
        when(driver.getCommandExecutor()).thenReturn(executor);

        assertEquals("http://localhost:9515", new DriverMetadata(driver).getCommandExecutorUrl());
    }

    @Test
    public void testGetAddressOfRemoteServerOnRealExecutor() throws Exception {
        HttpCommandExecutor executor = new HttpCommandExecutor(new URL("https://hub.example.com/wd/hub"));
        assertEquals(new URL("https://hub.example.com/wd/hub"), DriverMetadata.getAddressOfRemoteServer(executor));
    }

    @Test
    public void testReadRemoteServerFieldMatchesAccessor() throws Exception {
        HttpCommandExecutor executor = new HttpCommandExecutor(new URL("https://hub.example.com/wd/hub"));
        assertEquals(new URL("https://hub.example.com/wd/hub"), DriverMetadata.readRemoteServerField(executor));
    }

    @Test
    public void testGetAddressOfRemoteServerWrapsInvocationFailure() {
        HttpCommandExecutor executor = mock(HttpCommandExecutor.class, invocation -> {
            throw new IllegalStateException("boom");
        });
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> DriverMetadata.getAddressOfRemoteServer(executor));
        assertEquals("boom", e.getCause().getCause().getMessage());
    }

    @Test
    public void testReadRemoteServerFieldRejectsOtherExecutors() {
        assertThrows(RuntimeException.class,
            () -> DriverMetadata.readRemoteServerField(new BrokenTracedCommandExecutor()));
    }
}
