package com.kiu.digitalq.server;

import com.kiu.digitalq.model.Event;
import com.kiu.digitalq.model.Teller;
import com.kiu.digitalq.model.TellerStatus;
import com.kiu.digitalq.model.Ticket;
import com.kiu.digitalq.model.TicketState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * Plain-text persistence for the queue.
 *
 * <p>Each day gets its own file ({@code queue-2026-10-26.tsv}) so the midnight reset
 * is simply a new file, and earlier days remain available as a lightweight history.
 * No database or third-party library is required.</p>
 */
final class Store {

    private static final String CONFIG_FILE = "config.properties";
    private static final String TELLERS_FILE = "tellers.tsv";
    private static final String DEFAULT_ADMIN_HASH = Hashes.sha256("admin123");

    private final Path directory;

    Store(Path directory) {
        this.directory = directory;
    }

    Path directory() {
        return directory;
    }

    boolean hasTellersFile() {
        return Files.exists(directory.resolve(TELLERS_FILE));
    }

    // ---------------------------------------------------------------- config

    Config loadConfig() throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(CONFIG_FILE);
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        String hash = properties.getProperty("admin.password.hash", "").trim();
        if (hash.isEmpty()) {
            hash = DEFAULT_ADMIN_HASH;
        }
        return new Config(hash);
    }

    void saveConfig(Config config) throws IOException {
        Files.createDirectories(directory);
        Properties properties = new Properties();
        properties.setProperty("admin.password.hash", config.adminHash());
        try (BufferedWriter writer = Files.newBufferedWriter(directory.resolve(CONFIG_FILE),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(writer, "KIU digitalQ - server configuration");
        }
    }

    boolean isDefaultAdminPassword(String hash) {
        return DEFAULT_ADMIN_HASH.equals(hash);
    }

    // --------------------------------------------------------------- tellers

    List<Teller> loadTellers() throws IOException {
        List<Teller> tellers = new ArrayList<>();
        Path file = directory.resolve(TELLERS_FILE);
        if (!Files.exists(file)) {
            return tellers;
        }
        for (String line : readLines(file)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 5) {
                continue;
            }
            try {
                Teller teller = new Teller(parts[0], clean(parts[1]), Integer.parseInt(parts[2]), parts[3]);
                teller.enabled = Boolean.parseBoolean(parts[4]);
                if (!teller.enabled) {
                    teller.status = TellerStatus.CLOSED;
                }
                tellers.add(teller);
            } catch (NumberFormatException ignored) {
                // Skip a corrupt row rather than refusing to start.
            }
        }
        return tellers;
    }

    void saveTellers(Collection<Teller> tellers) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# KIU digitalQ tellers\n");
        out.append("# id\tname\tcounter\tpin\tenabled\n");
        for (Teller teller : tellers) {
            out.append(teller.id).append('\t')
                    .append(clean(teller.name)).append('\t')
                    .append(teller.counter).append('\t')
                    .append(teller.pin).append('\t')
                    .append(teller.enabled).append('\n');
        }
        write(directory.resolve(TELLERS_FILE), out.toString());
    }

    // ------------------------------------------------------------------- day

    DayData loadDay(LocalDate day) throws IOException {
        Path file = directory.resolve(dayFile(day));
        if (!Files.exists(file)) {
            return null;
        }
        DayData data = new DayData();
        for (String line : readLines(file)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            switch (parts[0]) {
                case "next" -> {
                    if (parts.length > 1) {
                        data.nextNumber = parseInt(parts[1], 0);
                    }
                }
                case "T" -> {
                    if (parts.length < 9) {
                        continue;
                    }
                    try {
                        int number = Integer.parseInt(parts[1]);
                        String reg = parts[2];
                        LocalDateTime issued = LocalDateTime.parse(parts[5]);
                        Ticket ticket = new Ticket(number, reg, issued);
                        ticket.state = TicketState.parse(parts[3]);
                        ticket.tellerId = "-".equals(parts[4]) || parts[4].isEmpty() ? null : parts[4];
                        ticket.calledAt = parseNullable(parts[6]);
                        ticket.closedAt = parseNullable(parts[7]);
                        ticket.recalls = parseInt(parts[8], 0);
                        data.tickets.add(ticket);
                    } catch (NumberFormatException | DateTimeParseException ignored) {
                        // Ignore a corrupt ticket line.
                    }
                }
                case "E" -> {
                    if (parts.length < 4) {
                        continue;
                    }
                    try {
                        data.events.add(new Event(LocalDateTime.parse(parts[1]), clean(parts[3]), parts[2]));
                    } catch (DateTimeParseException ignored) {
                        // Ignore a corrupt event line.
                    }
                }
                default -> {
                    // Unknown row, ignore forward compatibility noise.
                }
            }
        }
        return data;
    }

    void saveDay(LocalDate day, DayData data) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# KIU digitalQ queue for ").append(day).append('\n');
        out.append("next\t").append(data.nextNumber).append('\n');
        for (Ticket ticket : data.tickets) {
            out.append("T\t").append(ticket.number).append('\t')
                    .append(ticket.regNo).append('\t')
                    .append(ticket.state.name()).append('\t')
                    .append(ticket.tellerId == null ? "-" : ticket.tellerId).append('\t')
                    .append(ticket.issuedAt).append('\t')
                    .append(ticket.calledAt == null ? "-" : ticket.calledAt).append('\t')
                    .append(ticket.closedAt == null ? "-" : ticket.closedAt).append('\t')
                    .append(ticket.recalls).append('\n');
        }
        for (Event event : data.events) {
            out.append("E\t").append(event.at).append('\t')
                    .append(clean(event.kind)).append('\t')
                    .append(clean(event.text)).append('\n');
        }
        write(directory.resolve(dayFile(day)), out.toString());
    }

    private String dayFile(LocalDate day) {
        return "queue-" + day + ".tsv";
    }

    // --------------------------------------------------------------- helpers

    private List<String> readLines(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (!lines.isEmpty() && lines.get(0).startsWith("\uFEFF")) {
            lines.set(0, lines.get(0).substring(1));
        }
        return lines;
    }

    private void write(Path file, String content) throws IOException {
        Files.createDirectories(directory);
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static LocalDateTime parseNullable(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    record Config(String adminHash) {
    }

    static final class DayData {
        int nextNumber;
        List<Ticket> tickets = new ArrayList<>();
        List<Event> events = new ArrayList<>();
    }
}
