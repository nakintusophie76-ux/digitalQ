package com.kiu.digitalq.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Remembers the server address and auto-start preference between launches. */
public final class ClientConfig {

    private static final Path FILE = Path.of(System.getProperty("user.home"), ".kiu-digitalq", "client.properties");

    private String serverUrl = "http://localhost:8765";
    private boolean autoStartServer = true;

    public void load() {
        if (!Files.exists(FILE)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            serverUrl = properties.getProperty("server.url", serverUrl);
            autoStartServer = Boolean.parseBoolean(properties.getProperty("server.autoStart", "true"));
        } catch (IOException ignored) {
            // Fall back to the defaults; the connection bar lets the user fix it.
        }
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("server.url", serverUrl);
        properties.setProperty("server.autoStart", String.valueOf(autoStartServer));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "KIU digitalQ client settings");
            }
        } catch (IOException ignored) {
            // Not being able to remember the address is not fatal.
        }
    }

    public String serverUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public boolean autoStartServer() {
        return autoStartServer;
    }

    public void setAutoStartServer(boolean autoStartServer) {
        this.autoStartServer = autoStartServer;
    }
}
