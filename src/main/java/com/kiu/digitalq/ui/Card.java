package com.kiu.digitalq.ui;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** A white rounded card with a soft shadow, used as the main surface. */
public class Card extends JPanel {

    private Color cardColor = Theme.CARD;
    private Color borderColor = Theme.LINE;
    private int arc = 18;
    private boolean shadow = true;
    private float flash = 0f;

    public Card() {
        this(null);
    }

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(new EmptyBorder(20, 20, 20, 20));
    }

    public Card setCardColor(Color color) {
        this.cardColor = color;
        repaint();
        return this;
    }

    public Card setBorderColor(Color color) {
        this.borderColor = color;
        repaint();
        return this;
    }

    public Card setArc(int arc) {
        this.arc = arc;
        repaint();
        return this;
    }

    public Card setShadow(boolean shadow) {
        this.shadow = shadow;
        repaint();
        return this;
    }

    public Card setPadding(int top, int left, int bottom, int right) {
        setBorder(new EmptyBorder(top, left, bottom, right));
        return this;
    }

    /** Briefly highlights the card in gold, for a newly called student. */
    public void flash() {
        flash = 1f;
        Timer timer = new Timer(55, null);
        timer.addActionListener(event -> {
            flash -= 0.045f;
            if (flash <= 0) {
                flash = 0;
                timer.stop();
            }
            repaint();
        });
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        if (shadow) {
            g2.setColor(new Color(0x0B, 0x2B, 0x4C, 16));
            g2.fillRoundRect(1, 3, width - 2, height - 3, arc, arc);
        }
        g2.setColor(cardColor);
        g2.fillRoundRect(0, 0, width, height - 1, arc, arc);
        g2.setColor(borderColor);
        g2.drawRoundRect(0, 0, width - 1, height - 2, arc, arc);
        if (flash > 0) {
            g2.setColor(new Color(0xF5, 0xB3, 0x01, (int) (90 * flash)));
            g2.fillRoundRect(0, 0, width, height - 1, arc, arc);
        }
        g2.dispose();
        super.paintComponent(graphics);
    }
}
