package com.roommatch.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Needs a display (skipped on a headless machine). */
class RoomMatchGUITest {
    @Test
    void loginBarLogsInAndOut() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        RoomMatchGUI gui = new RoomMatchGUI(new GuiTestServices().services, GuiTasks.immediate());
        assertFalse(gui.logoutButton.isEnabled());
        gui.userIdField.setText("nobody");
        gui.loginButton.doClick();
        assertTrue(gui.statusLabel.getText().contains("No user"));
        gui.userIdField.setText("p001");
        gui.loginButton.doClick();
        assertTrue(gui.whoLabel.getText().contains("p001"));
        assertTrue(gui.logoutButton.isEnabled());
        gui.logoutButton.doClick();
        assertEquals("Not logged in", gui.whoLabel.getText());
    }

    @Test
    void guiCommandRefusesToRunWithoutADisplay() {
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless());
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int code = new RoomMatchCLI(new java.io.PrintStream(new java.io.ByteArrayOutputStream()),
                new java.io.PrintStream(err), () -> new GuiTestServices().services).run(new String[] {"gui"});
        assertEquals(1, code);
        assertTrue(err.toString().contains("no display"));
    }
}
