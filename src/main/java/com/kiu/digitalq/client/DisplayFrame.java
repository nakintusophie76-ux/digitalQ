package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json.JsonArray;
import com.kiu.digitalq.json.Json.JsonObject;
import com.kiu.digitalq.ui.Brand;
import com.kiu.digitalq.ui.Card;
import com.kiu.digitalq.ui.Dot;
import com.kiu.digitalq.ui.FlatButton;
import com.kiu.digitalq.ui.PillLabel;
import com.kiu.digitalq.ui.Theme;
import com.kiu.digitalq.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The waiting-room board. Designed to be readable across a room: large ticket
 * numbers, a tile per counter, the waiting list and a ticker with the latest
 * calls. It refreshes every two seconds and can run full screen.
 */
public final class DisplayFrame extends JFrame {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private final ApiClient api;
    private final Poller poller;
    private final Timer clockTimer;
    private final Timer tickerTimer;

    private final JLabel clockLabel = new JLabel("--:--:--");
    private final JLabel dateLabel = new JLabel(" ");
    private final JLabel resetLabel = new JLabel(" ");
    private final Dot liveDot = new Dot(Theme.GREEN, 10);
    private final JLabel liveLabel = new JLabel("LIVE");
    private final FlatButton fullScreenButton = new FlatButton("Full screen", FlatButton.Kind.GOLD);

    private final JLabel servingValue = statValue();
    private final JLabel waitingValue = statValue();
    private final JLabel servedValue = statValue();
    private final JLabel averageValue = statValue();
    private final JLabel nextValue = statValue();

    private final JPanel counterGrid = Ui.panel(new GridLayout(1, 1, 16, 16));
    private final JPanel waitingChips = Ui.panel(new GridLayout(0, 3, 8, 8));
    private final PillLabel waitingCount = new PillLabel("0 waiting");
    private final JLabel nextUpLabel = new JLabel(" ");
    private final JLabel tickerLabel = new JLabel("Waiting for the first student…");

    private final Map<String, CounterTile> tiles = new LinkedHashMap<>();
    private final List<String> tickerItems = new ArrayList<>();

    private String countersKey = "";
    private String waitingKey = "";
    private String eventsKey = "";
    private int tickerIndex;
    private int tileCount = -1;
    private boolean firstState = true;
    private boolean fullScreen;

    public DisplayFrame(ApiClient api) {
        super("KIU digitalQ — Waiting-room display");
        this.api = api;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(980, 660));
        Ui.sizeWindow(this, 1400, 760);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.NAVY_DEEP);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        poller = new Poller(2000, () -> api.get("display"), this::applyState, this::handleError);

        clockTimer = new Timer(1000, event -> updateClock());
        clockTimer.start();
        updateClock();

        tickerTimer = new Timer(5000, event -> rotateTicker());
        tickerTimer.start();

        getRootPane().registerKeyboardAction(event -> toggleFullScreen(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(event -> {
            if (fullScreen) {
                exitFullScreen();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                poller.stop();
                clockTimer.stop();
                tickerTimer.stop();
                if (fullScreen) {
                    exitFullScreen();
                }
            }
        });

        poller.startDelayed(150);
    }

    // ------------------------------------------------------------------- UI

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(24, 0));
        header.setBackground(Theme.NAVY_DEEP);
        header.setBorder(new EmptyBorder(18, 30, 16, 28));

        JPanel brand = Ui.panel(new BorderLayout(0, 0));
        brand.add(Brand.lockup(true, "Finance Department · waiting room", 25), BorderLayout.WEST);
        header.add(brand, BorderLayout.WEST);

        JPanel live = Ui.panel(new FlowLayout(FlowLayout.CENTER, 7, 6));
        live.add(liveDot);
        live.add(liveLabel);
        header.add(live, BorderLayout.CENTER);
        liveLabel.setFont(Theme.font(Font.BOLD, 10.5f));
        liveLabel.setForeground(Theme.WHITE_70);

        JPanel right = Ui.panel(new BorderLayout(14, 0));
        JPanel clock = Ui.panel(null);
        clock.setLayout(new BoxLayout(clock, BoxLayout.Y_AXIS));
        clockLabel.setFont(Theme.mono(29));
        clockLabel.setForeground(Color.WHITE);
        clockLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        dateLabel.setFont(Theme.font(10.5f));
        dateLabel.setForeground(Theme.WHITE_45);
        dateLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetLabel.setFont(Theme.font(Font.BOLD, 10.5f));
        resetLabel.setForeground(Theme.GOLD);
        resetLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        clock.add(clockLabel);
        clock.add(Box.createVerticalStrut(1));
        clock.add(dateLabel);
        clock.add(Box.createVerticalStrut(2));
        clock.add(resetLabel);
        right.add(clock, BorderLayout.CENTER);

        fullScreenButton.setButtonFont(Theme.font(Font.BOLD, 11f));
        fullScreenButton.setPadding(8, 13);
        fullScreenButton.setArc(10);
        fullScreenButton.addActionListener(event -> toggleFullScreen());
        JPanel buttonWrap = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 0, 18));
        buttonWrap.add(fullScreenButton);
        right.add(buttonWrap, BorderLayout.EAST);

        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = Ui.panel(new BorderLayout(0, 14));
        body.setBorder(new EmptyBorder(0, 24, 16, 24));

        JPanel stats = Ui.panel(new GridLayout(1, 5, 12, 0));
        stats.setBorder(new EmptyBorder(2, 0, 0, 0));
        stats.add(statCard("Now serving", servingValue, Theme.GOLD));
        stats.add(statCard("Waiting", waitingValue, Color.WHITE));
        stats.add(statCard("Served today", servedValue, Theme.GREEN));
        stats.add(statCard("Average service", averageValue, Color.WHITE));
        stats.add(statCard("Next number", nextValue, Theme.GOLD));
        body.add(stats, BorderLayout.NORTH);

        JPanel main = Ui.panel(new BorderLayout(16, 0));
        main.add(counterGrid, BorderLayout.CENTER);
        main.add(buildWaitingPanel(), BorderLayout.EAST);
        body.add(main, BorderLayout.CENTER);

        body.add(buildTicker(), BorderLayout.SOUTH);
        return body;
    }

    private Card statCard(String caption, JLabel value, Color color) {
        Card card = new Card(new BorderLayout(0, 0));
        card.setCardColor(Theme.NAVY_LIGHT);
        card.setBorderColor(Theme.alpha(Color.WHITE, 34));
        card.setShadow(false);
        card.setPadding(11, 18, 10, 18);
        value.setForeground(color);
        JLabel captionLabel = new JLabel(caption.toUpperCase());
        captionLabel.setFont(Theme.font(Font.BOLD, 9.5f));
        captionLabel.setForeground(Theme.WHITE_45);
        card.add(captionLabel, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static JLabel statValue() {
        JLabel label = new JLabel("0");
        label.setFont(Theme.font(Font.BOLD, 27));
        label.setForeground(Color.WHITE);
        return label;
    }

    private Card buildWaitingPanel() {
        Card card = new Card(new BorderLayout(0, 10));
        card.setCardColor(Theme.NAVY_LIGHT);
        card.setBorderColor(Theme.alpha(Color.WHITE, 34));
        card.setShadow(false);
        card.setPadding(16, 18, 14, 18);
        card.setPreferredSize(new Dimension(390, 0));
        card.setMinimumSize(new Dimension(330, 0));

        JPanel top = Ui.panel(new BorderLayout(8, 0));
        JLabel title = new JLabel("WAITING ROOM");
        title.setFont(Theme.font(Font.BOLD, 11f));
        title.setForeground(Theme.WHITE_70);
        top.add(title, BorderLayout.WEST);
        waitingCount.setPill("0 waiting", Theme.NAVY, Theme.GOLD);
        top.add(waitingCount, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        waitingChips.setOpaque(false);
        JScrollPane scroll = new JScrollPane(waitingChips);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setOpaque(false);
        card.add(scroll, BorderLayout.CENTER);

        JPanel bottom = Ui.panel(new BorderLayout(0, 2));
        bottom.setBorder(new EmptyBorder(8, 0, 0, 0));
        JLabel nextCaption = new JLabel("UP NEXT");
        nextCaption.setFont(Theme.font(Font.BOLD, 9.5f));
        nextCaption.setForeground(Theme.WHITE_45);
        nextUpLabel.setFont(Theme.mono(22));
        nextUpLabel.setForeground(Theme.GOLD);
        bottom.add(nextCaption, BorderLayout.NORTH);
        bottom.add(nextUpLabel, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private Card buildTicker() {
        Card card = new Card(new BorderLayout(14, 0));
        card.setCardColor(Theme.NAVY);
        card.setBorderColor(Theme.alpha(Color.WHITE, 30));
        card.setShadow(false);
        card.setArc(14);
        card.setPadding(9, 16, 9, 18);

        PillLabel badge = new PillLabel("LATEST");
        badge.setPill("LATEST", Theme.NAVY, Theme.GOLD);
        badge.setFont(Theme.font(Font.BOLD, 9.5f));
        badge.setBorder(new EmptyBorder(5, 10, 5, 10));
        card.add(badge, BorderLayout.WEST);

        tickerLabel.setFont(Theme.font(13.5f));
        tickerLabel.setForeground(Color.WHITE);
        card.add(tickerLabel, BorderLayout.CENTER);

        JLabel hint = new JLabel("F11 full screen");
        hint.setFont(Theme.font(9.5f));
        hint.setForeground(Theme.WHITE_45);
        card.add(hint, BorderLayout.EAST);
        return card;
    }

    private void updateClock() {
        LocalTime now = LocalTime.now();
        clockLabel.setText(Ui.clock(now));
        dateLabel.setText(LocalDate.now().format(DATE));
        long seconds = Math.max(0, 86400 - now.toSecondOfDay());
        resetLabel.setText("RESETS IN " + Ui.countdown(seconds).toUpperCase(Locale.ROOT));
    }

    // ------------------------------------------------------------- live data

    private void applyState(JsonObject state) {
        liveDot.setColor(Theme.GREEN);
        liveLabel.setText("LIVE");
        liveLabel.setForeground(Theme.WHITE_70);

        JsonArray tellers = state.arr("tellers");
        int serving = 0;
        for (Object value : tellers) {
            if (value instanceof JsonObject teller && teller.bool("serving")) {
                serving++;
            }
        }
        servingValue.setText(String.valueOf(serving));
        waitingValue.setText(String.valueOf(state.integer("waitingCount")));
        servedValue.setText(String.valueOf(state.integer("servedToday")));
        averageValue.setText(state.integer("avgServiceMinutes") + "m");
        String next = state.str("nextToken");
        nextValue.setText(next.isEmpty() ? "—" : next);

        updateCounters(tellers);
        updateWaiting(state.arr("waiting"), state.integer("waitingCount"));
        updateTicker(state.arr("events"));
        firstState = false;
    }

    private void updateCounters(JsonArray tellers) {
        String key = com.kiu.digitalq.json.Json.write(tellers);
        if (key.equals(countersKey)) {
            return;
        }
        countersKey = key;

        List<JsonObject> list = new ArrayList<>();
        for (Object value : tellers) {
            if (value instanceof JsonObject teller) {
                list.add(teller);
            }
        }

        boolean called = false;
        Iterator<Map.Entry<String, CounterTile>> iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CounterTile> entry = iterator.next();
            boolean present = list.stream().anyMatch(t -> t.str("id").equals(entry.getKey()));
            if (!present) {
                iterator.remove();
            }
        }
        for (JsonObject teller : list) {
            String id = teller.str("id");
            CounterTile tile = tiles.get(id);
            if (tile == null) {
                tile = new CounterTile();
                tiles.put(id, tile);
            }
            called |= tile.update(teller, !firstState);
        }

        int visible = Math.min(tiles.size(), 9);
        if (visible != tileCount || countersKey.equals(key)) {
            boolean rebuildNeeded = counterGrid.getComponentCount() != Math.max(1, visible);
            if (rebuildNeeded) {
                counterGrid.removeAll();
                int shown = 0;
                for (CounterTile tile : tiles.values()) {
                    if (shown >= 9) {
                        break;
                    }
                    counterGrid.add(tile);
                    shown++;
                }
                if (shown == 0) {
                    counterGrid.add(emptyCounters());
                }
                int rows;
                int cols;
                int count = Math.max(1, shown);
                if (count == 1) {
                    rows = 1;
                    cols = 1;
                } else if (count == 2) {
                    rows = 1;
                    cols = 2;
                } else if (count == 3 || count == 4) {
                    rows = 2;
                    cols = 2;
                } else if (count <= 6) {
                    rows = 2;
                    cols = 3;
                } else {
                    rows = 3;
                    cols = 3;
                }
                counterGrid.setLayout(new GridLayout(rows, cols, 16, 16));
                counterGrid.revalidate();
                counterGrid.repaint();
                tileCount = visible;
            }
        }
        if (called && !firstState) {
            Ui.beep();
        }
    }

    private JComponent emptyCounters() {
        JPanel panel = Ui.panel(new BorderLayout());
        JLabel label = new JLabel("No counters have been set up yet", SwingConstants.CENTER);
        label.setFont(Theme.font(16));
        label.setForeground(Theme.WHITE_45);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private void updateWaiting(JsonArray waiting, int total) {
        String key = com.kiu.digitalq.json.Json.write(waiting) + "/" + total;
        waitingCount.setPill(total + " waiting", Theme.NAVY, Theme.GOLD);
        if (key.equals(waitingKey)) {
            return;
        }
        waitingKey = key;

        waitingChips.removeAll();
        int shown = 0;
        StringBuilder nextUp = new StringBuilder();
        for (Object value : waiting) {
            if (!(value instanceof JsonObject entry)) {
                continue;
            }
            String token = entry.str("token");
            if (shown < 48) {
                waitingChips.add(chip(token));
                shown++;
            }
            if (shown <= 3) {
                if (nextUp.length() > 0) {
                    nextUp.append("  ·  ");
                }
                nextUp.append(token);
            }
        }
        if (shown == 0) {
            JLabel empty = new JLabel("No students waiting.");
            empty.setFont(Theme.font(14));
            empty.setForeground(Theme.WHITE_45);
            empty.setBorder(new EmptyBorder(10, 2, 0, 0));
            waitingChips.add(empty);
            nextUpLabel.setText("—");
        } else {
            if (waiting.size() > shown) {
                JLabel more = new JLabel("+" + (waiting.size() - shown) + " more");
                more.setFont(Theme.font(Font.BOLD, 13));
                more.setForeground(Theme.WHITE_70);
                waitingChips.add(more);
            }
            nextUpLabel.setText(nextUp.toString());
        }
        waitingChips.revalidate();
        waitingChips.repaint();
    }

    private JLabel chip(String token) {
        JLabel label = new JLabel(token, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) graphics.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.GOLD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setOpaque(false);
        label.setFont(Theme.mono(25));
        label.setForeground(Theme.NAVY);
        label.setPreferredSize(new Dimension(80, 50));
        return label;
    }

    private void updateTicker(JsonArray events) {
        String key = com.kiu.digitalq.json.Json.write(events);
        if (key.equals(eventsKey)) {
            return;
        }
        eventsKey = key;
        tickerItems.clear();
        for (int i = events.size() - 1; i >= 0; i--) {
            Object value = events.get(i);
            if (value instanceof JsonObject event) {
                String text = event.str("text");
                if (!text.isBlank()) {
                    tickerItems.add(event.str("time") + "   ·   " + text);
                }
            }
        }
        tickerIndex = 0;
        showTicker();
    }

    private void rotateTicker() {
        if (tickerItems.size() <= 1) {
            return;
        }
        tickerIndex = (tickerIndex + 1) % tickerItems.size();
        showTicker();
    }

    private void showTicker() {
        if (tickerItems.isEmpty()) {
            tickerLabel.setText("Waiting for the first student…");
            tickerLabel.setForeground(Theme.WHITE_45);
        } else {
            tickerLabel.setText(tickerItems.get(tickerIndex));
            tickerLabel.setForeground(Color.WHITE);
        }
    }

    private void handleError(Exception error) {
        liveDot.setColor(Theme.RED);
        String message = error.getMessage() == null ? "offline" : error.getMessage();
        liveLabel.setText("RECONNECTING");
        liveLabel.setForeground(Theme.RED);
        tickerLabel.setText(message);
        tickerLabel.setForeground(Theme.RED);
    }

    // -------------------------------------------------------------- full screen

    private void toggleFullScreen() {
        if (fullScreen) {
            exitFullScreen();
        } else {
            enterFullScreen();
        }
    }

    private void enterFullScreen() {
        try {
            GraphicsDevice device = getGraphicsConfiguration().getDevice();
            if (device.isFullScreenSupported()) {
                device.setFullScreenWindow(this);
                fullScreen = true;
            } else {
                setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
            }
        } catch (HeadlessException | IllegalStateException ex) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
        fullScreenButton.setText("Exit full screen");
    }

    private void exitFullScreen() {
        try {
            GraphicsDevice device = getGraphicsConfiguration().getDevice();
            device.setFullScreenWindow(null);
        } catch (HeadlessException ex) {
            // Nothing to restore.
        }
        fullScreen = false;
        fullScreenButton.setText("Full screen");
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------- counter tile

    private final class CounterTile extends Card {

        private final JLabel counterLabel = new JLabel("COUNTER");
        private final PillLabel statusPill = new PillLabel("Ready");
        private final JLabel tokenLabel = new JLabel("—");
        private final JLabel nameLabel = new JLabel(" ");
        private final JLabel servedLabel = new JLabel(" ");
        private String lastToken = "";

        CounterTile() {
            super(new BorderLayout(0, 4));
            setCardColor(Theme.NAVY_LIGHT);
            setBorderColor(Theme.alpha(Color.WHITE, 38));
            setShadow(false);
            setPadding(14, 20, 13, 20);

            JPanel top = Ui.panel(new BorderLayout(8, 0));
            counterLabel.setFont(Theme.font(Font.BOLD, 10f));
            counterLabel.setForeground(Theme.WHITE_45);
            top.add(counterLabel, BorderLayout.WEST);
            top.add(statusPill, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);

            tokenLabel.setFont(Theme.mono(66));
            tokenLabel.setForeground(Theme.WHITE_45);
            tokenLabel.setHorizontalAlignment(SwingConstants.CENTER);
            add(tokenLabel, BorderLayout.CENTER);

            JPanel bottom = Ui.panel(new BorderLayout(0, 1));
            nameLabel.setFont(Theme.font(Font.BOLD, 15));
            nameLabel.setForeground(Color.WHITE);
            servedLabel.setFont(Theme.font(10.5f));
            servedLabel.setForeground(Theme.WHITE_45);
            bottom.add(nameLabel, BorderLayout.NORTH);
            bottom.add(servedLabel, BorderLayout.SOUTH);
            add(bottom, BorderLayout.SOUTH);
        }

        boolean update(JsonObject teller, boolean animate) {
            boolean serving = teller.bool("serving");
            String token = teller.str("currentToken");
            String status = teller.str("status");

            counterLabel.setText("COUNTER " + teller.integer("counter"));
            statusPill.setPill(teller.str("statusLabel"), Theme.statusText(status),
                    Theme.statusSoft(status));
            tokenLabel.setText(serving && !token.isEmpty() ? token : "—");
            tokenLabel.setForeground(serving && !token.isEmpty() ? Theme.GOLD : Theme.WHITE_45);
            nameLabel.setText(teller.str("name"));
            servedLabel.setText("Served today  " + teller.integer("servedToday"));

            boolean called = serving && !token.isEmpty() && !token.equals(lastToken);
            if (called && animate) {
                flash();
            }
            lastToken = token;
            return called;
        }
    }
}
