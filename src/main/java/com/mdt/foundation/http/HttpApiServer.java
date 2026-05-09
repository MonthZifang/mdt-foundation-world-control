package com.mdt.foundation.http;

import com.mdt.foundation.api.ActionRequest;
import com.mdt.foundation.api.ActionResult;
import com.mdt.foundation.api.FoundationWorldControlApi;
import com.mdt.foundation.config.PluginConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class HttpApiServer implements AutoCloseable {
    private final PluginConfiguration configuration;
    private final FoundationWorldControlApi api;
    private HttpServer server;
    private ExecutorService executorService;

    public HttpApiServer(PluginConfiguration configuration, FoundationWorldControlApi api) {
        this.configuration = configuration;
        this.api = api;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(configuration.getApiHost(), configuration.getApiPort()), 0);
        executorService = Executors.newCachedThreadPool(new ThreadFactory() {
            private int index;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "mdt-foundation-http-" + index++);
                thread.setDaemon(true);
                return thread;
            }
        });
        server.setExecutor(executorService);
        server.createContext("/api/v1/health", new HealthHandler());
        server.createContext("/api/v1/world/execute", new ExecuteHandler());
        server.start();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"method_not_allowed\"}");
                return;
            }
            sendJson(exchange, 200, "{\"success\":true,\"status\":\"ok\"}");
        }
    }

    private final class ExecuteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                sendJson(exchange, 401, "{\"success\":false,\"message\":\"unauthorized\"}");
                return;
            }

            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, "{\"success\":false,\"message\":\"method_not_allowed\"}");
                return;
            }

            Map<String, String> parameters = new LinkedHashMap<String, String>();
            parameters.putAll(parseQuery(exchange.getRequestURI().getRawQuery()));
            if ("POST".equalsIgnoreCase(method)) {
                parameters.putAll(parseQuery(readBody(exchange)));
            }

            String operation = parameters.remove("operation");
            ActionResult result;
            try {
                result = api.execute(ActionRequest.of(operation, parameters));
            } catch (Exception exception) {
                sendJson(exchange, 500, "{\"success\":false,\"message\":\"" + escape(exception.getMessage()) + "\"}");
                return;
            }

            int status = result.isSuccess() ? 200 : 400;
            sendJson(exchange, status, toJson(result));
        }
    }

    private boolean authorize(HttpExchange exchange) {
        if (!configuration.isApiRequireToken()) {
            return true;
        }

        String token = exchange.getRequestHeaders().getFirst("X-Api-Token");
        if (token == null || token.isEmpty()) {
            token = parseQuery(exchange.getRequestURI().getRawQuery()).get("token");
        }
        return configuration.getApiToken().equals(token);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream inputStream = exchange.getRequestBody();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return values;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] split = pair.split("=", 2);
            String key = decode(split[0]);
            String value = split.length > 1 ? decode(split[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 decode failed", exception);
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String toJson(ActionResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"success\":").append(result.isSuccess());
        builder.append(",\"operation\":\"").append(escape(result.getOperation())).append("\"");
        builder.append(",\"message\":\"").append(escape(result.getMessage())).append("\"");
        builder.append(",\"data\":").append(mapToJson(result.getData()));
        builder.append("}");
        return builder.toString();
    }

    private String mapToJson(Map<String, Object> values) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            first = false;
            builder.append("\"").append(escape(entry.getKey())).append("\":");
            builder.append(valueToJson(entry.getValue()));
        }
        builder.append("}");
        return builder.toString();
    }

    private String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Iterable) {
            StringBuilder builder = new StringBuilder();
            builder.append("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    builder.append(",");
                }
                first = false;
                builder.append(valueToJson(item));
            }
            builder.append("]");
            return builder.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }
}
