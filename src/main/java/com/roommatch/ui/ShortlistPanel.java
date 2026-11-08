package com.roommatch.ui;

import com.roommatch.service.ShortlistService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/** Shortlist tab (F09): saved candidates, with removal. Unavailable ones are shown, not hidden. */
final class ShortlistPanel extends JPanel {
    private final GuiSession session;
    private final Consumer<String> status;
    final DefaultListModel<String> model = new DefaultListModel<>();
    final JList<String> list = new JList<>(model);
    final JButton removeButton = new JButton("Remove selected");
    final JButton refreshButton = new JButton("Refresh");
    private List<ShortlistService.Entry> entries = List.of();

    ShortlistPanel(GuiSession session, Consumer<String> status) {
        super(new BorderLayout(8, 8));
        this.session = session;
        this.status = status;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        list.setName("shortlist");
        removeButton.setName("remove");
        refreshButton.setName("refreshShortlist");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(removeButton);
        buttons.add(refreshButton);
        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        removeButton.addActionListener(e -> remove());
        refreshButton.addActionListener(e -> refresh());
        list.addListSelectionListener(e -> removeButton.setEnabled(list.getSelectedIndex() >= 0));
        session.onLoginChange(this::refresh);
        session.onShortlistChange(this::refresh);
        refresh();
    }

    void refresh() {
        model.clear();
        entries = List.of();
        if (!session.loggedIn()) {
            model.addElement("Log in to see your shortlist.");
        } else {
            try {
                entries = session.controller.getShortlist(session.user());
            } catch (RuntimeException e) {
                status.accept(GuiErrors.describe(e));
            }
            if (entries.isEmpty()) {
                model.addElement("Nothing bookmarked yet. Select a match and press Bookmark.");
            }
            for (ShortlistService.Entry entry : entries) {
                model.addElement(describe(entry));
            }
        }
        removeButton.setEnabled(false);
        refreshButton.setEnabled(session.loggedIn());
    }

    private static String describe(ShortlistService.Entry entry) {
        if (!entry.available() || entry.user() == null) {
            return entry.id() + "  -  no longer available";
        }
        return String.format(Locale.ROOT, "%s  -  %s, budget %.0f, move-in %s", entry.id(),
                entry.user().getLocation(), entry.user().getBudget(), entry.user().getMoveInDate());
    }

    private void remove() {
        int index = list.getSelectedIndex();
        if (index < 0 || index >= entries.size()) {
            return;
        }
        String id = entries.get(index).id();
        try {
            session.controller.removeBookmark(session.user(), id);
            status.accept("Removed " + id + " from your shortlist.");
        } catch (RuntimeException e) {
            status.accept(GuiErrors.describe(e));
        }
        refresh();
    }

    int shown() { return entries.size(); }
}
