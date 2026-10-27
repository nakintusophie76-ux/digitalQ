package com.kiu.digitalq.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A small coloured status dot with a changeable colour. */
public class Dot extends JComponent {

    private Color color;
    private final int size;

    public Dot(Color color, int size) {
        this.color = color;
        this.size = size;
        setOpaque(false);
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    public void setColor(Color color) {
        this.color = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
        g2.fillOval(0, 0, size, size);
        g2.setColor(color);
        int inset = Math.max(1, size / 4);
        g2.fillOval(inset, inset, size - inset * 2, size - inset * 2);
        g2.dispose();
    }
}
