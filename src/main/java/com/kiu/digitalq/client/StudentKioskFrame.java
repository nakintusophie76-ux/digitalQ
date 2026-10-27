package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json.JsonObject;
import com.kiu.digitalq.ui.Brand;
import com.kiu.digitalq.ui.Card;
import com.kiu.digitalq.ui.DigitFilter;
import com.kiu.digitalq.ui.Dot;
import com.kiu.digitalq.ui.FlatButton;
import com.kiu.digitalq.ui.PillLabel;
import com.kiu.digitalq.ui.Theme;
import com.kiu.digitalq.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The student-facing kiosk. A student enters a five-digit registration number
 * and immediately sees their ticket, live queue position and the counter they
 * should walk to once they are called.
 */
public final class StudentKioskFrame extends JFrame {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH);

    private final ApiClient api;
    private final Poller poller;

    private final CardLayout contentLayout = new CardLayout();
    private final JPanel contentPanel = Ui.panel(contentLayout);

    private final JTextField regField = Ui.field(34);
    private final JLabel lengthHint = Ui.muted("0 of 5 digits", 11);
    private final JLabel entryMessage = Ui.muted(" ", 12);
    private final FlatButton submitButton =
            new FlatButton("Get my queue number  →", FlatButton.Kind.GOLD);

    private final JLabel clockLabel = new JLabel("--:--:--");
    private final JLabel dateLabel = new JLabel(" ");
    private final JLabel resetFooter = Ui.muted(" ", 10.5f);
    private final Dot connectionDot = new Dot(Theme.GREEN, 8);
    private final JLabel connectionLabel = Ui.muted("Connected", 10.5f);

    private final PillLabel ticketState = new PillLabel("Waiting");
    private final JLabel tokenLabel = new JLabel("—");
    private final JLabel ticketMessage = new JLabel(" ");
    private final JLabel assignmentTitle = Ui.overline("Your assignment");
    private final JLabel assignmentValue = new JLabel("Waiting for your call");
    private final JLabel assignmentHint = Ui.muted(" ", 12);
    private final JPanel assignmentCard = Ui.panel(new BorderLayout(0, 4));
    private final JLabel positionValue = statValue();
    private final JLabel aheadValue = statValue();
    private final JLabel etaValue = statValue();
    private final JLabel issuedValue = statValue();
    private final JLabel regValue = statValue();
    private final JLabel dayNote = Ui.muted(" ", 10.5f);
    private final FlatButton newStudentButton =
            new FlatButton("New student", FlatButton.Kind.OUTLINE);

    private String activeReg = "";
    private String activeToken = "";
    private String lastState = "";
    private boolean busy;
    private boolean firstPoll;

    public StudentKioskFrame(ApiClient api) {
        super("KIU digitalQ — Student kiosk");
        this.api = api;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(980, 690));
        Ui.sizeWindow(this, 1160, 712);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        poller = new Poller(2000, this::statusRequest, this::applyTicket, this::handlePollError);

        Timer clock = new Timer(1000, event -> updateClock());
        clock.start();
        updateClock();
        updateLengthHint();
        regField.requestFocusInWindow();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                poller.stop();
            }
        });
    }

    // ------------------------------------------------------------------- UI

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(Theme.NAVY);
        header.setBorder(new EmptyBorder(16, 30, 16, 28));

        header.add(Brand.lockup(true, "Finance Department · student kiosk", 21), BorderLayout.WEST);

        JPanel clock = Ui.panel(new BorderLayout(0, 1));
        clockLabel.setFont(Theme.font(Font.BOLD, 26));
        clockLabel.setForeground(Color.WHITE);
        clockLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        dateLabel.setFont(Theme.font(11));
        dateLabel.setForeground(Theme.WHITE_70);
        dateLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        clock.add(clockLabel, BorderLayout.CENTER);
        clock.add(dateLabel, BorderLayout.SOUTH);
        header.add(clock, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = Ui.panel(new BorderLayout());
        body.setBorder(new EmptyBorder(20, 26, 14, 26));

        contentPanel.setOpaque(false);
        contentPanel.add(buildEntryCard(), "entry");
        contentPanel.add(buildTicketCard(), "ticket");
        contentLayout.show(contentPanel, "entry");

        body.add(contentPanel, BorderLayout.CENTER);
        return body;
    }

    private Card buildEntryCard() {
        Card card = new Card(new BorderLayout(0, 20));
        card.setPadding(24, 38, 22, 38);

        JPanel heading = Ui.panel(new BorderLayout(0, 5));
        JLabel welcome = Ui.title("Take your place in the queue", 30);
        JLabel subtitle = Ui.muted(
                "Enter the five-digit registration number on your student ID and we will give you a queue number.",
                12.5f);
        heading.add(welcome, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.CENTER);

        JPanel headingWrap = Ui.panel(new BorderLayout(0, 4));
        headingWrap.add(Brand.overline("Welcome to the Finance Department", Theme.AMBER), BorderLayout.NORTH);
        headingWrap.add(heading, BorderLayout.CENTER);
        card.add(headingWrap, BorderLayout.NORTH);

        JPanel entry = Ui.panel(new BorderLayout(38, 0));
        entry.add(buildRegistrationField(), BorderLayout.CENTER);
        entry.add(buildKeypad(), BorderLayout.EAST);
        card.add(entry, BorderLayout.CENTER);

        JPanel south = Ui.panel(null);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));

        submitButton.setButtonFont(Theme.font(Font.BOLD, 16));
        submitButton.setPadding(16, 20);
        submitButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        submitButton.addActionListener(event -> submitRegistration());
        south.add(submitButton);
        south.add(Box.createVerticalStrut(10));

        entryMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(entryMessage);
        south.add(Box.createVerticalStrut(6));

        JLabel note = Ui.muted(
                "Keep your queue number — it will be called on the waiting-room display.", 10.5f);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(note);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildRegistrationField() {
        JPanel panel = Ui.panel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel label = Ui.overline("Student registration number");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(10));

        regField.setHorizontalAlignment(SwingConstants.CENTER);
        regField.setFont(Theme.mono(40));
        regField.setForeground(Theme.NAVY);
        regField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        regField.setPreferredSize(new Dimension(460, 86));
        regField.setAlignmentX(Component.LEFT_ALIGNMENT);
        ((AbstractDocument) regField.getDocument()).setDocumentFilter(new DigitFilter(5));
        regField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateLengthHint();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateLengthHint();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateLengthHint();
            }
        });
        regField.addActionListener(event -> submitRegistration());
        regField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) {
                char typed = event.getKeyChar();
                if (!Character.isDigit(typed) && typed != KeyEvent.VK_BACK_SPACE
                        && typed != KeyEvent.VK_DELETE) {
                    return;
                }
                Ui.clear(entryMessage);
            }
        });
        panel.add(regField);
        panel.add(Box.createVerticalStrut(9));

        JPanel hints = Ui.panel(new BorderLayout());
        hints.setAlignmentX(Component.LEFT_ALIGNMENT);
        hints.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        hints.add(lengthHint, BorderLayout.WEST);
        hints.add(Ui.muted("e.g. 20541", 11), BorderLayout.EAST);
        panel.add(hints);
        panel.add(Box.createVerticalStrut(20));

        Card help = new Card(new BorderLayout(12, 0));
        help.setCardColor(Theme.NAVY_SOFT);
        help.setBorderColor(Theme.NAVY_SOFT);
        help.setShadow(false);
        help.setPadding(13, 15, 13, 15);
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        JLabel helpIcon = Ui.iconTile("i", Theme.NAVY, Color.WHITE, 34);
        help.add(helpIcon, BorderLayout.WEST);
        JLabel helpText = Ui.muted(
                "Your registration number is on your student ID card. It has exactly five digits.",
                11.5f);
        helpText.setForeground(Theme.NAVY);
        help.add(helpText, BorderLayout.CENTER);
        panel.add(help);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildKeypad() {
        JPanel wrap = Ui.panel(new BorderLayout(0, 10));
        wrap.setPreferredSize(new Dimension(300, 0));
        wrap.add(Ui.overline("Keypad"), BorderLayout.NORTH);

        JPanel keys = Ui.panel(new GridLayout(4, 3, 9, 9));
        String[][] layout = {
                {"1", "2", "3"},
                {"4", "5", "6"},
                {"7", "8", "9"},
                {"Clear", "0", "⌫"}
        };
        for (String[] row : layout) {
            for (String key : row) {
                FlatButton button = new FlatButton(key, "Clear".equals(key) || "⌫".equals(key)
                        ? FlatButton.Kind.GHOST : FlatButton.Kind.OUTLINE);
                button.setButtonFont(Theme.font(Font.BOLD,
                        "Clear".equals(key) ? 12.5f : 20));
                button.setArc(14);
                button.addActionListener(event -> keypad(key));
                keys.add(button);
            }
        }
        wrap.add(keys, BorderLayout.CENTER);

        JLabel hint = Ui.muted("Tap the numbers or type on the keyboard.", 10.5f);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        wrap.add(hint, BorderLayout.SOUTH);
        return wrap;
    }

    private Card buildTicketCard() {
        Card card = new Card(new BorderLayout(0, 14));
        card.setPadding(22, 38, 20, 38);

        JPanel top = Ui.panel(new BorderLayout(0, 2));
        top.add(Brand.overline("Your queue number", Theme.AMBER), BorderLayout.NORTH);
        JPanel stateRow = Ui.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        stateRow.add(ticketState);
        top.add(stateRow, BorderLayout.CENTER);
        card.add(top, BorderLayout.NORTH);

        JPanel middle = Ui.panel(null);
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));

        tokenLabel.setFont(Theme.mono(92));
        tokenLabel.setForeground(Theme.NAVY);
        tokenLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        middle.add(tokenLabel);
        middle.add(Box.createVerticalStrut(2));

        JLabel tokenCaption = Ui.muted("Please watch for this number on the display.", 12);
        tokenCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
        middle.add(tokenCaption);
        middle.add(Box.createVerticalStrut(12));

        assignmentCard.setOpaque(false);
        assignmentCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.NAVY),
                new EmptyBorder(8, 14, 8, 12)));
        assignmentCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        assignmentCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        assignmentTitle.setForeground(Theme.MUTED);
        assignmentValue.setFont(Theme.font(Font.BOLD, 24));
        assignmentValue.setForeground(Theme.NAVY);
        assignmentCard.add(assignmentTitle, BorderLayout.NORTH);
        assignmentCard.add(assignmentValue, BorderLayout.CENTER);
        JPanel hintRow = Ui.panel(new BorderLayout());
        hintRow.add(assignmentHint, BorderLayout.WEST);
        assignmentCard.add(hintRow, BorderLayout.SOUTH);
        middle.add(assignmentCard);
        middle.add(Box.createVerticalStrut(8));

        ticketMessage.setFont(Theme.font(12.5f));
        ticketMessage.setForeground(Theme.MUTED);
        ticketMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        middle.add(ticketMessage);
        middle.add(Box.createVerticalStrut(14));

        JPanel stats = Ui.panel(new GridLayout(1, 4, 12, 0));
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        stats.add(statBlock("Position", positionValue));
        stats.add(statBlock("Ahead of you", aheadValue));
        stats.add(statBlock("Estimated wait", etaValue));
        stats.add(statBlock("Issued at", issuedValue));
        middle.add(stats);
        middle.add(Box.createVerticalStrut(8));

        JPanel meta = Ui.panel(new BorderLayout());
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        meta.add(regValue, BorderLayout.WEST);
        meta.add(dayNote, BorderLayout.EAST);
        middle.add(meta);

        card.add(middle, BorderLayout.CENTER);

        JPanel south = Ui.panel(new BorderLayout(12, 0));
        newStudentButton.setPadding(12, 18);
        newStudentButton.addActionListener(event -> resetForNewStudent());
        FlatButton refresh = new FlatButton("Refresh status", FlatButton.Kind.GHOST);
        refresh.setPadding(12, 14);
        refresh.addActionListener(event -> {
            if (activeToken != null && !activeToken.isEmpty()) {
                poller.refreshNow();
            }
        });
        JPanel buttons = Ui.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(newStudentButton);
        buttons.add(refresh);
        south.add(buttons, BorderLayout.WEST);
        south.add(Ui.muted("Queue resets at midnight.", 10.5f), BorderLayout.EAST);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private JPanel statBlock(String caption, JLabel value) {
        JPanel block = Ui.panel(new BorderLayout(0, 2));
        block.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE_SOFT),
                new EmptyBorder(7, 0, 0, 0)));
        block.add(Ui.overline(caption), BorderLayout.NORTH);
        block.add(value, BorderLayout.CENTER);
        return block;
    }

    private static JLabel statValue() {
        JLabel label = new JLabel("—");
        label.setFont(Theme.font(Font.BOLD, 17));
        label.setForeground(Theme.TEXT);
        return label;
    }

    private JPanel buildFooter() {
        JPanel footer = Ui.panel(new BorderLayout(10, 0));
        footer.setBorder(new EmptyBorder(0, 30, 11, 30));

        JPanel live = Ui.panel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        live.add(connectionDot);
        live.add(connectionLabel);
        footer.add(live, BorderLayout.WEST);

        resetFooter.setHorizontalAlignment(SwingConstants.RIGHT);
        footer.add(resetFooter, BorderLayout.EAST);
        return footer;
    }

    private void updateClock() {
        LocalTime now = LocalTime.now();
        clockLabel.setText(Ui.clock(now));
        dateLabel.setText(LocalDate.now().format(DATE));
        long seconds = Math.max(0, 86400 - now.toSecondOfDay());
        resetFooter.setText("KIU digitalQ  ·  Tokens 000–999  ·  resets in "
                + Ui.countdown(seconds) + "  ·  Finance Department");
    }

    private void updateLengthHint() {
        int length = regField.getText().length();
        lengthHint.setText(length + " of 5 digits");
        lengthHint.setForeground(length == 5 ? Theme.GREEN : Theme.MUTED);
        submitButton.setEnabled(!busy && length == 5);
    }

    // --------------------------------------------------------------- actions

    private void keypad(String key) {
        if (busy) {
            return;
        }
        String current = regField.getText();
        switch (key) {
            case "Clear" -> regField.setText("");
            case "⌫" -> {
                if (!current.isEmpty()) {
                    regField.setText(current.substring(0, current.length() - 1));
                }
            }
            default -> {
                if (current.length() < 5) {
                    regField.setText(current + key);
                }
            }
        }
        regField.requestFocusInWindow();
        Ui.clear(entryMessage);
        updateLengthHint();
    }

    private void submitRegistration() {
        if (busy) {
            return;
        }
        String reg = regField.getText().trim();
        if (reg.length() != 5) {
            Ui.error(entryMessage, "Please enter all five digits of your registration number.");
            regField.requestFocusInWindow();
            return;
        }
        busy = true;
        submitButton.setEnabled(false);
        submitButton.setText("Checking your number…");
        Ui.muted(entryMessage, "Looking up your ticket…");
        Async.run(() -> api.post("student/register", "reg", reg), response -> {
            busy = false;
            submitButton.setText("Get my queue number  →");
            updateLengthHint();
            activeReg = response.str("reg", reg);
            activeToken = response.str("token");
            firstPoll = true;
            lastState = response.str("state");
            showTicket(response, response.bool("alreadyRegistered"));
            poller.startDelayed(2000);
        }, error -> {
            busy = false;
            submitButton.setText("Get my queue number  →");
            updateLengthHint();
            Ui.error(entryMessage, error.getMessage());
            connectionDot.setColor(Theme.RED);
            connectionLabel.setText("Offline");
            connectionLabel.setForeground(Theme.RED);
        });
    }

    private void showTicket(JsonObject response, boolean alreadyRegistered) {
        String state = response.str("state");
        String label = response.str("stateLabel", "Waiting");
        String token = response.str("token", "—");
        boolean assigned = response.bool("assigned");

        tokenLabel.setText(token);
        ticketState.setPill(label, ticketStateText(state), ticketStateSoft(state));
        ticketState.setText(label);
        ticketMessage.setText(alreadyRegistered
                ? "You already had a ticket for today. This is the same number — please keep it."
                : response.str("message", " "));
        ticketMessage.setForeground(alreadyRegistered ? Theme.AMBER : Theme.MUTED);

        regValue.setText("Reg no " + response.str("reg"));
        regValue.setFont(Theme.font(11));
        regValue.setForeground(Theme.MUTED);
        dayNote.setText(response.str("dayLabel"));
        dayNote.setFont(Theme.font(11));
        dayNote.setForeground(Theme.MUTED);

        positionValue.setText(response.integer("position") > 0
                ? String.valueOf(response.integer("position")) : "—");
        aheadValue.setText(response.integer("position") > 0
                ? String.valueOf(response.integer("ahead")) : "—");
        etaValue.setText(response.integer("position") > 0
                ? response.integer("etaMinutes") + " min" : "—");
        issuedValue.setText(response.str("issuedAt", "—"));

        if (assigned) {
            JsonObject teller = response.obj("teller");
            String counter = "Counter " + teller.integer("counter");
            assignmentTitle.setText("Go to");
            assignmentValue.setText(counter + "  ·  " + teller.str("name"));
            assignmentValue.setForeground(Theme.GREEN);
            assignmentHint.setText("Your number " + token + " is being called now — please proceed.");
            assignmentHint.setForeground(Theme.GREEN);
            assignmentCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.GREEN),
                    new EmptyBorder(8, 14, 8, 12)));
            if (firstPoll && !"SERVING".equals(lastState)) {
                ticketCardFlash();
            }
        } else if ("WAITING".equals(state)) {
            assignmentTitle.setText("Your assignment");
            assignmentValue.setText("Waiting for your call");
            assignmentValue.setForeground(Theme.NAVY);
            assignmentHint.setText(response.integer("position") > 0
                    ? "You are number " + response.integer("position")
                        + " in line. Watch the display."
                    : "Please wait — the display will show your number.");
            assignmentHint.setForeground(Theme.MUTED);
            assignmentCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, Theme.NAVY),
                    new EmptyBorder(8, 14, 8, 12)));
        } else if ("DONE".equals(state)) {
            assignmentTitle.setText("Status");
            assignmentValue.setText("Service completed");
            assignmentValue.setForeground(Theme.GREEN);
            assignmentHint.setText("Thank you for visiting the Finance Department.");
            assignmentHint.setForeground(Theme.MUTED);
        } else if ("MISSED".equals(state)) {
            assignmentTitle.setText("Status");
            assignmentValue.setText("Number was missed");
            assignmentValue.setForeground(Theme.RED);
            assignmentHint.setText("Please speak to the finance desk for assistance.");
            assignmentHint.setForeground(Theme.RED);
        }

        boolean finished = "DONE".equals(state) || "MISSED".equals(state);
        newStudentButton.setText(finished ? "Next student  →" : "New student");
        newStudentButton.setEnabled(true);
        if (finished) {
            poller.stop();
        }
        lastState = state;
        firstPoll = false;
        contentLayout.show(contentPanel, "ticket");
    }

    private void ticketCardFlash() {
        for (Component component : contentPanel.getComponents()) {
            if (component instanceof Card card && component.isVisible()) {
                card.flash();
            }
        }
        Ui.beep();
    }

    private JsonObject statusRequest() throws Exception {
        return api.get("student/status", "reg", activeReg, "token", activeToken);
    }

    private void applyTicket(JsonObject response) {
        connectionDot.setColor(Theme.GREEN);
        connectionLabel.setText("Connected");
        connectionLabel.setForeground(Theme.MUTED);

        if (!response.bool("found")) {
            poller.stop();
            activeReg = "";
            activeToken = "";
            resetForNewStudent();
            Ui.error(entryMessage,
                    "That ticket is no longer active. A new day may have started — enter your number again.");
            return;
        }
        String state = response.str("state");
        boolean becameServing = "SERVING".equals(state) && !"SERVING".equals(lastState);
        showTicket(response, false);
        if (becameServing) {
            ticketCardFlash();
        }
    }

    private void resetForNewStudent() {
        poller.stop();
        activeReg = "";
        activeToken = "";
        lastState = "";
        busy = false;
        regField.setText("");
        regField.requestFocusInWindow();
        Ui.clear(entryMessage);
        contentLayout.show(contentPanel, "entry");
        updateLengthHint();
        tokenLabel.setText("—");
    }

    private void handlePollError(Exception error) {
        connectionDot.setColor(Theme.RED);
        connectionLabel.setText(error instanceof ApiClient.ApiException apiError
                && apiError.isConnectionProblem() ? "Offline" : "Reconnecting…");
        connectionLabel.setForeground(Theme.RED);
        if (error instanceof ApiClient.ApiException apiError && apiError.isConnectionProblem()) {
            ticketMessage.setText("Connection lost. Retrying…");
            ticketMessage.setForeground(Theme.RED);
        }
    }

    private static Color ticketStateText(String state) {
        return switch (state) {
            case "SERVING" -> Theme.GREEN;
            case "DONE" -> Theme.SLATE;
            case "MISSED" -> Theme.RED;
            default -> Theme.AMBER;
        };
    }

    private static Color ticketStateSoft(String state) {
        return switch (state) {
            case "SERVING" -> Theme.GREEN_SOFT;
            case "DONE" -> Theme.SLATE_SOFT;
            case "MISSED" -> Theme.RED_SOFT;
            default -> Theme.AMBER_SOFT;
        };
    }
}
