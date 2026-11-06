package com.roommatch.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfilePanelTest {
    private final GuiTestServices fixture = new GuiTestServices();
    private final GuiSession session = fixture.session();
    private final List<String> status = new ArrayList<>();
    private final ProfilePanel panel = new ProfilePanel(session, status::add);

    private String lastStatus() { return status.get(status.size() - 1); }

    @Test
    void everythingIsDisabledUntilSomeoneLogsIn() {
        assertFalse(panel.saveButton.isEnabled());
        assertFalse(panel.startButton.isEnabled());
        assertFalse(panel.sendButton.isEnabled());
        assertTrue(panel.transcript.getText().startsWith("Log in"));
    }

    @Test
    void loggingInShowsTheUsersDetails() {
        session.login("p001");
        assertEquals(session.user().getLocation(), panel.locationField.getText());
        assertTrue(panel.saveButton.isEnabled());
        assertTrue(panel.startButton.isEnabled());
        assertFalse(panel.sendButton.isEnabled());
        assertTrue(panel.lifestyleArea.getText().startsWith("Complete"));
    }

    @Test
    void savingValidChangesUpdatesTheUserAndTellsOtherTabs() {
        session.login("p001");
        List<String> heard = new ArrayList<>();
        session.onProfileChange(() -> heard.add("changed"));
        panel.budgetField.setText("1500");
        panel.locationField.setText("Vaughan");
        panel.saveButton.doClick();
        assertEquals(1500.0, session.user().getBudget());
        assertEquals("Vaughan", session.user().getLocation());
        assertEquals(1, heard.size());
        assertEquals("Details saved.", lastStatus());
    }

    @Test
    void anInvalidValueSavesNothingAndExplainsWhy() {
        session.login("p001");
        double before = session.user().getBudget();
        String locationBefore = session.user().getLocation();
        panel.budgetField.setText("lots");
        panel.locationField.setText("Elsewhere");
        panel.saveButton.doClick();
        assertEquals(before, session.user().getBudget());
        assertEquals(locationBefore, session.user().getLocation());
        assertTrue(lastStatus().startsWith("Not saved"));
        assertEquals(locationBefore, panel.locationField.getText());
    }

    @Test
    void theProfileConversationBuildsAndSavesALifestyleProfile() {
        session.login("p006");
        assertTrue(panel.lifestyleArea.getText().startsWith("Not set"));
        panel.startButton.doClick();
        assertTrue(panel.transcript.getText().startsWith("Agent: "));
        assertTrue(panel.sendButton.isEnabled());
        assertFalse(panel.startButton.isEnabled());

        panel.answerField.setText("I like a spotless, quiet home, few guests, early to bed.");
        panel.sendButton.doClick();
        assertTrue(panel.transcript.getText().contains("You: I like a spotless"));
        assertTrue(panel.transcript.getText().contains("Press \"Save profile\""));

        panel.confirmButton.doClick();
        assertEquals("Profile saved.", lastStatus());
        assertTrue(session.user().getLifestyleProfile().orElseThrow().isComplete());
        assertTrue(panel.lifestyleArea.getText().startsWith("Complete"));
        assertTrue(panel.startButton.isEnabled());
        assertFalse(panel.sendButton.isEnabled());
    }

    @Test
    void discardingLeavesTheSavedProfileAlone() {
        session.login("p006");
        panel.startButton.doClick();
        panel.cancelButton.doClick();
        assertTrue(session.user().getLifestyleProfile().isEmpty()
                || session.user().getLifestyleProfile().get().asMap().isEmpty());
        assertFalse(session.controller.isElicitating(session.user()));
        assertTrue(panel.startButton.isEnabled());
    }

    @Test
    void aFailingAiServiceShowsAMessageAndKeepsTheConversationUsable() {
        session.login("p006");
        panel.startButton.doClick();
        fixture.aiDown = true;
        panel.answerField.setText("quiet please");
        panel.sendButton.doClick();
        assertTrue(lastStatus().startsWith("The AI service failed"));
        assertTrue(panel.sendButton.isEnabled());
    }

    @Test
    void aCorrectionNeedsTextAndChangesTheDraft() {
        session.login("p006");
        panel.startButton.doClick();
        panel.correctButton.doClick();
        assertEquals("Type the corrected answer first.", lastStatus());
        panel.dimensionBox.setSelectedItem("cleanliness");
        panel.correctionField.setText("very tidy");
        panel.correctButton.doClick();
        assertEquals("very tidy", session.controller.elicitationDraft(session.user()).get("cleanliness"));
    }

    @Test
    void loggingOutResetsThePanel() {
        session.login("p006");
        panel.startButton.doClick();
        session.logout();
        assertFalse(panel.saveButton.isEnabled());
        assertEquals("", panel.locationField.getText());
        assertTrue(panel.transcript.getText().startsWith("Log in"));
    }

    @Test
    void loginRejectsUnknownBlankAndDeactivatedIds() {
        assertThrows(IllegalArgumentException.class, () -> session.login(" "));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> session.login("zzz")).getMessage().contains("zzz"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> session.login("p005")).getMessage().contains("deactivated"));
        assertFalse(session.loggedIn());
    }
}
