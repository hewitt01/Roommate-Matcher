package com.roommatch.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchesPanelTest {
    private final GuiTestServices fixture = new GuiTestServices();
    private final GuiSession session = fixture.session();
    private final List<String> status = new ArrayList<>();
    private final MatchesPanel panel = new MatchesPanel(session, status::add);
    private final ShortlistPanel shortlist = new ShortlistPanel(session, status::add);

    private String lastStatus() { return status.get(status.size() - 1); }

    private void rank() {
        session.login("p001");
        panel.findButton.doClick();
    }

    @Test
    void nothingWorksBeforeLogin() {
        assertFalse(panel.findButton.isEnabled());
        assertEquals("Log in first.", panel.detail.getText());
        assertTrue(shortlist.model.get(0).startsWith("Log in"));
    }

    @Test
    void findMatchesFillsTheTableBestFirstAndSelectsTheTop() {
        rank();
        assertEquals(5, panel.model.getRowCount());
        assertEquals(1, panel.model.getValueAt(0, 0));
        double first = Double.parseDouble((String) panel.model.getValueAt(0, 6));
        double second = Double.parseDouble((String) panel.model.getValueAt(1, 6));
        assertTrue(first >= second);
        assertEquals(0, panel.table.getSelectedRow());
        assertTrue(panel.explainButton.isEnabled());
        assertTrue(panel.detail.getText().contains("Skipped p006"));
    }

    @Test
    void partialProfilesAreMarkedNotAssessed() {
        rank();
        boolean found = false;
        for (int i = 0; i < panel.model.getRowCount(); i++) {
            if ("p003".equals(panel.model.getValueAt(i, 1))) {
                assertTrue(((String) panel.model.getValueAt(i, 7)).startsWith("not assessed"));
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    void filtersAreAppliedAndBadInputIsExplained() {
        session.login("p001");
        panel.cityField.setText("Vaughan");
        panel.findButton.doClick();
        assertTrue(panel.model.getRowCount() > 0);
        for (int i = 0; i < panel.model.getRowCount(); i++) {
            assertEquals("Vaughan", panel.model.getValueAt(i, 2));
        }
        panel.cityField.setText("");
        panel.minBudgetField.setText("abc");
        panel.findButton.doClick();
        assertTrue(lastStatus().contains("must be a number"));
        panel.minBudgetField.setText("2000");
        panel.maxBudgetField.setText("1000");
        panel.findButton.doClick();
        assertTrue(lastStatus().contains("above maximum"));
        panel.minBudgetField.setText("");
        panel.maxBudgetField.setText("");
        panel.fromField.setText("tomorrow");
        panel.findButton.doClick();
        assertTrue(lastStatus().contains("2027-01-15"));
    }

    @Test
    void filtersThatMatchNobodySaySo() {
        session.login("p001");
        panel.cityField.setText("Atlantis");
        panel.findButton.doClick();
        assertEquals(0, panel.model.getRowCount());
        assertTrue(panel.detail.getText().startsWith("No candidates match"));
        assertFalse(panel.explainButton.isEnabled());
    }

    @Test
    void explainFrictionAndAskShowTheirResults() {
        rank();
        panel.explainButton.doClick();
        assertTrue(panel.detail.getText().contains("You two match on sleep and noise."));
        panel.frictionButton.doClick();
        assertTrue(panel.detail.getText().contains("Tidiness differs."));
        assertTrue(panel.detail.getText().contains("Quiet hours after ten."));
        panel.questionField.setText("Are they tidy?");
        panel.askButton.doClick();
        assertTrue(panel.detail.getText().contains("They are quiet and tidy."));
        panel.questionField.setText("Do they have pets?");
        panel.askButton.doClick();
        assertTrue(panel.detail.getText().contains("Not in their profile"));
    }

    @Test
    void anEmptyQuestionIsRefused() {
        rank();
        int before = fixture.scoringCalls.get();
        panel.askButton.doClick();
        assertEquals("Type a question first.", lastStatus());
        assertEquals(before, fixture.scoringCalls.get());
    }

    @Test
    void aFailingAiServiceIsReportedAndNothingIsLost() {
        rank();
        int rows = panel.model.getRowCount();
        fixture.aiDown = true;
        panel.explainButton.doClick();
        assertTrue(lastStatus().startsWith("The AI service failed"));
        assertEquals(rows, panel.model.getRowCount());
        assertTrue(panel.explainButton.isEnabled());
        panel.findButton.doClick();
        assertEquals(rows, panel.model.getRowCount(), "a failed re-run keeps the previous table");
    }

    @Test
    void editingTheProfileShowsTheOutOfDateBannerAndExplainRescores() {
        rank();
        assertEquals(" ", panel.staleBanner.getText());
        session.controller.updateProfile(session.user(), Map.of("budget", "1500"));
        session.profileChanged();
        assertTrue(panel.staleBanner.getText().contains("out of date"));
        int before = fixture.scoringCalls.get();
        panel.explainButton.doClick();
        assertTrue(fixture.scoringCalls.get() > before);
        assertTrue(panel.detail.getText().contains("Re-scored first"));
        panel.findButton.doClick();
        assertEquals(" ", panel.staleBanner.getText());
    }

    @Test
    void bookmarkingShowsUpOnTheShortlistTabAndCanBeRemoved() {
        rank();
        String id = (String) panel.model.getValueAt(0, 1);
        panel.bookmarkButton.doClick();
        assertEquals("Bookmarked " + id + ".", lastStatus());
        assertEquals(1, shortlist.shown());
        assertTrue(shortlist.model.get(0).startsWith(id));
        panel.bookmarkButton.doClick();
        assertTrue(lastStatus().contains("already on your shortlist"));
        assertEquals(1, shortlist.shown());

        shortlist.list.setSelectedIndex(0);
        assertTrue(shortlist.removeButton.isEnabled());
        shortlist.removeButton.doClick();
        assertEquals(0, shortlist.shown());
        assertTrue(shortlist.model.get(0).startsWith("Nothing bookmarked"));
    }

    @Test
    void loggingOutClearsTheResults() {
        rank();
        session.logout();
        assertEquals(0, panel.model.getRowCount());
        assertEquals("Log in first.", panel.detail.getText());
        assertFalse(panel.findButton.isEnabled());
    }

    @Test
    void shortlistsArePerUserAndRestoredOnLogin() {
        rank();
        panel.bookmarkButton.doClick();
        session.login("p002");
        assertTrue(shortlist.model.get(0).startsWith("Nothing bookmarked"));
        session.login("p001");
        assertEquals(1, shortlist.shown());
    }
}
