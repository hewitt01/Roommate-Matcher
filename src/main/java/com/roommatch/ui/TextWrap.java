package com.roommatch.ui;

import java.util.ArrayList;
import java.util.List;

/** Word-wraps text for the console. Keeps blank lines between paragraphs. */
final class TextWrap {
    private TextWrap() { }

    static String wrap(String text, int width, String indent) {
        int room = Math.max(10, width - indent.length());
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (line.length() > 0 && line.length() + 1 + word.length() > room) {
                    lines.add(indent + line);
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            lines.add(indent + line);
        }
        return String.join(System.lineSeparator(), lines);
    }
}
