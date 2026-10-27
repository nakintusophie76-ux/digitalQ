package com.kiu.digitalq.ui;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Rounded, flat button used throughout the application. */
public class FlatButton extends JButton {

    public enum Kind {
        PRIMARY, GOLD, SUCCESS, DANGER, OUTLINE, SOFT, GHOST, DARK
    }

    private final Kind kind;
    private final Color base;
    private final Color hover;
    private final Color pressed;
    private final Color foreground;
    private final Color line;
    private int arc = 12;
    private boolean bold = true;

    public FlatButton(String text) {
        this(text, Kind.PRIMARY);
    }

    public FlatButton(String text, Kind kind) {
        super(text);
        this.kind = kind;
        switch (kind) {
            case PRIMARY -> {
                base = Theme.NAVY;
                hover = Theme.NAVY_LIGHT;
                pressed = Theme.NAVY_DEEP;
                foreground = Color.WHITE;
                line = null;
            }
            case GOLD -> {
                base = Theme.GOLD;
                hover = Theme.GOLD_HOVER;
                pressed = Theme.GOLD_DARK;
                foreground = Theme.NAVY;
                line = null;
            }
            case SUCCESS -> {
                base = Theme.GREEN;
                hover = new Color(0x1C, 0xB4, 0x7A);
                pressed = new Color(0x10, 0x82, 0x56);
                foreground = Color.WHITE;
                line = null;
            }
            case DANGER -> {
                base = Theme.RED;
                hover = new Color(0xD9, 0x4A, 0x4A);
                pressed = new Color(0xA8, 0x32, 0x32);
                foreground = Color.WHITE;
                line = null;
            }
            case OUTLINE -> {
                base = Color.WHITE;
                hover = new Color(0xF3, 0xF7, 0xFC);
                pressed = new Color(0xE6, 0xEE, 0xF9);
                foreground = Theme.NAVY;
                line = Theme.LINE;
            }
            case SOFT -> {
                base = Theme.NAVY_SOFT;
                hover = new Color(0xDD, 0xE9, 0xF7);
                pressed = new Color(0xCB, 0xDC, 0xF0);
                foreground = Theme.NAVY;
                line = null;
            }
            case GHOST -> {
                base = new Color(0, 0, 0, 0);
                hover = new Color(0xEF, 0xF3, 0xF9);
                pressed = new Color(0xE3, 0xE9, 0xF2);
                foreground = Theme.MUTED;
                line = null;
            }
            case DARK -> {
                base = Theme.NAVY_DEEP;
                hover = Theme.NAVY;
                pressed = Color.BLACK;
                foreground = Color.WHITE;
                line = null;
            }
            default -> throw new IllegalStateException("Unhandled button kind " + kind);
        }
        setForeground(foreground);
        setFont(Theme.font(Font.BOLD, 14));
        setBorder(new EmptyBorder(11, 18, 11, 18));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public FlatButton setButtonFont(Font font) {
        setFont(font);
        return this;
    }

    public FlatButton setBold(boolean bold) {
        this.bold = bold;
        setFont(getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, getFont().getSize2D()));
        return this;
    }

    public FlatButton setArc(int arc) {
        this.arc = arc;
        return this;
    }

    public FlatButton setPadding(int vertical, int horizontal) {
        setBorder(new EmptyBorder(vertical, horizontal, vertical, horizontal));
        return this;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int radius = Math.min(arc, height);

        Color fill;
        if (!isEnabled()) {
            fill = kind == Kind.GHOST ? new Color(0, 0, 0, 0) : Theme.mix(base, Color.WHITE, 0.62);
        } else if (getModel().isPressed()) {
            fill = pressed;
        } else if (getModel().isRollover()) {
            fill = hover;
        } else {
            fill = base;
        }
        if (fill.getAlpha() > 0) {
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, width, height, radius, radius);
        }
        if (line != null && isEnabled()) {
            g2.setColor(getModel().isRollover() ? Theme.mix(line, Theme.NAVY, 0.25) : line);
            g2.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
        }
        if (!isEnabled()) {
            g2.dispose();
            Color old = getForeground();
            setForeground(Theme.FAINT);
            super.paintComponent(graphics);
            setForeground(old);
            return;
        }
        g2.dispose();
        super.paintComponent(graphics);
    }
}
