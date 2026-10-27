package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json;
import com.kiu.digitalq.json.Json.JsonArray;
import com.kiu.digitalq.json.Json.JsonObject;
import com.kiu.digitalq.model.TellerStatus;
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
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Administrator console: counters, tickets, activity and settings. */
public final class AdminFrame extends JFrame {

    private static final String[] TAB_NAMES =
            {"Overview", "Counters", "Queue", "Activity", "Settings"};

    private final ApiClient api;
    private final String token;
    private final Poller poller;

    private final Dot liveDot = new Dot(Theme.GREEN, 8);
    private final JLabel liveLabel = Ui.muted("Live", 10.5f);
    private final JLabel updatedLabel = Ui.muted(" ", 10.5f);
    private final JLabel dayHeader = Ui.muted(" ", 12);
    private final JLabel statusMessage = Ui.muted(" ", 10.5f);
    private final Timer messageTimer;

    private final CardLayoutHolder pages = new CardLayoutHolder();
    private final List<TabButton> tabButtons = new ArrayList<>();
    private int activeTab;

    // Overview
    private final JLabel statRegistered = bigValue(Theme.NAVY);
    private final JLabel statWaiting = bigValue(Theme.AMBER);
    private final JLabel statServing = bigValue(Theme.GREEN);
    private final JLabel statDone = bigValue(Theme.GREEN);
    private final JLabel statMissed = bigValue(Theme.RED);
    private final JLabel statCounters = bigValue(Theme.PURPLE);
    private final JPanel overviewCounters = Ui.panel(null);
    private final JPanel overviewWaiting = Ui.panel(new GridLayout(0, 4, 6, 6));
    private final PillLabel overviewWaitingCount = new PillLabel("0 waiting");
    private final JTextArea overviewActivity = Ui.readOnlyArea();
    private final JLabel overviewNext = new JLabel("—");
    private final JLabel overviewLast = new JLabel("—");
    private final JLabel overviewAverage = new JLabel("—");
    private final JLabel overviewReset = new JLabel("—");
    private final PillLabel overviewCountersCount = new PillLabel("0 open");

    // Counters
    private final DefaultTableModel counterModel =
            model("Counter", "Teller", "Availability", "Now serving", "Served today", "Open");
    private final JTable counterTable = table(counterModel);
    private final List<String> counterIds = new ArrayList<>();
    private final Map<String, JsonObject> tellerCache = new LinkedHashMap<>();
    private final FlatButton editTellerButton = new FlatButton("Edit", FlatButton.Kind.OUTLINE);
    private final FlatButton availabilityButton =
            new FlatButton("Availability", FlatButton.Kind.OUTLINE);
    private final FlatButton deleteTellerButton = new FlatButton("Delete", FlatButton.Kind.OUTLINE);
    private final JPopupMenu availabilityMenu = new JPopupMenu();

    // Queue
    private final DefaultTableModel queueModel =
            model("Token", "Registration", "State", "Issued", "Called", "Closed", "Teller");
    private final JTable queueTable = table(queueModel);
    private final JLabel queueSummary = Ui.muted(" ", 11.5f);
    private final JPanel queueWaiting = Ui.panel(new GridLayout(0, 10, 6, 6));
    private final PillLabel queueWaitingCount = new PillLabel("0 waiting");
    private final FlatButton queueResetButton =
            new FlatButton("Reset queue", FlatButton.Kind.DANGER);

    // Activity
    private final DefaultTableModel activityModel = model("Time", "Type", "Details");
    private final JTable activityTable = table(activityModel);
    private final PillLabel activityCount = new PillLabel("0 events");

    // Settings
    private final JPasswordField oldPassword = Ui.passwordField(14);
    private final JPasswordField newPassword = Ui.passwordField(14);
    private final JPasswordField confirmPassword = Ui.passwordField(14);
    private final JLabel passwordMessage = Ui.muted(" ", 11.5f);
    private final JLabel settingsNext = new JLabel("—");
    private final JLabel settingsAverage = new JLabel("—");
    private final JLabel settingsRegistered = new JLabel("—");
    private final JLabel settingsReset = new JLabel("—");
    private final JLabel settingsPort = new JLabel("—");
    private final JLabel settingsDataDir = new JLabel("—");
    private final JPanel defaultPasswordNote = Ui.panel(new BorderLayout(8, 0));
    private final FlatButton settingsResetButton =
            new FlatButton("Reset today's queue", FlatButton.Kind.DANGER);
    private final FlatButton passwordButton =
            new FlatButton("Update password", FlatButton.Kind.PRIMARY);

    private String tellersKey = "";
    private String waitingKey = "";
    private String ticketsKey = "";
    private String eventsKey = "";
    private boolean busy;
    private boolean sessionEnded;

    public AdminFrame(ApiClient api, String token) {
        super("KIU digitalQ — Administrator console");
        this.api = api;
        this.token = token;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 650));
        Ui.sizeWindow(this, 1340, 720);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        messageTimer = new Timer(6000, event -> {
            statusMessage.setText(" ");
            statusMessage.setForeground(Theme.MUTED);
        });
        messageTimer.setRepeats(false);

        wireActions();
        openTab(0);
        updateActionButtons();

        poller = new Poller(2000,
                () -> api.get("admin/state", "t", token),
                this::applyState,
                this::handlePollError);
        poller.startDelayed(100);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                poller.stop();
                if (!sessionEnded) {
                    sessionEnded = true;
                    Async.run(() -> api.post("admin/logout", "t", token), ignored -> {
                    }, ignored -> {
                    });
                }
            }
        });
    }

    // ------------------------------------------------------------------- UI

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(Theme.NAVY);
        header.setBorder(new EmptyBorder(15, 24, 15, 22));

        header.add(Brand.lockup(true, "Finance Department · administrator", 20),
                BorderLayout.WEST);

        JPanel live = Ui.panel(new FlowLayout(FlowLayout.CENTER, 7, 8));
        live.add(liveDot);
        live.add(liveLabel);
        header.add(live, BorderLayout.CENTER);

        JPanel right = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        updatedLabel.setForeground(Theme.WHITE_70);
        right.add(updatedLabel);
        FlatButton signOut = new FlatButton("Sign out", FlatButton.Kind.OUTLINE);
        signOut.setPadding(8, 14);
        signOut.setButtonFont(Theme.font(Font.BOLD, 11.5f));
        signOut.addActionListener(event -> signOut());
        right.add(signOut);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = Ui.panel(new BorderLayout(0, 12));
        body.setBorder(new EmptyBorder(15, 24, 8, 24));
        body.add(buildTitleAndTabs(), BorderLayout.NORTH);

        pages.panel.setOpaque(false);
        addPage("Overview", buildOverviewPage());
        addPage("Counters", buildCountersPage());
        addPage("Queue", buildQueuePage());
        addPage("Activity", buildActivityPage());
        addPage("Settings", buildSettingsPage());
        body.add(pages.panel, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildTitleAndTabs() {
        JPanel north = Ui.panel(new BorderLayout(0, 10));

        JPanel row = Ui.panel(new BorderLayout(0, 0));
        JPanel titles = Ui.panel(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = Ui.title("Today at the Finance Department", 25);
        dayHeader.setText("Loading today's queue…");
        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(dayHeader);
        row.add(titles, BorderLayout.WEST);
        row.add(Ui.muted("Everything is saved to the queue server automatically.", 10.5f),
                BorderLayout.EAST);
        north.add(row, BorderLayout.NORTH);

        JPanel tabs = Ui.panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int index = i;
            TabButton button = new TabButton(TAB_NAMES[i]);
            button.addActionListener(event -> openTab(index));
            tabButtons.add(button);
            tabs.add(button);
        }
        north.add(tabs, BorderLayout.SOUTH);
        return north;
    }

    private void addPage(String name, JPanel page) {
        page.setOpaque(false);
        pages.panel.add(page, name);
    }

    private void openTab(int index) {
        activeTab = index;
        pages.show(TAB_NAMES[index]);
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).setActive(i == index);
        }
    }

    private JPanel buildFooter() {
        JPanel footer = Ui.panel(new BorderLayout(10, 0));
        footer.setBorder(new EmptyBorder(0, 24, 10, 24));
        footer.add(statusMessage, BorderLayout.WEST);
        footer.add(Ui.muted("KIU digitalQ · tokens 000–999 · queue resets at midnight", 10f),
                BorderLayout.EAST);
        return footer;
    }

    // --------------------------------------------------------------- overview

    private JPanel buildOverviewPage() {
        JPanel page = Ui.panel(new BorderLayout(0, 14));

        JPanel stats = Ui.panel(new GridLayout(1, 6, 12, 0));
        stats.add(statCard("Registered today", statRegistered));
        stats.add(statCard("Waiting now", statWaiting));
        stats.add(statCard("Being served", statServing));
        stats.add(statCard("Completed", statDone));
        stats.add(statCard("No-shows", statMissed));
        stats.add(statCard("Counters open", statCounters));
        page.add(stats, BorderLayout.NORTH);

        JPanel columns = Ui.panel(new BorderLayout(14, 0));
        columns.add(buildOverviewCountersCard(), BorderLayout.CENTER);
        columns.add(buildOverviewSide(), BorderLayout.EAST);
        page.add(columns, BorderLayout.CENTER);
        return page;
    }

    private Card statCard(String caption, JLabel value) {
        Card card = new Card(new BorderLayout(0, 2));
        card.setPadding(12, 16, 11, 16);
        JLabel captionLabel = Ui.overline(caption);
        card.add(captionLabel, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private static JLabel bigValue(Color color) {
        JLabel label = new JLabel("0");
        label.setFont(Theme.font(Font.BOLD, 29));
        label.setForeground(color);
        return label;
    }

    private Card buildOverviewCountersCard() {
        Card card = new Card(new BorderLayout(0, 8));
        card.setPadding(15, 17, 12, 17);

        JPanel head = Ui.panel(new BorderLayout());
        JLabel title = Ui.title("Counters at a glance", 15);
        head.add(title, BorderLayout.WEST);
        overviewCountersCount.setPill("0 open", Theme.NAVY, Theme.NAVY_SOFT);
        head.add(overviewCountersCount, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        overviewCounters.setLayout(new BoxLayout(overviewCounters, BoxLayout.Y_AXIS));
        JScrollPane scroll = Ui.scroll(overviewCounters);
        scroll.getViewport().setBackground(Color.WHITE);
        card.add(scroll, BorderLayout.CENTER);

        JLabel note = Ui.muted("Tellers set their own availability from their terminal.", 10.5f);
        note.setBorder(new EmptyBorder(6, 0, 0, 0));
        card.add(note, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildOverviewSide() {
        JPanel side = Ui.panel(null);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(345, 0));

        Card waiting = new Card(new BorderLayout(0, 8));
        waiting.setPadding(14, 16, 12, 16);
        waiting.setAlignmentX(Component.LEFT_ALIGNMENT);
        waiting.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        JPanel waitHead = Ui.panel(new BorderLayout());
        waitHead.add(Ui.title("Waiting room", 14.5f), BorderLayout.WEST);
        overviewWaitingCount.setPill("0 waiting", Theme.NAVY, Theme.NAVY_SOFT);
        waitHead.add(overviewWaitingCount, BorderLayout.EAST);
        waiting.add(waitHead, BorderLayout.NORTH);
        JScrollPane waitScroll = Ui.scroll(overviewWaiting);
        waitScroll.setPreferredSize(new Dimension(250, 110));
        waiting.add(waitScroll, BorderLayout.CENTER);
        side.add(waiting);
        side.add(Box.createVerticalStrut(12));

        Card day = new Card(new GridLayout(2, 2, 12, 12));
        day.setPadding(14, 16, 14, 16);
        day.setAlignmentX(Component.LEFT_ALIGNMENT);
        day.setMaximumSize(new Dimension(Integer.MAX_VALUE, 136));
        day.add(infoBlock("Next token", overviewNext, 17, Theme.NAVY));
        day.add(infoBlock("Last token", overviewLast, 17, Theme.AMBER));
        day.add(infoBlock("Average service", overviewAverage, 17, Theme.GREEN));
        day.add(infoBlock("Resets in", overviewReset, 17, Theme.TEXT));
        side.add(day);
        side.add(Box.createVerticalStrut(12));

        Card activity = new Card(new BorderLayout(0, 8));
        activity.setPadding(14, 16, 12, 16);
        activity.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel activityTitle = Ui.title("Recent activity", 14.5f);
        activity.add(activityTitle, BorderLayout.NORTH);
        overviewActivity.setRows(6);
        overviewActivity.setFont(Theme.font(10.6f));
        JScrollPane activityScroll = Ui.scroll(overviewActivity);
        activity.add(activityScroll, BorderLayout.CENTER);
        activity.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        side.add(activity);
        return side;
    }

    private JPanel infoBlock(String caption, JLabel value, float size, Color color) {
        JPanel block = Ui.panel(new BorderLayout(0, 2));
        block.add(Ui.overline(caption), BorderLayout.NORTH);
        value.setFont(Theme.font(Font.BOLD, size));
        value.setForeground(color);
        block.add(value, BorderLayout.CENTER);
        return block;
    }

    // --------------------------------------------------------------- counters

    private JPanel buildCountersPage() {
        JPanel page = Ui.panel(new BorderLayout(0, 12));

        JPanel head = Ui.panel(new BorderLayout(12, 0));
        JPanel titles = Ui.panel(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.title("Counters & tellers", 20));
        titles.add(Box.createVerticalStrut(2));
        titles.add(Ui.muted("Create counters, edit PINs and override availability.", 11.5f));
        head.add(titles, BorderLayout.WEST);

        FlatButton addTeller = new FlatButton("+  Add teller", FlatButton.Kind.PRIMARY);
        addTeller.setPadding(9, 15);
        addTeller.setButtonFont(Theme.font(Font.BOLD, 12));
        addTeller.addActionListener(event -> addTeller());

        editTellerButton.setPadding(9, 14);
        editTellerButton.setButtonFont(Theme.font(Font.BOLD, 12));
        editTellerButton.addActionListener(event -> editTeller());

        availabilityButton.setPadding(9, 14);
        availabilityButton.setButtonFont(Theme.font(Font.BOLD, 12));
        availabilityButton.addActionListener(event -> showAvailabilityMenu());

        deleteTellerButton.setPadding(9, 14);
        deleteTellerButton.setButtonFont(Theme.font(Font.BOLD, 12));
        deleteTellerButton.setForeground(Theme.RED);
        deleteTellerButton.addActionListener(event -> deleteTeller());

        JPanel buttons = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(availabilityButton);
        buttons.add(editTellerButton);
        buttons.add(deleteTellerButton);
        buttons.add(addTeller);
        head.add(buttons, BorderLayout.EAST);
        page.add(head, BorderLayout.NORTH);

        Card tableCard = new Card(new BorderLayout(0, 8));
        tableCard.setPadding(10, 8, 12, 8);
        counterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        counterTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateActionButtons();
            }
        });
        counterTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && counterTable.getSelectedRow() >= 0) {
                    editTeller();
                }
            }
        });
        counterTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        counterTable.getColumnModel().getColumn(1).setPreferredWidth(230);
        counterTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        counterTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        counterTable.getColumnModel().getColumn(4).setPreferredWidth(110);
        counterTable.getColumnModel().getColumn(5).setPreferredWidth(70);
        counterTable.getColumnModel().getColumn(2).setCellRenderer(new StatusRenderer());
        tableCard.add(Ui.scroll(counterTable), BorderLayout.CENTER);

        JLabel hint = Ui.muted(
                "Double-click a teller to edit. Availability changes are also logged in Activity.",
                10.5f);
        hint.setBorder(new EmptyBorder(4, 4, 0, 0));
        tableCard.add(hint, BorderLayout.SOUTH);
        page.add(tableCard, BorderLayout.CENTER);

        for (TellerStatus status : TellerStatus.values()) {
            if (status == TellerStatus.SERVING) {
                continue;
            }
            JMenuItem item = new JMenuItem(status.label());
            item.setFont(Theme.font(12));
            item.addActionListener(event -> setSelectedTellerStatus(status));
            availabilityMenu.add(item);
        }
        return page;
    }

    // ------------------------------------------------------------------ queue

    private JPanel buildQueuePage() {
        JPanel page = Ui.panel(new BorderLayout(0, 12));

        JPanel head = Ui.panel(new BorderLayout(12, 0));
        JPanel titles = Ui.panel(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.title("Today's queue", 20));
        titles.add(Box.createVerticalStrut(2));
        queueSummary.setText(" ");
        titles.add(queueSummary);
        head.add(titles, BorderLayout.WEST);

        queueResetButton.setPadding(9, 15);
        queueResetButton.setButtonFont(Theme.font(Font.BOLD, 12));
        queueResetButton.addActionListener(event -> resetQueue());
        head.add(queueResetButton, BorderLayout.EAST);
        page.add(head, BorderLayout.NORTH);

        Card waiting = new Card(new BorderLayout(0, 6));
        waiting.setPadding(12, 16, 11, 16);
        JPanel waitHead = Ui.panel(new BorderLayout());
        waitHead.add(Ui.overline("Waiting now"), BorderLayout.WEST);
        queueWaitingCount.setPill("0 waiting", Theme.NAVY, Theme.NAVY_SOFT);
        waitHead.add(queueWaitingCount, BorderLayout.EAST);
        waiting.add(waitHead, BorderLayout.NORTH);
        JScrollPane waitScroll = Ui.scroll(queueWaiting);
        waitScroll.setPreferredSize(new Dimension(250, 62));
        waiting.add(waitScroll, BorderLayout.CENTER);

        Card tableCard = new Card(new BorderLayout());
        tableCard.setPadding(10, 8, 12, 8);
        queueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        queueTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        queueTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        queueTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        queueTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        queueTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        queueTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        queueTable.getColumnModel().getColumn(6).setPreferredWidth(170);
        queueTable.getColumnModel().getColumn(2).setCellRenderer(new StateRenderer());
        tableCard.add(Ui.scroll(queueTable), BorderLayout.CENTER);

        JPanel main = Ui.panel(new BorderLayout(0, 12));
        main.add(waiting, BorderLayout.NORTH);
        main.add(tableCard, BorderLayout.CENTER);
        page.add(main, BorderLayout.CENTER);
        return page;
    }

    // --------------------------------------------------------------- activity

    private JPanel buildActivityPage() {
        JPanel page = Ui.panel(new BorderLayout(0, 12));

        JPanel head = Ui.panel(new BorderLayout(12, 0));
        JPanel titles = Ui.panel(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Ui.title("Activity log", 20));
        titles.add(Box.createVerticalStrut(2));
        titles.add(Ui.muted("Issues, calls, completions, no-shows and administrator changes.", 11.5f));
        head.add(titles, BorderLayout.WEST);
        activityCount.setPill("0 events", Theme.NAVY, Theme.NAVY_SOFT);
        JPanel countWrap = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        countWrap.add(activityCount);
        head.add(countWrap, BorderLayout.EAST);
        page.add(head, BorderLayout.NORTH);

        Card tableCard = new Card(new BorderLayout());
        tableCard.setPadding(10, 8, 12, 8);
        activityTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        activityTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        activityTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        activityTable.getColumnModel().getColumn(2).setPreferredWidth(800);
        activityTable.getColumnModel().getColumn(1).setCellRenderer(new KindRenderer());
        tableCard.add(Ui.scroll(activityTable), BorderLayout.CENTER);
        page.add(tableCard, BorderLayout.CENTER);
        return page;
    }

    // --------------------------------------------------------------- settings

    private JPanel buildSettingsPage() {
        JPanel content = Ui.panel(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(0, 2, 12, 2));

        Card rules = new Card(new BorderLayout(0, 12));
        rules.setPadding(18, 20, 18, 20);
        rules.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel rulesTitle = Ui.title("Queue rules & server", 15.5f);
        rules.add(rulesTitle, BorderLayout.NORTH);

        JPanel grid = Ui.panel(new GridLayout(3, 2, 14, 10));
        grid.add(infoBlock("Next token", settingsNext, 16, Theme.NAVY));
        grid.add(infoBlock("Average service", settingsAverage, 16, Theme.GREEN));
        grid.add(infoBlock("Tokens issued today", settingsRegistered, 16, Theme.AMBER));
        grid.add(infoBlock("Resets in", settingsReset, 16, Theme.TEXT));
        grid.add(infoBlock("Server port", settingsPort, 16, Theme.PURPLE));
        settingsDataDir.setFont(Theme.mono(10.5f));
        settingsDataDir.setForeground(Theme.MUTED);
        JPanel dataBlock = Ui.panel(new BorderLayout(0, 2));
        dataBlock.add(Ui.overline("Data folder"), BorderLayout.NORTH);
        dataBlock.add(settingsDataDir, BorderLayout.CENTER);
        grid.add(dataBlock);
        rules.add(grid, BorderLayout.CENTER);

        JLabel rulesNote = Ui.muted(
                "Tokens are three digits and start at 000 every day. The queue clears itself at midnight.",
                10.5f);
        rules.add(rulesNote, BorderLayout.SOUTH);
        content.add(rules);
        content.add(Box.createVerticalStrut(14));

        Card password = new Card(new BorderLayout(0, 12));
        password.setPadding(18, 20, 18, 20);
        password.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel passwordHead = Ui.panel(new BorderLayout());
        passwordHead.add(Ui.title("Administrator password", 15.5f), BorderLayout.WEST);
        password.add(passwordHead, BorderLayout.NORTH);

        JPanel passwordBody = Ui.panel(null);
        passwordBody.setLayout(new BoxLayout(passwordBody, BoxLayout.Y_AXIS));

        defaultPasswordNote.setOpaque(true);
        defaultPasswordNote.setBackground(Theme.AMBER_SOFT);
        defaultPasswordNote.setBorder(new EmptyBorder(9, 12, 9, 12));
        defaultPasswordNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        defaultPasswordNote.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        PillLabel defaultPill = new PillLabel("Default password");
        defaultPill.setPill("Default password", Theme.AMBER, Theme.GOLD_SOFT);
        defaultPill.setFont(Theme.font(Font.BOLD, 10));
        defaultPasswordNote.add(defaultPill, BorderLayout.WEST);
        JLabel defaultText = Ui.muted(
                "The default password admin123 is still in use. Please change it.", 11);
        defaultText.setForeground(Theme.AMBER);
        defaultPasswordNote.add(defaultText, BorderLayout.CENTER);
        passwordBody.add(defaultPasswordNote);
        passwordBody.add(Box.createVerticalStrut(12));

        JPanel fields = Ui.panel(new GridLayout(1, 3, 12, 0));
        fields.add(passwordField("Current password", oldPassword));
        fields.add(passwordField("New password", newPassword));
        fields.add(passwordField("Confirm new password", confirmPassword));
        fields.setAlignmentX(Component.LEFT_ALIGNMENT);
        fields.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        passwordBody.add(fields);
        passwordBody.add(Box.createVerticalStrut(7));
        JLabel passwordHint = Ui.muted(
                "Use at least 6 characters. Changing the password signs out other admin consoles.",
                10.5f);
        passwordHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordBody.add(passwordHint);
        passwordBody.add(Box.createVerticalStrut(10));

        passwordButton.setPadding(10, 16);
        passwordButton.setButtonFont(Theme.font(Font.BOLD, 12.5f));
        passwordButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordButton.addActionListener(event -> changePassword());
        passwordBody.add(passwordButton);
        passwordBody.add(Box.createVerticalStrut(6));
        passwordMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordBody.add(passwordMessage);
        password.add(passwordBody, BorderLayout.CENTER);
        content.add(password);
        content.add(Box.createVerticalStrut(14));

        Card danger = new Card(new BorderLayout(14, 0));
        danger.setPadding(18, 20, 18, 20);
        danger.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel dangerText = Ui.panel(null);
        dangerText.setLayout(new BoxLayout(dangerText, BoxLayout.Y_AXIS));
        dangerText.add(Ui.title("Reset the queue", 15.5f));
        dangerText.add(Box.createVerticalStrut(3));
        dangerText.add(Ui.muted(
                "Clears today's tickets and counters, and starts again at 000. "
                        + "Use this only when the queue has to restart.", 11));
        danger.add(dangerText, BorderLayout.CENTER);
        settingsResetButton.setPadding(10, 16);
        settingsResetButton.setButtonFont(Theme.font(Font.BOLD, 12));
        settingsResetButton.addActionListener(event -> resetQueue());
        danger.add(settingsResetButton, BorderLayout.EAST);
        content.add(danger);

        JScrollPane scroll = Ui.scroll(content);
        scroll.getViewport().setBackground(Theme.BG);
        JPanel page = Ui.panel(new BorderLayout());
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private JPanel passwordField(String caption, JPasswordField field) {
        JPanel block = Ui.panel(new BorderLayout(0, 5));
        JLabel label = Ui.overline(caption);
        block.add(label, BorderLayout.NORTH);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        block.add(field, BorderLayout.CENTER);
        return block;
    }

    // --------------------------------------------------------------- actions

    private void wireActions() {
        // Wired during construction where the action belongs to a control.
    }

    private void addTeller() {
        int suggested = 1;
        for (JsonObject teller : tellerCache.values()) {
            suggested = Math.max(suggested, teller.integer("counter") + 1);
        }
        suggested = Math.min(99, suggested);
        if (TellerDialog.show(this, api, token, null, suggested)) {
            showMessage("Teller saved.", Theme.GREEN);
            poller.refreshNow();
        }
    }

    private void editTeller() {
        JsonObject teller = selectedTeller();
        if (teller == null) {
            return;
        }
        if (TellerDialog.show(this, api, token, teller, teller.integer("counter"))) {
            showMessage("Teller updated.", Theme.GREEN);
            poller.refreshNow();
        }
    }

    private void deleteTeller() {
        JsonObject teller = selectedTeller();
        if (teller == null) {
            return;
        }
        String extra = teller.bool("serving")
                ? "\n\nThis teller is currently serving ticket " + teller.str("currentToken") + "."
                : "";
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete " + teller.str("name") + " and Counter " + teller.integer("counter") + "?"
                        + extra + "\n\nThis cannot be undone.",
                "Delete teller", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        perform(() -> api.post("admin/teller/delete", "t", token, "id", teller.str("id")),
                "Deleting teller…", null);
    }

    private void showAvailabilityMenu() {
        JsonObject teller = selectedTeller();
        if (teller == null) {
            return;
        }
        availabilityMenu.show(availabilityButton, 0, availabilityButton.getHeight());
    }

    private void setSelectedTellerStatus(TellerStatus status) {
        JsonObject teller = selectedTeller();
        if (teller == null) {
            return;
        }
        if (teller.bool("serving") && status != TellerStatus.AVAILABLE) {
            int answer = JOptionPane.showConfirmDialog(this,
                    teller.str("name") + " is serving ticket " + teller.str("currentToken") + ".\n\n"
                            + "Changing availability will return that ticket to the queue. Continue?",
                    "Change availability", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        perform(() -> api.post("admin/teller/status", "t", token,
                        "id", teller.str("id"), "status", status.name()),
                "Updating availability…", null);
    }

    private void resetQueue() {
        if (busy) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Reset today's queue?\n\n"
                        + "All tickets will be cleared, tellers will be marked ready and the next\n"
                        + "token will start again at 000. This cannot be undone.",
                "Reset queue", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        busy = true;
        updateActionButtons();
        showMessage("Resetting the queue…", Theme.MUTED);
        Async.run(() -> api.post("admin/reset", "t", token), state -> {
            busy = false;
            applyState(state);
            updateActionButtons();
            showMessage("Queue reset. Tokens start again at 000.", Theme.GREEN);
            poller.refreshNow();
        }, error -> {
            busy = false;
            updateActionButtons();
            handleActionError(error);
        });
    }

    private void changePassword() {
        if (busy) {
            return;
        }
        String oldValue = new String(oldPassword.getPassword());
        String newValue = new String(newPassword.getPassword());
        String confirmation = new String(confirmPassword.getPassword());
        if (oldValue.isEmpty()) {
            Ui.error(passwordMessage, "Enter your current password.");
            oldPassword.requestFocusInWindow();
            return;
        }
        if (newValue.length() < 6) {
            Ui.error(passwordMessage, "The new password must be at least 6 characters.");
            newPassword.requestFocusInWindow();
            return;
        }
        if (!newValue.equals(confirmation)) {
            Ui.error(passwordMessage, "The two new passwords do not match.");
            confirmPassword.requestFocusInWindow();
            return;
        }
        busy = true;
        updateActionButtons();
        passwordButton.setEnabled(false);
        Ui.muted(passwordMessage, "Updating password…");
        Async.run(() -> api.post("admin/password", "t", token,
                        "oldPassword", oldValue, "newPassword", newValue),
                response -> {
                    busy = false;
                    passwordButton.setEnabled(true);
                    updateActionButtons();
                    oldPassword.setText("");
                    newPassword.setText("");
                    confirmPassword.setText("");
                    Ui.success(passwordMessage, response.str("message", "Password updated."));
                    showMessage("Administrator password updated.", Theme.GREEN);
                    poller.refreshNow();
                }, error -> {
                    busy = false;
                    passwordButton.setEnabled(true);
                    updateActionButtons();
                    if (isSessionProblem(error)) {
                        endSession(error.getMessage());
                        return;
                    }
                    Ui.error(passwordMessage, error.getMessage());
                    showMessage(error.getMessage(), Theme.RED);
                });
    }

    private void perform(Async.Task<JsonObject> task, String progress, String successMessage) {
        if (busy) {
            return;
        }
        busy = true;
        updateActionButtons();
        showMessage(progress, Theme.MUTED);
        Async.run(task, response -> {
            busy = false;
            updateActionButtons();
            showMessage(successMessage == null
                    ? response.str("message", "Saved.") : successMessage, Theme.GREEN);
            poller.refreshNow();
        }, error -> {
            busy = false;
            updateActionButtons();
            handleActionError(error);
        });
    }

    private void handleActionError(Exception error) {
        if (isSessionProblem(error)) {
            endSession(error.getMessage());
            return;
        }
        showMessage(error.getMessage() == null ? "The action failed." : error.getMessage(), Theme.RED);
        if (error instanceof ApiClient.ApiException apiError && apiError.isConnectionProblem()) {
            liveDot.setColor(Theme.RED);
            liveLabel.setText("Offline");
            liveLabel.setForeground(Theme.RED);
        }
    }

    private JsonObject selectedTeller() {
        int row = counterTable.getSelectedRow();
        if (row < 0 || row >= counterIds.size()) {
            return null;
        }
        return tellerCache.get(counterIds.get(row));
    }

    private void updateActionButtons() {
        boolean hasSelection = counterTable.getSelectedRow() >= 0;
        editTellerButton.setEnabled(hasSelection && !busy);
        availabilityButton.setEnabled(hasSelection && !busy);
        deleteTellerButton.setEnabled(hasSelection && !busy);
        queueResetButton.setEnabled(!busy);
        settingsResetButton.setEnabled(!busy);
    }

    // ------------------------------------------------------------- live data

    private void applyState(JsonObject state) {
        liveDot.setColor(Theme.GREEN);
        liveLabel.setText("Live");
        liveLabel.setForeground(Theme.MUTED);
        updatedLabel.setText("Updated " + state.str("time"));
        dayHeader.setText(state.str("dayLabel"));

        JsonObject stats = state.obj("stats");
        statRegistered.setText(String.valueOf(stats.integer("registered")));
        statWaiting.setText(String.valueOf(stats.integer("waiting")));
        statServing.setText(String.valueOf(stats.integer("serving")));
        statDone.setText(String.valueOf(stats.integer("done")));
        statMissed.setText(String.valueOf(stats.integer("missed")));
        statCounters.setText(stats.integer("countersOpen") + " / " + stats.integer("counters"));
        overviewCountersCount.setPill(stats.integer("countersOpen") + " open",
                Theme.NAVY, Theme.NAVY_SOFT);

        overviewNext.setText(state.str("nextToken"));
        overviewLast.setText(state.str("lastToken").isEmpty() ? "—" : state.str("lastToken"));
        overviewAverage.setText(state.integer("avgServiceMinutes") + "m");
        overviewReset.setText(Ui.countdown(state.integer("resetInSeconds")));

        settingsNext.setText(state.str("nextToken"));
        settingsAverage.setText(state.integer("avgServiceMinutes") + "m");
        settingsRegistered.setText(String.valueOf(stats.integer("registered")));
        settingsReset.setText(Ui.countdown(state.integer("resetInSeconds")));
        settingsPort.setText(String.valueOf(state.integer("port")));
        settingsDataDir.setText(state.str("dataDir"));
        settingsDataDir.setToolTipText(state.str("dataDir"));
        defaultPasswordNote.setVisible(state.bool("adminPasswordDefault"));

        String newTellersKey = Json.write(state.arr("tellers"));
        if (!newTellersKey.equals(tellersKey)) {
            tellersKey = newTellersKey;
            updateTellers(state.arr("tellers"));
        }

        String newWaitingKey = Json.write(state.arr("waiting")) + "/" + state.integer("waitingCount");
        if (!newWaitingKey.equals(waitingKey)) {
            waitingKey = newWaitingKey;
            updateWaiting(state.arr("waiting"), state.integer("waitingCount"));
        }

        String newTicketsKey = Json.write(state.arr("tickets"));
        if (!newTicketsKey.equals(ticketsKey)) {
            ticketsKey = newTicketsKey;
            updateQueue(state.arr("tickets"));
        }

        String newEventsKey = Json.write(state.arr("events"));
        if (!newEventsKey.equals(eventsKey)) {
            eventsKey = newEventsKey;
            updateEvents(state.arr("events"));
        }
    }

    private void updateTellers(JsonArray tellers) {
        String selectedId = selectedTellerId();
        tellerCache.clear();
        counterIds.clear();
        counterModel.setRowCount(0);

        overviewCounters.removeAll();
        int open = 0;

        for (Object value : tellers) {
            if (!(value instanceof JsonObject teller)) {
                continue;
            }
            String id = teller.str("id");
            tellerCache.put(id, teller);
            counterIds.add(id);
            boolean enabled = teller.bool("enabled");
            if (enabled && !"CLOSED".equals(teller.str("status"))) {
                open++;
            }
            counterModel.addRow(new Object[]{
                    teller.integer("counter"),
                    teller.str("name"),
                    teller.str("statusLabel"),
                    teller.bool("serving") ? teller.str("currentToken") : "—",
                    teller.integer("servedToday"),
                    enabled ? "Yes" : "No"
            });
            overviewCounters.add(overviewCounterRow(teller));
        }

        if (overviewCounters.getComponentCount() == 0) {
            JLabel empty = Ui.muted("No tellers have been created yet.", 12);
            empty.setBorder(new EmptyBorder(16, 4, 16, 4));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            overviewCounters.add(empty);
        }
        overviewCounters.revalidate();
        overviewCounters.repaint();

        if (selectedId != null) {
            int row = counterIds.indexOf(selectedId);
            if (row >= 0) {
                counterTable.setRowSelectionInterval(row, row);
            }
        }
        updateActionButtons();
    }

    private String selectedTellerId() {
        int row = counterTable.getSelectedRow();
        if (row < 0 || row >= counterIds.size()) {
            return null;
        }
        return counterIds.get(row);
    }

    private JPanel overviewCounterRow(JsonObject teller) {
        JPanel row = Ui.panel(new BorderLayout(12, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE_SOFT),
                new EmptyBorder(8, 2, 8, 2)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));

        JLabel badge = Ui.iconTile("C" + teller.integer("counter"),
                Theme.NAVY_SOFT, Theme.NAVY, 36);
        row.add(badge, BorderLayout.WEST);

        JPanel who = Ui.panel(new BorderLayout(0, 1));
        JLabel name = Ui.title(teller.str("name"), 12.5f);
        who.add(name, BorderLayout.NORTH);
        who.add(Ui.muted("Served today  " + teller.integer("servedToday"), 9.8f),
                BorderLayout.SOUTH);
        row.add(who, BorderLayout.CENTER);

        JPanel right = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 9, 4));
        String status = teller.str("status");
        PillLabel statusPill = new PillLabel(teller.str("statusLabel"));
        statusPill.setPill(teller.str("statusLabel"), Theme.statusText(status),
                Theme.statusSoft(status));
        right.add(statusPill);
        JLabel token = new JLabel(teller.bool("serving") ? teller.str("currentToken") : "—");
        token.setFont(Theme.mono(19));
        token.setForeground(teller.bool("serving") ? Theme.NAVY : Theme.FAINT);
        token.setPreferredSize(new Dimension(46, 24));
        token.setHorizontalAlignment(SwingConstants.RIGHT);
        right.add(token);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private void updateWaiting(JsonArray waiting, int total) {
        overviewWaitingCount.setPill(total + " waiting", Theme.NAVY, Theme.NAVY_SOFT);
        queueWaitingCount.setPill(total + " waiting", Theme.NAVY, Theme.NAVY_SOFT);

        overviewWaiting.removeAll();
        queueWaiting.removeAll();
        int shown = 0;
        for (Object value : waiting) {
            if (!(value instanceof JsonObject entry)) {
                continue;
            }
            String token = entry.str("token");
            if (shown < 24) {
                overviewWaiting.add(waitingChip(token, 12.5f));
            }
            if (shown < 40) {
                queueWaiting.add(waitingChip(token, 13));
            }
            shown++;
        }
        if (overviewWaiting.getComponentCount() == 0) {
            JLabel empty = Ui.muted("No students waiting.", 11.5f);
            overviewWaiting.add(empty);
        }
        if (queueWaiting.getComponentCount() == 0) {
            JLabel empty = Ui.muted("No students waiting — the queue is clear.", 11.5f);
            empty.setBorder(new EmptyBorder(6, 4, 0, 0));
            queueWaiting.add(empty);
        }
        overviewWaiting.revalidate();
        overviewWaiting.repaint();
        queueWaiting.revalidate();
        queueWaiting.repaint();
    }

    private JLabel waitingChip(String token, float size) {
        PillLabel chip = new PillLabel(token);
        chip.setPill(token, Theme.NAVY, Theme.NAVY_SOFT);
        chip.setFont(Theme.font(Font.BOLD, size));
        chip.setBorder(new EmptyBorder(5, 8, 5, 8));
        return chip;
    }

    private void updateQueue(JsonArray tickets) {
        queueModel.setRowCount(0);
        int waiting = 0;
        for (int i = tickets.size() - 1; i >= 0; i--) {
            Object value = tickets.get(i);
            if (!(value instanceof JsonObject ticket)) {
                continue;
            }
            if ("WAITING".equals(ticket.str("state"))) {
                waiting++;
            }
            queueModel.addRow(new Object[]{
                    ticket.str("token"),
                    ticket.str("reg"),
                    ticket.str("stateLabel"),
                    ticket.str("issuedAt"),
                    ticket.str("calledAt").isEmpty() ? "—" : ticket.str("calledAt"),
                    ticket.str("closedAt").isEmpty() ? "—" : ticket.str("closedAt"),
                    ticket.str("tellerName").isEmpty() ? "—" : ticket.str("tellerName")
            });
        }
        int total = queueModel.getRowCount();
        queueSummary.setText(total + " tickets issued today · " + waiting + " waiting · next token "
                + (overviewNext.getText() == null ? "—" : overviewNext.getText()));
    }

    private void updateEvents(JsonArray events) {
        activityModel.setRowCount(0);
        StringBuilder activity = new StringBuilder();
        int index = 0;
        for (int i = events.size() - 1; i >= 0; i--) {
            Object value = events.get(i);
            if (!(value instanceof JsonObject event)) {
                continue;
            }
            String kind = event.str("kind");
            String kindLabel = kindLabel(kind);
            activityModel.addRow(new Object[]{
                    event.str("time"), kindLabel, event.str("text")
            });
            if (index < 8) {
                activity.append(event.str("time")).append("   ")
                        .append(event.str("text")).append('\n');
                index++;
            }
        }
        activityCount.setPill(events.size() + " events", Theme.NAVY, Theme.NAVY_SOFT);
        if (activity.length() == 0) {
            overviewActivity.setText("No activity yet today.");
            overviewActivity.setForeground(Theme.MUTED);
        } else {
            overviewActivity.setText(activity.toString());
            overviewActivity.setForeground(Theme.TEXT);
        }
        overviewActivity.setCaretPosition(0);
    }

    private static String kindLabel(String kind) {
        return switch (kind == null ? "" : kind) {
            case "ISSUE" -> "Ticket";
            case "CALL" -> "Call";
            case "DONE" -> "Completed";
            case "MISS" -> "No-show";
            case "STATUS" -> "Status";
            case "RESET" -> "Reset";
            default -> "Info";
        };
    }

    // ------------------------------------------------------------- errors/session

    private void handlePollError(Exception error) {
        if (isSessionProblem(error)) {
            endSession(error.getMessage());
            return;
        }
        liveDot.setColor(Theme.AMBER);
        liveLabel.setText("Reconnecting…");
        liveLabel.setForeground(Theme.AMBER);
        if (error instanceof ApiClient.ApiException apiError && apiError.isConnectionProblem()) {
            liveDot.setColor(Theme.RED);
            liveLabel.setText("Offline");
            liveLabel.setForeground(Theme.RED);
        }
    }

    private static boolean isSessionProblem(Exception error) {
        return error instanceof ApiClient.ApiException apiError && apiError.isSessionProblem();
    }

    private void endSession(String message) {
        if (sessionEnded) {
            return;
        }
        sessionEnded = true;
        poller.stop();
        messageTimer.stop();
        JOptionPane.showMessageDialog(this,
                (message == null ? "Your administrator session has ended." : message)
                        + "\n\nPlease sign in again.",
                "Session ended", JOptionPane.WARNING_MESSAGE);
        dispose();
    }

    private void signOut() {
        poller.stop();
        sessionEnded = true;
        messageTimer.stop();
        Async.run(() -> api.post("admin/logout", "t", token), ignored -> {
        }, ignored -> {
        });
        dispose();
    }

    private void showMessage(String text, Color color) {
        statusMessage.setText(text == null || text.isBlank() ? " " : text);
        statusMessage.setForeground(color);
        messageTimer.restart();
    }

    // ------------------------------------------------------------- table bits

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        Ui.styleTable(table);
        return table;
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String status = switch (value == null ? "" : value.toString()) {
                case "Ready" -> "AVAILABLE";
                case "Serving" -> "SERVING";
                case "Resting" -> "RESTING";
                case "Not around" -> "NOT_AROUND";
                default -> "CLOSED";
            };
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(Theme.font(Font.BOLD, 11.5f));
            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(Theme.statusSoft(status));
                setForeground(Theme.statusText(status));
            }
            return this;
        }
    }

    private static final class StateRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String text = value == null ? "" : value.toString();
            Color foreground;
            Color background;
            switch (text) {
                case "Being served" -> {
                    foreground = Theme.GREEN;
                    background = Theme.GREEN_SOFT;
                }
                case "Completed" -> {
                    foreground = Theme.SLATE;
                    background = Theme.SLATE_SOFT;
                }
                case "Missed" -> {
                    foreground = Theme.RED;
                    background = Theme.RED_SOFT;
                }
                default -> {
                    foreground = Theme.AMBER;
                    background = Theme.AMBER_SOFT;
                }
            }
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(Theme.font(Font.BOLD, 11.5f));
            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(background);
                setForeground(foreground);
            }
            return this;
        }
    }

    private static final class KindRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String text = value == null ? "" : value.toString();
            Color foreground;
            Color background;
            switch (text) {
                case "Ticket" -> {
                    foreground = Theme.NAVY;
                    background = Theme.NAVY_SOFT;
                }
                case "Call" -> {
                    foreground = Theme.AMBER;
                    background = Theme.AMBER_SOFT;
                }
                case "Completed" -> {
                    foreground = Theme.GREEN;
                    background = Theme.GREEN_SOFT;
                }
                case "No-show" -> {
                    foreground = Theme.RED;
                    background = Theme.RED_SOFT;
                }
                case "Status" -> {
                    foreground = Theme.PURPLE;
                    background = Theme.PURPLE_SOFT;
                }
                default -> {
                    foreground = Theme.SLATE;
                    background = Theme.SLATE_SOFT;
                }
            }
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(Theme.font(Font.BOLD, 11.5f));
            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(background);
                setForeground(foreground);
            }
            return this;
        }
    }

    // --------------------------------------------------------------- tab button

    private static final class TabButton extends javax.swing.JButton {

        private boolean active;
        private boolean hover;

        TabButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(new EmptyBorder(8, 15, 8, 15));
            setFont(Theme.font(12.5f));
            setForeground(Theme.MUTED);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 12.5f));
            setForeground(active ? Theme.NAVY : Theme.MUTED);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active) {
                g2.setColor(Theme.NAVY_SOFT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            } else if (hover) {
                g2.setColor(Theme.BG_ALT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            g2.dispose();
            super.paintComponent(graphics);
        }

        @Override
        public void setBackground(Color color) {
            super.setBackground(color);
        }
    }

    // ------------------------------------------------------------- card layout

    /** Small wrapper so the page layout reads cleanly at the top of the class. */
    private static final class CardLayoutHolder {
        private final java.awt.CardLayout layout = new java.awt.CardLayout();
        private final JPanel panel = new JPanel(layout);

        void show(String name) {
            layout.show(panel, name);
        }
    }
}
