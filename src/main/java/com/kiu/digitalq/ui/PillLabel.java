package com.kiu.digitalq.ui;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A rounded status/label pill. */
public class PillLabel extends JLabel {

    private Color pillBackground = Theme.SLATE_SOFT;

    public PillLabel(String text) {
        super(text);
        setOpaque(false);
        setHorizontalAlignment(CENTER);
        setFont(Theme.font(Font.BOLD, 11.5f));
        setForeground(Theme.SLATE);
        setBorder(new EmptyBorder(5, 11, 5, 11));
    }

    public PillLabel setPill(String text, Color foreground, Color background) {
        setText(text);
        setForeground(foreground);
        this.pillBackground = background;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(pillBackground);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(graphics);
    }
}
