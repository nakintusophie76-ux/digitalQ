package com.kiu.digitalq.ui;

import com.kiu.digitalq.model.TellerStatus;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Clickable availability row used on the teller terminal. */
public final class StatusRow extends JPanel {

    private final TellerStatus status;
    private final JLabel title;
    private final JLabel hint;
    private final Runnable action;
    private boolean active;
    private boolean hover;
    private boolean usable = true;

    public StatusRow(TellerStatus status, Runnable action) {
        this.status = status;
        this.action = action;
        setOpaque(false);
        setLayout(new BorderLayout(10, 0));
        setBorder(new EmptyBorder(8, 11, 8, 11));

        JPanel dot = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.statusColor(status.name()));
                g2.fillOval(0, 0, 9, 9);
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(9, 9));

        title = new JLabel(status.label());
        title.setFont(Theme.font(Font.BOLD, 12.5f));
        title.setForeground(Theme.TEXT);
        hint = new JLabel(status.hint());
        hint.setFont(Theme.font(10.5f));
        hint.setForeground(Theme.MUTED);

        JPanel text = Ui.panel(new BorderLayout(0, 2));
        text.add(title, BorderLayout.NORTH);
        text.add(hint, BorderLayout.CENTER);

        JPanel left = Ui.panel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        left.add(dot);

        add(left, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
        setPreferredSize(new Dimension(280, 50));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        MouseAdapter handler = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (usable) {
                    hover = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (usable && StatusRow.this.action != null) {
                    StatusRow.this.action.run();
                }
            }
        };
        addMouseListener(handler);
        title.addMouseListener(handler);
        hint.addMouseListener(handler);
        dot.addMouseListener(handler);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public void setActive(boolean active) {
        this.active = active;
        title.setForeground(active ? Theme.statusText(status.name()) : Theme.TEXT);
        repaint();
    }

    public void setUsable(boolean usable) {
        this.usable = usable;
        setCursor(usable
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
        title.setForeground(usable ? (active ? Theme.statusText(status.name()) : Theme.TEXT) : Theme.MUTED);
        hint.setForeground(usable ? Theme.MUTED : Theme.FAINT);
        if (!usable) {
            hover = false;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill;
        if (active) {
            fill = Theme.statusSoft(status.name());
        } else if (hover && usable) {
            fill = Theme.BG;
        } else {
            fill = new Color(0, 0, 0, 0);
        }
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        if (active) {
            g2.setColor(Theme.alpha(Theme.statusColor(status.name()), 90));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        }
        g2.dispose();
        super.paintComponent(graphics);
    }
}
