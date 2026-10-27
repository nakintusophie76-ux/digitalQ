package com.kiu.digitalq.client;

import com.kiu.digitalq.ui.Brand;
import com.kiu.digitalq.ui.Card;
import com.kiu.digitalq.ui.FlatButton;
import com.kiu.digitalq.ui.Theme;
import com.kiu.digitalq.ui.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;

/** Modal sign-in for the administrator console. */
public final class AdminLoginDialog extends JDialog {

    private final ApiClient api;
    private final JPasswordField password = Ui.passwordField(15);
    private final JLabel message = Ui.muted(" ", 11.5f);
    private final FlatButton signIn = new FlatButton("Open admin console", FlatButton.Kind.PRIMARY);
    private String token;

    private AdminLoginDialog(Window owner, ApiClient api) {
        super(owner, "KIU digitalQ — Administrator sign-in", ModalityType.APPLICATION_MODAL);
        this.api = api;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(450, 440);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);

        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        password.requestFocusInWindow();
    }

    public static String show(Window owner, ApiClient api) {
        AdminLoginDialog dialog = new AdminLoginDialog(owner, api);
        dialog.setVisible(true);
        return dialog.token;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.NAVY);
        header.setBorder(new EmptyBorder(20, 24, 20, 24));
        header.add(Brand.lockup(true, "Finance Department", 20), BorderLayout.NORTH);

        JPanel text = Ui.panel(new BorderLayout(0, 3));
        text.setBorder(new EmptyBorder(16, 0, 0, 0));
        text.add(Ui.whiteTitle("Administrator sign-in", 21), BorderLayout.NORTH);
        JLabel subtitle = new JLabel("Manage tellers, counters and the queue.");
        subtitle.setFont(Theme.font(12));
        subtitle.setForeground(Theme.WHITE_70);
        text.add(subtitle, BorderLayout.CENTER);
        header.add(text, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildBody() {
        Card card = new Card(new BorderLayout(0, 14));
        card.setPadding(22, 22, 20, 22);

        JPanel form = Ui.panel(null);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JLabel label = Ui.overline("Administrator password");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        password.setAlignmentX(Component.LEFT_ALIGNMENT);
        password.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        password.addActionListener(event -> submit());

        JPanel messageRow = Ui.panel(new BorderLayout());
        messageRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageRow.add(message, BorderLayout.CENTER);
        messageRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        form.add(label);
        form.add(Box.createVerticalStrut(7));
        form.add(password);
        form.add(Box.createVerticalStrut(12));
        form.add(messageRow);

        signIn.setButtonFont(Theme.font(Font.BOLD, 14));
        signIn.setPadding(12, 18);
        signIn.addActionListener(event -> submit());
        FlatButton cancel = new FlatButton("Cancel", FlatButton.Kind.GHOST);
        cancel.addActionListener(event -> dispose());

        JPanel buttons = Ui.panel(new BorderLayout(8, 0));
        buttons.add(cancel, BorderLayout.WEST);
        buttons.add(signIn, BorderLayout.CENTER);

        card.add(form, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.SOUTH);

        JPanel body = Ui.panel(new BorderLayout());
        body.setBorder(new EmptyBorder(20, 22, 18, 22));
        body.add(card, BorderLayout.NORTH);
        return body;
    }

    private void submit() {
        String value = new String(password.getPassword());
        if (value.isEmpty()) {
            Ui.error(message, "Enter the administrator password.");
            return;
        }
        signIn.setEnabled(false);
        signIn.setText("Signing in…");
        Ui.muted(message, "Checking password…");
        Async.run(() -> api.post("admin/login", "password", value), response -> {
            token = response.str("token");
            dispose();
        }, error -> {
            signIn.setEnabled(true);
            signIn.setText("Open admin console");
            Ui.error(message, error.getMessage());
            password.selectAll();
            password.requestFocusInWindow();
        });
    }
}
