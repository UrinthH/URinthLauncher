package net.kdt.pojavlaunch.downloads;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A tiny local stand-in for Modrinth's API and file server, for the tests. */
final class FakeModrinth implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, byte[]> bodies = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    final List<String> userAgents = Collections.synchronizedList(new ArrayList<>());

    FakeModrinth() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void json(String pathAndQuery, String body) {
        bodies.put(pathAndQuery, body.getBytes(StandardCharsets.UTF_8));
    }

    void bytes(String path, byte[] body) {
        bodies.put(path, body);
    }

    void status(String path, int code) {
        statuses.put(path, code);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String key = exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery());
        requests.add(key);
        String agent = exchange.getRequestHeaders().getFirst("User-Agent");
        userAgents.add(agent == null ? "" : agent);
        Integer forced = statuses.get(key);
        if (forced == null) forced = statuses.get(exchange.getRequestURI().getRawPath());
        byte[] body = bodies.get(key);
        if (body == null) body = bodies.get(exchange.getRequestURI().getRawPath());
        int code = forced != null ? forced : body != null ? 200 : 404;
        if (body == null) body = new byte[0];
        exchange.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
