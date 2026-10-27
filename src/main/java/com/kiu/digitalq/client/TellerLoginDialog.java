package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json.JsonArray;
import com.kiu.digitalq.json.Json.JsonObject;
import com.kiu.digitalq.ui.Brand;
import com.kiu.digitalq.ui.Card;
import com.kiu.digitalq.ui.DigitFilter;
import com.kiu.digitalq.ui.FlatButton;
import com.kiu.digitalq.ui.Theme;
import com.kiu.digitalq.ui.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** Modal sign-in for a teller terminal. */
public final class TellerLoginDialog extends JDialog {

    private final ApiClient api;
    private final JComboBox<TellerItem> counters = new JComboBox<>();
    private final JPasswordField pin = Ui.passwordField(15);
    private final JLabel message = Ui.muted(" ", 11.5f);
    private final FlatButton signIn = new FlatButton("Sign in to my counter", FlatButton.Kind.PRIMARY);
    private final FlatButton reload = new FlatButton("Reload", FlatButton.Kind.GHOST);
    private TellerSession session;

    private TellerLoginDialog(Window owner, ApiClient api) {
        super(owner, "KIU digitalQ — Teller sign-in", ModalityType.APPLICATION_MODAL);
        this.api = api;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(470, 560);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        loadTellers();
    }

    public static TellerSession show(Window owner, ApiClient api) {
        TellerLoginDialog dialog = new TellerLoginDialog(owner, api);
        dialog.setVisible(true);
        return dialog.session;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.NAVY);
        header.setBorder(new EmptyBorder(20, 24, 20, 24));
        header.add(Brand.lockup(true, "Finance Department", 20), BorderLayout.NORTH);

        JPanel text = Ui.panel(new BorderLayout(0, 3));
        text.setBorder(new EmptyBorder(16, 0, 0, 0));
        JLabel title = Ui.whiteTitle("Teller sign-in", 21);
        JLabel subtitle = new JLabel("Select your counter and enter your PIN.");
        subtitle.setFont(Theme.font(12));
        subtitle.setForeground(Theme.WHITE_70);
        text.add(title, BorderLayout.NORTH);
        text.add(subtitle, BorderLayout.CENTER);
        header.add(text, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildBody() {
        Card card = new Card(new BorderLayout(0, 14));
        card.setPadding(22, 22, 20, 22);

        JPanel form = Ui.panel(null);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JLabel counterLabel = Ui.overline("Your counter");
        counterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        counters.setAlignmentX(Component.LEFT_ALIGNMENT);
        counters.setFont(Theme.font(14));
        counters.setBackground(Color.WHITE);
        counters.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE, 1, true),
                new EmptyBorder(3, 8, 3, 8)));
        counters.setPreferredSize(new Dimension(200, 42));
        counters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        counters.setRenderer(new CounterRenderer());
        counters.addActionListener(event -> pin.requestFocusInWindow());

        JLabel pinLabel = Ui.overline("Your PIN");
        pinLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pin.setAlignmentX(Component.LEFT_ALIGNMENT);
        pin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        ((AbstractDocument) pin.getDocument()).setDocumentFilter(new DigitFilter(6));
        pin.addActionListener(event -> submit());

        JPanel pinRow = Ui.panel(new BorderLayout(8, 0));
        pinRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pinRow.add(pin, BorderLayout.CENTER);
        reload.setPadding(9, 12);
        reload.setButtonFont(Theme.font(Font.BOLD, 11.5f));
        reload.addActionListener(event -> loadTellers());
        pinRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JPanel messageRow = Ui.panel(new BorderLayout());
        messageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageRow.add(message, BorderLayout.CENTER);
        messageRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        form.add(counterLabel);
        form.add(Box.createVerticalStrut(7));
        form.add(counters);
        form.add(Box.createVerticalStrut(16));
        form.add(pinLabel);
        form.add(Box.createVerticalStrut(7));
        form.add(pinRow);
        form.add(Box.createVerticalStrut(12));
        form.add(messageRow);

        signIn.setButtonFont(Theme.font(Font.BOLD, 14));
        signIn.setPadding(12, 18);
        signIn.addActionListener(event -> submit());

        JPanel buttons = Ui.panel(new BorderLayout(0, 0));
        JPanel hintRow = Ui.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        hintRow.add(reload);
        buttons.add(hintRow, BorderLayout.WEST);
        buttons.add(signIn, BorderLayout.CENTER);

        card.add(form, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        JPanel body = Ui.panel(new BorderLayout());
        body.setBorder(new EmptyBorder(20, 22, 14, 22));
        body.add(card, BorderLayout.NORTH);

        JLabel note = Ui.muted("PINs are created by the Finance Department administrator.", 10.5f);
        note.setBorder(new EmptyBorder(0, 26, 16, 26));
        body.add(note, BorderLayout.SOUTH);
        return body;
    }

    private void loadTellers() {
        counters.removeAllItems();
        signIn.setEnabled(false);
        Ui.muted(message, "Loading counters…");
        Async.run(() -> api.get("teller/list"), response -> {
            List<TellerItem> items = new ArrayList<>();
            JsonArray tellers = response.arr("tellers");
            for (Object value : tellers) {
                if (value instanceof JsonObject teller && teller.bool("enabled")) {
                    items.add(new TellerItem(teller.str("id"), teller.str("name"), teller.integer("counter")));
                }
            }
            for (TellerItem item : items) {
                counters.addItem(item);
            }
            if (items.isEmpty()) {
                Ui.error(message, "No tellers are available. Ask the administrator to create one.");
                counters.setEnabled(false);
                pin.setEnabled(false);
                signIn.setEnabled(false);
            } else {
                counters.setEnabled(true);
                pin.setEnabled(true);
                signIn.setEnabled(true);
                Ui.muted(message, "Enter your 4-digit PIN to start serving students.");
                pin.requestFocusInWindow();
            }
        }, error -> {
            Ui.error(message, error.getMessage());
            counters.setEnabled(false);
            pin.setEnabled(false);
            signIn.setEnabled(false);
        });
    }

    private void submit() {
        TellerItem item = (TellerItem) counters.getSelectedItem();
        if (item == null) {
            Ui.error(message, "Select your counter first.");
            return;
        }
        String pinValue = new String(pin.getPassword()).trim();
        if (pinValue.isEmpty()) {
            Ui.error(message, "Enter your PIN.");
            pin.requestFocusInWindow();
            return;
        }
        signIn.setEnabled(false);
        signIn.setText("Signing in…");
        Ui.muted(message, "Checking your PIN…");
        Async.run(() -> api.post("teller/login", "teller", item.id(), "pin", pinValue), response -> {
            JsonObject teller = response.obj("teller");
            session = new TellerSession(response.str("token"), teller.str("id"),
                    teller.str("name"), teller.integer("counter"));
            dispose();
        }, error -> {
            signIn.setEnabled(true);
            signIn.setText("Sign in to my counter");
            Ui.error(message, error.getMessage());
            pin.selectAll();
            pin.requestFocusInWindow();
        });
    }

    private record TellerItem(String id, String name, int counter) {
        @Override
        public String toString() {
            return name + "   ·   Counter " + counter;
        }
    }

    private static final class CounterRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            label.setFont(Theme.font(13.5f));
            label.setBorder(new EmptyBorder(8, 10, 8, 10));
            if (selected) {
                label.setBackground(Theme.NAVY_SOFT);
                label.setForeground(Theme.NAVY);
            } else {
                label.setBackground(Color.WHITE);
                label.setForeground(Theme.TEXT);
            }
            return label;
        }
    }
}
