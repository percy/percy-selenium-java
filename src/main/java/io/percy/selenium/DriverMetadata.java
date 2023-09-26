package io.percy.selenium;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Field;
import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;


public class DriverMetadata {

    private String sessionId;
    private WebDriver driver;
    private ConcurrentHashMap<String, String> capabilities = new ConcurrentHashMap<String, String>();
    private final List<String> capsNeeded = new ArrayList<>(Arrays.asList("browserName", "platform", "platformName", "version", "osVersion", "proxy"));
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
            String remoteWebAddress = ((HttpCommandExecutor) executor).getAddressOfRemoteServer().toString();
            Cache.CACHE_MAP.put(key, remoteWebAddress);
        }
        return (String) Cache.CACHE_MAP.get(key);
    }
}
