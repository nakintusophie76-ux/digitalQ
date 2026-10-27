package com.kiu.digitalq.server;

import com.kiu.digitalq.json.Json;
import com.kiu.digitalq.json.Json.JsonArray;
import com.kiu.digitalq.json.Json.JsonObject;
import com.kiu.digitalq.model.Event;
import com.kiu.digitalq.model.Teller;
import com.kiu.digitalq.model.TellerStatus;
import com.kiu.digitalq.model.Ticket;
import com.kiu.digitalq.model.TicketState;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The single source of truth for the queue.
 *
 * <p>All public methods are synchronized: the HTTP server may call them from several
 * worker threads, while teller terminals and the waiting-room display poll snapshots.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Tokens are issued in order from {@code 000} to {@code 999}, one per student per day.</li>
 *   <li>At midnight the queue rolls over: tokens restart at {@code 000}.</li>
 *   <li>Students are assigned automatically to the least-busy teller who is ready.</li>
 *   <li>When a teller finishes a student, the next waiting student is assigned immediately.</li>
 * </ul>
 */
public final class QueueEngine {

    public static final String APP_NAME = "KIU digitalQ";
    public static final String VERSION = "1.0";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private static final int MAX_TOKEN = 999;
    private static final int MAX_EVENTS = 200;
    private static final long TELLER_SESSION_MILLIS = Duration.ofHours(12).toMillis();
    private static final long ADMIN_SESSION_MILLIS = Duration.ofHours(8).toMillis();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_LONG =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private final Store store;
    private final int port;
    private final Object lock = new Object();
    private final LinkedHashMap<String, Teller> tellers = new LinkedHashMap<>();
    private final TreeMap<Integer, Ticket> tickets = new TreeMap<>();
    private final HashMap<String, Integer> regIndex = new HashMap<>();
    private final Deque<Event> events = new ArrayDeque<>();
    private final HashMap<String, Session> sessions = new HashMap<>();

    private LocalDate day = LocalDate.now();
    private int nextNumber;
    private long assignSeq;
    private String adminHash;

    public QueueEngine(Path dataDirectory, int port) {
        this.store = new Store(dataDirectory);
        this.port = port;
        Store.Config config;
        try {
            config = store.loadConfig();
            store.saveConfig(config);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the data folder " + dataDirectory.toAbsolutePath(), ex);
        }
        this.adminHash = config.adminHash();

        try {
            List<Teller> loadedTellers = store.loadTellers();
            if (loadedTellers.isEmpty() && !store.hasTellersFile()) {
                seedTellers();
                store.saveTellers(tellers.values());
            } else {
                for (Teller teller : loadedTellers) {
                    tellers.put(teller.id, teller);
                }
            }

            Store.DayData data = store.loadDay(day);
            if (data != null) {
                nextNumber = Math.max(0, Math.min(data.nextNumber, MAX_TOKEN + 1));
                for (Ticket ticket : data.tickets) {
                    tickets.put(ticket.number, ticket);
                    regIndex.put(ticket.regNo, ticket.number);
                }
                for (Event event : data.events) {
                    if (events.size() >= MAX_EVENTS) {
                        events.removeFirst();
                    }
                    events.addLast(event);
                }
            } else {
                addEvent("Queue opened for " + day.format(DATE_LONG), "RESET");
            }

            rebuildAfterLoad();
            if (dispatch()) {
                // Assignments made during restart are saved below.
            }
            saveAll();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read queue data from " + dataDirectory.toAbsolutePath(), ex);
        }
    }

    // ------------------------------------------------------------- public API

    public int port() {
        return port;
    }

    public Path dataDirectory() {
        return store.directory().toAbsolutePath();
    }

    public synchronized JsonObject ping() {
        rolloverIfNeeded();
        JsonObject response = envelope();
        response.put("server", APP_NAME + " server")
                .put("tellers", tellers.size())
                .put("serving", countState(TicketState.SERVING))
                .put("waiting", countState(TicketState.WAITING))
                .put("port", port);
        return response;
    }

    public synchronized JsonObject displayState() {
        rolloverIfNeeded();
        JsonObject response = envelope();
        JsonArray waiting = new JsonArray();
        int position = 0;
        for (Ticket ticket : tickets.values()) {
            if (ticket.state == TicketState.WAITING) {
                position++;
                if (position <= 60) {
                    waiting.add(Json.object()
                            .put("token", ticket.token())
                            .put("position", position));
                }
            }
        }
        JsonArray tellerArray = new JsonArray();
        for (Teller teller : sortedTellers()) {
            tellerArray.add(tellerJson(teller));
        }
        response.put("waiting", waiting)
                .put("tellers", tellerArray)
                .put("events", eventsJson(6))
                .put("servedToday", servedToday())
                .put("avgServiceMinutes", Math.max(1, (int) Math.round(averageServiceSeconds() / 60.0)))
                .put("nextToken", nextNumber <= MAX_TOKEN ? token(nextNumber) : "---")
                .put("dayLabel", day.format(DATE_LONG));
        return response;
    }

    public synchronized JsonObject publicTellers() {
        rolloverIfNeeded();
        JsonObject response = envelope();
        JsonArray array = new JsonArray();
        for (Teller teller : sortedTellers()) {
            array.add(Json.object()
                    .put("id", teller.id)
                    .put("name", teller.name)
                    .put("counter", teller.counter)
                    .put("enabled", teller.enabled)
                    .put("statusLabel", teller.status.label()));
        }
        response.put("tellers", array);
        return response;
    }

    /**
     * Issues a ticket (or returns today's existing ticket) for a five-digit registration number.
     */
    public synchronized JsonObject register(String registrationNumber) {
        rolloverIfNeeded();
        String reg = normaliseRegistration(registrationNumber);
        if (!reg.matches("\\d{5}")) {
            throw new QueueException("Enter your 5-digit student registration number, for example 20541.");
        }
        Integer existing = regIndex.get(reg);
        if (existing != null) {
            Ticket ticket = tickets.get(existing);
            if (ticket != null) {
                return studentTicketJson(ticket, true);
            }
        }
        if (nextNumber > MAX_TOKEN) {
            throw new QueueException("Today's queue is full (1000 tickets). Please speak to the finance office.");
        }
        Ticket ticket = new Ticket(nextNumber++, reg, LocalDateTime.now());
        tickets.put(ticket.number, ticket);
        regIndex.put(reg, ticket.number);
        addEvent("Ticket " + ticket.token() + " issued to " + reg, "ISSUE");
        dispatch();
        saveAll();
        return studentTicketJson(ticket, false);
    }

    public synchronized JsonObject studentStatus(String registrationNumber, String tokenValue) {
        rolloverIfNeeded();
        Ticket ticket = null;
        if (registrationNumber != null && !registrationNumber.isBlank()) {
            Integer number = regIndex.get(normaliseRegistration(registrationNumber));
            if (number != null) {
                ticket = tickets.get(number);
            }
        } else if (tokenValue != null && !tokenValue.isBlank()) {
            try {
                ticket = tickets.get(Integer.parseInt(tokenValue.trim()));
            } catch (NumberFormatException ignored) {
                // Not a numeric token.
            }
        }
        if (ticket == null) {
            JsonObject response = envelope();
            response.put("found", false)
                    .put("message", "No ticket was found for today. Enter your registration number to get one.");
            return response;
        }
        return studentTicketJson(ticket, true);
    }

    public synchronized JsonObject tellerLogin(String tellerId, String pin) {
        rolloverIfNeeded();
        Teller teller = tellers.get(tellerId);
        if (teller == null) {
            throw new QueueException("That teller does not exist. Ask the administrator to create one.");
        }
        if (!teller.enabled) {
            throw new QueueException("This counter has been disabled by the administrator.");
        }
        if (pin == null || !teller.pin.equals(pin.trim())) {
            throw new QueueException("Incorrect PIN for " + teller.name + ".");
        }
        sessions.entrySet().removeIf(entry -> "teller".equals(entry.getValue().role)
                && teller.id.equals(entry.getValue().tellerId));
        String token = UUID.randomUUID().toString();
        Session session = new Session("teller", teller.id, System.currentTimeMillis() + TELLER_SESSION_MILLIS);
        sessions.put(token, session);
        addEvent(teller.name + " signed in at Counter " + teller.counter, "STATUS");
        saveAll();
        JsonObject response = tellerStateJson(teller);
        response.put("token", token);
        return response;
    }

    public synchronized JsonObject tellerState(String token) {
        rolloverIfNeeded();
        Teller teller = requireTeller(token);
        JsonObject response = tellerStateJson(teller);
        response.put("token", token);
        return response;
    }

    public synchronized JsonObject tellerLogout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
        return Json.object().put("ok", true);
    }

    public synchronized JsonObject setTellerStatus(String token, String statusValue) {
        rolloverIfNeeded();
        Teller teller = requireTeller(token);
        TellerStatus target = TellerStatus.parse(statusValue);
        if (target == null) {
            throw new QueueException("Unknown status.");
        }
        if (target == TellerStatus.SERVING) {
            throw new QueueException("Serving is set automatically when a student is assigned to you.");
        }
        if (teller.currentTicket != null) {
            throw new QueueException("Finish or mark no-show for ticket "
                    + token(teller.currentTicket) + " before changing your status.");
        }
        teller.status = target;
        teller.statusSince = LocalDateTime.now();
        addEvent(teller.name + " is now " + target.label().toLowerCase(Locale.ROOT), "STATUS");
        if (target == TellerStatus.AVAILABLE) {
            dispatch();
        }
        saveAll();
        JsonObject response = tellerStateJson(teller);
        response.put("token", token);
        response.put("message", "Status updated to " + target.label() + ".");
        return response;
    }

    /** Completes the current student and immediately serves the next one, if any. */
    public synchronized JsonObject finishTeller(String token) {
        rolloverIfNeeded();
        Teller teller = requireTeller(token);
        if (teller.currentTicket == null) {
            throw new QueueException("There is no student at your counter right now.");
        }
        Ticket ticket = tickets.get(teller.currentTicket);
        String completedToken = ticket == null ? token(teller.currentTicket) : ticket.token();
        if (ticket != null) {
            ticket.state = TicketState.DONE;
            ticket.closedAt = LocalDateTime.now();
            addEvent("Ticket " + ticket.token() + " completed at " + teller.name, "DONE");
        }
        teller.servedToday++;
        teller.currentTicket = null;
        teller.status = teller.enabled ? TellerStatus.AVAILABLE : TellerStatus.CLOSED;
        teller.statusSince = LocalDateTime.now();
        dispatch();
        saveAll();
        JsonObject response = tellerStateJson(teller);
        response.put("token", token);
        response.put("completedToken", completedToken);
        response.put("message", "Ticket " + completedToken + " completed.");
        return response;
    }

    /** Marks the current student as missed and calls the next one. */
    public synchronized JsonObject noShowTeller(String token) {
        rolloverIfNeeded();
        Teller teller = requireTeller(token);
        if (teller.currentTicket == null) {
            throw new QueueException("There is no student at your counter right now.");
        }
        Ticket ticket = tickets.get(teller.currentTicket);
        String missedToken = ticket == null ? token(teller.currentTicket) : ticket.token();
        if (ticket != null) {
            ticket.state = TicketState.MISSED;
            ticket.closedAt = LocalDateTime.now();
            addEvent("Ticket " + ticket.token() + " marked as no-show at " + teller.name, "MISS");
        }
        teller.currentTicket = null;
        teller.status = teller.enabled ? TellerStatus.AVAILABLE : TellerStatus.CLOSED;
        teller.statusSince = LocalDateTime.now();
        dispatch();
        saveAll();
        JsonObject response = tellerStateJson(teller);
        response.put("token", token);
        response.put("message", "Ticket " + missedToken + " marked as no-show.");
        return response;
    }

    public synchronized JsonObject recallTeller(String token) {
        rolloverIfNeeded();
        Teller teller = requireTeller(token);
        if (teller.currentTicket == null) {
            throw new QueueException("There is no student at your counter right now.");
        }
        Ticket ticket = tickets.get(teller.currentTicket);
        if (ticket != null) {
            ticket.recalls++;
            addEvent("Ticket " + ticket.token() + " called again to " + teller.name, "CALL");
        }
        saveAll();
        JsonObject response = tellerStateJson(teller);
        response.put("token", token);
        response.put("message", "Ticket " + token(teller.currentTicket) + " called again.");
        return response;
    }

    // ---------------------------------------------------------------- admin

    public synchronized JsonObject adminLogin(String password) {
        rolloverIfNeeded();
        if (password == null || !Hashes.sha256(password).equals(adminHash)) {
            throw new QueueException("Incorrect administrator password.");
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session("admin", null, System.currentTimeMillis() + ADMIN_SESSION_MILLIS));
        return envelope().put("token", token).put("role", "admin");
    }

    public synchronized JsonObject adminLogout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
        return Json.object().put("ok", true);
    }

    public synchronized JsonObject adminState(String token) {
        rolloverIfNeeded();
        requireAdmin(token);
        JsonObject response = envelope();
        response.put("nextToken", nextNumber <= MAX_TOKEN ? token(nextNumber) : "---")
                .put("servedToday", servedToday())
                .put("avgServiceMinutes", Math.max(1, (int) Math.round(averageServiceSeconds() / 60.0)))
                .put("dataDir", store.directory().toAbsolutePath().toString())
                .put("port", port)
                .put("adminPasswordDefault", store.isDefaultAdminPassword(adminHash))
                .put("dayLabel", day.format(DATE_LONG));

        JsonArray tellerArray = new JsonArray();
        for (Teller teller : sortedTellers()) {
            JsonObject json = tellerJson(teller);
            Ticket current = teller.currentTicket == null ? null : tickets.get(teller.currentTicket);
            json.put("currentReg", current == null ? "" : current.regNo);
            tellerArray.add(json);
        }
        response.put("tellers", tellerArray);

        JsonArray waiting = new JsonArray();
        int position = 0;
        for (Ticket ticket : tickets.values()) {
            if (ticket.state == TicketState.WAITING) {
                position++;
                waiting.add(ticketJson(ticket)
                        .put("position", position)
                        .put("waitedMinutes", minutesSince(ticket.issuedAt)));
            }
        }
        response.put("waiting", waiting);
        response.put("waitingCount", position);

        JsonArray allTickets = new JsonArray();
        List<Ticket> newestFirst = new ArrayList<>(tickets.values());
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            Ticket ticket = newestFirst.get(i);
            Teller teller = ticket.tellerId == null ? null : tellers.get(ticket.tellerId);
            allTickets.add(ticketJson(ticket)
                    .put("tellerName", teller == null ? "" : teller.name)
                    .put("closedAt", ticket.closedAt == null ? "" : ticket.closedAt.format(TIME)));
        }
        response.put("tickets", allTickets);

        response.put("stats", Json.object()
                .put("registered", tickets.size())
                .put("waiting", countState(TicketState.WAITING))
                .put("serving", countState(TicketState.SERVING))
                .put("done", countState(TicketState.DONE))
                .put("missed", countState(TicketState.MISSED))
                .put("counters", tellers.size())
                .put("countersOpen", tellers.values().stream()
                        .filter(t -> t.enabled && t.status != TellerStatus.CLOSED)
                        .count()));

        response.put("events", eventsJson(60));
        return response;
    }

    public synchronized JsonObject adminCreateTeller(String token, String name, String counterValue,
                                                     String pin, String enabledValue) {
        rolloverIfNeeded();
        requireAdmin(token);
        String cleanName = validateName(name, null);
        int counter = validateCounter(counterValue, null);
        String cleanPin = validatePin(pin, true);
        Teller teller = new Teller(newId(), cleanName, counter, cleanPin);
        teller.enabled = enabledValue == null || !"false".equalsIgnoreCase(enabledValue);
        teller.status = teller.enabled ? TellerStatus.AVAILABLE : TellerStatus.CLOSED;
        tellers.put(teller.id, teller);
        addEvent("Teller " + teller.name + " created at Counter " + teller.counter, "STATUS");
        dispatch();
        saveAll();
        return envelope().put("teller", tellerJson(teller))
                .put("message", teller.name + " created.");
    }

    public synchronized JsonObject adminUpdateTeller(String token, String tellerId, String name,
                                                     String counterValue, String pin,
                                                     String enabledValue) {
        rolloverIfNeeded();
        requireAdmin(token);
        Teller teller = findTeller(tellerId);
        String cleanName = validateName(name, teller.id);
        int counter = validateCounter(counterValue, teller.id);
        String cleanPin = validatePin(pin, false);
        boolean enable = enabledValue == null || !"false".equalsIgnoreCase(enabledValue);

        teller.name = cleanName;
        teller.counter = counter;
        if (cleanPin != null) {
            teller.pin = cleanPin;
        }
        if (teller.enabled && !enable) {
            if (teller.currentTicket != null) {
                releaseCurrent(teller, "counter disabled by the administrator");
            }
            teller.enabled = false;
            teller.status = TellerStatus.CLOSED;
            teller.statusSince = LocalDateTime.now();
            addEvent(teller.name + " disabled by the administrator", "STATUS");
        } else if (!teller.enabled && enable) {
            teller.enabled = true;
            teller.status = TellerStatus.AVAILABLE;
            teller.statusSince = LocalDateTime.now();
            addEvent(teller.name + " enabled at Counter " + teller.counter, "STATUS");
        }
        dispatch();
        saveAll();
        return envelope().put("teller", tellerJson(teller))
                .put("message", "Saved " + teller.name + ".");
    }

    public synchronized JsonObject adminDeleteTeller(String token, String tellerId) {
        rolloverIfNeeded();
        requireAdmin(token);
        Teller teller = findTeller(tellerId);
        if (teller.currentTicket != null) {
            throw new QueueException("Finish or mark no-show for ticket "
                    + token(teller.currentTicket) + " before deleting " + teller.name + ".");
        }
        tellers.remove(teller.id);
        sessions.entrySet().removeIf(entry -> teller.id.equals(entry.getValue().tellerId));
        addEvent(teller.name + " removed by the administrator", "STATUS");
        dispatch();
        saveAll();
        return Json.object().put("ok", true).put("message", teller.name + " deleted.");
    }

    /** Administrator override for a teller's availability. */
    public synchronized JsonObject adminSetTellerStatus(String token, String tellerId, String statusValue) {
        rolloverIfNeeded();
        requireAdmin(token);
        Teller teller = findTeller(tellerId);
        TellerStatus target = TellerStatus.parse(statusValue);
        if (target == null || target == TellerStatus.SERVING) {
            throw new QueueException("Choose a valid availability status.");
        }
        if (teller.currentTicket != null) {
            if (target == TellerStatus.AVAILABLE) {
                throw new QueueException(teller.name + " is serving ticket " + token(teller.currentTicket)
                        + ". Use Finish or No-show first.");
            }
            releaseCurrent(teller, "availability changed by the administrator");
        }
        teller.status = target;
        teller.statusSince = LocalDateTime.now();
        addEvent(teller.name + " set to " + target.label().toLowerCase(Locale.ROOT)
                + " by the administrator", "STATUS");
        if (target == TellerStatus.AVAILABLE) {
            dispatch();
        }
        saveAll();
        return Json.object().put("ok", true)
                .put("message", teller.name + " is now " + target.label() + ".");
    }

    public synchronized JsonObject adminResetQueue(String token) {
        rolloverIfNeeded();
        requireAdmin(token);
        tickets.clear();
        regIndex.clear();
        nextNumber = 0;
        assignSeq = 0;
        for (Teller teller : tellers.values()) {
            teller.currentTicket = null;
            teller.servedToday = 0;
            teller.status = teller.enabled ? TellerStatus.AVAILABLE : TellerStatus.CLOSED;
            teller.statusSince = LocalDateTime.now();
            teller.lastAssignedSeq = 0;
        }
        addEvent("Queue reset by the administrator. Tokens start again at 000.", "RESET");
        saveAll();
        return adminState(token);
    }

    public synchronized JsonObject adminChangePassword(String token, String oldPassword, String newPassword) {
        requireAdmin(token);
        if (oldPassword == null || !Hashes.sha256(oldPassword).equals(adminHash)) {
            throw new QueueException("The current administrator password is incorrect.");
        }
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new QueueException("The new password must be at least 6 characters long.");
        }
        adminHash = Hashes.sha256(newPassword.trim());
        try {
            store.saveConfig(new Store.Config(adminHash));
        } catch (IOException ex) {
            throw new QueueException("Could not save the new password: " + ex.getMessage(), ex);
        }
        sessions.entrySet().removeIf(entry -> "admin".equals(entry.getValue().role)
                && !entry.getKey().equals(token));
        return Json.object().put("ok", true).put("message", "Administrator password updated.");
    }

    /** Called by the server's scheduler so the queue rolls over even when nobody is polling. */
    public synchronized void tick() {
        rolloverIfNeeded();
    }

    // ------------------------------------------------------------- internals

    private void seedTellers() {
        createSeedTeller("Teller One", 1, "1111");
        createSeedTeller("Teller Two", 2, "2222");
        createSeedTeller("Teller Three", 3, "3333");
    }

    private void createSeedTeller(String name, int counter, String pin) {
        Teller teller = new Teller(newId(), name, counter, pin);
        tellers.put(teller.id, teller);
    }

    private String newId() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void rebuildAfterLoad() {
        for (Teller teller : tellers.values()) {
            teller.currentTicket = null;
            teller.servedToday = 0;
            teller.status = teller.enabled ? TellerStatus.AVAILABLE : TellerStatus.CLOSED;
            teller.statusSince = LocalDateTime.now();
            teller.lastAssignedSeq = 0;
        }
        for (Ticket ticket : tickets.values()) {
            if (ticket.state == TicketState.SERVING) {
                Teller teller = ticket.tellerId == null ? null : tellers.get(ticket.tellerId);
                if (teller != null && teller.enabled) {
                    teller.currentTicket = ticket.number;
                    teller.status = TellerStatus.SERVING;
                    teller.statusSince = ticket.calledAt == null ? LocalDateTime.now() : ticket.calledAt;
                } else {
                    ticket.state = TicketState.WAITING;
                    ticket.tellerId = null;
                    ticket.calledAt = null;
                }
            } else if (ticket.state == TicketState.DONE && ticket.tellerId != null) {
                Teller teller = tellers.get(ticket.tellerId);
                if (teller != null) {
                    teller.servedToday++;
                }
            }
        }
        assignSeq = tickets.size();
    }

    private boolean dispatch() {
        boolean changed = false;
        while (true) {
            Ticket next = null;
            for (Ticket ticket : tickets.values()) {
                if (ticket.state == TicketState.WAITING) {
                    next = ticket;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            Teller teller = pickTeller();
            if (teller == null) {
                break;
            }
            next.state = TicketState.SERVING;
            next.tellerId = teller.id;
            next.calledAt = LocalDateTime.now();
            teller.currentTicket = next.number;
            teller.status = TellerStatus.SERVING;
            teller.statusSince = next.calledAt;
            teller.lastAssignedSeq = ++assignSeq;
            addEvent("Ticket " + next.token() + " called to " + teller.name
                    + " · Counter " + teller.counter, "CALL");
            changed = true;
        }
        return changed;
    }

    private Teller pickTeller() {
        Teller best = null;
        for (Teller teller : tellers.values()) {
            if (!teller.enabled || teller.status != TellerStatus.AVAILABLE || teller.currentTicket != null) {
                continue;
            }
            if (best == null
                    || teller.servedToday < best.servedToday
                    || (teller.servedToday == best.servedToday
                        && teller.lastAssignedSeq < best.lastAssignedSeq)) {
                best = teller;
            }
        }
        return best;
    }

    private void releaseCurrent(Teller teller, String reason) {
        if (teller.currentTicket == null) {
            return;
        }
        Ticket ticket = tickets.get(teller.currentTicket);
        if (ticket != null) {
            ticket.state = TicketState.WAITING;
            ticket.tellerId = null;
            ticket.calledAt = null;
            addEvent("Ticket " + ticket.token() + " returned to the queue (" + reason + ")", "INFO");
        }
        teller.currentTicket = null;
    }

    private void rolloverIfNeeded() {
        LocalDate today = LocalDate.now();
        if (today.equals(day)) {
            return;
        }
        day = today;
        nextNumber = 0;
        tickets.clear();
        regIndex.clear();
        events.clear();
        assignSeq = 0;
        for (Teller teller : tellers.values()) {
            teller.currentTicket = null;
            teller.servedToday = 0;
            teller.status = teller.enabled ? TellerStatus.AVAILABLE : TellerStatus.CLOSED;
            teller.statusSince = LocalDateTime.now();
            teller.lastAssignedSeq = 0;
        }
        addEvent("New day — queue reset. Tokens start again at 000.", "RESET");
        saveAll();
    }

    private JsonObject envelope() {
        long resetSeconds = Math.max(0, 86400 - LocalTime.now().toSecondOfDay());
        return Json.object()
                .put("ok", true)
                .put("app", APP_NAME)
                .put("version", VERSION)
                .put("day", day.toString())
                .put("dayLabel", day.format(DATE_LONG))
                .put("time", LocalTime.now().format(TIME))
                .put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("lastToken", nextNumber == 0 ? "" : token(nextNumber - 1))
                .put("waitingCount", countState(TicketState.WAITING))
                .put("resetInSeconds", resetSeconds)
                .put("resetAt", "00:00");
    }

    private JsonObject tellerStateJson(Teller teller) {
        JsonObject response = envelope();
        JsonObject json = tellerJson(teller);
        Ticket current = teller.currentTicket == null ? null : tickets.get(teller.currentTicket);
        json.put("serving", current != null);
        json.put("current", current == null ? Json.object() : ticketJson(current)
                .put("waitedMinutes", minutesSince(current.issuedAt)));
        response.put("teller", json)
                .put("nextUp", nextUp(5))
                .put("events", eventsJson(8))
                .put("avgServiceMinutes", Math.max(1, (int) Math.round(averageServiceSeconds() / 60.0)));
        response.put("resetLabel", "Resets at midnight");
        return response;
    }

    private JsonObject studentTicketJson(Ticket ticket, boolean alreadyRegistered) {
        JsonObject response = envelope();
        int position = positionOf(ticket);
        Teller teller = ticket.tellerId == null ? null : tellers.get(ticket.tellerId);
        JsonObject contact = Json.object();
        if (teller != null) {
            contact.put("id", teller.id)
                    .put("name", teller.name)
                    .put("counter", teller.counter);
        }
        boolean assigned = ticket.state == TicketState.SERVING;
        response.put("found", true)
                .put("token", ticket.token())
                .put("tokenNumber", ticket.number)
                .put("reg", ticket.regNo)
                .put("state", ticket.state.name())
                .put("stateLabel", ticket.state.label())
                .put("alreadyRegistered", alreadyRegistered)
                .put("issuedAt", ticket.issuedAt.format(TIME))
                .put("calledAt", ticket.calledAt == null ? "" : ticket.calledAt.format(TIME))
                .put("position", position)
                .put("ahead", Math.max(0, position - 1))
                .put("etaMinutes", ticket.state == TicketState.WAITING ? estimateMinutes(position) : 0)
                .put("assigned", assigned)
                .put("teller", contact)
                .put("message", messageFor(ticket, teller))
                .put("resetInSeconds", resetSeconds());
        return response;
    }

    private String messageFor(Ticket ticket, Teller teller) {
        return switch (ticket.state) {
            case WAITING -> "Please wait. Your number will be shown on the display.";
            case SERVING -> teller == null
                    ? "You are being served."
                    : "Proceed to Counter " + teller.counter + " — " + teller.name + ".";
            case DONE -> "Service completed. Thank you for visiting the Finance Department.";
            case MISSED -> "Your number was called and marked as a no-show. Please speak to the finance desk.";
        };
    }

    private JsonObject tellerJson(Teller teller) {
        return Json.object()
                .put("id", teller.id)
                .put("name", teller.name)
                .put("counter", teller.counter)
                .put("enabled", teller.enabled)
                .put("status", teller.status.name())
                .put("statusLabel", teller.status.label())
                .put("statusHint", teller.status.hint())
                .put("statusSince", teller.statusSince == null ? "" : teller.statusSince.format(TIME))
                .put("servedToday", teller.servedToday)
                .put("currentToken", teller.currentTicket == null ? "" : token(teller.currentTicket))
                .put("serving", teller.currentTicket != null);
    }

    private List<Teller> sortedTellers() {
        List<Teller> list = new ArrayList<>(tellers.values());
        list.sort((a, b) -> Integer.compare(a.counter, b.counter));
        return list;
    }

    private JsonObject ticketJson(Ticket ticket) {
        return Json.object()
                .put("token", ticket.token())
                .put("reg", ticket.regNo)
                .put("state", ticket.state.name())
                .put("stateLabel", ticket.state.label())
                .put("issuedAt", ticket.issuedAt.format(TIME))
                .put("calledAt", ticket.calledAt == null ? "" : ticket.calledAt.format(TIME))
                .put("closedAt", ticket.closedAt == null ? "" : ticket.closedAt.format(TIME))
                .put("recalls", ticket.recalls);
    }

    private JsonArray nextUp(int limit) {
        JsonArray array = new JsonArray();
        for (Ticket ticket : tickets.values()) {
            if (ticket.state == TicketState.WAITING) {
                array.add(ticket.token());
                if (array.size() >= limit) {
                    break;
                }
            }
        }
        return array;
    }

    private JsonArray eventsJson(int limit) {
        List<Event> list = new ArrayList<>(events);
        JsonArray array = new JsonArray();
        int start = Math.max(0, list.size() - limit);
        for (int i = start; i < list.size(); i++) {
            Event event = list.get(i);
            array.add(Json.object()
                    .put("time", event.at.format(TIME))
                    .put("text", event.text)
                    .put("kind", event.kind));
        }
        return array;
    }

    private int positionOf(Ticket target) {
        if (target.state != TicketState.WAITING) {
            return 0;
        }
        int position = 0;
        for (Ticket ticket : tickets.values()) {
            if (ticket.state == TicketState.WAITING) {
                position++;
                if (ticket.number == target.number) {
                    return position;
                }
            }
        }
        return 0;
    }

    private int estimateMinutes(int position) {
        if (position <= 0) {
            return 0;
        }
        double averageSeconds = averageServiceSeconds();
        int active = Math.max(1, activeCounters());
        return Math.max(1, (int) Math.ceil(position * averageSeconds / 60.0 / active));
    }

    private double averageServiceSeconds() {
        long total = 0;
        int count = 0;
        for (Ticket ticket : tickets.values()) {
            if (ticket.state == TicketState.DONE && ticket.calledAt != null && ticket.closedAt != null) {
                long seconds = Duration.between(ticket.calledAt, ticket.closedAt).getSeconds();
                if (seconds >= 0 && seconds < 4 * 3600) {
                    total += seconds;
                    count++;
                }
            }
        }
        if (count == 0) {
            return 240; // sensible default until real data exists
        }
        return Math.max(45, (double) total / count);
    }

    private int activeCounters() {
        int count = 0;
        for (Teller teller : tellers.values()) {
            if (teller.enabled && (teller.status == TellerStatus.AVAILABLE
                    || teller.status == TellerStatus.SERVING)) {
                count++;
            }
        }
        return count;
    }

    private int countState(TicketState state) {
        int count = 0;
        for (Ticket ticket : tickets.values()) {
            if (ticket.state == state) {
                count++;
            }
        }
        return count;
    }

    private int servedToday() {
        return countState(TicketState.DONE);
    }

    private long minutesSince(LocalDateTime moment) {
        return Math.max(0, Duration.between(moment, LocalDateTime.now()).toMinutes());
    }

    private long resetSeconds() {
        return Math.max(0, 86400 - LocalTime.now().toSecondOfDay());
    }

    private void addEvent(String text, String kind) {
        if (events.size() >= MAX_EVENTS) {
            events.removeFirst();
        }
        events.addLast(new Event(LocalDateTime.now(), text, kind));
    }

    private void saveAll() {
        try {
            store.saveTellers(tellers.values());
            Store.DayData data = new Store.DayData();
            data.nextNumber = nextNumber;
            data.tickets = new ArrayList<>(tickets.values());
            data.events = new ArrayList<>(events);
            store.saveDay(day, data);
        } catch (IOException ex) {
            System.err.println("[KIU digitalQ] Could not save queue data: " + ex.getMessage());
        }
    }

    private String token(int number) {
        return String.format("%03d", number);
    }

    private static String normaliseRegistration(String value) {
        return value == null ? "" : value.replaceAll("[\\s-]", "");
    }

    // --------------------------------------------------------------- sessions

    private Teller requireTeller(String token) {
        Session session = requireSession(token, "teller");
        Teller teller = tellers.get(session.tellerId);
        if (teller == null) {
            sessions.remove(token);
            throw new QueueException("This teller no longer exists. Please sign in again.");
        }
        if (!teller.enabled) {
            throw new QueueException("This counter has been disabled by the administrator.");
        }
        return teller;
    }

    private void requireAdmin(String token) {
        requireSession(token, "admin");
    }

    private Session requireSession(String token, String role) {
        if (token == null || token.isBlank()) {
            throw new QueueException("Please sign in again.");
        }
        Session session = sessions.get(token);
        long now = System.currentTimeMillis();
        if (session == null || session.expiresAt < now) {
            sessions.remove(token);
            throw new QueueException("Your session has expired. Please sign in again.");
        }
        if (!role.equals(session.role)) {
            throw new QueueException("You do not have permission to do that.");
        }
        session.expiresAt = now + ("admin".equals(role) ? ADMIN_SESSION_MILLIS : TELLER_SESSION_MILLIS);
        return session;
    }

    // ------------------------------------------------------------- validation

    private String validateName(String value, String excludeId) {
        String name = value == null ? "" : value.replaceAll("[\\t\\r\\n]", " ").trim();
        if (name.length() < 2 || name.length() > 40) {
            throw new QueueException("Teller name must be between 2 and 40 characters.");
        }
        for (Teller teller : tellers.values()) {
            if (teller.name.equalsIgnoreCase(name) && !teller.id.equals(excludeId)) {
                throw new QueueException("A teller named \"" + name + "\" already exists.");
            }
        }
        return name;
    }

    private int validateCounter(String value, String excludeId) {
        int counter;
        try {
            counter = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ex) {
            throw new QueueException("Counter must be a number between 1 and 99.");
        }
        if (counter < 1 || counter > 99) {
            throw new QueueException("Counter must be a number between 1 and 99.");
        }
        for (Teller teller : tellers.values()) {
            if (teller.counter == counter && !teller.id.equals(excludeId)) {
                throw new QueueException("Counter " + counter + " is already assigned to " + teller.name + ".");
            }
        }
        return counter;
    }

    private String validatePin(String value, boolean required) {
        String pin = value == null ? "" : value.trim();
        if (pin.isEmpty() && !required) {
            return null;
        }
        if (!pin.matches("\\d{4,6}")) {
            throw new QueueException("PIN must be 4 to 6 digits.");
        }
        return pin;
    }

    private Teller findTeller(String tellerId) {
        Teller teller = tellers.get(tellerId);
        if (teller == null) {
            throw new QueueException("That teller no longer exists.");
        }
        return teller;
    }

    private static final class Session {
        final String role;
        final String tellerId;
        long expiresAt;

        Session(String role, String tellerId, long expiresAt) {
            this.role = role;
            this.tellerId = tellerId;
            this.expiresAt = expiresAt;
        }
    }
}
