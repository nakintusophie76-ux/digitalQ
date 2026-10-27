package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json.JsonArray;
import com.kiu.digitalq.json.Json.JsonObject;
import com.kiu.digitalq.model.TellerStatus;
import com.kiu.digitalq.ui.Brand;
import com.kiu.digitalq.ui.Card;
import com.kiu.digitalq.ui.Dot;
import com.kiu.digitalq.ui.FlatButton;
import com.kiu.digitalq.ui.PillLabel;
import com.kiu.digitalq.ui.StatusRow;
import com.kiu.digitalq.ui.Theme;
import com.kiu.digitalq.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;

/** The teller's counter console: call, recall, no-show, finish and availability. */
public final class TellerFrame extends JFrame {

    private final ApiClient api;
    private final TellerSession session;
    private final Poller poller;

    private final PillLabel headerStatus = new PillLabel("Ready");
    private final Dot connectionDot = new Dot(Theme.GREEN, 8);
    private final JLabel connectionLabel = Ui.muted("Live", 10.5f);

    private final JLabel tokenLabel = new JLabel("—");
    private final JLabel regLabel = new JLabel("Loading your counter…");
    private final JLabel metaLabel = Ui.muted(" ", 12);
    private final JLabel sinceLabel = Ui.muted(" ", 10.5f);
    private final JLabel messageLabel = Ui.muted(" ", 11.5f);
    private final Card nowCard = new Card(new BorderLayout(0, 10));

    private final FlatButton finishButton = new FlatButton("Finish & call next", FlatButton.Kind.SUCCESS);
    private final FlatButton recallButton = new FlatButton("Recall", FlatButton.Kind.OUTLINE);
    private final FlatButton noShowButton = new FlatButton("No-show", FlatButton.Kind.GHOST);

    private final Map<TellerStatus, StatusRow> statusRows = new EnumMap<>(TellerStatus.class);
    private final JPanel nextPanel = Ui.panel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    private final PillLabel nextCount = new PillLabel("0 waiting");
    private final JLabel servedValue = new JLabel("0");
    private final JLabel waitingValue = new JLabel("0");
    private final JLabel averageValue = new JLabel("0m");
    private final JTextArea activityArea = Ui.readOnlyArea();

    private final Timer messageTimer;
    private boolean serving;
    private boolean busy;
    private boolean firstState = true;
    private boolean sessionEnded;
    private String lastToken = "";

    public TellerFrame(ApiClient api, TellerSession session) {
        super(session.name() + " · Counter " + session.counter() + " · KIU digitalQ");
        this.api = api;
        this.session = session;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(980, 680));
        Ui.sizeWindow(this, 1180, 700);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.BG);
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        messageTimer = new Timer(6000, event -> {
            messageLabel.setText(" ");
            messageLabel.setForeground(Theme.MUTED);
        });
        messageTimer.setRepeats(false);

        finishButton.addActionListener(event -> finishStudent());
        recallButton.addActionListener(event -> recallStudent());
        noShowButton.addActionListener(event -> noShowStudent());

        poller = new Poller(1500,
                () -> api.get("teller/state", "t", session.token()),
                this::applyState,
                this::handlePollError);
        poller.startDelayed(200);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                poller.stop();
                signOutQuietly();
            }
        });
        updateEnabled();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(24, 0));
        header.setBackground(Theme.NAVY);
        header.setBorder(new EmptyBorder(15, 26, 15, 24));

        header.add(Brand.lockup(true, "Teller terminal", 20), BorderLayout.WEST);

        JPanel who = Ui.panel(new BorderLayout(0, 2));
        JLabel name = Ui.whiteTitle(session.name(), 18);
        JLabel counter = new JLabel("Counter " + session.counter());
        counter.setFont(Theme.font(11.5f));
        counter.setForeground(Theme.WHITE_70);
        who.add(name, BorderLayout.NORTH);
        who.add(counter, BorderLayout.CENTER);

        JPanel identity = Ui.panel(new BorderLayout(14, 0));
        identity.setBorder(new EmptyBorder(0, 10, 0, 0));
        identity.add(who, BorderLayout.CENTER);
        header.add(identity, BorderLayout.CENTER);

        JPanel right = Ui.panel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        right.add(headerStatus);
        FlatButton signOut = new FlatButton("Sign out", FlatButton.Kind.OUTLINE);
        signOut.setPadding(8, 14);
        signOut.setButtonFont(Theme.font(Font.BOLD, 11.5f));
        signOut.addActionListener(event -> signOut());
        right.add(signOut);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = Ui.panel(new BorderLayout(16, 0));
        body.setBorder(new EmptyBorder(16, 22, 12, 22));

        JPanel left = Ui.panel(new BorderLayout(0, 14));

        nowCard.setPadding(24, 26, 20, 26);
        nowCard.setBorderColor(Theme.LINE);

        JPanel top = Ui.panel(new BorderLayout());
        top.add(Ui.overline("Now serving"), BorderLayout.WEST);
        sinceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        top.add(sinceLabel, BorderLayout.EAST);
        nowCard.add(top, BorderLayout.NORTH);

        JPanel tokenArea = Ui.panel(null);
        tokenArea.setLayout(new BoxLayout(tokenArea, BoxLayout.Y_AXIS));
        JLabel ticketCaption = Ui.overline("Ticket");
        ticketCaption.setAlignmentX(Component.CENTER_ALIGNMENT);
        tokenLabel.setFont(Theme.font(Font.BOLD, 100));
        tokenLabel.setForeground(Theme.NAVY);
        tokenLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        regLabel.setFont(Theme.font(Font.BOLD, 15));
        regLabel.setForeground(Theme.TEXT);
        regLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        metaLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        tokenArea.add(ticketCaption);
        tokenArea.add(Box.createVerticalStrut(2));
        tokenArea.add(tokenLabel);
        tokenArea.add(Box.createVerticalStrut(2));
        tokenArea.add(regLabel);
        tokenArea.add(Box.createVerticalStrut(4));
        tokenArea.add(metaLabel);
        nowCard.add(tokenArea, BorderLayout.CENTER);

        JPanel actions = Ui.panel(new BorderLayout(0, 8));
        JPanel buttons = Ui.panel(new GridLayout(1, 3, 9, 0));
        finishButton.setButtonFont(Theme.font(Font.BOLD, 14));
        finishButton.setPadding(13, 12);
        recallButton.setPadding(13, 12);
        noShowButton.setPadding(13, 12);
        noShowButton.setForeground(Theme.RED);
        buttons.add(finishButton);
        buttons.add(recallButton);
        buttons.add(noShowButton);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        actions.add(buttons, BorderLayout.NORTH);
        actions.add(messageLabel, BorderLayout.CENTER);
        nowCard.add(actions, BorderLayout.SOUTH);
        left.add(nowCard, BorderLayout.CENTER);

        Card nextCard = new Card(new BorderLayout(0, 8));
        nextCard.setPadding(14, 18, 13, 18);
        JPanel nextTop = Ui.panel(new BorderLayout());
        nextTop.add(Ui.overline("Next in line"), BorderLayout.WEST);
        nextCount.setPill("0 waiting", Theme.NAVY, Theme.NAVY_SOFT);
        nextTop.add(nextCount, BorderLayout.EAST);
        nextCard.add(nextTop, BorderLayout.NORTH);
        JScrollPane nextScroll = Ui.scroll(nextPanel);
        nextScroll.setPreferredSize(new Dimension(200, 58));
        nextScroll.setBorder(BorderFactory.createEmptyBorder());
        nextCard.add(nextScroll, BorderLayout.CENTER);
        left.add(nextCard, BorderLayout.SOUTH);

        body.add(left, BorderLayout.CENTER);
        body.add(buildSidebar(), BorderLayout.EAST);

        JLabel note = Ui.muted("Students are assigned automatically. Finish one to receive the next.", 10.5f);
        JPanel south = Ui.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        south.setBorder(new EmptyBorder(0, 2, 0, 0));
        south.add(note);
        body.add(south, BorderLayout.SOUTH);
        return body;
    }

    private JPanel buildSidebar() {
        JPanel side = Ui.panel(null);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(340, 0));

        Card statusCard = new Card(new BorderLayout(0, 8));
        statusCard.setPadding(16, 16, 14, 16);
        statusCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel statusHead = Ui.panel(new BorderLayout(0, 2));
        statusHead.add(Ui.title("My availability", 14.5f), BorderLayout.NORTH);
        statusHead.add(Ui.muted("Set this before you step away.", 10.5f), BorderLayout.CENTER);
        statusCard.add(statusHead, BorderLayout.NORTH);

        JPanel rows = Ui.panel(null);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        for (TellerStatus status : new TellerStatus[]{
                TellerStatus.AVAILABLE, TellerStatus.RESTING,
                TellerStatus.NOT_AROUND, TellerStatus.CLOSED}) {
            StatusRow row = new StatusRow(status, () -> changeStatus(status));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            statusRows.put(status, row);
            rows.add(row);
            rows.add(Box.createVerticalStrut(4));
        }
        statusCard.add(rows, BorderLayout.CENTER);
        statusCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 310));
        side.add(statusCard);
        side.add(Box.createVerticalStrut(12));

        Card statsCard = new Card(new GridLayout(1, 3, 6, 0));
        statsCard.setPadding(14, 12, 12, 12);
        statsCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsCard.add(statBlock(servedValue, "Served today", Theme.GREEN));
        statsCard.add(statBlock(waitingValue, "Waiting", Theme.NAVY));
        statsCard.add(statBlock(averageValue, "Avg. service", Theme.AMBER));
        statsCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        side.add(statsCard);
        side.add(Box.createVerticalStrut(12));

        Card activityCard = new Card(new BorderLayout(0, 8));
        activityCard.setPadding(14, 14, 12, 14);
        activityCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        activityCard.add(Ui.title("Recent activity", 13.5f), BorderLayout.NORTH);
        activityArea.setRows(6);
        activityArea.setFont(Theme.font(10.8f));
        JScrollPane activityScroll = Ui.scroll(activityArea);
        activityCard.add(activityScroll, BorderLayout.CENTER);
        activityCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        side.add(activityCard);

        return side;
    }

    private JPanel statBlock(JLabel value, String label, Color color) {
        JPanel block = Ui.panel(new BorderLayout(0, 0));
        value.setFont(Theme.font(Font.BOLD, 25));
        value.setForeground(color);
        value.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel caption = Ui.muted(label, 9.5f);
        caption.setHorizontalAlignment(SwingConstants.CENTER);
        block.add(value, BorderLayout.CENTER);
        block.add(caption, BorderLayout.SOUTH);
        return block;
    }

    private JPanel buildFooter() {
        JPanel footer = Ui.panel(new BorderLayout(8, 0));
        footer.setBorder(new EmptyBorder(0, 26, 10, 26));

        JPanel live = Ui.panel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        live.add(connectionDot);
        live.add(connectionLabel);
        footer.add(live, BorderLayout.WEST);
        footer.add(Ui.muted("KIU digitalQ · queue resets at midnight · tokens 000–999", 10f),
                BorderLayout.EAST);
        return footer;
    }

    // ------------------------------------------------------------- live data

    private void applyState(JsonObject state) {
        JsonObject teller = state.obj("teller");
        String status = teller.str("status");
        String statusLabel = teller.str("statusLabel", "Ready");

        headerStatus.setPill(statusLabel, Theme.statusText(status), Theme.statusSoft(status));
        connectionDot.setColor(Theme.GREEN);
        connectionLabel.setText("Live");
        connectionLabel.setForeground(Theme.MUTED);

        serving = teller.bool("serving");
        JsonObject current = teller.obj("current");
        String newToken = serving ? current.str("token") : "";

        if (serving) {
            tokenLabel.setText(newToken);
            tokenLabel.setForeground(Theme.NAVY);
            regLabel.setText("Student reg no " + current.str("reg"));
            regLabel.setForeground(Theme.TEXT);
            String meta = "Issued " + current.str("issuedAt") + "   ·   Called " + current.str("calledAt");
            if (current.integer("recalls") > 0) {
                meta += "   ·   Recalled " + current.integer("recalls") + "×";
            }
            metaLabel.setText(meta);
            sinceLabel.setText("Since " + current.str("calledAt"));
        } else {
            tokenLabel.setText("—");
            tokenLabel.setForeground(Theme.FAINT);
            regLabel.setText("No student at your counter");
            regLabel.setForeground(Theme.MUTED);
            metaLabel.setText(switch (status) {
                case "AVAILABLE" -> "You are ready — the next student will appear here automatically.";
                case "RESTING" -> "You are resting. Select Ready when you return.";
                case "NOT_AROUND" -> "You are marked as not around. Select Ready when you return.";
                case "CLOSED" -> "Your counter is closed. Select Ready to open it again.";
                default -> "Select an availability status.";
            });
            sinceLabel.setText(statusSince(teller));
        }

        for (Map.Entry<TellerStatus, StatusRow> entry : statusRows.entrySet()) {
            entry.getValue().setActive(entry.getKey().name().equalsIgnoreCase(status));
        }
        updateEnabled();

        if (!newToken.isEmpty() && !newToken.equals(lastToken) && !firstState) {
            nowCard.flash();
            Ui.beep();
        }
        lastToken = newToken;

        servedValue.setText(String.valueOf(teller.integer("servedToday")));
        waitingValue.setText(String.valueOf(state.integer("waitingCount")));
        averageValue.setText(state.integer("avgServiceMinutes") + "m");

        JsonArray nextUp = state.arr("nextUp");
        nextCount.setPill(state.integer("waitingCount") + " waiting", Theme.NAVY, Theme.NAVY_SOFT);
        nextPanel.removeAll();
        if (nextUp.isEmpty()) {
            nextPanel.add(Ui.muted("No one waiting — you have caught up.", 11.5f));
        } else {
            for (Object value : nextUp) {
                PillLabel chip = new PillLabel(String.valueOf(value));
                chip.setFont(Theme.font(Font.BOLD, 16));
                chip.setBorder(new EmptyBorder(6, 12, 6, 12));
                chip.setPill(String.valueOf(value), Theme.NAVY, Theme.NAVY_SOFT);
                nextPanel.add(chip);
            }
        }
        nextPanel.revalidate();
        nextPanel.repaint();

        StringBuilder activity = new StringBuilder();
        for (Object value : state.arr("events")) {
            if (value instanceof JsonObject event) {
                activity.append(event.str("time")).append("   ")
                        .append(event.str("text")).append('\n');
            }
        }
        activityArea.setText(activity.toString());
        activityArea.setCaretPosition(activityArea.getDocument().getLength());

        firstState = false;
    }

    private String statusSince(JsonObject teller) {
        String since = teller.str("statusSince");
        return since.isEmpty() ? " " : "Since " + since;
    }

    private void updateEnabled() {
        finishButton.setEnabled(serving && !busy);
        recallButton.setEnabled(serving && !busy);
        noShowButton.setEnabled(serving && !busy);
        for (StatusRow row : statusRows.values()) {
            row.setUsable(!serving && !busy);
        }
    }

    // --------------------------------------------------------------- actions

    private void finishStudent() {
        perform(() -> api.post("teller/finish", "t", session.token()), "Finishing…");
    }

    private void recallStudent() {
        perform(() -> api.post("teller/recall", "t", session.token()), "Calling again…");
    }

    private void noShowStudent() {
        if (!serving) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Mark this student as a no-show?\n\n"
                        + "The ticket will be closed and the next student will be called.\n"
                        + "Use Recall if the student is still on the way.",
                "No-show", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        perform(() -> api.post("teller/noshow", "t", session.token()), "Marking no-show…");
    }

    private void changeStatus(TellerStatus status) {
        if (serving || busy) {
            return;
        }
        busy = true;
        updateEnabled();
        Async.run(() -> api.post("teller/status", "t", session.token(), "status", status.name()),
                state -> {
                    busy = false;
                    applyState(state);
                    showMessage(state.str("message"), Theme.GREEN);
                },
                error -> {
                    busy = false;
                    updateEnabled();
                    handleError(error);
                });
    }

    private void perform(Async.Task<JsonObject> call, String progress) {
        if (busy) {
            return;
        }
        busy = true;
        updateEnabled();
        showMessage(progress, Theme.MUTED);
        Async.run(call, state -> {
            busy = false;
            applyState(state);
            showMessage(state.str("message"), Theme.GREEN);
            poller.refreshNow();
        }, error -> {
            busy = false;
            updateEnabled();
            handleError(error);
        });
    }

    private void showMessage(String text, Color color) {
        messageLabel.setText(text == null || text.isBlank() ? " " : text);
        messageLabel.setForeground(color);
        messageTimer.restart();
    }

    private void handlePollError(Exception error) {
        if (error instanceof ApiClient.ApiException apiError && apiError.isSessionProblem()) {
            endSession(apiError.getMessage());
            return;
        }
        connectionDot.setColor(Theme.AMBER);
        connectionLabel.setText("Reconnecting…");
        connectionLabel.setForeground(Theme.AMBER);
    }

    private void handleError(Exception error) {
        if (error instanceof ApiClient.ApiException apiError && apiError.isSessionProblem()) {
            endSession(apiError.getMessage());
            return;
        }
        String message = error.getMessage() == null ? "The action could not be completed." : error.getMessage();
        showMessage(message, Theme.RED);
        if (error instanceof ApiClient.ApiException apiError && apiError.isConnectionProblem()) {
            connectionDot.setColor(Theme.RED);
            connectionLabel.setText("Offline");
            connectionLabel.setForeground(Theme.RED);
        }
    }

    private void endSession(String message) {
        if (sessionEnded) {
            return;
        }
        sessionEnded = true;
        poller.stop();
        JOptionPane.showMessageDialog(this,
                message + "\n\nPlease sign in again.",
                "Session ended", JOptionPane.WARNING_MESSAGE);
        dispose();
    }

    private void signOut() {
        poller.stop();
        sessionEnded = true;
        Async.run(() -> api.post("teller/logout", "t", session.token()), ignored -> {
        }, ignored -> {
        });
        dispose();
    }

    private void signOutQuietly() {
        if (sessionEnded) {
            return;
        }
        sessionEnded = true;
        Async.run(() -> api.post("teller/logout", "t", session.token()), ignored -> {
        }, ignored -> {
        });
    }
}
