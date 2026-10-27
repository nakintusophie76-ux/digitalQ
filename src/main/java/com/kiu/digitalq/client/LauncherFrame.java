package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json.JsonObject;
import com.kiu.digitalq.server.QueueServer;
import com.kiu.digitalq.ui.Brand;
import com.kiu.digitalq.ui.Card;
import com.kiu.digitalq.ui.Dot;
import com.kiu.digitalq.ui.FlatButton;
import com.kiu.digitalq.ui.Theme;
import com.kiu.digitalq.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Main menu. Opens the student kiosk, teller terminal, waiting-room display and
 * administrator console, and can run the queue server on this computer.
 */
public final class LauncherFrame extends JFrame {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);

    private final ClientConfig config = new ClientConfig();
    private final JTextField serverField = Ui.field(13);
    private final JLabel statusTitle = new JLabel("Connecting…");
    private final JLabel statusDetail = new JLabel(" ");
    private final Dot statusDot = new Dot(Theme.AMBER, 12);
    private final FlatButton startButton = new FlatButton("Start local server", FlatButton.Kind.OUTLINE);
    private final JCheckBox autoStart = new JCheckBox("Start a local server automatically");
    private final JLabel clockLabel = new JLabel();
    private final JLabel dateLabel = new JLabel();

    private ApiClient api;
    private QueueServer localServer;
    private boolean connected;
    private boolean checking;
    private boolean autoStartTried;
    private int openWindows;

    public LauncherFrame() {
        super("KIU digitalQ — Finance Department");
        config.load();
        api = new ApiClient(config.serverUrl());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 680));
        Ui.sizeWindow(this, 1180, 720);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(buildHero(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        serverField.setText(api.baseUrl());
        autoStart.setSelected(config.autoStartServer());
        autoStart.setOpaque(false);
        autoStart.setFont(Theme.font(11.5f));
        autoStart.setForeground(Theme.MUTED);
        autoStart.setFocusPainted(false);
        autoStart.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        autoStart.addActionListener(event -> {
            config.setAutoStartServer(autoStart.isSelected());
            config.save();
        });
        serverField.addActionListener(event -> connect(true));
        startButton.addActionListener(event -> startLocalServer());
        startButton.setToolTipText("Run the queue server on this computer");

        Timer clock = new Timer(1000, event -> updateClock());
        clock.start();
        updateClock();

        Timer connectionCheck = new Timer(7000, event -> {
            if (!checking) {
                pingQuietly();
            }
        });
        connectionCheck.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                config.save();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (localServer != null) {
                localServer.stop();
            }
        }, "kiuq-launcher-shutdown"));

        SwingUtilities.invokeLater(this::initialConnect);
    }

    // ------------------------------------------------------------------- UI

    private JPanel buildHero() {
        JPanel hero = new JPanel(new BorderLayout(20, 0)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, Theme.NAVY_DEEP, getWidth(), getHeight(), Theme.NAVY_LIGHT));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 9));
                g2.fillOval(getWidth() - 330, -170, 460, 460);
                g2.setColor(new Color(245, 179, 1, 26));
                g2.fillOval(getWidth() - 150, 58, 190, 190);
                g2.dispose();
            }
        };
        hero.setOpaque(false);
        hero.setBorder(new EmptyBorder(22, 32, 22, 32));

        JPanel left = Ui.panel(new BorderLayout(0, 8));
        left.add(Brand.lockup(true, "Queue management · Finance Department", 26), BorderLayout.NORTH);
        JLabel tagline = Ui.whiteTitle("Student finance, without the queue.", 25);
        tagline.setBorder(new EmptyBorder(6, 0, 0, 0));
        left.add(tagline, BorderLayout.CENTER);
        JLabel sub = new JLabel("Issue queue numbers, call students to counters and keep the waiting room informed.");
        sub.setFont(Theme.font(12.5f));
        sub.setForeground(Theme.WHITE_70);
        left.add(sub, BorderLayout.SOUTH);

        JPanel right = Ui.panel(new BorderLayout(0, 2));
        clockLabel.setFont(Theme.font(Font.BOLD, 27));
        clockLabel.setForeground(Color.WHITE);
        clockLabel.setHorizontalAlignment(JLabel.RIGHT);
        dateLabel.setFont(Theme.font(11.5f));
        dateLabel.setForeground(Theme.WHITE_70);
        dateLabel.setHorizontalAlignment(JLabel.RIGHT);
        right.add(clockLabel, BorderLayout.CENTER);
        right.add(dateLabel, BorderLayout.SOUTH);
        right.setBorder(new EmptyBorder(10, 0, 10, 0));

        hero.add(left, BorderLayout.CENTER);
        hero.add(right, BorderLayout.EAST);
        return hero;
    }

    private JPanel buildBody() {
        JPanel body = Ui.panel(new BorderLayout(0, 16));
        body.setBorder(new EmptyBorder(20, 30, 14, 30));

        JPanel grid = Ui.panel(new GridLayout(2, 2, 16, 16));
        grid.add(new RoleCard("01", "Student kiosk",
                "Issue a 3-digit queue number in seconds.",
                "Open kiosk", Theme.NAVY_SOFT, Theme.NAVY,
                () -> open(() -> new StudentKioskFrame(api))));
        grid.add(new RoleCard("02", "Teller terminal",
                "Call students, set availability, finish and move on.",
                "Sign in", Theme.GREEN_SOFT, Theme.GREEN,
                this::openTeller));
        grid.add(new RoleCard("03", "Waiting-room display",
                "A live board showing who is being served right now.",
                "Open display", Theme.GOLD_SOFT, Theme.AMBER,
                () -> open(() -> new DisplayFrame(api))));
        grid.add(new RoleCard("04", "Admin console",
                "Create tellers, watch the queue and reset the day.",
                "Sign in", Theme.PURPLE_SOFT, Theme.PURPLE,
                this::openAdmin));

        body.add(grid, BorderLayout.CENTER);
        body.add(buildConnectionBar(), BorderLayout.SOUTH);
        return body;
    }

    private JPanel buildConnectionBar() {
        Card bar = new Card(new BorderLayout(16, 0));
        bar.setPadding(13, 18, 13, 18);

        JPanel state = Ui.panel(new BorderLayout(9, 0));
        statusDot.setPreferredSize(new Dimension(12, 12));
        JPanel dotWrap = Ui.panel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        dotWrap.setPreferredSize(new Dimension(12, 20));
        dotWrap.add(statusDot);
        state.add(dotWrap, BorderLayout.WEST);

        JPanel text = Ui.panel(new BorderLayout(0, 2));
        statusTitle.setFont(Theme.font(Font.BOLD, 12.5f));
        statusTitle.setForeground(Theme.TEXT);
        statusDetail.setFont(Theme.font(10.5f));
        statusDetail.setForeground(Theme.MUTED);
        text.add(statusTitle, BorderLayout.NORTH);
        text.add(statusDetail, BorderLayout.CENTER);
        state.add(text, BorderLayout.CENTER);
        state.setPreferredSize(new Dimension(315, 44));

        JPanel controls = Ui.panel(new BorderLayout(8, 0));
        serverField.setToolTipText("For example http://192.168.1.10:8765");
        FlatButton connect = new FlatButton("Connect", FlatButton.Kind.PRIMARY);
        connect.setPadding(9, 16);
        connect.addActionListener(event -> {
            autoStartTried = false;
            connect(true);
        });
        startButton.setPadding(9, 14);
        JPanel buttons = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(connect);
        buttons.add(startButton);
        controls.add(serverField, BorderLayout.CENTER);
        controls.add(buttons, BorderLayout.EAST);

        JPanel right = Ui.panel(new BorderLayout(0, 4));
        right.add(controls, BorderLayout.CENTER);
        JPanel options = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        options.add(autoStart);
        right.add(options, BorderLayout.SOUTH);

        bar.add(state, BorderLayout.WEST);
        bar.add(right, BorderLayout.CENTER);
        return bar;
    }

    private JPanel buildFooter() {
        JPanel footer = Ui.panel(new BorderLayout(10, 0));
        footer.setBorder(new EmptyBorder(0, 32, 12, 32));
        JLabel left = Ui.muted("KIU digitalQ 1.0 · Finance Department · Kampala International University", 10.5f);
        JLabel right = Ui.muted("Tokens 000–999 · queue resets at midnight", 10.5f);
        footer.add(left, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    private void updateClock() {
        LocalTime now = LocalTime.now();
        clockLabel.setText(Ui.clock(now));
        dateLabel.setText(LocalDate.now().format(DATE));
    }

    // -------------------------------------------------------------- roles

    private void open(Runnable frameFactory) {
        if (!connected) {
            showNotConnected();
            return;
        }
        frameFactory.run();
    }

    private void showNotConnected() {
        JOptionPane.showMessageDialog(this,
                "Not connected to the queue server.\n\n"
                        + "Check the server address below, then press Connect.\n"
                        + "If this is the main computer, press \"Start local server\".",
                "KIU digitalQ", JOptionPane.WARNING_MESSAGE);
    }

    private void openTeller() {
        if (!connected) {
            showNotConnected();
            return;
        }
        TellerSession session = TellerLoginDialog.show(this, api);
        if (session != null) {
            TellerFrame frame = new TellerFrame(api, session);
            frame.setVisible(true);
        }
    }

    private void openAdmin() {
        if (!connected) {
            showNotConnected();
            return;
        }
        String token = AdminLoginDialog.show(this, api);
        if (token != null) {
            AdminFrame frame = new AdminFrame(api, token);
            frame.setVisible(true);
        }
    }

    // --------------------------------------------------------- connection

    private void initialConnect() {
        if (api.isLocalHost() && config.autoStartServer()) {
            setStatus("Starting local server…", "Preparing the queue on this computer.", Theme.AMBER);
            startLocalServer();
        } else {
            connect(false);
        }
    }

    private void connect(boolean allowAutoStart) {
        if (checking) {
            return;
        }
        checking = true;
        String url = ApiClient.normalise(serverField.getText());
        serverField.setText(url);
        api = new ApiClient(url);
        config.setServerUrl(url);
        config.save();
        setStatus("Connecting…", "Checking " + url, Theme.AMBER);
        startButton.setEnabled(api.isLocalHost() && localServer == null);
        Async.run(api::ping, ping -> {
            checking = false;
            connected = true;
            config.save();
            showConnected(ping);
        }, error -> {
            checking = false;
            connected = false;
            if (allowAutoStart && autoStart.isSelected() && api.isLocalHost() && !autoStartTried) {
                autoStartTried = true;
                startLocalServer();
            } else {
                showOffline(error == null ? "Could not connect." : error.getMessage());
            }
        });
    }

    private void startLocalServer() {
        if (localServer != null || checking) {
            return;
        }
        int port = api.port();
        checking = true;
        startButton.setEnabled(false);
        startButton.setText("Starting…");
        setStatus("Starting the queue server…", "Creating the data folder and demo counters on first run.", Theme.AMBER);
        Async.run(() -> {
            try (ServerSocket probe = new ServerSocket()) {
                probe.setReuseAddress(false);
                probe.bind(new InetSocketAddress(port));
            } catch (IOException ex) {
                throw new IOException("Port " + port + " is already in use. Another server may already be running.", ex);
            }
            QueueServer server = new QueueServer(port, Path.of("data"));
            server.start();
            return server;
        }, server -> {
            checking = false;
            localServer = server;
            serverField.setText("http://localhost:" + port);
            startButton.setText("Server running");
            startButton.setEnabled(false);
            setStatus("Local server started", "Connecting to the queue…", Theme.AMBER);
            api = new ApiClient("http://localhost:" + port);
            connect(false);
        }, error -> {
            checking = false;
            startButton.setEnabled(true);
            startButton.setText("Start local server");
            String message = error == null ? "Could not start the server." : error.getMessage();
            Async.run(api::ping, ping -> {
                connected = true;
                showConnected(ping);
            }, pingError -> {
                connected = false;
                showOffline(message);
            });
        });
    }

    private void pingQuietly() {
        ApiClient current = api;
        if (current == null) {
            return;
        }
        Async.run(current::ping, ping -> {
            connected = true;
            if (!checking) {
                showConnected(ping);
            }
        }, error -> {
            if (connected && !checking) {
                connected = false;
                showOffline("Connection to the server was lost.");
            }
        });
    }

    private void setStatus(String title, String detail, Color color) {
        statusTitle.setText(title);
        statusDetail.setText(detail == null ? " " : detail);
        statusDot.setColor(color);
        startButton.setEnabled(api.isLocalHost() && localServer == null);
    }

    private void showConnected(JsonObject ping) {
        statusDot.setColor(Theme.GREEN);
        statusTitle.setText("Queue server connected");
        String where = api.isLocalHost() ? "on this computer" : "on " + api.host();
        String waiting = ping.str("waiting", "0");
        String tellers = ping.str("tellers", "0");
        statusDetail.setText(where + " · " + tellers + " counters · " + waiting + " waiting");
        statusDetail.setToolTipText(api.baseUrl() + " · " + ping.str("dayLabel"));
        startButton.setText(localServer != null ? "Server running" : "Start local server");
        startButton.setEnabled(api.isLocalHost() && localServer == null);
    }

    private void showOffline(String message) {
        statusDot.setColor(Theme.RED);
        statusTitle.setText("Queue server is offline");
        statusDetail.setText("Not connected — press Connect to retry.");
        statusDetail.setToolTipText(api.baseUrl() + " — " + message);
        startButton.setText("Start local server");
        startButton.setEnabled(api.isLocalHost() && localServer == null);
    }

    // ------------------------------------------------------------- role card

    private final class RoleCard extends Card {

        private final Color accent;
        private final Runnable action;

        RoleCard(String number, String title, String description, String actionText,
                 Color accentSoft, Color accent, Runnable action) {
            super(new BorderLayout(0, 8));
            this.accent = accent;
            this.action = action;
            setPadding(20, 22, 18, 22);
            setBorderColor(Theme.LINE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPanel top = Ui.panel(new BorderLayout(14, 0));
            top.add(Ui.iconTile(number, accentSoft, accent, 46), BorderLayout.WEST);
            JPanel titles = Ui.panel(new BorderLayout(0, 3));
            titles.add(Ui.title(title, 16.5f), BorderLayout.NORTH);
            titles.add(Ui.muted(description, 11.5f), BorderLayout.CENTER);
            top.add(titles, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            JLabel actionLabel = new JLabel(actionText + "  →");
            actionLabel.setFont(Theme.font(Font.BOLD, 12));
            actionLabel.setForeground(accent);
            add(actionLabel, BorderLayout.SOUTH);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    setBorderColor(accent);
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    setBorderColor(Theme.LINE);
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    activate();
                }
            };
            installMouse(this, mouse);
        }

        private void activate() {
            if (connected) {
                action.run();
            } else {
                showNotConnected();
            }
        }

        private void installMouse(Component component, MouseAdapter adapter) {
            component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            component.addMouseListener(adapter);
            if (component instanceof java.awt.Container container) {
                for (Component child : container.getComponents()) {
                    installMouse(child, adapter);
                }
            }
        }
    }
}
