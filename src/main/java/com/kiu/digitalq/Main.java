package com.kiu.digitalq;

import com.kiu.digitalq.client.LauncherFrame;
import com.kiu.digitalq.ui.Theme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;

/** Desktop application entry point. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("KIU digitalQ needs a graphical desktop.");
            System.err.println("To run only the queue server, use: java -cp build\\classes com.kiu.digitalq.ServerMain");
            return;
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("apple.awt.application.name", "KIU digitalQ");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The cross-platform look and feel is a fine fallback.
        }
        UIManager.put("OptionPane.messageFont", Theme.font(13));
        UIManager.put("OptionPane.buttonFont", Theme.font(13));
        UIManager.put("ToolTip.font", Theme.font(12));
        UIManager.put("Button.defaultButtonFollowsFocus", Boolean.FALSE);
        UIManager.put("Spinner.font", Theme.font(13));
        UIManager.put("ComboBox.font", Theme.font(13));
        UIManager.put("CheckBox.font", Theme.font(12));

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Something went wrong:\n\n" + (error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage()),
                    "KIU digitalQ", JOptionPane.ERROR_MESSAGE));
        });

        SwingUtilities.invokeLater(() -> new LauncherFrame().setVisible(true));
    }
}
