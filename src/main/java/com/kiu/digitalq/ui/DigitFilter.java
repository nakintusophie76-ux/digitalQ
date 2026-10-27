package com.kiu.digitalq.ui;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Allows only digits, with a maximum length. Used for registration numbers and PINs. */
public final class DigitFilter extends DocumentFilter {

    private final int maxLength;

    public DigitFilter(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
            throws BadLocationException {
        if (text == null || !text.matches("\\d*")) {
            return;
        }
        if (bypass.getDocument().getLength() + text.length() > maxLength) {
            return;
        }
        bypass.insertString(offset, text, attributes);
    }

    @Override
    public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
            throws BadLocationException {
        if (text == null || !text.matches("\\d*")) {
            return;
        }
        int newLength = bypass.getDocument().getLength() - length + text.length();
        if (newLength > maxLength) {
            return;
        }
        bypass.replace(offset, length, text, attributes);
    }
}
