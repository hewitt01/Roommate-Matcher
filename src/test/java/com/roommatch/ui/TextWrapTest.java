package com.roommatch.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextWrapTest {
    @Test
    void wrapsAtWordBoundariesWithinTheWidthAndAddsTheIndent() {
        String wrapped = TextWrap.wrap("one two three four five six seven eight nine ten", 20, "> ");
        for (String line : wrapped.split("\\R")) {
            assertTrue(line.length() <= 20, "too long: '" + line + "'");
            assertTrue(line.startsWith("> "));
        }
        assertEquals("one two three four five six seven eight nine ten",
                wrapped.replace("> ", "").replaceAll("\\R", " "));
    }

    @Test
    void shortTextStaysOnOneLine() {
        assertEquals("  hello world", TextWrap.wrap("hello world", 78, "  "));
    }

    @Test
    void paragraphBreaksAreKept() {
        String wrapped = TextWrap.wrap("first paragraph\n\nsecond paragraph", 78, "");
        assertEquals("first paragraph" + System.lineSeparator() + System.lineSeparator() + "second paragraph", wrapped);
    }

    @Test
    void aWordLongerThanTheLineIsNotCut() {
        String longWord = "x".repeat(50);
        assertTrue(TextWrap.wrap("a " + longWord + " b", 20, "").contains(longWord));
    }

    @Test
    void listItemsGetAHangingIndent() {
        String item = TextWrap.wrapItem("alpha beta gamma delta epsilon zeta eta theta", 24, "  1. ", "     ");
        String[] lines = item.split("\\R");
        assertTrue(lines.length > 1);
        assertTrue(lines[0].startsWith("  1. alpha"));
        for (int i = 1; i < lines.length; i++) {
            assertTrue(lines[i].startsWith("     ") && !lines[i].startsWith("  1."));
        }
        for (String line : lines) {
            assertTrue(line.length() <= 24, "too long: '" + line + "'");
        }
    }
}
