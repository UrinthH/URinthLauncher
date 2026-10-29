package net.kdt.pojavlaunch.downloads;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny local stand-in for Modrinth's API and file server, for the tests. Plain sockets, because
 * the JDK's own web server classes are not on the unit-test classpath of an Android module.
 * One request per connection, answered with "Connection: close".
 */
final class FakeModrinth implements AutoCloseable {
    private final ServerSocket socket;
    private final Thread acceptor;
    private final Map<String, byte[]> bodies = Collections.synchronizedMap(new HashMap<String, byte[]>());
    private final Map<String, Integer> statuses = Collections.synchronizedMap(new HashMap<String, Integer>());
    final List<String> requests = Collections.synchronizedList(new ArrayList<String>());
    final List<String> userAgents = Collections.synchronizedList(new ArrayList<String>());
    private volatile boolean closed;

    FakeModrinth() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!closed) {
                    try {
                        final Socket client = socket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    handle(client);
                                } catch (IOException ignored) {
                                    // the client went away
                                } finally {
                                    try {
                                        client.close();
                                    } catch (IOException ignored) {
                                        // closing anyway
                                    }
                                }
                            }
                        }).start();
                    } catch (IOException e) {
                        return; // closed
                    }
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    String base() {
        return "http://127.0.0.1:" + socket.getLocalPort();
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

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) != -1) {
            if (value == '\n') break;
            if (value != '\r') line.write(value);
        }
        if (value == -1 && line.size() == 0) return null;
        return new String(line.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private void handle(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        String requestLine = readLine(in);
        if (requestLine == null) return;
        String[] parts = requestLine.split(" ");
        String key = parts.length > 1 ? parts[1] : "/";
        String path = key.contains("?") ? key.substring(0, key.indexOf('?')) : key;
        String agent = "";
        int contentLength = 0;
        String header;
        while ((header = readLine(in)) != null && !header.isEmpty()) {
            String lower = header.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith("user-agent:")) agent = header.substring(11).trim();
            if (lower.startsWith("content-length:")) contentLength = Integer.parseInt(header.substring(15).trim());
        }
        for (int i = 0; i < contentLength; i++) {
            if (in.read() == -1) break; // the body of a POST is not needed
        }
        requests.add(key);
        userAgents.add(agent);
        Integer forced = statuses.get(key);
        if (forced == null) forced = statuses.get(path);
        byte[] body = bodies.get(key);
        if (body == null) body = bodies.get(path);
        int code = forced != null ? forced : body != null ? 200 : 404;
        if (body == null) body = new byte[0];
        OutputStream out = client.getOutputStream();
        out.write(("HTTP/1.1 " + code + " X\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
