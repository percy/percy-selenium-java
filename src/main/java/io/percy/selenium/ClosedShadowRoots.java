package io.percy.selenium;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Fills {@code window.__percyClosedShadowRoots} (host element -> closed ShadowRoot), which
 * PercyDOM.serialize reads to capture closed shadow trees. Strategies, first available wins:
 * Chromium CDP, then WebDriver BiDi (only if the session already has webSocketUrl), then
 * WebDriver classic Get Element Shadow Root (local sessions only, capped).
 */
final class ClosedShadowRoots {
    static final String CLASSIC_REMOTE_SKIP_MESSAGE =
        "closed shadow roots skipped on remote session; start the session with webSocketUrl: true or set PERCY_CLOSED_SHADOW_CLASSIC=true";

    static boolean forceClassic = Boolean.parseBoolean(
        System.getenv().getOrDefault("PERCY_CLOSED_SHADOW_CLASSIC", "false").trim().toLowerCase());
    static int maxHosts = parseMaxHosts(System.getenv("PERCY_CLOSED_SHADOW_MAX_HOSTS"));

    private static final Set<String> REMOTE_SKIP_LOGGED = ConcurrentHashMap.newKeySet();

    private static final String SET_JS =
        "function(shadowRoot) { window.__percyClosedShadowRoots = window.__percyClosedShadowRoots || new WeakMap();"
        + " window.__percyClosedShadowRoots.set(this, shadowRoot); }";

    // Custom elements whose shadowRoot reads null are the only hosts that can hide a closed root;
    // open roots are walked so closed roots nested inside them are found too.
    private static final String CANDIDATES_JS =
        "const root = arguments[0] || document; const out = [];"
        + " const walk = r => { for (const e of r.querySelectorAll('*')) {"
        + " if (e.shadowRoot) walk(e.shadowRoot); else if (e.localName.includes('-')) out.push(e); } };"
        + " walk(root); return out;";

    private static final String CLASSIC_SET_JS =
        "(window.__percyClosedShadowRoots = window.__percyClosedShadowRoots || new WeakMap()).set(arguments[0], arguments[1]);";

    private static final String BIDI_LIST_FN =
        "(...roots) => { const out = []; const walk = r => { for (const e of r.querySelectorAll('*')) {"
        + " if (e.shadowRoot) walk(e.shadowRoot); else if (e.localName.includes('-')) out.push(e); } };"
        + " for (const r of (roots.length ? roots : [document])) walk(r); return out; }";

    private static final String BIDI_SET_FN =
        "(pairs) => { const m = window.__percyClosedShadowRoots = window.__percyClosedShadowRoots || new WeakMap();"
        + " for (const [h, s] of pairs) m.set(h, s); return pairs.length; }";

    private static final int BIDI_MAX_DEPTH = 10;

    private ClosedShadowRoots() {}

    static int parseMaxHosts(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : 200;
        } catch (NumberFormatException e) {
            return 200;
        }
    }

    /** Never throws: closed-root exposure is best effort and must not fail a snapshot. */
    static void expose(WebDriver driver) {
        try {
            BrowserSupport.Cdp cdp = BrowserSupport.cdp(driver);
            if (cdp != null && tryCdp(driver, cdp)) {
                return;
            }
            BrowserSupport.BiDi bidi = BrowserSupport.bidi(driver);
            if (bidi != null && tryBiDi(driver, bidi)) {
                return;
            }
            if (!forceClassic && !BrowserSupport.isLocal(driver)) {
                if (REMOTE_SKIP_LOGGED.add(BrowserSupport.sessionKey(driver))) {
                    Percy.log(CLASSIC_REMOTE_SKIP_MESSAGE, "info");
                }
                return;
            }
            exposeClassic(driver);
        } catch (Throwable e) {
            Percy.log("Could not expose closed shadow roots: " + e.getMessage(), "warn");
        }
    }

    @SuppressWarnings("unchecked")
    static boolean tryCdp(WebDriver driver, BrowserSupport.Cdp cdp) {
        try {
            cdp.execute("DOM.enable", new HashMap<>());
            Map<String, Object> params = new HashMap<>();
            params.put("depth", -1);
            params.put("pierce", true);
            Map<String, Object> doc = cdp.execute("DOM.getDocument", params);
            Object root = doc == null ? null : doc.get("root");
            if (!(root instanceof Map)) {
                Percy.log("CDP DOM.getDocument returned no root; skipping closed shadow root capture", "debug");
                return true;
            }
            List<Object[]> pairs = new ArrayList<>();
            collectCdpPairs((Map<String, Object>) root, pairs);
            if (pairs.isEmpty()) {
                return true;
            }
            Percy.log("Found " + pairs.size() + " closed shadow root(s), exposing via CDP", "debug");
            for (Object[] pair : pairs) {
                String hostId = resolveObjectId(cdp, pair[0]);
                String shadowId = resolveObjectId(cdp, pair[1]);
                if (hostId == null || shadowId == null) {
                    continue;
                }
                Map<String, Object> arg = new HashMap<>();
                arg.put("objectId", shadowId);
                Map<String, Object> call = new HashMap<>();
                call.put("functionDeclaration", SET_JS);
                call.put("objectId", hostId);
                call.put("arguments", Collections.singletonList(arg));
                cdp.execute("Runtime.callFunctionOn", call);
            }
            return true;
        } catch (Exception e) {
            Percy.log("Could not expose closed shadow roots via CDP: " + e.getMessage(), "debug");
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectCdpPairs(Map<String, Object> node, List<Object[]> pairs) {
        // Closed roots inside child frame documents live in another execution context.
        if (node.get("contentDocument") != null) {
            return;
        }
        Object shadowRoots = node.get("shadowRoots");
        if (shadowRoots instanceof List) {
            for (Object sr : (List<Object>) shadowRoots) {
                if (!(sr instanceof Map)) continue;
                Map<String, Object> shadow = (Map<String, Object>) sr;
                if ("closed".equals(shadow.get("shadowRootType"))) {
                    pairs.add(new Object[] { node.get("backendNodeId"), shadow.get("backendNodeId") });
                }
                collectCdpPairs(shadow, pairs);
            }
        }
        Object children = node.get("children");
        if (children instanceof List) {
            for (Object child : (List<Object>) children) {
                if (child instanceof Map) collectCdpPairs((Map<String, Object>) child, pairs);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String resolveObjectId(BrowserSupport.Cdp cdp, Object backendNodeId) {
        if (backendNodeId == null) return null;
        Map<String, Object> params = new HashMap<>();
        params.put("backendNodeId", backendNodeId);
        Map<String, Object> resp = cdp.execute("DOM.resolveNode", params);
        Object obj = resp == null ? null : resp.get("object");
        Object id = obj instanceof Map ? ((Map<String, Object>) obj).get("objectId") : null;
        return id == null ? null : id.toString();
    }

    @SuppressWarnings("unchecked")
    static boolean tryBiDi(WebDriver driver, BrowserSupport.BiDi bidi) {
        try {
            String context = driver.getWindowHandle();
            Map<String, Object> serialization = new HashMap<>();
            serialization.put("maxDomDepth", 0);
            serialization.put("includeShadowTree", "all");

            List<Object> roots = new ArrayList<>();
            int exposed = 0;
            for (int depth = 0; depth < BIDI_MAX_DEPTH; depth++) {
                Map<String, Object> params = callParams(context, BIDI_LIST_FN, roots);
                params.put("serializationOptions", serialization);
                params.put("resultOwnership", "none");
                Map<String, Object> res = bidi.send("script.callFunction", params);
                List<Object[]> pairs = closedPairsFromBiDi(res);
                if (pairs.isEmpty()) {
                    break;
                }
                List<Object> pairValues = new ArrayList<>();
                roots = new ArrayList<>();
                for (Object[] pair : pairs) {
                    pairValues.add(arrayValue(Arrays.asList(sharedRef(pair[0]), sharedRef(pair[1]))));
                    roots.add(sharedRef(pair[1]));
                }
                bidi.send("script.callFunction",
                    callParams(context, BIDI_SET_FN, Collections.singletonList(arrayValue(pairValues))));
                exposed += pairs.size();
            }
            if (exposed > 0) {
                Percy.log("Exposed " + exposed + " closed shadow root(s) via BiDi", "debug");
            }
            return true;
        } catch (Exception e) {
            Percy.log("Could not expose closed shadow roots via BiDi: " + e.getMessage(), "debug");
            return false;
        }
    }

    private static Map<String, Object> callParams(String context, String fn, List<Object> args) {
        Map<String, Object> target = new HashMap<>();
        target.put("context", context);
        Map<String, Object> params = new HashMap<>();
        params.put("functionDeclaration", fn);
        params.put("awaitPromise", false);
        params.put("target", target);
        params.put("arguments", args);
        return params;
    }

    private static Map<String, Object> sharedRef(Object sharedId) {
        Map<String, Object> ref = new HashMap<>();
        ref.put("sharedId", sharedId);
        return ref;
    }

    private static Map<String, Object> arrayValue(List<Object> values) {
        Map<String, Object> arr = new HashMap<>();
        arr.put("type", "array");
        arr.put("value", values);
        return arr;
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> closedPairsFromBiDi(Map<String, Object> response) {
        List<Object[]> pairs = new ArrayList<>();
        Object result = response == null ? null : response.get("result");
        Object items = result instanceof Map ? ((Map<String, Object>) result).get("value") : null;
        if (!(items instanceof List)) {
            return pairs;
        }
        for (Object item : (List<Object>) items) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> node = (Map<String, Object>) item;
            Object value = node.get("value");
            Object shadow = value instanceof Map ? ((Map<String, Object>) value).get("shadowRoot") : null;
            if (!(shadow instanceof Map)) continue;
            Map<String, Object> shadowNode = (Map<String, Object>) shadow;
            Object shadowValue = shadowNode.get("value");
            Object mode = shadowValue instanceof Map ? ((Map<String, Object>) shadowValue).get("mode") : null;
            if ("closed".equals(mode) && node.get("sharedId") != null && shadowNode.get("sharedId") != null) {
                pairs.add(new Object[] { node.get("sharedId"), shadowNode.get("sharedId") });
            }
        }
        return pairs;
    }

    @SuppressWarnings("unchecked")
    static int exposeClassic(WebDriver driver) {
        JavascriptExecutor jse = (JavascriptExecutor) driver;
        Deque<SearchContext> queue = new ArrayDeque<>();
        int checked = 0;
        int exposed = 0;
        boolean first = true;
        while (first || !queue.isEmpty()) {
            SearchContext scope = first ? null : queue.pop();
            first = false;
            Object listed = scope == null ? jse.executeScript(CANDIDATES_JS) : jse.executeScript(CANDIDATES_JS, scope);
            if (!(listed instanceof List)) {
                continue;
            }
            for (Object candidate : (List<Object>) listed) {
                if (!(candidate instanceof WebElement)) continue;
                if (checked >= maxHosts) {
                    Percy.log("Closed shadow root host cap of " + maxHosts
                        + " reached; set PERCY_CLOSED_SHADOW_MAX_HOSTS to raise it", "debug");
                    return exposed;
                }
                checked++;
                SearchContext root;
                try {
                    root = ((WebElement) candidate).getShadowRoot();
                } catch (Exception e) {
                    continue;
                }
                // Safari answers null instead of a no-such-shadow-root error for closed roots.
                if (root == null) continue;
                jse.executeScript(CLASSIC_SET_JS, candidate, root);
                exposed++;
                queue.push(root);
            }
        }
        if (exposed > 0) {
            Percy.log("Exposed " + exposed + " closed shadow root(s) via WebDriver classic", "debug");
        }
        return exposed;
    }
}
