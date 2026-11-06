package com.roommatch.ui;

import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Profile tab: edit budget / location / move-in date (F02) and build the lifestyle profile by chat (F01). */
final class ProfilePanel extends JPanel {
    private final GuiSession session;
    private final Consumer<String> status;

    final JTextField budgetField = new JTextField(8);
    final JTextField locationField = new JTextField(14);
    final JTextField moveInField = new JTextField(10);
    final JButton saveButton = new JButton("Save changes");
    final JTextArea lifestyleArea = new JTextArea(5, 30);

    final JButton startButton = new JButton("Build profile with AI");
    final JTextArea transcript = new JTextArea(12, 40);
    final JTextField answerField = new JTextField(30);
    final JButton sendButton = new JButton("Send");
    final JComboBox<String> dimensionBox = new JComboBox<>(LifestyleProfile.DIMENSIONS.toArray(new String[0]));
    final JTextField correctionField = new JTextField(14);
    final JButton correctButton = new JButton("Correct");
    final JButton confirmButton = new JButton("Save profile");
    final JButton cancelButton = new JButton("Discard");

    ProfilePanel(GuiSession session, Consumer<String> status) {
        super(new BorderLayout(8, 8));
        this.session = session;
        this.status = status;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        name();
        add(detailsPanel(), BorderLayout.NORTH);
        add(chatPanel(), BorderLayout.CENTER);
        session.onLoginChange(this::refresh);
        saveButton.addActionListener(e -> save());
        startButton.addActionListener(e -> startConversation());
        sendButton.addActionListener(e -> send());
        answerField.addActionListener(e -> send());
        correctButton.addActionListener(e -> correct());
        confirmButton.addActionListener(e -> confirm());
        cancelButton.addActionListener(e -> cancel());
        refresh();
    }

    private void name() {
        budgetField.setName("budget");
        locationField.setName("location");
        moveInField.setName("moveInDate");
        saveButton.setName("saveProfile");
        lifestyleArea.setName("lifestyle");
        startButton.setName("startElicitation");
        transcript.setName("transcript");
        answerField.setName("answer");
        sendButton.setName("send");
        dimensionBox.setName("dimension");
        correctionField.setName("correction");
        correctButton.setName("correct");
        confirmButton.setName("confirmProfile");
        cancelButton.setName("cancelProfile");
    }

    private JPanel detailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Your details"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 5, 3, 5);
        c.anchor = GridBagConstraints.WEST;
        addRow(panel, c, 0, "Monthly budget", budgetField);
        addRow(panel, c, 1, "Location", locationField);
        addRow(panel, c, 2, "Move-in date (YYYY-MM-DD)", moveInField);
        c.gridx = 1;
        c.gridy = 3;
        panel.add(saveButton, c);
        lifestyleArea.setEditable(false);
        lifestyleArea.setLineWrap(true);
        lifestyleArea.setWrapStyleWord(true);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        JScrollPane scroll = new JScrollPane(lifestyleArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Lifestyle profile"));
        panel.add(scroll, c);
        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label, JTextField field) {
        c.gridx = 0;
        c.gridy = row;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        panel.add(field, c);
    }

    private JPanel chatPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Profile conversation"));
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(startButton);
        top.add(confirmButton);
        top.add(cancelButton);
        JPanel answerRow = new JPanel(new BorderLayout(6, 0));
        answerRow.add(answerField, BorderLayout.CENTER);
        answerRow.add(sendButton, BorderLayout.EAST);
        JPanel fixRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fixRow.add(new JLabel("Fix an answer:"));
        fixRow.add(dimensionBox);
        fixRow.add(correctionField);
        fixRow.add(correctButton);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(answerRow, BorderLayout.NORTH);
        bottom.add(fixRow, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(transcript), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    // ------------------------------------------------------------------ state

    private boolean chatting() {
        return session.loggedIn() && session.controller.isElicitating(session.user());
    }

    /** Re-reads everything from the logged-in user and enables only what makes sense right now. */
    void refresh() {
        User user = session.user();
        boolean in = user != null;
        budgetField.setText(in ? String.valueOf(trimNumber(user.getBudget())) : "");
        locationField.setText(in ? user.getLocation() : "");
        moveInField.setText(in ? String.valueOf(user.getMoveInDate()) : "");
        lifestyleArea.setText(lifestyleText(user));
        boolean chat = chatting();
        for (JTextField f : new JTextField[] {budgetField, locationField, moveInField}) {
            f.setEnabled(in);
        }
        saveButton.setEnabled(in);
        startButton.setEnabled(in && !chat);
        answerField.setEnabled(chat);
        sendButton.setEnabled(chat);
        dimensionBox.setEnabled(chat);
        correctionField.setEnabled(chat);
        correctButton.setEnabled(chat);
        confirmButton.setEnabled(chat);
        cancelButton.setEnabled(chat);
        if (!in) {
            transcript.setText("Log in to build your lifestyle profile.");
        } else if (!chat && transcript.getText().startsWith("Log in")) {
            transcript.setText("");
        }
    }

    private static Object trimNumber(double value) {
        return value == Math.floor(value) ? (Object) (long) value : (Object) value;
    }

    private static String lifestyleText(User user) {
        if (user == null) {
            return "";
        }
        Optional<LifestyleProfile> profile = user.getLifestyleProfile();
        if (profile.isEmpty() || profile.get().asMap().isEmpty()) {
            return "Not set yet. Use \"Build profile with AI\" below.";
        }
        LifestyleProfile p = profile.get();
        StringBuilder sb = new StringBuilder(p.isComplete() ? "Complete\n"
                : "Incomplete (missing: " + String.join(", ", p.missingDimensions()) + ")\n");
        p.asMap().forEach((d, v) -> sb.append(d).append(": ").append(v).append('\n'));
        return sb.toString().trim();
    }

    // ---------------------------------------------------------------- actions

    private void save() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(User.FIELD_BUDGET, budgetField.getText().trim());
        fields.put(User.FIELD_LOCATION, locationField.getText().trim());
        fields.put(User.FIELD_MOVE_IN_DATE, moveInField.getText().trim());
        try {
            session.controller.updateProfile(session.user(), fields);
        } catch (RuntimeException e) {
            status.accept("Not saved: " + GuiErrors.describe(e));
            refresh();
            return;
        }
        status.accept("Details saved.");
        refresh();
        session.profileChanged();
    }

    private void startConversation() {
        status.accept("Starting the profile conversation...");
        session.tasks.run(() -> session.controller.runElicitation(session.user()), first -> {
            transcript.setText("");
            say("Agent", first);
            status.accept("Answer in your own words.");
            refresh();
            answerField.requestFocusInWindow();
        }, error -> status.accept(GuiErrors.describe(error)));
    }

    private void send() {
        String text = answerField.getText().trim();
        if (text.isEmpty() || !chatting()) {
            return;
        }
        answerField.setText("");
        say("You", text);
        setBusy(true);
        session.tasks.run(() -> session.controller.handleResponse(session.user(), text), reply -> {
            say("Agent", reply);
            if (session.controller.isElicitationComplete(session.user())) {
                say("Agent", "That is everything. Press \"Save profile\" to keep it, or fix an answer first.");
            }
            setBusy(false);
        }, error -> {
            status.accept(GuiErrors.describe(error));
            setBusy(false);
        });
    }

    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy);
        answerField.setEnabled(!busy);
        if (!busy) {
            answerField.requestFocusInWindow();
        }
    }

    private void correct() {
        String dimension = (String) dimensionBox.getSelectedItem();
        String value = correctionField.getText().trim();
        if (value.isEmpty()) {
            status.accept("Type the corrected answer first.");
            return;
        }
        try {
            session.controller.correctElicitation(session.user(), dimension, value);
        } catch (RuntimeException e) {
            status.accept(GuiErrors.describe(e));
            return;
        }
        correctionField.setText("");
        say("You", "(corrected " + dimension + ") " + value);
        status.accept("Updated " + dimension + ".");
    }

    private void confirm() {
        LifestyleProfile saved;
        try {
            saved = session.controller.confirmElicitation(session.user());
        } catch (RuntimeException e) {
            status.accept(GuiErrors.describe(e));
            return;
        }
        status.accept(saved.isComplete() ? "Profile saved."
                : "Progress saved. Still missing: " + String.join(", ", saved.missingDimensions())
                        + ". Ranking needs all of them.");
        transcript.setText("");
        refresh();
        session.profileChanged();
    }

    private void cancel() {
        session.controller.cancelElicitation(session.user());
        transcript.setText("");
        status.accept("Discarded. Your saved profile is unchanged.");
        refresh();
    }

    private void say(String who, String text) {
        transcript.append(who + ": " + text + "\n\n");
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }
}
