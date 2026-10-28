package io.percy.selenium;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;


public class DriverMetadata {

    private String sessionId;
    private WebDriver driver;
    private ConcurrentHashMap<String, String> capabilities = new ConcurrentHashMap<String, String>();
    private final List<String> capsNeeded = new ArrayList<>(Arrays.asList("browserName", "platform", "platformName", "version", "osVersion", "proxy", "deviceName"));
    public DriverMetadata(WebDriver driver) {
        this.driver = driver;
        this.sessionId = ((RemoteWebDriver) driver).getSessionId().toString();
    }

    public  String getSessionId() {
        return this.sessionId;
    }

    public ConcurrentHashMap<String, String> getCapabilities() {
        String key = "capabilities_" + this.sessionId;
        if (Cache.CACHE_MAP.get(key) == null) {
            Capabilities caps = ((RemoteWebDriver) driver).getCapabilities();
            ConcurrentHashMap<String, String> capabilities = new ConcurrentHashMap<String, String>();

            Iterator<String> iterator = capsNeeded.iterator();
            while (iterator.hasNext()) {
                String cap = iterator.next();
                if (caps.getCapability(cap) != null) {
                    capabilities.put(cap, caps.getCapability(cap).toString());
                }
            }
            Cache.CACHE_MAP.put(key, capabilities);
        }
        return (ConcurrentHashMap<String, String>) Cache.CACHE_MAP.get(key);
    }

    public String getCommandExecutorUrl() {
        String key = "commandExecutorUrl_" + this.sessionId;
        if (Cache.CACHE_MAP.get(key) == null) {
            String remoteWebAddress = getClientConfigBaseUri();
            if (remoteWebAddress == null) {
                CommandExecutor executor = ((RemoteWebDriver) driver).getCommandExecutor();

                // Get HttpCommandExecutor From TracedCommandExecutor
                if (executor.getClass().toString().contains("TracedCommandExecutor")) {
                    Class className = executor.getClass();
                    try {
                        Field field = className.getDeclaredField("delegate");
                        // make private field accessible
                        field.setAccessible(true);
                        executor = (HttpCommandExecutor) field.get(executor);
                    } catch (Exception e) {
                        Percy.log(e.toString());
                        return e.toString();
                    }
                }
                remoteWebAddress = getAddressOfRemoteServer(executor).toString();
            }
            Cache.CACHE_MAP.put(key, remoteWebAddress);
        }
        return (String) Cache.CACHE_MAP.get(key);
    }

    // RemoteWebDriver.getClientConfig() is the non-deprecated source of the hub URL in Selenium
    // 4.50+, but it is absent from older Selenium versions, so it is looked up reflectively. Its
    // baseUri is null when the driver was built from a CommandExecutor (e.g. local drivers).
    private String getClientConfigBaseUri() {
        try {
            Object clientConfig = driver.getClass().getMethod("getClientConfig").invoke(driver);
            if (clientConfig == null) {
                return null;
            }
            Object baseUri = clientConfig.getClass().getMethod("baseUri").invoke(clientConfig);
            return baseUri == null ? null : baseUri.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // HttpCommandExecutor.getAddressOfRemoteServer() is deprecated for removal since Selenium 4.50.
    // Calling it reflectively keeps this class loadable once it is gone; the private field it
    // returns is the last resort.
    static URL getAddressOfRemoteServer(CommandExecutor executor) {
        try {
            Method method = HttpCommandExecutor.class.getMethod("getAddressOfRemoteServer");
            return (URL) method.invoke(executor);
        } catch (NoSuchMethodException e) {
            return readRemoteServerField(executor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve the remote server address", e);
        }
    }

    static URL readRemoteServerField(CommandExecutor executor) {
        try {
            Field field = HttpCommandExecutor.class.getDeclaredField("remoteServer");
            field.setAccessible(true);
            return (URL) field.get(executor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not resolve the remote server address", e);
        }
    }
}
