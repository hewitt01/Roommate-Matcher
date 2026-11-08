package com.roommatch.ui;

import com.roommatch.agent.MatchAnswer;
import com.roommatch.agent.MatchExplanation;
import com.roommatch.agent.RankingOutcome;
import com.roommatch.agent.FrictionMapView;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.FrictionMap;
import com.roommatch.model.HardFilters;
import com.roommatch.model.Shortlist.AddResult;
import com.roommatch.model.User;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * Matches tab: filters and ranking (F03, F04), the out-of-date banner (F10), explanation (F05),
 * friction map (F06), questions about one candidate (F07) and bookmarking (F09).
 */
final class MatchesPanel extends JPanel {
    private static final String[] COLUMNS =
            {"#", "Candidate", "City", "Rent", "You->Them", "Them->You", "Overall", "Notes"};

    private final GuiSession session;
    private final Consumer<String> status;

    final JTextField cityField = new JTextField(9);
    final JTextField minBudgetField = new JTextField(5);
    final JTextField maxBudgetField = new JTextField(5);
    final JTextField fromField = new JTextField(8);
    final JTextField toField = new JTextField(8);
    final JButton findButton = new JButton("Find matches");
    final JLabel staleBanner = new JLabel(" ");
    final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    final JTable table = new JTable(model);
    final JTextArea detail = new JTextArea(9, 40);
    final JButton explainButton = new JButton("Explain");
    final JButton frictionButton = new JButton("Friction map");
    final JButton bookmarkButton = new JButton("Bookmark");
    final JTextField questionField = new JTextField(28);
    final JButton askButton = new JButton("Ask");

    private RankingOutcome outcome;
    private HardFilters lastFilters = HardFilters.none();

    MatchesPanel(GuiSession session, Consumer<String> status) {
        super(new BorderLayout(8, 8));
        this.session = session;
        this.status = status;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        nameParts();
        add(topPanel(), BorderLayout.NORTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        int[] widths = {30, 80, 100, 60, 80, 80, 70, 320};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), detailPanel());
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        findButton.addActionListener(e -> findMatches());
        explainButton.addActionListener(e -> explain());
        frictionButton.addActionListener(e -> friction());
        bookmarkButton.addActionListener(e -> bookmark());
        askButton.addActionListener(e -> ask());
        questionField.addActionListener(e -> ask());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtons();
            }
        });
        session.onLoginChange(this::reset);
        session.onProfileChange(this::checkStale);
        reset();
    }

    private void nameParts() {
        cityField.setName("city");
        minBudgetField.setName("minBudget");
        maxBudgetField.setName("maxBudget");
        fromField.setName("from");
        toField.setName("to");
        findButton.setName("find");
        staleBanner.setName("staleBanner");
        table.setName("matchTable");
        detail.setName("detail");
        explainButton.setName("explain");
        frictionButton.setName("friction");
        bookmarkButton.setName("bookmark");
        questionField.setName("question");
        askButton.setName("ask");
    }

    private JPanel topPanel() {
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filters.setBorder(BorderFactory.createTitledBorder("Filters (all optional)"));
        filters.add(new JLabel("City"));
        filters.add(cityField);
        filters.add(new JLabel("Rent from"));
        filters.add(minBudgetField);
        filters.add(new JLabel("to"));
        filters.add(maxBudgetField);
        filters.add(new JLabel("Move in from"));
        filters.add(fromField);
        filters.add(new JLabel("to"));
        filters.add(toField);
        filters.add(findButton);
        fromField.setToolTipText("YYYY-MM-DD");
        toField.setToolTipText("YYYY-MM-DD");
        staleBanner.setForeground(new java.awt.Color(0x9A, 0x4B, 0x00));
        JPanel top = new JPanel(new BorderLayout());
        top.add(filters, BorderLayout.CENTER);
        top.add(staleBanner, BorderLayout.SOUTH);
        return top;
    }

    private JPanel detailPanel() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttons.add(explainButton);
        buttons.add(frictionButton);
        buttons.add(bookmarkButton);
        buttons.add(new JLabel("   Ask about them:"));
        buttons.add(questionField);
        buttons.add(askButton);
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Selected match"));
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(detail), BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------ state

    private void reset() {
        outcome = null;
        model.setRowCount(0);
        staleBanner.setText(" ");
        detail.setText(session.loggedIn() ? "Press \"Find matches\" to rank compatible roommates."
                : "Log in first.");
        updateButtons();
    }

    private CompatibilityResult selected() {
        int row = table.getSelectedRow();
        if (outcome == null || row < 0 || row >= outcome.ranked().size()) {
            return null;
        }
        return outcome.ranked().get(row);
    }

    private void updateButtons() {
        boolean in = session.loggedIn();
        boolean picked = selected() != null;
        findButton.setEnabled(in);
        explainButton.setEnabled(picked);
        frictionButton.setEnabled(picked);
        bookmarkButton.setEnabled(picked);
        askButton.setEnabled(picked);
        questionField.setEnabled(picked);
    }

    private void setBusy(boolean busy) {
        findButton.setEnabled(!busy && session.loggedIn());
        if (busy) {
            for (JButton b : new JButton[] {explainButton, frictionButton, bookmarkButton, askButton}) {
                b.setEnabled(false);
            }
        } else {
            updateButtons();
        }
    }

    /** F10: shown when the profile changed after the last ranking. */
    private void checkStale() {
        if (outcome != null && session.loggedIn() && session.controller.isStale(session.user())) {
            staleBanner.setText("Your profile changed, so these results are out of date. Press \"Find matches\" to refresh them.");
        }
    }

    // ---------------------------------------------------------------- ranking

    private HardFilters readFilters() {
        HardFilters f = HardFilters.none();
        String city = cityField.getText().trim();
        if (!city.isEmpty()) {
            f = f.withCity(city);
        }
        Double min = number(minBudgetField, "Rent from");
        Double max = number(maxBudgetField, "Rent to");
        if (min != null || max != null) {
            f = f.withBudgetRange(min, max);
        }
        LocalDate from = date(fromField, "Move in from");
        LocalDate to = date(toField, "Move in to");
        if (from != null || to != null) {
            f = f.withMoveInWindow(from, to);
        }
        return f;
    }

    private static Double number(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number, got '" + text + "'.");
        }
    }

    private static LocalDate date(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + " must look like 2027-01-15, got '" + text + "'.");
        }
    }

    private void findMatches() {
        HardFilters filters;
        try {
            filters = readFilters();
        } catch (RuntimeException e) {
            status.accept(GuiErrors.describe(e));
            return;
        }
        User user = session.user();
        lastFilters = filters;
        status.accept("Ranking candidates with the AI. This can take a little while...");
        setBusy(true);
        session.tasks.run(() -> session.controller.getRankedMatches(user, filters), result -> {
            if (session.user() != user) {
                return;
            }
            showOutcome(result);
            setBusy(false);
        }, error -> {
            status.accept(GuiErrors.describe(error));
            setBusy(false);
        });
    }

    private void showOutcome(RankingOutcome result) {
        outcome = result;
        staleBanner.setText(" ");
        model.setRowCount(0);
        int rank = 1;
        for (CompatibilityResult r : result.ranked()) {
            User c = r.getCandidate();
            String notes = r.getNotAssessedDimensions().isEmpty() ? ""
                    : "not assessed: " + String.join(", ", r.getNotAssessedDimensions());
            model.addRow(new Object[] {rank++, c.getId(), c.getLocation(), String.format(Locale.ROOT, "%.0f", c.getBudget()),
                    String.format(Locale.ROOT, "%.1f", r.getScoreAtoB()),
                    String.format(Locale.ROOT, "%.1f", r.getScoreBtoA()),
                    String.format(Locale.ROOT, "%.1f", r.overallScore()), notes});
        }
        StringBuilder text = new StringBuilder();
        if (result.isEmptyPool()) {
            text.append("No candidates match your filters. Try relaxing the city, budget or move-in dates.");
            status.accept("No candidates match your filters.");
        } else {
            text.append(result.ranked().size()).append(" ranked, ").append(result.skipped().size()).append(" skipped.");
            status.accept("Ranked " + result.ranked().size() + " candidates. Select one to explain, check friction, ask or bookmark.");
        }
        for (RankingOutcome.Skipped s : result.skipped()) {
            text.append("\nSkipped ").append(s.candidate().getId()).append(": ").append(s.reason());
        }
        detail.setText(text.toString());
        if (!result.ranked().isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        updateButtons();
    }

    // ------------------------------------------------------- one-candidate tools

    private static String rescoredNote(boolean rescored) {
        return rescored ? "(Re-scored first, because your profile changed since the ranking.)\n\n" : "";
    }

    private void explain() {
        CompatibilityResult pick = selected();
        if (pick == null) {
            return;
        }
        User user = session.user();
        runDetail("Asking the AI to explain " + pick.getCandidate().getId() + "...",
                () -> session.controller.getExplanation(user, pick.getCandidate()),
                (MatchExplanation e) -> rescoredNote(e.rescored()) + "Why " + pick.getCandidate().getId()
                        + " matches:\n\n" + e.text());
    }

    private void friction() {
        CompatibilityResult pick = selected();
        if (pick == null) {
            return;
        }
        User user = session.user();
        runDetail("Building the friction map for " + pick.getCandidate().getId() + "...",
                () -> session.controller.getFrictionMap(user, pick.getCandidate()),
                (FrictionMapView v) -> rescoredNote(v.rescored()) + frictionText(pick.getCandidate().getId(), v.map()));
    }

    static String frictionText(String id, FrictionMap map) {
        StringBuilder sb = new StringBuilder("Friction map for " + id + "\n\nLikely friction points:\n");
        map.getFrictionPoints().forEach(p -> sb.append("  - ").append(p).append('\n'));
        sb.append("\nSuggested house rules:\n");
        map.getSuggestedRules().forEach(r -> sb.append("  - ").append(r).append('\n'));
        if (!map.getNotAssessedDimensions().isEmpty()) {
            sb.append("\nNot assessed (missing data): ").append(String.join(", ", map.getNotAssessedDimensions()));
        }
        return sb.toString().trim();
    }

    private void ask() {
        CompatibilityResult pick = selected();
        String question = questionField.getText().trim();
        if (pick == null) {
            return;
        }
        if (question.isEmpty()) {
            status.accept("Type a question first.");
            return;
        }
        User user = session.user();
        runDetail("Asking about " + pick.getCandidate().getId() + "...",
                () -> session.controller.answerMatchQuestion(user, pick.getCandidate(), question),
                (MatchAnswer a) -> rescoredNote(a.rescored()) + "Q: " + question + "\n\n"
                        + (a.answerable() ? "" : "(Not in their profile.) ") + a.text());
    }

    private <T> void runDetail(String working, java.util.concurrent.Callable<T> work,
                               java.util.function.Function<T, String> render) {
        User user = session.user();
        status.accept(working);
        setBusy(true);
        session.tasks.run(work, result -> {
            if (session.user() == user) {
                detail.setText(render.apply(result));
                detail.setCaretPosition(0);
                status.accept("Done.");
                setBusy(false);
            }
        }, error -> {
            status.accept(GuiErrors.describe(error));
            setBusy(false);
        });
    }

    private void bookmark() {
        CompatibilityResult pick = selected();
        if (pick == null) {
            return;
        }
        String id = pick.getCandidate().getId();
        try {
            AddResult result = session.controller.bookmark(session.user(), pick.getCandidate());
            status.accept(switch (result) {
                case ADDED -> "Bookmarked " + id + ".";
                case ALREADY_BOOKMARKED -> id + " is already on your shortlist.";
                case UNAVAILABLE -> id + " is no longer available, so it was not bookmarked.";
            });
            session.shortlistChanged();
        } catch (RuntimeException e) {
            status.accept(GuiErrors.describe(e));
        }
    }

    // exposed for tests
    RankingOutcome outcome() { return outcome; }

    HardFilters lastFilters() { return lastFilters; }

    List<CompatibilityResult> rows() { return outcome == null ? List.of() : outcome.ranked(); }
}
