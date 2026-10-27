package com.kiu.digitalq.server;

import com.kiu.digitalq.json.Json;
import com.kiu.digitalq.json.Json.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Embedded HTTP server. A single lightweight process owns the queue; the Swing
 * clients (kiosk, teller terminals, display, admin console) talk to it over the
 * local network, so they can run on different computers.
 */
public final class QueueServer {

    private final int port;
    private final QueueEngine engine;
    private HttpServer httpServer;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;

    public QueueServer(int port, Path dataDirectory) {
        this.port = port;
        this.engine = new QueueEngine(dataDirectory, port);
    }

    public QueueEngine engine() {
        return engine;
    }

    public int port() {
        return port;
    }

    public synchronized void start() throws IOException {
        if (httpServer != null) {
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        executor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "kiuq-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        httpServer = server;

        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kiuq-midnight");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                engine.tick();
            } catch (RuntimeException ex) {
                System.err.println("[KIU digitalQ] Scheduler error: " + ex.getMessage());
            }
        }, 20, 20, TimeUnit.SECONDS);

        System.out.println("[KIU digitalQ] Server started on port " + port);
        System.out.println("[KIU digitalQ] Data folder: " + engine.dataDirectory());
        for (String url : lanUrls(port)) {
            System.out.println("[KIU digitalQ] Reachable at " + url);
        }
    }

    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public String baseUrl() {
        return "http://localhost:" + port;
    }

    // --------------------------------------------------------------- handlers

    private void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                addCommonHeaders(exchange.getResponseHeaders());
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("/".equals(path) || "/index.html".equals(path)) {
                send(exchange, 200, "text/html; charset=utf-8", STATUS_PAGE.getBytes(StandardCharsets.UTF_8), method);
                return;
            }
            if ("/favicon.ico".equals(path)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            Map<String, String> params = readParams(exchange);
            JsonObject result = route(path, method, params);
            if (result == null) {
                send(exchange, 404, "application/json; charset=utf-8",
                        Json.write(Json.object().put("ok", false).put("error", "Unknown endpoint."))
                                .getBytes(StandardCharsets.UTF_8), method);
                return;
            }
            send(exchange, 200, "application/json; charset=utf-8",
                    Json.write(result).getBytes(StandardCharsets.UTF_8), method);
        } catch (QueueException ex) {
            sendQuietly(exchange, 200, Json.object().put("ok", false).put("error", ex.getMessage()));
        } catch (Exception ex) {
            System.err.println("[KIU digitalQ] Request failed: " + ex);
            sendQuietly(exchange, 500, Json.object().put("ok", false)
                    .put("error", "Server error: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
        } finally {
            exchange.close();
        }
    }

    private JsonObject route(String path, String method, Map<String, String> p) {
        switch (path) {
            case "/api/ping":
                return engine.ping();
            case "/api/display":
                return engine.displayState();
            case "/api/teller/list":
                return engine.publicTellers();
            case "/api/student/status":
                return engine.studentStatus(p.get("reg"), p.get("token"));
            case "/api/student/register":
                requirePost(method);
                return engine.register(p.get("reg"));
            case "/api/teller/login":
                requirePost(method);
                return engine.tellerLogin(p.get("teller"), p.get("pin"));
            case "/api/teller/logout":
                requirePost(method);
                return engine.tellerLogout(p.get("t"));
            case "/api/teller/state":
                return engine.tellerState(p.get("t"));
            case "/api/teller/status":
                requirePost(method);
                return engine.setTellerStatus(p.get("t"), p.get("status"));
            case "/api/teller/finish":
                requirePost(method);
                return engine.finishTeller(p.get("t"));
            case "/api/teller/noshow":
                requirePost(method);
                return engine.noShowTeller(p.get("t"));
            case "/api/teller/recall":
                requirePost(method);
                return engine.recallTeller(p.get("t"));
            case "/api/admin/login":
                requirePost(method);
                return engine.adminLogin(p.get("password"));
            case "/api/admin/logout":
                requirePost(method);
                return engine.adminLogout(p.get("t"));
            case "/api/admin/state":
                return engine.adminState(p.get("t"));
            case "/api/admin/teller/create":
                requirePost(method);
                return engine.adminCreateTeller(p.get("t"), p.get("name"), p.get("counter"),
                        p.get("pin"), p.get("enabled"));
            case "/api/admin/teller/update":
                requirePost(method);
                return engine.adminUpdateTeller(p.get("t"), p.get("id"), p.get("name"),
                        p.get("counter"), p.get("pin"), p.get("enabled"));
            case "/api/admin/teller/delete":
                requirePost(method);
                return engine.adminDeleteTeller(p.get("t"), p.get("id"));
            case "/api/admin/teller/status":
                requirePost(method);
                return engine.adminSetTellerStatus(p.get("t"), p.get("id"), p.get("status"));
            case "/api/admin/reset":
                requirePost(method);
                return engine.adminResetQueue(p.get("t"));
            case "/api/admin/password":
                requirePost(method);
                return engine.adminChangePassword(p.get("t"), p.get("oldPassword"), p.get("newPassword"));
            default:
                return null;
        }
    }

    private static void requirePost(String method) {
        if (!"POST".equalsIgnoreCase(method)) {
            throw new QueueException("This endpoint requires POST.");
        }
    }

    private Map<String, String> readParams(HttpExchange exchange) throws IOException {
        Map<String, String> params = new HashMap<>();
        parseForm(exchange.getRequestURI().getRawQuery(), params);
        byte[] body = exchange.getRequestBody().readNBytes(64 * 1024);
        if (body.length > 0) {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
                parseForm(new String(body, StandardCharsets.UTF_8), params);
            }
        }
        return params;
    }

    private static void parseForm(String form, Map<String, String> target) {
        if (form == null || form.isBlank()) {
            return;
        }
        for (String pair : form.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            try {
                target.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                        URLDecoder.decode(value, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed parameter.
            }
        }
    }

    private void sendQuietly(HttpExchange exchange, int status, JsonObject body) {
        try {
            send(exchange, status, "application/json; charset=utf-8",
                    Json.write(body).getBytes(StandardCharsets.UTF_8), exchange.getRequestMethod());
        } catch (IOException ignored) {
            // Client went away.
        }
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body, String method)
            throws IOException {
        Headers headers = exchange.getResponseHeaders();
        addCommonHeaders(headers);
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store, max-age=0");
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void addCommonHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        headers.set("X-Content-Type-Options", "nosniff");
    }

    // --------------------------------------------------------------- network

    public static List<String> lanUrls(int port) {
        List<String> urls = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    InetAddress inet = address.getAddress();
                    if (inet instanceof Inet4Address && !inet.isLoopbackAddress() && !inet.isLinkLocalAddress()) {
                        urls.add("http://" + inet.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (SocketException ignored) {
            // Not fatal - the server still works on localhost.
        }
        return urls;
    }

    private static final String STATUS_PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>KIU digitalQ · server</title>
            <style>
              :root { color-scheme: light; }
              * { box-sizing: border-box; }
              body { margin: 0; font-family: "Segoe UI", system-ui, sans-serif;
                     background: #f2f5fa; color: #14213a; }
              header { background: #0b2b4c; color: #fff; padding: 22px 28px;
                       display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
              .mark { width: 46px; height: 46px; border-radius: 13px; background: #f5b301;
                      color: #0b2b4c; font-weight: 800; display: grid; place-items: center;
                      font-size: 14px; letter-spacing: .5px; }
              header h1 { margin: 0; font-size: 22px; }
              header p { margin: 3px 0 0; color: #b9c8dc; font-size: 13px; }
              .clock { margin-left: auto; text-align: right; }
              .clock strong { font-size: 24px; display: block; letter-spacing: 1px; }
              .clock span { color: #b9c8dc; font-size: 12px; }
              main { max-width: 1040px; margin: 0 auto; padding: 24px; }
              .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
                       gap: 14px; margin-bottom: 20px; }
              .stat, .panel { background: #fff; border: 1px solid #e2e8f1; border-radius: 16px;
                              padding: 18px; box-shadow: 0 6px 20px rgba(11, 43, 76, .05); }
              .stat span { color: #6b7a90; font-size: 12px; text-transform: uppercase;
                           letter-spacing: .08em; font-weight: 600; }
              .stat strong { display: block; font-size: 30px; margin-top: 6px; }
              .panel h2 { margin: 0 0 14px; font-size: 16px; }
              .teller { display: grid; grid-template-columns: 92px 1fr auto; gap: 12px;
                        align-items: center; padding: 12px 0; border-top: 1px solid #edf1f6; }
              .teller:first-of-type { border-top: 0; }
              .token { font-size: 26px; font-weight: 800; letter-spacing: 1px; color: #0b2b4c; }
              .who { font-weight: 600; }
              .who small { display: block; color: #6b7a90; font-weight: 500; margin-top: 2px; }
              .pill { padding: 5px 10px; border-radius: 999px; font-size: 11px; font-weight: 700;
                      background: #eef2f8; color: #4b5c73; }
              .pill.serving { background: #e5f6ef; color: #0d7a4f; }
              .pill.available { background: #e8f7ef; color: #18a06a; }
              .pill.resting { background: #fdf3dd; color: #a97400; }
              .pill.not_around { background: #fdeaea; color: #b53a3a; }
              .waiting { display: flex; flex-wrap: wrap; gap: 8px; }
              .chip { font-weight: 800; font-size: 20px; background: #f5b301; color: #0b2b4c;
                      border-radius: 12px; padding: 8px 12px; }
              .muted { color: #6b7a90; font-size: 14px; }
              footer { color: #6b7a90; font-size: 12px; padding: 0 24px 28px; max-width: 1040px;
                       margin: 0 auto; line-height: 1.6; }
              code { background: #e9eef6; padding: 1px 5px; border-radius: 4px; }
            </style>
            </head>
            <body>
            <header>
              <div class="mark">KIU</div>
              <div>
                <h1>KIU digitalQ</h1>
                <p>Finance Department queue server &middot; view-only board</p>
              </div>
              <div class="clock"><strong id="clock">--:--</strong><span id="day">connecting&hellip;</span></div>
            </header>
            <main>
              <div class="stats">
                <div class="stat"><span>Now serving</span><strong id="serving">0</strong></div>
                <div class="stat"><span>Waiting</span><strong id="waiting">0</strong></div>
                <div class="stat"><span>Served today</span><strong id="served">0</strong></div>
                <div class="stat"><span>Last token</span><strong id="last">—</strong></div>
              </div>
              <div class="panel" style="margin-bottom:20px">
                <h2>Counters</h2>
                <div id="tellers"><p class="muted">Loading&hellip;</p></div>
              </div>
              <div class="panel">
                <h2>Waiting room</h2>
                <div id="waitlist" class="waiting"><span class="muted">Loading&hellip;</span></div>
              </div>
            </main>
            <footer>
              This page is served by the KIU digitalQ Java server. The queue restarts at
              <strong>midnight</strong> (tokens 000&ndash;999). Teller terminals, the student kiosk and the
              full display are available in the desktop application.
              API: <code>/api/ping</code>, <code>/api/display</code>.
            </footer>
            <script>
              const byId = (id) => document.getElementById(id);
              async function refresh() {
                try {
                  const response = await fetch('/api/display', { cache: 'no-store' });
                  const data = await response.json();
                  byId('clock').textContent = data.time;
                  byId('day').textContent = data.dayLabel;
                  byId('waiting').textContent = data.waitingCount;
                  byId('serving').textContent = (data.tellers || []).filter(t => t.serving).length;
                  byId('served').textContent = data.servedToday;
                  byId('last').textContent = data.lastToken || '—';
                  const box = byId('tellers');
                  box.replaceChildren();
                  (data.tellers || []).forEach(t => {
                    const row = document.createElement('div');
                    row.className = 'teller';
                    const token = document.createElement('div');
                    token.className = 'token';
                    token.textContent = t.serving ? t.currentToken : '—';
                    const who = document.createElement('div');
                    who.className = 'who';
                    who.textContent = t.name;
                    const small = document.createElement('small');
                    small.textContent = 'Counter ' + t.counter + ' · served ' + t.servedToday;
                    who.appendChild(small);
                    const pill = document.createElement('span');
                    pill.className = 'pill ' + String(t.status).toLowerCase();
                    pill.textContent = t.statusLabel;
                    row.append(token, who, pill);
                    box.appendChild(row);
                  });
                  if (!(data.tellers || []).length) {
                    const empty = document.createElement('p');
                    empty.className = 'muted';
                    empty.textContent = 'No tellers have been created yet.';
                    box.appendChild(empty);
                  }
                  const list = byId('waitlist');
                  list.replaceChildren();
                  (data.waiting || []).forEach(w => {
                    const chip = document.createElement('span');
                    chip.className = 'chip';
                    chip.textContent = w.token;
                    list.appendChild(chip);
                  });
                  if (!(data.waiting || []).length) {
                    const empty = document.createElement('span');
                    empty.className = 'muted';
                    empty.textContent = 'No students waiting.';
                    list.appendChild(empty);
                  }
                } catch (error) {
                  byId('day').textContent = 'server not reachable';
                }
              }
              refresh();
              setInterval(refresh, 2000);
            </script>
            </body>
            </html>
            """;
}
