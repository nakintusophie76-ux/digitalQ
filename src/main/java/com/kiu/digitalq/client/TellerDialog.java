package com.kiu.digitalq.client;

import com.kiu.digitalq.json.Json.JsonObject;
import com.kiu.digitalq.ui.Brand;
import com.kiu.digitalq.ui.Card;
import com.kiu.digitalq.ui.DigitFilter;
import com.kiu.digitalq.ui.FlatButton;
import com.kiu.digitalq.ui.Theme;
import com.kiu.digitalq.ui.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;

/** Add or edit a teller / counter. */
public final class TellerDialog extends JDialog {

    private final ApiClient api;
    private final String adminToken;
    private final JsonObject existing;
    private final JTextField nameField = Ui.field(14);
    private final JTextField counterField = Ui.field(14);
    private final JPasswordField pinField = Ui.passwordField(14);
    private final JCheckBox enabledBox = new JCheckBox("Open this counter for students");
    private final JLabel message = Ui.muted(" ", 11.5f);
    private final FlatButton saveButton;
    private boolean saved;

    private TellerDialog(Window owner, ApiClient api, String adminToken,
                         JsonObject existing, int suggestedCounter) {
        super(owner, existing == null ? "KIU digitalQ — Add teller" : "KIU digitalQ — Edit teller",
                ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.adminToken = adminToken;
        this.existing = existing;
        this.saveButton = new FlatButton(existing == null ? "Create teller" : "Save changes",
                FlatButton.Kind.PRIMARY);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(480, 640);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(suggestedCounter), BorderLayout.CENTER);

        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(saveButton);
    }

    public static boolean show(Window owner, ApiClient api, String adminToken,
                               JsonObject existing, int suggestedCounter) {
        TellerDialog dialog = new TellerDialog(owner, api, adminToken, existing, suggestedCounter);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.NAVY);
        header.setBorder(new EmptyBorder(20, 24, 20, 24));
        header.add(Brand.lockup(true, "Finance Department · administrator", 20), BorderLayout.NORTH);

        JPanel text = Ui.panel(new BorderLayout(0, 3));
        text.setBorder(new EmptyBorder(15, 0, 0, 0));
        text.add(Ui.whiteTitle(existing == null ? "Add a teller" : "Edit " + existing.str("name"), 21),
                BorderLayout.NORTH);
        JLabel subtitle = new JLabel(existing == null
                ? "Create a counter and give the teller a PIN."
                : "Update the counter, name or PIN.");
        subtitle.setFont(Theme.font(11.5f));
        subtitle.setForeground(Theme.WHITE_70);
        text.add(subtitle, BorderLayout.CENTER);
        header.add(text, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildBody(int suggestedCounter) {
        Card card = new Card(new BorderLayout(0, 16));
        card.setPadding(22, 22, 20, 22);

        JPanel form = Ui.panel(null);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        nameField.setText(existing == null ? "" : existing.str("name"));
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(label("Teller name"));
        form.add(Box.createVerticalStrut(6));
        form.add(nameField);
        form.add(Box.createVerticalStrut(5));
        JLabel nameHint = Ui.muted("For example Teller One or Aisha N.", 10.5f);
        nameHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(nameHint);
        form.add(Box.createVerticalStrut(16));

        counterField.setText(String.valueOf(existing == null
                ? suggestedCounter : existing.integer("counter")));
        ((AbstractDocument) counterField.getDocument()).setDocumentFilter(new DigitFilter(2));
        counterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        counterField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(label("Counter number"));
        form.add(Box.createVerticalStrut(6));
        form.add(counterField);
        form.add(Box.createVerticalStrut(5));
        JLabel counterHint = Ui.muted("A number from 1 to 99, shown to students on the display.", 10.5f);
        counterHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(counterHint);
        form.add(Box.createVerticalStrut(16));

        ((AbstractDocument) pinField.getDocument()).setDocumentFilter(new DigitFilter(6));
        pinField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        pinField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(label("PIN"));
        form.add(Box.createVerticalStrut(6));
        form.add(pinField);
        form.add(Box.createVerticalStrut(5));
        JLabel pinHint = Ui.muted(existing == null
                ? "4 to 6 digits. The teller uses this to sign in."
                : "Leave blank to keep the current PIN. Enter 4–6 digits to change it.", 10.5f);
        pinHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(pinHint);
        form.add(Box.createVerticalStrut(16));

        enabledBox.setSelected(existing == null || existing.bool("enabled"));
        enabledBox.setOpaque(false);
        enabledBox.setFont(Theme.font(12));
        enabledBox.setForeground(Theme.TEXT);
        enabledBox.setFocusPainted(false);
        enabledBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        enabledBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(enabledBox);
        form.add(Box.createVerticalStrut(12));

        JPanel messageRow = Ui.panel(new BorderLayout());
        messageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        messageRow.add(message, BorderLayout.CENTER);
        form.add(messageRow);

        saveButton.setButtonFont(Theme.font(Font.BOLD, 14));
        saveButton.setPadding(12, 18);
        saveButton.addActionListener(event -> submit());
        FlatButton cancel = new FlatButton("Cancel", FlatButton.Kind.GHOST);
        cancel.addActionListener(event -> dispose());

        JPanel buttons = Ui.panel(new BorderLayout(8, 0));
        buttons.add(cancel, BorderLayout.WEST);
        buttons.add(saveButton, BorderLayout.CENTER);

        card.add(form, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        JPanel body = Ui.panel(new BorderLayout());
        body.setBorder(new EmptyBorder(20, 22, 18, 22));
        body.add(card, BorderLayout.NORTH);
        if (existing != null) {
            JLabel note = Ui.muted("Tellers cannot receive students while the counter is closed.", 10.5f);
            note.setBorder(new EmptyBorder(10, 26, 0, 26));
            body.add(note, BorderLayout.SOUTH);
        }
        return body;
    }

    private JLabel label(String text) {
        JLabel label = Ui.overline(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void submit() {
        String name = nameField.getText().trim();
        if (name.length() < 2) {
            Ui.error(message, "Enter the teller's name (at least 2 characters).");
            nameField.requestFocusInWindow();
            return;
        }
        String counter = counterField.getText().trim();
        if (counter.isEmpty()) {
            Ui.error(message, "Enter a counter number between 1 and 99.");
            counterField.requestFocusInWindow();
            return;
        }
        String pin = new String(pinField.getPassword()).trim();
        if (existing == null && pin.isEmpty()) {
            Ui.error(message, "Create a 4–6 digit PIN for this teller.");
            pinField.requestFocusInWindow();
            return;
        }
        if (!pin.isEmpty() && !pin.matches("\\d{4,6}")) {
            Ui.error(message, "The PIN must be 4 to 6 digits.");
            pinField.requestFocusInWindow();
            return;
        }

        saveButton.setEnabled(false);
        saveButton.setText(existing == null ? "Creating…" : "Saving…");
        Ui.muted(message, "Saving to the queue server…");

        boolean enabled = enabledBox.isSelected();
        Async.run(() -> {
            if (existing == null) {
                return api.post("admin/teller/create", "t", adminToken,
                        "name", name, "counter", counter, "pin", pin,
                        "enabled", String.valueOf(enabled));
            }
            return api.post("admin/teller/update", "t", adminToken,
                    "id", existing.str("id"), "name", name, "counter", counter,
                    "pin", pin, "enabled", String.valueOf(enabled));
        }, response -> {
            saved = true;
            dispose();
        }, error -> {
            saveButton.setEnabled(true);
            saveButton.setText(existing == null ? "Create teller" : "Save changes");
            Ui.error(message, error.getMessage());
        });
    }
}
