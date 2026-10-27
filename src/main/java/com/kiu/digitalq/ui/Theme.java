package com.kiu.digitalq.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Colours, fonts and status colours shared by every screen. */
public final class Theme {

    public static final Color NAVY = new Color(0x0B, 0x2B, 0x4C);
    public static final Color NAVY_LIGHT = new Color(0x12, 0x3C, 0x69);
    public static final Color NAVY_DEEP = new Color(0x07, 0x1E, 0x36);
    public static final Color NAVY_SOFT = new Color(0xEA, 0xF1, 0xFA);
    public static final Color GOLD = new Color(0xF5, 0xB3, 0x01);
    public static final Color GOLD_HOVER = new Color(0xFF, 0xC4, 0x2E);
    public static final Color GOLD_DARK = new Color(0xD9, 0x9A, 0x00);
    public static final Color GOLD_SOFT = new Color(0xFF, 0xF4, 0xD6);

    public static final Color BG = new Color(0xF2, 0xF5, 0xFA);
    public static final Color BG_ALT = new Color(0xFA, 0xFB, 0xFD);
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(0x14, 0x21, 0x3A);
    public static final Color MUTED = new Color(0x6B, 0x7A, 0x90);
    public static final Color FAINT = new Color(0x9A, 0xA6, 0xB6);
    public static final Color LINE = new Color(0xE1, 0xE7, 0xF0);
    public static final Color LINE_SOFT = new Color(0xED, 0xF1, 0xF6);

    public static final Color GREEN = new Color(0x17, 0xA0, 0x6A);
    public static final Color GREEN_SOFT = new Color(0xE6, 0xF7, 0xEF);
    public static final Color AMBER = new Color(0xB8, 0x7A, 0x00);
    public static final Color AMBER_SOFT = new Color(0xFD, 0xF3, 0xDD);
    public static final Color RED = new Color(0xC5, 0x3D, 0x3D);
    public static final Color RED_SOFT = new Color(0xFD, 0xEA, 0xEA);
    public static final Color PURPLE = new Color(0x5B, 0x4B, 0xC4);
    public static final Color PURPLE_SOFT = new Color(0xEE, 0xEC, 0xFC);
    public static final Color SLATE = new Color(0x5A, 0x6B, 0x82);
    public static final Color SLATE_SOFT = new Color(0xEE, 0xF2, 0xF7);
    public static final Color WHITE = Color.WHITE;
    public static final Color WHITE_70 = new Color(255, 255, 255, 178);
    public static final Color WHITE_45 = new Color(255, 255, 255, 115);

    private static final String FAMILY = pickFamily(
            "Segoe UI", "Inter", "SF Pro Text", "Ubuntu", "Cantarell", "Helvetica Neue", "SansSerif");
    private static final String MONO_FAMILY = pickFamily(
            "Consolas", "Cascadia Mono", "JetBrains Mono", "Menlo", "DejaVu Sans Mono", "Monospaced");

    private Theme() {
    }

    public static Font font(float size) {
        return font(Font.PLAIN, size);
    }

    public static Font font(int style, float size) {
        return new Font(FAMILY, style, Math.max(9, Math.round(size)));
    }

    public static Font mono(float size) {
        return new Font(MONO_FAMILY, Font.PLAIN, Math.max(9, Math.round(size)));
    }

    public static Color mix(Color first, Color second, double ratio) {
        double amount = Math.max(0, Math.min(1, ratio));
        return new Color(
                (int) Math.round(first.getRed() * (1 - amount) + second.getRed() * amount),
                (int) Math.round(first.getGreen() * (1 - amount) + second.getGreen() * amount),
                (int) Math.round(first.getBlue() * (1 - amount) + second.getBlue() * amount));
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static Color statusColor(String status) {
        if (status == null) {
            return SLATE;
        }
        return switch (status.toUpperCase()) {
            case "AVAILABLE" -> GREEN;
            case "SERVING" -> NAVY;
            case "RESTING" -> AMBER;
            case "NOT_AROUND" -> RED;
            case "CLOSED" -> SLATE;
            default -> SLATE;
        };
    }

    public static Color statusSoft(String status) {
        if (status == null) {
            return SLATE_SOFT;
        }
        return switch (status.toUpperCase()) {
            case "AVAILABLE" -> GREEN_SOFT;
            case "SERVING" -> GOLD_SOFT;
            case "RESTING" -> AMBER_SOFT;
            case "NOT_AROUND" -> RED_SOFT;
            case "CLOSED" -> SLATE_SOFT;
            default -> SLATE_SOFT;
        };
    }

    public static Color statusText(String status) {
        if (status == null) {
            return SLATE;
        }
        return switch (status.toUpperCase()) {
            case "AVAILABLE" -> GREEN;
            case "SERVING" -> NAVY;
            case "RESTING" -> AMBER;
            case "NOT_AROUND" -> RED;
            case "CLOSED" -> SLATE;
            default -> SLATE;
        };
    }

    private static String pickFamily(String... candidates) {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidate : candidates) {
            if (available.contains(candidate)) {
                return candidate;
            }
        }
        return Font.SANS_SERIF;
    }
}
