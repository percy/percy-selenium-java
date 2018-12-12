package io.percy.selenium;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * HTTP server that serves the static files that make our test app.
 */
class TestServer {
    // Server port.
    private static final Integer PORT = 8000;

    // Location for static files in our test app.
    private static final String TESTAPP_DIR = "src/test/resources/testapp/";
    private static final String INDEX_FILE = "index.html";

    // Recognized Mime type map (extension -> mimetype)
    private static final Map<String, String> MIME_MAP = new HashMap<String, String>();
    static {
        MIME_MAP.put("html", "text/html");
        MIME_MAP.put("js", "application/javascript");
        MIME_MAP.put("css", "text/css");
    }

    private static ExecutorService executor;
    private static HttpServer server;

    public static HttpServer startServer() throws IOException {
        executor = Executors.newFixedThreadPool(1);
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        HttpContext context = server.createContext("/");
        context.setHandler(TestServer::handleRequest);
        server.setExecutor(executor);
        server.start();
        return server;
    }

    public static void shutdown() {
        if (server != null) {
            server.stop(1);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        String requestedPath = exchange.getRequestURI().getPath();
        if (requestedPath.equals("/")) {
            serveStaticFile(exchange, INDEX_FILE);
        } else {
            if (requestedPath.startsWith("/")) {
                requestedPath = requestedPath.substring(1);
            }
            serveStaticFile(exchange, requestedPath);
        }
    }

    private static void serveStaticFile(HttpExchange exchange, String resourcePath) throws IOException {
        byte[] response;
        int responseCode;
        File file = new File(String.format("%s/%s", TESTAPP_DIR, resourcePath));
        if (!file.canRead()) {
            response = "404 - File Not Found".getBytes();
            responseCode = 404;
        } else {
            InputStream in = new FileInputStream(file);
            response = new byte[in.available()];
            in.read(response);
            responseCode = 200;
            in.close();
        }

        exchange.getResponseHeaders().add("Content-Type", getMimeType(resourcePath));
        exchange.sendResponseHeaders(responseCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private static String getMimeType(String resourcePath) {
        int lastDotIndex = resourcePath.lastIndexOf('.');
        String extension = lastDotIndex > 0 ? resourcePath.substring(lastDotIndex + 1) : "";
        return MIME_MAP.getOrDefault(extension, "text/plain");
    }
}
