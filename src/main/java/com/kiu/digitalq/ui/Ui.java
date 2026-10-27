package com.kiu.digitalq.ui;

import com.kiu.digitalq.model.TellerStatus;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Small factory and helper methods that keep the Swing screens consistent. */
public final class Ui {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter SHORT_CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private Ui() {
    }

    // ------------------------------------------------------------- typography

    public static JLabel title(String text, float size) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, size));
        label.setForeground(Theme.TEXT);
        return label;
    }

    public static JLabel label(String text, float size) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(size));
        label.setForeground(Theme.TEXT);
        return label;
    }

    public static JLabel muted(String text, float size) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(size));
        label.setForeground(Theme.MUTED);
        return label;
    }

    /** Updates an existing label with muted helper text. */
    public static void muted(JLabel label, String text) {
        label.setText(text == null ? " " : text);
        label.setForeground(Theme.MUTED);
    }

    public static JLabel overline(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(Theme.font(Font.BOLD, 10.5f));
        label.setForeground(Theme.MUTED);
        return label;
    }

    public static JLabel whiteTitle(String text, float size) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, size));
        label.setForeground(Color.WHITE);
        return label;
    }

    // ---------------------------------------------------------------- layout

    public static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    public static JPanel row(int gap, Component... components) {
        JPanel panel = panel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        for (Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    public static JPanel column(int gap, Component... components) {
        JPanel panel = panel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (int i = 0; i < components.length; i++) {
            panel.add(components[i]);
            if (i < components.length - 1 && gap > 0) {
                panel.add(Box.createVerticalStrut(gap));
            }
        }
        return panel;
    }

    public static JPanel padded(Component component, int top, int left, int bottom, int right) {
        JPanel wrapper = panel(new java.awt.BorderLayout());
        wrapper.setBorder(new EmptyBorder(top, left, bottom, right));
        wrapper.add(component, java.awt.BorderLayout.CENTER);
        return wrapper;
    }

    public static JPanel grow(Component component) {
        JPanel wrapper = panel(new java.awt.BorderLayout());
        wrapper.add(component, java.awt.BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Sizes a window to a comfortable preferred size, shrinking it when the
     * screen is smaller so nothing hides behind the taskbar, then centres it.
     */
    public static void sizeWindow(Window window, int preferredWidth, int preferredHeight) {
        GraphicsConfiguration configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        Rectangle bounds = configuration.getBounds();
        int usableWidth = bounds.width - insets.left - insets.right;
        int usableHeight = bounds.height - insets.top - insets.bottom;
        int width = Math.min(preferredWidth, Math.max(720, usableWidth - 16));
        int height = Math.min(preferredHeight, Math.max(560, usableHeight - 16));
        window.setSize(width, height);
        window.setLocation(bounds.x + insets.left + Math.max(0, (usableWidth - width) / 2),
                bounds.y + insets.top + Math.max(0, (usableHeight - height) / 2));
    }

    // ---------------------------------------------------------------- inputs

    public static JTextField field(float size) {
        JTextField field = new JTextField();
        styleField(field, size);
        return field;
    }

    public static JPasswordField passwordField(float size) {
        JPasswordField field = new JPasswordField();
        styleField(field, size);
        return field;
    }

    private static void styleField(JTextField field, float size) {
        field.setFont(Theme.font(size));
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.NAVY);
        field.setBackground(Color.WHITE);
        field.setBorder(fieldBorder(Theme.LINE));
        field.setSelectionColor(new Color(0xDC, 0xE8, 0xF8));
        field.setSelectedTextColor(Theme.TEXT);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                field.setBorder(fieldBorder(Theme.NAVY));
            }

            @Override
            public void focusLost(FocusEvent event) {
                field.setBorder(fieldBorder(Theme.LINE));
            }
        });
    }

    private static javax.swing.border.Border fieldBorder(Color color) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                new EmptyBorder(10, 13, 10, 13));
    }

    // ---------------------------------------------------------------- pieces

    public static JComponent statusDot(Color color, int size) {
        JComponent dot = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(0, 0, size, size);
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(size, size));
        dot.setMinimumSize(new Dimension(size, size));
        dot.setMaximumSize(new Dimension(size, size));
        return dot;
    }

    public static PillLabel pill(String text, String status) {
        return new PillLabel(text).setPill(text, Theme.statusText(status), Theme.statusSoft(status));
    }

    public static PillLabel pill(String text, Color foreground, Color background) {
        return new PillLabel(text).setPill(text, foreground, background);
    }

    public static JLabel iconTile(String text, Color background, Color foreground, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setFont(Theme.font(Font.BOLD, size * 0.34f));
        label.setForeground(foreground);
        label.setPreferredSize(new Dimension(size, size));
        label.setMinimumSize(new Dimension(size, size));
        label.setMaximumSize(new Dimension(size, size));
        return label;
    }

    public static JComponent statusPill(String statusLabel, String statusName) {
        return pill(statusLabel, statusName);
    }

    public static JComponent divider() {
        JPanel line = panel(null);
        line.setPreferredSize(new Dimension(1, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE_SOFT));
        return line;
    }

    public static StatusRow statusRow(TellerStatus status, Runnable action) {
        return new StatusRow(status, action);
    }

    // ---------------------------------------------------------------- tables

    public static void styleTable(JTable table) {
        table.setRowHeight(40);
        table.setFont(Theme.font(13));
        table.setBackground(Color.WHITE);
        table.setForeground(Theme.TEXT);
        table.setGridColor(Theme.LINE_SOFT);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(Theme.GOLD_SOFT);
        table.setSelectionForeground(Theme.TEXT);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.getTableHeader().setPreferredSize(new Dimension(10, 38));
        table.setDefaultRenderer(Object.class, new BodyRenderer());
    }

    public static JScrollPane scroll(Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setViewportBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(Color.WHITE);
        pane.setBackground(Color.WHITE);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }

    public static JTextArea readOnlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(Theme.font(12.5f));
        area.setForeground(Theme.TEXT);
        area.setBackground(Color.WHITE);
        area.setBorder(new EmptyBorder(6, 4, 6, 4));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    public static void attachClick(Component component, Runnable action) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                action.run();
            }
        });
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                attachClick(child, action);
            }
        }
    }

    // ---------------------------------------------------------------- format

    public static String clock(LocalTime time) {
        return time.format(CLOCK);
    }

    public static String shortClock(LocalTime time) {
        return time.format(SHORT_CLOCK);
    }

    public static String countdown(long seconds) {
        if (seconds <= 0) {
            return "0m";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return Math.max(1, seconds) + "s";
    }

    public static String plural(long count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    public static void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    public static void error(JLabel label, String text) {
        label.setText(text == null ? "" : text);
        label.setForeground(Theme.RED);
    }

    public static void success(JLabel label, String text) {
        label.setText(text == null ? "" : text);
        label.setForeground(Theme.GREEN);
    }

    public static void clear(JLabel label) {
        label.setText(" ");
        label.setForeground(Theme.MUTED);
    }

    // ------------------------------------------------------------- helpers

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setOpaque(true);
            setBackground(Theme.BG);
            setForeground(Theme.MUTED);
            setFont(Theme.font(Font.BOLD, 10.5f));
            setBorder(new EmptyBorder(10, 10, 10, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            setText(value == null ? "" : value.toString().toUpperCase());
            return this;
        }
    }

    private static final class BodyRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            if (!selected) {
                setBackground(row % 2 == 0 ? Color.WHITE : Theme.BG_ALT);
                setForeground(Theme.TEXT);
            }
            return this;
        }
    }
}
