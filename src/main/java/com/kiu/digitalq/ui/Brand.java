package com.kiu.digitalq.ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** The KIU digitalQ emblem and wordmark. */
public final class Brand {

    private Brand() {
    }

    public static JComponent mark(int size) {
        JComponent mark = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x07, 0x1E, 0x36, 45));
                g2.fillRoundRect(0, 2, size, size, (int) (size * 0.3), (int) (size * 0.3));
                g2.setColor(Theme.GOLD);
                g2.fillRoundRect(0, 0, size, size, (int) (size * 0.3), (int) (size * 0.3));
                g2.setColor(Theme.NAVY);
                g2.setFont(Theme.font(Font.BOLD, size * 0.255f));
                String text = "KIU";
                int textWidth = g2.getFontMetrics().stringWidth(text);
                int baseline = (size - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();
                g2.drawString(text, (size - textWidth) / 2, baseline);
                g2.dispose();
            }
        };
        mark.setPreferredSize(new Dimension(size, size));
        mark.setMinimumSize(new Dimension(size, size));
        mark.setMaximumSize(new Dimension(size, size));
        mark.setOpaque(false);
        return mark;
    }

    public static JPanel lockup(boolean onDark, String subtitle, float titleSize) {
        JPanel row = Ui.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(mark(onDark ? 46 : 44));
        row.add(Box.createHorizontalStrut(13));

        JPanel text = Ui.panel(null);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JPanel words = Ui.panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel kiu = new JLabel("KIU");
        kiu.setFont(Theme.font(Font.BOLD, titleSize));
        kiu.setForeground(onDark ? Color.WHITE : Theme.NAVY);
        JLabel q = new JLabel("digitalQ");
        q.setFont(Theme.font(Font.BOLD, titleSize));
        q.setForeground(onDark ? Theme.GOLD : Theme.AMBER);
        words.add(kiu);
        words.add(q);
        text.add(words);

        if (subtitle != null && !subtitle.isBlank()) {
            JLabel sub = new JLabel(subtitle);
            sub.setFont(Theme.font(11.5f));
            sub.setForeground(onDark ? Theme.WHITE_70 : Theme.MUTED);
            text.add(Box.createVerticalStrut(2));
            text.add(sub);
        }
        row.add(text);
        return row;
    }

    public static JLabel overline(String text, Color color) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(Theme.font(Font.BOLD, 10f));
        label.setForeground(color);
        return label;
    }
}
